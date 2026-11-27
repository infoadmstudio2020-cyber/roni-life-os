package com.roni.library.runtime;

import com.roni.library.runtime.container.WebViewContainer;
import com.roni.library.runtime.vitals.WebVitalsCollector;
import org.junit.Assert;
import org.junit.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class WebVitalsAndContainerTest {

    @Test
    public void testWebVitalsCollector() {
        WebVitalsCollector collector = new WebVitalsCollector();
        collector.recordFp(45);
        collector.recordFcp(60);
        collector.recordLcp(120);
        collector.recordDomReady(85);
        collector.recordLoadTime(150);

        Map<String, Object> metrics = collector.getMetrics();
        Assert.assertEquals(45L, metrics.get("fpMs"));
        Assert.assertEquals(60L, metrics.get("fcpMs"));
        Assert.assertEquals(120L, metrics.get("lcpMs"));
        Assert.assertEquals(85L, metrics.get("domReadyMs"));
        Assert.assertEquals(150L, metrics.get("pageLoadMs"));

        collector.reset();
        Map<String, Object> resetMetrics = collector.getMetrics();
        Assert.assertEquals(WebVitalsCollector.UNRECORDED, resetMetrics.get("fpMs"));
    }

    @Test
    public void testWebViewContainerLifecycle() {
        WebViewContainer container = new WebViewContainer("test-container-1");
        Assert.assertEquals("test-container-1", container.getContainerId());
        Assert.assertFalse(container.isDestroyed());
        Assert.assertTrue(container.isHeadless());

        container.loadUrl("https://domain.local/page");
        Assert.assertEquals("https://domain.local/page", container.getCurrentUrl());
        // Headless containers must not fabricate metrics.
        Assert.assertEquals(WebVitalsCollector.UNRECORDED,
                container.getVitalsCollector().getMetrics().get("pageLoadMs"));

        final AtomicReference<String> jsResult = new AtomicReference<>();
        container.evaluateJavascript("document.title", new WebViewContainer.JavascriptCallback() {
            @Override
            public void onResult(String result) {
                jsResult.set(result);
            }
        });
        Assert.assertNotNull(jsResult.get());
        Assert.assertTrue(jsResult.get().contains("unavailable"));

        container.destroy();
        Assert.assertTrue(container.isDestroyed());
    }
}
