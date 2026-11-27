package com.roni.library.ui.builder;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic to Advanced Builders for App Navigation Components (Group 2).
 * Components:
 *  5. Toolbar / Action Bar
 *  6. Toolbar Menu
 *  7. Navigation Drawer
 *  8. Bottom Navigation
 *  9. Navigation Rail
 * 10. Tab Layout
 * 11. Floating Action Button (FAB)
 */
public final class NavigationBuilders {

    private NavigationBuilders() {}

    // ==========================================
    // 5 & 6. Toolbar & Toolbar Menu Config & Builder
    // ==========================================
    public static class ToolbarConfig {
        public static class ActionItem {
            private final String id;
            private final String label;
            private final int iconResId;
            private final Runnable action;

            public ActionItem(String id, String label, int iconResId, Runnable action) {
                this.id = id;
                this.label = label;
                this.iconResId = iconResId;
                this.action = action;
            }
            public String getId() { return id; }
            public String getLabel() { return label; }
            public int getIconResId() { return iconResId; }
            public Runnable getAction() { return action; }
        }

        private final boolean enabled;
        private final String title;
        private final String subtitle;
        private final int navigationIconResId;
        private final Runnable navigationOnClick;
        private final boolean centerTitle;
        private final float elevation;
        private final int backgroundColor;
        private final int titleTextColor;
        private final List<ActionItem> actions;

        private ToolbarConfig(Builder b) {
            this.enabled = b.enabled;
            this.title = b.title;
            this.subtitle = b.subtitle;
            this.navigationIconResId = b.navigationIconResId;
            this.navigationOnClick = b.navigationOnClick;
            this.centerTitle = b.centerTitle;
            this.elevation = b.elevation;
            this.backgroundColor = b.backgroundColor;
            this.titleTextColor = b.titleTextColor;
            this.actions = new ArrayList<>(b.actions);
        }

        public static Builder builder() { return new Builder(); }
        public static ToolbarConfig basic(String title) {
            return builder().title(title).build();
        }

        public boolean isEnabled() { return enabled; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public int getNavigationIconResId() { return navigationIconResId; }
        public Runnable getNavigationOnClick() { return navigationOnClick; }
        public boolean isCenterTitle() { return centerTitle; }
        public float getElevation() { return elevation; }
        public int getBackgroundColor() { return backgroundColor; }
        public int getTitleTextColor() { return titleTextColor; }
        public List<ActionItem> getActions() { return actions; }

        public static class Builder {
            private boolean enabled = true;
            private String title = "";
            private String subtitle = "";
            private int navigationIconResId = 0;
            private Runnable navigationOnClick = null;
            private boolean centerTitle = false;
            private float elevation = 4.0f;
            private int backgroundColor = Color.TRANSPARENT;
            private int titleTextColor = Color.WHITE;
            private final List<ActionItem> actions = new ArrayList<>();

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder title(String title) { this.title = title != null ? title : ""; return this; }
            public Builder subtitle(String subtitle) { this.subtitle = subtitle != null ? subtitle : ""; return this; }
            public Builder navigationIcon(@DrawableRes int iconResId, @Nullable Runnable onClick) {
                this.navigationIconResId = iconResId;
                this.navigationOnClick = onClick;
                return this;
            }
            public Builder centerTitle(boolean center) { this.centerTitle = center; return this; }
            public Builder elevation(float elevation) { this.elevation = elevation; return this; }
            public Builder backgroundColor(@ColorInt int color) { this.backgroundColor = color; return this; }
            public Builder titleTextColor(@ColorInt int color) { this.titleTextColor = color; return this; }
            public Builder addAction(String id, String label, @DrawableRes int iconResId, Runnable action) {
                this.actions.add(new ActionItem(id, label, iconResId, action));
                return this;
            }
            public ToolbarConfig build() { return new ToolbarConfig(this); }
        }
    }

    // ==========================================
    // 7. Navigation Drawer Config & Builder
    // ==========================================
    public static class DrawerConfig {
        public static class DrawerItem {
            private final String id;
            private final String title;
            private final int iconResId;
            private final Runnable action;

            public DrawerItem(String id, String title, int iconResId, Runnable action) {
                this.id = id;
                this.title = title;
                this.iconResId = iconResId;
                this.action = action;
            }
            public String getId() { return id; }
            public String getTitle() { return title; }
            public int getIconResId() { return iconResId; }
            public Runnable getAction() { return action; }
        }

