package com.roni.library.ui.manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized, observable UI state manager tracking component states,
 * active tabs, drawer status, loading status, and active overlays.
 */
public class UIStateManager {

    public interface UIStateListener {
        void onComponentVisibilityChanged(ComponentType type, boolean visible);
        void onTabChanged(String tabId);
        void onDrawerStateChanged(boolean isOpen);
        void onLoadingStateChanged(boolean isLoading, String message);
        void onThemeChanged(Map<String, Object> themeTokens);
    }

    private final Map<ComponentType, Boolean> componentVisibility = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UIStateListener> listeners = new CopyOnWriteArrayList<>();

    private volatile String activeTabId = "";
    private volatile boolean drawerOpen = false;
    private volatile boolean loading = false;
    private volatile String loadingMessage = "";

    public UIStateManager() {
        for (ComponentType type : ComponentType.values()) {
            componentVisibility.put(type, true);
        }
    }

    public void addListener(UIStateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(UIStateListener listener) {
        listeners.remove(listener);
    }

    public void setComponentVisibility(ComponentType type, boolean visible) {
        if (type == null) return;
        componentVisibility.put(type, visible);
        for (UIStateListener l : listeners) {
            l.onComponentVisibilityChanged(type, visible);
        }
    }

    public boolean isComponentVisible(ComponentType type) {
        if (type == null) return false;
        Boolean v = componentVisibility.get(type);
        return v != null && v;
    }

    public void setActiveTabId(String tabId) {
        this.activeTabId = tabId != null ? tabId : "";
        for (UIStateListener l : listeners) {
            l.onTabChanged(this.activeTabId);
        }
    }

    public String getActiveTabId() {
        return activeTabId;
    }

    public void setDrawerOpen(boolean open) {
        this.drawerOpen = open;
        for (UIStateListener l : listeners) {
            l.onDrawerStateChanged(open);
        }
    }

    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    public void setLoading(boolean loading, String message) {
        this.loading = loading;
        this.loadingMessage = message != null ? message : "";
        for (UIStateListener l : listeners) {
            l.onLoadingStateChanged(loading, this.loadingMessage);
        }
    }

    public boolean isLoading() {
        return loading;
    }

    public String getLoadingMessage() {
        return loadingMessage;
    }

    public void notifyThemeChanged(Map<String, Object> themeTokens) {
        for (UIStateListener l : listeners) {
            l.onThemeChanged(themeTokens);
        }
    }

    public void reset() {
        listeners.clear();
        activeTabId = "";
        drawerOpen = false;
        loading = false;
        loadingMessage = "";
        for (ComponentType type : ComponentType.values()) {
            componentVisibility.put(type, true);
        }
    }
}
