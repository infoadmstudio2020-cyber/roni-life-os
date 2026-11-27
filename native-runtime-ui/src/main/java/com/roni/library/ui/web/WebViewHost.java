package com.roni.library.ui.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.bridge.BridgeConfig;
import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.bridge.WebBridge;
import com.roni.library.bridge.js.NativeJavascriptInterface;
import com.roni.library.ui.download.DownloadCenter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen capable WebView host for NativeRuntime web applications.
 *
 * <p>Supports remote URLs, APK assets, device files, ZIP archives, inline HTML and
 * content:// URIs. Local sources are served from a secure virtual HTTPS origin so
 * that fetch/XHR, service workers and modern browser APIs behave like a real server.</p>
 *
 * <p>The host automatically attaches the NativeRuntime JavaScript bridge, allowing the
 * web application to call native UI commands such as {@code ui.showToast},
 * {@code ui.showAlert}, {@code ui.showLoading}, {@code ui.setToolbarTitle} and
 * {@code ui.selectTab}.</p>
 */
public class WebViewHost extends FrameLayout {

    private static final String TAG = "NativeRuntimeWebHost";
    private static final int REQUEST_FILE_CHOOSER = 0x4E57;

    public interface Listener {
        void onPageStarted(String url);
        void onPageFinished(String url);
        void onProgressChanged(int progress);
        void onTitleReceived(String title);
        void onReceivedError(int errorCode, String description, String failingUrl);
        void onExternalLink(String url);
    }

    private final WebHostConfig config;

    private WebView webView;
    private ProgressBar progressBar;
    private View errorView;
    private TextView errorText;

    private WebResourceProvider provider;
    private WebSource currentSource;
    private volatile String activeHost = WebSourceResolver.VIRTUAL_HOST;
    private String currentUrl = "";
    private String bridgeInterfaceName;
    private boolean bridgeAttached;
    private boolean destroyed;
    private volatile int pageZoom = 100;
    private boolean progressBarEnabled;

    private ValueCallback<Uri[]> filePathCallback;
    private Listener listener;

    public WebViewHost(@NonNull Context context) {
        this(context, WebHostConfig.defaultConfig());
    }

    public WebViewHost(@NonNull Context context, @Nullable WebHostConfig config) {
        super(context);
        this.config = config != null ? config : WebHostConfig.defaultConfig();
        this.progressBarEnabled = this.config.isShowProgressBar();
        initView(context);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initView(Context context) {
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setBackgroundColor(config.getBackgroundColor());

        webView = new WebView(context);
        webView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(config.getBackgroundColor());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(config.isJavaScriptEnabled());
        settings.setDomStorageEnabled(config.isDomStorageEnabled());
        settings.setDatabaseEnabled(config.isDatabaseEnabled());
        settings.setUseWideViewPort(config.isUseWideViewPort());
        settings.setLoadWithOverviewMode(config.isLoadWithOverviewMode());
        settings.setSupportZoom(config.isAllowZoom());
        settings.setBuiltInZoomControls(config.isAllowZoom());
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(config.isAllowFileAccess());
        settings.setAllowContentAccess(config.isAllowContentAccess());
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(config.isMediaPlaybackRequiresUserGesture());
        settings.setCacheMode(mapCacheMode(config.getCacheMode()));
        settings.setTextZoom(config.getTextZoom());
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportMultipleWindows(config.isSupportMultipleWindows());
        settings.setJavaScriptCanOpenWindowsAutomatically(config.isSupportMultipleWindows());
        if (config.getInitialScale() > 0) {
            webView.setInitialScale(config.getInitialScale());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(config.isMixedContentAllowed()
                    ? WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(config.isSafeBrowsingEnabled());
        }
        if (!config.getUserAgentSuffix().isEmpty()) {
            settings.setUserAgentString(settings.getUserAgentString() + " " + config.getUserAgentSuffix());
        }

        webView.setLayerType(config.isHardwareAcceleration()
                ? WebView.LAYER_TYPE_HARDWARE : WebView.LAYER_TYPE_SOFTWARE, null);
        webView.setWebViewClient(new HostWebViewClient());
        webView.setWebChromeClient(new HostWebChromeClient());
        webView.setDownloadListener(new HostDownloadListener());
        if (config.isDownloadsEnabled()) {
            // Pages can push generated files (blob:/data:) through this bridge; the listener
            // below covers ordinary link downloads.
            webView.addJavascriptInterface(new DownloadCenter.JsBridge(getContext()),
                    DownloadCenter.JS_INTERFACE);
        }

        if (config.isDebugLogging() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookies.setAcceptThirdPartyCookies(webView, true);
            }
        } catch (Throwable ignored) {
        }

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        LayoutParams progressParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        progressBar.setLayoutParams(progressParams);
        progressBar.setMax(100);
        progressBar.setVisibility(GONE);

        errorView = buildErrorView(context);

        addView(webView);
        addView(progressBar);
        addView(errorView);

        attachBridge();
    }

    private View buildErrorView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        layout.setBackgroundColor(Color.WHITE);
        layout.setVisibility(GONE);

        errorText = new TextView(context);
        errorText.setTextColor(Color.parseColor("#334155"));
        errorText.setTextSize(15f);
        errorText.setGravity(Gravity.CENTER);
        layout.addView(errorText);

        Button retry = new Button(context);
        retry.setText("Retry");
        retry.setOnClickListener(v -> reload());
        layout.addView(retry);
        return layout;
    }

