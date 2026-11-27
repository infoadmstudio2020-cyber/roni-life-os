package com.roni.library.ui.navigation;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Complete Navigation UI Manager coordinating Bottom Navigation, Navigation Drawer,
 * Navigation Rail, Tab Layout, and Floating Action Button (FAB).
 */
public class NavigationManager {

    public enum DrawerState {
        OPEN, CLOSED, DRAGGING
    }

    public interface OnTabSelectedListener {
        void onTabSelected(String tabId, int position);
    }

    public interface OnDrawerItemClickListener {
        void onDrawerItemClick(String itemId);
    }

    public static class TabItem {
        private final String tabId;
        private final String label;
        private final int iconResId;

        public TabItem(String tabId, String label) {
            this(tabId, label, 0);
        }

        public TabItem(String tabId, String label, int iconResId) {
            this.tabId = tabId != null ? tabId : "";
            this.label = label != null ? label : "";
            this.iconResId = iconResId;
        }

        public String getTabId() { return tabId; }
        public String getLabel() { return label; }
        public int getIconResId() { return iconResId; }
    }

    public static class DrawerItem {
        private final String id;
        private final String title;
        private final int iconResId;
        private final Runnable action;

        public DrawerItem(String id, String title, int iconResId, Runnable action) {
            this.id = id != null ? id : "";
            this.title = title != null ? title : "";
            this.iconResId = iconResId;
            this.action = action;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public int getIconResId() { return iconResId; }
        public Runnable getAction() { return action; }
    }

    private final List<TabItem> tabs = new CopyOnWriteArrayList<>();
    private final List<DrawerItem> drawerItems = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> badges = new ConcurrentHashMap<>();
    private final Map<String, String> drawerToTabMap = new ConcurrentHashMap<>();
    private final Map<String, String> tabToDrawerMap = new ConcurrentHashMap<>();
    private volatile String selectedTab = "";
    private volatile String selectedDrawerItemId = "";
    private volatile boolean syncDrawerAndBottomNav = true;
    private volatile DrawerState drawerState = DrawerState.CLOSED;

    private BottomNavigationView boundBottomNav;
    private DrawerLayout boundDrawerLayout;
    private NavigationView boundNavigationView;
    private NavigationRailView boundRailView;
    private TabLayout boundTabLayout;
    private FloatingActionButton boundFab;
    private Activity boundActivity;

    private OnTabSelectedListener tabListener;
    private OnDrawerItemClickListener drawerItemListener;

    public NavigationManager() {}

    // ==================== Linking & Synchronization ====================

    /**
     * Explicitly links a Drawer Navigation Item to a Bottom Navigation Tab.
     * When either item is clicked or selected, the counterpart will automatically synchronize.
     */
    public void linkDrawerAndBottomNav(@NonNull String drawerItemId, @NonNull String tabId) {
        drawerToTabMap.put(drawerItemId, tabId);
        tabToDrawerMap.put(tabId, drawerItemId);
    }

    public void setSyncDrawerAndBottomNav(boolean enabled) {
        this.syncDrawerAndBottomNav = enabled;
    }

    public boolean isSyncDrawerAndBottomNav() {
        return syncDrawerAndBottomNav;
    }

    @Nullable
    public String findMatchingTabId(@Nullable String drawerItemId) {
        if (drawerItemId == null) return null;
        // 1. Explicit mapping
        String explicit = drawerToTabMap.get(drawerItemId);
        if (explicit != null && hasTab(explicit)) return explicit;

        // 2. Direct exact ID match
        if (hasTab(drawerItemId)) return drawerItemId;

        // 3. Normalized stripped prefix (nav_settings -> settings, drawer_settings -> settings)
        String stripped = stripPrefix(drawerItemId);
        if (hasTab(stripped)) return stripped;

        for (TabItem tab : tabs) {
            if (stripPrefix(tab.getTabId()).equalsIgnoreCase(stripped)) {
                return tab.getTabId();
            }
        }

        // 4. Label / Title match
        DrawerItem dItem = getDrawerItem(drawerItemId);
        if (dItem != null && !dItem.getTitle().isEmpty()) {
            for (TabItem tab : tabs) {
                if (tab.getLabel().equalsIgnoreCase(dItem.getTitle())) {
                    return tab.getTabId();
                }
            }
        }
        return null;
    }

