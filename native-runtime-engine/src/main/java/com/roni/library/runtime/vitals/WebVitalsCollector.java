package com.roni.library.runtime.vitals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and reports Web Vitals performance metrics (FP, FCP, LCP, DOM Ready, Load).
 *
 * <p>Every metric starts at {@link #UNRECORDED} (-1) and only changes when a caller records a
 * real measurement. Nothing in this class invents a value.</p>
 */
public class WebVitalsCollector {

    /** Sentinel returned for a metric that has never been recorded. */
    public static final long UNRECORDED = -1L;

    private final AtomicLong firstPaintMs = new AtomicLong(UNRECORDED);
    private final AtomicLong firstContentfulPaintMs = new AtomicLong(UNRECORDED);
    private final AtomicLong largestContentfulPaintMs = new AtomicLong(UNRECORDED);
    private final AtomicLong domReadyMs = new AtomicLong(UNRECORDED);
    private final AtomicLong pageLoadTimeMs = new AtomicLong(UNRECORDED);
    private final AtomicLong navigationStartNanos = new AtomicLong(0L);

    public WebVitalsCollector() {}

    /**
     * Marks the start of a navigation. Used to derive {@link #getNavigationDurationMs()}.
     */
    public void recordNavigationStart() {
        navigationStartNanos.set(System.nanoTime());
    }

    public void recordFp(long ms) {
        firstPaintMs.set(ms);
    }

    public void recordFcp(long ms) {
        firstContentfulPaintMs.set(ms);
    }

    public void recordLcp(long ms) {
        largestContentfulPaintMs.set(ms);
    }

    public void recordDomReady(long ms) {
        domReadyMs.set(ms);
    }

    public void recordLoadTime(long ms) {
        pageLoadTimeMs.set(ms);
    }

    public long getFpMs() {
        return firstPaintMs.get();
    }

    public long getFcpMs() {
        return firstContentfulPaintMs.get();
    }

    public long getLcpMs() {
        return largestContentfulPaintMs.get();
    }

    public long getDomReadyMs() {
        return domReadyMs.get();
    }

    public long getPageLoadMs() {
        return pageLoadTimeMs.get();
    }

    /**
     * @return milliseconds since {@link #recordNavigationStart()}, or {@link #UNRECORDED} when no
     *         navigation has started
     */
    public long getNavigationDurationMs() {
        long start = navigationStartNanos.get();
        if (start == 0L) {
            return UNRECORDED;
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("fpMs", firstPaintMs.get());
        metrics.put("fcpMs", firstContentfulPaintMs.get());
        metrics.put("lcpMs", largestContentfulPaintMs.get());
        metrics.put("domReadyMs", domReadyMs.get());
        metrics.put("pageLoadMs", pageLoadTimeMs.get());
        metrics.put("navigationDurationMs", getNavigationDurationMs());
        return metrics;
    }

    public void reset() {
        firstPaintMs.set(UNRECORDED);
        firstContentfulPaintMs.set(UNRECORDED);
        largestContentfulPaintMs.set(UNRECORDED);
        domReadyMs.set(UNRECORDED);
        pageLoadTimeMs.set(UNRECORDED);
        navigationStartNanos.set(0L);
    }
}
