package com.roni.life.os.app.project;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.util.AppLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * প্রজেক্ট ও অ্যাপ-কনফিগারেশনের একমাত্র সত্যের উৎস (single source of truth)।
 *
 * <p>সব ডেটা {@link KeyValueBackend}-এ সংরক্ষিত হয়, যা ডিফল্টভাবে
 * NativeStorageEngine-এর KeyValueStore ব্যবহার করে।</p>
 */
public class ProjectStore {

    private static final String KEY_INDEX = "projects.index";
    private static final String KEY_PROJECT_PREFIX = "project.";
    private static final String KEY_APP_CONFIG = "app.config";
    private static final String INDEX_SEPARATOR = ",";

    private final KeyValueBackend backend;

    /** Optional hook fired after any mutation so the on-disk mirror can refresh. */
    private Runnable changeListener;

    public ProjectStore(KeyValueBackend backend) {
        this.backend = backend;
    }

    /**
     * Registers a listener invoked after every registry or config mutation.
     *
     * <p>Used by {@code StorageMirror} to keep the JSON view of the storage tree in step with
     * the authoritative store. The listener must be cheap and must not mutate the store, or
     * it will recurse.</p>
     */
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Exposes the storage backend so sibling stores (for example
     * {@code ProjectUIConfigStore}) can persist their own namespaced keys without
     * duplicating the storage wiring.
     */
    public KeyValueBackend getBackend() {
        return backend;
    }

    // ==================== Project CRUD ====================

    public List<Project> getAll() {
        List<Project> projects = new ArrayList<>();
        for (String id : readIndex()) {
            Project project = findById(id);
            if (project != null) {
                projects.add(project);
            }
        }
        Collections.sort(projects, DISPLAY_ORDER);
        return projects;
    }

