package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.project.BootstrapRenderingMode;
import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectRoleResolver;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.project.ProjectType;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers the three-type project system: model persistence, role resolution, duplicate handling,
 * deletion cleanup, restart behaviour and cache preservation.
 */
public class ProjectTypeSystemTest {

    private InMemoryBackend backend;
    private ProjectStore store;

    @Before
    public void setUp() {
        backend = new InMemoryBackend();
        store = new ProjectStore(backend);
    }

    // ==================== Model ====================

    @Test
    public void newProjectDefaultsToWebApplication() {
        Project project = new Project();
        assertEquals(ProjectType.WEB, project.getProjectType());
        assertEquals(BootstrapRenderingMode.NATIVE, project.getBootstrapRenderingMode());
        assertFalse(project.isBootstrapAtStartup());
        assertTrue(project.isWebProject());
    }

    @Test
    public void typeAndRenderingModeSurviveSerialization() {
        Project project = project("Bootstrap", ProjectType.BOOTSTRAP);
        project.setBootstrapRenderingMode(BootstrapRenderingMode.WEB);
        project.setBootstrapAtStartup(true);
        project.setSplashTransition("SCALE");

        Project decoded = Project.decode(project.encode());

        assertNotNull(decoded);
        assertEquals(ProjectType.BOOTSTRAP, decoded.getProjectType());
        assertEquals(BootstrapRenderingMode.WEB, decoded.getBootstrapRenderingMode());
        assertTrue(decoded.isBootstrapAtStartup());
        assertEquals("SCALE", decoded.getSplashTransition());
    }

    @Test
    public void legacyRecordWithoutTypeDecodesAsWeb() {
        Project legacy = project("Legacy", ProjectType.WEB);
        String encoded = legacy.encode();
        // Simulate a record written before the type system existed.
        String stripped = encoded
                .replaceAll("&?projectType=[^&]*", "")
                .replaceAll("&?bootstrapRenderingMode=[^&]*", "")
                .replaceAll("&?bootstrapAtStartup=[^&]*", "");

        Project decoded = Project.decode(stripped);

        assertNotNull(decoded);
        assertEquals(ProjectType.WEB, decoded.getProjectType());
        assertEquals(BootstrapRenderingMode.NATIVE, decoded.getBootstrapRenderingMode());
        assertFalse(decoded.isBootstrapAtStartup());
    }

    @Test
    public void copyNeverInheritsTheStartupRole() {
        Project bootstrap = project("Bootstrap", ProjectType.BOOTSTRAP);
        bootstrap.setBootstrapAtStartup(true);

        Project copy = bootstrap.copy("Bootstrap copy");

        assertFalse(copy.isBootstrapAtStartup());
        assertEquals(ProjectType.BOOTSTRAP, copy.getProjectType());
    }

    // ==================== Role resolution ====================

    @Test
    public void configuredRoleWins() {
        Project first = project("Splash A", ProjectType.SPLASH);
        Project second = project("Splash B", ProjectType.SPLASH);
        store.save(first);
        store.save(second);

        assertTrue(store.setActiveSplash(second.getId()));

        assertEquals(second.getId(), store.activeSplash().getId());
    }

    @Test
    public void staleConfiguredRoleFallsBackToPolicy() {
        Project splash = project("Splash", ProjectType.SPLASH);
        store.save(splash);

        AppConfig config = store.loadConfig();
        config.setActiveSplashProjectId("deleted-project-id");
        store.saveConfig(config);

        Project resolved = store.activeSplash();
        assertNotNull(resolved);
        assertEquals(splash.getId(), resolved.getId());
    }

    @Test
    public void roleRejectsAProjectOfTheWrongType() {
        Project web = project("Web", ProjectType.WEB);
        store.save(web);

        assertFalse(store.setActiveSplash(web.getId()));
        assertNull(store.activeSplash());
    }

    @Test
    public void duplicateRolesAreReportedNotSilentlyIgnored() {
        store.save(project("Splash A", ProjectType.SPLASH));
        store.save(project("Splash B", ProjectType.SPLASH));

        List<ProjectRoleResolver.RoleSelection> ambiguous = store.ambiguousRoles();

        assertFalse(ambiguous.isEmpty());
        assertEquals(ProjectType.SPLASH, ambiguous.get(0).getRole());
        assertTrue(ambiguous.get(0).isAmbiguous());
    }