    @Nullable
    public String findMatchingDrawerItemId(@Nullable String tabId) {
        if (tabId == null) return null;
        // 1. Explicit mapping
        String explicit = tabToDrawerMap.get(tabId);
        if (explicit != null && hasDrawerItem(explicit)) return explicit;

        // 2. Direct exact ID match
        if (hasDrawerItem(tabId)) return tabId;

        // 3. Prefix variants (nav_home, drawer_home, menu_home)
        if (hasDrawerItem("nav_" + tabId)) return "nav_" + tabId;
        if (hasDrawerItem("drawer_" + tabId)) return "drawer_" + tabId;
        if (hasDrawerItem("menu_" + tabId)) return "menu_" + tabId;

        String stripped = stripPrefix(tabId);
        for (DrawerItem item : drawerItems) {
            if (stripPrefix(item.getId()).equalsIgnoreCase(stripped)) {
                return item.getId();
            }
        }

        // 4. Label / Title match
        TabItem tab = getTab(tabId);
        if (tab != null && !tab.getLabel().isEmpty()) {
            for (DrawerItem item : drawerItems) {
                if (item.getTitle().equalsIgnoreCase(tab.getLabel())) {
                    return item.getId();
                }
            }
        }
        return null;
    }

    private static String stripPrefix(String str) {
        if (str == null) return "";
        if (str.startsWith("nav_")) return str.substring(4);
        if (str.startsWith("drawer_")) return str.substring(7);
        if (str.startsWith("menu_")) return str.substring(5);
        if (str.startsWith("tab_")) return str.substring(4);
        return str;
    }

    public boolean hasTab(String tabId) {
        if (tabId == null) return false;
        for (TabItem item : tabs) {
            if (item.getTabId().equalsIgnoreCase(tabId)) return true;
        }
        return false;
    }

    public boolean hasDrawerItem(String drawerId) {
        if (drawerId == null) return false;
        for (DrawerItem item : drawerItems) {
            if (item.getId().equalsIgnoreCase(drawerId)) return true;
        }
        return false;
    }

    @Nullable
    public TabItem getTab(String tabId) {
        if (tabId == null) return null;
        for (TabItem item : tabs) {
            if (item.getTabId().equalsIgnoreCase(tabId)) return item;
        }
        return null;
    }

    @Nullable
    public DrawerItem getDrawerItem(String drawerId) {
        if (drawerId == null) return null;
        for (DrawerItem item : drawerItems) {
            if (item.getId().equalsIgnoreCase(drawerId)) return item;
        }
        return null;
    }

    // ==================== Tabs / Bottom Navigation ====================

    public void addTab(String tabId, String label) {
        addTab(tabId, label, 0);
    }

    public void addTab(String tabId, String label, int iconResId) {
        if (tabId != null) {
            tabs.add(new TabItem(tabId, label, iconResId));
            if (selectedTab.isEmpty()) {
                selectedTab = tabId;
            }
            updateBottomNavView();
        }
    }

    public void registerTab(String tabId) {
        addTab(tabId, tabId);
    }

    public void selectTab(String tabId) {
        selectTab(tabId, true);
    }

