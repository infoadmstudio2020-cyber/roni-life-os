package com.roni.life.os.app.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Source selection and execution mode are separate fields; these tests pin that separation and
 * the "unknown value means default" behaviour that keeps a hand-edited file safe.
 */
public class SourceConfigTest {

    @Test
    public void defaults_areSafe() {
        SourceConfig config = new SourceConfig();

        assertEquals(SourceSlot.DEFAULT, config.getActiveSource());
        assertTrue(config.isFallbackEnabled());
        assertEquals(SourceConfig.MODE_NATIVE, config.getActiveMode());
        assertFalse(config.isWebMode());
        assertEquals("index.html", config.getRuntimeEntry());
    }

    @Test
    public void roundTrip_preservesSourceAndMode() {
        SourceConfig config = new SourceConfig();
        config.setActiveSource(SourceSlot.RUNTIME);
        config.setFallbackEnabled(false);
        config.setActiveMode(SourceConfig.MODE_WEB);
        config.setRuntimeEntry("app/index.html");
        config.touch();

        SourceConfig decoded = SourceConfig.decode(config.encode());

        assertEquals(SourceSlot.RUNTIME, decoded.getActiveSource());
        assertFalse(decoded.isFallbackEnabled());
        assertTrue(decoded.isWebMode());
        assertEquals("app/index.html", decoded.getRuntimeEntry());
        assertEquals(config.getUpdatedAt(), decoded.getUpdatedAt());
    }

    @Test
    public void sourceAndMode_areIndependent() {
        SourceConfig config = new SourceConfig();
        config.setActiveSource(SourceSlot.RUNTIME);
        config.setActiveMode(SourceConfig.MODE_NATIVE);

        assertEquals(SourceSlot.RUNTIME, config.getActiveSource());
        assertFalse(config.isWebMode());

        config.setActiveMode(SourceConfig.MODE_WEB);
        assertEquals(SourceSlot.RUNTIME, config.getActiveSource());
        assertTrue(config.isWebMode());
    }

    @Test
    public void decodeOfNull_returnsDefaults() {
        SourceConfig decoded = SourceConfig.decode(null);

        assertEquals(SourceSlot.DEFAULT, decoded.getActiveSource());
        assertTrue(decoded.isFallbackEnabled());
    }

    @Test
    public void decodeOfGarbage_returnsDefaults() {
        SourceConfig decoded = SourceConfig.decode("not json");

        assertEquals(SourceSlot.DEFAULT, decoded.getActiveSource());
        assertTrue(decoded.isFallbackEnabled());
        assertEquals(SourceConfig.MODE_NATIVE, decoded.getActiveMode());
    }

    @Test
    public void unknownValues_fallBackSafely() {
        SourceConfig decoded = SourceConfig.decode(
                "{\"activeSource\": \"cloud\", \"activeMode\": \"hologram\", \"fallbackEnabled\": false}");

        assertEquals(SourceSlot.DEFAULT, decoded.getActiveSource());
        assertEquals(SourceConfig.MODE_NATIVE, decoded.getActiveMode());
        assertFalse(decoded.isFallbackEnabled());
    }

    @Test
    public void blankEntry_isNormalised() {
        SourceConfig config = new SourceConfig();
        config.setRuntimeEntry("   ");

        assertEquals("index.html", config.getRuntimeEntry());
    }
}
