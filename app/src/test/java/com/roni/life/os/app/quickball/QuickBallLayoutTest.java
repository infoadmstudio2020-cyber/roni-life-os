package com.roni.life.os.app.quickball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class QuickBallLayoutTest {

    @Test
    public void sanitizeDropsUnknownIds() {
        List<String> result = QuickBallLayout.sanitize(
                Arrays.asList("a", "b"),
                Arrays.asList("b", "ghost", "a"));
        assertEquals(Arrays.asList("b", "a"), result);
    }

    @Test
    public void sanitizeAppendsNewlyRegisteredIds() {
        List<String> result = QuickBallLayout.sanitize(
                Arrays.asList("a", "b", "c"),
                Collections.singletonList("b"));
        assertEquals(Arrays.asList("b", "a", "c"), result);
    }

    @Test
    public void sanitizeCollapsesDuplicates() {
        List<String> result = QuickBallLayout.sanitize(
                Arrays.asList("a", "b"),
                Arrays.asList("a", "a", "b", "a"));
        assertEquals(Arrays.asList("a", "b"), result);
    }

    @Test
    public void moveClampsAtBothEnds() {
        List<String> order = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("b", "a", "c"), QuickBallLayout.move(order, "a", 1));
        assertEquals(Arrays.asList("a", "c", "b"), QuickBallLayout.move(order, "c", -1));
        assertEquals(order, QuickBallLayout.move(order, "a", -5));
        assertEquals(order, QuickBallLayout.move(order, "c", 5));
        assertEquals(order, QuickBallLayout.move(order, "missing", 1));
    }

    @Test
    public void enabledOrderedKeepsStoredOrder() {
        List<String> order = Arrays.asList("a", "b", "c");
        List<String> result = QuickBallLayout.enabledOrdered(order,
                new LinkedHashSet<>(Arrays.asList("c", "a")));
        assertEquals(Arrays.asList("a", "c"), result);
    }

    @Test
    public void joinAndSplitRoundTrip() {
        List<String> ids = Arrays.asList("qb.inspector", "qb.tasks", "qb.events");
        assertEquals(ids, QuickBallLayout.split(QuickBallLayout.join(ids)));
        assertTrue(QuickBallLayout.split("").isEmpty());
        assertTrue(QuickBallLayout.split(null).isEmpty());
    }

    @Test
    public void joinSkipsEmptyValues() {
        assertEquals("a,b", QuickBallLayout.join(Arrays.asList("a", "", null, "b")));
        assertFalse(QuickBallLayout.join(Collections.emptyList()).contains(","));
    }
}
