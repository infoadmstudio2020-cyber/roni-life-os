package com.roni.library.ui.handler;

import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.ui.host.NativeUIHost;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;

/**
 * Full WebView control surface exposed to web applications through the
 * {@code web.*} bridge namespace.
 *
 * <p><b>U5 — relationship with {@code WebViewConfigurator}:</b> this handler is the
 * <em>imperative</em> path — it mutates a live {@code WebSettings} instance in response to a
 * bridge call. {@code com.roni.library.runtime.WebViewConfigurator} is the <em>declarative</em>
 * path — it applies a {@code NativeRuntimeEngineConfig} once when the container is created.
 * The two are intentionally separate: the configurator owns defaults, this handler owns runtime
 * overrides. Do not add per-setting methods to {@code WebViewConfigurator} for this handler;
 * keep new settings here.</p>
 */
public class NativeWebCommandHandler implements IApiHandler {

    public static final String[] SUPPORTED_ACTIONS = {
            "loadSource", "loadUrl", "loadHtml", "reload", "stop", "goBack", "goForward",
            "canGoBack", "canGoForward", "clearHistory", "getState", "evaluate",
            "setZoom", "getZoom", "setTextZoom", "getTextZoom",
            "setUserAgent", "getUserAgent", "getSettings", "setSettings",
            "clearCache", "setCacheMode", "setCookie", "getCookies", "clearCookies",
            "setBackgroundColor", "setProgressBarVisible", "emitEvent",
            "setJavaScript", "setDomStorage", "setAllowFileAccess", "setAllowContentAccess",
            "setMixedContentMode", "setSafeBrowsing", "setSupportZoom", "setBuiltInZoomControls",
            "setDisplayZoomControls", "setLoadWithOverviewMode", "setUseWideViewPort",
            "setDefaultTextEncodingName", "setStandardFontFamily", "setDefaultFontSize",
            "setMinimumFontSize", "setDatabaseEnabled", "setGeolocationEnabled", "setSaveFormData",
            "setJavaScriptCanOpenWindowsAutomatically", "setSupportMultipleWindows",
            "setMediaPlaybackRequiresUserGesture", "setOffscreenPreRaster", "setForceDark",
            "setAlgorithmicDarkeningAllowed", "setBlockNetworkImage", "setBlockNetworkLoads",
            "setLoadsImagesAutomatically"
    };

    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 10L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final NativeUIHost uiHost;

    // WebSettings has no geolocation-enabled getter. Keep the state owned by this handler.
    // All reads and writes happen on the main thread through handleRequest().
    private boolean geolocationEnabled = false;

    public NativeWebCommandHandler(NativeUIHost uiHost) {
        this.uiHost = uiHost != null ? uiHost : new NativeUIHost();
    }

    @Override
    public String getPath() {
        return "web";
    }

    @Override
    public String getAction() {
        return "*";
    }

