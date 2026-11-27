package com.roni.life.os.app.project;

import com.roni.life.os.app.util.Codec;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * একটি প্রজেক্টের সম্পূর্ণ ডেটা মডেল।
 *
 * <p>সিরিয়ালাইজেশন ইচ্ছাকৃতভাবে Android-নিরপেক্ষ (URL-encoded key/value) রাখা হয়েছে,
 * যাতে ইউনিট টেস্টে সরাসরি যাচাই করা যায় এবং ভবিষ্যতে ফরম্যাট পরিবর্তন সহজ হয়।</p>
 */
public final class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_SPLASH_DURATION_MS = 1200;
    public static final int MIN_SPLASH_DURATION_MS = 200;
    public static final int MAX_SPLASH_DURATION_MS = 10000;
    public static final int DEFAULT_ICON_COLOR = 0xFF6C5CE7;

    private String id;
    private String name;
    private String description;
    private int iconColor;
    private ProjectSourceType sourceType;
    private String sourcePath;
    private String entryPoint;
    private String baseUrl;
    private String inlineHtml;
    private boolean splashEnabled;
    private boolean bootstrapEnabled;
    private int splashDurationMs;
    private boolean defaultProject;
    private long lastOpenedAt;
    private long createdAt;
    private long updatedAt;

    // ==================== Three-type role system ====================

    /** The role this project plays: SPLASH, BOOTSTRAP or WEB. */
    private ProjectType projectType;
    /** How a BOOTSTRAP project renders. Ignored for SPLASH and WEB. */
    private BootstrapRenderingMode bootstrapRenderingMode;
    /** Whether a BOOTSTRAP project opens automatically during startup. */
    private boolean bootstrapAtStartup;
    /** Source type for a SPLASH project's presentation content. */
    private ProjectSourceType splashSourceType;
    /** Source path for a SPLASH project's presentation content. */
    private String splashSourcePath;
    /** Splash transition: NONE / FADE / SLIDE_UP / SCALE / PULSE. */
    private String splashTransition;

    /** Resource lifecycle summary: not_initialized / initializing / ready / incomplete / failed. */
    private String resourceStatus;
    /** Version of the resource manifest this project expects. */
    private String resourceVersion;
    /** Optional expected SHA-256 of the project archive. */
    private String resourceChecksum;
    private long resourceInitializedAt;
    private long resourceLastVerifiedAt;

    public Project() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.description = "";
        this.iconColor = DEFAULT_ICON_COLOR;
        this.sourceType = ProjectSourceType.ASSET;
        this.sourcePath = "";
        this.entryPoint = "index.html";
        this.baseUrl = "";
        this.inlineHtml = "";
        this.splashEnabled = true;
        this.bootstrapEnabled = true;
        this.splashDurationMs = DEFAULT_SPLASH_DURATION_MS;
        this.defaultProject = false;
        this.lastOpenedAt = 0L;
        this.projectType = ProjectType.WEB;
        this.bootstrapRenderingMode = BootstrapRenderingMode.NATIVE;
        this.bootstrapAtStartup = false;
        this.splashSourceType = ProjectSourceType.ASSET;
        this.splashSourcePath = "";
        this.splashTransition = "FADE";
        this.resourceStatus = "not_initialized";
        this.resourceVersion = "1";
        this.resourceChecksum = "";
        this.resourceInitializedAt = 0L;
        this.resourceLastVerifiedAt = 0L;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null && !id.trim().isEmpty() ? id.trim() : UUID.randomUUID().toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    public int getIconColor() {
        return iconColor;
    }

    public void setIconColor(int iconColor) {
        this.iconColor = iconColor;
    }

    public ProjectSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ProjectSourceType sourceType) {
        this.sourceType = sourceType != null ? sourceType : ProjectSourceType.ASSET;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath != null ? sourcePath.trim() : "";
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint != null ? entryPoint.trim() : "";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
    }

    public String getInlineHtml() {
        return inlineHtml;
    }

    public void setInlineHtml(String inlineHtml) {
        this.inlineHtml = inlineHtml != null ? inlineHtml : "";
    }

    public boolean isSplashEnabled() {
        return splashEnabled;
    }

    public void setSplashEnabled(boolean splashEnabled) {
        this.splashEnabled = splashEnabled;
    }

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    public int getSplashDurationMs() {
        return splashDurationMs;
    }

    public void setSplashDurationMs(int splashDurationMs) {
        this.splashDurationMs = Math.max(MIN_SPLASH_DURATION_MS,
                Math.min(MAX_SPLASH_DURATION_MS, splashDurationMs));
    }

    public boolean isDefault() {
        return defaultProject;
    }

    public void setDefault(boolean defaultProject) {
        this.defaultProject = defaultProject;
    }

    // ==================== Three-type role system ====================

    public ProjectType getProjectType() {
        return projectType != null ? projectType : ProjectType.WEB;
    }

    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType != null ? projectType : ProjectType.WEB;
    }

    public BootstrapRenderingMode getBootstrapRenderingMode() {
        return bootstrapRenderingMode != null ? bootstrapRenderingMode : BootstrapRenderingMode.NATIVE;
    }

    public void setBootstrapRenderingMode(BootstrapRenderingMode mode) {
        this.bootstrapRenderingMode = mode != null ? mode : BootstrapRenderingMode.NATIVE;
    }

    public boolean isBootstrapAtStartup() {
        return bootstrapAtStartup;
    }

    public void setBootstrapAtStartup(boolean bootstrapAtStartup) {
        this.bootstrapAtStartup = bootstrapAtStartup;
    }

    public ProjectSourceType getSplashSourceType() {
        return splashSourceType != null ? splashSourceType : ProjectSourceType.ASSET;
    }

    public void setSplashSourceType(ProjectSourceType splashSourceType) {
        this.splashSourceType = splashSourceType != null ? splashSourceType : ProjectSourceType.ASSET;
    }

    public String getSplashSourcePath() {
        return splashSourcePath;
    }

    public void setSplashSourcePath(String splashSourcePath) {
        this.splashSourcePath = splashSourcePath != null ? splashSourcePath.trim() : "";
    }

    public String getSplashTransition() {
        return splashTransition != null ? splashTransition : "FADE";
    }

    public void setSplashTransition(String splashTransition) {
        this.splashTransition = splashTransition != null ? splashTransition : "FADE";
    }

    /** True when this project is a SPLASH presentation project. */
    public boolean isSplashProject() {
        return getProjectType() == ProjectType.SPLASH;
    }

    /** True when this project is a BOOTSTRAP initialisation project. */
    public boolean isBootstrapProject() {
        return getProjectType() == ProjectType.BOOTSTRAP;
    }

    /** True when this project is a WEB application project. */
    public boolean isWebProject() {
        return getProjectType() == ProjectType.WEB;
    }

    /** True when this BOOTSTRAP project renders through a WebView. */
    public boolean isWebBootstrap() {
        return isBootstrapProject() && getBootstrapRenderingMode().isWeb();
    }

    /**
     * True when this project should open automatically at startup.
     *
     * <p>Only a BOOTSTRAP project with {@code bootstrapAtStartup} enabled qualifies. A SPLASH
     * project is driven by the splash setting, and a WEB project is the default application —
     * neither is ever "auto-opened" through this flag.</p>
     */
    public boolean shouldOpenAtStartup() {
        return isBootstrapProject() && bootstrapAtStartup;
    }

    public long getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(long lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ==================== Resource metadata ====================

    public String getResourceStatus() {
        return resourceStatus;
    }

    public void setResourceStatus(String resourceStatus) {
        this.resourceStatus = resourceStatus != null && !resourceStatus.trim().isEmpty()
                ? resourceStatus.trim() : "not_initialized";
    }

    public String getResourceVersion() {
        return resourceVersion;
    }

    public void setResourceVersion(String resourceVersion) {
        this.resourceVersion = resourceVersion != null && !resourceVersion.trim().isEmpty()
                ? resourceVersion.trim() : "1";
    }

    public String getResourceChecksum() {
        return resourceChecksum;
    }

    public void setResourceChecksum(String resourceChecksum) {
        this.resourceChecksum = resourceChecksum != null ? resourceChecksum.trim() : "";
    }

    public long getResourceInitializedAt() {
        return resourceInitializedAt;
    }

    public void setResourceInitializedAt(long resourceInitializedAt) {
        this.resourceInitializedAt = resourceInitializedAt;
    }

    public long getResourceLastVerifiedAt() {
        return resourceLastVerifiedAt;
    }

    public void setResourceLastVerifiedAt(long resourceLastVerifiedAt) {
        this.resourceLastVerifiedAt = resourceLastVerifiedAt;
    }

    /** @return {@code true} when the project's resources were downloaded and verified once */
    public boolean isResourceReady() {
        return "ready".equalsIgnoreCase(resourceStatus);
    }

    /** সম্পাদনার সময় টাইমস্ট্যাম্প হালনাগাদ করে। */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /** একই কনফিগারেশনের একটি নতুন প্রজেক্ট (নতুন আইডি) তৈরি করে। */
    public Project copy(String newName) {
        Project copy = new Project();
        copy.name = newName != null && !newName.trim().isEmpty() ? newName.trim() : name + " (copy)";
        copy.description = description;
        copy.iconColor = iconColor;
        copy.sourceType = sourceType;
        copy.sourcePath = sourcePath;
        copy.entryPoint = entryPoint;
        copy.baseUrl = baseUrl;
        copy.inlineHtml = inlineHtml;
        copy.splashEnabled = splashEnabled;
        copy.bootstrapEnabled = bootstrapEnabled;
        copy.splashDurationMs = splashDurationMs;
        copy.defaultProject = false;
        copy.lastOpenedAt = 0L;
        copy.projectType = projectType;
        copy.bootstrapRenderingMode = bootstrapRenderingMode;
        // A copy never inherits the startup role: two projects must not both auto-open.
        copy.bootstrapAtStartup = false;
        copy.splashSourceType = splashSourceType;
        copy.splashSourcePath = splashSourcePath;
        copy.splashTransition = splashTransition;
        // A copy is a new project: it owns its own resource directory and must initialize it.
        copy.resourceStatus = "not_initialized";
        copy.resourceVersion = resourceVersion;
        copy.resourceChecksum = resourceChecksum;
        copy.resourceInitializedAt = 0L;
        copy.resourceLastVerifiedAt = 0L;
        copy.createdAt = System.currentTimeMillis();
        copy.updatedAt = copy.createdAt;
        return copy;
    }

    public String encode() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("description", description);
        map.put("iconColor", String.valueOf(iconColor));
        map.put("sourceType", sourceType != null ? sourceType.getKey() : ProjectSourceType.ASSET.getKey());
        map.put("sourcePath", sourcePath);
        map.put("entryPoint", entryPoint);
        map.put("baseUrl", baseUrl);
        map.put("inlineHtml", inlineHtml);
        map.put("splashEnabled", String.valueOf(splashEnabled));
        map.put("bootstrapEnabled", String.valueOf(bootstrapEnabled));
        map.put("splashDurationMs", String.valueOf(splashDurationMs));
        map.put("defaultProject", String.valueOf(defaultProject));
        map.put("lastOpenedAt", String.valueOf(lastOpenedAt));
        map.put("createdAt", String.valueOf(createdAt));
        map.put("updatedAt", String.valueOf(updatedAt));
        map.put("resourceStatus", resourceStatus);
        map.put("resourceVersion", resourceVersion);
        map.put("resourceChecksum", resourceChecksum);
        map.put("resourceInitializedAt", String.valueOf(resourceInitializedAt));
        map.put("resourceLastVerifiedAt", String.valueOf(resourceLastVerifiedAt));
        map.put("projectType", getProjectType().getKey());
        map.put("bootstrapRenderingMode", getBootstrapRenderingMode().getKey());
        map.put("bootstrapAtStartup", String.valueOf(bootstrapAtStartup));
        map.put("splashSourceType", getSplashSourceType().getKey());
        map.put("splashSourcePath", splashSourcePath != null ? splashSourcePath : "");
        map.put("splashTransition", getSplashTransition());
        return Codec.encode(map);
    }

    public static Project decode(String raw) {
        Map<String, String> map = Codec.decode(raw);
        if (map.isEmpty() || !map.containsKey("id")) {
            // বৈধ এনকোডিংয়ে অন্তত id থাকতেই হবে — নাহলে এটি প্রজেক্ট নয়।
            return null;
        }
        Project project = new Project();
        project.id = Codec.str(map, "id", project.id);
        project.name = Codec.str(map, "name", "");
        project.description = Codec.str(map, "description", "");
        project.iconColor = Codec.integer(map, "iconColor", DEFAULT_ICON_COLOR);
        project.sourceType = ProjectSourceType.fromKey(Codec.str(map, "sourceType", "asset"));
        project.sourcePath = Codec.str(map, "sourcePath", "");
        project.entryPoint = Codec.str(map, "entryPoint", "index.html");
        project.baseUrl = Codec.str(map, "baseUrl", "");
        project.inlineHtml = Codec.str(map, "inlineHtml", "");
        project.splashEnabled = Codec.bool(map, "splashEnabled", true);
        project.bootstrapEnabled = Codec.bool(map, "bootstrapEnabled", true);
        project.splashDurationMs = Codec.integer(map, "splashDurationMs", DEFAULT_SPLASH_DURATION_MS);
        project.defaultProject = Codec.bool(map, "defaultProject", false);
        project.lastOpenedAt = Codec.lng(map, "lastOpenedAt", 0L);
        project.createdAt = Codec.lng(map, "createdAt", System.currentTimeMillis());
        project.updatedAt = Codec.lng(map, "updatedAt", project.createdAt);
        project.resourceStatus = Codec.str(map, "resourceStatus", "not_initialized");
        project.resourceVersion = Codec.str(map, "resourceVersion", "1");
        project.resourceChecksum = Codec.str(map, "resourceChecksum", "");
        project.resourceInitializedAt = Codec.lng(map, "resourceInitializedAt", 0L);
        project.resourceLastVerifiedAt = Codec.lng(map, "resourceLastVerifiedAt", 0L);
        // Records written before the type system existed decode as WEB application projects,
        // which is exactly what they were.
        project.projectType = ProjectType.fromKey(Codec.str(map, "projectType", ProjectType.WEB.getKey()));
        project.bootstrapRenderingMode = BootstrapRenderingMode.fromKey(
                Codec.str(map, "bootstrapRenderingMode", BootstrapRenderingMode.NATIVE.getKey()));
        project.bootstrapAtStartup = Codec.bool(map, "bootstrapAtStartup", false);
        project.splashSourceType = ProjectSourceType.fromKey(
                Codec.str(map, "splashSourceType", ProjectSourceType.ASSET.getKey()));
        project.splashSourcePath = Codec.str(map, "splashSourcePath", "");
        project.splashTransition = Codec.str(map, "splashTransition", "FADE");
        return project;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Project)) {
            return false;
        }
        Project that = (Project) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Project{id='" + id + "', name='" + name + "', type=" + getProjectType()
                + ", source=" + sourceType + ", splash=" + splashEnabled
                + ", bootstrap=" + bootstrapEnabled + "}";
    }
}
