package com.roni.life.os.app.core;

import android.content.Context;
import android.content.res.AssetManager;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.project.ProjectType;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * First-launch storage initialisation for RONiLiFEOS.
 *
 * <p>Creates the complete tree under {@code Android/data/<package>/files/RONiLiFEOS}, copies the
 * template bundle from {@code assets/ronilifeos/} into it, generates the default project and
 * records an initialisation marker. Runs before the runtime bootstrap, so a half-built tree can
 * never reach the WebView host.</p>
 *
 * <p><b>Idempotent by construction.</b> Every step is "create if missing" and every file copy is
 * skipped when the destination already exists, so a second launch touches nothing the user may
 * have edited. The marker file is written last and atomically: an interrupted run leaves no
 * marker, and the next launch simply repeats the same safe steps.</p>
 */
public final class RONiLiFEOSStorageInitializer {

    private static final String TAG = "StorageInit";

    /** Template bundle inside the APK. Mirrors the storage tree one-to-one. */
    public static final String ASSET_ROOT = "ronilifeos";

    /** Version of the initialisation contract; see {@link StorageSpec#INIT_VERSION}. */
    public static final int INIT_VERSION = StorageSpec.INIT_VERSION;

    private static final int MAX_ASSET_DEPTH = 12;
    private static final String TEMP_SUFFIX = ".tmp";

    private RONiLiFEOSStorageInitializer() {
    }

    /** Outcome of one initialisation pass. */
    public static final class Result {

        private final boolean success;
        private final boolean firstRun;
        private final boolean recovered;
        private final int version;
        private final int copiedFiles;
        private final String message;
        private final long durationMs;
        private final List<String> missing;

        Result(boolean success, boolean firstRun, boolean recovered, int version, int copiedFiles,
               String message, long durationMs, List<String> missing) {
            this.success = success;
            this.firstRun = firstRun;
            this.recovered = recovered;
            this.version = version;
            this.copiedFiles = copiedFiles;
            this.message = message != null ? message : "";
            this.durationMs = durationMs;
            this.missing = missing != null ? missing : new ArrayList<String>();
        }

        public boolean isSuccess() {
            return success;
        }

        /** True when no marker existed — a clean install or an interrupted first run. */
        public boolean isFirstRun() {
            return firstRun;
        }

        /** True when the marker existed but required paths had to be recreated. */
        public boolean isRecovered() {
            return recovered;
        }

        public int getVersion() {
            return version;
        }

        public int getCopiedFiles() {
            return copiedFiles;
        }

        public String getMessage() {
            return message;
        }

        public long getDurationMs() {
            return durationMs;
        }

        /** Required paths that are still missing; empty on success. */
        public List<String> getMissing() {
            return missing;
        }
    }

    // ==================== Entry points ====================

    /**
     * Runs the full initialisation pass.
     *
     * @param store registry used to register the default project; may be {@code null} when the
     *              caller only needs the filesystem tree
     */
    public static Result initialize(Context context, ProjectStore store) {
        long startedAt = System.currentTimeMillis();
        if (context == null) {
            return new Result(false, false, false, INIT_VERSION, 0,
                    "No context supplied.", elapsed(startedAt), null);
        }
        Context app = context.getApplicationContext();

        // ১. কাঠামো তৈরি (এবং পুরোনো ইনস্টল হলে একবার স্থানান্তর)।
        StorageLayout.provision(app);
        File root = StorageLayout.root(app);

        StorageInitState previous = readState(app);
        boolean firstRun = previous == null || !previous.isInitialized();
        boolean upgrade = previous != null && previous.getVersion() < INIT_VERSION;

        // ২. টেমপ্লেট কপি — বিদ্যমান ফাইল কখনো ওভাররাইট হয় না।
        int copied;
        try {
            copied = copyAssetTree(app, ASSET_ROOT, root, 0);
        } catch (IOException e) {
            AppLog.e(TAG, "Template copy failed", e);
            return new Result(false, firstRun, false, INIT_VERSION, 0,
                    "Template copy failed: " + e.getMessage(), elapsed(startedAt),
                    collectMissing(root));
        }

        // ৩. ডিফল্ট প্রজেক্ট তৈরি ও সক্রিয় করা।
        boolean projectReady = ensureDefaultProject(app, store);

        // ৪. যাচাই — কিছু বাকি থাকলে মার্কার লেখা হয় না, পরের লঞ্চে আবার চেষ্টা হবে।
        List<String> missing = collectMissing(root);
        if (!missing.isEmpty()) {
            AppLog.w(TAG, "Storage incomplete: " + missing.size() + " path(s) missing");
            return new Result(false, firstRun, false, INIT_VERSION, copied,
                    "Storage is incomplete: " + join(missing), elapsed(startedAt), missing);
        }
        if (!projectReady) {
            return new Result(false, firstRun, false, INIT_VERSION, copied,
                    "The default project could not be registered.", elapsed(startedAt), missing);
        }

        // ৫. স্ট্যাটাস সংরক্ষণ (atomic) — এটাই "initialized" এর একমাত্র প্রমাণ।
        boolean recovered = !firstRun && (copied > 0 || upgrade);
        try {
            writeState(app, new StorageInitState(true, INIT_VERSION, firstRun,
                    System.currentTimeMillis(), StorageLayout.LAYOUT_VERSION,
                    root.getAbsolutePath(), copied));
        } catch (IOException e) {
            AppLog.e(TAG, "Unable to write the initialisation marker", e);
            return new Result(false, firstRun, false, INIT_VERSION, copied,
                    "Unable to save the initialisation status: " + e.getMessage(),
                    elapsed(startedAt), missing);
        }

        long duration = elapsed(startedAt);
        AppLog.i("Storage initialised · v" + INIT_VERSION + " · firstRun=" + firstRun
                + " · copied=" + copied + " · " + duration + "ms");
        return new Result(true, firstRun, recovered, INIT_VERSION, copied,
                firstRun ? "Storage initialised." : "Storage verified.", duration, missing);
    }

