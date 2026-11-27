package com.roni.library.network.http;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * HTTP/1.1 client built on {@link HttpURLConnection}.
 *
 * <p>Timeouts default to the engine configuration and may be overridden per request. Redirects are
 * followed manually so that the hop count is bounded and {@code Authorization} is dropped when the
 * redirect leaves the original host.</p>
 */
public class HttpClientEngine {
    public interface MockTransport {
        HttpResponse execute(HttpRequest request) throws Exception;
    }

    public interface HttpCallback {
        void onSuccess(HttpResponse response);
        void onFailure(Throwable t);
    }

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 15_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 15_000;
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final List<HttpInterceptor> interceptors = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile MockTransport mockTransport;
    private volatile int defaultConnectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
    private volatile int defaultReadTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    private volatile int maxRedirects = DEFAULT_MAX_REDIRECTS;

    public HttpClientEngine() {
        this.interceptors.add(new RetryInterceptor(2, 100));
    }

    /**
     * Applies engine-level defaults. Called by {@code NativeNetworkEngine.init}.
     */
    public void configure(int connectTimeoutMs, int readTimeoutMs, int maxRetries) {
        if (connectTimeoutMs > 0) {
            this.defaultConnectTimeoutMs = connectTimeoutMs;
        }
        if (readTimeoutMs > 0) {
            this.defaultReadTimeoutMs = readTimeoutMs;
        }
        for (int i = 0; i < interceptors.size(); i++) {
            if (interceptors.get(i) instanceof RetryInterceptor) {
                interceptors.set(i, new RetryInterceptor(Math.max(0, maxRetries), 100));
            }
        }
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = Math.max(0, maxRedirects);
    }

    public void addInterceptor(HttpInterceptor interceptor) {
        if (interceptor != null) {
            this.interceptors.add(interceptor);
        }
    }

    public void setMockTransport(MockTransport transport) {
        this.mockTransport = transport;
    }

    public HttpResponse execute(HttpRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("HttpRequest cannot be null");
        }
        List<HttpInterceptor> chainInterceptors = new ArrayList<>(interceptors);
        RealInterceptorChain chain = new RealInterceptorChain(chainInterceptors, 0, request);
        return chain.proceed(request);
    }

    public Future<HttpResponse> executeAsync(HttpRequest request, HttpCallback callback) {
        return executor.submit(() -> {
            try {
                HttpResponse response = execute(request);
                if (callback != null) {
                    callback.onSuccess(response);
                }
                return response;
            } catch (Throwable t) {
                if (callback != null) {
                    callback.onFailure(t);
                }
                throw (t instanceof Exception) ? (Exception) t : new RuntimeException(t);
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    private class RealInterceptorChain implements HttpInterceptor.Chain {
        private final List<HttpInterceptor> interceptors;
        private final int index;
        private final HttpRequest request;

        RealInterceptorChain(List<HttpInterceptor> interceptors, int index, HttpRequest request) {
            this.interceptors = interceptors;
            this.index = index;
            this.request = request;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public HttpResponse proceed(HttpRequest req) throws Exception {
            if (index < interceptors.size()) {
                RealInterceptorChain next = new RealInterceptorChain(interceptors, index + 1, req);
                HttpInterceptor interceptor = interceptors.get(index);
                return interceptor.intercept(next);
            }
            return executeTerminal(req);
        }
    }

    private HttpResponse executeTerminal(HttpRequest req) throws Exception {
        if (mockTransport != null) {
            return mockTransport.execute(req);
        }

        long startTime = System.currentTimeMillis();
        HttpRequest current = req;
        String originalHost = hostOf(req.getUrl());

        for (int hop = 0; hop <= maxRedirects; hop++) {
            HttpResponse response = executeOnce(current);
            int status = response.getStatusCode();
            if (status < 300 || status >= 400 || status == 304) {
                return new HttpResponse(status, response.getHeaders(), response.getBody(),
                        System.currentTimeMillis() - startTime);
            }
            String location = response.getHeader("Location");
            if (location == null || location.isEmpty()) {
                return new HttpResponse(status, response.getHeaders(), response.getBody(),
                        System.currentTimeMillis() - startTime);
            }
            String nextUrl = resolveLocation(current.getUrl(), location);
            String nextHost = hostOf(nextUrl);
            HttpRequest.Builder builder = current.newBuilder().setUrl(nextUrl);
            if (originalHost != null && !originalHost.equalsIgnoreCase(nextHost)) {
                builder.removeHeader("Authorization");
                builder.removeHeader("Cookie");
            }
            current = builder.build();
        }
        throw new java.io.IOException("Too many redirects (max " + maxRedirects + ")");
    }

    private HttpResponse executeOnce(HttpRequest req) throws Exception {
        String fullUrl = buildFullUrl(req);
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(req.getMethod().name());
            conn.setConnectTimeout(req.getConnectTimeoutMs() > 0
                    ? req.getConnectTimeoutMs() : defaultConnectTimeoutMs);
            conn.setReadTimeout(req.getReadTimeoutMs() > 0
                    ? req.getReadTimeoutMs() : defaultReadTimeoutMs);
            conn.setInstanceFollowRedirects(false);
            conn.setDoInput(true);

            for (Map.Entry<String, String> header : req.getHeaders().entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }

            if (req.getBody() != null && (req.getMethod() == HttpMethod.POST
                    || req.getMethod() == HttpMethod.PUT
                    || req.getMethod() == HttpMethod.PATCH)) {
                conn.setDoOutput(true);
                byte[] bodyBytes = req.getBody().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                    os.flush();
                }
            }

            int statusCode = conn.getResponseCode();
            Map<String, String> respHeaders = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    respHeaders.put(entry.getKey(), entry.getValue().get(0));
                }
            }

            InputStream is = (statusCode >= 200 && statusCode < 400)
                    ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder bodyBuilder = new StringBuilder();
            if (is != null) {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        bodyBuilder.append(line).append('\n');
                    }
                }
            }
            return new HttpResponse(statusCode, respHeaders, bodyBuilder.toString().trim(), 0);
        } finally {
            conn.disconnect();
        }
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveLocation(String base, String location) throws Exception {
        try {
            return new URL(new URL(base), location).toString();
        } catch (Exception e) {
            return location;
        }
    }

    private String buildFullUrl(HttpRequest req) {
        String base = req.getUrl();
        if (req.getQueryParameters().isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append(base.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : req.getQueryParameters().entrySet()) {
            if (!first) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}
