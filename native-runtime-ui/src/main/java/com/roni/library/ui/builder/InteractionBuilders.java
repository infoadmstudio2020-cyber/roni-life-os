package com.roni.library.ui.builder;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Basic to Advanced Builders for Interaction Components (Group 6).
 * Components:
 * 28. Back Navigation
 * 29. Touch / Click Events
 * 30. UI State Management
 * 31. Component Lifecycle Events
 */
public final class InteractionBuilders {

    private InteractionBuilders() {}

    // ==========================================
    // 28. Back Navigation Config & Builder
    // ==========================================
    public static class BackNavigationConfig {
        public interface BackPressInterceptor {
            boolean onBackPressed();
        }

        private final boolean closeDrawerOnBack;
        private final boolean closeLoadingOnBack;
        private final boolean closeBottomSheetOnBack;
        private final boolean closeDialogOnBack;
        private final boolean webViewBackHistoryEnabled;
        private final boolean doubleBackToExit;
        private final String doubleBackExitMessage;
        private final List<BackPressInterceptor> customInterceptors;

        private BackNavigationConfig(Builder b) {
            this.closeDrawerOnBack = b.closeDrawerOnBack;
            this.closeLoadingOnBack = b.closeLoadingOnBack;
            this.closeBottomSheetOnBack = b.closeBottomSheetOnBack;
            this.closeDialogOnBack = b.closeDialogOnBack;
            this.webViewBackHistoryEnabled = b.webViewBackHistoryEnabled;
            this.doubleBackToExit = b.doubleBackToExit;
            this.doubleBackExitMessage = b.doubleBackExitMessage;
            this.customInterceptors = new ArrayList<>(b.customInterceptors);
        }

        public static Builder builder() { return new Builder(); }
        public static BackNavigationConfig basic(boolean doubleBackToExit) {
            return builder().doubleBackToExit(doubleBackToExit).build();
        }

        public boolean isCloseDrawerOnBack() { return closeDrawerOnBack; }
        public boolean isCloseLoadingOnBack() { return closeLoadingOnBack; }
        public boolean isCloseBottomSheetOnBack() { return closeBottomSheetOnBack; }
        public boolean isCloseDialogOnBack() { return closeDialogOnBack; }
        public boolean isWebViewBackHistoryEnabled() { return webViewBackHistoryEnabled; }
        public boolean isDoubleBackToExit() { return doubleBackToExit; }
        public String getDoubleBackExitMessage() { return doubleBackExitMessage; }
        public List<BackPressInterceptor> getCustomInterceptors() { return customInterceptors; }

        public static class Builder {
            private boolean closeDrawerOnBack = true;
            private boolean closeLoadingOnBack = true;
            private boolean closeBottomSheetOnBack = true;
            private boolean closeDialogOnBack = true;
            private boolean webViewBackHistoryEnabled = true;
            private boolean doubleBackToExit = false;
            private String doubleBackExitMessage = "Press back again to exit";
            private final List<BackPressInterceptor> customInterceptors = new ArrayList<>();

            public Builder closeDrawerOnBack(boolean close) { this.closeDrawerOnBack = close; return this; }
            public Builder closeLoadingOnBack(boolean close) { this.closeLoadingOnBack = close; return this; }
            public Builder closeBottomSheetOnBack(boolean close) { this.closeBottomSheetOnBack = close; return this; }
            public Builder closeDialogOnBack(boolean close) { this.closeDialogOnBack = close; return this; }
            public Builder webViewBackHistoryEnabled(boolean enabled) { this.webViewBackHistoryEnabled = enabled; return this; }
            public Builder doubleBackToExit(boolean doubleBack) { this.doubleBackToExit = doubleBack; return this; }
            public Builder doubleBackExitMessage(String msg) { this.doubleBackExitMessage = msg; return this; }
            public Builder addInterceptor(BackPressInterceptor interceptor) {
                this.customInterceptors.add(interceptor);
                return this;
            }
            public BackNavigationConfig build() { return new BackNavigationConfig(this); }
        }
    }

    // ==========================================
    // 29. Touch / Click Events Config & Builder
    // ==========================================
    public static class TouchEventsConfig {
        private final long debounceThresholdMs;
        private final boolean enableHapticFeedback;

        private TouchEventsConfig(Builder b) {
            this.debounceThresholdMs = b.debounceThresholdMs;
            this.enableHapticFeedback = b.enableHapticFeedback;
        }

        public static Builder builder() { return new Builder(); }
        public static TouchEventsConfig basic(long debounceThresholdMs) {
            return builder().debounceThresholdMs(debounceThresholdMs).build();
        }

        public long getDebounceThresholdMs() { return debounceThresholdMs; }
        public boolean isEnableHapticFeedback() { return enableHapticFeedback; }

