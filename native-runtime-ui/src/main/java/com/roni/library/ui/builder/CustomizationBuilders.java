package com.roni.library.ui.builder;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic to Advanced Builders for UI Customization Components (Group 5).
 * Components:
 * 23. Theme Integration
 * 24. Background / Color
 * 25. Animation
 * 26. Visibility Controller
 * 27. Dynamic Layout Controller
 */
public final class CustomizationBuilders {

    private CustomizationBuilders() {}

    // ==========================================
    // 23. Theme Integration Config & Builder
    // ==========================================
    public static class ThemeConfig {
        private final boolean isDark;
        private final String primaryColor;
        private final String surfaceColor;
        private final String textColor;
        private final String cardColor;
        private final Map<String, Object> extraTokens;

        private ThemeConfig(Builder b) {
            this.isDark = b.isDark;
            this.primaryColor = b.primaryColor;
            this.surfaceColor = b.surfaceColor;
            this.textColor = b.textColor;
            this.cardColor = b.cardColor;
            this.extraTokens = new HashMap<>(b.extraTokens);
        }

        public static Builder builder() { return new Builder(); }
        public static ThemeConfig basic(String primaryColor, boolean isDark) {
            return builder().primaryColor(primaryColor).isDark(isDark).build();
        }

        public boolean isDark() { return isDark; }
        public String getPrimaryColor() { return primaryColor; }
        public String getSurfaceColor() { return surfaceColor; }
        public String getTextColor() { return textColor; }
        public String getCardColor() { return cardColor; }
        public Map<String, Object> getExtraTokens() { return extraTokens; }

        public static class Builder {
            private boolean isDark = true;
            private String primaryColor = "#0284C7";
            private String surfaceColor = "#0B0F19";
            private String textColor = "#F8FAFC";
            private String cardColor = "#0F172A";
            private final Map<String, Object> extraTokens = new HashMap<>();

            public Builder isDark(boolean dark) { this.isDark = dark; return this; }
            public Builder primaryColor(String color) { this.primaryColor = color; return this; }
            public Builder surfaceColor(String color) { this.surfaceColor = color; return this; }
            public Builder textColor(String color) { this.textColor = color; return this; }
            public Builder cardColor(String color) { this.cardColor = color; return this; }
            public Builder addToken(String key, Object val) { this.extraTokens.put(key, val); return this; }
            public ThemeConfig build() { return new ThemeConfig(this); }
        }
    }

    // ==========================================
    // 24. Background / Color Config & Builder
    // ==========================================
    public static class BackgroundConfig {
        private final int backgroundColor;
        private final int tintColor;

        private BackgroundConfig(Builder b) {
            this.backgroundColor = b.backgroundColor;
            this.tintColor = b.tintColor;
        }

        public static Builder builder() { return new Builder(); }
        public static BackgroundConfig basic(@ColorInt int color) {
            return builder().backgroundColor(color).build();
        }

        public int getBackgroundColor() { return backgroundColor; }
        public int getTintColor() { return tintColor; }

        public static class Builder {
            private int backgroundColor = Color.TRANSPARENT;
            private int tintColor = Color.TRANSPARENT;

            public Builder backgroundColor(@ColorInt int color) { this.backgroundColor = color; return this; }
            public Builder tintColor(@ColorInt int tint) { this.tintColor = tint; return this; }
            public BackgroundConfig build() { return new BackgroundConfig(this); }
        }
    }

    // ==========================================
    // 25. Animation Config & Builder
    // ==========================================
    public static class AnimationConfig {
        private final long durationMs;
        private final boolean fadeEnabled;
        private final boolean slideEnabled;
        private final long startDelayMs;

        private AnimationConfig(Builder b) {
            this.durationMs = b.durationMs;
            this.fadeEnabled = b.fadeEnabled;
            this.slideEnabled = b.slideEnabled;
            this.startDelayMs = b.startDelayMs;
        }

        public static Builder builder() { return new Builder(); }
        public static AnimationConfig basic(long durationMs) {
            return builder().durationMs(durationMs).build();
        }

        public long getDurationMs() { return durationMs; }
        public boolean isFadeEnabled() { return fadeEnabled; }
        public boolean isSlideEnabled() { return slideEnabled; }
        public long getStartDelayMs() { return startDelayMs; }

        public static class Builder {
            private long durationMs = 250L;
            private boolean fadeEnabled = true;
            private boolean slideEnabled = false;
            private long startDelayMs = 0L;

            public Builder durationMs(long ms) { this.durationMs = ms; return this; }
            public Builder fadeEnabled(boolean fade) { this.fadeEnabled = fade; return this; }
            public Builder slideEnabled(boolean slide) { this.slideEnabled = slide; return this; }
            public Builder startDelayMs(long delay) { this.startDelayMs = delay; return this; }
            public AnimationConfig build() { return new AnimationConfig(this); }
        }
    }

