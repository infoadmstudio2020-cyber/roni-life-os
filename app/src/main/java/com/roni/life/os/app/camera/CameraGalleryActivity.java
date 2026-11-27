package com.roni.life.os.app.camera;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.camera.CameraService;
import com.roni.life.os.app.R;

import java.util.List;

/** Project scoped media gallery. */
public final class CameraGalleryActivity extends AppCompatActivity
        implements CameraMediaAdapter.Listener {

    private CameraMediaAdapter adapter;
    private TextView emptyText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_gallery);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        emptyText = findViewById(R.id.empty_text);
        RecyclerView grid = findViewById(R.id.media_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new CameraMediaAdapter(this);
        grid.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        CameraService service = PlatformEngine.getInstance().getCameraService();
        List<CameraMediaItem> items = service != null
                ? service.getMedia(null, 100) : java.util.Collections.emptyList();
        adapter.submit(items);
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(@NonNull CameraMediaItem item) {
        Intent intent = new Intent(this, CameraReviewActivity.class);
        intent.putExtra(CameraReviewActivity.EXTRA_MEDIA_ID, item.getId());
        startActivity(intent);
    }
}