        private final boolean enabled;
        private final String headerTitle;
        private final String headerSubtitle;
        private final int headerLayoutResId;
        private final int widthDp;
        private final int scrimColor;
        private final float elevation;
        private final List<DrawerItem> items;

        private DrawerConfig(Builder b) {
            this.enabled = b.enabled;
            this.headerTitle = b.headerTitle;
            this.headerSubtitle = b.headerSubtitle;
            this.headerLayoutResId = b.headerLayoutResId;
            this.widthDp = b.widthDp;
            this.scrimColor = b.scrimColor;
            this.elevation = b.elevation;
            this.items = new ArrayList<>(b.items);
        }

        public static Builder builder() { return new Builder(); }
        public static DrawerConfig basic(String headerTitle, String headerSubtitle) {
            return builder().headerTitle(headerTitle).headerSubtitle(headerSubtitle).build();
        }

        public boolean isEnabled() { return enabled; }
        public String getHeaderTitle() { return headerTitle; }
        public String getHeaderSubtitle() { return headerSubtitle; }
        public int getHeaderLayoutResId() { return headerLayoutResId; }
        public int getWidthDp() { return widthDp; }
        public int getScrimColor() { return scrimColor; }
        public float getElevation() { return elevation; }
        public List<DrawerItem> getItems() { return items; }

        public static class Builder {
            private boolean enabled = true;
            private String headerTitle = "";
            private String headerSubtitle = "";
            private int headerLayoutResId = 0;
            private int widthDp = 300;
            private int scrimColor = 0x80000000;
            private float elevation = 16.0f;
            private final List<DrawerItem> items = new ArrayList<>();

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder headerTitle(String title) { this.headerTitle = title; return this; }
            public Builder headerSubtitle(String subtitle) { this.headerSubtitle = subtitle; return this; }
            public Builder headerLayoutResId(@LayoutRes int resId) { this.headerLayoutResId = resId; return this; }
            public Builder widthDp(int dp) { this.widthDp = dp; return this; }
            public Builder scrimColor(@ColorInt int color) { this.scrimColor = color; return this; }
            public Builder elevation(float elevation) { this.elevation = elevation; return this; }
            public Builder addItem(String id, String title, @DrawableRes int iconResId, Runnable action) {
                this.items.add(new DrawerItem(id, title, iconResId, action));
                return this;
            }
            public DrawerConfig build() { return new DrawerConfig(this); }
        }
    }

    // ==========================================
    // 8. Bottom Navigation Config & Builder
    // ==========================================
    public static class BottomNavConfig {
        public static class TabItem {
            private final String id;
            private final String label;
            private final int iconResId;

            public TabItem(String id, String label, int iconResId) {
                this.id = id;
                this.label = label;
                this.iconResId = iconResId;
            }
            public String getId() { return id; }
            public String getLabel() { return label; }
            public int getIconResId() { return iconResId; }
        }

        private final boolean enabled;
        private final float elevation;
        private final int backgroundColor;
        private final boolean itemRippleEnabled;
        private final int labelVisibilityMode; // 0=labeled, 1=selected, 2=unlabeled
        private final List<TabItem> tabs;
        private final Map<String, Integer> badges;

        private BottomNavConfig(Builder b) {
            this.enabled = b.enabled;
            this.elevation = b.elevation;
            this.backgroundColor = b.backgroundColor;
            this.itemRippleEnabled = b.itemRippleEnabled;
            this.labelVisibilityMode = b.labelVisibilityMode;
            this.tabs = new ArrayList<>(b.tabs);
            this.badges = new HashMap<>(b.badges);
        }

        public static Builder builder() { return new Builder(); }

        public boolean isEnabled() { return enabled; }
        public float getElevation() { return elevation; }
        public int getBackgroundColor() { return backgroundColor; }
        public boolean isItemRippleEnabled() { return itemRippleEnabled; }
        public int getLabelVisibilityMode() { return labelVisibilityMode; }
        public List<TabItem> getTabs() { return tabs; }
        public Map<String, Integer> getBadges() { return badges; }

