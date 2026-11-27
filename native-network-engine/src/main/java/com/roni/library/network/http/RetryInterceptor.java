package com.roni.library.network.http;

import java.io.IOException;

/**
 * Retries idempotent requests on transport failures and 5xx responses with exponential backoff.
 *
 * <p>Non-idempotent methods ({@code POST}, {@code PATCH}) are never retried: replaying them can
 * duplicate a server-side side effect.</p>
 */
public class RetryInterceptor implements HttpInterceptor {
    private final int maxRetries;
    private final long initialDelayMs;
    private int executionAttempts = 0;

    public RetryInterceptor(int maxRetries, long initialDelayMs) {
        this.maxRetries = Math.max(0, maxRetries);
        this.initialDelayMs = Math.max(0, initialDelayMs);
    }

    public RetryInterceptor(int maxRetries) {
        this(maxRetries, 200);
    }

    public RetryInterceptor() {
        this(3, 200);
    }

    public int getExecutionAttempts() {
        return executionAttempts;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public HttpResponse intercept(Chain chain) throws Exception {
        executionAttempts = 0;
        HttpRequest request = chain.request();
        boolean idempotent = isIdempotent(request.getMethod());
        int allowedRetries = idempotent ? maxRetries : 0;

        Exception lastException = null;
        long currentDelay = initialDelayMs;

        for (int attempt = 0; attempt <= allowedRetries; attempt++) {
            executionAttempts++;
            try {
                HttpResponse response = chain.proceed(request);
                if (response == null || response.getStatusCode() < 500) {
                    return response;
                }
                if (attempt < allowedRetries) {
                    if (!sleep(currentDelay)) {
                        return response;
                    }
                    currentDelay *= 2;
                } else {
                    return response;
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < allowedRetries) {
                    if (!sleep(currentDelay)) {
                        throw e;
                    }
                    currentDelay *= 2;
                }
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("Failed to execute HTTP request after " + executionAttempts + " attempts");
    }

    private static boolean sleep(long delayMs) {
        if (delayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isIdempotent(HttpMethod method) {
        if (method == null) {
            return false;
        }
        switch (method) {
            case GET:
            case HEAD:
            case PUT:
            case DELETE:
            case OPTIONS:
                return true;
            default:
                return false;
        }
    }
}
