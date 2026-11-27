package com.roni.library.network;

import com.roni.library.network.http.HttpClientEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streaming file downloader with resume and cancellation.
 *
 * <p>Partial downloads are written to {@code <destination>.part}. When a partial file exists the
 * next attempt sends a {@code Range} header; a {@code 206} response appends, a {@code 200}
 * response restarts from zero. Progress callbacks are throttled to at most one per 100 ms.</p>
 */
public class DownloadManager {
    public interface DownloadListener {
        void onProgress(long bytesRead, long totalBytes);
        void onSuccess(File file);
        void onFailure(Throwable t);
    }

    private static final long PROGRESS_INTERVAL_MS = 100L;
    private static final int BUFFER_SIZE = 16 * 1024;

    private final HttpClientEngine clientEngine;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, DownloadTask> active = new ConcurrentHashMap<>();

    public DownloadManager(HttpClientEngine clientEngine) {
        this.clientEngine = clientEngine != null ? clientEngine : new HttpClientEngine();
    }

    public DownloadManager() {
        this(new HttpClientEngine());
    }

    public Future<?> download(String url, File destination, DownloadListener listener) {
        return download(url, destination, listener, url);
    }

    public Future<?> download(String url, File destination, DownloadListener listener, String id) {
        String key = id != null ? id : url;
        DownloadTask task = new DownloadTask(url, destination, listener);
        active.put(key, task);
        return executor.submit(() -> {
            try {
                task.run();
            } finally {
                active.remove(key);
            }
        });
    }

    /**
     * Cancels an in-flight download: the stream is closed and the {@code .part} file is deleted.
     */
    public boolean cancel(String id) {
        DownloadTask task = id != null ? active.get(id) : null;
        if (task == null) {
            return false;
        }
        task.cancel();
        return true;
    }

    public boolean isDownloading(String id) {
        return id != null && active.containsKey(id);
    }

    public void shutdown() {
        for (DownloadTask task : active.values()) {
            task.cancel();
        }
        active.clear();
        executor.shutdown();
    }

    private final class DownloadTask {
        private final String url;
        private final File destination;
        private final DownloadListener listener;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile HttpURLConnection connection;

        DownloadTask(String url, File destination, DownloadListener listener) {
            this.url = url;
            this.destination = destination;
            this.listener = listener;
        }

        void cancel() {
            cancelled.set(true);
            HttpURLConnection conn = connection;
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                    // Nothing to do.
                }
            }
            File part = partFile();
            if (part.exists() && !part.delete()) {
                part.deleteOnExit();
            }
        }

        private File partFile() {
            return new File(destination.getAbsolutePath() + ".part");
        }

        void run() {
            File part = partFile();
            try {
                if (destination.getParentFile() != null) {
                    destination.getParentFile().mkdirs();
                }
                long existing = part.exists() ? part.length() : 0L;

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                this.connection = conn;
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                if (existing > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existing + "-");
                }

                int status = conn.getResponseCode();
                boolean append;
                if (status == HttpURLConnection.HTTP_PARTIAL) {
                    append = true;
                } else if (status == HttpURLConnection.HTTP_OK) {
                    append = false;
                    existing = 0L;
                } else {
                    throw new IOException("Download failed with HTTP " + status);
                }

                long contentLength = conn.getContentLengthLong();
                long total = contentLength > 0 ? existing + contentLength : -1L;

                if (cancelled.get()) {
                    throw new IOException("Download cancelled");
                }

                long written = existing;
                long lastReport = 0L;
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(part, append)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (cancelled.get()) {
                            throw new IOException("Download cancelled");
                        }
                        out.write(buffer, 0, read);
                        written += read;
                        long now = System.currentTimeMillis();
                        if (listener != null && now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now;
                            listener.onProgress(written, total);
                        }
                    }
                    out.flush();
                }

                if (cancelled.get()) {
                    throw new IOException("Download cancelled");
                }

                if (destination.exists() && !destination.delete()) {
                    throw new IOException("Cannot replace " + destination.getAbsolutePath());
                }
                if (!part.renameTo(destination)) {
                    throw new IOException("Cannot move partial file to " + destination.getAbsolutePath());
                }

                if (listener != null) {
                    listener.onProgress(written, total > 0 ? total : written);
                    listener.onSuccess(destination);
                }
            } catch (Throwable t) {
                if (cancelled.get()) {
                    deleteQuietly(part);
                }
                if (listener != null) {
                    listener.onFailure(t);
                }
            } finally {
                this.connection = null;
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    /**
     * Reads the current size of a partial download, or {@code 0} when none exists.
     */
    public long getPartialSize(File destination) {
        File part = new File(destination.getAbsolutePath() + ".part");
        return part.exists() ? part.length() : 0L;
    }

    /**
     * Truncates a partial download so the next attempt starts from zero.
     */
    public void resetPartial(File destination) throws IOException {
        File part = new File(destination.getAbsolutePath() + ".part");
        if (part.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
                raf.setLength(0);
            }
        }
    }
}