        public static class Builder {
            private boolean enabled = true;
            private float elevation = 8.0f;
            private int backgroundColor = Color.TRANSPARENT;
            private boolean itemRippleEnabled = true;
            private int labelVisibilityMode = 0;
            private final List<TabItem> tabs = new ArrayList<>();
            private final Map<String, Integer> badges = new HashMap<>();

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder elevation(float elevation) { this.elevation = elevation; return this; }
            public Builder backgroundColor(@ColorInt int color) { this.backgroundColor = color; return this; }
            public Builder itemRippleEnabled(boolean ripple) { this.itemRippleEnabled = ripple; return this; }
            public Builder labelVisibilityMode(int mode) { this.labelVisibilityMode = mode; return this; }
            public Builder addTab(String id, String label, @DrawableRes int iconResId) {
                this.tabs.add(new TabItem(id, label, iconResId));
                return this;
            }
            public Builder badge(String tabId, int count) {
                this.badges.put(tabId, count);
                return this;
            }
            public BottomNavConfig build() { return new BottomNavConfig(this); }
        }
    }

    // ==========================================
    // 9. Navigation Rail Config & Builder
    // ==========================================
    public static class NavigationRailConfig {
        private final boolean enabled;
        private final int menuGravity;
        private final float elevation;

        private NavigationRailConfig(Builder b) {
            this.enabled = b.enabled;
            this.menuGravity = b.menuGravity;
            this.elevation = b.elevation;
        }

        public static Builder builder() { return new Builder(); }
        public boolean isEnabled() { return enabled; }
        public int getMenuGravity() { return menuGravity; }
        public float getElevation() { return elevation; }

        public static class Builder {
            private boolean enabled = false;
            private int menuGravity = 17; // center
            private float elevation = 4.0f;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder menuGravity(int gravity) { this.menuGravity = gravity; return this; }
            public Builder elevation(float elevation) { this.elevation = elevation; return this; }
            public NavigationRailConfig build() { return new NavigationRailConfig(this); }
        }
    }

    // ==========================================
    // 10. Tab Layout Config & Builder
    // ==========================================
    public static class TabLayoutConfig {
        private final boolean enabled;
        private final int tabMode; // 0=scrollable, 1=fixed
        private final int tabGravity; // 0=fill, 1=center
        private final int indicatorColor;
        private final int indicatorHeightDp;

        private TabLayoutConfig(Builder b) {
            this.enabled = b.enabled;
            this.tabMode = b.tabMode;
            this.tabGravity = b.tabGravity;
            this.indicatorColor = b.indicatorColor;
            this.indicatorHeightDp = b.indicatorHeightDp;
        }

        public static Builder builder() { return new Builder(); }
        public boolean isEnabled() { return enabled; }
        public int getTabMode() { return tabMode; }
        public int getTabGravity() { return tabGravity; }
        public int getIndicatorColor() { return indicatorColor; }
        public int getIndicatorHeightDp() { return indicatorHeightDp; }

        public static class Builder {
            private boolean enabled = false;
            private int tabMode = 1; // fixed
            private int tabGravity = 0; // fill
            private int indicatorColor = Color.TRANSPARENT;
            private int indicatorHeightDp = 3;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder tabMode(int mode) { this.tabMode = mode; return this; }
            public Builder tabGravity(int gravity) { this.tabGravity = gravity; return this; }
            public Builder indicatorColor(@ColorInt int color) { this.indicatorColor = color; return this; }
            public Builder indicatorHeightDp(int dp) { this.indicatorHeightDp = dp; return this; }
            public TabLayoutConfig build() { return new TabLayoutConfig(this); }
        }
    }

    // ==========================================
    // 11. Floating Action Button (FAB) Config & Builder
    // ==========================================
    public static class FabConfig {
        private final boolean enabled;
        private final int iconResId;
        private final Runnable onClick;
        private final boolean extended;
        private final String text;
        private final float elevation;
        private final int backgroundColor;

        private FabConfig(Builder b) {
            this.enabled = b.enabled;
            this.iconResId = b.iconResId;
            this.onClick = b.onClick;
            this.extended = b.extended;
            this.text = b.text;
            this.elevation = b.elevation;
            this.backgroundColor = b.backgroundColor;
        }

        public static Builder builder() { return new Builder(); }
        public static FabConfig basic(@DrawableRes int iconResId, @Nullable Runnable onClick) {
            return builder().icon(iconResId).onClick(onClick).build();
        }