    /** Convenience overload for callers that only need the tree. */
    public static Result initialize(Context context) {
        return initialize(context, null);
    }

    /**
     * Registers the default project in {@code store} if it is not there yet and points the
     * active-project setting at it.
     *
     * <p>Separate from {@link #initialize} because the authoritative store only exists after the
     * runtime bootstrap; calling this again with that store keeps the registry in step without
     * repeating any file work.</p>
     */
    public static boolean ensureRegistered(Context context, ProjectStore store) {
        if (context == null || store == null) {
            return false;
        }
        return ensureDefaultProject(context.getApplicationContext(), store);
    }

    /**
     * Re-runs initialisation and re-registers the default project.
     *
     * <p>Used when the registry is empty at runtime — an interrupted first run, a cleared
     * key-value store, or a storage repair. Safe to call repeatedly: every step is
     * "create if missing" and no user file is overwritten.</p>
     */
    public static Result repair(Context context, ProjectStore store) {
        Result result = initialize(context, store);
        if (store != null) {
            ensureRegistered(context, store);
        }
        return result;
    }

    /** True when the marker exists and matches the current contract version. */
    public static boolean isInitialized(Context context) {
        StorageInitState state = readState(context);
        return state != null && state.isInitialized() && state.getVersion() >= INIT_VERSION;
    }

    // ==================== Default project ====================

    private static boolean ensureDefaultProject(Context context, ProjectStore store) {
        File dir = StorageLayout.defaultProjectDir(context);
        StorageLayout.ensureProjectDirs(context, StorageSpec.DEFAULT_PROJECT_ID);

        // The manifest is part of the template bundle; write a minimal one only if it is absent
        // (for example when the APK was built without the template).
        File manifest = new File(dir, "manifest.json");
        if (!manifest.isFile() || manifest.length() == 0L) {
            try {
                writeAtomically(manifest, minimalManifest());
            } catch (IOException e) {
                AppLog.w(TAG, "Unable to write the default manifest: " + e.getMessage());
                return false;
            }
        }

        if (store == null) {
            return true;
        }

        // The seeded project is a first-run convenience, never a policy. It is created only
        // when the registry is completely empty, so deleting it cannot resurrect it on the
        // next launch and steal the default flag from the project the user chose.
        Project existing = store.findById(StorageSpec.DEFAULT_PROJECT_ID);
        if (existing == null && store.count() == 0) {
            Project project = new Project();
            project.setId(StorageSpec.DEFAULT_PROJECT_ID);
            project.setName("Default project");
            project.setDescription("Generated on first launch. Edit or replace it freely.");
            project.setProjectType(ProjectType.WEB);
            project.setSourceType(ProjectSourceType.FILE);
            project.setSourcePath(dir.getAbsolutePath());
            project.setEntryPoint("ui/index.html");
            project.setIconColor(0xFF6C5CE7);
            project.setSplashEnabled(true);
            project.setBootstrapEnabled(true);
            project.setSplashDurationMs(Project.DEFAULT_SPLASH_DURATION_MS);
            project.setDefault(true);
            try {
                store.save(project);
            } catch (Throwable error) {
                AppLog.e(TAG, "Unable to register the default project", error);
                return false;
            }
            if (store.findById(StorageSpec.DEFAULT_PROJECT_ID) == null) {
                // A backend that is not ready yet can drop the write; retry once before failing.
                try {
                    store.save(project);
                } catch (Throwable error) {
                    AppLog.e(TAG, "Default project registration retry failed", error);
                }
            }
            if (store.findById(StorageSpec.DEFAULT_PROJECT_ID) == null) {
                AppLog.w(TAG, "Default project is still missing after registration");
                return false;
            }
            AppLog.i("Default project registered at " + dir.getAbsolutePath());
        }

        // Never overwrite an existing selection. The previous implementation rewrote
        // defaultProjectId to the seeded id on every launch, which is exactly what made an
        // explicitly selected default project revert after a restart.
        AppConfig config = store.loadConfig();
        boolean changed = false;
        boolean nothingSelected = config.getDefaultWebProjectId().isEmpty()
                && config.getDefaultProjectId().isEmpty();
        if (nothingSelected && store.findById(StorageSpec.DEFAULT_PROJECT_ID) != null) {
            config.setDefaultWebProjectId(StorageSpec.DEFAULT_PROJECT_ID);
            config.setDefaultProjectId(StorageSpec.DEFAULT_PROJECT_ID);
            changed = true;
        }
        if (!config.isSeeded()) {
            config.setSeeded(true);
            changed = true;
        }
        if (changed) {
            store.saveConfig(config);
        }
        return true;
    }

