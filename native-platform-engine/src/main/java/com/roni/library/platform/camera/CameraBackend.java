package com.roni.library.platform.camera;

import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.contracts.camera.CameraFlashMode;
import com.roni.library.contracts.camera.CameraResult;
import com.roni.library.contracts.camera.CameraState;
import com.roni.library.contracts.camera.PhotoCaptureRequest;
import com.roni.library.contracts.camera.VideoRecordingRequest;

import java.io.File;

/**
 * Hardware abstraction for the camera engine.
 *
 * <p>The production implementation is {@link Camera2Backend}. Tests substitute a fake so policy,
 * isolation and state handling can be verified on the JVM without a device.</p>
 */
public interface CameraBackend {

    boolean isSupported();

    @NonNull
    CameraCapabilities getCapabilities();

    boolean hasCameraPermission();

    boolean hasAudioPermission();

    /** Preview surface. May be {@code null} in headless tests. */
    void setPreviewTarget(@Nullable TextureView target);

    @NonNull
    CameraResult<CameraCapabilities> open(@NonNull CameraConfiguration configuration);

    void close();

    @NonNull
    CameraResult<Void> applyConfiguration(@NonNull CameraConfiguration configuration);

    @NonNull
    CameraResult<Void> setZoom(float zoom);

    @NonNull
    CameraResult<Void> setFlash(@NonNull CameraFlashMode mode);

    @NonNull
    CameraResult<Void> focus(float normalizedX, float normalizedY);

    @NonNull
    CameraResult<Void> setExposureCompensation(float ev);

    /** Blocking still capture. Must not be called on the main thread. */
    @NonNull
    CameraResult<File> capture(@NonNull PhotoCaptureRequest request, @NonNull File output);

    @NonNull
    CameraResult<File> startRecording(@NonNull VideoRecordingRequest request,
                                      @NonNull File output);

    @NonNull
    CameraResult<File> stopRecording();

    @NonNull
    CameraResult<Void> pauseRecording();

    @NonNull
    CameraResult<Void> resumeRecording();

    boolean isRecording();

    long getRecordingDurationMs();

    long getRecordingSizeBytes();

    @NonNull
    CameraState getState();
}
