package com.roni.life.os.app.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.contracts.camera.CameraError;
import com.roni.library.contracts.camera.CameraFlashMode;
import com.roni.library.contracts.camera.CameraLens;
import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.contracts.camera.CameraResult;
import com.roni.library.contracts.camera.CameraState;
import com.roni.library.contracts.camera.PhotoCaptureRequest;
import com.roni.library.contracts.camera.VideoRecordingRequest;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.camera.CameraEventListener;
import com.roni.library.platform.camera.CameraProjectContext;
import com.roni.library.platform.camera.CameraService;
import com.roni.life.os.app.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Live camera workspace: preview, capture, recording and professional controls. */
public final class CameraActivity extends AppCompatActivity
        implements CameraEventListener, TextureView.SurfaceTextureListener {

    public static final String EXTRA_PROJECT_ID = "extra_camera_project_id";

    private static final int REQUEST_CAMERA = 4201;
    private static final int REQUEST_AUDIO = 4202;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    private CameraService service;
    private TextureView preview;
    private CameraGridOverlay grid;
    private TextView statusText;
    private TextView zoomLabel;
    private TextView recordingTimer;
    private SeekBar zoomBar;
    private SeekBar isoBar;
    private SeekBar shutterBar;
    private SeekBar focusBar;
    private SeekBar exposureBar;
    private View proPanel;
    private Button shutterButton;
    private Button flashButton;
    private Button modePhoto;
    private Button modeVideo;
    private Button proButton;
    private Button timerButton;
    private Button whiteBalanceButton;

    private boolean videoMode;
    private boolean recording;
    private boolean opened;
    private int timerSeconds;
    private float zoom = 1f;
    private CameraFlashMode flashMode = CameraFlashMode.OFF;
    private List<String> whiteBalanceModes;
    private int whiteBalanceIndex;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!recording || service == null) {
                return;
            }
            long seconds = service.getStatus().get("recordingDurationMs") instanceof Number
                    ? ((Number) service.getStatus().get("recordingDurationMs")).longValue() / 1000L
                    : 0L;
            recordingTimer.setText(String.format(java.util.Locale.US, "● %02d:%02d",
                    seconds / 60, seconds % 60));
            main.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId != null && !projectId.isEmpty()) {
            CameraProjectContext.setActiveProject(projectId);
        }

        preview = findViewById(R.id.preview);
        grid = findViewById(R.id.grid_overlay);
        statusText = findViewById(R.id.status_text);
        zoomLabel = findViewById(R.id.zoom_label);
        recordingTimer = findViewById(R.id.recording_timer);
        zoomBar = findViewById(R.id.zoom_bar);
        isoBar = findViewById(R.id.iso_bar);
        shutterBar = findViewById(R.id.shutter_bar);
        focusBar = findViewById(R.id.focus_bar);
        exposureBar = findViewById(R.id.exposure_bar);
        proPanel = findViewById(R.id.pro_panel);
        shutterButton = findViewById(R.id.shutter_button);
        flashButton = findViewById(R.id.flash_button);
        modePhoto = findViewById(R.id.mode_photo);
        modeVideo = findViewById(R.id.mode_video);
        proButton = findViewById(R.id.pro_button);
        timerButton = findViewById(R.id.timer_button);
        whiteBalanceButton = findViewById(R.id.white_balance_button);

        service = PlatformEngine.getInstance().getCameraService();
        if (service == null) {
            showStatus("Camera engine is unavailable");
            return;
        }
        service.setListener(this);
        preview.setSurfaceTextureListener(this);

        findViewById(R.id.close_button).setOnClickListener(view -> finish());
        findViewById(R.id.gallery_button).setOnClickListener(view ->
                startActivity(new Intent(this, CameraGalleryActivity.class)));
        findViewById(R.id.settings_button).setOnClickListener(view ->
                startActivity(new Intent(this, CameraSettingsActivity.class)));
        findViewById(R.id.switch_button).setOnClickListener(view -> switchCamera());
        flashButton.setOnClickListener(view -> cycleFlash());
        shutterButton.setOnClickListener(view -> onShutter());
        modePhoto.setOnClickListener(view -> setVideoMode(false));
        modeVideo.setOnClickListener(view -> setVideoMode(true));
        proButton.setOnClickListener(view -> togglePro());
        timerButton.setOnClickListener(view -> cycleTimer());
        whiteBalanceButton.setOnClickListener(view -> cycleWhiteBalance());

        zoomBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || service == null) {
                    return;
                }
                float max = Math.max(1f, service.getCapabilities().getMaxZoom());
                zoom = 1f + (max - 1f) * progress / 100f;
                zoomLabel.setText(String.format(java.util.Locale.US, "%.1fx", zoom));
                service.setZoom(zoom);
            }
        });
        isoBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    applyProConfiguration();
                }
            }
        });
        shutterBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    applyProConfiguration();
                }
            }
        });
        focusBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    applyProConfiguration();
                }
            }
        });
        exposureBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    applyProConfiguration();
                }
            }
        });

        configureControls();
    }

    private void configureControls() {
        if (service == null) {
            return;
        }
        CameraCapabilities capabilities = service.getCapabilities();
        if (!capabilities.isSupported()) {
            showStatus("This device does not expose a usable camera");
            shutterButton.setEnabled(false);
            return;
        }
        zoomBar.setEnabled(capabilities.isZoomSupported());
        flashButton.setEnabled(capabilities.supportsFlash(CameraFlashMode.AUTO));
        isoBar.setEnabled(capabilities.isManualIsoSupported());
        shutterBar.setEnabled(capabilities.isManualShutterSupported());
        focusBar.setEnabled(capabilities.isManualFocusSupported());
        exposureBar.setEnabled(capabilities.isExposureCompensationSupported());
        whiteBalanceButton.setEnabled(capabilities.isWhiteBalanceSupported());
        whiteBalanceModes = capabilities.getWhiteBalanceModes();
        whiteBalanceIndex = 0;
        if (!whiteBalanceModes.isEmpty()) {
            whiteBalanceButton.setText("WB " + whiteBalanceModes.get(0));
        }
        grid.setGridEnabled(true);
        grid.setLevelEnabled(false);
    }

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (service != null && preview.isAvailable() && !opened) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (service != null) {
            if (recording) {
                stopRecording();
            }
            service.close();
            opened = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            service.setListener(null);
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void openCamera() {
        if (service == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        service.setPreviewTarget(preview);
        CameraConfiguration configuration = service.getConfiguration().toBuilder()
                .lens(CameraLens.BACK)
                .build();
        CameraResult<CameraCapabilities> result = service.open(configuration);
        if (!result.isSuccess()) {
            CameraError error = result.getError();
            showStatus(error != null ? error.getMessage() : "Unable to open the camera");
            return;
        }
        opened = true;
        hideStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_CAMERA) {
            if (granted) {
                openCamera();
            } else {
                showStatus("Camera permission is required to use the camera");
            }
        } else if (requestCode == REQUEST_AUDIO) {
            if (granted) {
                startRecording(true);
            } else {
                showStatus("Microphone permission is required to record audio");
            }
        }
    }

    // ------------------------------------------------------------------
    // capture
    // ------------------------------------------------------------------

    private void onShutter() {
        if (service == null) {
            return;
        }
        if (videoMode) {
            if (recording) {
                stopRecording();
            } else {
                startRecordingWithPermission();
            }
            return;
        }
        PhotoCaptureRequest request = new PhotoCaptureRequest.Builder()
                .key("photo")
                .selfTimerMs(timerSeconds * 1000L)
                .build();
        shutterButton.setEnabled(false);
        worker.execute(() -> {
            CameraResult<CameraMediaItem> result = service.capture(request);
            main.post(() -> {
                shutterButton.setEnabled(true);
                if (result.isSuccess() && result.getValue() != null) {
                    openReview(result.getValue());
                } else {
                    CameraError error = result.getError();
                    showStatus(error != null ? error.getMessage() : "Capture failed");
                }
            });
        });
    }

    private void startRecordingWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        startRecording(true);
    }

    private void startRecording(boolean withAudio) {
        if (service == null) {
            return;
        }
        VideoRecordingRequest request = new VideoRecordingRequest.Builder()
                .key("video")
                .withAudio(withAudio)
                .build();
        worker.execute(() -> {
            CameraResult<CameraMediaItem> result = service.startRecording(request);
            main.post(() -> {
                if (result.isSuccess()) {
                    recording = true;
                    recordingTimer.setVisibility(View.VISIBLE);
                    shutterButton.setText("■");
                    main.post(timerTick);
                } else {
                    CameraError error = result.getError();
                    showStatus(error != null ? error.getMessage() : "Unable to record");
                }
            });
        });
    }

    private void stopRecording() {
        if (service == null) {
            return;
        }
        worker.execute(() -> {
            CameraResult<CameraMediaItem> result = service.stopRecording();
            main.post(() -> {
                recording = false;
                recordingTimer.setVisibility(View.GONE);
                shutterButton.setText("◉");
                main.removeCallbacks(timerTick);
                if (result.isSuccess() && result.getValue() != null) {
                    openReview(result.getValue());
                } else {
                    CameraError error = result.getError();
                    showStatus(error != null ? error.getMessage() : "Recording failed");
                }
            });
        });
    }

    private void openReview(@NonNull CameraMediaItem item) {
        Intent intent = new Intent(this, CameraReviewActivity.class);
        intent.putExtra(CameraReviewActivity.EXTRA_MEDIA_ID, item.getId());
        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // controls
    // ------------------------------------------------------------------

    private void switchCamera() {
        if (service == null) {
            return;
        }
        CameraResult<Void> result = service.switchCamera();
        if (!result.isSuccess()) {
            CameraError error = result.getError();
            showStatus(error != null ? error.getMessage() : "Unable to switch camera");
        }
    }

    private void cycleFlash() {
        if (service == null) {
            return;
        }
        CameraCapabilities capabilities = service.getCapabilities();
        CameraFlashMode[] order = {CameraFlashMode.OFF, CameraFlashMode.AUTO,
                CameraFlashMode.ON, CameraFlashMode.TORCH};
        int current = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == flashMode) {
                current = i;
            }
        }
        for (int step = 1; step <= order.length; step++) {
            CameraFlashMode next = order[(current + step) % order.length];
            if (capabilities.supportsFlash(next)) {
                flashMode = next;
                service.setFlash(next);
                flashButton.setText(flashLabel(next));
                return;
            }
        }
    }

    private static String flashLabel(CameraFlashMode mode) {
        switch (mode) {
            case ON:
                return "⚡ON";
            case AUTO:
                return "⚡A";
            case TORCH:
                return "🔦";
            case OFF:
            default:
                return "⚡";
        }
    }

    private void setVideoMode(boolean value) {
        videoMode = value;
        modePhoto.setTextColor(value ? 0xFFAAAAAA : 0xFFFFFFFF);
        modeVideo.setTextColor(value ? 0xFFFFFFFF : 0xFFAAAAAA);
        shutterButton.setText(value ? "●" : "◉");
    }

    private void togglePro() {
        boolean visible = proPanel.getVisibility() != View.VISIBLE;
        proPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        proButton.setTextColor(visible ? 0xFFFFC107 : 0xFFAAAAAA);
        if (visible && service != null) {
            CameraConfiguration configuration = service.getConfiguration();
            isoBar.setProgress(0);
            shutterBar.setProgress(0);
            focusBar.setProgress(0);
            exposureBar.setProgress(50);
            whiteBalanceButton.setText("WB " + configuration.getWhiteBalanceMode());
        }
    }

    private void cycleTimer() {
        if (timerSeconds == 0) {
            timerSeconds = 3;
        } else if (timerSeconds == 3) {
            timerSeconds = 10;
        } else {
            timerSeconds = 0;
        }
        timerButton.setText(timerSeconds == 0 ? "TIMER OFF" : "TIMER " + timerSeconds + "s");
    }

    private void cycleWhiteBalance() {
        if (service == null || whiteBalanceModes == null || whiteBalanceModes.isEmpty()) {
            return;
        }
        whiteBalanceIndex = (whiteBalanceIndex + 1) % whiteBalanceModes.size();
        String mode = whiteBalanceModes.get(whiteBalanceIndex);
        whiteBalanceButton.setText("WB " + mode);
        CameraConfiguration updated = service.getConfiguration().toBuilder()
                .whiteBalanceMode(mode).build();
        service.applyConfiguration(updated);
    }

    private void applyProConfiguration() {
        if (service == null) {
            return;
        }
        CameraCapabilities capabilities = service.getCapabilities();
        int iso = 0;
        if (isoBar.getProgress() > 0 && capabilities.isManualIsoSupported()) {
            iso = capabilities.getMinIso() + (capabilities.getMaxIso() - capabilities.getMinIso())
                    * isoBar.getProgress() / 100;
        }
        long shutter = 0L;
        if (shutterBar.getProgress() > 0 && capabilities.isManualShutterSupported()) {
            shutter = capabilities.getMinShutterNs()
                    + (long) ((capabilities.getMaxShutterNs() - capabilities.getMinShutterNs())
                    * (shutterBar.getProgress() / 100.0));
        }
        float focus = 0f;
        if (focusBar.getProgress() > 0 && capabilities.isManualFocusSupported()) {
            focus = capabilities.getMinFocusDistance() * focusBar.getProgress() / 100f;
        }
        float exposure = 0f;
        if (capabilities.isExposureCompensationSupported()) {
            float min = capabilities.getMinExposureCompensation();
            float max = capabilities.getMaxExposureCompensation();
            exposure = min + (max - min) * exposureBar.getProgress() / 100f;
        }
        CameraConfiguration updated = service.getConfiguration().toBuilder()
                .proMode(true)
                .iso(iso)
                .shutterNs(shutter)
                .focusDistance(focus)
                .exposureCompensation(exposure)
                .build();
        service.applyConfiguration(updated);
    }

    // ------------------------------------------------------------------
    // listener
    // ------------------------------------------------------------------

    @Override
    public void onStateChanged(@NonNull CameraState state) {
        main.post(() -> {
            if (state == CameraState.ERROR) {
                showStatus("The camera session was interrupted");
            }
        });
    }

    @Override
    public void onError(@NonNull CameraError error) {
        main.post(() -> showStatus(error.getMessage()));
    }

    @Override
    public void onRecordingStopped(@NonNull CameraMediaItem item) {
        main.post(() -> {
            recording = false;
            recordingTimer.setVisibility(View.GONE);
            shutterButton.setText("◉");
            main.removeCallbacks(timerTick);
        });
    }

    // ------------------------------------------------------------------
    // surface
    // ------------------------------------------------------------------

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (!opened) {
            openCamera();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    private void showStatus(@NonNull String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusText.setVisibility(View.GONE);
    }

    /** Adapter that keeps the listener boilerplate out of the activity. */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
