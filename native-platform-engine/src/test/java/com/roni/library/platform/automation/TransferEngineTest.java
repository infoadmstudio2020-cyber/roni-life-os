package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.automation.TransferProgress;
import com.roni.library.contracts.automation.TransferRequest;
import com.roni.library.platform.filemanager.FileManagerRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Random;

import com.sun.net.httpserver.HttpServer;

public class TransferEngineTest {

    private HttpServer server;
    private File root;
    private TransferEngine engine;
    private byte[] payload;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("automation-transfer").toFile();
        payload = new byte[128 * 1024];
        new Random(7L).nextBytes(payload);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file.bin", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.createContext("/slow.bin", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int offset = 0; offset < payload.length; offset += 4096) {
                    out.write(payload, offset, Math.min(4096, payload.length - offset));
                    out.flush();
                    Thread.sleep(40L);
                }
            } catch (Exception ignored) {
                // client cancelled
            }
        });
        server.start();

        engine = new TransferEngine(new EventEngine(
                new AutomationStore(new AutomationStore.MemoryKeyValueStore())),
                new FileManagerRepository(root, null));
    }

    @After
    public void tearDown() {
        engine.release();
        server.stop(0);
    }

    @Test
    public void downloadWritesCompleteFile() throws Exception {
        TransferProgress started = engine.start("p1", new TransferRequest.Builder()
                .id("t1")
                .direction(TransferRequest.DIRECTION_DOWNLOAD)
                .url(url("/file.bin"))
                .localPath("downloads/file.bin")
                .build());

        assertEquals(TransferProgress.STATE_QUEUED, started.getState());
        TransferProgress finished = awaitFinished("p1", "t1");

        assertEquals(TransferProgress.STATE_COMPLETED, finished.getState());
        assertEquals(payload.length, finished.getBytesTransferred());
        File target = new File(root, "p1/downloads/file.bin");
        assertTrue(target.exists());
        assertEquals(payload.length, target.length());
        assertTrue(!new File(target.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void cancelStopsSlowDownload() throws Exception {
        engine.start("p1", new TransferRequest.Builder()
                .id("t2")
                .direction(TransferRequest.DIRECTION_DOWNLOAD)
                .url(url("/slow.bin"))
                .localPath("downloads/slow.bin")
                .build());

        Thread.sleep(120L);
        assertTrue(engine.cancel("p1", "t2"));
        TransferProgress finished = awaitFinished("p1", "t2");

        assertEquals(TransferProgress.STATE_CANCELLED, finished.getState());
    }

    @Test
    public void invalidUrlIsRejected() {
        try {
            engine.start("p1", new TransferRequest.Builder()
                    .id("t3")
                    .direction(TransferRequest.DIRECTION_DOWNLOAD)
                    .url("ftp://example.com/file")
                    .localPath("file.bin")
                    .build());
            throw new AssertionError("Expected rejection");
        } catch (AutomationException expected) {
            assertTrue(expected.getMessage().contains("http"));
        }
    }

    @Test
    public void transfersAreIsolatedPerProject() throws Exception {
        engine.start("p1", new TransferRequest.Builder()
                .id("t4")
                .direction(TransferRequest.DIRECTION_DOWNLOAD)
                .url(url("/file.bin"))
                .localPath("downloads/file.bin")
                .build());
        awaitFinished("p1", "t4");

        assertEquals(1, engine.list("p1").size());
        assertTrue(engine.list("p2").isEmpty());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private TransferProgress awaitFinished(String projectId, String transferId)
            throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            TransferProgress progress = engine.get(projectId, transferId);
            if (progress != null && progress.isFinished()) {
                return progress;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Transfer did not finish: " + transferId);
    }
}
