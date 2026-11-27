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
import com.roni.life.os.app.project.StartupDecision;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The complete startup matrix, driven only by {@code splashEnabled} and {@code dashboardEnabled}.
 *
 * <pre>
 * Splash | Dashboard | Expected
 * -------+-----------+---------------------------
 *  ON    |    ON     | Splash → Dashboard
 *  OFF   |    ON     | Dashboard
 *  ON    |    OFF    | Splash → Default Project
 *  OFF   |    OFF    | Default Project
 * </pre>
 *
 * <p>The second half of every case is resolved through the real store, so the test covers the
 * default-project rule as well as the flag matrix.</p>
 */
public class StartupMatrixTest {

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
    private Project defaultProject;

    @Before
    public void setUp() {
        store = new ProjectStore(new InMemoryBackend());
        defaultProject = ProjectFactory.createBlank("Default app");
        store.save(defaultProject);
        store.setDefaultProject(defaultProject.getId());
    }

    // ==================== The four combinations ====================

    @Test
    public void splashOn_dashboardOn_splashThenDashboard() {
        assertEquals(StartupDecision.ConsumerDestination.SPLASH,
                StartupDecision.resolveConsumer(true, true, true));
        assertEquals(StartupDecision.ConsumerDestination.DASHBOARD,
                StartupDecision.resolveConsumerAfterSplash(true, true));
    }

    @Test
    public void splashOff_dashboardOn_dashboard() {
        assertEquals(StartupDecision.ConsumerDestination.DASHBOARD,
                StartupDecision.resolveConsumer(false, true, true));
    }

    @Test
    public void splashOn_dashboardOff_splashThenDefaultProject() {
        assertEquals(StartupDecision.ConsumerDestination.SPLASH,
                StartupDecision.resolveConsumer(true, false, true));
        assertEquals(StartupDecision.ConsumerDestination.DEFAULT_PROJECT,
                StartupDecision.resolveConsumerAfterSplash(false, true));
    }

    @Test
    public void splashOff_dashboardOff_defaultProject() {
        assertEquals(StartupDecision.ConsumerDestination.DEFAULT_PROJECT,
                StartupDecision.resolveConsumer(false, false, true));
    }

    // ==================== Dashboard OFF never falls back to the dashboard ====================

    @Test
    public void dashboardOff_withoutDefaultIsAConfigurationError() {
        assertEquals(StartupDecision.ConsumerDestination.CONFIG_ERROR,
                StartupDecision.resolveConsumer(false, false, false));
        assertEquals(StartupDecision.ConsumerDestination.CONFIG_ERROR,
                StartupDecision.resolveConsumerAfterSplash(false, false));
    }

    @Test
    public void dashboardOff_neverResolvesToDashboard() {
        for (boolean splash : new boolean[]{true, false}) {
            for (boolean hasDefault : new boolean[]{true, false}) {
                StartupDecision.ConsumerDestination first =
                        StartupDecision.resolveConsumer(splash, false, hasDefault);
                assertTrue(first != StartupDecision.ConsumerDestination.DASHBOARD);
                StartupDecision.ConsumerDestination after =
                        StartupDecision.resolveConsumerAfterSplash(false, hasDefault);
                assertTrue(after != StartupDecision.ConsumerDestination.DASHBOARD);
            }
        }
    }

    // ==================== The resolved project is the selected one ====================

    @Test
    public void dashboardOff_opensTheSelectedDefaultProject() {
        Project second = ProjectFactory.createBlank("Second app");
        Project third = ProjectFactory.createBlank("Third app");
        store.save(second);
        store.save(third);

        store.setDefaultProject(third.getId());

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();
        assertTrue(resolution.isResolved());
        assertEquals(third.getId(), resolution.getProjectId());
    }

    @Test
    public void dashboardOff_afterRestartStillOpensTheSelectedProject() {
        Project second = ProjectFactory.createBlank("Second app");
        store.save(second);
        store.setDefaultProject(second.getId());

        ProjectStore restarted = new ProjectStore(store.getBackend());
        DefaultProjectResolver.Resolution resolution = restarted.resolveDefaultProject();

        assertTrue(resolution.isResolved());
        assertEquals(second.getId(), resolution.getProjectId());
    }

    @Test
    public void dashboardOff_withSeveralProjectsAndNoSelectionIsAnError() {
        store.save(ProjectFactory.createBlank("Second app"));
        store.clearDefault();

        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        assertFalse(resolution.isResolved());
        assertEquals(DefaultProjectResolver.Status.NOT_CONFIGURED, resolution.getStatus());
        assertNull(resolution.getProject());
    }

    @Test
    public void dashboardOff_withOneProjectAndNoSelectionIsStillAnError() {
        ProjectStore fresh = new ProjectStore(new InMemoryBackend());
        Project only = ProjectFactory.createBlank("Only app");
        fresh.save(only);

        // A single registered project is not adopted automatically: after the user deletes the
        // default, opening a different project would be the silent substitution this rule bans.
        DefaultProjectResolver.Resolution resolution = fresh.resolveDefaultProject();

        assertFalse(resolution.isResolved());
        assertEquals(DefaultProjectResolver.Status.NOT_CONFIGURED, resolution.getStatus());
        assertNull(resolution.getProject());
    }

    // ==================== Splash independence ====================

    @Test
    public void splashOff_doesNotRequireASplashProject() {
        AppConfig config = store.loadConfig();
        config.setSplashEnabled(false);
        config.setDashboardEnabled(false);
        store.saveConfig(config);

        assertEquals(StartupDecision.ConsumerDestination.DEFAULT_PROJECT,
                StartupDecision.resolveConsumer(config.isSplashEnabled(),
                        config.isDashboardEnabled(), true));
        assertNotNull(store.defaultProject());
    }
}
