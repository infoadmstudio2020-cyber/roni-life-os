package com.roni.life.os.app.resource;

import android.content.Context;

import com.roni.library.network.DownloadManager;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production {@link ResourceFetcher}.
 *
 * <p>Remote archives go through the existing {@code DownloadManager}, which already writes to a
 * {@code .part} file and resumes with a {@code Range} header — so an interrupted download
 * continues instead of restarting. Local and bundled archives are copied, never re-downloaded.</p>
 */
public final class DownloadManagerFetcher implements ResourceFetcher {

    private static final String TAG = "ResourceFetcher";
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private final Context context;

    public DownloadManagerFetcher(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    @Override
    public void fetch(ResourceSpec spec, File destination) throws Exception {
        if (spec == null || destination == null) {
            throw new IllegalArgumentException("A resource and a destination are required.");
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent.getAbsolutePath());
        }

        switch (spec.getKind()) {
            case REMOTE_ARCHIVE:
                download(spec, destination);
                break;
            case LOCAL_ARCHIVE:
                copyFile(new File(stripScheme(spec.getLocation())), destination);
                break;
            case ASSET_ARCHIVE:
                copyAsset(spec.getLocation(), destination);
                break;
            case REMOTE_DOCUMENT:
                // The WebView loads the document itself; there is nothing to store.
                break;
            default:
                break;
        }
    }

    @Override
    public boolean isChanged(ResourceSpec spec, ResourceRecord record) {
        if (spec == null || record == null || !spec.isDocument()) {
            return true;
        }
        String tag = remoteTag(spec);
        if (tag.isEmpty()) {
            // No tag: a change cannot be proven, so the working copy is kept.
            return false;
        }
        return !tag.equalsIgnoreCase(record.getChecksum());
    }

    @Override
    public String remoteTag(ResourceSpec spec) {
        if (spec == null || !spec.isDocument()) {
            return "";
        }
        String etag = headEtag(spec.getLocation());
        return etag != null ? etag : "";
    }

    // ==================== Transports ====================

    private void download(ResourceSpec spec, File destination) throws Exception {
        DownloadManager manager = new DownloadManager();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            Future<?> future = manager.download(spec.getLocation(), destination,
                    new DownloadManager.DownloadListener() {
                        @Override
                        public void onProgress(long bytesRead, long totalBytes) {
                            // Progress is reported by the manager's own listener chain.
                        }

                        @Override
                        public void onSuccess(File file) {
                            // Completion is confirmed by the future and by verification.
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            failure.set(t);
                        }
                    }, spec.getId());
            future.get();
        } finally {
            manager.shutdown();
        }
        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException(error.getMessage() != null
                    ? error.getMessage() : error.getClass().getSimpleName(), error);
        }
        if (!destination.isFile()) {
            throw new IllegalStateException("The download produced no file.");
        }
    }

    private void copyFile(File source, File destination) throws Exception {
        if (source == null || !source.isFile()) {
            throw new IllegalStateException("The archive was not found: "
                    + (source != null ? source.getAbsolutePath() : ""));
        }
        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copy(input, output);
        }
    }

    private void copyAsset(String path, File destination) throws Exception {
        if (context == null) {
            throw new IllegalStateException("No context is available to read the asset.");
        }
        String normalized = path != null ? path : "";
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        try (InputStream input = context.getAssets().open(normalized);
             OutputStream output = new FileOutputStream(destination)) {
            copy(input, output);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    /** Best-effort ETag probe used to detect a changed remote document. */
    private String headEtag(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400) {
                return null;
            }
            return connection.getHeaderField("ETag");
        } catch (Throwable error) {
            AppLog.w(TAG, "ETag probe failed for " + url + ": " + error.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String stripScheme(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("file://")) {
            return path.substring("file://".length());
        }
        if (path.startsWith("file:")) {
            return path.substring("file:".length());
        }
        return path;
    }
}
