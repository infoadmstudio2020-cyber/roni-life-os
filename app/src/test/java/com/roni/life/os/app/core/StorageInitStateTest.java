package com.roni.life.os.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The initialisation marker is the only proof that a first run finished. These tests pin the
 * round trip and the "corrupt file means not initialised" behaviour that makes a retry safe.
 */
public class StorageInitStateTest {

    @Test
    public void roundTrip_preservesEveryField() {
        StorageInitState state = new StorageInitState(true, 1, true, 1700000000000L, 3,
                "/storage/emulated/0/Android/data/com.roni.life.os.app/files/RONiLiFEOS", 23);

        StorageInitState decoded = StorageInitState.decode(state.encode());

        assertNotNull(decoded);
        assertTrue(decoded.isInitialized());
        assertEquals(1, decoded.getVersion());
        assertTrue(decoded.isFirstRun());
        assertEquals(1700000000000L, decoded.getInitializedAt());
        assertEquals(3, decoded.getLayoutVersion());
        assertEquals("/storage/emulated/0/Android/data/com.roni.life.os.app/files/RONiLiFEOS",
                decoded.getRoot());
        assertEquals(23, decoded.getCopiedFiles());
    }

    @Test
    public void decodeOfNull_returnsNull() {
        assertNull(StorageInitState.decode(null));
        assertNull(StorageInitState.decode(""));
        assertNull(StorageInitState.decode("   "));
    }

    @Test
    public void decodeOfGarbage_isNotInitialised() {
        StorageInitState decoded = StorageInitState.decode("not json at all");

        assertNotNull(decoded);
        assertFalse(decoded.isInitialized());
        assertEquals(0, decoded.getVersion());
        assertEquals("", decoded.getRoot());
    }

    @Test
    public void decodeOfTruncatedDocument_isNotInitialised() {
        // A crash mid-write can leave a prefix behind; it must never read as "done".
        StorageInitState decoded = StorageInitState.decode("{\n  \"initialized\": tr");

        assertNotNull(decoded);
        assertFalse(decoded.isInitialized());
    }

    @Test
    public void decodeOfOlderVersion_isDetectedAsUpgrade() {
        StorageInitState decoded = StorageInitState.decode(
                "{\"initialized\": true, \"version\": 0, \"layoutVersion\": 2}");

        assertNotNull(decoded);
        assertTrue(decoded.isInitialized());
        assertTrue(decoded.getVersion() < StorageSpec.INIT_VERSION);
    }

    @Test
    public void encode_escapesQuotesAndBackslashes() {
        StorageInitState state = new StorageInitState(true, 1, false, 1L, 3,
                "C:\\path\\\"quoted\"", 0);

        StorageInitState decoded = StorageInitState.decode(state.encode());

        assertNotNull(decoded);
        assertEquals("C:\\path\\\"quoted\"", decoded.getRoot());
    }

    @Test
    public void encode_isStableForTheSameState() {
        StorageInitState state = new StorageInitState(true, 1, false, 42L, 3, "/root", 7);

        assertEquals(state.encode(), state.encode());
        assertEquals(state.encode(), StorageInitState.decode(state.encode()).encode());
    }
}
