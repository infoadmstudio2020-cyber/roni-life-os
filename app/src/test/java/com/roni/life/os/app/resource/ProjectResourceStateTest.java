package com.roni.life.os.app.resource;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Persistence of the per-project resource state. */
public class ProjectResourceStateTest {

    private ResourceSpec spec(String id, String version) {
        return new ResourceSpec(id, ResourceSpec.Kind.REMOTE_ARCHIVE,
                "https://example.com/" + id + ".zip", id + ".zip", version, "", 0L, true, false);
    }

    @Test
    public void stateRoundTripsThroughEncoding() {
        ProjectResourceState state = new ProjectResourceState("p1");
        state.put(ResourceRecord.verified(spec("a", "1"), "/tmp/a.zip", 10L, "abc", 100L));
        state.put(ResourceRecord.verified(spec("b", "2"), "/tmp/b.zip", 20L, "def", 200L));
        state.markReady("3", 300L);

        ProjectResourceState decoded = ProjectResourceState.decode(state.encode());
        assertNotNull(decoded);
        assertEquals("p1", decoded.getProjectId());
        assertEquals(ProjectResourceState.Status.READY, decoded.getStatus());
        assertEquals("3", decoded.getManifestVersion());
        assertEquals(2, decoded.size());
        assertEquals(300L, decoded.getLastVerifiedAt());
        assertEquals(300L, decoded.getInitializedAt());
        assertEquals("/tmp/a.zip", decoded.get("a").getLocalPath());
        assertEquals("def", decoded.get("b").getChecksum());
        assertEquals(20L, decoded.get("b").getSizeBytes());
    }

    @Test
    public void corruptEncodingYieldsNull() {
        assertNull(ProjectResourceState.decode("garbage"));
        assertNull(ProjectResourceState.decode(""));
    }

    @Test
    public void initializedAtIsSetOnlyOnce() {
        ProjectResourceState state = new ProjectResourceState("p1");
        state.markReady("1", 100L);
        state.markReady("1", 500L);
        assertEquals(100L, state.getInitializedAt());
        assertEquals(500L, state.getLastVerifiedAt());
    }

    @Test
    public void incompleteIsNotReady() {
        ProjectResourceState state = new ProjectResourceState("p1");
        state.markIncomplete("1", 100L);
        assertFalse(state.isReady());
        assertEquals(ProjectResourceState.Status.INCOMPLETE, state.getStatus());
    }

    @Test
    public void retainOnlyDropsStaleRecords() {
        ProjectResourceState state = new ProjectResourceState("p1");
        state.put(ResourceRecord.verified(spec("a", "1"), "/tmp/a", 1L, "", 1L));
        state.put(ResourceRecord.verified(spec("b", "1"), "/tmp/b", 1L, "", 1L));
        state.retainOnly(Arrays.asList("a"));
        assertEquals(1, state.size());
        assertNotNull(state.get("a"));
        assertNull(state.get("b"));
    }

    @Test
    public void recordStatusSurvivesEncoding() {
        ResourceRecord record = new ResourceRecord("a");
        record.mark(ResourceRecord.Status.CORRUPT);
        ResourceRecord decoded = ResourceRecord.decode(record.encode());
        assertNotNull(decoded);
        assertEquals(ResourceRecord.Status.CORRUPT, decoded.getStatus());
        assertFalse(decoded.isVerified());
    }

    @Test
    public void unknownStatusFallsBackToPending() {
        assertEquals(ResourceRecord.Status.PENDING,
                ResourceRecord.Status.fromKey("nonsense"));
        assertEquals(ProjectResourceState.Status.NOT_INITIALIZED,
                ProjectResourceState.Status.fromKey(null));
    }

    @Test
    public void storePersistsAndClears() {
        InMemoryBackend backend = new InMemoryBackend();
        ProjectResourceStore store = new ProjectResourceStore(backend);
        ProjectResourceState state = new ProjectResourceState("p1");
        state.markReady("1", 10L);
        store.save(state);

        ProjectResourceState loaded = store.load("p1");
        assertNotNull(loaded);
        assertTrue(loaded.isReady());

        store.clear("p1");
        assertNull(store.load("p1"));
    }

    @Test
    public void storeToleratesMissingBackend() {
        ProjectResourceStore store = new ProjectResourceStore(null);
        assertNull(store.load("p1"));
        store.save(new ProjectResourceState("p1"));
        store.clear("p1");
    }

    /** Minimal backend used by the store tests. */
    static final class InMemoryBackend implements com.roni.life.os.app.project.KeyValueBackend {
        private final java.util.Map<String, String> values = new java.util.HashMap<>();

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
