package com.roni.life.os.app.project;

/**
 * New project তৈরির সহায়ক ফ্যাক্টরি।
 */
public final class ProjectFactory {

    /** বান্ডল করা Demo web appের অ্যাসেট Path। */
    public static final String DEMO_ASSET_PATH = "webapp.zip";

    private ProjectFactory() {
    }

    public static Project createBlank(String name) {
        Project project = new Project();
        project.setName(name != null && !name.trim().isEmpty() ? name : "New project");
        project.setDescription("");
        project.setSourceType(ProjectSourceType.ZIP_ASSET);
        project.setSourcePath(DEMO_ASSET_PATH);
        project.setEntryPoint("index.html");
        project.setSplashEnabled(true);
        project.setBootstrapEnabled(true);
        project.setSplashDurationMs(Project.DEFAULT_SPLASH_DURATION_MS);
        return project;
    }

    public static Project duplicate(Project source, String newName) {
        if (source == null) {
            return createBlank(newName);
        }
        return source.copy(newName);
    }

    /** প্রথম ইনস্টলেশনে দেখানোর জন্য একটি প্রস্তুত ডেমো প্রজেক্ট। */
    public static Project createDemoProject() {
        Project project = new Project();
        project.setName("Demo web app");
        project.setDescription("Bundled demo web application for the NativeRuntime ecosystem.");
        project.setSourceType(ProjectSourceType.ZIP_ASSET);
        project.setSourcePath(DEMO_ASSET_PATH);
        project.setEntryPoint("index.html");
        project.setIconColor(0xFF00B894);
        project.setSplashEnabled(true);
        project.setBootstrapEnabled(true);
        project.setSplashDurationMs(Project.DEFAULT_SPLASH_DURATION_MS);
        project.setDefault(true);
        return project;
    }
}