    public Project findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        String raw = backend.get(KEY_PROJECT_PREFIX + id, null);
        if (raw == null) {
            return null;
        }
        return Project.decode(raw);
    }

    public boolean exists(String id) {
        return findById(id) != null;
    }

    public int count() {
        return readIndex().size();
    }

    /** নতুন প্রজেক্ট সংরক্ষণ বা বিদ্যমান প্রজেক্ট হালনাগাদ করে। */
    public void save(Project project) {
        if (project == null || project.getId() == null) {
            return;
        }
        project.touch();
        backend.put(KEY_PROJECT_PREFIX + project.getId(), project.encode());

        List<String> index = readIndex();
        if (!index.contains(project.getId())) {
            index.add(project.getId());
            writeIndex(index);
        }
        // A saved record is the authority on its own default flag. Routing every save through
        // the single default writer is what keeps the flag and both config pointers in step,
        // whichever screen performed the edit.
        syncDefault(project);
        notifyChanged();
    }

    /**
     * Applies a project's default flag in both directions.
     *
     * <p>Promotion goes through {@link #setDefaultProject(String)} so the flag, the
     * authoritative pointer and the legacy mirror are written together. Demotion clears the
     * pointers when they still name this project, so an unticked checkbox really removes the
     * project from the startup path.</p>
     *
     * @param project the project that was just saved
     */
    public void syncDefault(Project project) {
        if (project == null || project.getId() == null) {
            return;
        }
        if (project.isDefault()) {
            setDefaultProject(project.getId());
            return;
        }
        AppConfig config = loadConfig();
        if (project.getId().equals(config.getDefaultWebProjectId())
                || project.getId().equals(config.getDefaultProjectId())) {
            clearDefault();
        }
    }

    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        boolean removed = backend.remove(KEY_PROJECT_PREFIX + id);
        List<String> index = readIndex();
        if (index.remove(id)) {
            writeIndex(index);
        }
        AppConfig config = loadConfig();
        boolean configChanged = clearRolePointers(config, id);
        if (configChanged) {
            saveConfig(config);
        }
        notifyChanged();
        return removed;
    }

    /**
     * সাম্প্রতিক খোলা প্রজেক্টগুলো সর্বশেষ-আগে ফেরত দেয়।
     *
     * @param limit সর্বোচ্চ কতটি; {@code <= 0} হলে সব
     */
    public List<Project> getRecent(int limit) {
        List<Project> opened = new ArrayList<>();
        for (Project project : getAll()) {
            if (project.getLastOpenedAt() > 0L) {
                opened.add(project);
            }
        }
        Collections.sort(opened, new Comparator<Project>() {
            @Override
            public int compare(Project left, Project right) {
                return Long.compare(right.getLastOpenedAt(), left.getLastOpenedAt());
            }
        });
        if (limit > 0 && opened.size() > limit) {
            return new ArrayList<>(opened.subList(0, limit));
        }
        return opened;
    }

    /**
     * শুধুমাত্র রেজিস্ট্রি এন্ট্রি সরায় — ব্যবহারকারীর প্রজেক্ট ফাইল কখনো মুছে ফেলে না।
     *
     * <p>{@link #delete(String)}-এর থেকে আলাদা: এখানে প্রজেক্টের ডেটা ও ফাইল অক্ষত থাকে,
     * শুধু ড্যাশবোর্ডের তালিকা থেকে সরে যায়।</p>
     *
     * @return {@code true} যদি এন্ট্রি সরানো হয়
     */
    public boolean removeFromRegistry(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        List<String> index = readIndex();
        boolean removed = index.remove(id);
        if (removed) {
            writeIndex(index);
        }
        AppConfig config = loadConfig();
        boolean configChanged = clearRolePointers(config, id);
        if (configChanged) {
            saveConfig(config);
        }
        return removed;
    }

    /**
     * Drops every role pointer that referenced {@code id}.
     *
     * <p>Only configuration is rewritten. The project record itself is left untouched, so
     * unregistering a project never silently destroys its data.</p>
     */
    private boolean clearRolePointers(AppConfig config, String id) {
        boolean changed = false;
        if (id.equals(config.getDefaultProjectId())) {
            config.setDefaultProjectId("");
            changed = true;
        }
        if (id.equals(config.getLastOpenedProjectId())) {
            config.setLastOpenedProjectId("");
            changed = true;
        }
        if (id.equals(config.getActiveSplashProjectId())) {
            config.setActiveSplashProjectId("");
            changed = true;
        }
        if (id.equals(config.getActiveBootstrapProjectId())) {
            config.setActiveBootstrapProjectId("");
            changed = true;
        }
        if (id.equals(config.getDefaultWebProjectId())) {
            config.setDefaultWebProjectId("");
            changed = true;
        }
        return changed;
    }

    /** বিদ্যমান প্রজেক্টের একটি কপি তৈরি করে এবং সংরক্ষণ করে। */
    public Project duplicate(String id, String newName) {
        Project source = findById(id);
        if (source == null) {
            return null;
        }
        Project copy = ProjectFactory.duplicate(source, newName);
        save(copy);
        return copy;
    }

    /** Outcome of a default-project selection. */
    public static final class DefaultResult {
        private final boolean success;
        private final String message;
        private final Project project;

        DefaultResult(boolean success, String message, Project project) {
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

        /** The project that is now the default, or {@code null} on failure. */
        public Project getProject() {
            return project;
        }
    }

    /**
     * Selects the default application project — the only writer of the default state.
     *
     * <p>The operation is atomic from the registry's point of view: the previous flag is
     * cleared, the new flag is set, and both config pointers are written before the method
     * returns. A project that does not exist, or that cannot be hosted by
     * {@code ProjectActivity}, is rejected with a message instead of being stored and silently
     * ignored later.</p>
     *
     * @param id the project to make default, or {@code null}/empty to clear the selection
     */
    public DefaultResult setDefaultProject(String id) {
        String normalized = id != null ? id.trim() : "";
        if (normalized.isEmpty()) {
            clearDefault();
            return new DefaultResult(true, "No default project is selected.", null);
        }

        Project target = findById(normalized);
        if (target == null) {
            AppLog.w("SET_DEFAULT: rejected — project does not exist id=" + normalized);
            return new DefaultResult(false, "That project no longer exists.", null);
        }
        if (!target.getProjectType().canBeDefaultApplication()) {
            AppLog.w("SET_DEFAULT: rejected — type " + target.getProjectType()
                    + " cannot be the default application project id=" + normalized);
            return new DefaultResult(false,
                    "A " + target.getProjectType().getLabel()
                            + " project cannot be the default application project.", null);
        }
        if (!DefaultProjectResolver.isLaunchable(target)) {
            AppLog.w("SET_DEFAULT: rejected — project is not launchable id=" + normalized);
            return new DefaultResult(false,
                    "That project has no usable source and cannot be launched.", null);
        }

        AppConfig config = loadConfig();
        String previousId = config.getDefaultWebProjectId();

        // Clear every other flag first, then set the selected one. Both writes go straight to
        // the backend so no intermediate save() can re-enter this method.
        for (Project project : getAll()) {
            boolean shouldBeDefault = project.getId().equals(normalized);
            if (project.isDefault() != shouldBeDefault) {
                project.setDefault(shouldBeDefault);
                backend.put(KEY_PROJECT_PREFIX + project.getId(), project.encode());
            }
        }

        config.setDefaultWebProjectId(normalized);
        config.setDefaultProjectId(normalized);
        saveConfig(config);

        AppConfig persisted = loadConfig();
        Project resolved = defaultProject();
        AppLog.i("SET_DEFAULT: selectedId=" + normalized
                + " previousId=" + (previousId.isEmpty() ? "none" : previousId)
                + " persistedId=" + (persisted.getDefaultWebProjectId().isEmpty()
                        ? "none" : persisted.getDefaultWebProjectId())
                + " resolvedId=" + (resolved != null ? resolved.getId() : "none"));

        if (resolved == null || !normalized.equals(resolved.getId())) {
            return new DefaultResult(false,
                    "The default project could not be persisted.", null);
        }
        notifyChanged();
        return new DefaultResult(true, "Default project: " + target.getName(), target);
    }

    /** Clears the default selection in the flag and in both config pointers. */
    public void clearDefault() {
        for (Project project : getAll()) {
            if (project.isDefault()) {
                project.setDefault(false);
                backend.put(KEY_PROJECT_PREFIX + project.getId(), project.encode());
            }
        }
        AppConfig config = loadConfig();
        if (!config.getDefaultWebProjectId().isEmpty()
                || !config.getDefaultProjectId().isEmpty()) {
            config.setDefaultWebProjectId("");
            config.setDefaultProjectId("");
            saveConfig(config);
        }
        AppLog.i("SET_DEFAULT: cleared");
        notifyChanged();
    }

    /**
     * Backwards-compatible entry point.
     *
     * <p>Kept for existing callers; the result is logged by
     * {@link #setDefaultProject(String)} so a rejected selection is never silent.</p>
     */
    public void setDefault(String id) {
        setDefaultProject(id);
    }

    /** প্রজেক্ট খোলার সময় lastOpened ট্র্যাক করে। */
    public void markOpened(String id) {
        Project project = findById(id);
        if (project == null) {
            return;
        }
        project.setLastOpenedAt(System.currentTimeMillis());
        backend.put(KEY_PROJECT_PREFIX + project.getId(), project.encode());

        AppConfig config = loadConfig();
        config.setLastOpenedProjectId(id);
        saveConfig(config);
    }

    /**
     * Bootstrap বন্ধ থাকা অবস্থায় কোন প্রজেক্ট খুলতে হবে তা নির্ধারণ করে।
     *
     * <p>Delegates to the authoritative default rule. {@code lastOpenedProjectId} is
     * deliberately not part of the priority: opening a project must never change which project
     * starts.</p>
     */
    public Project resolveStartupProject() {
        return defaultProject();
    }

    // ==================== Role resolution ====================

    /** The active SPLASH project, or {@code null} when none is registered. */
    public Project activeSplash() {
        AppConfig config = loadConfig();
        return ProjectRoleResolver.resolveSplash(getAll(), config.getActiveSplashProjectId()).getProject();
    }

    /** The active BOOTSTRAP project, or {@code null} when none is registered. */
    public Project activeBootstrap() {
        AppConfig config = loadConfig();
        return ProjectRoleResolver.resolveBootstrap(getAll(), config.getActiveBootstrapProjectId()).getProject();
    }

    /** The default WEB application project, or {@code null} when none is registered. */
    public Project defaultWebProject() {
        return defaultProject();
    }

    /**
     * The single authoritative default-project resolver.
     *
     * <p>Delegates to {@link DefaultProjectResolver}: {@code defaultWebProjectId} is the only
     * authoritative value, the legacy flag and mirror are migrated once, and
     * {@code lastOpenedProjectId} is never consulted. When nothing valid is configured this
     * returns {@code null} so startup can show the configuration screen instead of opening a
     * project the user did not choose.</p>
     */
    public Project defaultProject() {
        return DefaultProjectResolver.resolve(this).getProject();
    }

    /** Full resolution, including the failure reason — used by the startup router. */
    public DefaultProjectResolver.Resolution resolveDefaultProject() {
        return DefaultProjectResolver.resolve(this);
    }

    /** True when the active Bootstrap project opted into opening at startup. */
    public boolean isBootstrapAtStartupEnabled() {
        Project bootstrap = activeBootstrap();
        return bootstrap != null && bootstrap.isBootstrapAtStartup();
    }

    /**
     * Selects the active project for a role.
     *
     * <p>The id is validated against the role's type. Passing an id of the wrong type is
     * rejected rather than stored, so a WEB project can never become the Splash project.</p>
     *
     * @return {@code true} when the selection was applied
     */
    public boolean setActiveRole(ProjectType role, String id) {
        if (role == null) {
            return false;
        }
        String normalized = id != null ? id.trim() : "";
        if (!normalized.isEmpty()) {
            Project project = findById(normalized);
            if (project == null || project.getProjectType() != role) {
                return false;
            }
        }
        AppConfig config = loadConfig();
        if (role == ProjectType.SPLASH) {
            config.setActiveSplashProjectId(normalized);
        } else if (role == ProjectType.BOOTSTRAP) {
            config.setActiveBootstrapProjectId(normalized);
        } else if (role == ProjectType.WORKSPACE) {
            // A workspace is a live folder reference, never the default application project.
            config.setActiveWorkspaceProjectId(normalized);
        } else {
            config.setDefaultWebProjectId(normalized);
            config.setDefaultProjectId(normalized);
        }
        saveConfig(config);
        if (role == ProjectType.WEB) {
            applyDefaultFlag(normalized);
        }
        return true;
    }

    public boolean setActiveSplash(String id) {
        return setActiveRole(ProjectType.SPLASH, id);
    }

    public boolean setActiveBootstrap(String id) {
        return setActiveRole(ProjectType.BOOTSTRAP, id);
    }

    public boolean setDefaultWeb(String id) {
        return setActiveRole(ProjectType.WEB, id);
    }

    /**
     * Clears role pointers that no longer resolve to a live project of the right type.
     *
     * <p>Called after a delete or unregister. It only rewrites configuration — project data is
     * never touched, so a stale pointer can never destroy a project.</p>
     *
     * @return {@code true} when anything was repaired
     */
    public boolean clearInvalidReferences() {
        List<Project> projects = getAll();
        AppConfig config = loadConfig();
        boolean changed = false;

        if (!config.getActiveSplashProjectId().isEmpty()
                && !isLiveOfType(projects, config.getActiveSplashProjectId(), ProjectType.SPLASH)) {
            config.setActiveSplashProjectId("");
            changed = true;
        }
        if (!config.getActiveBootstrapProjectId().isEmpty()
                && !isLiveOfType(projects, config.getActiveBootstrapProjectId(), ProjectType.BOOTSTRAP)) {
            config.setActiveBootstrapProjectId("");
            changed = true;
        }
        if (!config.getDefaultWebProjectId().isEmpty()
                && !isLiveOfType(projects, config.getDefaultWebProjectId(), ProjectType.WEB)) {
            config.setDefaultWebProjectId("");
            changed = true;
        }
        if (!config.getActiveWorkspaceProjectId().isEmpty()
                && !isLiveOfType(projects, config.getActiveWorkspaceProjectId(),
                ProjectType.WORKSPACE)) {
            config.setActiveWorkspaceProjectId("");
            changed = true;
        }
        if (!config.getDefaultProjectId().isEmpty()
                && !isLiveOfType(projects, config.getDefaultProjectId(), ProjectType.WEB)) {
            config.setDefaultProjectId("");
            changed = true;
        }
        if (!config.getLastOpenedProjectId().isEmpty()
                && findById(config.getLastOpenedProjectId()) == null) {
            config.setLastOpenedProjectId("");
            changed = true;
        }
        if (changed) {
            saveConfig(config);
        }
        return changed;
    }

    /** Roles that currently have more than one competing project. */
    public List<ProjectRoleResolver.RoleSelection> ambiguousRoles() {
        AppConfig config = loadConfig();
        return ProjectRoleResolver.ambiguousRoles(getAll(),
                config.getActiveSplashProjectId(),
                config.getActiveBootstrapProjectId(),
                config.getDefaultWebProjectId());
    }

    private boolean isLiveOfType(List<Project> projects, String id, ProjectType type) {
        for (Project project : projects) {
            if (project.getId().equals(id)) {
                return project.getProjectType() == type;
            }
        }
        return false;
    }

    private void applyDefaultFlag(String keepId) {
        for (Project project : getAll()) {
            boolean shouldBeDefault = project.getId().equals(keepId);
            if (project.isDefault() != shouldBeDefault) {
                project.setDefault(shouldBeDefault);
                backend.put(KEY_PROJECT_PREFIX + project.getId(), project.encode());
            }
        }
    }

    // ==================== App Config ====================

    public AppConfig loadConfig() {
        String raw = backend.get(KEY_APP_CONFIG, null);
        AppConfig config = AppConfig.decode(raw);
        return config != null ? config : new AppConfig();
    }

    public void saveConfig(AppConfig config) {
        if (config == null) {
            return;
        }
        backend.put(KEY_APP_CONFIG, config.encode());
        notifyChanged();
    }

    // ==================== Internal ====================

    private List<String> readIndex() {
        String raw = backend.get(KEY_INDEX, "");
        List<String> ids = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return ids;
        }
        for (String part : raw.split(INDEX_SEPARATOR)) {
            String id = part.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void writeIndex(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (builder.length() > 0) {
                builder.append(INDEX_SEPARATOR);
            }
            builder.append(id);
        }
        backend.put(KEY_INDEX, builder.toString());
    }

    /** ডিফল্ট প্রজেক্ট আগে, তারপর সর্বশেষ খোলা, তারপর নাম অনুসারে। */
    private static final Comparator<Project> DISPLAY_ORDER = new Comparator<Project>() {
        @Override
        public int compare(Project left, Project right) {
            if (left.isDefault() != right.isDefault()) {
                return left.isDefault() ? -1 : 1;
            }
            if (left.getLastOpenedAt() != right.getLastOpenedAt()) {
                return left.getLastOpenedAt() > right.getLastOpenedAt() ? -1 : 1;
            }
            String leftName = left.getName() != null ? left.getName() : "";
            String rightName = right.getName() != null ? right.getName() : "";
            return leftName.compareToIgnoreCase(rightName);
        }
    };
}
