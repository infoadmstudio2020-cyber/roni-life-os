package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.project.DefaultProjectResolver;
import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectFactory;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.project.ProjectType;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The authoritative default-project rule.
 *
 * <p>These tests encode the acceptance condition: with several registered projects, the one the
 * user explicitly selected must be the one that starts — after a restart, after another project
 * was opened, and after the legacy fields were written by an older build.</p>
 */
public class DefaultProjectResolverTest {

    private static final class InMemoryBackend implements KeyValueBackend {
        private final Map<String, String> data = new HashMap<>();

        @Override
        public String get(String key, String fallback) {
            String value = data.get(key);
            return value != null ? value : fallback;
        }

        @Override
        public void put(String key, String value) {
            data.put(key, value);
        }

        @Override
        public boolean remove(String key) {
            return data.remove(key) != null;
        }
    }

    private InMemoryBackend backend;
    private ProjectStore store;

    @Before
    public void setUp() {
        backend = new InMemoryBackend();
        store = new ProjectStore(backend);
    }

    // ==================== Selection ====================

    @Test
    public void explicitDefaultWinsOverLastOpened() {
        Project first = web("First");
        Project second = web("Second");
        store.save(first);
        store.save(second);

        store.setDefaultProject(first.getId());
        store.markOpened(second.getId());

        assertEquals(first.getId(), store.defaultProject().getId());
        assertEquals(first.getId(), store.resolveStartupProject().getId());
    }

    @Test
    public void selectingAnotherProjectReplacesThePreviousDefault() {
        Project first = web("First");
        Project second = web("Second");
        Project third = web("Third");
        store.save(first);
        store.save(second);
        store.save(third);

        store.setDefaultProject(first.getId());
        store.setDefaultProject(second.getId());

        assertFalse(store.findById(first.getId()).isDefault());
        assertTrue(store.findById(second.getId()).isDefault());
        assertFalse(store.findById(third.getId()).isDefault());
        assertEquals(second.getId(), store.loadConfig().getDefaultWebProjectId());
        assertEquals(second.getId(), store.loadConfig().getDefaultProjectId());
        assertEquals(second.getId(), store.defaultProject().getId());
    }

    @Test
    public void selectionSurvivesAProcessRestart() {
        Project first = web("First");
        Project second = web("Second");
        store.save(first);
        store.save(second);
        store.setDefaultProject(second.getId());

        // A new store over the same backend is exactly what a cold start sees.
        ProjectStore restarted = new ProjectStore(backend);

        assertEquals(second.getId(), restarted.defaultProject().getId());
        assertEquals(second.getId(), restarted.loadConfig().getDefaultWebProjectId());
    }

    @Test
    public void onlyOneProjectCarriesTheDefaultFlag() {
        Project first = web("First");
        Project second = web("Second");
        store.save(first);
        store.save(second);

        store.setDefaultProject(first.getId());
        store.setDefaultProject(second.getId());

        int flagged = 0;
        for (Project project : store.getAll()) {
            if (project.isDefault()) {
                flagged++;
            }
        }
        assertEquals(1, flagged);
    }

    // ==================== Validation ====================