    public void selectTab(String tabId, boolean syncWithDrawer) {
        if (tabId != null) {
            this.selectedTab = tabId;
            if (boundBottomNav != null && boundActivity != null) {
                boundActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < tabs.size(); i++) {
                            if (tabs.get(i).getTabId().equals(tabId)) {
                                boundBottomNav.setSelectedItemId(i + 1);
                                break;
                            }
                        }
                    }
                });
            }
            if (syncWithDrawer && syncDrawerAndBottomNav) {
                String matchingDrawerId = findMatchingDrawerItemId(tabId);
                if (matchingDrawerId != null && !matchingDrawerId.equals(selectedDrawerItemId)) {
                    selectDrawerItem(matchingDrawerId, false);
                }
            }
        }
    }

    public String getSelectedTab() { return selectedTab; }
    public String getActiveTabId() { return selectedTab; }
    public int getTabCount() { return tabs.size(); }
    public List<TabItem> getTabs() { return new ArrayList<>(tabs); }

    public List<String> getRegisteredTabs() {
        List<String> list = new ArrayList<>();
        for (TabItem item : tabs) {
            list.add(item.getTabId());
        }
        return list;
    }

    public void setBadge(String tabId, int count) {
        if (tabId != null) {
            badges.put(tabId, Math.max(0, count));
            updateBadgeView(tabId, count);
        }
    }

    public int getBadge(String tabId) {
        if (tabId == null) return 0;
        Integer b = badges.get(tabId);
        return b != null ? b : 0;
    }

    public synchronized void bindBottomNavigationView(@NonNull BottomNavigationView nav,
                                                      @NonNull Activity activity,
                                                      @Nullable OnTabSelectedListener listener) {
        this.boundBottomNav = nav;
        this.boundActivity = activity;
        this.tabListener = listener;

        updateBottomNavView();

        nav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int index = item.getItemId() - 1;
                if (index >= 0 && index < tabs.size()) {
                    String tabId = tabs.get(index).getTabId();
                    selectedTab = tabId;
                    if (syncDrawerAndBottomNav) {
                        String matchingDrawer = findMatchingDrawerItemId(tabId);
                        if (matchingDrawer != null) {
                            selectDrawerItem(matchingDrawer, false);
                        }
                    }
                    if (tabListener != null) {
                        tabListener.onTabSelected(tabId, index);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void updateBottomNavView() {
        if (boundBottomNav == null || boundActivity == null) return;
        boundActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (boundBottomNav == null) return;
                Menu menu = boundBottomNav.getMenu();
                menu.clear();
                for (int i = 0; i < tabs.size(); i++) {
                    TabItem tab = tabs.get(i);
                    MenuItem mi = menu.add(0, i + 1, i, tab.getLabel());
                    if (tab.getIconResId() != 0) {
                        mi.setIcon(tab.getIconResId());
                    } else {
                        mi.setIcon(android.R.drawable.ic_menu_agenda);
                    }
                }
            }
        });
    }

    private void updateBadgeView(String tabId, int count) {
        if (boundBottomNav == null || boundActivity == null) return;
        boundActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (boundBottomNav == null) return;
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i).getTabId().equals(tabId)) {
                        int itemId = i + 1;
                        if (count > 0) {
                            BadgeDrawable badge = boundBottomNav.getOrCreateBadge(itemId);
                            badge.setNumber(count);
                            badge.setVisible(true);
                        } else {
                            boundBottomNav.removeBadge(itemId);
                        }
                        break;
                    }
                }
            }
        });
    }

    // ==================== Drawer ====================

    public synchronized void bindDrawerLayout(@NonNull DrawerLayout drawerLayout,
                                             @Nullable NavigationView navView,
                                             @NonNull Activity activity,
                                             @Nullable OnDrawerItemClickListener listener) {
        this.boundDrawerLayout = drawerLayout;
        this.boundNavigationView = navView;
        this.boundActivity = activity;
        this.drawerItemListener = listener;

        updateNavigationView();

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                drawerState = DrawerState.OPEN;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                drawerState = DrawerState.CLOSED;
            }
        });
    }

    public void addDrawerItem(String id, String title, int iconResId, Runnable action) {
        drawerItems.add(new DrawerItem(id, title, iconResId, action));
        if (selectedDrawerItemId.isEmpty() && !drawerItems.isEmpty()) {
            selectedDrawerItemId = id;
        }
        updateNavigationView();
    }

    public void removeDrawerItem(String id) {
        if (id == null) return;
        for (DrawerItem item : drawerItems) {
            if (id.equals(item.getId())) {
                drawerItems.remove(item);
                break;
            }
        }
        updateNavigationView();
    }

    public String getSelectedDrawerItemId() {
        return selectedDrawerItemId;
    }

    public void selectDrawerItem(@Nullable String drawerItemId) {
        selectDrawerItem(drawerItemId, true);
    }

    public void selectDrawerItem(@Nullable String drawerItemId, boolean syncWithBottomNav) {
        if (drawerItemId == null) return;
        this.selectedDrawerItemId = drawerItemId;

        if (boundNavigationView != null && boundActivity != null) {
            boundActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (boundNavigationView == null) return;
                    Menu menu = boundNavigationView.getMenu();
                    for (int i = 0; i < drawerItems.size(); i++) {
                        MenuItem mi = menu.findItem(i + 1);
                        if (mi != null) {
                            boolean match = drawerItems.get(i).getId().equalsIgnoreCase(drawerItemId);
                            mi.setChecked(match);
                            if (match) {
                                boundNavigationView.setCheckedItem(i + 1);
                            }
                        }
                    }
                }
            });
        }

        if (syncWithBottomNav && syncDrawerAndBottomNav) {
            String matchingTabId = findMatchingTabId(drawerItemId);
            if (matchingTabId != null && !matchingTabId.equalsIgnoreCase(selectedTab)) {
                selectTab(matchingTabId, false);
            }
        }
    }

    private void updateNavigationView() {
        if (boundNavigationView == null || boundActivity == null) return;
        boundActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (boundNavigationView == null) return;
                Menu menu = boundNavigationView.getMenu();
                menu.clear();
                for (int i = 0; i < drawerItems.size(); i++) {
                    final DrawerItem item = drawerItems.get(i);
                    final int itemId = i + 1;
                    MenuItem mi = menu.add(0, itemId, i, item.getTitle());
                    mi.setCheckable(true);
                    if (item.getId().equalsIgnoreCase(selectedDrawerItemId)) {
                        mi.setChecked(true);
                        boundNavigationView.setCheckedItem(itemId);
                    }
                    if (item.getIconResId() != 0) {
                        mi.setIcon(item.getIconResId());
                    }
                    mi.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            selectDrawerItem(item.getId(), true);
                            if (item.getAction() != null) {
                                item.getAction().run();
                            }
                            if (drawerItemListener != null) {
                                drawerItemListener.onDrawerItemClick(item.getId());
                            }
                            closeDrawer();
                            return true;
                        }
                    });
                }
            }
        });
    }

    public void openDrawer() {
        drawerState = DrawerState.OPEN;
        if (boundDrawerLayout != null && boundActivity != null) {
            boundActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (boundDrawerLayout != null) {
                        boundDrawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            });
        }
    }

    public void closeDrawer() {
        drawerState = DrawerState.CLOSED;
        if (boundDrawerLayout != null && boundActivity != null) {
            boundActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (boundDrawerLayout != null) {
                        boundDrawerLayout.closeDrawer(GravityCompat.START);
                    }
                }
            });
        }
    }

    public void toggleDrawer() {
        if (isDrawerOpen()) {
            closeDrawer();
        } else {
            openDrawer();
        }
    }

    public boolean isDrawerOpen() {
        if (boundDrawerLayout != null) {
            return boundDrawerLayout.isDrawerOpen(GravityCompat.START);
        }
        return drawerState == DrawerState.OPEN;
    }

    public void setDrawerState(DrawerState newState) {
        if (newState != null) {
            this.drawerState = newState;
            if (newState == DrawerState.OPEN) {
                openDrawer();
            } else if (newState == DrawerState.CLOSED) {
                closeDrawer();
            }
        }
    }

    public DrawerState getDrawerState() { return drawerState; }

    // ==================== Floating Action Button ====================

    public synchronized void bindFloatingActionButton(@NonNull FloatingActionButton fab,
                                                      @NonNull Activity activity,
                                                      @Nullable Runnable onClick) {
        this.boundFab = fab;
        this.boundActivity = activity;
        if (onClick != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClick.run();
                }
            });
        }
    }

    public synchronized void bindNavigationRail(@NonNull NavigationRailView railView, @NonNull Activity activity) {
        this.boundRailView = railView;
        this.boundActivity = activity;
    }

    public synchronized void bindTabLayout(@NonNull TabLayout tabLayout, @NonNull Activity activity) {
        this.boundTabLayout = tabLayout;
        this.boundActivity = activity;
    }

    public void setFabVisible(boolean visible) {
        if (boundFab != null && boundActivity != null) {
            boundActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (boundFab != null) {
                        if (visible) boundFab.show(); else boundFab.hide();
                    }
                }
            });
        }
    }

    public void clear() {
        tabs.clear();
        drawerItems.clear();
        badges.clear();
        selectedTab = "";
        drawerState = DrawerState.CLOSED;
    }

    public synchronized void reset() {
        clear();
        boundBottomNav = null;
        boundDrawerLayout = null;
        boundNavigationView = null;
        boundRailView = null;
        boundTabLayout = null;
        boundFab = null;
        boundActivity = null;
        tabListener = null;
        drawerItemListener = null;
    }
}
