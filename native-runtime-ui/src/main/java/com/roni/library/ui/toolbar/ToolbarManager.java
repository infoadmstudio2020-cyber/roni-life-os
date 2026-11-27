package com.roni.library.ui.toolbar;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import com.roni.library.contracts.ui.ToolbarConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Action Bar / Toolbar titles, navigation icons, action items,
 * and live Android Toolbar bindings.
 */
public class ToolbarManager {

    public interface ToolbarListener {
        void onTitleChanged(String title);
        void onSubtitleChanged(String subtitle);
        void onVisibilityChanged(boolean visible);
        void onActionClicked(String actionId);
    }

    public static class ActionButton {
        private final String id;
        private final String label;
        private final Runnable action;
        private final int iconResId;

        public ActionButton(String id, String label, Runnable action) {
            this(id, label, 0, action);
        }

        public ActionButton(String id, String label, int iconResId, Runnable action) {
            this.id = id != null ? id : "";
            this.label = label != null ? label : "";
            this.iconResId = iconResId;
            this.action = action;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public int getIconResId() { return iconResId; }

        public void execute() {
            if (action != null) {
                try {
                    action.run();
                } catch (Throwable ignored) {}
            }
        }
    }

    private volatile String title = "";
    private volatile String subtitle = "";
    private volatile boolean navigationIconVisible = true;
    private volatile boolean visible = true;
    private final List<ActionButton> actions = new CopyOnWriteArrayList<>();
    private final List<ToolbarListener> listeners = new CopyOnWriteArrayList<>();

    private Toolbar boundToolbar;
    private Activity boundActivity;
    private ToolbarConfig currentConfig;

    public ToolbarManager() {}

    public synchronized void bindToolbar(@NonNull Toolbar toolbar, @NonNull Activity activity, @Nullable Runnable onNavClick) {
        this.boundToolbar = toolbar;
        this.boundActivity = activity;

        applyToView();

        if (navigationIconVisible && onNavClick != null) {
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onNavClick.run();
                }
            });
        }
    }

    public synchronized void applyToView() {
        if (boundToolbar == null || boundActivity == null) return;
        boundActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (boundToolbar == null) return;
                boundToolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
                boundToolbar.setTitle(title);
                boundToolbar.setSubtitle(subtitle);

                Menu menu = boundToolbar.getMenu();
                menu.clear();
                for (int i = 0; i < actions.size(); i++) {
                    final ActionButton action = actions.get(i);
                    MenuItem item = menu.add(0, i + 1, i, action.getLabel());
                    if (action.getIconResId() != 0) {
                        item.setIcon(action.getIconResId());
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    } else {
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                    }
                    item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem mi) {
                            action.execute();
                            for (ToolbarListener l : listeners) {
                                l.onActionClicked(action.getId());
                            }
                            return true;
                        }
                    });
                }
            }
        });
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
        for (ToolbarListener l : listeners) {
            l.onTitleChanged(this.title);
        }
        applyToView();
    }

    public String getTitle() { return title; }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle != null ? subtitle : "";
        for (ToolbarListener l : listeners) {
            l.onSubtitleChanged(this.subtitle);
        }
        applyToView();
    }

    public String getSubtitle() { return subtitle; }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        for (ToolbarListener l : listeners) {
            l.onVisibilityChanged(visible);
        }
        applyToView();
    }

    public boolean isVisible() { return visible; }

    public void setNavigationIconVisible(boolean visible) {
        this.navigationIconVisible = visible;
        if (boundToolbar != null && boundActivity != null) {
            boundActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (boundToolbar != null && !navigationIconVisible) {
                        boundToolbar.setNavigationIcon(null);
                    }
                }
            });
        }
    }

    public boolean isNavigationIconVisible() { return navigationIconVisible; }

    public void addAction(String id, String label, Runnable action) {
        addAction(id, label, 0, action);
    }

    public void addAction(String id, String label, int iconResId, Runnable action) {
        actions.add(new ActionButton(id, label, iconResId, action));
        applyToView();
    }

    public void removeAction(String id) {
        if (id == null) return;
        for (ActionButton btn : actions) {
            if (id.equals(btn.getId())) {
                actions.remove(btn);
                break;
            }
        }
        applyToView();
    }

    public int getActionCount() { return actions.size(); }

    public List<ActionButton> getActions() { return new ArrayList<>(actions); }

    public void clearActions() {
        actions.clear();
        applyToView();
    }

    public void setConfig(ToolbarConfig config) {
        this.currentConfig = config;
        if (config != null) {
            setTitle(config.getTitle());
            setSubtitle(config.getSubtitle());
        }
    }

    public ToolbarConfig getConfig() { return currentConfig; }

    public void addListener(ToolbarListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(ToolbarListener listener) {
        listeners.remove(listener);
    }

    public Toolbar getBoundToolbar() { return boundToolbar; }

    public synchronized void reset() {
        boundToolbar = null;
        boundActivity = null;
        actions.clear();
        listeners.clear();
        title = "";
        subtitle = "";
        visible = true;
        navigationIconVisible = true;
    }
}
