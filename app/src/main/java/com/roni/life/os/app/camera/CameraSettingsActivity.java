package com.roni.life.os.app.camera;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.camera.CameraService;
import com.roni.library.storage.StorageEngine;
import com.roni.library.storage.preferences.EncryptedPreferences;
import com.roni.life.os.app.R;

import java.util.ArrayList;
import java.util.List;

/** Camera preferences: grid, level, HDR, audio, quality and video resolution. */
public final class CameraSettingsActivity extends AppCompatActivity {

    private static final String PREFS = "camera_settings";

    private EncryptedPreferences preferences;
    private CameraService service;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        try {
            preferences = StorageEngine.getPreferences(PREFS);
        } catch (Throwable ignored) {
            preferences = null;
        }
        service = PlatformEngine.getInstance().getCameraService();
        CameraCapabilities capabilities = service != null
                ? service.getCapabilities() : CameraCapabilities.unsupported();

        TextView summary = findViewById(R.id.capability_summary);
        summary.setText(capabilities.isSupported()
                ? "Lenses: " + capabilities.getLenses()
                + " · Max photo: " + capabilities.getMaxPhotoWidth() + "x"
                + capabilities.getMaxPhotoHeight()
                + " · Manual ISO: " + capabilities.isManualIsoSupported()
                + " · Manual shutter: " + capabilities.isManualShutterSupported()
                : "No usable camera detected on this device");

        Switch gridSwitch = findViewById(R.id.grid_switch);
        Switch levelSwitch = findViewById(R.id.level_switch);
        Switch hdrSwitch = findViewById(R.id.hdr_switch);
        Switch audioSwitch = findViewById(R.id.audio_switch);
        SeekBar qualityBar = findViewById(R.id.quality_bar);
        TextView qualityLabel = findViewById(R.id.quality_label);
        Spinner resolutionSpinner = findViewById(R.id.resolution_spinner);
        TextView storage = findViewById(R.id.storage_summary);

        gridSwitch.setChecked(bool("grid", true));
        levelSwitch.setChecked(bool("level", false));
        hdrSwitch.setChecked(bool("hdr", false));
        hdrSwitch.setEnabled(capabilities.isHdrSupported());
        audioSwitch.setChecked(bool("audio", true));
        audioSwitch.setEnabled(capabilities.isAudioSupported());
        qualityBar.setProgress(intValue("quality", 95) - 40);
        qualityLabel.setText("JPEG quality: " + intValue("quality", 95));

        List<String> resolutions = new ArrayList<>();
        resolutions.add("Auto");
        resolutions.addAll(capabilities.getVideoResolutions());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, resolutions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(adapter);
        String saved = preferences != null ? preferences.getString("resolution", "Auto") : "Auto";
        int index = resolutions.indexOf(saved);
        resolutionSpinner.setSelection(index >= 0 ? index : 0);

        if (service != null) {
            storage.setText(String.format(java.util.Locale.US,
                    "Free storage: %.1f GB", service.getFreeSpaceBytes() / (1024.0 * 1024 * 1024)));
        }

        gridSwitch.setOnCheckedChangeListener((button, checked) -> put("grid", checked));
        levelSwitch.setOnCheckedChangeListener((button, checked) -> put("level", checked));
        hdrSwitch.setOnCheckedChangeListener((button, checked) -> {
            put("hdr", checked);
            applyConfiguration();
        });
        audioSwitch.setOnCheckedChangeListener((button, checked) -> put("audio", checked));
        qualityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int quality = 40 + progress;
                qualityLabel.setText("JPEG quality: " + quality);
                if (fromUser) {
                    put("quality", quality);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        resolutionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                String value = resolutions.get(position);
                if (preferences != null) {
                    preferences.putString("resolution", value);
                }
                applyConfiguration();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void applyConfiguration() {
        if (service == null) {
            return;
        }
        CameraConfiguration configuration = service.getConfiguration().toBuilder()
                .hdr(bool("hdr", false))
                .jpegQuality(intValue("quality", 95))
                .build();
        service.applyConfiguration(configuration);
    }

    private boolean bool(String key, boolean fallback) {
        return preferences != null ? preferences.getBoolean(key, fallback) : fallback;
    }

    private int intValue(String key, int fallback) {
        return preferences != null ? preferences.getInt(key, fallback) : fallback;
    }

    private void put(String key, boolean value) {
        if (preferences != null) {
            preferences.putBoolean(key, value);
        }
    }

    private void put(String key, int value) {
        if (preferences != null) {
            preferences.putInt(key, value);
        }
    }
}
