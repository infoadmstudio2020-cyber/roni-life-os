package com.roni.library.platform.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.contracts.camera.CameraError;
import com.roni.library.contracts.camera.CameraErrorCodes;
import com.roni.library.contracts.camera.CameraFlashMode;
import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.contracts.camera.CameraResult;
import com.roni.library.contracts.camera.PhotoCaptureRequest;
import com.roni.library.contracts.camera.VideoRecordingRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native bridge route {@code /camera}.
 *
 * <p>The owning project is always taken from {@link CameraProjectContext}; a {@code projectId}
 * inside the payload is ignored. Every action validates its input before touching the hardware.</p>
 */
public final class CameraCommandHandler implements IApiHandler {

    public static final String ROUTE = "camera";

    private final CameraService service;

    public CameraCommandHandler(@NonNull CameraService service) {
        this.service = service;
    }

    @NonNull
    @Override
    public String getPath() {
        return ROUTE;
    }

    @NonNull
    @Override
    public String getAction() {
        return "*";
    }

    @NonNull
    @Override
    public NativeApiResponse handleRequest(@NonNull NativeApiRequest request) {
        String action = request.getAction() != null ? request.getAction() : "";
        try {
            switch (action) {
                case "getCapabilities":
                case "capabilities":
                    return getCapabilities(request);
                case "requestPermission":
                case "permission":
                    return requestPermission(request);
                case "open":
                    return open(request);
                case "close":
                    return close(request);
                case "switchCamera":
                case "switch":
                    return switchCamera(request);
                case "capture":
                case "takePhoto":
                    return capture(request);
                case "startRecording":
                case "record":
                    return startRecording(request);
                case "stopRecording":
                    return stopRecording(request);
                case "pauseRecording":
                    return pauseRecording(request);
                case "resumeRecording":
                    return resumeRecording(request);
                case "setZoom":
                    return setZoom(request);
                case "setFlash":
                    return setFlash(request);
                case "setFocus":
                    return setFocus(request);
                case "setExposure":
                    return setExposure(request);
                case "setResolution":
                    return setResolution(request);
                case "getStatus":
                case "status":
                    return getStatus(request);
                case "getMedia":
                case "listMedia":
                    return getMedia(request);
                case "deleteMedia":
                    return deleteMedia(request);
                default:
                    return failure(request, new CameraError(CameraErrorCodes.INVALID_REQUEST,
                            "Unknown camera action: " + action));
            }
        } catch (CameraException error) {
            return failure(request, error.toError());
        } catch (Throwable error) {
            return failure(request, new CameraError(CameraErrorCodes.INVALID_REQUEST,
                    "Camera request failed: " + error.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    @NonNull
    private NativeApiResponse getCapabilities(@NonNull NativeApiRequest request) {
        CameraCapabilities capabilities = service.getCapabilities();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("capabilities", capabilities.toMap());
        data.put("supported", capabilities.isSupported());
        data.put("cameraPermission", service.hasCameraPermission());
        data.put("audioPermission", service.hasAudioPermission());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse requestPermission(@NonNull NativeApiRequest request) {
        boolean camera = service.hasCameraPermission();
        boolean audio = service.hasAudioPermission();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cameraPermission", camera);
        data.put("audioPermission", audio);
        data.put("granted", camera);
        // The bridge cannot show a system dialog; the host app owns the runtime prompt.
        data.put("needsHostPrompt", !camera);
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse open(@NonNull NativeApiRequest request) {
        if (!CameraProjectContext.hasActiveProject()) {
            return failure(request, new CameraError(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active"));
        }
        CameraConfiguration configuration =
                CameraConfiguration.fromMap(parameters(request));
        CameraResult<CameraCapabilities> result = service.open(configuration);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("capabilities", service.getCapabilities().toMap());
        data.put("configuration", service.getConfiguration().toMap());
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse close(@NonNull NativeApiRequest request) {
        service.close();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse switchCamera(@NonNull NativeApiRequest request) {
        CameraResult<Void> result = service.switchCamera();
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configuration", service.getConfiguration().toMap());
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse capture(@NonNull NativeApiRequest request) {
        PhotoCaptureRequest captureRequest = PhotoCaptureRequest.fromMap(parameters(request));
        CameraResult<CameraMediaItem> result = service.capture(captureRequest);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("media", result.getValue() != null ? result.getValue().toMap() : null);
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse startRecording(@NonNull NativeApiRequest request) {
        VideoRecordingRequest recordingRequest =
                VideoRecordingRequest.fromMap(parameters(request));
        CameraResult<CameraMediaItem> result = service.startRecording(recordingRequest);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("media", result.getValue() != null ? result.getValue().toMap() : null);
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse stopRecording(@NonNull NativeApiRequest request) {
        CameraResult<CameraMediaItem> result = service.stopRecording();
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("media", result.getValue() != null ? result.getValue().toMap() : null);
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse pauseRecording(@NonNull NativeApiRequest request) {
        CameraResult<Void> result = service.pauseRecording();
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse resumeRecording(@NonNull NativeApiRequest request) {
        CameraResult<Void> result = service.resumeRecording();
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", service.getState().name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse setZoom(@NonNull NativeApiRequest request) {
        float zoom = floatValue(parameters(request).get("zoom"), 1f);
        CameraResult<Void> result = service.setZoom(zoom);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("zoom", zoom);
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse setFlash(@NonNull NativeApiRequest request) {
        CameraFlashMode mode = CameraFlashMode.fromName(
                stringValue(parameters(request).get("mode"), "OFF"));
        CameraResult<Void> result = service.setFlash(mode);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flash", mode.name());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse setFocus(@NonNull NativeApiRequest request) {
        Map<String, Object> params = parameters(request);
        float x = floatValue(params.get("x"), 0.5f);
        float y = floatValue(params.get("y"), 0.5f);
        CameraResult<Void> result = service.setFocus(x, y);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("x", x);
        data.put("y", y);
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse setExposure(@NonNull NativeApiRequest request) {
        float ev = floatValue(parameters(request).get("ev"), 0f);
        CameraResult<Void> result = service.setExposure(ev);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ev", ev);
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse setResolution(@NonNull NativeApiRequest request) {
        Map<String, Object> params = parameters(request);
        int width = intValue(params.get("width"), 0);
        int height = intValue(params.get("height"), 0);
        CameraResult<Void> result = service.setResolution(width, height);
        if (!result.isSuccess()) {
            return failure(request, result.getError());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("width", width);
        data.put("height", height);
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse getStatus(@NonNull NativeApiRequest request) {
        return success(request, service.getStatus());
    }

    @NonNull
    private NativeApiResponse getMedia(@NonNull NativeApiRequest request) {
        if (!CameraProjectContext.hasActiveProject()) {
            return failure(request, new CameraError(CameraErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is active"));
        }
        Map<String, Object> params = parameters(request);
        String type = stringValue(params.get("type"), "");
        int limit = intValue(params.get("limit"), 50);
        List<CameraMediaItem> items = service.getMedia(type, limit);
        List<Map<String, Object>> encoded = new ArrayList<>();
        for (CameraMediaItem item : items) {
            encoded.add(item.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", encoded);
        data.put("count", encoded.size());
        data.put("freeSpaceBytes", service.getFreeSpaceBytes());
        return success(request, data);
    }

    @NonNull
    private NativeApiResponse deleteMedia(@NonNull NativeApiRequest request) throws CameraException {
        String id = stringValue(parameters(request).get("id"), "");
        if (id.isEmpty()) {
            return failure(request, new CameraError(CameraErrorCodes.INVALID_REQUEST,
                    "id is required"));
        }
        boolean deleted = service.deleteMedia(id);
        if (!deleted) {
            return failure(request, new CameraError(CameraErrorCodes.MEDIA_NOT_FOUND,
                    "Media item not found for this project"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        data.put("id", id);
        return success(request, data);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @NonNull
    private static Map<String, Object> parameters(@NonNull NativeApiRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        Object raw = request.getParameter("parameters");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                if (entry.getKey() != null) {
                    params.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return params;
    }

    @NonNull
    private static NativeApiResponse success(@NonNull NativeApiRequest request,
                                             @NonNull Map<String, Object> data) {
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private static NativeApiResponse failure(@NonNull NativeApiRequest request,
                                             @Nullable CameraError error) {
        CameraError resolved = error != null ? error
                : new CameraError(CameraErrorCodes.INVALID_REQUEST, "Camera request failed");
        return NativeApiResponse.error(request.getRequestId(), statusFor(resolved.getCode()),
                new NativeApiError(resolved.getCode(), resolved.getMessage()));
    }

    private static int statusFor(@NonNull String code) {
        switch (code) {
            case CameraErrorCodes.PERMISSION_DENIED:
            case CameraErrorCodes.MIC_PERMISSION_DENIED:
            case CameraErrorCodes.FORBIDDEN:
            case CameraErrorCodes.NO_ACTIVE_PROJECT:
                return 403;
            case CameraErrorCodes.MEDIA_NOT_FOUND:
                return 404;
            case CameraErrorCodes.BUSY:
            case CameraErrorCodes.ALREADY_RECORDING:
            case CameraErrorCodes.STORAGE_FULL:
                return 409;
            case CameraErrorCodes.ENGINE_UNAVAILABLE:
            case CameraErrorCodes.DISABLED:
            case CameraErrorCodes.UNSUPPORTED:
                return 503;
            default:
                return 400;
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String && !((String) value).isEmpty() ? (String) value : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static float floatValue(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }
}