        public boolean isEnabled() { return enabled; }
        public int getIconResId() { return iconResId; }
        public Runnable getOnClick() { return onClick; }
        public boolean isExtended() { return extended; }
        public String getText() { return text; }
        public float getElevation() { return elevation; }
        public int getBackgroundColor() { return backgroundColor; }

        public static class Builder {
            private boolean enabled = true;
            private int iconResId = 0;
            private Runnable onClick = null;
            private boolean extended = false;
            private String text = "";
            private float elevation = 6.0f;
            private int backgroundColor = Color.TRANSPARENT;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder icon(@DrawableRes int iconResId) { this.iconResId = iconResId; return this; }
            public Builder onClick(@Nullable Runnable onClick) { this.onClick = onClick; return this; }
            public Builder extended(boolean extended) { this.extended = extended; return this; }
            public Builder text(String text) { this.text = text != null ? text : ""; return this; }
            public Builder elevation(float elevation) { this.elevation = elevation; return this; }
            public Builder backgroundColor(@ColorInt int color) { this.backgroundColor = color; return this; }
            public FabConfig build() { return new FabConfig(this); }
        }
    }

    // ==========================================
    // Complete App Navigation Group Config & Builder
    // ==========================================
    public static class AppNavigationConfig {
        private final ToolbarConfig toolbar;
        private final DrawerConfig drawer;
        private final BottomNavConfig bottomNav;
        private final NavigationRailConfig navigationRail;
        private final TabLayoutConfig tabLayout;
        private final FabConfig fab;

        private AppNavigationConfig(Builder b) {
            this.toolbar = b.toolbar != null ? b.toolbar : ToolbarConfig.builder().build();
            this.drawer = b.drawer != null ? b.drawer : DrawerConfig.builder().build();
            this.bottomNav = b.bottomNav != null ? b.bottomNav : BottomNavConfig.builder().build();
            this.navigationRail = b.navigationRail != null ? b.navigationRail : NavigationRailConfig.builder().build();
            this.tabLayout = b.tabLayout != null ? b.tabLayout : TabLayoutConfig.builder().build();
            this.fab = b.fab != null ? b.fab : FabConfig.builder().build();
        }

        public static Builder builder() { return new Builder(); }

        public ToolbarConfig getToolbar() { return toolbar; }
        public DrawerConfig getDrawer() { return drawer; }
        public BottomNavConfig getBottomNav() { return bottomNav; }
        public NavigationRailConfig getNavigationRail() { return navigationRail; }
        public TabLayoutConfig getTabLayout() { return tabLayout; }
        public FabConfig getFab() { return fab; }

        public static class Builder {
            private ToolbarConfig toolbar;
            private DrawerConfig drawer;
            private BottomNavConfig bottomNav;
            private NavigationRailConfig navigationRail;
            private TabLayoutConfig tabLayout;
            private FabConfig fab;

            public Builder toolbar(@NonNull ToolbarConfig toolbar) { this.toolbar = toolbar; return this; }
            public Builder toolbar(@NonNull ToolbarConfig.Builder builder) { this.toolbar = builder.build(); return this; }
            public Builder drawer(@NonNull DrawerConfig drawer) { this.drawer = drawer; return this; }
            public Builder drawer(@NonNull DrawerConfig.Builder builder) { this.drawer = builder.build(); return this; }
            public Builder bottomNav(@NonNull BottomNavConfig bottomNav) { this.bottomNav = bottomNav; return this; }
            public Builder bottomNav(@NonNull BottomNavConfig.Builder builder) { this.bottomNav = builder.build(); return this; }
            public Builder navigationRail(@NonNull NavigationRailConfig rail) { this.navigationRail = rail; return this; }
            public Builder navigationRail(@NonNull NavigationRailConfig.Builder builder) { this.navigationRail = builder.build(); return this; }
            public Builder tabLayout(@NonNull TabLayoutConfig tabLayout) { this.tabLayout = tabLayout; return this; }
            public Builder tabLayout(@NonNull TabLayoutConfig.Builder builder) { this.tabLayout = builder.build(); return this; }
            public Builder fab(@NonNull FabConfig fab) { this.fab = fab; return this; }
            public Builder fab(@NonNull FabConfig.Builder builder) { this.fab = builder.build(); return this; }
            public AppNavigationConfig build() { return new AppNavigationConfig(this); }
        }
    }
}
