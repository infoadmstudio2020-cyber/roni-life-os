package com.roni.library.platform.camera;

import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.contracts.camera.CameraError;
import com.roni.library.contracts.camera.CameraErrorCodes;
import com.roni.library.contracts.camera.CameraFlashMode;
import com.roni.library.contracts.camera.CameraLens;
import com.roni.library.contracts.camera.CameraLimits;
import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.contracts.camera.CameraResult;
import com.roni.library.contracts.camera.CameraState;
import com.roni.library.contracts.camera.PhotoCaptureRequest;
import com.roni.library.contracts.camera.VideoRecordingRequest;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera engine policy layer.
 *
 * <p>Owns project scoping, validation, media registration and state reporting. Hardware work is
 * delegated to a {@link CameraBackend}, which keeps this class fully testable on the JVM.</p>
 *
 * <p>Blocking methods ({@link #capture}, {@link #startRecording}, {@link #stopRecording}) must be
 * called from a worker thread.</p>
 */
public final class CameraService {

    private final CameraBackend backend;
    private final CameraMediaRepository repository;
    private final Object lock = new Object();

    @Nullable
    private CameraEventListener listener;
    private CameraConfiguration configuration = CameraConfiguration.defaults();
    private boolean enabled = true;
    private String activeRecordingId = "";

    public CameraService(@NonNull CameraBackend backend,
                         @NonNull CameraMediaRepository repository) {
        this.backend = backend;
        this.repository = repository;
    }

    public void init() {
        String project = CameraProjectContext.getActiveProject();
        if (!project.isEmpty()) {
            repository.recoverInterrupted(project);
        }
    }

    public void release() {
        try {
            if (backend.isRecording()) {
                backend.stopRecording();
            }
        } catch (Throwable ignored) {
        }
        backend.close();
        activeRecordingId = "";
    }

    /** Attaches the host preview surface; the engine never creates its own window. */
    public void setPreviewTarget(@Nullable TextureView target) {
        backend.setPreviewTarget(target);
    }

    public void setListener(@Nullable CameraEventListener value) {
        this.listener = value;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSupported() {
        return backend.isSupported();
    }

    @NonNull
    public CameraCapabilities getCapabilities() {
        return backend.getCapabilities();
    }

    @NonNull
    public CameraState getState() {
        return backend.getState();
    }

    @NonNull
    public CameraConfiguration getConfiguration() {
        return configuration;
    }

    public boolean hasCameraPermission() {
        return backend.hasCameraPermission();
    }

    public boolean hasAudioPermission() {
        return backend.hasAudioPermission();
    }

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    @NonNull
    public CameraResult<CameraCapabilities> open(@NonNull CameraConfiguration requested) {
        if (!enabled) {
            return CameraResult.fail(CameraErrorCodes.DISABLED, "The camera is disabled");
        }
        if (!CameraProjectContext.hasActiveProject()) {
            return CameraResult.fail(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active");
        }
        if (!backend.isSupported()) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED,
                    "This device does not expose a usable camera");
        }
        if (!backend.hasCameraPermission()) {
            return CameraResult.fail(CameraErrorCodes.PERMISSION_DENIED,
                    "Camera permission has not been granted");
        }
        NativeApiError error = requested.validate(backend.getCapabilities());
        if (error != null) {
            return CameraResult.fail(toCameraError(error));
        }
        CameraResult<CameraCapabilities> result = backend.open(requested);
        if (result.isSuccess()) {
            configuration = requested;
            notifyState();
        } else if (result.getError() != null) {
            notifyError(result.getError());
        }
        return result;
    }

    public void close() {
        backend.close();
        activeRecordingId = "";
        notifyState();
    }

    @NonNull
    public CameraResult<Void> switchCamera() {
        CameraLens next = configuration.getLens() == CameraLens.BACK
                ? CameraLens.FRONT : CameraLens.BACK;
        if (!backend.getCapabilities().supportsLens(next)) {
            return CameraResult.fail(CameraErrorCodes.INVALID_LENS,
                    "Lens " + next.name() + " is not available");
        }
        CameraConfiguration updated = configuration.toBuilder().lens(next).build();
        CameraResult<CameraCapabilities> result = open(updated);
        if (!result.isSuccess()) {
            CameraError error = result.getError();
            return CameraResult.fail(error != null ? error
                    : new CameraError(CameraErrorCodes.BUSY, "Unable to switch camera"));
        }
        return CameraResult.ok(null);
    }

    // ------------------------------------------------------------------
    // photo
    // ------------------------------------------------------------------

    @NonNull
    public CameraResult<CameraMediaItem> capture(@NonNull PhotoCaptureRequest request) {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return CameraResult.fail(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active");
        }
        if (!enabled) {
            return CameraResult.fail(CameraErrorCodes.DISABLED, "The camera is disabled");
        }
        if (!backend.hasCameraPermission()) {
            return CameraResult.fail(CameraErrorCodes.PERMISSION_DENIED,
                    "Camera permission has not been granted");
        }
        NativeApiError invalid = request.validate();
        if (invalid != null) {
            return CameraResult.fail(toCameraError(invalid));
        }
        if (!backend.getState().isOpen()) {
            return CameraResult.fail(CameraErrorCodes.NOT_OPEN, "Camera is not open");
        }
        try {
            repository.ensureCapacity(4L * 1024L * 1024L);
            File output = repository.createPhotoFile(project, request.getKey());
            CameraResult<File> captured = backend.capture(request, output);
            if (!captured.isSuccess()) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                CameraError error = captured.getError();
                if (error != null) {
                    notifyError(error);
                    return CameraResult.fail(error);
                }
                return CameraResult.fail(CameraErrorCodes.CAPTURE_FAILED, "Capture failed");
            }
            String thumbnail = repository.generateThumbnail(project, output, false);
            CameraMediaItem item = new CameraMediaItem.Builder()
                    .id(project + "-" + System.currentTimeMillis() + "-" + request.getKey())
                    .projectId(project)
                    .type(CameraMediaItem.TYPE_PHOTO)
                    .fileName(output.getName())
                    .relativePath(repository.relativePath(project, output))
                    .thumbnailPath(thumbnail)
                    .mimeType("image/jpeg")
                    .sizeBytes(output.length())
                    .size(0, 0)
                    .createdAt(System.currentTimeMillis())
                    .state(CameraMediaItem.STATE_READY)
                    .build();
            repository.register(item);
            CameraEventListener current = listener;
            if (current != null) {
                current.onPhotoSaved(item);
            }
            return CameraResult.ok(item);
        } catch (CameraException error) {
            notifyError(error.toError());
            return CameraResult.fail(error.toError());
        } catch (Throwable error) {
            CameraError failure = new CameraError(CameraErrorCodes.CAPTURE_FAILED,
                    "Capture failed: " + error.getMessage());
            notifyError(failure);
            return CameraResult.fail(failure);
        }
    }

    // ------------------------------------------------------------------
    // video
    // ------------------------------------------------------------------

    @NonNull
    public CameraResult<CameraMediaItem> startRecording(@NonNull VideoRecordingRequest request) {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return CameraResult.fail(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active");
        }
        if (!enabled) {
            return CameraResult.fail(CameraErrorCodes.DISABLED, "The camera is disabled");
        }
        if (!backend.hasCameraPermission()) {
            return CameraResult.fail(CameraErrorCodes.PERMISSION_DENIED,
                    "Camera permission has not been granted");
        }
        if (request.isWithAudio() && !backend.hasAudioPermission()) {
            return CameraResult.fail(CameraErrorCodes.MIC_PERMISSION_DENIED,
                    "Microphone permission is required to record audio");
        }
        NativeApiError invalid = request.validate();
        if (invalid != null) {
            return CameraResult.fail(toCameraError(invalid));
        }
        if (!backend.getState().isOpen()) {
            return CameraResult.fail(CameraErrorCodes.NOT_OPEN, "Camera is not open");
        }
        if (backend.isRecording()) {
            return CameraResult.fail(CameraErrorCodes.ALREADY_RECORDING,
                    "A recording is already running");
        }
        try {
            repository.ensureCapacity(CameraLimits.MIN_FREE_SPACE_BYTES);
            File output = repository.createVideoFile(project, request.getKey());
            CameraResult<File> started = backend.startRecording(request, output);
            if (!started.isSuccess()) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                CameraError error = started.getError();
                if (error != null) {
                    notifyError(error);
                    return CameraResult.fail(error);
                }
                return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                        "Unable to start recording");
            }
            CameraMediaItem pending = new CameraMediaItem.Builder()
                    .id(project + "-" + System.currentTimeMillis() + "-" + request.getKey())
                    .projectId(project)
                    .type(CameraMediaItem.TYPE_VIDEO)
                    .fileName(output.getName())
                    .relativePath(repository.relativePath(project, output))
                    .mimeType("video/mp4")
                    .createdAt(System.currentTimeMillis())
                    .state(CameraMediaItem.STATE_INTERRUPTED)
                    .build();
            repository.register(pending);
            activeRecordingId = pending.getId();
            CameraEventListener current = listener;
            if (current != null) {
                current.onRecordingStarted();
            }
            notifyState();
            return CameraResult.ok(pending);
        } catch (CameraException error) {
            notifyError(error.toError());
            return CameraResult.fail(error.toError());
        } catch (Throwable error) {
            CameraError failure = new CameraError(CameraErrorCodes.RECORDING_FAILED,
                    "Unable to start recording: " + error.getMessage());
            notifyError(failure);
            return CameraResult.fail(failure);
        }
    }

    @NonNull
    public CameraResult<CameraMediaItem> stopRecording() {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return CameraResult.fail(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active");
        }
        if (!backend.isRecording()) {
            return CameraResult.fail(CameraErrorCodes.NOT_RECORDING, "No recording is running");
        }
        long duration = backend.getRecordingDurationMs();
        CameraResult<File> stopped = backend.stopRecording();
        if (!stopped.isSuccess() || stopped.getValue() == null) {
            CameraError error = stopped.getError();
            if (error != null) {
                notifyError(error);
                return CameraResult.fail(error);
            }
            return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                    "Recording could not be finalized");
        }
        File output = stopped.getValue();
        String thumbnail = repository.generateThumbnail(project, output, true);
        CameraMediaItem item = new CameraMediaItem.Builder()
                .id(activeRecordingId.isEmpty()
                        ? project + "-" + System.currentTimeMillis() + "-video"
                        : activeRecordingId)
                .projectId(project)
                .type(CameraMediaItem.TYPE_VIDEO)
                .fileName(output.getName())
                .relativePath(repository.relativePath(project, output))
                .thumbnailPath(thumbnail)
                .mimeType("video/mp4")
                .sizeBytes(output.length())
                .durationMs(duration)
                .createdAt(System.currentTimeMillis())
                .state(CameraMediaItem.STATE_READY)
                .build();
        repository.register(item);
        activeRecordingId = "";
        CameraEventListener current = listener;
        if (current != null) {
            current.onRecordingStopped(item);
        }
        notifyState();
        return CameraResult.ok(item);
    }

    @NonNull
    public CameraResult<Void> pauseRecording() {
        CameraResult<Void> result = backend.pauseRecording();
        if (result.isSuccess()) {
            notifyState();
        }
        return result;
    }

    @NonNull
    public CameraResult<Void> resumeRecording() {
        CameraResult<Void> result = backend.resumeRecording();
        if (result.isSuccess()) {
            notifyState();
        }
        return result;
    }

    // ------------------------------------------------------------------
    // controls
    // ------------------------------------------------------------------

    @NonNull
    public CameraResult<Void> setZoom(float zoom) {
        if (zoom < 1f || zoom > CameraLimits.MAX_ZOOM) {
            return CameraResult.fail(CameraErrorCodes.INVALID_ZOOM,
                    "Zoom must be between 1 and " + CameraLimits.MAX_ZOOM);
        }
        return backend.setZoom(zoom);
    }

    @NonNull
    public CameraResult<Void> setFlash(@NonNull CameraFlashMode mode) {
        CameraResult<Void> result = backend.setFlash(mode);
        if (result.isSuccess()) {
            configuration = configuration.toBuilder().flashMode(mode).build();
        }
        return result;
    }

    @NonNull
    public CameraResult<Void> setFocus(float normalizedX, float normalizedY) {
        if (normalizedX < 0f || normalizedX > 1f || normalizedY < 0f || normalizedY > 1f) {
            return CameraResult.fail(CameraErrorCodes.INVALID_FOCUS,
                    "Focus coordinates must be normalized between 0 and 1");
        }
        return backend.focus(normalizedX, normalizedY);
    }

    @NonNull
    public CameraResult<Void> setExposure(float ev) {
        CameraCapabilities capabilities = backend.getCapabilities();
        if (!capabilities.isExposureCompensationSupported()) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED_FEATURE,
                    "Exposure compensation is not supported");
        }
        if (ev < capabilities.getMinExposureCompensation()
                || ev > capabilities.getMaxExposureCompensation()) {
            return CameraResult.fail(CameraErrorCodes.INVALID_EXPOSURE,
                    "Exposure compensation is outside the supported range");
        }
        CameraResult<Void> result = backend.setExposureCompensation(ev);
        if (result.isSuccess()) {
            configuration = configuration.toBuilder().exposureCompensation(ev).build();
        }
        return result;
    }

    @NonNull
    public CameraResult<Void> setResolution(int width, int height) {
        CameraCapabilities capabilities = backend.getCapabilities();
        if (width <= 0 || height <= 0) {
            return CameraResult.fail(CameraErrorCodes.INVALID_RESOLUTION,
                    "Resolution must be positive");
        }
        if (width > capabilities.getMaxPhotoWidth() || height > capabilities.getMaxPhotoHeight()) {
            return CameraResult.fail(CameraErrorCodes.INVALID_RESOLUTION,
                    "Requested resolution exceeds the sensor maximum");
        }
        CameraConfiguration updated = configuration.toBuilder()
                .photoSize(width, height).build();
        CameraResult<Void> result = backend.applyConfiguration(updated);
        if (result.isSuccess()) {
            configuration = updated;
        }
        return result;
    }

    @NonNull
    public CameraResult<Void> applyConfiguration(@NonNull CameraConfiguration updated) {
        NativeApiError error = updated.validate(backend.getCapabilities());
        if (error != null) {
            return CameraResult.fail(toCameraError(error));
        }
        CameraResult<Void> result = backend.applyConfiguration(updated);
        if (result.isSuccess()) {
            configuration = updated;
        }
        return result;
    }

    // ------------------------------------------------------------------
    // status and media
    // ------------------------------------------------------------------

    @NonNull
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", backend.getState().name());
        status.put("supported", backend.isSupported());
        status.put("project", CameraProjectContext.getActiveProject());
        status.put("cameraPermission", backend.hasCameraPermission());
        status.put("audioPermission", backend.hasAudioPermission());
        status.put("recording", backend.isRecording());
        status.put("recordingDurationMs", backend.getRecordingDurationMs());
        status.put("recordingSizeBytes", backend.getRecordingSizeBytes());
        status.put("configuration", configuration.toMap());
        status.put("capabilities", backend.getCapabilities().toMap());
        return status;
    }

    @NonNull
    public List<CameraMediaItem> getMedia(@Nullable String type, int limit) {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return repository.list(project, type, limit);
    }

    @Nullable
    public CameraMediaItem getMediaItem(@NonNull String id) {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return null;
        }
        return repository.get(project, id);
    }

    public boolean deleteMedia(@NonNull String id) throws CameraException {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            throw new CameraException(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active");
        }
        return repository.delete(project, id);
    }

    /** Resolves a media file for the host UI. Never exposed to web pages. */
    @Nullable
    public File resolveMedia(@NonNull String id) {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return null;
        }
        CameraMediaItem item = repository.get(project, id);
        if (item == null) {
            return null;
        }
        try {
            return repository.resolve(project, item.getRelativePath());
        } catch (CameraException error) {
            return null;
        }
    }

    @Nullable
    public File resolveThumbnail(@NonNull String id) {
        String project = CameraProjectContext.getActiveProject();
        if (project.isEmpty()) {
            return null;
        }
        CameraMediaItem item = repository.get(project, id);
        if (item == null || item.getThumbnailPath().isEmpty()) {
            return null;
        }
        try {
            return repository.resolve(project, item.getThumbnailPath());
        } catch (CameraException error) {
            return null;
        }
    }

    public long getFreeSpaceBytes() {
        return repository.freeSpaceBytes();
    }

    @NonNull
    private static CameraError toCameraError(@NonNull NativeApiError error) {
        return new CameraError(error.getCode(), error.getMessage());
    }

    private void notifyState() {
        CameraEventListener current = listener;
        if (current != null) {
            current.onStateChanged(backend.getState());
        }
    }

    private void notifyError(@NonNull CameraError error) {
        CameraEventListener current = listener;
        if (current != null) {
            current.onError(error);
        }
    }
}