    @Test
    public void defaultWebIgnoresSplashAndBootstrapProjects() {
        store.save(project("Splash", ProjectType.SPLASH));
        store.save(project("Bootstrap", ProjectType.BOOTSTRAP));
        Project web = project("Web", ProjectType.WEB);
        store.save(web);
        store.setDefaultProject(web.getId());

        assertEquals(web.getId(), store.defaultWebProject().getId());
        assertEquals(web.getId(), store.resolveStartupProject().getId());
    }

    @Test
    public void splashAndBootstrapProjectsAreNeverTheStartupProject() {
        Project splash = project("Splash", ProjectType.SPLASH);
        Project bootstrap = project("Bootstrap", ProjectType.BOOTSTRAP);
        store.save(splash);
        store.save(bootstrap);

        // Only non-application projects exist: startup must report a configuration error
        // instead of opening one of them.
        assertNull(store.defaultWebProject());
        assertNull(store.resolveStartupProject());
        assertFalse(store.setDefaultProject(splash.getId()).isSuccess());
        assertFalse(store.setDefaultProject(bootstrap.getId()).isSuccess());
    }

    // ==================== Deletion ====================

    @Test
    public void deletingTheActiveSplashClearsThePointerAndKeepsOtherProjects() {
        Project splash = project("Splash", ProjectType.SPLASH);
        Project web = project("Web", ProjectType.WEB);
        store.save(splash);
        store.save(web);
        store.setActiveSplash(splash.getId());

        store.delete(splash.getId());

        assertNull(store.activeSplash());
        assertNotNull(store.findById(web.getId()));
        assertEquals(1, store.count());
    }

    @Test
    public void unregisteringKeepsProjectData() {
        Project splash = project("Splash", ProjectType.SPLASH);
        store.save(splash);
        store.setActiveSplash(splash.getId());

        store.removeFromRegistry(splash.getId());

        // The registry entry is gone, but the record itself is untouched.
        assertNotNull(store.findById(splash.getId()));
        assertNull(store.activeSplash());
    }

    @Test
    public void clearInvalidReferencesRepairsEveryRolePointer() {
        Project web = project("Web", ProjectType.WEB);
        store.save(web);

        AppConfig config = store.loadConfig();
        config.setActiveSplashProjectId("gone-1");
        config.setActiveBootstrapProjectId("gone-2");
        config.setDefaultWebProjectId("gone-3");
        store.saveConfig(config);

        assertTrue(store.clearInvalidReferences());

        AppConfig repaired = store.loadConfig();
        assertEquals("", repaired.getActiveSplashProjectId());
        assertEquals("", repaired.getActiveBootstrapProjectId());
        assertEquals("", repaired.getDefaultWebProjectId());
    }

    // ==================== Restart ====================

    @Test
    public void rolesSurviveAProcessRestart() {
        Project splash = project("Splash", ProjectType.SPLASH);
        Project bootstrap = project("Bootstrap", ProjectType.BOOTSTRAP);
        Project web = project("Web", ProjectType.WEB);
        store.save(splash);
        store.save(bootstrap);
        store.save(web);
        store.setActiveSplash(splash.getId());
        store.setActiveBootstrap(bootstrap.getId());
        store.setDefaultWeb(web.getId());

        // A new store over the same backend is exactly what a cold start sees.
        ProjectStore restarted = new ProjectStore(backend);

        assertEquals(splash.getId(), restarted.activeSplash().getId());
        assertEquals(bootstrap.getId(), restarted.activeBootstrap().getId());
        assertEquals(web.getId(), restarted.defaultWebProject().getId());
    }

    @Test
    public void bootstrapAtStartupSurvivesRestart() {
        Project bootstrap = project("Bootstrap", ProjectType.BOOTSTRAP);
        bootstrap.setBootstrapAtStartup(true);
        store.save(bootstrap);

        ProjectStore restarted = new ProjectStore(backend);

        assertTrue(restarted.isBootstrapAtStartupEnabled());
    }

    // ==================== Helpers ====================

    private Project project(String name, ProjectType type) {
        Project project = new Project();
        project.setName(name);
        project.setProjectType(type);
        return project;
    }

    /** Minimal in-memory backend — the same contract the production store uses. */
    private static final class InMemoryBackend implements KeyValueBackend {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String fallback) {
            String value = values.get(key);
            return value != null ? value : fallback;
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public boolean remove(String key) {
            return values.remove(key) != null;
        }
    }
}
