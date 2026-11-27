package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.runtime.SessionSnapshot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot encoding used for cold-start recovery. Names may contain spaces, punctuation and
 * non-ASCII characters, so the round trip must be lossless.
 */
public class SessionSnapshotTest {

    @Test
    public void roundTripPreservesEveryField() {
        SessionSnapshot original = new SessionSnapshot(
                "project-1", "ডেমো ওয়েব অ্যাপ & co", 1_700_000_000_000L,
                0xFF7C6CFF, "asset", true);

        SessionSnapshot decoded = SessionSnapshot.decode(original.encode());

        assertEquals("project-1", decoded.getProjectId());
        assertEquals("ডেমো ওয়েব অ্যাপ & co", decoded.getName());
        assertEquals(1_700_000_000_000L, decoded.getLastActiveAt());
        assertEquals(0xFF7C6CFF, decoded.getIconColor());
        assertEquals("asset", decoded.getSourceKey());
        assertTrue(decoded.isPreview());
    }

    @Test
    public void missingIdIsRejected() {
        assertNull(SessionSnapshot.decode(""));
        assertNull(SessionSnapshot.decode(null));
        assertNull(SessionSnapshot.decode("v=1&name=Nameless"));
    }

    @Test
    public void malformedNumbersFallBackToZero() {
        SessionSnapshot decoded = SessionSnapshot.decode("v=1&id=a&at=not-a-number&color=x");

        assertEquals("a", decoded.getProjectId());
        assertEquals(0L, decoded.getLastActiveAt());
        assertEquals(0, decoded.getIconColor());
    }

    @Test
    public void listRoundTripSkipsMalformedRecords() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new SessionSnapshot("a", "Project A", 10L, 1, "asset", false));
        snapshots.add(new SessionSnapshot("b", "Project B", 20L, 2, "url", false));

        String encoded = SessionSnapshot.encodeList(snapshots);
        List<SessionSnapshot> decoded = SessionSnapshot.decodeList(encoded + "\nbroken");

        assertEquals(2, decoded.size());
        assertEquals("a", decoded.get(0).getProjectId());
        assertEquals("b", decoded.get(1).getProjectId());
    }

    @Test
    public void emptyListEncodesToEmptyString() {
        assertEquals("", SessionSnapshot.encodeList(null));
        assertEquals("", SessionSnapshot.encodeList(new ArrayList<>()));
        assertTrue(SessionSnapshot.decodeList("").isEmpty());
    }

    @Test
    public void snapshotWithoutIdIsNotEncoded() {
        assertEquals("", new SessionSnapshot(null, "Nameless", 0L, 0, "", false).encode());
        assertFalse(SessionSnapshot.encodeList(
                java.util.Collections.singletonList(
                        new SessionSnapshot("", "Nameless", 0L, 0, "", false))).contains("id="));
    }
}
