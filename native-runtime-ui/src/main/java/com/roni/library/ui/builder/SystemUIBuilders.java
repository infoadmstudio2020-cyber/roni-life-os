package com.roni.library.ui.builder;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Basic to Advanced Builders for System UI Components (Group 1).
 * Components:
 *  1. Status Bar
 *  2. System Navigation Bar
 *  3. Fullscreen / Immersive Mode
 *  4. Edge-to-Edge Layout
 */
public final class SystemUIBuilders {

    private SystemUIBuilders() {}

    // ==========================================
    // 1. Status Bar Config & Builder
    // ==========================================
    public static class StatusBarConfig {
        private final boolean enabled;
        private final int color;
        private final boolean lightIcons;
        private final boolean translucent;
        private final boolean contrastEnforced;
        private final long animationDurationMs;

        private StatusBarConfig(Builder b) {
            this.enabled = b.enabled;
            this.color = b.color;
            this.lightIcons = b.lightIcons;
            this.translucent = b.translucent;
            this.contrastEnforced = b.contrastEnforced;
            this.animationDurationMs = b.animationDurationMs;
        }

        public static Builder builder() { return new Builder(); }
        public static StatusBarConfig basic(@ColorInt int color, boolean lightIcons) {
            return builder().color(color).lightIcons(lightIcons).build();
        }

        public boolean isEnabled() { return enabled; }
        public int getColor() { return color; }
        public boolean isLightIcons() { return lightIcons; }
        public boolean isTranslucent() { return translucent; }
        public boolean isContrastEnforced() { return contrastEnforced; }
        public long getAnimationDurationMs() { return animationDurationMs; }

        public static class Builder {
            private boolean enabled = true;
            private int color = Color.TRANSPARENT;
            private boolean lightIcons = false;
            private boolean translucent = false;
            private boolean contrastEnforced = true;
            private long animationDurationMs = 200L;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder color(@ColorInt int color) { this.color = color; return this; }
            public Builder lightIcons(boolean lightIcons) { this.lightIcons = lightIcons; return this; }
            public Builder translucent(boolean translucent) { this.translucent = translucent; return this; }
            public Builder contrastEnforced(boolean contrast) { this.contrastEnforced = contrast; return this; }
            public Builder animationDurationMs(long ms) { this.animationDurationMs = ms; return this; }
            public StatusBarConfig build() { return new StatusBarConfig(this); }
        }
    }

    // ==========================================
    // 2. System Navigation Bar Config & Builder
    // ==========================================
    public static class NavigationBarConfig {
        private final boolean enabled;
        private final int color;
        private final boolean lightButtons;
        private final int dividerColor;
        private final boolean contrastEnforced;

        private NavigationBarConfig(Builder b) {
            this.enabled = b.enabled;
            this.color = b.color;
            this.lightButtons = b.lightButtons;
            this.dividerColor = b.dividerColor;
            this.contrastEnforced = b.contrastEnforced;
        }

        public static Builder builder() { return new Builder(); }
        public static NavigationBarConfig basic(@ColorInt int color, boolean lightButtons) {
            return builder().color(color).lightButtons(lightButtons).build();
        }

        public boolean isEnabled() { return enabled; }
        public int getColor() { return color; }
        public boolean isLightButtons() { return lightButtons; }
        public int getDividerColor() { return dividerColor; }
        public boolean isContrastEnforced() { return contrastEnforced; }

        public static class Builder {
            private boolean enabled = true;
            private int color = Color.TRANSPARENT;
            private boolean lightButtons = false;
            private int dividerColor = Color.TRANSPARENT;
            private boolean contrastEnforced = true;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder color(@ColorInt int color) { this.color = color; return this; }
            public Builder lightButtons(boolean light) { this.lightButtons = light; return this; }
            public Builder dividerColor(@ColorInt int color) { this.dividerColor = color; return this; }
            public Builder contrastEnforced(boolean contrast) { this.contrastEnforced = contrast; return this; }
            public NavigationBarConfig build() { return new NavigationBarConfig(this); }
        }
    }

    // ==========================================
    // 3. Fullscreen / Immersive Mode Config & Builder
    // ==========================================
    public static class ImmersiveModeConfig {
        private final boolean fullscreen;
        private final boolean stickyImmersive;
        private final boolean hideNavigationOnSwipe;
        private final boolean displayCutoutModeShortEdges;

        private ImmersiveModeConfig(Builder b) {
            this.fullscreen = b.fullscreen;
            this.stickyImmersive = b.stickyImmersive;
            this.hideNavigationOnSwipe = b.hideNavigationOnSwipe;
            this.displayCutoutModeShortEdges = b.displayCutoutModeShortEdges;
        }

        public static Builder builder() { return new Builder(); }
        public static ImmersiveModeConfig basic(boolean fullscreen) {
            return builder().fullscreen(fullscreen).build();
        }

        public boolean isFullscreen() { return fullscreen; }
        public boolean isStickyImmersive() { return stickyImmersive; }
        public boolean isHideNavigationOnSwipe() { return hideNavigationOnSwipe; }
        public boolean isDisplayCutoutModeShortEdges() { return displayCutoutModeShortEdges; }

