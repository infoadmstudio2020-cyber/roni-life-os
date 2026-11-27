package com.roni.library.ui.overlay;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import java.util.List;

/**
 * Manages contextual native PopupMenu presentation on anchor views.
 *
 * <p>Safe to call from any thread. The bridge dispatches handlers on a background pool, so the
 * PopupMenu is always created and shown on the main thread.</p>
 */
public class PopupMenuManager {

    public static class PopupMenuItem {
        private final int id;
        private final String title;
        private final Runnable action;

        public PopupMenuItem(int id, String title, Runnable action) {
            this.id = id;
            this.title = title != null ? title : "";
            this.action = action;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public Runnable getAction() { return action; }
    }

    public PopupMenuManager() {}

    public void showPopupMenu(@NonNull Context context, @NonNull View anchor, @NonNull List<PopupMenuItem> items) {
        Runnable task = () -> {
            PopupMenu popupMenu = new PopupMenu(context, anchor);
            for (PopupMenuItem item : items) {
                popupMenu.getMenu().add(0, item.getId(), 0, item.getTitle());
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                for (PopupMenuItem item : items) {
                    if (item.getId() == menuItem.getItemId()) {
                        if (item.getAction() != null) {
                            item.getAction().run();
                        }
                        return true;
                    }
                }
                return false;
            });
            popupMenu.show();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            anchor.post(task);
        }
    }
}
