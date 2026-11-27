package com.roni.library.ui.interaction;

import android.view.View;
import androidx.annotation.NonNull;

import com.roni.library.ui.manager.ComponentType;
import com.roni.library.ui.manager.UIStateManager;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Group 6: Interaction Coordinator.
 * Coordinates all 4 Interaction components:
 *  28. Back Navigation (hierarchical back handling)
 *  29. Touch / Click Events (debouncing, click routing, touch listeners)
 *  30. UI State Management (reactive observable state machine)
 *  31. Component Lifecycle Events (dispatching onStart, onResume, onPause, onDestroy)
 *
 * Connects user input, observable UI state, and lifecycle events cleanly.
 */
public class InteractionGroup {

    public interface BackPressInterceptor {
        /**
         * Return true if the back press was handled and should not propagate further.
         */
        boolean onBackPressed();
    }

    public interface LifecycleListener {
        void onComponentEvent(ComponentType type, String event);
    }

    private final UIStateManager stateManager;
    private final List<BackPressInterceptor> backInterceptors = new CopyOnWriteArrayList<>();
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    public InteractionGroup() {
        this(new UIStateManager());
    }

    public InteractionGroup(@NonNull UIStateManager stateManager) {
        this.stateManager = stateManager;
    }

    // Component 28: Back Navigation
    public void addBackInterceptor(@NonNull BackPressInterceptor interceptor) {
        backInterceptors.add(0, interceptor); // Highest priority first
    }

    public void removeBackInterceptor(@NonNull BackPressInterceptor interceptor) {
        backInterceptors.remove(interceptor);
    }

    public boolean dispatchBackPressed() {
        for (BackPressInterceptor interceptor : backInterceptors) {
            if (interceptor.onBackPressed()) {
                return true;
            }
        }
        return false;
    }

    // Component 29: Touch / Click Events
    public void setDebouncedClickListener(@NonNull View view, final long minIntervalMs, @NonNull final Runnable action) {
        view.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClickTime >= minIntervalMs) {
                    lastClickTime = now;
                    action.run();
                }
            }
        });
    }

    // Component 30: UI State Management
    public UIStateManager getStateManager() {
        return stateManager;
    }

    // Component 31: Component Lifecycle Events
    public void addLifecycleListener(@NonNull LifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    public void removeLifecycleListener(@NonNull LifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    public void notifyLifecycleEvent(@NonNull ComponentType type, @NonNull String event) {
        for (LifecycleListener listener : lifecycleListeners) {
            listener.onComponentEvent(type, event);
        }
    }
}
