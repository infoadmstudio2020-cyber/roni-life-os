package com.roni.library.runtime.container;

import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.roni.library.runtime.vitals.WebVitalsCollector;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Container wrapping a {@link WebView} lifecycle and page navigation states.
 *
 * <p>Two modes exist:</p>
 * <ul>
 *   <li><b>Attached</b> — constructed with a {@link WebView} (or via {@link #attachWebView}).
 *       Navigation, script evaluation and vitals are delegated to the real view.</li>
 *   <li><b>Headless</b> — constructed with {@link #WebViewContainer(String)} only. No WebView is
 *       attached, no metrics are collected, and {@code evaluateJavascript} reports
 *       {@code {"status":"unavailable","reason":"headless"}} instead of a fake success.</li>
 * </ul>
 */
public class WebViewContainer {

    public interface JavascriptCallback {
        void onResult(String result);
    }

    private static final String HEADLESS_RESULT =
            "{\"status\":\"unavailable\",\"reason\":\"headless\"}";

    private final String containerId;
    private volatile String currentUrl = "about:blank";
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final WebVitalsCollector vitalsCollector = new WebVitalsCollector();
    private volatile WebView webView;

    /**
     * Creates a headless container — no WebView attached; metrics are not collected.
     */
    public WebViewContainer(String containerId) {
        this.containerId = containerId != null ? containerId : String.valueOf(System.currentTimeMillis());
    }

    /**
     * Creates a container bound to a real {@link WebView}.
     */
    public WebViewContainer(String containerId, WebView webView) {
        this(containerId);
        attachWebView(webView);
    }

    public String getContainerId() {
        return containerId;
    }

    /**
     * Attaches a real WebView and installs the vitals {@link WebViewClient}.
     */
    public void attachWebView(WebView webView) {
        this.webView = webView;
        if (webView != null) {
            webView.setWebViewClient(new VitalsWebViewClient(null));
        }
    }

    /**
     * Attaches a real WebView, wrapping an existing client so its callbacks still fire.
     */
    public void attachWebView(WebView webView, WebViewClient delegate) {
        this.webView = webView;
        if (webView != null) {
            webView.setWebViewClient(new VitalsWebViewClient(delegate));
        }
    }

    public WebView getWebView() {
        return webView;
    }

    public boolean isHeadless() {
        return webView == null;
    }

    public String getCurrentUrl() {
        WebView view = webView;
        if (view != null) {
            String live = view.getUrl();
            if (live != null) {
                return live;
            }
        }
        return currentUrl;
    }

    public void loadUrl(String url) {
        if (destroyed.get()) {
            return;
        }
        String target = url != null ? url : "about:blank";
        this.currentUrl = target;
        WebView view = webView;
        if (view == null) {
            // Headless: navigation state only, no fabricated metrics.
            return;
        }
        vitalsCollector.recordNavigationStart();
        runOnMain(() -> view.loadUrl(target));
    }

    public void evaluateJavascript(String script, JavascriptCallback callback) {
        if (destroyed.get()) {
            if (callback != null) {
                callback.onResult(HEADLESS_RESULT);
            }
            return;
        }
        WebView view = webView;
        if (view == null) {
            if (callback != null) {
                callback.onResult(HEADLESS_RESULT);
            }
            return;
        }
        runOnMain(() -> view.evaluateJavascript(script != null ? script : "",
                value -> {
                    if (callback != null) {
                        callback.onResult(value);
                    }
                }));
    }

    public WebVitalsCollector getVitalsCollector() {
        return vitalsCollector;
    }

    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        WebView view = webView;
        webView = null;
        if (view != null) {
            runOnMain(view::destroy);
        }
        currentUrl = "about:blank";
        vitalsCollector.reset();
    }

    public boolean isDestroyed() {
        return destroyed.get();
    }

    private static void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new android.os.Handler(Looper.getMainLooper()).post(action);
        }
    }

    /**
     * Records real navigation timings. Nothing is recorded when the page never finishes or the
     * performance snippet fails.
     */
    private final class VitalsWebViewClient extends WebViewClient {
        private final WebViewClient delegate;

        VitalsWebViewClient(WebViewClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            vitalsCollector.recordNavigationStart();
            if (delegate != null) {
                delegate.onPageStarted(view, url, favicon);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            long duration = vitalsCollector.getNavigationDurationMs();
            if (duration >= 0) {
                vitalsCollector.recordLoadTime(duration);
            }
            view.evaluateJavascript(PERFORMANCE_SNIPPET, value -> applyPerformanceSample(value));
            if (delegate != null) {
                delegate.onPageFinished(view, url);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return delegate != null && delegate.shouldOverrideUrlLoading(view, url);
        }
    }

    private static final String PERFORMANCE_SNIPPET =
            "(function(){try{"
                    + "var p=performance.getEntriesByType('paint');"
                    + "var fp=-1,fcp=-1;"
                    + "for(var i=0;i<p.length;i++){"
                    + "if(p[i].name==='first-paint'){fp=Math.round(p[i].startTime);}"
                    + "if(p[i].name==='first-contentful-paint'){fcp=Math.round(p[i].startTime);}"
                    + "}"
                    + "var t=performance.timing;"
                    + "var dom=(t&&t.domContentLoadedEventEnd>0&&t.navigationStart>0)"
                    + "?t.domContentLoadedEventEnd-t.navigationStart:-1;"
                    + "return '{\"fp\":'+fp+',\"fcp\":'+fcp+',\"dom\":'+dom+'}';"
                    + "}catch(e){return '';}})()";

    private void applyPerformanceSample(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return;
        }
        String cleaned = json.replace("\\\"", "\"");
        long fp = readLong(cleaned, "fp");
        long fcp = readLong(cleaned, "fcp");
        long dom = readLong(cleaned, "dom");
        if (fp >= 0) {
            vitalsCollector.recordFp(fp);
        }
        if (fcp >= 0) {
            vitalsCollector.recordFcp(fcp);
        }
        if (dom >= 0) {
            vitalsCollector.recordDomReady(dom);
        }
    }

    private static long readLong(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex < 0) {
            return -1L;
        }
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return -1L;
        }
        int end = colon + 1;
        while (end < json.length() && (json.charAt(end) == ' ' || json.charAt(end) == '"')) {
            end++;
        }
        int start = end;
        while (end < json.length()
                && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (start == end) {
            return -1L;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
