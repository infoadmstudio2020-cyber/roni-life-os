package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.AutomationLimits;
import com.roni.library.contracts.automation.TransferProgress;
import com.roni.library.contracts.automation.TransferRequest;
import com.roni.library.platform.filemanager.FileManagerException;
import com.roni.library.platform.filemanager.FileManagerRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streaming download and upload engine.
 *
 * <p>Files are written to a {@code .part} sibling and renamed only after a complete transfer,
 * so an interrupted download never replaces a good file. Downloads resume with a Range request
 * when a partial file exists. Nothing is buffered fully in memory.</p>
 */
public final class TransferEngine {

    private final EventEngine events;
    private final FileManagerRepository files;
    private final Map<String, TransferProgress> transfers = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean released = new AtomicBoolean(false);

    public TransferEngine(@NonNull EventEngine events, @NonNull FileManagerRepository files) {
        this.events = events;
        this.files = files;
    }

    @NonNull
    public TransferProgress start(@NonNull String projectId, @NonNull TransferRequest request)
            throws AutomationException {
        if (projectId.trim().isEmpty()) {
            throw new AutomationException(AutomationErrorCodes.NO_ACTIVE_PROJECT,
                    "No active project");
        }
        if (request.getUrl().isEmpty() || request.getUrl().length() > AutomationLimits.MAX_URL_LENGTH) {
            throw AutomationException.invalid("A valid URL is required");
        }
        if (!request.getUrl().startsWith("http://") && !request.getUrl().startsWith("https://")) {
            throw AutomationException.invalid("Only http and https URLs are supported");
        }
        if (request.getLocalPath().isEmpty()) {
            throw AutomationException.invalid("A local path is required");
        }
        if (list(projectId).size() >= AutomationLimits.MAX_TRANSFERS) {
            throw new AutomationException(AutomationErrorCodes.TRANSFER_LIMIT,
                    "Transfer limit reached");
        }
        File target;
        try {
            target = files.resolve(projectId, request.getLocalPath());
        } catch (FileManagerException error) {
            throw new AutomationException(error.getCode(), error.getMessage(), error);
        }
        if (request.isDownload() && files.freeSpaceBytes() < AutomationLimits.MIN_FREE_SPACE_BYTES) {
            throw new AutomationException(AutomationErrorCodes.LOW_STORAGE,
                    "Not enough free storage for this download");
        }
        String id = request.getId().isEmpty() ? UUID.randomUUID().toString() : request.getId();
        TransferProgress queued = new TransferProgress(id, request.getDirection(),
                TransferProgress.STATE_QUEUED, 0L, request.getExpectedBytes(),
                request.getLocalPath(), null, System.currentTimeMillis());
        transfers.put(key(projectId, id), queued);
        events.emit(projectId, EventEngine.TOPIC_TRANSFER, "queued", queued.toMap());
        executor.execute(() -> run(projectId, id, request, target));
        return queued;
    }

    private void run(@NonNull String projectId, @NonNull String id,
                     @NonNull TransferRequest request, @NonNull File target) {
        update(projectId, new TransferProgress(id, request.getDirection(),
                TransferProgress.STATE_RUNNING, 0L, request.getExpectedBytes(),
                request.getLocalPath(), null, System.currentTimeMillis()));
        try {
            if (request.isDownload()) {
                download(projectId, id, request, target);
            } else {
                upload(projectId, id, request, target);
            }
        } catch (Throwable error) {
            boolean wasCancelled = cancelled.contains(id);
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            TransferProgress current = transfers.get(key(projectId, id));
            long transferred = current == null ? 0L : current.getBytesTransferred();
            long total = current == null ? request.getExpectedBytes() : current.getTotalBytes();
            update(projectId, new TransferProgress(id, request.getDirection(),
                    wasCancelled ? TransferProgress.STATE_CANCELLED : TransferProgress.STATE_FAILED,
                    transferred, total, request.getLocalPath(),
                    wasCancelled ? null : message, System.currentTimeMillis()));
        } finally {
            cancelled.remove(id);
        }
    }

