package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectStore;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry semantics: recent ordering, unregister-without-delete, and the dashboard mode
 * preference.
 */
public class ProjectRegistryTest {

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

    private ProjectStore store;

    @Before
    public void setUp() {
        store = new ProjectStore(new InMemoryBackend());
    }

    private Project project(String name, long openedAt) {
        Project project = new Project();
        project.setName(name);
        project.setSourceType(ProjectSourceType.FILE);
        project.setSourcePath("/tmp/" + name);
        project.setLastOpenedAt(openedAt);
        return project;
    }

    @Test
    public void recentProjectsAreOrderedByLastOpened() {
        Project older = project("older", 1000L);
        Project newer = project("newer", 5000L);
        Project never = project("never", 0L);
        store.save(older);
        store.save(newer);
        store.save(never);

        List<Project> recent = store.getRecent(2);
        assertEquals(2, recent.size());
        assertEquals("newer", recent.get(0).getName());
        assertEquals("older", recent.get(1).getName());
    }

    @Test
    public void recentRespectsTheLimit() {
        for (int i = 0; i < 6; i++) {
            store.save(project("p" + i, 1000L + i));
        }
        assertEquals(3, store.getRecent(3).size());
        assertEquals(6, store.getRecent(0).size());
    }

    @Test
    public void removeFromRegistryKeepsTheProjectRecord() {
        Project project = project("keep-me", 1000L);
        store.save(project);

        assertTrue(store.removeFromRegistry(project.getId()));
        assertTrue(store.getAll().isEmpty());
        // The record itself survives, so the project can be re-registered later.
        assertNotNull(store.findById(project.getId()));
    }

    @Test
    public void removeFromRegistryClearsStartupPointers() {
        Project project = project("default-one", 1000L);
        store.save(project);
        store.setDefault(project.getId());

        assertTrue(store.removeFromRegistry(project.getId()));
        assertEquals("", store.loadConfig().getDefaultProjectId());
    }

    @Test
    public void removeFromRegistryRejectsUnknownIds() {
        assertFalse(store.removeFromRegistry("missing"));
        assertFalse(store.removeFromRegistry(null));
    }

    @Test
    public void dashboardModeDefaultsToNative() {
        AppConfig config = new AppConfig();
        assertEquals(AppConfig.DASHBOARD_NATIVE, config.getDashboardMode());
        assertFalse(config.isWebDashboard());
    }

    @Test
    public void dashboardModeSurvivesEncoding() {
        AppConfig config = new AppConfig();
        config.setDashboardMode(AppConfig.DASHBOARD_WEB);

        AppConfig decoded = AppConfig.decode(config.encode());
        assertNotNull(decoded);
        assertEquals(AppConfig.DASHBOARD_WEB, decoded.getDashboardMode());
        assertTrue(decoded.isWebDashboard());
    }

    @Test
    public void unknownDashboardModeFallsBackToNative() {
        AppConfig config = new AppConfig();
        config.setDashboardMode("SOMETHING_ELSE");
        assertEquals(AppConfig.DASHBOARD_NATIVE, config.getDashboardMode());
    }

    @Test
    public void safFolderIsAPathBackedLocalSource() {
        assertTrue(ProjectSourceType.SAF_FOLDER.requiresPath());
        assertTrue(ProjectSourceType.SAF_FOLDER.isLocal());
        assertTrue(ProjectSourceType.SAF_FOLDER.isSaf());
        assertTrue(ProjectSourceType.SAF_FOLDER.requiresEntryPoint());
        assertEquals(ProjectSourceType.SAF_FOLDER,
                ProjectSourceType.fromKey("saf_folder"));
    }

    @Test
    public void safSourcePathRoundTripsThroughProjectEncoding() {
        Project project = new Project();
        project.setName("linked");
        project.setSourceType(ProjectSourceType.SAF_FOLDER);
        project.setSourcePath("content://com.android.externalstorage.documents/tree/primary%3Awww");

        Project decoded = Project.decode(project.encode());
        assertNotNull(decoded);
        assertEquals(ProjectSourceType.SAF_FOLDER, decoded.getSourceType());
        assertEquals(project.getSourcePath(), decoded.getSourcePath());
    }

    @Test
    public void unregisteredProjectIsNotResolvedAsStartupProject() {
        Project project = project("hidden", 1000L);
        store.save(project);
        store.markOpened(project.getId());
        store.removeFromRegistry(project.getId());

        assertNull(store.resolveStartupProject());
    }
}