    // ==========================================
    // 26. Visibility Controller Config & Builder
    // ==========================================
    public static class VisibilityConfig {
        private final boolean animateTransitions;
        private final long animationDurationMs;

        private VisibilityConfig(Builder b) {
            this.animateTransitions = b.animateTransitions;
            this.animationDurationMs = b.animationDurationMs;
        }

        public static Builder builder() { return new Builder(); }
        public boolean isAnimateTransitions() { return animateTransitions; }
        public long getAnimationDurationMs() { return animationDurationMs; }

        public static class Builder {
            private boolean animateTransitions = true;
            private long animationDurationMs = 200L;

            public Builder animateTransitions(boolean animate) { this.animateTransitions = animate; return this; }
            public Builder animationDurationMs(long duration) { this.animationDurationMs = duration; return this; }
            public VisibilityConfig build() { return new VisibilityConfig(this); }
        }
    }

    // ==========================================
    // 27. Dynamic Layout Controller Config & Builder
    // ==========================================
    public static class DynamicLayoutConfig {
        private final boolean autoRelayout;
        private final boolean preserveScrollPosition;

        private DynamicLayoutConfig(Builder b) {
            this.autoRelayout = b.autoRelayout;
            this.preserveScrollPosition = b.preserveScrollPosition;
        }

        public static Builder builder() { return new Builder(); }
        public boolean isAutoRelayout() { return autoRelayout; }
        public boolean isPreserveScrollPosition() { return preserveScrollPosition; }

        public static class Builder {
            private boolean autoRelayout = true;
            private boolean preserveScrollPosition = true;

            public Builder autoRelayout(boolean auto) { this.autoRelayout = auto; return this; }
            public Builder preserveScrollPosition(boolean preserve) { this.preserveScrollPosition = preserve; return this; }
            public DynamicLayoutConfig build() { return new DynamicLayoutConfig(this); }
        }
    }

    // ==========================================
    // Complete Customization Group Config & Builder
    // ==========================================
    public static class CustomizationGroupConfig {
        private final ThemeConfig theme;
        private final BackgroundConfig background;
        private final AnimationConfig animation;
        private final VisibilityConfig visibility;
        private final DynamicLayoutConfig dynamicLayout;

        private CustomizationGroupConfig(Builder b) {
            this.theme = b.theme != null ? b.theme : ThemeConfig.builder().build();
            this.background = b.background != null ? b.background : BackgroundConfig.builder().build();
            this.animation = b.animation != null ? b.animation : AnimationConfig.builder().build();
            this.visibility = b.visibility != null ? b.visibility : VisibilityConfig.builder().build();
            this.dynamicLayout = b.dynamicLayout != null ? b.dynamicLayout : DynamicLayoutConfig.builder().build();
        }

        public static Builder builder() { return new Builder(); }

        public ThemeConfig getTheme() { return theme; }
        public BackgroundConfig getBackground() { return background; }
        public AnimationConfig getAnimation() { return animation; }
        public VisibilityConfig getVisibility() { return visibility; }
        public DynamicLayoutConfig getDynamicLayout() { return dynamicLayout; }

        public static class Builder {
            private ThemeConfig theme;
            private BackgroundConfig background;
            private AnimationConfig animation;
            private VisibilityConfig visibility;
            private DynamicLayoutConfig dynamicLayout;

            public Builder theme(@NonNull ThemeConfig theme) { this.theme = theme; return this; }
            public Builder theme(@NonNull ThemeConfig.Builder builder) { this.theme = builder.build(); return this; }
            public Builder background(@NonNull BackgroundConfig bg) { this.background = bg; return this; }
            public Builder background(@NonNull BackgroundConfig.Builder builder) { this.background = builder.build(); return this; }
            public Builder animation(@NonNull AnimationConfig anim) { this.animation = anim; return this; }
            public Builder animation(@NonNull AnimationConfig.Builder builder) { this.animation = builder.build(); return this; }
            public Builder visibility(@NonNull VisibilityConfig vis) { this.visibility = vis; return this; }
            public Builder visibility(@NonNull VisibilityConfig.Builder builder) { this.visibility = builder.build(); return this; }
            public Builder dynamicLayout(@NonNull DynamicLayoutConfig layout) { this.dynamicLayout = layout; return this; }
            public Builder dynamicLayout(@NonNull DynamicLayoutConfig.Builder builder) { this.dynamicLayout = builder.build(); return this; }
            public CustomizationGroupConfig build() { return new CustomizationGroupConfig(this); }
        }
    }
}
