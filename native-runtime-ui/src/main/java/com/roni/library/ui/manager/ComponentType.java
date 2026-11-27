package com.roni.library.ui.manager;

/**
 * Enumeration of all 31 Native UI Components across 6 functional groups.
 * Consumers can use any component separately, in arbitrary combinations,
 * or as whole functional groups without any limit.
 */
public enum ComponentType {

    // ==================== Group 1: System UI ====================
    STATUS_BAR(Group.SYSTEM_UI, 1, "Status Bar"),
    NAVIGATION_BAR(Group.SYSTEM_UI, 2, "System Navigation Bar"),
    FULLSCREEN_IMMERSIVE(Group.SYSTEM_UI, 3, "Fullscreen / Immersive Mode"),
    EDGE_TO_EDGE(Group.SYSTEM_UI, 4, "Edge-to-Edge Layout"),

    // ==================== Group 2: App Navigation ====================
    TOOLBAR(Group.APP_NAVIGATION, 5, "Toolbar / Action Bar"),
    TOOLBAR_MENU(Group.APP_NAVIGATION, 6, "Toolbar Menu"),
    DRAWER(Group.APP_NAVIGATION, 7, "Navigation Drawer"),
    BOTTOM_NAV(Group.APP_NAVIGATION, 8, "Bottom Navigation"),
    NAVIGATION_RAIL(Group.APP_NAVIGATION, 9, "Navigation Rail"),
    TAB_LAYOUT(Group.APP_NAVIGATION, 10, "Tab Layout"),
    FAB(Group.APP_NAVIGATION, 11, "Floating Action Button"),

    // ==================== Group 3: Content ====================
    WEB_VIEW(Group.CONTENT, 12, "WebView Container"),
    CUSTOM_VIEW(Group.CONTENT, 13, "Custom View Container"),
    FRAGMENT_CONTAINER(Group.CONTENT, 14, "Fragment Container"),
    MULTI_CONTENT(Group.CONTENT, 15, "Multiple Content Containers"),

    // ==================== Group 4: Overlay ====================
    DIALOG(Group.OVERLAY, 16, "Dialog"),
    ALERT_DIALOG(Group.OVERLAY, 17, "Alert Dialog"),
    BOTTOM_SHEET(Group.OVERLAY, 18, "Bottom Sheet"),
    POPUP_MENU(Group.OVERLAY, 19, "Popup Menu"),
    SNACKBAR(Group.OVERLAY, 20, "Snackbar"),
    TOAST(Group.OVERLAY, 21, "Toast"),
    LOADING_OVERLAY(Group.OVERLAY, 22, "Loading Overlay"),

    // ==================== Group 5: UI Customization ====================
    THEME_INTEGRATION(Group.CUSTOMIZATION, 23, "Theme Integration"),
    BACKGROUND_COLOR(Group.CUSTOMIZATION, 24, "Background / Color"),
    ANIMATION(Group.CUSTOMIZATION, 25, "Animation"),
    VISIBILITY_CONTROLLER(Group.CUSTOMIZATION, 26, "Visibility Controller"),
    DYNAMIC_LAYOUT(Group.CUSTOMIZATION, 27, "Dynamic Layout Controller"),

    // ==================== Group 6: Interaction ====================
    BACK_NAVIGATION(Group.INTERACTION, 28, "Back Navigation"),
    TOUCH_CLICK_EVENTS(Group.INTERACTION, 29, "Touch / Click Events"),
    UI_STATE_MANAGEMENT(Group.INTERACTION, 30, "UI State Management"),
    COMPONENT_LIFECYCLE(Group.INTERACTION, 31, "Component Lifecycle Events"),

    // Aliases for full backward compatibility with previous code
    CUSTOM_CONTENT(Group.CONTENT, 13, "Custom View Container"),
    SYSTEM_UI(Group.SYSTEM_UI, 0, "System UI");

    public enum Group {
        SYSTEM_UI("System UI", 1, 4),
        APP_NAVIGATION("App Navigation", 5, 11),
        CONTENT("Content", 12, 15),
        OVERLAY("Overlay", 16, 22),
        CUSTOMIZATION("UI Customization", 23, 27),
        INTERACTION("Interaction", 28, 31);

        private final String displayName;
        private final int startId;
        private final int endId;

        Group(String displayName, int startId, int endId) {
            this.displayName = displayName;
            this.startId = startId;
            this.endId = endId;
        }

        public String getDisplayName() { return displayName; }
        public int getStartId() { return startId; }
        public int getEndId() { return endId; }
        public int getComponentCount() { return endId - startId + 1; }
    }

    private final Group group;
    private final int componentNumber;
    private final String displayName;

    ComponentType(Group group, int componentNumber, String displayName) {
        this.group = group;
        this.componentNumber = componentNumber;
        this.displayName = displayName;
    }

    public Group getGroup() { return group; }
    public int getComponentNumber() { return componentNumber; }
    public String getDisplayName() { return displayName; }
}
