package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectFactory;
import com.roni.life.os.app.project.ProjectStore;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ইন-মেমরি ব্যাকএন্ডে প্রজেক্ট স্টোরের CRUD, ডিফল্ট ও স্টার্টআপ রেজোলিউশন যাচাই।
 */
public class ProjectStoreTest {

    /** টেস্টের জন্য সাধারণ ইন-মেমরি KeyValueBackend। */
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

    @Test
    public void saveAndFind_roundTrip() {
        Project project = ProjectFactory.createBlank("প্রথম");
        store.save(project);

        assertEquals(1, store.count());
        Project loaded = store.findById(project.getId());
        assertNotNull(loaded);
        assertEquals("প্রথম", loaded.getName());
    }

    @Test
    public void saveTwice_doesNotDuplicateIndex() {
        Project project = ProjectFactory.createBlank("একবার");
        store.save(project);
        store.save(project);
        assertEquals(1, store.count());
    }

    @Test
    public void delete_removesProjectAndClearsConfig() {
        Project project = ProjectFactory.createBlank("মুছতে হবে");
        store.save(project);
        store.setDefault(project.getId());
        store.markOpened(project.getId());

        assertTrue(store.delete(project.getId()));
        assertEquals(0, store.count());
        assertNull(store.findById(project.getId()));

        AppConfig config = store.loadConfig();
        assertEquals("", config.getDefaultProjectId());
        assertEquals("", config.getLastOpenedProjectId());
    }

    @Test
    public void duplicate_createsIndependentCopy() {
        Project project = ProjectFactory.createBlank("মূল");
        store.save(project);

        Project copy = store.duplicate(project.getId(), "মূল (কপি)");
        assertNotNull(copy);
        assertEquals(2, store.count());
        assertFalse(copy.getId().equals(project.getId()));
        assertEquals("মূল (কপি)", copy.getName());
    }

    @Test
    public void setDefault_clearsOtherFlags() {
        Project first = ProjectFactory.createBlank("প্রথম");
        Project second = ProjectFactory.createBlank("দ্বিতীয়");
        store.save(first);
        store.save(second);

        store.setDefault(second.getId());

        assertFalse(store.findById(first.getId()).isDefault());
        assertTrue(store.findById(second.getId()).isDefault());
        assertEquals(second.getId(), store.loadConfig().getDefaultProjectId());
    }

    @Test
    public void resolveStartupProject_ignoresLastOpened() {
        Project first = ProjectFactory.createBlank("প্রথম");
        Project second = ProjectFactory.createBlank("দ্বিতীয়");
        store.save(first);
        store.save(second);
        store.setDefault(first.getId());
        store.markOpened(second.getId());

        // Opening a project must never change which project starts. The explicit default wins.
        Project resolved = store.resolveStartupProject();
        assertNotNull(resolved);
        assertEquals(first.getId(), resolved.getId());
    }

    @Test
    public void resolveStartupProject_fallsBackToDefault() {
        Project first = ProjectFactory.createBlank("প্রথম");
        store.save(first);
        store.setDefault(first.getId());

        Project resolved = store.resolveStartupProject();
        assertNotNull(resolved);
        assertEquals(first.getId(), resolved.getId());
    }

    @Test
    public void resolveStartupProject_returnsNullWhenEmpty() {
        assertNull(store.resolveStartupProject());
    }

    @Test
    public void getAll_isSortedWithDefaultFirst() {
        Project alpha = ProjectFactory.createBlank("ক");
        Project beta = ProjectFactory.createBlank("খ");
        store.save(alpha);
        store.save(beta);
        store.setDefault(beta.getId());

        List<Project> all = store.getAll();
        assertEquals(2, all.size());
        assertTrue(all.get(0).isDefault());
    }

    @Test
    public void config_roundTrip() {
        AppConfig config = new AppConfig();
        config.setSplashEnabled(false);
        config.setBootstrapEnabled(false);
        config.setTelemetryEnabled(false);
        config.setDebugEnabled(true);
        config.setSplashDurationMs(3000);

        store.saveConfig(config);
        AppConfig loaded = store.loadConfig();

        assertFalse(loaded.isSplashEnabled());
        assertFalse(loaded.isBootstrapEnabled());
        assertFalse(loaded.isTelemetryEnabled());
        assertTrue(loaded.isDebugEnabled());
        assertEquals(3000, loaded.getSplashDurationMs());
    }
}
