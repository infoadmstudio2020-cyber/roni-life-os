package com.roni.life.os.app.uiconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.library.ui.config.ProjectUIConfig;
import com.roni.library.ui.config.ProjectUIConfigPresets;
import com.roni.library.ui.manager.ComponentType;
import com.roni.life.os.app.project.KeyValueBackend;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistence tests for {@link ProjectUIConfigStore}.
 *
 * <p>The store is exercised against an in-memory {@link KeyValueBackend}, the same seam the
 * existing {@code ProjectStoreTest} uses.</p>
 */
public class ProjectUIConfigStoreTest {

    private InMemoryBackend backend;
    private ProjectUIConfigStore store;

    @Before
    public void setUp() {
        backend = new InMemoryBackend();
        store = new ProjectUIConfigStore(backend);
    }

    @Test
    public void loadOfUnknownProjectReturnsDefaults() {
        ProjectUIConfig config = store.load("missing");

        assertNotNull(config);
        assertTrue(config.isEnabled(ComponentType.STATUS_BAR));
        assertFalse(config.isEnabled(ComponentType.TOOLBAR));
    }

    @Test
    public void loadOfNullProjectReturnsDefaults() {
        assertNotNull(store.load(null));
        assertNotNull(store.load(""));
    }

    @Test
    public void saveThenLoadRoundTrips() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId("dashboard_full");
        config.setEnabled(ComponentType.TOOLBAR, true);
        config.setEnabled(ComponentType.DRAWER, true);
        config.getSplash().setLogoText("Saved splash");

        assertTrue(store.save("project-a", config));

        ProjectUIConfig loaded = store.load("project-a");
        assertEquals("dashboard_full", loaded.getPresetId());
        assertTrue(loaded.isEnabled(ComponentType.TOOLBAR));
        assertTrue(loaded.isEnabled(ComponentType.DRAWER));
        assertEquals("Saved splash", loaded.getSplash().getLogoText());
    }

    @Test
    public void projectsAreIsolatedFromEachOther() {
        ProjectUIConfig a = new ProjectUIConfig();
        a.setEnabled(ComponentType.TOOLBAR, true);
        a.getSplash().setLogoText("A");

        ProjectUIConfig b = new ProjectUIConfig();
        b.setEnabled(ComponentType.DRAWER, true);
        b.getSplash().setLogoText("B");

        store.save("project-a", a);
        store.save("project-b", b);

        ProjectUIConfig loadedA = store.load("project-a");
        ProjectUIConfig loadedB = store.load("project-b");

        assertTrue(loadedA.isEnabled(ComponentType.TOOLBAR));
        assertFalse(loadedA.isEnabled(ComponentType.DRAWER));
        assertEquals("A", loadedA.getSplash().getLogoText());

        assertTrue(loadedB.isEnabled(ComponentType.DRAWER));
        assertFalse(loadedB.isEnabled(ComponentType.TOOLBAR));
        assertEquals("B", loadedB.getSplash().getLogoText());
    }

    @Test
    public void changingOneProjectDoesNotAffectAnother() {
        store.save("project-a", ProjectUIConfigPresets.create(ProjectUIConfigPresets.MINIMAL));
        store.save("project-b", ProjectUIConfigPresets.create(ProjectUIConfigPresets.MINIMAL));

        ProjectUIConfig changed = store.load("project-a");
        changed.setEnabled(ComponentType.FAB, true);
        store.save("project-a", changed);

        assertTrue(store.load("project-a").isEnabled(ComponentType.FAB));
        assertFalse(store.load("project-b").isEnabled(ComponentType.FAB));
    }

    @Test
    public void existsReflectsStoredRecords() {
        assertFalse(store.exists("project-a"));

        store.save("project-a", new ProjectUIConfig());

        assertTrue(store.exists("project-a"));
    }

    @Test
    public void resetRemovesTheRecord() {
        store.save("project-a", ProjectUIConfigPresets.create(ProjectUIConfigPresets.EVERYTHING));
        assertTrue(store.exists("project-a"));

        assertTrue(store.reset("project-a"));

        assertFalse(store.exists("project-a"));
        // Falls back to defaults rather than throwing.
        assertFalse(store.load("project-a").isEnabled(ComponentType.TOOLBAR));
    }

    @Test
    public void applyPresetPersistsThePreset() {
        assertTrue(store.applyPreset("project-a", ProjectUIConfigPresets.FULLSCREEN_WEBVIEW));

        ProjectUIConfig loaded = store.load("project-a");
        assertEquals(ProjectUIConfigPresets.FULLSCREEN_WEBVIEW, loaded.getPresetId());
        assertTrue(loaded.isEnabled(ComponentType.WEB_VIEW));
        assertFalse(loaded.getDashboard().isEnabled());
    }

    @Test
    public void exportThenImportRestoresTheConfiguration() {
        ProjectUIConfig original = new ProjectUIConfig();
        original.setEnabled(ComponentType.TAB_LAYOUT, true);
        original.getTheme().setPrimaryColor(0xFF00FF00);
        store.save("project-a", original);

        String payload = store.export("project-a");
        assertTrue(payload.contains("v="));

        assertTrue(store.importFrom("project-b", payload));

        ProjectUIConfig imported = store.load("project-b");
        assertTrue(imported.isEnabled(ComponentType.TAB_LAYOUT));
        assertEquals(0xFF00FF00, imported.getTheme().getPrimaryColor());
    }

    @Test
    public void importRejectsPayloadWithoutSchemaMarker() {
        store.save("project-a", ProjectUIConfigPresets.create(ProjectUIConfigPresets.MINIMAL));

        assertFalse(store.importFrom("project-a", "not-a-config"));

        // The existing record is untouched.
        assertEquals(ProjectUIConfigPresets.MINIMAL, store.load("project-a").getPresetId());
    }

    @Test
    public void importRejectsEmptyPayload() {
        assertFalse(store.importFrom("project-a", ""));
        assertFalse(store.importFrom("project-a", null));
    }

    @Test
    public void saveRejectsNullArguments() {
        assertFalse(store.save(null, new ProjectUIConfig()));
        assertFalse(store.save("project-a", null));
    }

    @Test
    public void storeWithoutBackendDegradesGracefully() {
        ProjectUIConfigStore orphan = new ProjectUIConfigStore(null);

        assertNotNull(orphan.load("project-a"));
        assertFalse(orphan.save("project-a", new ProjectUIConfig()));
        assertFalse(orphan.exists("project-a"));
    }

    /** Minimal in-memory backend mirroring the production contract. */
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
