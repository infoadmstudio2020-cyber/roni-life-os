package com.roni.library.runtime;

import com.roni.library.runtime.vitals.WebVitalsCollector;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * Every metric must be either a real measurement or {@link WebVitalsCollector#UNRECORDED}.
 */
public class WebVitalsCollectorTest {

    @Test
    public void unrecordedMetricsReturnMinusOne() {
        WebVitalsCollector collector = new WebVitalsCollector();
        Map<String, Object> metrics = collector.getMetrics();

        Assert.assertEquals(WebVitalsCollector.UNRECORDED, metrics.get("fpMs"));
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, metrics.get("fcpMs"));
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, metrics.get("lcpMs"));
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, metrics.get("domReadyMs"));
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, metrics.get("pageLoadMs"));
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, metrics.get("navigationDurationMs"));
    }

    @Test
    public void navigationStartProducesANonNegativeDuration() {
        WebVitalsCollector collector = new WebVitalsCollector();
        collector.recordNavigationStart();
        long duration = collector.getNavigationDurationMs();
        Assert.assertTrue(duration >= 0);
        collector.recordLoadTime(duration);
        Assert.assertEquals(duration, collector.getPageLoadMs());
    }

    @Test
    public void resetClearsEverything() {
        WebVitalsCollector collector = new WebVitalsCollector();
        collector.recordNavigationStart();
        collector.recordFp(10);
        collector.recordFcp(20);
        collector.recordLcp(30);
        collector.recordDomReady(40);
        collector.recordLoadTime(50);

        collector.reset();

        Assert.assertEquals(WebVitalsCollector.UNRECORDED, collector.getFpMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, collector.getFcpMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, collector.getLcpMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, collector.getDomReadyMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, collector.getPageLoadMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, collector.getNavigationDurationMs());
    }
}