    @Override
    public NativeApiResponse handleRequest(NativeApiRequest request) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return handleRequestOnMainThread(request);
        }

        FutureTask<NativeApiResponse> task = new FutureTask<>(
                () -> handleRequestOnMainThread(request)
        );

        if (!MAIN_HANDLER.post(task)) {
            String requestId = request != null ? request.getRequestId() : "unknown";
            return NativeApiResponse.error(requestId,
                    new NativeApiError("MAIN_THREAD_UNAVAILABLE",
                            "Unable to dispatch WebView command to the main thread"));
        }

        try {
            return task.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Do not interrupt a running UI operation. FutureTask cancellation prevents
            // execution if it has not started yet; an already-running operation may finish.
            task.cancel(false);
            String requestId = request != null ? request.getRequestId() : "unknown";
            return NativeApiResponse.error(requestId,
                    new NativeApiError("MAIN_THREAD_TIMEOUT",
                            "Timed out waiting for WebView command on the main thread"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String requestId = request != null ? request.getRequestId() : "unknown";
            return NativeApiResponse.error(requestId,
                    new NativeApiError("REQUEST_INTERRUPTED",
                            "Interrupted while waiting for the main thread"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = cause.getMessage() != null
                    ? cause.getMessage() : cause.getClass().getSimpleName();
            String requestId = request != null ? request.getRequestId() : "unknown";
            return NativeApiResponse.error(requestId,
                    new NativeApiError("INTERNAL_ERROR", message));
        }
    }

    /** Executes all WebView and WebSettings access on Android's main thread. */
    private NativeApiResponse handleRequestOnMainThread(NativeApiRequest request) {
        if (request == null) {
            return NativeApiResponse.error("unknown",
                    new NativeApiError("INVALID_REQUEST", "Request is null"));
        }

        String reqId = request.getRequestId();
        String action = normalizeAction(request.getAction());
        if (action.isEmpty()) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("MISSING_ACTION", "Action cannot be empty"));
        }

        WebViewHost host = host();
        WebView view = host != null ? host.getWebView() : null;
        WebSettings settings = view != null ? view.getSettings() : null;

        try {
            switch (action) {
                case "loadSource": {
                    if (host == null) return unavailable(reqId);
                    WebSource source = parseWebSource(request);
                    host.loadSource(source);
                    return success(reqId, "loaded", true);
                }
                case "loadUrl": {
                    if (host == null) return unavailable(reqId);
                    host.loadUrl(request.getStringParam("url", ""));
                    return success(reqId, "loaded", true);
                }
                case "loadHtml": {
                    if (host == null) return unavailable(reqId);
                    host.loadHtml(request.getStringParam("html", ""),
                            request.getStringParam("baseUrl", ""));
                    return success(reqId, "loaded", true);
                }
                case "reload": {
                    if (host == null) return unavailable(reqId);
                    host.reload();
                    return success(reqId, "reloaded", true);
                }
                case "stop": {
                    if (host == null) return unavailable(reqId);
                    host.stopLoading();
                    return success(reqId, "stopped", true);
                }
                case "goBack": {
                    if (host == null) return unavailable(reqId);
                    host.goBack();
                    return success(reqId, "navigated", true);
                }
                case "goForward": {
                    if (host == null) return unavailable(reqId);
                    host.goForward();
                    return success(reqId, "navigated", true);
                }
                case "canGoBack": {
                    return success(reqId, "canGoBack", host != null && host.canGoBack());
                }
                case "canGoForward": {
                    return success(reqId, "canGoForward", host != null && host.canGoForward());
                }
                case "clearHistory": {
                    if (host == null) return unavailable(reqId);
                    host.clearHistory();
                    return success(reqId, "cleared", true);
                }
                case "getState": {
                    Map<String, Object> data = new HashMap<>();
                    data.put("url", host != null ? host.getCurrentUrl() : "");
                    data.put("canGoBack", host != null && host.canGoBack());
                    data.put("canGoForward", host != null && host.canGoForward());
                    if (view != null) {
                        data.put("title", view.getTitle());
                        data.put("progress", view.getProgress());
                    }
                    return NativeApiResponse.success(reqId, data);
                }
                case "evaluate": {
                    if (host == null) return unavailable(reqId);
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        return NativeApiResponse.error(reqId, new NativeApiError(
                                "MAIN_THREAD_BLOCKED",
                                "web.evaluate must be called from a background thread"));
                    }
                    final CountDownLatch latch = new CountDownLatch(1);
                    final String[] result = new String[1];
                    host.evaluateJavascript(request.getStringParam("script", ""), value -> {
                        result[0] = value;
                        latch.countDown();
                    });
                    boolean completed;
                    try {
                        completed = latch.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        completed = false;
                    }
                    if (!completed) {
                        Map<String, Object> timeout = new HashMap<>();
                        timeout.put("evaluated", false);
                        timeout.put("error", "TIMEOUT");
                        return NativeApiResponse.success(reqId, timeout);
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("evaluated", true);
                    data.put("result", result[0]);
                    return NativeApiResponse.success(reqId, data);
                }
                case "setZoom": {
                    if (host == null) return unavailable(reqId);
                    int zoom = (int) longValue(request.getParameters().get("zoom"), 100);
                    host.setPageZoom(zoom);
                    return success(reqId, "zoom", host.getPageZoom());
                }
                case "getZoom": {
                    if (host == null) return unavailable(reqId);
                    return success(reqId, "zoom", host.getPageZoom());
                }
                case "setTextZoom": {
                    if (settings == null) return unavailable(reqId);
                    settings.setTextZoom((int) longValue(request.getParameters().get("zoom"), 100));
                    return success(reqId, "zoom", settings.getTextZoom());
                }
                case "getTextZoom": {
                    if (settings == null) return unavailable(reqId);
                    return success(reqId, "zoom", settings.getTextZoom());
                }
                case "setUserAgent": {
                    if (settings == null) return unavailable(reqId);
                    settings.setUserAgentString(request.getStringParam("userAgent", ""));
                    return success(reqId, "userAgent", settings.getUserAgentString());
                }
                case "getUserAgent": {
                    if (settings == null) return unavailable(reqId);
                    return success(reqId, "userAgent", settings.getUserAgentString());
                }
                case "getSettings": {
                    if (settings == null) return unavailable(reqId);
                    return NativeApiResponse.success(reqId, readSettings(settings));
                }
                case "setSettings": {
                    if (settings == null) return unavailable(reqId);
                    applySettings(settings, request.getParameters());
                    return NativeApiResponse.success(reqId, readSettings(settings));
                }
                case "clearCache": {
                    if (view == null) return unavailable(reqId);
                    view.clearCache(true);
                    return success(reqId, "cleared", true);
                }
                case "setCacheMode": {
                    if (settings == null) return unavailable(reqId);
                    applyCacheMode(settings, request.getStringParam("mode", "default"));
                    return success(reqId, "cacheMode", settings.getCacheMode());
                }
                case "setCookie": {
                    CookieManager manager = CookieManager.getInstance();
                    manager.setAcceptCookie(true);
                    manager.setCookie(request.getStringParam("url", ""),
                            request.getStringParam("value", ""));
                    manager.flush();
                    return success(reqId, "set", true);
                }
                case "getCookies": {
                    String cookies = CookieManager.getInstance()
                            .getCookie(request.getStringParam("url", ""));
                    return success(reqId, "cookies", cookies != null ? cookies : "");
                }
                case "clearCookies": {
                    CookieManager manager = CookieManager.getInstance();
                    manager.removeAllCookies(null);
                    manager.flush();
                    return success(reqId, "cleared", true);
                }
                case "setBackgroundColor": {
                    if (view == null) return unavailable(reqId);
                    view.setBackgroundColor(parseColor(request.getStringParam("color", "#FFFFFF")));
                    return success(reqId, "color", request.getStringParam("color", "#FFFFFF"));
                }
                case "setProgressBarVisible": {
                    if (host == null) return unavailable(reqId);
                    boolean visible = booleanValue(request.getParameters().get("visible"), true);
                    host.setProgressBarVisible(visible);
                    return success(reqId, "visible", visible);
                }
                case "emitEvent": {
                    if (host == null) return unavailable(reqId);
                    String topic = request.getStringParam("topic", "web.event");
                    Object raw = request.getParameters().get("data");
                    Map<String, Object> data = new HashMap<>();
                    if (raw instanceof Map) {
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                            data.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    host.emitEvent(topic, data);
                    return success(reqId, "emitted", true);
                }
                case "setJavaScript": {
                    if (settings == null) return unavailable(reqId);
                    settings.setJavaScriptEnabled(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getJavaScriptEnabled());
                }
                case "setDomStorage": {
                    if (settings == null) return unavailable(reqId);
                    settings.setDomStorageEnabled(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getDomStorageEnabled());
                }
                case "setAllowFileAccess": {
                    if (settings == null) return unavailable(reqId);
                    settings.setAllowFileAccess(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getAllowFileAccess());
                }
                case "setAllowContentAccess": {
                    if (settings == null) return unavailable(reqId);
                    settings.setAllowContentAccess(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getAllowContentAccess());
                }
                case "setMixedContentMode": {
                    if (settings == null) return unavailable(reqId);
                    applyMixedContentMode(settings, request.getStringParam("mode", "compatibility"));
                    return success(reqId, "mode", request.getStringParam("mode", "compatibility"));
                }
                case "setSafeBrowsing": {
                    if (settings == null) return unavailable(reqId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.setSafeBrowsingEnabled(booleanValue(request.getParameters().get("enabled"), true));
                    }
                    return success(reqId, "enabled", booleanValue(request.getParameters().get("enabled"), true));
                }
                case "setSupportZoom": {
                    if (settings == null) return unavailable(reqId);
                    settings.setSupportZoom(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.supportZoom());
                }
                case "setBuiltInZoomControls": {
                    if (settings == null) return unavailable(reqId);
                    settings.setBuiltInZoomControls(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getBuiltInZoomControls());
                }
                case "setDisplayZoomControls": {
                    if (settings == null) return unavailable(reqId);
                    settings.setDisplayZoomControls(booleanValue(request.getParameters().get("enabled"), false));
                    return success(reqId, "enabled", settings.getDisplayZoomControls());
                }
                case "setLoadWithOverviewMode": {
                    if (settings == null) return unavailable(reqId);
                    settings.setLoadWithOverviewMode(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getLoadWithOverviewMode());
                }
                case "setUseWideViewPort": {
                    if (settings == null) return unavailable(reqId);
                    settings.setUseWideViewPort(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getUseWideViewPort());
                }
                case "setDefaultTextEncodingName": {
                    if (settings == null) return unavailable(reqId);
                    settings.setDefaultTextEncodingName(request.getStringParam("encoding", "UTF-8"));
                    return success(reqId, "encoding", settings.getDefaultTextEncodingName());
                }
                case "setStandardFontFamily": {
                    if (settings == null) return unavailable(reqId);
                    settings.setStandardFontFamily(request.getStringParam("family", "sans-serif"));
                    return success(reqId, "family", settings.getStandardFontFamily());
                }
                case "setDefaultFontSize": {
                    if (settings == null) return unavailable(reqId);
                    settings.setDefaultFontSize((int) longValue(request.getParameters().get("size"), 16));
                    return success(reqId, "size", settings.getDefaultFontSize());
                }
                case "setMinimumFontSize": {
                    if (settings == null) return unavailable(reqId);
                    settings.setMinimumFontSize((int) longValue(request.getParameters().get("size"), 8));
                    return success(reqId, "size", settings.getMinimumFontSize());
                }
                case "setDatabaseEnabled": {
                    if (settings == null) return unavailable(reqId);
                    settings.setDatabaseEnabled(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getDatabaseEnabled());
                }
                case "setGeolocationEnabled": {
                    if (settings == null) return unavailable(reqId);

                    boolean enabled = booleanValue(
                            request.getParameters().get("enabled"),
                            false
                    );

                    geolocationEnabled = enabled;
                    settings.setGeolocationEnabled(enabled);

                    return success(reqId, "enabled", geolocationEnabled);
                }
                case "setSaveFormData": {
                    if (settings == null) return unavailable(reqId);
                    settings.setSaveFormData(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getSaveFormData());
                }
                case "setJavaScriptCanOpenWindowsAutomatically": {
                    if (settings == null) return unavailable(reqId);
                    settings.setJavaScriptCanOpenWindowsAutomatically(
                            booleanValue(request.getParameters().get("enabled"), false));
                    return success(reqId, "enabled", settings.getJavaScriptCanOpenWindowsAutomatically());
                }
                case "setSupportMultipleWindows": {
                    if (settings == null) return unavailable(reqId);
                    settings.setSupportMultipleWindows(booleanValue(request.getParameters().get("enabled"), false));
                    return success(reqId, "enabled", settings.supportMultipleWindows());
                }
                case "setMediaPlaybackRequiresUserGesture": {
                    if (settings == null) return unavailable(reqId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        settings.setMediaPlaybackRequiresUserGesture(
                                booleanValue(request.getParameters().get("enabled"), true));
                    }
                    return success(reqId, "enabled", booleanValue(request.getParameters().get("enabled"), true));
                }
                case "setOffscreenPreRaster": {
                    if (settings == null) return unavailable(reqId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        settings.setOffscreenPreRaster(booleanValue(request.getParameters().get("enabled"), false));
                    }
                    return success(reqId, "enabled", booleanValue(request.getParameters().get("enabled"), false));
                }
                case "setForceDark": {
                    if (settings == null) return unavailable(reqId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        applyForceDark(settings, request.getStringParam("mode", "auto"));
                    }
                    return success(reqId, "mode", request.getStringParam("mode", "auto"));
                }
                case "setAlgorithmicDarkeningAllowed": {
                    if (settings == null) return unavailable(reqId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        settings.setAlgorithmicDarkeningAllowed(
                                booleanValue(request.getParameters().get("enabled"), true));
                    }
                    return success(reqId, "enabled", booleanValue(request.getParameters().get("enabled"), true));
                }
                case "setBlockNetworkImage": {
                    if (settings == null) return unavailable(reqId);
                    settings.setBlockNetworkImage(booleanValue(request.getParameters().get("enabled"), false));
                    return success(reqId, "enabled", settings.getBlockNetworkImage());
                }
                case "setBlockNetworkLoads": {
                    if (settings == null) return unavailable(reqId);
                    settings.setBlockNetworkLoads(booleanValue(request.getParameters().get("enabled"), false));
                    return success(reqId, "enabled", settings.getBlockNetworkLoads());
                }
                case "setLoadsImagesAutomatically": {
                    if (settings == null) return unavailable(reqId);
                    settings.setLoadsImagesAutomatically(booleanValue(request.getParameters().get("enabled"), true));
                    return success(reqId, "enabled", settings.getLoadsImagesAutomatically());
                }
                default:
                    return NativeApiResponse.error(reqId,
                            new NativeApiError("UNKNOWN_ACTION", "Action not supported: " + action));
            }
        } catch (Throwable t) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("INTERNAL_ERROR", t.getMessage()));
        }
    }

    private WebViewHost host() {
        return uiHost.getUIComponentManager() != null
                ? uiHost.getUIComponentManager().getWebViewHost() : null;
    }

    private NativeApiResponse unavailable(String reqId) {
        return NativeApiResponse.error(reqId,
                new NativeApiError("WEBVIEW_UNAVAILABLE", "WebView host is not attached"));
    }

    private NativeApiResponse success(String reqId, String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return NativeApiResponse.success(reqId, data);
    }

    private String normalizeAction(String action) {
        if (action == null) return "";
        String value = action.trim();
        if (value.startsWith("web.")) return value.substring(4);
        if (value.startsWith("/web/")) return value.substring(5);
        return value;
    }

    private WebSource parseWebSource(NativeApiRequest request) {
        String type = request.getStringParam("type", "remote").toLowerCase(Locale.US);
        String location = request.getStringParam("location", "");
        String entryPoint = request.getStringParam("entryPoint", "");
        WebSource source;
        switch (type) {
            case "asset": source = WebSource.asset(location); break;
            case "file": source = WebSource.file(location); break;
            case "zip": source = WebSource.zipAsset(location); break;
            case "zipfile": source = WebSource.zipFile(location); break;
            case "inline": source = WebSource.inlineHtml(request.getStringParam("html", "")); break;
            case "content": source = WebSource.contentUri(location); break;
            default: source = WebSource.remote(location); break;
        }
        if (!entryPoint.isEmpty()) source = source.withEntryPoint(entryPoint);
        return source;
    }

    private Map<String, Object> readSettings(WebSettings settings) {
        Map<String, Object> data = new HashMap<>();

        data.put("javaScriptEnabled", settings.getJavaScriptEnabled());
        data.put("domStorageEnabled", settings.getDomStorageEnabled());
        data.put("databaseEnabled", settings.getDatabaseEnabled());
        data.put("geolocationEnabled", geolocationEnabled);
        data.put("saveFormData", settings.getSaveFormData());
        data.put("supportZoom", settings.supportZoom());
        data.put("builtInZoomControls", settings.getBuiltInZoomControls());
        data.put("displayZoomControls", settings.getDisplayZoomControls());
        data.put("loadWithOverviewMode", settings.getLoadWithOverviewMode());
        data.put("useWideViewPort", settings.getUseWideViewPort());
        data.put("defaultTextEncodingName", settings.getDefaultTextEncodingName());
        data.put("standardFontFamily", settings.getStandardFontFamily());
        data.put("defaultFontSize", settings.getDefaultFontSize());
        data.put("minimumFontSize", settings.getMinimumFontSize());
        data.put("textZoom", settings.getTextZoom());
        data.put("userAgent", settings.getUserAgentString());
        data.put("allowFileAccess", settings.getAllowFileAccess());
        data.put("allowContentAccess", settings.getAllowContentAccess());

        data.put("javaScriptCanOpenWindowsAutomatically",
                settings.getJavaScriptCanOpenWindowsAutomatically());

        data.put("supportMultipleWindows", settings.supportMultipleWindows());
        data.put("blockNetworkImage", settings.getBlockNetworkImage());
        data.put("blockNetworkLoads", settings.getBlockNetworkLoads());
        data.put("loadsImagesAutomatically", settings.getLoadsImagesAutomatically());
        data.put("cacheMode", settings.getCacheMode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            data.put("mediaPlaybackRequiresUserGesture",
                    settings.getMediaPlaybackRequiresUserGesture());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            data.put("mixedContentMode", settings.getMixedContentMode());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            data.put("offscreenPreRaster", settings.getOffscreenPreRaster());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            data.put("safeBrowsingEnabled", settings.getSafeBrowsingEnabled());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            data.put("forceDark", settings.getForceDark());
        }

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                data.put("algorithmicDarkeningAllowed",
                        settings.isAlgorithmicDarkeningAllowed());
            } catch (NoSuchMethodError ignored) {
                data.put("algorithmicDarkeningAllowed", false);
            }
        }

        return data;
    }
    private void applySettings(WebSettings settings, Map<String, Object> params) {
        if (params == null) return;
        if (params.containsKey("javaScriptEnabled")) {
            settings.setJavaScriptEnabled(booleanValue(params.get("javaScriptEnabled"), true));
        }
        if (params.containsKey("domStorageEnabled")) {
            settings.setDomStorageEnabled(booleanValue(params.get("domStorageEnabled"), true));
        }
        if (params.containsKey("databaseEnabled")) {
            settings.setDatabaseEnabled(booleanValue(params.get("databaseEnabled"), true));
        }
        if (params.containsKey("geolocationEnabled")) {
            geolocationEnabled = booleanValue(params.get("geolocationEnabled"), false);
            settings.setGeolocationEnabled(geolocationEnabled);
        }
        if (params.containsKey("saveFormData")) {
            settings.setSaveFormData(booleanValue(params.get("saveFormData"), true));
        }
        if (params.containsKey("supportZoom")) {
            settings.setSupportZoom(booleanValue(params.get("supportZoom"), true));
        }
        if (params.containsKey("builtInZoomControls")) {
            settings.setBuiltInZoomControls(booleanValue(params.get("builtInZoomControls"), true));
        }
        if (params.containsKey("displayZoomControls")) {
            settings.setDisplayZoomControls(booleanValue(params.get("displayZoomControls"), false));
        }
        if (params.containsKey("loadWithOverviewMode")) {
            settings.setLoadWithOverviewMode(booleanValue(params.get("loadWithOverviewMode"), true));
        }
        if (params.containsKey("useWideViewPort")) {
            settings.setUseWideViewPort(booleanValue(params.get("useWideViewPort"), true));
        }
        if (params.containsKey("defaultTextEncodingName")) {
            settings.setDefaultTextEncodingName(stringValue(params.get("defaultTextEncodingName"), "UTF-8"));
        }
        if (params.containsKey("standardFontFamily")) {
            settings.setStandardFontFamily(stringValue(params.get("standardFontFamily"), "sans-serif"));
        }
        if (params.containsKey("defaultFontSize")) {
            settings.setDefaultFontSize((int) longValue(params.get("defaultFontSize"), 16));
        }
        if (params.containsKey("minimumFontSize")) {
            settings.setMinimumFontSize((int) longValue(params.get("minimumFontSize"), 8));
        }
        if (params.containsKey("textZoom")) {
            settings.setTextZoom((int) longValue(params.get("textZoom"), 100));
        }
        if (params.containsKey("userAgent")) {
            settings.setUserAgentString(stringValue(params.get("userAgent"), ""));
        }
        if (params.containsKey("allowFileAccess")) {
            settings.setAllowFileAccess(booleanValue(params.get("allowFileAccess"), true));
        }
        if (params.containsKey("allowContentAccess")) {
            settings.setAllowContentAccess(booleanValue(params.get("allowContentAccess"), true));
        }
        if (params.containsKey("javaScriptCanOpenWindowsAutomatically")) {
            settings.setJavaScriptCanOpenWindowsAutomatically(
                    booleanValue(params.get("javaScriptCanOpenWindowsAutomatically"), false));
        }
        if (params.containsKey("supportMultipleWindows")) {
            settings.setSupportMultipleWindows(booleanValue(params.get("supportMultipleWindows"), false));
        }
        if (params.containsKey("blockNetworkImage")) {
            settings.setBlockNetworkImage(booleanValue(params.get("blockNetworkImage"), false));
        }
        if (params.containsKey("blockNetworkLoads")) {
            settings.setBlockNetworkLoads(booleanValue(params.get("blockNetworkLoads"), false));
        }
        if (params.containsKey("loadsImagesAutomatically")) {
            settings.setLoadsImagesAutomatically(booleanValue(params.get("loadsImagesAutomatically"), true));
        }
        if (params.containsKey("cacheMode")) {
            applyCacheMode(settings, stringValue(params.get("cacheMode"), "default"));
        }
        if (params.containsKey("mixedContentMode")) {
            applyMixedContentMode(settings, stringValue(params.get("mixedContentMode"), "compatibility"));
        }
        if (params.containsKey("mediaPlaybackRequiresUserGesture")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(
                    booleanValue(params.get("mediaPlaybackRequiresUserGesture"), true));
        }
        if (params.containsKey("offscreenPreRaster")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(booleanValue(params.get("offscreenPreRaster"), false));
        }
        if (params.containsKey("safeBrowsingEnabled")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(booleanValue(params.get("safeBrowsingEnabled"), true));
        }
        if (params.containsKey("forceDark")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            applyForceDark(settings, stringValue(params.get("forceDark"), "auto"));
        }
        if (params.containsKey("algorithmicDarkeningAllowed")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(
                    booleanValue(params.get("algorithmicDarkeningAllowed"), true));
        }
    }

    private void applyCacheMode(WebSettings settings, String mode) {
        String value = mode != null ? mode.toLowerCase(Locale.US) : "default";
        switch (value) {
            case "cache": settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK); break;
            case "no_cache": settings.setCacheMode(WebSettings.LOAD_NO_CACHE); break;
            case "cache_only": settings.setCacheMode(WebSettings.LOAD_CACHE_ONLY); break;
            default: settings.setCacheMode(WebSettings.LOAD_DEFAULT); break;
        }
    }

    private void applyMixedContentMode(WebSettings settings, String mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        String value = mode != null ? mode.toLowerCase(Locale.US) : "compatibility";
        switch (value) {
            case "always": settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); break;
            case "never": settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); break;
            default: settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); break;
        }
    }

    private void applyForceDark(WebSettings settings, String mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        String value = mode != null ? mode.toLowerCase(Locale.US) : "auto";
        switch (value) {
            case "on": settings.setForceDark(WebSettings.FORCE_DARK_ON); break;
            case "off": settings.setForceDark(WebSettings.FORCE_DARK_OFF); break;
            default: settings.setForceDark(WebSettings.FORCE_DARK_AUTO); break;
        }
    }

    private int parseColor(String value) {
        try {
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return Color.WHITE;
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }
}
