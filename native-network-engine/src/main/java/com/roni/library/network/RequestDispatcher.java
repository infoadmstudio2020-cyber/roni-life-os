package com.roni.library.network;

import com.roni.library.network.http.HttpClientEngine;
import com.roni.library.network.http.HttpRequest;
import com.roni.library.network.http.HttpResponse;
import java.util.concurrent.Future;

public class RequestDispatcher {

    private final HttpClientEngine httpClientEngine;

    public RequestDispatcher(HttpClientEngine httpClientEngine) {
        this.httpClientEngine = httpClientEngine;
    }

    public HttpResponse dispatchSync(HttpRequest request) throws Exception {
        if (httpClientEngine == null) {
            throw new IllegalStateException("HttpClientEngine is not initialized");
        }
        return httpClientEngine.execute(request);
    }

    public Future<HttpResponse> dispatchAsync(HttpRequest request, HttpClientEngine.HttpCallback callback) {
        if (httpClientEngine == null) {
            throw new IllegalStateException("HttpClientEngine is not initialized");
        }
        return httpClientEngine.executeAsync(request, callback);
    }
}
