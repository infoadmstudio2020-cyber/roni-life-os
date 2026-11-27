package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.core.AppConfig;

import org.junit.Test;

/**
 * অ্যাপ কনফিগারেশনের ডিফল্ট মান, ক্ল্যাম্পিং ও সিরিয়ালাইজেশন যাচাই।
 */
public class AppConfigTest {

    @Test
    public void defaults_areSensible() {
        AppConfig config = new AppConfig();
        assertTrue(config.isSplashEnabled());
        assertTrue(config.isBootstrapEnabled());
        assertTrue(config.isTelemetryEnabled());
        assertFalse(config.isDebugEnabled());
        assertEquals(AppConfig.DEFAULT_SPLASH_DURATION_MS, config.getSplashDurationMs());
        assertEquals("SYSTEM", config.getThemeMode());
    }

    @Test
    public void splashDuration_isClamped() {
        AppConfig config = new AppConfig();
        config.setSplashDurationMs(1);
        assertEquals(AppConfig.MIN_SPLASH_DURATION_MS, config.getSplashDurationMs());

        config.setSplashDurationMs(999999);
        assertEquals(AppConfig.MAX_SPLASH_DURATION_MS, config.getSplashDurationMs());
    }

    @Test
    public void roundTrip_preservesValues() {
        AppConfig config = new AppConfig();
        config.setSplashEnabled(false);
        config.setBootstrapEnabled(false);
        config.setDefaultProjectId("p-1");
        config.setLastOpenedProjectId("p-2");
        config.setThemeMode("DARK");
        config.setSeeded(true);

        AppConfig decoded = AppConfig.decode(config.encode());

        assertNotNull(decoded);
        assertFalse(decoded.isSplashEnabled());
        assertFalse(decoded.isBootstrapEnabled());
        assertEquals("p-1", decoded.getDefaultProjectId());
        assertEquals("p-2", decoded.getLastOpenedProjectId());
        assertEquals("DARK", decoded.getThemeMode());
        assertTrue(decoded.isSeeded());
    }

    @Test
    public void decodeOfNull_returnsNull() {
        assertEquals(null, AppConfig.decode(null));
    }

    @Test
    public void decodeOfGarbage_returnsNull() {
        assertEquals(null, AppConfig.decode("বৈধ নয়"));
    }
}
