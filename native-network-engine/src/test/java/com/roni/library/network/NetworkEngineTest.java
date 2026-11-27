package com.roni.library.network;

import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.network.handler.NetworkCommandHandler;
import com.roni.library.network.http.HttpClientEngine;
import com.roni.library.network.http.HttpMethod;
import com.roni.library.network.http.HttpRequest;
import com.roni.library.network.http.HttpResponse;
import com.roni.library.network.monitor.BandwidthQuality;
import com.roni.library.network.monitor.ConnectionType;
import com.roni.library.network.monitor.NetworkMonitor;
import com.roni.library.network.service.NativeNetworkService;
import com.roni.library.network.ws.WebSocketClient;
import com.roni.library.network.ws.WebSocketListener;
import com.roni.library.network.ws.WebSocketState;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkEngineTest {

    private NativeNetworkEngine engine;

    @Before
    public void setUp() {
        engine = NativeNetworkEngine.getInstance();
        engine.init(null);
    }

    @After
    public void tearDown() {
        if (engine != null) {
            engine.release();
        }
    }

    @Test
    public void testSingletonLifecycleAndConfig() {
        Assert.assertTrue(engine.isInitialized());
        Assert.assertNotNull(engine.getConfig());
        Assert.assertNotNull(engine.getService());
        Assert.assertNotNull(engine.getHttpClient());
        Assert.assertNotNull(engine.getNetworkMonitor());

        NativeNetworkEngine secondInstance = NativeNetworkEngine.getInstance();
        Assert.assertSame(engine, secondInstance);

        engine.release();
        Assert.assertFalse(engine.isInitialized());
        Assert.assertNull(engine.getConfig());
    }

    @Test
    public void testNetworkEngineWrapper() {
        NetworkEngine wrapper = NetworkEngine.getInstance();
        wrapper.init(null);
        Assert.assertTrue(wrapper.isInitialized());
        Assert.assertNotNull(wrapper.getHttpClient());
        Assert.assertNotNull(wrapper.getNetworkMonitor());
        Assert.assertNotNull(wrapper.getService());
        wrapper.release();
    }

    @Test
    public void testConfigBuilderBounds() {
        NativeNetworkEngineConfig config = NativeNetworkEngineConfig.builder()
                .debugEnabled(true)
                .tag("CustomTag")
                .timeout(5000)
                .connectTimeout(2000)
                .readTimeout(3000)
                .maxRetries(5)
                .build();

        Assert.assertTrue(config.isDebugEnabled());
        Assert.assertEquals("CustomTag", config.getTag());
        Assert.assertEquals(5000, config.getTimeout());
        Assert.assertEquals(2000, config.getConnectTimeout());
        Assert.assertEquals(3000, config.getReadTimeout());
        Assert.assertEquals(5, config.getMaxRetries());
    }

    @Test
    public void testResultWrapper() {
        NativeNetworkEngineResult<String> successResult = NativeNetworkEngineResult.success("Payload");
        Assert.assertTrue(successResult.isSuccess());
        Assert.assertEquals("Payload", successResult.getData());
        Assert.assertEquals(0, successResult.getErrorCode());

        NativeNetworkEngineResult<String> failResult = NativeNetworkEngineResult.failure(404, "Not Found");
        Assert.assertFalse(failResult.isSuccess());
        Assert.assertEquals(404, failResult.getErrorCode());
        Assert.assertEquals("Not Found", failResult.getErrorMessage());
    }

    @Test
    public void testHttpRequestAndResponse() {
        HttpRequest request = HttpRequest.builder()
                .setUrl("https://api.example.com/test")
                .setMethod(HttpMethod.POST)
                .addHeader("Authorization", "Bearer token123")
                .addQueryParameter("page", "1")
                .setBody("{\"key\":\"value\"}")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(8000)
                .build();

        Assert.assertEquals("https://api.example.com/test", request.getUrl());
        Assert.assertEquals(HttpMethod.POST, request.getMethod());
        Assert.assertEquals("Bearer token123", request.getHeaders().get("Authorization"));
        Assert.assertEquals("1", request.getQueryParameters().get("page"));
        Assert.assertEquals("{\"key\":\"value\"}", request.getBody());

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        HttpResponse response = new HttpResponse(200, headers, "{\"success\":true}", 45);

        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertTrue(response.isSuccessful());
        Assert.assertEquals("application/json", response.getHeader("Content-Type"));
        Assert.assertEquals("{\"success\":true}", response.getBody());
        Assert.assertEquals(45, response.getLatencyMs());
    }

    @Test
    public void testHttpClientEngineWithMockTransport() throws Exception {
        HttpClientEngine client = engine.getHttpClient();
        client.setMockTransport(req -> new HttpResponse(200, null, "{\"msg\":\"ok\"}", 10));

        HttpRequest request = HttpRequest.builder()
                .setUrl("https://mock.test/api")
                .setMethod(HttpMethod.GET)
                .build();

        HttpResponse resp = client.execute(request);
        Assert.assertEquals(200, resp.getStatusCode());
        Assert.assertEquals("{\"msg\":\"ok\"}", resp.getBody());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean asyncSuccess = new AtomicBoolean(false);
        client.executeAsync(request, new HttpClientEngine.HttpCallback() {
            @Override
            public void onSuccess(HttpResponse response) {
                asyncSuccess.set(response.isSuccessful());
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
        Assert.assertTrue(asyncSuccess.get());
    }

    @Test
    public void testRetryInterceptor() throws Exception {
        HttpClientEngine client = new HttpClientEngine();
        final AtomicInteger attempts = new AtomicInteger(0);

        client.setMockTransport(req -> {
            int current = attempts.incrementAndGet();
            if (current < 2) {
                throw new IOException("Simulated network blip");
            }
            return new HttpResponse(200, null, "{\"attempt\":" + current + "}", 12);
        });

        HttpRequest req = HttpRequest.builder().setUrl("https://retry.test").build();
        HttpResponse resp = client.execute(req);

        Assert.assertEquals(200, resp.getStatusCode());
        Assert.assertEquals(2, attempts.get());
        client.shutdown();
    }

    @Test
    public void testNetworkMonitor() {
        NetworkMonitor monitor = engine.getNetworkMonitor();
        // No Context is attached in a JVM test, so the monitor must not invent a connection.
        Assert.assertEquals(ConnectionType.NONE, monitor.getConnectionType());
        Assert.assertFalse(monitor.isConnected());
        Assert.assertEquals(BandwidthQuality.UNKNOWN, monitor.getBandwidthQuality());

        final AtomicBoolean stateChanged = new AtomicBoolean(false);
        NetworkMonitor.NetworkStateListener listener = (type, quality, connected) -> stateChanged.set(true);
        monitor.addListener(listener);

        monitor.updateConnection(ConnectionType.CELLULAR, BandwidthQuality.GOOD);
        Assert.assertEquals(ConnectionType.CELLULAR, monitor.getConnectionType());
        Assert.assertEquals(BandwidthQuality.GOOD, monitor.getBandwidthQuality());
        Assert.assertTrue(stateChanged.get());

        monitor.removeListener(listener);
    }

    @Test
    public void testWebSocketClientLifecycle() throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch msgLatch = new CountDownLatch(1);
        final StringBuilder received = new StringBuilder();

        java.net.ServerSocket serverSocket =
                new java.net.ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress());
        Thread serverThread = new Thread(() -> {
            try (java.net.Socket socket = serverSocket.accept()) {
                java.io.InputStream in = socket.getInputStream();
                java.io.OutputStream out = socket.getOutputStream();
                String request = com.roni.library.network.ws.TestWebSocketServer.readHeaderBlock(in);
                java.util.Map<String, String> headers =
                        com.roni.library.network.ws.TestWebSocketServer.parseHeaders(request);
                String accept = com.roni.library.network.ws.TestWebSocketServer
                        .expectedAccept(headers.get("sec-websocket-key"));
                out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                out.flush();
                com.roni.library.network.ws.TestWebSocketServer.writeText(out, "Hello WebSocket!");
                Thread.sleep(500);
            } catch (Throwable ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        WebSocketClient client = new WebSocketClient(
                "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/echo",
                new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocketClient ws) {
                        openLatch.countDown();
                    }

                    @Override
                    public void onMessage(WebSocketClient ws, String text) {
                        received.append(text);
                        msgLatch.countDown();
                    }

                    @Override
                    public void onMessage(WebSocketClient ws, byte[] bytes) {}

                    @Override
                    public void onClosing(WebSocketClient ws, int code, String reason) {}

                    @Override
                    public void onClosed(WebSocketClient ws, int code, String reason) {}

                    @Override
                    public void onFailure(WebSocketClient ws, Throwable t) {}
                });

        client.connect();
        Assert.assertTrue(openLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(WebSocketState.OPEN, client.getState());

        Assert.assertTrue(msgLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("Hello WebSocket!", received.toString());

        client.shutdown();
        serverSocket.close();
    }

    @Test
    public void testNetworkCommandHandler() {
        NativeNetworkService service = engine.getService();
        service.getHttpClient().setMockTransport(req -> new HttpResponse(200, null, "{\"code\":0}", 5));
        NetworkCommandHandler handler = new NetworkCommandHandler(service);

        Assert.assertEquals("network", handler.getPath());
        Assert.assertEquals("network", handler.getRoutePrefix());

        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://api.test/data");
        params.put("method", "GET");

        NativeApiRequest request = new NativeApiRequest("req-1", "network", "httpRequest", params, System.currentTimeMillis());
        NativeApiResponse response = handler.handleRequest(request);

        Assert.assertNotNull(response);
        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals("req-1", response.getRequestId());
        Assert.assertNotNull(response.getData());
    }

    @Test
    public void testSslPinningManager() {
        SslPinningManager pinningManager = new SslPinningManager();
        pinningManager.addPin("example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        Assert.assertTrue(pinningManager.hasPinsForHost("example.com"));
        Assert.assertFalse(pinningManager.hasPinsForHost("other.com"));
        Assert.assertEquals(1, pinningManager.getPins("example.com").size());
        pinningManager.clear();
        Assert.assertFalse(pinningManager.hasPinsForHost("example.com"));
    }
}
