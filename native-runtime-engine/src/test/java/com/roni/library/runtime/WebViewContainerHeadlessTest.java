package com.roni.library.runtime;

import com.roni.library.runtime.container.WebViewContainer;
import com.roni.library.runtime.vitals.WebVitalsCollector;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The headless contract: no WebView, no fabricated metrics, no fake script results.
 */
public class WebViewContainerHeadlessTest {

    @Test
    public void headlessLoadUrlRecordsNoVitals() {
        WebViewContainer container = new WebViewContainer("headless-1");
        container.loadUrl("https://example.test/page");

        Assert.assertTrue(container.isHeadless());
        Assert.assertNull(container.getWebView());
        Assert.assertEquals("https://example.test/page", container.getCurrentUrl());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED,
                container.getVitalsCollector().getPageLoadMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED,
                container.getVitalsCollector().getFpMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED,
                container.getVitalsCollector().getFcpMs());
        Assert.assertEquals(WebVitalsCollector.UNRECORDED,
                container.getVitalsCollector().getDomReadyMs());
    }

    @Test
    public void headlessEvaluateReportsUnavailable() {
        WebViewContainer container = new WebViewContainer("headless-2");
        final AtomicReference<String> result = new AtomicReference<>();
        container.evaluateJavascript("1 + 1", result::set);

        Assert.assertNotNull(result.get());
        Assert.assertTrue(result.get().contains("\"status\":\"unavailable\""));
        Assert.assertTrue(result.get().contains("\"reason\":\"headless\""));
    }

    @Test
    public void destroyIsIdempotent() {
        WebViewContainer container = new WebViewContainer("headless-3");
        container.destroy();
        container.destroy();
        Assert.assertTrue(container.isDestroyed());
        Assert.assertEquals("about:blank", container.getCurrentUrl());
    }

    @Test
    public void loadAfterDestroyIsIgnored() {
        WebViewContainer container = new WebViewContainer("headless-4");
        container.destroy();
        container.loadUrl("https://example.test/after");
        Assert.assertEquals("about:blank", container.getCurrentUrl());
    }
}