        public static class Builder {
            private boolean fullscreen = false;
            private boolean stickyImmersive = true;
            private boolean hideNavigationOnSwipe = true;
            private boolean displayCutoutModeShortEdges = false;

            public Builder fullscreen(boolean fullscreen) { this.fullscreen = fullscreen; return this; }
            public Builder stickyImmersive(boolean sticky) { this.stickyImmersive = sticky; return this; }
            public Builder hideNavigationOnSwipe(boolean hide) { this.hideNavigationOnSwipe = hide; return this; }
            public Builder displayCutoutModeShortEdges(boolean cutout) { this.displayCutoutModeShortEdges = cutout; return this; }
            public ImmersiveModeConfig build() { return new ImmersiveModeConfig(this); }
        }
    }

    // ==========================================
    // 4. Edge-to-Edge Layout Config & Builder
    // ==========================================
    public static class EdgeToEdgeConfig {
        private final boolean enabled;
        private final boolean applyTopInset;
        private final boolean applyBottomInset;
        private final boolean applyLeftRightInset;

        private EdgeToEdgeConfig(Builder b) {
            this.enabled = b.enabled;
            this.applyTopInset = b.applyTopInset;
            this.applyBottomInset = b.applyBottomInset;
            this.applyLeftRightInset = b.applyLeftRightInset;
        }

        public static Builder builder() { return new Builder(); }
        public static EdgeToEdgeConfig basic(boolean enabled) {
            return builder().enabled(enabled).build();
        }

        public boolean isEnabled() { return enabled; }
        public boolean isApplyTopInset() { return applyTopInset; }
        public boolean isApplyBottomInset() { return applyBottomInset; }
        public boolean isApplyLeftRightInset() { return applyLeftRightInset; }

        public static class Builder {
            private boolean enabled = true;
            private boolean applyTopInset = true;
            private boolean applyBottomInset = true;
            private boolean applyLeftRightInset = true;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder applyTopInset(boolean top) { this.applyTopInset = top; return this; }
            public Builder applyBottomInset(boolean bottom) { this.applyBottomInset = bottom; return this; }
            public Builder applyLeftRightInset(boolean lr) { this.applyLeftRightInset = lr; return this; }
            public EdgeToEdgeConfig build() { return new EdgeToEdgeConfig(this); }
        }
    }

    // ==========================================
    // Complete System UI Group Config & Builder
    // ==========================================
    public static class SystemUIConfig {
        private final StatusBarConfig statusBar;
        private final NavigationBarConfig navigationBar;
        private final ImmersiveModeConfig immersiveMode;
        private final EdgeToEdgeConfig edgeToEdge;

        private SystemUIConfig(Builder b) {
            this.statusBar = b.statusBar != null ? b.statusBar : StatusBarConfig.builder().build();
            this.navigationBar = b.navigationBar != null ? b.navigationBar : NavigationBarConfig.builder().build();
            this.immersiveMode = b.immersiveMode != null ? b.immersiveMode : ImmersiveModeConfig.builder().build();
            this.edgeToEdge = b.edgeToEdge != null ? b.edgeToEdge : EdgeToEdgeConfig.builder().build();
        }

        public static Builder builder() { return new Builder(); }

        public StatusBarConfig getStatusBar() { return statusBar; }
        public NavigationBarConfig getNavigationBar() { return navigationBar; }
        public ImmersiveModeConfig getImmersiveMode() { return immersiveMode; }
        public EdgeToEdgeConfig getEdgeToEdge() { return edgeToEdge; }

        public static class Builder {
            private StatusBarConfig statusBar;
            private NavigationBarConfig navigationBar;
            private ImmersiveModeConfig immersiveMode;
            private EdgeToEdgeConfig edgeToEdge;

            public Builder statusBar(@NonNull StatusBarConfig statusBar) { this.statusBar = statusBar; return this; }
            public Builder statusBar(@NonNull StatusBarConfig.Builder builder) { this.statusBar = builder.build(); return this; }
            public Builder navigationBar(@NonNull NavigationBarConfig navigationBar) { this.navigationBar = navigationBar; return this; }
            public Builder navigationBar(@NonNull NavigationBarConfig.Builder builder) { this.navigationBar = builder.build(); return this; }
            public Builder immersiveMode(@NonNull ImmersiveModeConfig immersiveMode) { this.immersiveMode = immersiveMode; return this; }
            public Builder immersiveMode(@NonNull ImmersiveModeConfig.Builder builder) { this.immersiveMode = builder.build(); return this; }
            public Builder edgeToEdge(@NonNull EdgeToEdgeConfig edgeToEdge) { this.edgeToEdge = edgeToEdge; return this; }
            public Builder edgeToEdge(@NonNull EdgeToEdgeConfig.Builder builder) { this.edgeToEdge = builder.build(); return this; }
            public SystemUIConfig build() { return new SystemUIConfig(this); }
        }
    }
}
