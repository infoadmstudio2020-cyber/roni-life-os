package com.roni.library.runtime.server;

import com.roni.library.runtime.source.WebSource;
import com.roni.library.runtime.source.WebSourceResolver;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local HTTP/1.1 server for offline web assets.
 *
 * <p>Binds to the loopback interface only. When the configured port is {@code 0} — or already in
 * use — an ephemeral port is bound and reported by {@link #getPort()}. Supports {@code GET} and
 * {@code HEAD}; every other method receives {@code 405}.</p>
 */
public class LocalWebServer {

    private static final int MAX_WORKERS = 4;

    private final int configuredPort;
    private final WebSourceResolver resolver;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ServerSocket serverSocket;
    private volatile int boundPort;
    private volatile ExecutorService acceptExecutor;
    private volatile ExecutorService workerExecutor;

    public LocalWebServer() {
        this(8080, new WebSourceResolver());
    }

    public LocalWebServer(int port, WebSourceResolver resolver) {
        this.configuredPort = port >= 0 ? port : 8080;
        this.resolver = resolver != null ? resolver : new WebSourceResolver();
        this.boundPort = this.configuredPort;
    }

    /**
     * @return the bound port while running, otherwise the configured port
     */
    public int getPort() {
        return running.get() ? boundPort : configuredPort;
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    public boolean isRunning() {
        return running.get();
    }


    public synchronized void start() {
        if (running.get()) {
            return;
        }

        ServerSocket socket;

        try {
            socket = bind(configuredPort);
        } catch (IOException first) {
            try {
                socket = bind(0);
            } catch (IOException second) {
                running.set(false);
                return;
            }
        }

        this.serverSocket = socket;
        this.boundPort = socket.getLocalPort();
        this.running.set(true);

        ExecutorService accept = Executors.newSingleThreadExecutor(
                namedFactory("local-web-accept")
        );

        ExecutorService workers = Executors.newFixedThreadPool(
                MAX_WORKERS,
                namedFactory("local-web-worker")
        );

        this.acceptExecutor = accept;
        this.workerExecutor = workers;

        // Final reference for lambda expression
        final ServerSocket activeSocket = socket;
        final ExecutorService activeWorkers = workers;

        accept.submit(() -> acceptLoop(activeSocket, activeWorkers));
    }


    private static ServerSocket bind(int port) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 16);
        return socket;
    }

    private void acceptLoop(ServerSocket socket, ExecutorService workers) {
        while (running.get() && !socket.isClosed()) {
            try {
                Socket client = socket.accept();
                workers.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running.get()) {
                    continue;
                }
                return;
            }
        }
    }

    private void handleConnection(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split("\\s+");
            String method = parts.length > 0 ? parts[0].toUpperCase(Locale.US) : "";
            String path = parts.length > 1 ? parts[1] : "/";

            // Drain headers so the client does not see a reset.
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // Headers are not used by the resolver.
            }

            OutputStream out = socket.getOutputStream();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                writeResponse(out, methodNotAllowed(path), false);
                return;
            }
            WebSource source = resolver.resolve(path);
            writeResponse(out, source, "HEAD".equals(method));
        } catch (Throwable ignored) {
            // A single failed connection must not stop the server.
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
            if (buffer.size() > 8192) {
                break;
            }
        }
        if (b == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void writeResponse(OutputStream out, WebSource source, boolean headOnly)
            throws IOException {
        byte[] body = source.getData();
        String mime = source.getMimeType() != null ? source.getMimeType() : "application/octet-stream";
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(source.getStatusCode()).append(' ')
                .append(reasonPhrase(source.getStatusCode())).append("\r\n")
                .append("Content-Type: ").append(mime).append("\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n")
                .append("Connection: close\r\n")
                .append("\r\n");
        out.write(header.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!headOnly) {
            out.write(body);
        }
        out.flush();
    }

    private static String reasonPhrase(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            default:
                return "OK";
        }
    }

    private static WebSource methodNotAllowed(String path) {
        return new WebSource(path, "text/plain",
                "405 Method Not Allowed".getBytes(StandardCharsets.UTF_8), 405, null);
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
        ExecutorService accept = acceptExecutor;
        acceptExecutor = null;
        if (accept != null) {
            accept.shutdownNow();
        }
        ExecutorService workers = workerExecutor;
        workerExecutor = null;
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    /**
     * In-process request entry point used by tests and by the runtime command handler.
     */
    public WebSource handleRequest(String path, String method) {
        if (!running.get()) {
            return WebSource.notFound(path);
        }
        String verb = method != null ? method.toUpperCase(Locale.US) : "GET";
        if (!"GET".equals(verb) && !"HEAD".equals(verb)) {
            return methodNotAllowed(path);
        }
        return resolver.resolve(path);
    }

    public WebSourceResolver getResolver() {
        return resolver;
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
