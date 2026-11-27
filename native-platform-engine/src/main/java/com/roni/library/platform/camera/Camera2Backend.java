package com.roni.library.platform.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.contracts.camera.CameraErrorCodes;
import com.roni.library.contracts.camera.CameraFlashMode;
import com.roni.library.contracts.camera.CameraLens;
import com.roni.library.contracts.camera.CameraResult;
import com.roni.library.contracts.camera.CameraState;
import com.roni.library.contracts.camera.PhotoCaptureRequest;
import com.roni.library.contracts.camera.VideoRecordingRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Camera2 implementation of {@link CameraBackend}.
 *
 * <p>Camera2 was chosen over CameraX because the ecosystem already targets the framework camera
 * APIs, needs manual ISO / shutter / focus control, and must not add a new dependency to the
 * module graph. Everything runs on a dedicated {@link HandlerThread}; blocking calls
 * ({@link #capture}, {@link #startRecording}, {@link #stopRecording}) must never run on the main
 * thread.</p>
 */
public final class Camera2Backend implements CameraBackend {

    private static final long CAPTURE_TIMEOUT_MS = 15_000L;
    private static final long SESSION_TIMEOUT_MS = 8_000L;

    private final Context context;
    private final CameraManager cameraManager;
    private final HandlerThread cameraThread;
    private final Handler cameraHandler;
    private final CameraStateMachine stateMachine = new CameraStateMachine();
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);

    private CameraCapabilities capabilities = CameraCapabilities.unsupported();
    private CameraConfiguration configuration = CameraConfiguration.defaults();
    private String backCameraId;
    private String frontCameraId;
    private String activeCameraId;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CameraCharacteristics characteristics;
    private TextureView previewTarget;
    private Surface previewSurface;
    private ImageReader photoReader;
    private MediaRecorder recorder;
    private File recordingFile;
    private long recordingStartedAt;
    private long recordingPausedAt;
    private long recordingPausedTotal;
    private float zoomRatio = 1f;
    private CameraFlashMode flashMode = CameraFlashMode.OFF;
    private float exposureEv;
    private int sensorOrientation;
    private boolean frontFacing;
    private int previewWidth = 1280;
    private int previewHeight = 720;

    public Camera2Backend(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
        this.cameraThread = new HandlerThread("roni-camera");
        this.cameraThread.start();
        this.cameraHandler = new Handler(cameraThread.getLooper());
        this.capabilities = detectCapabilities();
    }

    // ------------------------------------------------------------------
    // capabilities
    // ------------------------------------------------------------------

    @NonNull
    private CameraCapabilities detectCapabilities() {
        if (cameraManager == null) {
            return CameraCapabilities.unsupported();
        }
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) {
                return CameraCapabilities.unsupported();
            }
            List<CameraLens> lenses = new ArrayList<>();
            CameraCharacteristics preferred = null;
            for (String id : ids) {
                CameraCharacteristics info = cameraManager.getCameraCharacteristics(id);
                Integer facing = info.get(CameraCharacteristics.LENS_FACING);
                if (facing == null) {
                    continue;
                }
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = id;
                    lenses.add(CameraLens.BACK);
                    if (preferred == null) {
                        preferred = info;
                    }
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id;
                    lenses.add(CameraLens.FRONT);
                    if (preferred == null) {
                        preferred = info;
                    }
                } else {
                    lenses.add(CameraLens.EXTERNAL);
                    if (preferred == null) {
                        preferred = info;
                    }
                }
            }
            if (preferred == null) {
                return CameraCapabilities.unsupported();
            }
            return buildCapabilities(preferred, lenses);
        } catch (Throwable error) {
            return CameraCapabilities.unsupported();
        }
    }

    @NonNull
    private CameraCapabilities buildCapabilities(@NonNull CameraCharacteristics info,
                                                 @NonNull List<CameraLens> lenses) {
        CameraCapabilities.Builder builder = new CameraCapabilities.Builder().supported(true);
        for (CameraLens lens : lenses) {
            builder.addLens(lens);
        }

        StreamConfigurationMap map =
                info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] photoSizes = map != null ? map.getOutputSizes(ImageFormat.JPEG) : null;
        Size largest = null;
        if (photoSizes != null) {
            for (Size size : photoSizes) {
                if (largest == null || size.getWidth() * size.getHeight()
                        > largest.getWidth() * largest.getHeight()) {
                    largest = size;
                }
            }
        }
        if (largest != null) {
            builder.maxPhotoSize(largest.getWidth(), largest.getHeight());
        }
        builder.addAspectRatio("4:3");
        builder.addAspectRatio("16:9");
        if (photoSizes != null) {
            for (Size size : photoSizes) {
                if (size.getWidth() == size.getHeight()) {
                    builder.addAspectRatio("1:1");
                    break;
                }
            }
        }

        Float maxZoom = info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        builder.zoom(maxZoom != null && maxZoom > 1f, maxZoom != null ? maxZoom : 1f);

        Boolean flashAvailable = info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (Boolean.TRUE.equals(flashAvailable)) {
            builder.addFlashMode(CameraFlashMode.OFF);
            builder.addFlashMode(CameraFlashMode.ON);
            builder.addFlashMode(CameraFlashMode.AUTO);
            builder.addFlashMode(CameraFlashMode.TORCH);
        } else {
            builder.addFlashMode(CameraFlashMode.OFF);
        }

        int[] sceneModes = info.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        boolean hdr = false;
        if (sceneModes != null) {
            for (int mode : sceneModes) {
                if (mode == CameraCharacteristics.CONTROL_SCENE_MODE_HDR) {
                    hdr = true;
                    break;
                }
            }
        }
        builder.hdr(hdr);

        Range<Integer> isoRange = info.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (isoRange != null && isoRange.getUpper() > isoRange.getLower()) {
            builder.iso(true, isoRange.getLower(), isoRange.getUpper());
        }

        Range<Long> shutterRange = info.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (shutterRange != null && shutterRange.getUpper() > shutterRange.getLower()) {
            builder.shutter(true, shutterRange.getLower(), shutterRange.getUpper());
        }

        Float minFocus = info.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        builder.manualFocus(minFocus != null && minFocus > 0f, minFocus != null ? minFocus : 0f);

        int[] awbModes = info.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        List<String> wbModes = new ArrayList<>();
        if (awbModes != null) {
            for (int mode : awbModes) {
                String name = whiteBalanceName(mode);
                if (name != null) {
                    wbModes.add(name);
                }
            }
        }
        builder.whiteBalance(!wbModes.isEmpty(), wbModes);

        Range<Integer> evRange =
                info.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        android.util.Rational evStep =
                info.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (evRange != null && evStep != null) {
            builder.exposureCompensation(true,
                    evRange.getLower() * evStep.floatValue(),
                    evRange.getUpper() * evStep.floatValue(),
                    evStep.floatValue());
        }

        int[] stabilization =
                info.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        builder.stabilization(stabilization != null && stabilization.length > 0);

        List<String> videoSizes = new ArrayList<>();
        List<Integer> frameRates = new ArrayList<>();
        if (map != null) {
            Size[] recorderSizes = map.getOutputSizes(MediaRecorder.class);
            if (recorderSizes != null) {
                List<Size> sorted = new ArrayList<>(Arrays.asList(recorderSizes));
                Collections.sort(sorted, new Comparator<Size>() {
                    @Override
                    public int compare(Size left, Size right) {
                        return Integer.compare(right.getWidth() * right.getHeight(),
                                left.getWidth() * left.getHeight());
                    }
                });
                for (Size size : sorted) {
                    if (size.getWidth() < 1280 || size.getHeight() < 720) {
                        continue;
                    }
                    String value = size.getWidth() + "x" + size.getHeight();
                    if (!videoSizes.contains(value)) {
                        videoSizes.add(value);
                    }
                    if (videoSizes.size() >= 8) {
                        break;
                    }
                }
            }
        }
        Range<Integer>[] fpsRanges =
                info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (fpsRanges != null) {
            for (Range<Integer> range : fpsRanges) {
                int upper = range.getUpper();
                if (!frameRates.contains(upper)) {
                    frameRates.add(upper);
                }
            }
            Collections.sort(frameRates);
        }
        builder.video(!videoSizes.isEmpty(), videoSizes, frameRates);

        PackageManager packageManager = context.getPackageManager();
        builder.audio(packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
        builder.histogram(true);
        return builder.build();
    }

    @Nullable
    private static String whiteBalanceName(int mode) {
        switch (mode) {
            case CameraCharacteristics.CONTROL_AWB_MODE_AUTO:
                return "AUTO";
            case CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT:
                return "INCANDESCENT";
            case CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT:
                return "FLUORESCENT";
            case CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT:
                return "DAYLIGHT";
            case CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT:
                return "CLOUDY";
            case CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT:
                return "TWILIGHT";
            case CameraCharacteristics.CONTROL_AWB_MODE_SHADE:
                return "SHADE";
            default:
                return null;
        }
    }

    private static int whiteBalanceMode(@NonNull String name) {
        switch (name.toUpperCase(java.util.Locale.US)) {
            case "INCANDESCENT":
                return CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT;
            case "FLUORESCENT":
                return CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT;
            case "DAYLIGHT":
                return CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT;
            case "CLOUDY":
                return CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
            case "TWILIGHT":
                return CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT;
            case "SHADE":
                return CameraCharacteristics.CONTROL_AWB_MODE_SHADE;
            case "AUTO":
            default:
                return CameraCharacteristics.CONTROL_AWB_MODE_AUTO;
        }
    }

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    @Override
    public boolean isSupported() {
        return capabilities.isSupported();
    }

    @NonNull
    @Override
    public CameraCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean hasCameraPermission() {
        return context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean hasAudioPermission() {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void setPreviewTarget(@Nullable TextureView target) {
        this.previewTarget = target;
    }

    @NonNull
    @Override
    public CameraResult<CameraCapabilities> open(@NonNull CameraConfiguration config) {
        if (!capabilities.isSupported() || cameraManager == null) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED,
                    "This device does not expose a usable camera");
        }
        if (!hasCameraPermission()) {
            return CameraResult.fail(CameraErrorCodes.PERMISSION_DENIED,
                    "Camera permission has not been granted");
        }
        try {
            stateMachine.transition(CameraState.OPENING);
        } catch (CameraException error) {
            return CameraResult.fail(error.getCode(), error.getMessage());
        }
        closeInternal(false);
        configuration = config;
        activeCameraId = config.getLens() == CameraLens.FRONT ? frontCameraId : backCameraId;
        if (activeCameraId == null) {
            stateMachine.reset();
            return CameraResult.fail(CameraErrorCodes.INVALID_LENS,
                    "Requested lens is not available");
        }
        try {
            characteristics = cameraManager.getCameraCharacteristics(activeCameraId);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation != null ? orientation : 0;
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            frontFacing = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
            capabilities = buildCapabilities(characteristics, capabilities.getLenses());
            cameraManager.openCamera(activeCameraId, deviceCallback, cameraHandler);
            return CameraResult.ok(capabilities);
        } catch (CameraAccessException | SecurityException error) {
            stateMachine.reset();
            return CameraResult.fail(CameraErrorCodes.PERMISSION_DENIED,
                    "Unable to open the camera: " + error.getMessage());
        }
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            device = camera;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            device = null;
            stateMachine.tryTransition(CameraState.ERROR);
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            device = null;
            stateMachine.tryTransition(CameraState.ERROR);
        }
    };

    private void createPreviewSession() {
        if (device == null) {
            return;
        }
        try {
            if (previewTarget != null && previewTarget.isAvailable()) {
                SurfaceTexture texture = previewTarget.getSurfaceTexture();
                if (texture != null) {
                    texture.setDefaultBufferSize(previewWidth, previewHeight);
                    previewSurface = new Surface(texture);
                }
            }
            if (previewSurface == null) {
                stateMachine.tryTransition(CameraState.ERROR);
                return;
            }
            photoReader = ImageReader.newInstance(previewWidth, previewHeight,
                    ImageFormat.JPEG, 2);
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(photoReader.getSurface());
            device.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    session = captureSession;
                    try {
                        captureSession.setRepeatingRequest(buildPreviewRequest(), null,
                                cameraHandler);
                        stateMachine.tryTransition(CameraState.PREVIEW);
                    } catch (Throwable error) {
                        stateMachine.tryTransition(CameraState.ERROR);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    stateMachine.tryTransition(CameraState.ERROR);
                }
            }, cameraHandler);
        } catch (Throwable error) {
            stateMachine.tryTransition(CameraState.ERROR);
        }
    }

    @NonNull
    private CaptureRequest buildPreviewRequest() throws CameraAccessException {
        CaptureRequest.Builder builder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        builder.addTarget(previewSurface);
        applyCommon(builder);
        return builder.build();
    }

    private void applyCommon(@NonNull CaptureRequest.Builder builder) {
        applyFlash(builder);
        applyZoom(builder);
        applyExposure(builder);
        applyWhiteBalance(builder);
        applyManualExposure(builder);
        applyManualFocus(builder);
    }

    private void applyFlash(@NonNull CaptureRequest.Builder builder) {
        switch (flashMode) {
            case ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                break;
            case AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            case TORCH:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            case OFF:
            default:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    private void applyZoom(@NonNull CaptureRequest.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
            return;
        }
        if (characteristics == null) {
            return;
        }
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null) {
            return;
        }
        float ratio = Math.max(1f, zoomRatio);
        int cropWidth = Math.round(active.width() / ratio);
        int cropHeight = Math.round(active.height() / ratio);
        int left = active.left + (active.width() - cropWidth) / 2;
        int top = active.top + (active.height() - cropHeight) / 2;
        builder.set(CaptureRequest.SCALER_CROP_REGION,
                new Rect(left, top, left + cropWidth, top + cropHeight));
    }

    private void applyExposure(@NonNull CaptureRequest.Builder builder) {
        if (!capabilities.isExposureCompensationSupported()) {
            return;
        }
        float step = capabilities.getExposureCompensationStep();
        if (step <= 0f) {
            return;
        }
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                Math.round(exposureEv / step));
    }

    private void applyWhiteBalance(@NonNull CaptureRequest.Builder builder) {
        if (!capabilities.isWhiteBalanceSupported()) {
            return;
        }
        builder.set(CaptureRequest.CONTROL_AWB_MODE,
                whiteBalanceMode(configuration.getWhiteBalanceMode()));
    }

    private void applyManualExposure(@NonNull CaptureRequest.Builder builder) {
        if (!configuration.isProMode()) {
            return;
        }
        if (configuration.getIso() > 0 && capabilities.isManualIsoSupported()) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, configuration.getIso());
        }
        if (configuration.getShutterNs() > 0 && capabilities.isManualShutterSupported()) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, configuration.getShutterNs());
        }
    }

    private void applyManualFocus(@NonNull CaptureRequest.Builder builder) {
        if (!configuration.isProMode() || configuration.getFocusDistance() <= 0f
                || !capabilities.isManualFocusSupported()) {
            return;
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, configuration.getFocusDistance());
    }

    @Override
    public void close() {
        closeInternal(true);
    }

    private void closeInternal(boolean transition) {
        try {
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (Throwable ignored) {
                }
                recorder.reset();
                recorder.release();
                recorder = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (device != null) {
                device.close();
                device = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (photoReader != null) {
                photoReader.close();
                photoReader = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (previewSurface != null) {
                previewSurface.release();
                previewSurface = null;
            }
        } catch (Throwable ignored) {
        }
        captureInFlight.set(false);
        if (transition) {
            stateMachine.tryTransition(CameraState.CLOSED);
        }
    }

    // ------------------------------------------------------------------
    // controls
    // ------------------------------------------------------------------

    @NonNull
    @Override
    public CameraResult<Void> applyConfiguration(@NonNull CameraConfiguration config) {
        configuration = config;
        flashMode = config.getFlashMode();
        exposureEv = config.getExposureCompensation();
        return refreshPreview();
    }

    @NonNull
    @Override
    public CameraResult<Void> setZoom(float zoom) {
        if (!capabilities.isZoomSupported()) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED_FEATURE,
                    "Zoom is not supported by this camera");
        }
        zoomRatio = Math.max(1f, Math.min(zoom, capabilities.getMaxZoom()));
        return refreshPreview();
    }

    @NonNull
    @Override
    public CameraResult<Void> setFlash(@NonNull CameraFlashMode mode) {
        if (!capabilities.supportsFlash(mode)) {
            return CameraResult.fail(CameraErrorCodes.INVALID_FLASH,
                    "Flash mode " + mode.name() + " is not supported");
        }
        flashMode = mode;
        return refreshPreview();
    }

    @NonNull
    @Override
    public CameraResult<Void> focus(float normalizedX, float normalizedY) {
        if (session == null || device == null || characteristics == null) {
            return CameraResult.fail(CameraErrorCodes.NOT_OPEN, "Camera is not open");
        }
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED_FEATURE,
                    "Focus regions are not available");
        }
        try {
            int x = active.left + Math.round(Math.max(0f, Math.min(1f, normalizedX))
                    * active.width());
            int y = active.top + Math.round(Math.max(0f, Math.min(1f, normalizedY))
                    * active.height());
            int half = Math.max(50, Math.min(active.width(), active.height()) / 10);
            Rect region = new Rect(
                    Math.max(active.left, x - half),
                    Math.max(active.top, y - half),
                    Math.min(active.right, x + half),
                    Math.min(active.bottom, y + half));
            MeteringRectangle metering = new MeteringRectangle(region, 1000);
            CaptureRequest.Builder builder =
                    device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            applyCommon(builder);
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{metering});
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{metering});
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            session.setRepeatingRequest(builder.build(), null, cameraHandler);
            return CameraResult.ok(null);
        } catch (Throwable error) {
            return CameraResult.fail(CameraErrorCodes.CAPTURE_FAILED,
                    "Unable to focus: " + error.getMessage());
        }
    }

    @NonNull
    @Override
    public CameraResult<Void> setExposureCompensation(float ev) {
        if (!capabilities.isExposureCompensationSupported()) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED_FEATURE,
                    "Exposure compensation is not supported");
        }
        exposureEv = Math.max(capabilities.getMinExposureCompensation(),
                Math.min(ev, capabilities.getMaxExposureCompensation()));
        return refreshPreview();
    }

    @NonNull
    private CameraResult<Void> refreshPreview() {
        if (session == null || device == null) {
            return CameraResult.ok(null);
        }
        try {
            session.setRepeatingRequest(buildPreviewRequest(), null, cameraHandler);
            return CameraResult.ok(null);
        } catch (Throwable error) {
            return CameraResult.fail(CameraErrorCodes.CAPTURE_FAILED,
                    "Unable to update the preview: " + error.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // photo capture
    // ------------------------------------------------------------------

    @NonNull
    @Override
    public CameraResult<File> capture(@NonNull PhotoCaptureRequest request,
                                      @NonNull File output) {
        if (device == null || previewSurface == null) {
            return CameraResult.fail(CameraErrorCodes.NOT_OPEN, "Camera is not open");
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            return CameraResult.fail(CameraErrorCodes.BUSY, "A capture is already running");
        }
        try {
            stateMachine.transition(CameraState.CAPTURING);
        } catch (CameraException error) {
            captureInFlight.set(false);
            return CameraResult.fail(error.getCode(), error.getMessage());
        }
        try {
            if (request.getSelfTimerMs() > 0) {
                Thread.sleep(request.getSelfTimerMs());
            }
            Size size = choosePhotoSize(request.getWidth(), request.getHeight());
            ImageReader reader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                    ImageFormat.JPEG, Math.max(2, request.getBurstCount()));
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<File> written = new AtomicReference<>();
            AtomicReference<String> failure = new AtomicReference<>();
            reader.setOnImageAvailableListener(imageReader -> {
                Image image = null;
                try {
                    image = imageReader.acquireNextImage();
                    if (image == null) {
                        return;
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream stream = new FileOutputStream(output);
                    try {
                        stream.write(bytes);
                    } finally {
                        stream.close();
                    }
                    written.set(output);
                } catch (Throwable error) {
                    failure.set(error.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    latch.countDown();
                }
            }, cameraHandler);

            CountDownLatch configured = new CountDownLatch(1);
            AtomicReference<CameraCaptureSession> stillSession = new AtomicReference<>();
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(reader.getSurface());
            device.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    stillSession.set(captureSession);
                    configured.countDown();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    configured.countDown();
                }
            }, cameraHandler);

            if (!configured.await(SESSION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    || stillSession.get() == null) {
                reader.close();
                restorePreview();
                return CameraResult.fail(CameraErrorCodes.CAPTURE_FAILED,
                        "Unable to configure the capture session");
            }

            CameraCaptureSession captureSession = stillSession.get();
            for (int shot = 0; shot < request.getBurstCount(); shot++) {
                CaptureRequest.Builder builder =
                        device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(reader.getSurface());
                applyCommon(builder);
                builder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
                builder.set(CaptureRequest.JPEG_QUALITY,
                        (byte) Math.max(1, Math.min(100, request.getJpegQuality())));
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureSession.capture(builder.build(), null, cameraHandler);
                if (!latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
            captureSession.close();
            reader.close();
            restorePreview();
            if (written.get() == null) {
                String message = failure.get();
                return CameraResult.fail(CameraErrorCodes.CAPTURE_FAILED,
                        message != null ? message : "The capture timed out");
            }
            return CameraResult.ok(written.get());
        } catch (Throwable error) {
            restorePreview();
            return CameraResult.fail(CameraErrorCodes.CAPTURE_FAILED,
                    "Capture failed: " + error.getMessage());
        } finally {
            captureInFlight.set(false);
            stateMachine.tryTransition(CameraState.PREVIEW);
        }
    }

    private void restorePreview() {
        if (device == null || previewSurface == null) {
            return;
        }
        try {
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            device.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    session = captureSession;
                    try {
                        captureSession.setRepeatingRequest(buildPreviewRequest(), null,
                                cameraHandler);
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                }
            }, cameraHandler);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private Size choosePhotoSize(int requestedWidth, int requestedHeight) {
        if (requestedWidth > 0 && requestedHeight > 0) {
            return new Size(requestedWidth, requestedHeight);
        }
        if (characteristics != null) {
            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                if (sizes != null && sizes.length > 0) {
                    Size best = sizes[0];
                    for (Size size : sizes) {
                        if (size.getWidth() * size.getHeight()
                                > best.getWidth() * best.getHeight()) {
                            best = size;
                        }
                    }
                    return best;
                }
            }
        }
        return new Size(previewWidth, previewHeight);
    }

    private int jpegOrientation() {
        int deviceRotation = 0;
        try {
            WindowManager windowManager =
                    (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        deviceRotation = 90;
                        break;
                    case Surface.ROTATION_180:
                        deviceRotation = 180;
                        break;
                    case Surface.ROTATION_270:
                        deviceRotation = 270;
                        break;
                    default:
                        deviceRotation = 0;
                        break;
                }
            }
        } catch (Throwable ignored) {
        }
        if (frontFacing) {
            return (sensorOrientation - deviceRotation + 360) % 360;
        }
        return (sensorOrientation + deviceRotation) % 360;
    }

    // ------------------------------------------------------------------
    // video recording
    // ------------------------------------------------------------------

    @NonNull
    @Override
    public CameraResult<File> startRecording(@NonNull VideoRecordingRequest request,
                                             @NonNull File output) {
        if (device == null || previewSurface == null) {
            return CameraResult.fail(CameraErrorCodes.NOT_OPEN, "Camera is not open");
        }
        if (recorder != null) {
            return CameraResult.fail(CameraErrorCodes.ALREADY_RECORDING,
                    "A recording is already running");
        }
        if (request.isWithAudio() && !hasAudioPermission()) {
            return CameraResult.fail(CameraErrorCodes.MIC_PERMISSION_DENIED,
                    "Microphone permission is required to record audio");
        }
        try {
            stateMachine.transition(CameraState.RECORDING);
        } catch (CameraException error) {
            return CameraResult.fail(error.getCode(), error.getMessage());
        }
        try {
            Size size = chooseVideoSize(request.getWidth(), request.getHeight());
            MediaRecorder created = createRecorder(size, request, output);
            Surface recorderSurface = created.getSurface();
            CountDownLatch configured = new CountDownLatch(1);
            AtomicReference<CameraCaptureSession> recordSession = new AtomicReference<>();
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(recorderSurface);
            device.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    recordSession.set(captureSession);
                    configured.countDown();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    configured.countDown();
                }
            }, cameraHandler);

            if (!configured.await(SESSION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    || recordSession.get() == null) {
                created.reset();
                created.release();
                stateMachine.tryTransition(CameraState.PREVIEW);
                return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                        "Unable to configure the recording session");
            }
            session = recordSession.get();
            CaptureRequest.Builder builder =
                    device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(previewSurface);
            builder.addTarget(recorderSurface);
            applyCommon(builder);
            session.setRepeatingRequest(builder.build(), null, cameraHandler);
            created.start();
            recorder = created;
            recordingFile = output;
            recordingStartedAt = SystemClock.elapsedRealtime();
            recordingPausedAt = 0L;
            recordingPausedTotal = 0L;
            return CameraResult.ok(output);
        } catch (Throwable error) {
            stateMachine.tryTransition(CameraState.PREVIEW);
            return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                    "Unable to start recording: " + error.getMessage());
        }
    }

    @NonNull
    private MediaRecorder createRecorder(@NonNull Size size,
                                         @NonNull VideoRecordingRequest request,
                                         @NonNull File output) throws IOException {
        MediaRecorder created;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            created = new MediaRecorder(context);
        } else {
            created = new MediaRecorder();
        }
        if (request.isWithAudio()) {
            created.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        created.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        created.setOutputFile(output.getAbsolutePath());
        int bitRate = Math.max(2_000_000,
                size.getWidth() * size.getHeight() * request.getFrameRate() / 8);
        created.setVideoEncodingBitRate(bitRate);
        created.setVideoFrameRate(request.getFrameRate());
        created.setVideoSize(size.getWidth(), size.getHeight());
        created.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (request.isWithAudio()) {
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            created.setAudioEncodingBitRate(128_000);
            created.setAudioSamplingRate(44_100);
        }
        created.setOrientationHint(jpegOrientation());
        created.prepare();
        return created;
    }

    @NonNull
    private Size chooseVideoSize(int requestedWidth, int requestedHeight) {
        if (requestedWidth > 0 && requestedHeight > 0) {
            return new Size(requestedWidth, requestedHeight);
        }
        if (characteristics != null) {
            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(MediaRecorder.class);
                if (sizes != null) {
                    Size best = null;
                    for (Size size : sizes) {
                        if (size.getWidth() > 1920 || size.getHeight() > 1080) {
                            continue;
                        }
                        if (best == null || size.getWidth() * size.getHeight()
                                > best.getWidth() * best.getHeight()) {
                            best = size;
                        }
                    }
                    if (best != null) {
                        return best;
                    }
                }
            }
        }
        return new Size(1280, 720);
    }

    @NonNull
    @Override
    public CameraResult<File> stopRecording() {
        if (recorder == null) {
            return CameraResult.fail(CameraErrorCodes.NOT_RECORDING, "No recording is running");
        }
        File output = recordingFile;
        try {
            if (stateMachine.getState() == CameraState.PAUSED) {
                recorder.resume();
            }
            recorder.stop();
        } catch (Throwable error) {
            if (output != null && output.exists()) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }
            releaseRecorder();
            restorePreview();
            stateMachine.tryTransition(CameraState.PREVIEW);
            return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                    "Recording could not be finalized: " + error.getMessage());
        }
        releaseRecorder();
        restorePreview();
        stateMachine.tryTransition(CameraState.PREVIEW);
        if (output == null) {
            return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                    "Recording file is missing");
        }
        return CameraResult.ok(output);
    }

    private void releaseRecorder() {
        try {
            if (recorder != null) {
                recorder.reset();
                recorder.release();
            }
        } catch (Throwable ignored) {
        }
        recorder = null;
        recordingFile = null;
        recordingStartedAt = 0L;
        recordingPausedAt = 0L;
        recordingPausedTotal = 0L;
    }

    @NonNull
    @Override
    public CameraResult<Void> pauseRecording() {
        if (recorder == null || stateMachine.getState() != CameraState.RECORDING) {
            return CameraResult.fail(CameraErrorCodes.NOT_RECORDING, "No recording is running");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return CameraResult.fail(CameraErrorCodes.UNSUPPORTED_FEATURE,
                    "Pause is not supported below Android 7.0");
        }
        try {
            recorder.pause();
            recordingPausedAt = SystemClock.elapsedRealtime();
            stateMachine.transition(CameraState.PAUSED);
            return CameraResult.ok(null);
        } catch (Throwable error) {
            return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                    "Unable to pause: " + error.getMessage());
        }
    }

    @NonNull
    @Override
    public CameraResult<Void> resumeRecording() {
        if (recorder == null || stateMachine.getState() != CameraState.PAUSED) {
            return CameraResult.fail(CameraErrorCodes.NOT_RECORDING,
                    "No paused recording is running");
        }
        try {
            recorder.resume();
            if (recordingPausedAt > 0L) {
                recordingPausedTotal += SystemClock.elapsedRealtime() - recordingPausedAt;
                recordingPausedAt = 0L;
            }
            stateMachine.transition(CameraState.RECORDING);
            return CameraResult.ok(null);
        } catch (Throwable error) {
            return CameraResult.fail(CameraErrorCodes.RECORDING_FAILED,
                    "Unable to resume: " + error.getMessage());
        }
    }

    @Override
    public boolean isRecording() {
        return recorder != null;
    }

    @Override
    public long getRecordingDurationMs() {
        if (recordingStartedAt == 0L) {
            return 0L;
        }
        long now = SystemClock.elapsedRealtime();
        long paused = recordingPausedTotal;
        if (recordingPausedAt > 0L) {
            paused += now - recordingPausedAt;
        }
        return Math.max(0L, now - recordingStartedAt - paused);
    }

    @Override
    public long getRecordingSizeBytes() {
        return recordingFile != null && recordingFile.exists() ? recordingFile.length() : 0L;
    }

    @NonNull
    @Override
    public CameraState getState() {
        return stateMachine.getState();
    }
}
