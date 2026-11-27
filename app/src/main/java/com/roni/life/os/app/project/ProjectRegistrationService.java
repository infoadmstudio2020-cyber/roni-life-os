package com.roni.life.os.app.project;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.roni.life.os.app.resource.ArchiveExtractor;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Register operation: the only path that turns a draft into a permanent project.
 *
 * <p>Register is deliberately separate from Preview and Test. It performs exactly four steps,
 * in this order, and never reports success before all four have completed:</p>
 *
 * <ol>
 *   <li>validate the draft against the slot's rules;</li>
 *   <li>copy the source into the app's own project location
 *       ({@code files/RONiLiFEOS/app/projects/<uuid>});</li>
 *   <li>write the project metadata and register it in the {@link ProjectStore};</li>
 *   <li>point the slot's role at the new project (single-project slots only).</li>
 * </ol>
 *
 * <p>Every local source type ends up as an internal copy: a ZIP archive is extracted, a folder is
 * copied, and a bundled {@code ASSET} / {@code ZIP_ASSET} is read out of the APK. The registered
 * record therefore always points at {@code files/RONiLiFEOS/app/projects/<uuid>} and never at an
 * APK asset, so a later build cannot break an already-registered project.</p>
 *
 * <p>The original source is only ever read. A failed copy deletes the partial target directory
 * and leaves the registry untouched, so an interrupted registration can never leave a project
 * that points at half a folder.</p>
 */
public final class ProjectRegistrationService {

    private static final String TAG = "ProjectRegister";

    /** Ceiling on copied files, to stop a runaway folder tree. */
    public static final int MAX_FILES = 20000;

    /** Ceiling on folder nesting. */
    public static final int MAX_DEPTH = 24;

    /** How many replaced project versions are kept in {@code app/backups}. */
    public static final int MAX_BACKUPS = 5;

    /**
     * Serialises registration, process-wide.
     *
     * <p>Registration mints an id, copies a tree, seals it and writes the registry entry. Two
     * concurrent calls would each mint their own id and each seal their own copy, so a double-tap
     * on "Register" would produce two projects and two Keystore keys from one draft. Holding this
     * lock for the whole sequence makes registration atomic from the registry's point of view: a
     * caller either sees the project fully registered or not at all.</p>
     *
     * <p>A single global lock rather than a per-draft one is deliberate. Registration is a
     * user-initiated action that takes at most a few seconds, and a per-draft lock would still
     * allow two different drafts to interleave their Keystore operations.</p>
     */
    private static final Object REGISTRATION_LOCK = new Object();

    private ProjectRegistrationService() {
    }

    /** Outcome of a registration attempt. */
    public static final class Result {
        private final boolean success;
        private final String message;
        private final Project project;

        Result(boolean success, String message, Project project) {
            this.success = success;
            this.message = message != null ? message : "";
            this.project = project;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        /** The registered project, or {@code null} on failure. */
        public Project getProject() {
            return project;
        }
    }

    /**
     * Registers {@code draft} in {@code slot}.
     *
     * @param context           any context
     * @param store             the authoritative registry
     * @param draft             the project to register; its id is replaced
     * @param slot              the slot whose rules apply
     * @param previousProjectId the project this registration replaces, or {@code null}
     */
    public static Result register(Context context, ProjectStore store, Project draft,
                                  ProjectSlot slot, String previousProjectId) {
        synchronized (REGISTRATION_LOCK) {
            return registerLocked(context, store, draft, slot, previousProjectId);
        }
    }

