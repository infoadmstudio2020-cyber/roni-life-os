package com.roni.library.ui.navigation;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.tabs.TabLayout;
import com.roni.library.ui.toolbar.ToolbarManager;

/**
 * Group 2: App Navigation Coordinator.
 * Coordinates all 7 App Navigation components:
 *  5. Toolbar / Action Bar
 *  6. Toolbar Menu (Action items, search, overflow)
 *  7. Navigation Drawer
 *  8. Bottom Navigation
 *  9. Navigation Rail
 *  10. Tab Layout
 *  11. Floating Action Button (FAB)
 *
 * Each component can be used independently or bound into a coordinated navigation system.
 */
public class AppNavigationGroup {

    private final ToolbarManager toolbarManager;
    private final NavigationManager navigationManager;

    public AppNavigationGroup() {
        this(new ToolbarManager(), new NavigationManager());
    }

    public AppNavigationGroup(@NonNull ToolbarManager toolbarManager, @NonNull NavigationManager navigationManager) {
        this.toolbarManager = toolbarManager;
        this.navigationManager = navigationManager;
    }

    public ToolbarManager getToolbarManager() {
        return toolbarManager;
    }

    public NavigationManager getNavigationManager() {
        return navigationManager;
    }

    // Component 5: Toolbar / Action Bar
    public void setTitle(String title) {
        toolbarManager.setTitle(title);
    }

    public void setSubtitle(String subtitle) {
        toolbarManager.setSubtitle(subtitle);
    }

    // Component 6: Toolbar Menu & Action Items
    public void addToolbarAction(String id, String label, Runnable action) {
        toolbarManager.addAction(id, label, action);
    }

    public void addToolbarAction(String id, String label, int iconResId, Runnable action) {
        toolbarManager.addAction(id, label, iconResId, action);
    }

    public void removeToolbarAction(String id) {
        toolbarManager.removeAction(id);
    }

    // Component 7: Navigation Drawer
    public void addDrawerItem(String id, String title, int iconResId, Runnable action) {
        navigationManager.addDrawerItem(id, title, iconResId, action);
    }

    public void selectDrawerItem(String id) {
        navigationManager.selectDrawerItem(id);
    }

    public void openDrawer() {
        navigationManager.openDrawer();
    }

    public void closeDrawer() {
        navigationManager.closeDrawer();
    }

    // Component 8: Bottom Navigation
    public void addBottomNavTab(String id, String label, int iconResId) {
        navigationManager.addTab(id, label, iconResId);
    }

    public void selectBottomNavTab(String id) {
        navigationManager.selectTab(id);
    }

    public void setBadge(String tabId, int count) {
        navigationManager.setBadge(tabId, count);
    }

    // Bi-directional Drawer & Bottom Nav Linking
    public void linkDrawerAndBottomNav(String drawerItemId, String tabId) {
        navigationManager.linkDrawerAndBottomNav(drawerItemId, tabId);
    }

    // Component 9: Navigation Rail
    public void bindNavigationRail(@NonNull NavigationRailView railView, @NonNull Activity activity) {
        navigationManager.bindNavigationRail(railView, activity);
    }

    // Component 10: Tab Layout
    public void bindTabLayout(@NonNull TabLayout tabLayout, @NonNull Activity activity) {
        navigationManager.bindTabLayout(tabLayout, activity);
    }

    // Component 11: Floating Action Button (FAB)
    public void bindFab(@NonNull FloatingActionButton fab, @NonNull Activity activity, @Nullable Runnable onClick) {
        navigationManager.bindFloatingActionButton(fab, activity, onClick);
    }
}
