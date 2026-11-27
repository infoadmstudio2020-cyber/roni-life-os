package com.roni.library.runtime;

import com.roni.library.runtime.server.LocalWebServer;
import com.roni.library.runtime.source.WebSource;
import com.roni.library.runtime.source.WebSourceResolver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Verifies that {@link LocalWebServer} binds a real loopback socket and speaks HTTP/1.1.
 */
public class LocalWebServerTest {

    private LocalWebServer server;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static String request(int port, String method, String path) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            out.write((method + " " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    @Test
    public void startBindsARealSocketAndServesTheLandingPage() throws Exception {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        Assert.assertTrue(server.isRunning());
        Assert.assertTrue(server.getPort() > 0);
        Assert.assertEquals("http://127.0.0.1:" + server.getPort(), server.getBaseUrl());

        String response = request(server.getPort(), "GET", "/");
        Assert.assertTrue(response.startsWith("HTTP/1.1 200"));
        Assert.assertTrue(response.contains("NativeRuntime Container"));
    }

    @Test
    public void missingPathReturns404() throws Exception {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        String response = request(server.getPort(), "GET", "/missing");
        Assert.assertTrue(response.startsWith("HTTP/1.1 404"));
    }

    @Test
    public void postReturns405() throws Exception {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        String response = request(server.getPort(), "POST", "/");
        Assert.assertTrue(response.startsWith("HTTP/1.1 405"));
    }

    @Test
    public void headReturnsHeadersWithoutBody() throws Exception {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        String response = request(server.getPort(), "HEAD", "/");
        Assert.assertTrue(response.startsWith("HTTP/1.1 200"));
        Assert.assertTrue(response.contains("Content-Length:"));
        Assert.assertFalse(response.contains("NativeRuntime Container"));
    }

    @Test
    public void stopClosesTheSocket() throws Exception {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        int port = server.getPort();
        server.stop();
        Assert.assertFalse(server.isRunning());
        try {
            request(port, "GET", "/");
            Assert.fail("expected connection failure after stop()");
        } catch (java.io.IOException expected) {
            // Expected: nothing is listening any more.
        }
    }

    @Test
    public void startTwiceIsSafe() {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        int port = server.getPort();
        server.start();
        Assert.assertEquals(port, server.getPort());
        Assert.assertTrue(server.isRunning());
    }

    @Test
    public void handleRequestRejectsUnsupportedMethods() {
        server = new LocalWebServer(0, new WebSourceResolver());
        server.start();
        WebSource response = server.handleRequest("/", "DELETE");
        Assert.assertEquals(405, response.getStatusCode());
    }

    @Test
    public void handleRequestBeforeStartReturnsNotFound() {
        server = new LocalWebServer(0, new WebSourceResolver());
        WebSource response = server.handleRequest("/", "GET");
        Assert.assertFalse(response.isFound());
    }
}