    private static String minimalManifest() {
        return "{\n"
                + "  \"id\": \"" + StorageSpec.DEFAULT_PROJECT_ID + "\",\n"
                + "  \"name\": \"Default project\",\n"
                + "  \"version\": 1,\n"
                + "  \"entryPoint\": \"ui/index.html\",\n"
                + "  \"generated\": true\n"
                + "}\n";
    }

    // ==================== Template copy ====================

    /**
     * Copies {@code assetPath} into {@code target}, recursing through directories.
     *
     * @return number of files actually written (existing destinations are skipped)
     */
    private static int copyAssetTree(Context context, String assetPath, File target, int depth)
            throws IOException {
        if (depth > MAX_ASSET_DEPTH) {
            AppLog.w(TAG, "Asset tree too deep at " + assetPath);
            return 0;
        }
        AssetManager assets = context.getAssets();
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            return copyAssetFile(assets, assetPath, target);
        }
        if (!target.isDirectory() && !target.mkdirs() && !target.isDirectory()) {
            throw new IOException("Cannot create " + target.getAbsolutePath());
        }
        int copied = 0;
        for (String child : children) {
            copied += copyAssetTree(context, assetPath + "/" + child, new File(target, child),
                    depth + 1);
        }
        return copied;
    }

    private static int copyAssetFile(AssetManager assets, String assetPath, File target)
            throws IOException {
        if (target.isFile() && target.length() > 0L) {
            return 0; // Never overwrite: the user may have edited this file.
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        InputStream in;
        try {
            in = assets.open(assetPath);
        } catch (java.io.FileNotFoundException e) {
            // An empty directory in the bundle lists as a leaf but cannot be opened.
            return 0;
        }
        try {
            writeAtomically(target, in);
        } finally {
            closeQuietly(in);
        }
        return 1;
    }

    // ==================== Marker ====================

    private static StorageInitState readState(Context context) {
        File file = StorageLayout.initializationFile(context);
        if (!file.isFile()) {
            return null;
        }
        return StorageInitState.decode(readText(file));
    }

    private static void writeState(Context context, StorageInitState state) throws IOException {
        writeAtomically(StorageLayout.initializationFile(context), state.encode());
    }

    // ==================== Validation ====================

    /** Required paths that are absent or empty; empty list means the tree is complete. */
    private static List<String> collectMissing(File root) {
        List<String> missing = new ArrayList<>();
        for (String dir : StorageSpec.REQUIRED_DIRS) {
            if (!new File(root, dir).isDirectory()) {
                missing.add(dir);
            }
        }
        for (String file : StorageSpec.REQUIRED_FILES) {
            File target = new File(root, file);
            if (!target.isFile() || target.length() == 0L) {
                missing.add(file);
            }
        }
        return missing;
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    // ==================== File helpers ====================

    private static void writeAtomically(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        OutputStream out = new FileOutputStream(temp);
        try {
            out.write(content.getBytes("UTF-8"));
            out.flush();
        } finally {
            closeQuietly(out);
        }
        promote(temp, target);
    }

    private static void writeAtomically(File target, InputStream source) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        OutputStream out = new FileOutputStream(temp);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            closeQuietly(out);
        }
        promote(temp, target);
    }

    /** Renames a fully written temp file over its destination. */
    private static void promote(File temp, File target) throws IOException {
        if (temp.renameTo(target)) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Cannot replace " + target.getAbsolutePath());
        }
    }

    private static String readText(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[(int) Math.min(file.length(), 64L * 1024L)];
            int read = in.read(buffer);
            return read > 0 ? new String(buffer, 0, read, "UTF-8") : "";
        } catch (IOException e) {
            AppLog.w(TAG, "Cannot read " + file.getName() + ": " + e.getMessage());
            return "";
        } finally {
            closeQuietly(in);
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful to do on close failure.
            }
        }
    }

    private static void closeQuietly(OutputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful to do on close failure.
            }
        }
    }
}