    private void attachBridge() {
        if (!config.isBridgeEnabled() || bridgeAttached || webView == null) return;
        try {
            NativeWebBridgeSDK sdk = NativeWebBridgeSDK.getInstance();
            if (!sdk.isInitialized()) {
                sdk.init(getContext(), BridgeConfig.builder()
                        .interfaceName(config.getBridgeInterfaceName())
                        .debugLogging(config.isDebugLogging())
                        .build());
            }
            WebBridge bridge = sdk.getBridge();
            if (bridge == null) return;

            bridgeInterfaceName = bridge.getConfig() != null
                    ? bridge.getConfig().getInterfaceName()
                    : config.getBridgeInterfaceName();

            webView.addJavascriptInterface(new NativeJavascriptInterface(bridge), bridgeInterfaceName);
            bridge.setOriginProvider(() -> {
                WebView current = webView;
                return current != null ? originOf(current.getUrl()) : null;
            });
            bridge.setWebEvaluator(script -> {
                WebView target = webView;
                if (target == null || destroyed) return;
                target.post(() -> {
                    WebView current = webView;
                    if (current != null && !destroyed) {
                        try {
                            current.evaluateJavascript(script, null);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            });
            bridgeAttached = true;
        } catch (Throwable t) {
            Log.w(TAG, "Unable to attach native bridge: " + t.getMessage());
        }
    }

    @Nullable
    private WebBridge getBridge() {
        try {
            return NativeWebBridgeSDK.getInstance().getBridge();
        } catch (Throwable t) {
            return null;
        }
    }

    private void injectBootstrap() {
        if (!bridgeAttached) return;
        WebBridge bridge = getBridge();
        if (bridge == null || webView == null || destroyed) return;
        try {
            webView.evaluateJavascript(bridge.getBootstrapScript(), null);
        } catch (Throwable ignored) {
        }
    }

    // ==================== Source loading ====================

    public void loadSource(@Nullable WebSource source) {
        if (destroyed || webView == null || source == null) return;

        // The first page of a source is the root of the history: anything the WebView held
        // before it (a blank placeholder, a previous project) must not be reachable by back.
        clearHistoryOnNextFinish = true;

        closeProvider();
        currentSource = source;

        WebSourceResolver.ResolvedSource resolved =
                new WebSourceResolver().resolve(getContext(), source);
        if (resolved.isError()) {
            showError(resolved.getErrorMessage());
            return;
        }

        provider = resolved.getProvider();
        activeHost = resolved.getVirtualHost();
        applyAccessFlags(resolved);
        hideError();
        trustBridgeOrigin(activeHost);

        if (resolved.isInline()) {
            currentUrl = resolved.getBaseUrl();
            webView.loadDataWithBaseURL(resolved.getBaseUrl(), resolved.getInlineHtml(),
                    "text/html", "UTF-8", null);
        } else {
            currentUrl = resolved.getUrl();
            Map<String, String> headers = source.getHeaders();
            if (headers.isEmpty()) {
                webView.loadUrl(resolved.getUrl());
            } else {
                webView.loadUrl(resolved.getUrl(), headers);
            }
        }
    }

    public void loadUrl(@Nullable String url) {
        loadSource(WebSource.fromUrl(url));
    }

    public void loadHtml(@Nullable String html, @Nullable String baseUrl) {
        loadSource(WebSource.inlineHtml(html).withBaseUrl(baseUrl));
    }

    private void applyAccessFlags(WebSourceResolver.ResolvedSource resolved) {        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        settings.setAllowFileAccess(config.isAllowFileAccess() || resolved.isAllowFileAccess());
        settings.setAllowContentAccess(config.isAllowContentAccess() || resolved.isAllowContentAccess());
    }

    private void closeProvider() {
        WebResourceProvider current = provider;
        provider = null;
        if (current != null) {
            try {
                current.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Trusts the origin the host is about to load, so a per-project virtual host can use the
     * bridge. Only the origin actually loaded is added — never a wildcard.
     */
    private void trustBridgeOrigin(String host) {
        if (host == null || host.isEmpty()) return;
        WebBridge bridge = getBridge();
        if (bridge != null) {
            bridge.allowOrigin(WebPaths.originOf(host));
        }
    }

    // ==================== Navigation ====================

    public void reload() {
        if (destroyed || webView == null) return;
        if (currentSource != null) {
            loadSource(currentSource);
        } else {
            webView.reload();
        }
    }

    public void stopLoading() {
        if (webView != null) webView.stopLoading();
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
    }

    public boolean canGoForward() {
        return webView != null && webView.canGoForward();
    }

    public void goForward() {
        if (webView != null && webView.canGoForward()) webView.goForward();
    }

    public void clearHistory() {
        if (webView != null) webView.clearHistory();
    }

    /** Drops the pre-load history once the first page of a source has finished. */
    private boolean clearHistoryOnNextFinish;

    /**
     * Sets the page zoom percentage (100 = default). Applied through CSS zoom so it works on
     * every API level, unlike the deprecated {@code setInitialScale}.
     */
    public void setPageZoom(int zoom) {
        int clamped = Math.max(25, Math.min(400, zoom));
        this.pageZoom = clamped;
        WebView target = webView;
        if (target == null || destroyed) return;
        float factor = clamped / 100f;
        target.post(() -> {
            WebView current = webView;
            if (current != null && !destroyed) {
                try {
                    current.evaluateJavascript(
                            "document.documentElement.style.zoom='" + factor + "'", null);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public int getPageZoom() {
        return pageZoom;
    }

    public void evaluateJavascript(@NonNull String script, @Nullable ValueCallback<String> callback) {
        if (webView != null && !destroyed) {
            webView.evaluateJavascript(script, callback);
        }
    }

    public void emitEvent(@NonNull String topic, @Nullable Map<String, Object> data) {
        WebBridge bridge = getBridge();
        if (bridge != null) {
            bridge.emitToWeb(topic, data != null ? data : new HashMap<>());
        }
    }

    // ==================== Lifecycle ====================

    public void onResume() {
        if (webView != null && !destroyed) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    public void onPause() {
        if (webView != null && !destroyed) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    public void saveState(@Nullable Bundle outState) {
        if (outState == null || webView == null || destroyed) return;
        try {
            webView.saveState(outState);
        } catch (Throwable ignored) {
        }
    }

    public void restoreState(@Nullable Bundle inState) {
        if (inState == null || webView == null || destroyed) return;
        try {
            webView.restoreState(inState);
        } catch (Throwable ignored) {
        }
    }

    public boolean handleBackPressed() {
        if (destroyed || webView == null) return false;
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != REQUEST_FILE_CHOOSER || filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    public void onDestroy() {
        if (destroyed) return;
        destroyed = true;
        closeProvider();

        WebBridge bridge = getBridge();
        if (bridge != null) {
            bridge.clearWebEvaluator();
            bridge.clearOriginProvider();
        }

        if (webView != null) {
            try {
                if (bridgeInterfaceName != null) {
                    webView.removeJavascriptInterface(bridgeInterfaceName);
                }
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                // Project runtimes keep their HTTP cache: wiping it here made every
                // re-open re-fetch the project's remote assets.
                if (config == null || config.isClearCacheOnDestroy()) {
                    webView.clearCache(true);
                }
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.setDownloadListener(null);
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        removeAllViews();
    }

    // ==================== Accessors ====================

    @Nullable
    public WebView getWebView() { return webView; }

    @NonNull
    public String getCurrentUrl() { return currentUrl; }

    @Nullable
    public WebSource getCurrentSource() { return currentSource; }

    @Nullable
    public WebResourceProvider getProvider() { return provider; }

    public boolean isDestroyed() { return destroyed; }

    public WebHostConfig getConfig() { return config; }

    private volatile WebDiagnosticsListener diagnosticsListener;

    public void setListener(@Nullable Listener listener) { this.listener = listener; }

    /**
     * Registers the Runtime Inspector diagnostics sink.
     *
     * <p>Optional: when no sink is registered the host behaves exactly as before, so the
     * inspector can be switched off without touching any call site.</p>
     */
    public void setDiagnosticsListener(@Nullable WebDiagnosticsListener listener) {
        this.diagnosticsListener = listener;
    }

    public void setProgressBarVisible(boolean visible) {
        progressBarEnabled = visible;
        if (progressBar != null) {
            progressBar.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    // ==================== Internal helpers ====================

    // ==================== Diagnostics ====================

    /**
     * Injected once per page load. It reports uncaught errors and unhandled rejections through
     * {@code console.error}, which the host already receives via {@code onConsoleMessage}.
     *
     * <p>Deliberately uses no {@code addJavascriptInterface}: a page cannot call back into
     * native code through this hook, so it is safe for untrusted origins and needs no change to
     * the origin allowlist.</p>
     */
    private static final String ERROR_REPORTER_JS =
            "(function(){if(window.__roniInspectorInstalled){return;}"
                    + "window.__roniInspectorInstalled=true;"
                    + "window.addEventListener('error',function(e){"
                    + "var m='[JS-ERROR] '+((e&&e.message)?e.message:'unknown');"
                    + "if(e&&e.filename){m+=' @'+e.filename+':'+e.lineno+':'+e.colno;}"
                    + "if(e&&e.error&&e.error.stack){m+='\\n'+e.error.stack;}"
                    + "console.error(m);},true);"
                    + "window.addEventListener('unhandledrejection',function(e){"
                    + "var r=e?e.reason:null;"
                    + "var m='[JS-REJECTION] '+((r&&r.message)?r.message:String(r));"
                    + "if(r&&r.stack){m+='\\n'+r.stack;}"
                    + "console.error(m);});})();";

    private void installErrorReporter(WebView view) {
        if (view == null || diagnosticsListener == null) return;
        try {
            view.evaluateJavascript(ERROR_REPORTER_JS, null);
        } catch (Throwable ignored) {
            // A page that refuses evaluation simply reports no JS errors.
        }
    }

    private void notifyPageEvent(String event, String url) {
        WebDiagnosticsListener sink = diagnosticsListener;
        if (sink == null) return;
        try {
            sink.onPageEvent(event, url != null ? url : "");
        } catch (Throwable ignored) {
        }
    }

    private void notifyResourceError(String url, String description, boolean mainFrame) {
        WebDiagnosticsListener sink = diagnosticsListener;
        if (sink == null) return;
        try {
            sink.onResourceError(url, description, mainFrame);
        } catch (Throwable ignored) {
        }
    }

    /** Maps a console message level onto an {@code android.util.Log} priority. */
    private static int priorityOf(android.webkit.ConsoleMessage.MessageLevel level) {
        if (level == null) return Log.INFO;
        switch (level) {
            case ERROR:
                return Log.ERROR;
            case WARNING:
                return Log.WARN;
            case DEBUG:
                return Log.DEBUG;
            case TIP:
                return Log.VERBOSE;
            case LOG:
            default:
                return Log.INFO;
        }
    }

    private void showProgress() {
        if (progressBar != null && progressBarEnabled) {
            progressBar.setVisibility(VISIBLE);
        }
    }

    private void hideProgress() {
        if (progressBar != null) progressBar.setVisibility(GONE);
    }

    private void showError(String message) {
        if (errorText != null) {
            errorText.setText(message != null && !message.isEmpty()
                    ? message : "Unable to load the web application.");
        }
        if (errorView != null) errorView.setVisibility(VISIBLE);
        if (webView != null) webView.setVisibility(GONE);
        hideProgress();
    }

    private void hideError() {
        if (errorView != null) errorView.setVisibility(GONE);
        if (webView != null) webView.setVisibility(VISIBLE);
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent;
            if (url.startsWith("intent:")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            Activity activity = findActivity(getContext());
            if (activity != null) {
                activity.startActivity(intent);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            if (listener != null) listener.onExternalLink(url);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private WebResourceResponse notFoundResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                new HashMap<>(), new ByteArrayInputStream("Not Found".getBytes(StandardCharsets.UTF_8)));
    }

    private boolean handleUrl(@Nullable Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        String lower = scheme.toLowerCase(Locale.US);

        if ("http".equals(lower) || "https".equals(lower)) {
            String host = activeHost;
            if (host != null && host.equals(uri.getHost())) return false;
            if (config.isOpenExternalLinksInBrowser() && config.isExternalHost(uri.getHost())) {
                openExternal(uri.toString());
                return true;
            }
            return false;
        }

        if ("file".equals(lower) || "content".equals(lower) || "about".equals(lower)
                || "data".equals(lower) || "javascript".equals(lower) || "blob".equals(lower)) {
            return false;
        }

        openExternal(uri.toString());
        return true;
    }

    private final class HostWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request != null ? request.getUrl() : null;
            String host = activeHost;
            if (uri == null || host == null || !host.equals(uri.getHost())) {
                return null;
            }
            WebResourceProvider current = provider;
            if (current == null) return notFoundResponse();

            String path = uri.getPath();
            // Path validation happens once, here, so every provider inherits the same rule
            // instead of each one re-implementing it.
            if (!WebPaths.isSafe(path)) {
                android.util.Log.w(TAG, "Rejected unsafe resource path: " + path);
                return notFoundResponse();
            }

            WebResource resource = current.open(path);
            if (resource == null) return notFoundResponse();

            Map<String, String> headers = new HashMap<>(resource.getHeaders());
            headers.put("Cache-Control", "no-cache");

            ResourceDecoder decoder = config.getResourceDecoder();
            if (decoder != null) {
                // Encrypted project: the provider hands back ciphertext. The decoder owns the
                // source stream and returns a body that is already authenticated — buffered for
                // small resources, chunk-verified for large ones. MIME type and status are
                // preserved, which is what keeps ES6 modules, CSS and SVG loading correctly.
                try {
                    java.io.InputStream body = decoder.decodeStream(path,
                            resource.getStream());
                    return new WebResourceResponse(resource.getMimeType(), resource.getEncoding(),
                            resource.getStatusCode(), resource.getReasonPhrase(), headers, body);
                } catch (Throwable error) {
                    android.util.Log.w(TAG, "Cannot decode " + path, error);
                    return notFoundResponse();
                }
            }

            return new WebResourceResponse(resource.getMimeType(), resource.getEncoding(),
                    resource.getStatusCode(), resource.getReasonPhrase(), headers, resource.getStream());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request != null ? request.getUrl() : null);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url != null ? Uri.parse(url) : null);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            currentUrl = url != null ? url : "";
            showProgress();
            hideError();
            if (listener != null) listener.onPageStarted(currentUrl);
            notifyPageEvent("PAGE_STARTED", currentUrl);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            currentUrl = url != null ? url : currentUrl;
            hideProgress();
            if (clearHistoryOnNextFinish) {
                clearHistoryOnNextFinish = false;
                view.clearHistory();
            }
            injectBootstrap();
            if (listener != null) listener.onPageFinished(currentUrl);
            notifyPageEvent("PAGE_FINISHED", currentUrl);
            installErrorReporter(view);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                int code = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && error != null)
                        ? error.getErrorCode() : -1;
                String description = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && error != null)
                        ? String.valueOf(error.getDescription()) : "Page load error";
                String failingUrl = request.getUrl() != null ? request.getUrl().toString() : "";
                showError(description);
                if (listener != null) listener.onReceivedError(code, description, failingUrl);
                notifyResourceError(failingUrl, description, true);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            showError(description);
            if (listener != null) listener.onReceivedError(errorCode, description, failingUrl);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            if (request != null && request.isForMainFrame() && errorResponse != null
                    && errorResponse.getStatusCode() >= 400) {
                showError("HTTP " + errorResponse.getStatusCode() + " "
                        + errorResponse.getReasonPhrase());
                notifyResourceError(request.getUrl() != null ? request.getUrl().toString() : "",
                        "HTTP " + errorResponse.getStatusCode(), true);
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view,
                                           android.webkit.RenderProcessGoneDetail detail) {
            notifyPageEvent("RENDERER_GONE", currentUrl);
            // Preserve the platform default (the host is torn down) — the inspector only
            // records the crash, it does not change recovery behaviour.
            return false;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (handler != null) handler.cancel();
            showError("Secure connection failed.");
        }
    }

    private final class HostWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progressBar != null && progressBarEnabled) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? VISIBLE : GONE);
            }
            if (listener != null) listener.onProgressChanged(newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (listener != null) listener.onTitleReceived(title);
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            if (consoleMessage == null) return false;
            if (config.isDebugLogging()) {
                Log.d(TAG, consoleMessage.message() + " @" + consoleMessage.lineNumber());
            }
            WebDiagnosticsListener sink = diagnosticsListener;
            if (sink != null) {
                try {
                    sink.onConsoleMessage(priorityOf(consoleMessage.messageLevel()),
                            consoleMessage.message(), consoleMessage.sourceId(),
                            consoleMessage.lineNumber());
                } catch (Throwable ignored) {
                    // Diagnostics must never break page execution.
                }
            }
            return false;
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (!config.isFileChooserEnabled() || callback == null) return false;
            Activity activity = findActivity(getContext());
            if (activity == null || params == null) return false;

            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            try {
                activity.startActivityForResult(params.createIntent(), REQUEST_FILE_CHOOSER);
                return true;
            } catch (Throwable t) {
                filePathCallback = null;
                return false;
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            if (request == null) return;
            WebPermissionHandler handler = config.getPermissionHandler();
            if (handler == null) {
                request.deny();
                return;
            }
            try {
                handler.onPermissionRequest(request, new WebPermissionHandler.PermissionResponder() {
                    @Override
                    public void grant(String[] resources) {
                        try {
                            request.grant(resources);
                        } catch (Throwable ignored) {
                            // Request already handled or the page navigated away.
                        }
                    }

                    @Override
                    public void deny() {
                        try {
                            request.deny();
                        } catch (Throwable ignored) {
                            // Request already handled or the page navigated away.
                        }
                    }
                });
            } catch (Throwable t) {
                android.util.Log.w(TAG, "Permission handler failed, denying", t);
                request.deny();
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin,
                GeolocationPermissions.Callback callback) {

            if (callback == null) {
                return;
            }

            WebPermissionHandler handler = config.getPermissionHandler();

            if (handler == null) {
                callback.invoke(origin, false, false);
                return;
            }

            try {
                handler.onGeolocationRequest(
                        origin,
                        (allow, retain) -> {
                            callback.invoke(origin, allow, retain);
                        }
                );

            } catch (Throwable t) {
                android.util.Log.w(
                        TAG,
                        "Geolocation handler failed, denying",
                        t
                );

                callback.invoke(origin, false, false);
            }
        }


    }

    private final class HostDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimeType, long contentLength) {
            if (!config.isDownloadsEnabled()) return;
            if (url == null || url.isEmpty()) return;
            if (url.startsWith("blob:")) {
                // Only the page can read a blob, so it converts the bytes and posts them back.
                String name = DownloadCenter.guessName(url, contentDisposition, mimeType);
                webView.evaluateJavascript(
                        DownloadCenter.blobScript(url, name, mimeType), null);
                return;
            }
            DownloadCenter.enqueue(getContext(), url, userAgent, contentDisposition,
                    mimeType, contentLength);
        }
    }

    private static int mapCacheMode(int mode) {
        switch (mode) {
            case WebHostConfig.CACHE_ELSE_NETWORK:
                return WebSettings.LOAD_CACHE_ELSE_NETWORK;
            case WebHostConfig.CACHE_NO_CACHE:
                return WebSettings.LOAD_NO_CACHE;
            case WebHostConfig.CACHE_ONLY:
                return WebSettings.LOAD_CACHE_ONLY;
            case WebHostConfig.CACHE_DEFAULT:
                return WebSettings.LOAD_DEFAULT;
            default:
                android.util.Log.w(TAG, "Unknown cache mode " + mode + ", using LOAD_DEFAULT");
                return WebSettings.LOAD_DEFAULT;
        }
    }

    /**
     * @return the scheme://host[:port] origin of {@code url}, or {@code null} when it cannot be
     *         determined (about:blank, data:, file:, javascript:)
     */
    static String originOf(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            String host = uri.getHost();
            if (host == null) return null;
            int port = uri.getPort();
            return port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
        } catch (Throwable t) {
            return null;
        }
    }
}