    private void download(@NonNull String projectId, @NonNull String id,
                          @NonNull TransferRequest request, @NonNull File target)
            throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create the target folder");
        }
        File part = new File(target.getAbsolutePath() + ".part");
        long existing = part.exists() ? part.length() : 0L;
        HttpURLConnection connection = (HttpURLConnection) new URL(request.getUrl()).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestMethod("GET");
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (existing > 0L) {
            connection.setRequestProperty("Range", "bytes=" + existing + "-");
        }
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK && status != 206) {
            connection.disconnect();
            throw new IOException("Server returned HTTP " + status);
        }
        boolean append = status == 206 && existing > 0L;
        long total = request.getExpectedBytes();
        long contentLength = connection.getContentLengthLong();
        if (contentLength > 0L) {
            total = append ? existing + contentLength : contentLength;
        }
        long transferred = append ? existing : 0L;
        try (InputStream input = connection.getInputStream();
             OutputStream output = new FileOutputStream(part, append)) {
            byte[] buffer = new byte[AutomationLimits.TRANSFER_BUFFER_BYTES];
            int read;
            long lastReport = 0L;
            while ((read = input.read(buffer)) != -1) {
                if (cancelled.contains(id)) {
                    throw new IOException("Transfer cancelled");
                }
                output.write(buffer, 0, read);
                transferred += read;
                long now = System.currentTimeMillis();
                if (now - lastReport > 250L) {
                    lastReport = now;
                    update(projectId, new TransferProgress(id, request.getDirection(),
                            TransferProgress.STATE_RUNNING, transferred, total,
                            request.getLocalPath(), null, now));
                }
            }
            output.flush();
        } finally {
            connection.disconnect();
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Cannot replace the existing file");
        }
        if (!part.renameTo(target)) {
            throw new IOException("Cannot finalise the downloaded file");
        }
        update(projectId, new TransferProgress(id, request.getDirection(),
                TransferProgress.STATE_COMPLETED, transferred, total,
                request.getLocalPath(), null, System.currentTimeMillis()));
    }

    private void upload(@NonNull String projectId, @NonNull String id,
                        @NonNull TransferRequest request, @NonNull File source)
            throws IOException {
        if (!source.exists() || source.isDirectory()) {
            throw new IOException("Upload source does not exist");
        }
        long total = source.length();
        HttpURLConnection connection = (HttpURLConnection) new URL(request.getUrl()).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(total);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        long transferred = 0L;
        try (InputStream input = new FileInputStream(source);
             OutputStream output = connection.getOutputStream()) {
            byte[] buffer = new byte[AutomationLimits.TRANSFER_BUFFER_BYTES];
            int read;
            long lastReport = 0L;
            while ((read = input.read(buffer)) != -1) {
                if (cancelled.contains(id)) {
                    throw new IOException("Transfer cancelled");
                }
                output.write(buffer, 0, read);
                transferred += read;
                long now = System.currentTimeMillis();
                if (now - lastReport > 250L) {
                    lastReport = now;
                    update(projectId, new TransferProgress(id, request.getDirection(),
                            TransferProgress.STATE_RUNNING, transferred, total,
                            request.getLocalPath(), null, now));
                }
            }
            output.flush();
        }
        int status = connection.getResponseCode();
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("Server returned HTTP " + status);
        }
        update(projectId, new TransferProgress(id, request.getDirection(),
                TransferProgress.STATE_COMPLETED, transferred, total,
                request.getLocalPath(), null, System.currentTimeMillis()));
    }

    private void update(@NonNull String projectId, @NonNull TransferProgress progress) {
        transfers.put(key(projectId, progress.getId()), progress);
        String action = progress.isFinished()
                ? (TransferProgress.STATE_COMPLETED.equals(progress.getState()) ? "completed"
                : TransferProgress.STATE_CANCELLED.equals(progress.getState()) ? "cancelled"
                : "failed")
                : "progress";
        events.emit(projectId, EventEngine.TOPIC_TRANSFER, action, progress.toMap());
    }

    public boolean cancel(@NonNull String projectId, @NonNull String transferId) {
        TransferProgress progress = transfers.get(key(projectId, transferId));
        if (progress == null || progress.isFinished()) {
            return false;
        }
        cancelled.add(transferId);
        return true;
    }

    @Nullable
    public TransferProgress get(@NonNull String projectId, @NonNull String transferId) {
        return transfers.get(key(projectId, transferId));
    }

    @NonNull
    public List<TransferProgress> list(@NonNull String projectId) {
        List<TransferProgress> result = new ArrayList<>();
        for (Map.Entry<String, TransferProgress> entry : transfers.entrySet()) {
            if (entry.getKey().startsWith(projectId + "|")) {
                result.add(entry.getValue());
            }
        }
        result.sort((left, right) -> Long.compare(right.getUpdatedAt(), left.getUpdatedAt()));
        return result;
    }

    @NonNull
    public Map<String, Object> summary(@NonNull String projectId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int active = 0;
        int completed = 0;
        int failed = 0;
        for (TransferProgress progress : list(projectId)) {
            if (TransferProgress.STATE_RUNNING.equals(progress.getState())
                    || TransferProgress.STATE_QUEUED.equals(progress.getState())) {
                active++;
            } else if (TransferProgress.STATE_COMPLETED.equals(progress.getState())) {
                completed++;
            } else {
                failed++;
            }
        }
        summary.put("active", active);
        summary.put("completed", completed);
        summary.put("failed", failed);
        return summary;
    }

    public void release() {
        if (released.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }

    @NonNull
    private static String key(@NonNull String projectId, @NonNull String transferId) {
        return projectId + "|" + transferId;
    }
}