    private static Result registerLocked(Context context, ProjectStore store, Project draft,
                                         ProjectSlot slot, String previousProjectId) {
        if (context == null || store == null || draft == null || slot == null) {
            return new Result(false, "The registration request was incomplete.", null);
        }
        if (!slot.allowsRegister()) {
            return new Result(false,
                    slot.getLabel() + " projects are references and cannot be registered.", null);
        }
        if (!slot.supports(draft.getSourceType())) {
            return new Result(false, slot.getLabel() + " does not support "
                    + draft.getSourceType().getKey() + " sources.", null);
        }

        // 1. Validate before touching the file system.
        ProjectValidator.Result validation = ProjectValidator.validate(draft, store.getAll());
        if (!validation.isValid()) {
            return new Result(false, validation.getFirstError(), null);
        }

        // 1b. Assign the final, stable project id BEFORE anything is encrypted.
        //
        //     The id is the Keystore alias suffix, so it must be fixed before the first byte is
        //     sealed and must never change afterwards. The directory is named after it, which
        //     makes the id → ciphertext mapping obvious on disk and removes the previous
        //     two-UUID split (directory UUID vs record UUID) that made a sealed tree impossible
        //     to attribute to a project.
        String projectId = UUID.randomUUID().toString();
        File target = new File(ProjectImporter.projectsRoot(context), projectId);
        if (!target.mkdirs()) {
            return new Result(false, "Unable to create the project directory.", null);
        }

        // 2. Copy the source. Nothing is written to the registry until this succeeds.
        String entryPoint;
        ProjectSourceType storedType;
        String storedPath;
        try {
            CopyOutcome outcome = copySource(context, draft, target);
            entryPoint = outcome.entryPoint;
            storedType = outcome.sourceType;
            storedPath = outcome.sourcePath;
        } catch (Throwable error) {
            ProjectImporter.deleteRecursively(target);
            AppLog.e(TAG, "Registration copy failed for '" + draft.getName() + "'", error);
            String detail = error.getMessage() != null ? error.getMessage()
                    : error.getClass().getSimpleName();
            return new Result(false, "The project could not be copied: " + detail, null);
        }

        // 2b. Validate the manifest, then seal the tree under the final id.
        //
        //     Sealing must complete before the record exists. A project that is registered but
        //     not sealed would be served as ciphertext and fail every request; a project that is
        //     sealed but not registered would leave an orphan key. Both are avoided by doing the
        //     work in this order and refusing to continue on failure.
        if (storedType == ProjectSourceType.FILE) {
            ProjectManifest.Result manifest = ProjectManifest.validate(target);
            if (!manifest.isValid()) {
                ProjectImporter.deleteRecursively(target);
                return new Result(false, manifest.getMessage(), null);
            }
            if (manifest.getEntryPoint() != null) {
                entryPoint = manifest.getEntryPoint();
            }
            try {
                ProjectImporter.sealProject(target, projectId);
            } catch (IllegalStateException refused) {
                if (!com.roni.library.security.crypto.ProjectVault.isAvailable()) {
                    // API 21–22 has no Keystore AES/GCM. Fall back to plain storage rather than
                    // failing the registration: isVaulted() reports false and the WebView serves
                    // the bytes untouched. This is a documented downgrade, not a silent one.
                    AppLog.w(TAG, "Encryption unavailable; registering '" + draft.getName()
                            + "' unsealed");
                } else {
                    ProjectSealer.discardInterrupted(target);
                    AppLog.e(TAG, "Sealing refused for '" + draft.getName() + "'", refused);
                    return new Result(false,
                            "The project could not be secured: " + refused.getMessage(), null);
                }
            } catch (Throwable error) {
                // Sealing started and did not finish: the tree is part plaintext, part ciphertext.
                // It cannot be served and cannot be resumed, so it is discarded.
                ProjectSealer.discardInterrupted(target);
                AppLog.e(TAG, "Sealing failed for '" + draft.getName() + "'", error);
                String detail = error.getMessage() != null ? error.getMessage()
                        : error.getClass().getSimpleName();
                return new Result(false, "The project could not be secured: " + detail, null);
            }
        }

        // 3. Build the permanent record.
        Project registered = new Project();
        registered.setId(projectId);
        registered.setName(draft.getName() != null ? draft.getName().trim() : "Project");
        registered.setDescription(draft.getDescription());
        registered.setIconColor(draft.getIconColor());
        registered.setSourceType(storedType);
        registered.setSourcePath(storedPath);
        registered.setEntryPoint(entryPoint);
        registered.setBaseUrl(draft.getBaseUrl());
        registered.setInlineHtml(draft.getInlineHtml());
        registered.setProjectType(slot.getProjectType());
        registered.setBootstrapRenderingMode(draft.getBootstrapRenderingMode());
        registered.setBootstrapAtStartup(draft.isBootstrapAtStartup());
        registered.setSplashEnabled(draft.isSplashEnabled());
        registered.setBootstrapEnabled(draft.isBootstrapEnabled());
        registered.setSplashDurationMs(draft.getSplashDurationMs());
        registered.setSplashTransition(draft.getSplashTransition());
        registered.setSplashSourceType(draft.getSplashSourceType());
        registered.setSplashSourcePath(draft.getSplashSourcePath());
        registered.setResourceStatus("ready");
        registered.setResourceInitializedAt(System.currentTimeMillis());
        registered.setResourceLastVerifiedAt(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        registered.setCreatedAt(now);
        registered.setUpdatedAt(now);

        // 3b. Refuse to overwrite an existing record. A UUID collision is not a realistic threat,
        //     but the registry is the authority on identity and must never be silently rewritten.
        if (store.findById(projectId) != null) {
            ProjectImporter.deleteRecursively(target);
            AppLog.e(TAG, "Project id collision for " + projectId, null);
            return new Result(false, "A project with this identifier already exists.", null);
        }

        try {
            store.save(registered);
        } catch (Throwable error) {
            ProjectImporter.deleteRecursively(target);
            AppLog.e(TAG, "Unable to persist the registered project", error);
            return new Result(false, "The project could not be saved.", null);
        }

        // 4. Point the slot at the new project. Single-project slots replace the previous one.
        if (slot.isSingleProject()) {
            store.setActiveRole(slot.getProjectType(), registered.getId());
            if (previousProjectId != null && !previousProjectId.isEmpty()
                    && !previousProjectId.equals(registered.getId())) {
                removeReplaced(context, store, previousProjectId);
            }
        }

        AppLog.i(TAG, "Registered '" + registered.getName() + "' as " + slot.getKey()
                + " → " + storedPath);
        return new Result(true, "Registered " + registered.getName(), registered);
    }

    /**
     * Registers a project that ships inside the APK as a ZIP asset.
     *
     * <p>This is the first-run seeding path. The bundled archive is extracted into the app's own
     * project directory ({@code files/RONiLiFEOS/app/projects/<uuid>}) and the registry stores that
     * internal copy, so the project keeps working even when the APK asset is replaced or removed by
     * a later build.</p>
     *
     * @param context     any context
     * @param store       the authoritative registry
     * @param assetPath   ZIP path inside {@code assets/}
     * @param name        display name for the new project
     * @param description short description, may be empty
     * @param iconColor   ARGB icon colour
     */
    public static Result registerBundledZip(Context context, ProjectStore store, String assetPath,
                                            String name, String description, int iconColor) {
        synchronized (REGISTRATION_LOCK) {
            return registerBundledZipLocked(context, store, assetPath, name, description,
                    iconColor);
        }
    }

    private static Result registerBundledZipLocked(Context context, ProjectStore store,
                                                   String assetPath, String name,
                                                   String description, int iconColor) {
        if (context == null || store == null || assetPath == null || assetPath.trim().isEmpty()) {
            return new Result(false, "The bundled project request was incomplete.", null);
        }

        File target = new File(ProjectImporter.projectsRoot(context), UUID.randomUUID().toString());
        if (!target.mkdirs()) {
            return new Result(false, "Unable to create the project directory.", null);
        }

        String entryPoint;
        try (InputStream raw = context.getAssets().open(assetPath.trim())) {
            if (raw == null) {
                throw new IOException("The bundled archive could not be opened.");
            }
            ArchiveExtractor.extract(raw, target);
            entryPoint = ProjectImporter.detectEntryPoint(target);
        } catch (Throwable error) {
            ProjectImporter.deleteRecursively(target);
            AppLog.e(TAG, "Bundled project extraction failed for '" + assetPath + "'", error);
            String detail = error.getMessage() != null ? error.getMessage()
                    : error.getClass().getSimpleName();
            return new Result(false, "The bundled project could not be extracted: " + detail, null);
        }

        // The bundled project is sealed exactly like an imported one: same id, same ordering,
        // same refusal rules. A first-run project must not be the one unencrypted tree on disk.
        String projectId = UUID.randomUUID().toString();
        ProjectManifest.Result manifest = ProjectManifest.validate(target);
        if (!manifest.isValid()) {
            ProjectImporter.deleteRecursively(target);
            return new Result(false, manifest.getMessage(), null);
        }
        if (manifest.getEntryPoint() != null) {
            entryPoint = manifest.getEntryPoint();
        }
        try {
            ProjectImporter.sealProject(target, projectId);
        } catch (IllegalStateException refused) {
            if (!com.roni.library.security.crypto.ProjectVault.isAvailable()) {
                AppLog.w(TAG, "Encryption unavailable; registering bundled project unsealed");
            } else {
                ProjectSealer.discardInterrupted(target);
                return new Result(false,
                        "The bundled project could not be secured: " + refused.getMessage(), null);
            }
        } catch (Throwable error) {
            ProjectSealer.discardInterrupted(target);
            AppLog.e(TAG, "Bundled project sealing failed for '" + assetPath + "'", error);
            String detail = error.getMessage() != null ? error.getMessage()
                    : error.getClass().getSimpleName();
            return new Result(false, "The bundled project could not be secured: " + detail, null);
        }

        Project project = new Project();
        project.setId(projectId);
        project.setName(name != null && !name.trim().isEmpty() ? name.trim() : "Project");
        project.setDescription(description != null ? description : "");
        project.setIconColor(iconColor);
        project.setSourceType(ProjectSourceType.FILE);
        project.setSourcePath(target.getAbsolutePath());
        project.setEntryPoint(entryPoint);
        project.setProjectType(ProjectType.WEB);
        project.setSplashEnabled(true);
        project.setBootstrapEnabled(true);
        project.setSplashDurationMs(Project.DEFAULT_SPLASH_DURATION_MS);
        project.setDefault(true);
        project.setResourceStatus("ready");
        project.setResourceInitializedAt(System.currentTimeMillis());
        project.setResourceLastVerifiedAt(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        try {
            store.save(project);
        } catch (Throwable error) {
            ProjectImporter.deleteRecursively(target);
            AppLog.e(TAG, "Unable to persist the bundled project", error);
            return new Result(false, "The bundled project could not be saved.", null);
        }

        AppLog.i(TAG, "Registered bundled project '" + project.getName() + "' → "
                + target.getAbsolutePath());
        return new Result(true, "Registered " + project.getName(), project);
    }

    /**
     * Saves a Workspace reference without copying anything.
     *
     * <p>Used only by {@link ProjectSlot#WORKSPACE}: the folder stays where it is and the project
     * record holds the persisted tree URI. This is intentionally not a registration.</p>
     */
    public static Result saveReference(Context context, ProjectStore store, Project draft,
                                       ProjectSlot slot, String previousProjectId) {
        if (context == null || store == null || draft == null || slot == null) {
            return new Result(false, "The request was incomplete.", null);
        }
        if (!slot.isReferenceOnly()) {
            return new Result(false, "Only a Workspace stores a live folder reference.", null);
        }
        if (draft.getSourceType() != ProjectSourceType.SAF_FOLDER) {
            return new Result(false, "A Workspace requires a selected folder.", null);
        }
        Uri treeUri = SafPermissionManager.fromSourcePath(draft.getSourcePath());
        if (treeUri == null) {
            return new Result(false, "Select a folder first.", null);
        }
        if (!SafPermissionManager.hasPersisted(context.getContentResolver(), treeUri)) {
            return new Result(false, "Access to that folder was not granted.", null);
        }

        ProjectValidator.Result validation = ProjectValidator.validate(draft, store.getAll());
        if (!validation.isValid()) {
            return new Result(false, validation.getFirstError(), null);
        }

        Project reference = new Project();
        reference.setId(UUID.randomUUID().toString());
        reference.setName(draft.getName() != null ? draft.getName().trim() : "Workspace");
        reference.setDescription(draft.getDescription());
        reference.setIconColor(draft.getIconColor());
        reference.setSourceType(ProjectSourceType.SAF_FOLDER);
        reference.setSourcePath(draft.getSourcePath());
        reference.setEntryPoint(draft.getEntryPoint());
        reference.setProjectType(ProjectType.WORKSPACE);
        reference.setResourceStatus("reference");
        long now = System.currentTimeMillis();
        reference.setCreatedAt(now);
        reference.setUpdatedAt(now);

        try {
            store.save(reference);
        } catch (Throwable error) {
            AppLog.e(TAG, "Unable to save the workspace reference", error);
            return new Result(false, "The workspace could not be saved.", null);
        }

        store.setActiveRole(ProjectType.WORKSPACE, reference.getId());
        if (previousProjectId != null && !previousProjectId.isEmpty()
                && !previousProjectId.equals(reference.getId())) {
            removeReplaced(context, store, previousProjectId);
        }
        return new Result(true, "Workspace saved", reference);
    }

    /**
     * Deletes a project from the registry and removes the copy this app owns for it.
     *
     * <p>The user's original source is never touched: only directories inside the app's own
     * projects root are removed.</p>
     */
    public static boolean deleteProject(Context context, ProjectStore store, String projectId) {
        // Full deletion: registry entry, owned files, cached plaintext and the Keystore key.
        // Delegated so the key cleanup cannot be forgotten by a caller that only knows about
        // the registry.
        return ProjectDeletionService.delete(context, store, projectId);
    }

    /**
     * Archives a replaced project and removes its registry entry.
     *
     * <p>An update must not destroy the version that was working. The previous tree is moved into
     * {@code app/backups/<id>-<timestamp>} instead of being deleted, so a registration that turns
     * out to be broken can be rolled back by re-registering the archived copy. The archive is
     * pruned to the newest {@link #MAX_BACKUPS} entries so it cannot grow without bound.</p>
     */
    private static void removeReplaced(Context context, ProjectStore store, String projectId) {
        Project previous = store.findById(projectId);
        if (previous == null) {
            return;
        }
        String path = previous.getSourcePath();
        store.delete(projectId);
        // The replaced project's plaintext must not survive in RAM under a stale id.
        ProjectVaultRuntime.onProjectUpdated(projectId);
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        try {
            File root = ProjectImporter.projectsRoot(context).getCanonicalFile();
            File candidate = new File(path).getCanonicalFile();
            // Only touch directories this app created. A SAF folder or a user file is never
            // inside the projects root, so the check keeps the user's own data safe.
            if (!candidate.getPath().startsWith(root.getPath() + File.separator)) {
                return;
            }
            File backups = com.roni.life.os.app.core.StorageLayout.backupsDir(context);
            if (!backups.isDirectory() && !backups.mkdirs()) {
                AppLog.w(TAG, "No backups directory; removing the replaced project instead");
                ProjectImporter.deleteRecursively(candidate);
                return;
            }
            File archive = new File(backups, projectId + "-" + System.currentTimeMillis());
            if (!candidate.renameTo(archive)) {
                // Different volume, or a file still open: copy then delete.
                copyDirectory(candidate, archive);
                ProjectImporter.deleteRecursively(candidate);
            }
            writeBackupManifest(archive, previous);
            pruneBackups(backups);
        } catch (Throwable error) {
            AppLog.w(TAG, "Unable to archive the replaced project: " + error.getMessage());
        }
    }

    /**
     * Writes the registry record beside the archived files so a restore is self-contained.
     *
     * <p>Without it the backup would be a directory of ciphertext with no record of which project
     * it belonged to, and {@link ProjectRestoreService} could not re-register it.</p>
     */
    private static void writeBackupManifest(File archive, Project project) {
        if (archive == null || project == null) {
            return;
        }
        try {
            if (!archive.isDirectory() && !archive.mkdirs()) {
                return;
            }
            File manifest = new File(archive, ProjectRestoreService.BACKUP_MANIFEST);
            FileOutputStream out = new FileOutputStream(manifest);
            try {
                out.write(project.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (Throwable error) {
            AppLog.w(TAG, "Unable to write the backup manifest: " + error.getMessage());
        }
    }

    /** Keeps only the newest {@link #MAX_BACKUPS} archived projects. */
    private static void pruneBackups(File backups) {        File[] entries = backups.listFiles();
        if (entries == null || entries.length <= MAX_BACKUPS) {
            return;
        }
        java.util.Arrays.sort(entries, new java.util.Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });
        for (int i = MAX_BACKUPS; i < entries.length; i++) {
            ProjectImporter.deleteRecursively(entries[i]);
        }
    }

    /** What the copy step produced. */
    private static final class CopyOutcome {        final ProjectSourceType sourceType;
        final String sourcePath;
        final String entryPoint;

        CopyOutcome(ProjectSourceType sourceType, String sourcePath, String entryPoint) {
            this.sourceType = sourceType;
            this.sourcePath = sourcePath;
            this.entryPoint = entryPoint;
        }
    }

    private static CopyOutcome copySource(Context context, Project draft, File target)
            throws Exception {
        ProjectSourceType type = draft.getSourceType();
        String entry = draft.getEntryPoint() != null && !draft.getEntryPoint().trim().isEmpty()
                ? draft.getEntryPoint().trim() : "index.html";

        switch (type) {
            case ZIP_ASSET: {
                String assetPath = draft.getSourcePath() != null
                        ? draft.getSourcePath().trim() : "";
                if (assetPath.isEmpty()) {
                    throw new IOException("The bundled archive path is missing.");
                }
                try (InputStream raw = context.getAssets().open(assetPath)) {
                    if (raw == null) {
                        throw new IOException("The bundled archive could not be opened.");
                    }
                    ArchiveExtractor.extract(raw, target);
                }
                return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                        ProjectImporter.detectEntryPoint(target));
            }
            case ASSET: {
                String assetPath = draft.getSourcePath() != null
                        ? draft.getSourcePath().trim() : "";
                if (assetPath.isEmpty()) {
                    throw new IOException("The bundled asset path is missing.");
                }
                int copied = copyAssetTree(context.getAssets(), assetPath, target, 0, new int[]{0});
                if (copied == 0) {
                    throw new IOException("The bundled asset is empty.");
                }
                return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                        ProjectImporter.detectEntryPoint(target));
            }
            case REMOTE_URL: {
                String url = draft.getSourcePath() != null ? draft.getSourcePath().trim() : "";
                if (!isHttpUrl(url)) {
                    throw new IOException("The remote URL must start with http:// or https://");
                }
                // A remote project has no local copy; the URL is the source.
                ProjectImporter.deleteRecursively(target);
                return new CopyOutcome(ProjectSourceType.REMOTE_URL, url, entry);
            }
            case INLINE_HTML: {
                String html = draft.getInlineHtml();
                if (html == null || html.trim().isEmpty()) {
                    throw new IOException("The inline HTML is empty.");
                }
                File index = new File(target, "index.html");
                try (OutputStream out = new FileOutputStream(index)) {
                    out.write(html.getBytes("UTF-8"));
                }
                return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                        "index.html");
            }
            case ZIP_FILE: {
                Uri uri = SafPermissionManager.fromSourcePath(draft.getSourcePath());
                if (uri == null) {
                    throw new IOException("The archive location is missing.");
                }
                try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
                    if (raw == null) {
                        throw new IOException("The archive could not be opened.");
                    }
                    ArchiveExtractor.extract(raw, target);
                }
                return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                        ProjectImporter.detectEntryPoint(target));
            }
            case SAF_FOLDER: {
                Uri treeUri = SafPermissionManager.fromSourcePath(draft.getSourcePath());
                if (treeUri == null) {
                    throw new IOException("The folder location is missing.");
                }
                if (!SafPermissionManager.hasPersisted(context.getContentResolver(), treeUri)) {
                    throw new IOException("Access to the folder was revoked. Select it again.");
                }
                int copied = copySafTree(context, treeUri, target);
                if (copied == 0) {
                    throw new IOException("The selected folder is empty.");
                }
                return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                        ProjectImporter.detectEntryPoint(target));
            }
            case FILE: {
                File source = new File(draft.getSourcePath() != null ? draft.getSourcePath() : "");
                if (!source.exists()) {
                    throw new IOException("The source folder no longer exists.");
                }
                if (source.isDirectory()) {
                    copyDirectory(source, target);
                    return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                            ProjectImporter.detectEntryPoint(target));
                }
                File destination = new File(target, source.getName());
                copyFile(source, destination);
                return new CopyOutcome(ProjectSourceType.FILE, target.getAbsolutePath(),
                        source.getName());
            }
            default:
                throw new IOException("This source type cannot be registered.");
        }
    }

    private static boolean isHttpUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    // ==================== APK asset copy ====================

    /**
     * Copies an asset folder — or a single asset file — into {@code target}.
     *
     * <p>{@link AssetManager#list(String)} returns an empty array for a file, so a leaf is copied
     * with {@link AssetManager#open(String)}. Depth and file count are capped for the same reason
     * the archive extractor caps them: a runaway tree must not fill the device.</p>
     *
     * @return the number of files copied
     */
    private static int copyAssetTree(AssetManager assets, String assetPath, File target,
                                     int depth, int[] counter) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("The bundled asset is nested too deeply.");
        }
        String[] children;
        try {
            children = assets.list(assetPath);
        } catch (IOException error) {
            throw new IOException("The bundled asset could not be read: " + assetPath, error);
        }
        if (children == null || children.length == 0) {
            try (InputStream in = assets.open(assetPath);
                 OutputStream out = new FileOutputStream(target)) {
                copyStream(in, out);
            }
            counter[0]++;
            return 1;
        }
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("Unable to create " + target.getName());
        }
        int copied = 0;
        for (String child : children) {
            if (counter[0] >= MAX_FILES) {
                throw new IOException("The bundled asset contains too many files.");
            }
            String childPath = assetPath.isEmpty() ? child : assetPath + "/" + child;
            copied += copyAssetTree(assets, childPath, new File(target, child), depth + 1, counter);
        }
        return copied;
    }

    // ==================== File system copy ====================

    private static void copyDirectory(File source, File target) throws IOException {
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File destination = new File(target, child.getName());
            if (child.isDirectory()) {
                if (!destination.exists() && !destination.mkdirs()) {
                    throw new IOException("Unable to create " + destination.getName());
                }
                copyDirectory(child, destination);
            } else {
                copyFile(child, destination);
            }
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            copyStream(in, out);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    // ==================== SAF tree copy ====================

    private static int copySafTree(Context context, Uri treeUri, File target) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId);
        return copySafChildren(resolver, treeUri, childrenUri, target, 0, new int[]{0});
    }

    private static int copySafChildren(ContentResolver resolver, Uri treeUri, Uri childrenUri,
                                       File target, int depth, int[] counter) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("The folder is nested too deeply.");
        }
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        int copied = 0;
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return 0;
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (documentId == null || name == null || !isSafeName(name)) {
                    continue;
                }
                if (counter[0] >= MAX_FILES) {
                    throw new IOException("The folder contains too many files.");
                }
                File destination = new File(target, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!destination.exists() && !destination.mkdirs()) {
                        throw new IOException("Unable to create " + name);
                    }
                    Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                            treeUri, documentId);
                    copied += copySafChildren(resolver, treeUri, childUri, destination,
                            depth + 1, counter);
                } else {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, documentId);
                    try (InputStream in = resolver.openInputStream(documentUri)) {
                        if (in == null) {
                            continue;
                        }
                        try (OutputStream out = new FileOutputStream(destination)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    counter[0]++;
                    copied++;
                }
            }
        } catch (SecurityException error) {
            throw new IOException("Access to the folder was revoked.", error);
        }
        return copied;
    }

    /** Rejects names that could escape the target directory. */
    private static boolean isSafeName(String name) {
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return false;
        }
        return name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    /**
     * Lists the projects that belong to {@code slot}, newest first.
     *
     * <p>Used by the management screens so a slot never shows another slot's projects.</p>
     */
    public static List<Project> projectsOf(List<Project> all, ProjectSlot slot) {
        List<Project> matches = new java.util.ArrayList<>();
        if (all == null || slot == null) {
            return matches;
        }
        for (Project project : all) {
            if (project != null && project.getProjectType() == slot.getProjectType()) {
                matches.add(project);
            }
        }
        return matches;
    }
}
