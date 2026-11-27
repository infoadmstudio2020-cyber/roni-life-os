package com.roni.life.os.app.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.life.os.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Grid adapter for the camera gallery. */
public final class CameraMediaAdapter extends RecyclerView.Adapter<CameraMediaAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull CameraMediaItem item);
    }

    private final List<CameraMediaItem> items = new ArrayList<>();
    private final Listener listener;

    public CameraMediaAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<CameraMediaItem> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_camera_media, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ImageView thumb;
        private final TextView badge;

        Holder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.media_thumb);
            badge = itemView.findViewById(R.id.media_badge);
        }

        void bind(@NonNull final CameraMediaItem item) {
            badge.setText(item.isVideo()
                    ? "▶ " + (item.getDurationMs() / 1000) + "s" : "PHOTO");
            Bitmap bitmap = null;
            if (!item.getThumbnailPath().isEmpty()) {
                File file = CameraMediaFiles.resolveThumbnail(item);
                if (file != null && file.exists()) {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            }
            if (bitmap == null) {
                File file = CameraMediaFiles.resolve(item);
                if (file != null && file.exists() && !item.isVideo()) {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            }
            thumb.setImageBitmap(bitmap);
            itemView.setOnClickListener(view -> listener.onOpen(item));
        }
    }
}