        public static class Builder {
            private long debounceThresholdMs = 300L;
            private boolean enableHapticFeedback = false;

            public Builder debounceThresholdMs(long ms) { this.debounceThresholdMs = ms; return this; }
            public Builder enableHapticFeedback(boolean haptic) { this.enableHapticFeedback = haptic; return this; }
            public TouchEventsConfig build() { return new TouchEventsConfig(this); }
        }
    }

    // ==========================================
    // 30. UI State Management Config & Builder
    // ==========================================
    public static class UIStateConfig {
        private final String defaultSelectedTab;
        private final boolean persistAcrossOrientationChanges;

        private UIStateConfig(Builder b) {
            this.defaultSelectedTab = b.defaultSelectedTab;
            this.persistAcrossOrientationChanges = b.persistAcrossOrientationChanges;
        }

        public static Builder builder() { return new Builder(); }
        public static UIStateConfig basic(String defaultSelectedTab) {
            return builder().defaultSelectedTab(defaultSelectedTab).build();
        }

        public String getDefaultSelectedTab() { return defaultSelectedTab; }
        public boolean isPersistAcrossOrientationChanges() { return persistAcrossOrientationChanges; }

        public static class Builder {
            private String defaultSelectedTab = "";
            private boolean persistAcrossOrientationChanges = true;

            public Builder defaultSelectedTab(String tabId) { this.defaultSelectedTab = tabId != null ? tabId : ""; return this; }
            public Builder persistAcrossOrientationChanges(boolean persist) { this.persistAcrossOrientationChanges = persist; return this; }
            public UIStateConfig build() { return new UIStateConfig(this); }
        }
    }

    // ==========================================
    // 31. Component Lifecycle Events Config & Builder
    // ==========================================
    public static class LifecycleConfig {
        public interface LifecycleObserver {
            void onComponentCreated();
            void onComponentStarted();
            void onComponentResumed();
            void onComponentPaused();
            void onComponentDestroyed();
        }

        private final boolean trackLifecycle;

        private LifecycleConfig(Builder b) {
            this.trackLifecycle = b.trackLifecycle;
        }

        public static Builder builder() { return new Builder(); }
        public boolean isTrackLifecycle() { return trackLifecycle; }

        public static class Builder {
            private boolean trackLifecycle = true;

            public Builder trackLifecycle(boolean track) { this.trackLifecycle = track; return this; }
            public LifecycleConfig build() { return new LifecycleConfig(this); }
        }
    }

    // ==========================================
    // Complete Interaction Group Config & Builder
    // ==========================================
    public static class InteractionGroupConfig {
        private final BackNavigationConfig backNavigation;
        private final TouchEventsConfig touchEvents;
        private final UIStateConfig uiState;
        private final LifecycleConfig lifecycle;

        private InteractionGroupConfig(Builder b) {
            this.backNavigation = b.backNavigation != null ? b.backNavigation : BackNavigationConfig.builder().build();
            this.touchEvents = b.touchEvents != null ? b.touchEvents : TouchEventsConfig.builder().build();
            this.uiState = b.uiState != null ? b.uiState : UIStateConfig.builder().build();
            this.lifecycle = b.lifecycle != null ? b.lifecycle : LifecycleConfig.builder().build();
        }

        public static Builder builder() { return new Builder(); }

        public BackNavigationConfig getBackNavigation() { return backNavigation; }
        public TouchEventsConfig getTouchEvents() { return touchEvents; }
        public UIStateConfig getUiState() { return uiState; }
        public LifecycleConfig getLifecycle() { return lifecycle; }

        public static class Builder {
            private BackNavigationConfig backNavigation;
            private TouchEventsConfig touchEvents;
            private UIStateConfig uiState;
            private LifecycleConfig lifecycle;

            public Builder backNavigation(@NonNull BackNavigationConfig back) { this.backNavigation = back; return this; }
            public Builder backNavigation(@NonNull BackNavigationConfig.Builder builder) { this.backNavigation = builder.build(); return this; }
            public Builder touchEvents(@NonNull TouchEventsConfig touch) { this.touchEvents = touch; return this; }
            public Builder touchEvents(@NonNull TouchEventsConfig.Builder builder) { this.touchEvents = builder.build(); return this; }
            public Builder uiState(@NonNull UIStateConfig state) { this.uiState = state; return this; }
            public Builder uiState(@NonNull UIStateConfig.Builder builder) { this.uiState = builder.build(); return this; }
            public Builder lifecycle(@NonNull LifecycleConfig lc) { this.lifecycle = lc; return this; }
            public Builder lifecycle(@NonNull LifecycleConfig.Builder builder) { this.lifecycle = builder.build(); return this; }
            public InteractionGroupConfig build() { return new InteractionGroupConfig(this); }
        }
    }
}