    @Test
    public void nonApplicationProjectIsRejectedAndThePreviousDefaultIsKept() {
        Project web = web("Web");
        Project splash = project("Splash", ProjectType.SPLASH);
        store.save(web);
        store.save(splash);
        store.setDefaultProject(web.getId());

        ProjectStore.DefaultResult result = store.setDefaultProject(splash.getId());

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().isEmpty());
        assertEquals(web.getId(), store.defaultProject().getId());
        assertFalse(store.findById(splash.getId()).isDefault());
    }

    @Test
    public void missingProjectIsRejected() {
        Project web = web("Web");
        store.save(web);
        store.setDefaultProject(web.getId());

        ProjectStore.DefaultResult result = store.setDefaultProject("does-not-exist");

        assertFalse(result.isSuccess());
        assertEquals(web.getId(), store.defaultProject().getId());
    }

    @Test
    public void nonApplicationProjectIsNotLaunchable() {
        Project splash = project("Splash", ProjectType.SPLASH);
        Project bootstrap = project("Bootstrap", ProjectType.BOOTSTRAP);
        Project web = web("Web");

        assertFalse(DefaultProjectResolver.isLaunchable(splash));
        assertFalse(DefaultProjectResolver.isLaunchable(bootstrap));
        assertTrue(DefaultProjectResolver.isLaunchable(web));
        assertFalse(DefaultProjectResolver.isLaunchable(null));
    }

    // ==================== Failure reporting ====================

    @Test
    public void noSelectionIsReportedAsNotConfigured() {
        store.save(web("First"));

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        assertEquals(DefaultProjectResolver.Status.NOT_CONFIGURED, resolution.getStatus());
        assertNull(resolution.getProject());
        assertTrue(resolution.isFailure());
    }

    @Test
    public void deletedDefaultIsNotReplacedByAnotherProject() {
        Project first = web("First");
        Project second = web("Second");
        store.save(first);
        store.save(second);
        store.setDefaultProject(first.getId());

        store.delete(first.getId());

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();
        assertTrue(resolution.isFailure());
        assertNull(resolution.getProject());
        assertNull(store.defaultProject());
    }

    @Test
    public void stalePointerIsReportedAsMissing() {
        Project first = web("First");
        store.save(first);

        AppConfig config = store.loadConfig();
        config.setDefaultWebProjectId("gone");
        store.saveConfig(config);

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        assertEquals(DefaultProjectResolver.Status.MISSING, resolution.getStatus());
        assertNull(resolution.getProject());
    }

    // ==================== Legacy migration ====================

    @Test
    public void legacyFlagIsMigratedIntoTheAuthoritativePointer() {
        Project first = web("First");
        Project second = web("Second");
        store.save(first);
        store.save(second);

        // An older build wrote only the per-project flag.
        second.setDefault(true);
        backend.put("project." + second.getId(), second.encode());

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        assertTrue(resolution.isResolved());
        assertEquals(second.getId(), resolution.getProjectId());
        assertEquals(second.getId(), store.loadConfig().getDefaultWebProjectId());
    }

    @Test
    public void legacyMirrorIsMigratedIntoTheAuthoritativePointer() {
        Project first = web("First");
        store.save(first);

        AppConfig config = store.loadConfig();
        config.setDefaultProjectId(first.getId());
        store.saveConfig(config);

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        assertTrue(resolution.isResolved());
        assertEquals(first.getId(), resolution.getProjectId());
        assertEquals(first.getId(), store.loadConfig().getDefaultWebProjectId());
    }

    @Test
    public void savingAProjectWithTheFlagPromotesThePointer() {
        Project first = web("First");
        Project second = web("Second");
        store.save(first);
        store.setDefaultProject(first.getId());

        // This is the path the slot editor uses: the dialog sets the flag, then saves.
        second.setDefault(true);
        store.save(second);

        assertEquals(second.getId(), store.loadConfig().getDefaultWebProjectId());
        assertEquals(second.getId(), store.defaultProject().getId());
        assertFalse(store.findById(first.getId()).isDefault());
    }

    @Test
    public void untickingTheFlagClearsThePointer() {
        Project first = web("First");
        store.save(first);
        store.setDefaultProject(first.getId());

        Project reloaded = store.findById(first.getId());
        reloaded.setDefault(false);
        store.save(reloaded);

        assertNull(store.defaultProject());
        assertEquals("", store.loadConfig().getDefaultWebProjectId());
    }

    // ==================== Helpers ====================

    private Project web(String name) {
        return project(name, ProjectType.WEB);
    }

    private Project project(String name, ProjectType type) {
        Project project = ProjectFactory.createBlank(name);
        project.setProjectType(type);
        return project;
    }
}
