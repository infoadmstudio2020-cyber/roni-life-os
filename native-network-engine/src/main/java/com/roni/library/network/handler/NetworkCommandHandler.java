package com.roni.library.network.handler;

import com.roni.library.contracts.api.IApiCallback;
import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.network.http.HttpMethod;
import com.roni.library.network.http.HttpRequest;
import com.roni.library.network.http.HttpResponse;
import com.roni.library.network.service.NativeNetworkService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Executes one HTTP request through the existing {@link NativeNetworkService}.
 *
 * <p>Dispatched by {@code NativeRouter.dispatch()}, which runs handlers on its own
 * executor — so the blocking {@code execute()} call never touches the UI thread.
 * {@code dispatchSync()} callers must not invoke this from the main thread.</p>
 *
 * <p>Payload: {@code { url, method, headers, body, timeoutMs }}.</p>
 */
public class NetworkCommandHandler implements IApiHandler {

    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final int MAX_TIMEOUT_MS = 120000;

    private final NativeNetworkService networkService;

    public NetworkCommandHandler(NativeNetworkService networkService) {
        this.networkService = networkService;
    }

    public String getRoutePrefix() {
        return "network";
    }

    @Override
    public String getPath() {
        return "network";
    }

    @Override
    public String getAction() {
        return "*";
    }

    @Override
    public NativeApiResponse handleRequest(NativeApiRequest request) {
        if (request == null) {
            return NativeApiResponse.error("req_null", 400,
                    new NativeApiError("NULL_REQUEST", "Request is null"));
        }

        String reqId = request.getRequestId();
        Map<String, Object> params = request.getParameters();
        if (params == null) {
            params = new HashMap<>();
        }

        Object rawUrl = params.get("url");
        String url = rawUrl instanceof String ? ((String) rawUrl).trim() : "";
        if (url.isEmpty()) {
            return NativeApiResponse.error(reqId, 400,
                    new NativeApiError("INVALID_URL", "Parameter 'url' is required"));
        }
        if (!isAllowedScheme(url)) {
            return NativeApiResponse.error(reqId, 400,
                    new NativeApiError("UNSUPPORTED_SCHEME",
                            "Only http:// and https:// URLs are allowed"));
        }

        HttpMethod method = parseMethod(params.get("method"));
        if (method == null) {
            return NativeApiResponse.error(reqId, 400,
                    new NativeApiError("UNSUPPORTED_METHOD",
                            "Supported methods: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS"));
        }

        int timeoutMs = parseTimeout(params.get("timeoutMs"));

        try {
            HttpRequest.Builder builder = HttpRequest.builder()
                    .setUrl(url)
                    .setMethod(method)
                    .setConnectTimeoutMs(timeoutMs)
                    .setReadTimeoutMs(timeoutMs);

            Object body = params.get("body");
            if (body instanceof String && !((String) body).isEmpty()) {
                builder.setBody((String) body);
            }

            Object headers = params.get("headers");
            if (headers instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) headers).entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    String key = String.valueOf(entry.getKey());
                    if (isForbiddenHeader(key)) {
                        return NativeApiResponse.error(reqId, 400,
                                new NativeApiError("FORBIDDEN_HEADER",
                                        "Header not allowed from JavaScript: " + key));
                    }
                    builder.addHeader(key, String.valueOf(entry.getValue()));
                }
            }

            HttpResponse response = networkService.getHttpClient().execute(builder.build());

            Map<String, Object> data = new HashMap<>();
            data.put("statusCode", response.getStatusCode());
            data.put("body", response.getBody());
            data.put("latencyMs", response.getLatencyMs());
            data.put("method", method.name());
            data.put("url", url);
            return NativeApiResponse.success(reqId, data);
        } catch (Throwable error) {
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            return NativeApiResponse.error(reqId, 500,
                    new NativeApiError("NETWORK_ERROR", message));
        }
    }

    private static boolean isAllowedScheme(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isForbiddenHeader(String key) {
        String lower = key.toLowerCase(Locale.US);
        return lower.equals("host") || lower.equals("content-length") || lower.equals("connection");
    }

    private static HttpMethod parseMethod(Object raw) {
        if (!(raw instanceof String)) {
            return HttpMethod.GET;
        }
        String value = ((String) raw).trim().toUpperCase(Locale.US);
        if (value.isEmpty()) {
            return HttpMethod.GET;
        }
        for (HttpMethod candidate : HttpMethod.values()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    private static int parseTimeout(Object raw) {
        int value = DEFAULT_TIMEOUT_MS;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                value = Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                value = DEFAULT_TIMEOUT_MS;
            }
        }
        if (value <= 0) {
            value = DEFAULT_TIMEOUT_MS;
        }
        return Math.min(value, MAX_TIMEOUT_MS);
    }

    public void handle(NativeApiRequest request, IApiCallback callback) {
        if (callback == null) {
            return;
        }
        NativeApiResponse response = handleRequest(request);
        if (response.isSuccess()) {
            callback.onResponse(response);
        } else {
            callback.onError(response.getError());
        }
    }
}
