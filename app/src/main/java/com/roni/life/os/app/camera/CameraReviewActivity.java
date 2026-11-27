package com.roni.life.os.app.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.camera.CameraException;
import com.roni.library.platform.camera.CameraService;
import com.roni.life.os.app.R;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/** Capture review: preview the result, keep it or delete it. */
public final class CameraReviewActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_camera_media_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_review);

        ImageView image = findViewById(R.id.review_image);
        TextView meta = findViewById(R.id.review_meta);
        String id = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        CameraService service = PlatformEngine.getInstance().getCameraService();
        CameraMediaItem item = service != null && id != null ? service.getMediaItem(id) : null;

        if (item == null) {
            meta.setText("This media item is no longer available");
            findViewById(R.id.review_delete).setEnabled(false);
        } else {
            File file = item.isVideo()
                    ? CameraMediaFiles.resolveThumbnail(item) : CameraMediaFiles.resolve(item);
            if (file != null && file.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                image.setImageBitmap(bitmap);
            }
            String size = String.format(java.util.Locale.US, "%.1f MB",
                    item.getSizeBytes() / (1024.0 * 1024.0));
            String duration = item.isVideo()
                    ? " · " + (item.getDurationMs() / 1000) + "s" : "";
            meta.setText(item.getType() + " · " + size + duration + " · "
                    + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(item.getCreatedAt())));
        }

        findViewById(R.id.review_close).setOnClickListener(view -> finish());
        findViewById(R.id.review_delete).setOnClickListener(view -> {
            if (service == null || id == null) {
                finish();
                return;
            }
            try {
                service.deleteMedia(id);
            } catch (CameraException ignored) {
            }
            finish();
        });
        image.setOnClickListener(view -> {
            if (item != null && item.isVideo()) {
                meta.setVisibility(meta.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            }
        });
    }
}
