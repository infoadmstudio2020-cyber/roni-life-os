package com.roni.library.ui.handler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;

import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.bridge.WebBridge;
import com.roni.library.contracts.api.IApiCallback;
import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.contracts.ui.UICategory;
import com.roni.library.contracts.ui.UIComponentType;
import com.roni.library.ui.NativeActionSheet;
import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.NativeToast;
import com.roni.library.ui.host.NativeUIHost;
import com.roni.library.ui.manager.ComponentType;
import com.roni.library.ui.navigation.NavigationManager;
import com.roni.library.ui.overlay.PopupMenuManager;
import com.roni.library.ui.system.SystemUIManager;
import com.roni.library.ui.toolbar.ToolbarManager;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comprehensive native UI control surface exposed through the {@code ui.*}
 * bridge namespace.
 *
 * <p>Web applications can drive the toolbar, drawer, bottom navigation, FAB,
 * dialogs, bottom sheets, toasts, snackbars, loading overlays, popup menus,
 * system bars, themes and component visibility without writing any Java.</p>
 */
public class NativeUICommandHandler implements IApiHandler {

    public static final String[] SUPPORTED_ACTIONS = {
            // Overlays
            "showAlert", "showConfirm", "showToast", "showBottomSheet", "showActionSheet",
            "showLoading", "hideLoading", "showSnackbar", "showPopupMenu",
            "dismissDialog", "isDialogShowing", "dismissBottomSheet", "isBottomSheetVisible",
            "dismissToast", "isToastVisible", "isLoading", "dismissActionSheet",
            "getActionSheetState",
            // Toolbar
            "setToolbarTitle", "setToolbarSubtitle", "setToolbarVisible",
            "setNavigationIconVisible", "addToolbarAction", "removeToolbarAction",
            "clearToolbarActions", "getToolbarActions",
            // Drawer
            "openDrawer", "closeDrawer", "toggleDrawer", "isDrawerOpen",
            "selectDrawerItem", "getSelectedDrawerItem", "addDrawerItem", "removeDrawerItem",
            // Bottom navigation
            "selectTab", "addTab", "setBadge", "getBadge", "getTabs", "getSelectedTab",
            // FAB
            "setFabVisible", "isFabVisible",
            // Components
            "isComponentEnabled", "setComponentEnabled", "setComponentVisible",
            "getComponentState", "getComponentCount", "listComponents",
            // System UI / theme
            "setStatusBar", "setNavigationBar", "setFullscreen", "setImmersive",
            "setEdgeToEdge", "getSystemUIState", "applyTheme", "getTheme", "getConfig",
            // WebView convenience
            "reload", "getWebViewState", "setWebViewSource", "navigateBack", "openExternal"
    };

    private final NativeUIHost uiHost;

    public NativeUICommandHandler(NativeUIHost uiHost) {
        this.uiHost = uiHost != null ? uiHost : new NativeUIHost();
    }

    @Override
    public String getPath() {
        return "ui";
    }

    @Override
    public String getAction() {
        return "*";
    }

    public String getRoutePrefix() {
        return "ui";
    }

    public NativeUIHost getUiHost() {
        return uiHost;
    }

    public NativeToast getToast() {
        return uiHost.getToast();
    }

    public NativeActionSheet getActionSheet() {
        return uiHost.getActionSheet();
    }

    @Override
    public NativeApiResponse handleRequest(NativeApiRequest request) {
        if (request == null) {
            return NativeApiResponse.error("unknown",
                    new NativeApiError("INVALID_REQUEST", "Request is null"));
        }

        String action = request.getAction();
        String reqId = request.getRequestId();
        if (action == null || action.trim().isEmpty()) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("MISSING_ACTION", "Action cannot be empty"));
        }

        action = normalizeAction(action);
        Activity activity = uiHost.getActivity();

        try {
            switch (action) {
                // ==================== Overlays ====================
                case "showAlert": {
                    String title = request.getStringParam("title", "Alert");
                    String message = request.getStringParam("message", "");
                    if (activity != null) {
                        uiHost.getDialogManager().showNativeDialog(activity, title, message,
                                "OK", null, true, null, null);
                    } else {
                        uiHost.getDialogManager().showAlert(title, message, null);
                    }
                    return success(reqId, "shown", true);
                }

                case "showConfirm": {
                    String title = request.getStringParam("title", "Confirm");
                    String message = request.getStringParam("message", "");
                    String confirmText = request.getStringParam("confirmText", "OK");
                    String cancelText = request.getStringParam("cancelText", "Cancel");
                    if (activity != null) {
                        uiHost.getDialogManager().showNativeDialog(activity, title, message,
                                confirmText, cancelText, true,
                                () -> emitEvent("ui.confirm", "confirmed", true),
                                () -> emitEvent("ui.confirm", "confirmed", false));
                    } else {
                        uiHost.getDialogManager().showConfirm(title, message, null, null);
                    }
                    return success(reqId, "shown", true);
                }

                case "showToast": {
                    String message = request.getStringParam("message", "");
                    NativeToast.Duration duration = "long".equalsIgnoreCase(
                            request.getStringParam("duration", "short"))
                            ? NativeToast.Duration.LONG : NativeToast.Duration.SHORT;
                    NativeToast.Type type = parseToastType(request.getStringParam("type", "info"));
                    if (activity != null) {
                        uiHost.getToast().showNativeToast(activity, message, duration, type);
                    } else {
                        uiHost.getToast().show(message, duration, type);
                    }
                    return success(reqId, "shown", true);
                }

                case "showBottomSheet": {
                    String title = request.getStringParam("title", "");
                    List<NativeActionSheet.ActionItem> items =
                            parseActionItems(request.getParameters().get("items"));
                    if (activity != null) {
                        uiHost.getBottomSheet().showNativeBottomSheet(activity, title, items, null, null);
                    } else {
                        uiHost.getBottomSheet().setTitle(title);
                        uiHost.getBottomSheet().show();
                    }
                    return success(reqId, "shown", true);
                }

                case "showActionSheet": {
                    String title = request.getStringParam("title", "");
                    List<NativeActionSheet.ActionItem> items =
                            parseActionItems(request.getParameters().get("items"));
                    NativeActionSheet sheet = uiHost.getActionSheet();
                    sheet.clear();
                    sheet.setTitle(title);
                    for (NativeActionSheet.ActionItem item : items) {
                        sheet.addAction(item.getTitle(), item.isDestructive(), item.getAction());
                    }
                    sheet.show();
                    return success(reqId, "shown", true);
                }

                case "showLoading": {
                    String text = request.getStringParam("text", "Loading...");
                    boolean cancelable = booleanValue(request.getParameters().get("cancelable"), true);
                    if (activity != null) {
                        uiHost.getLoadingOverlayManager().showNativeLoading(activity, text, cancelable);
                    } else {
                        uiHost.getLoadingOverlayManager().show(text);
                    }
                    return success(reqId, "shown", true);
                }

                case "hideLoading": {
                    uiHost.getLoadingOverlayManager().hide();
                    return success(reqId, "hidden", true);
                }

                case "showSnackbar": {
                    String message = request.getStringParam("message", "");
                    String actionTitle = request.getStringParam("actionTitle", "");
                    int duration = "long".equalsIgnoreCase(request.getStringParam("duration", "short"))
                            ? com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            : com.google.android.material.snackbar.Snackbar.LENGTH_SHORT;
                    View anchor = uiHost.getRootView();
                    if (anchor == null && uiHost.getUIComponentManager() != null) {
                        anchor = uiHost.getUIComponentManager().getRootView();
                    }
                    if (anchor != null && uiHost.getUIComponentManager() != null) {
                        uiHost.getUIComponentManager().getSnackbarManager().show(
                                anchor, message, duration,
                                actionTitle.isEmpty() ? null : actionTitle,
                                actionTitle.isEmpty() ? null
                                        : () -> emitEvent("ui.snackbarAction", "actionTitle", actionTitle));
                    } else {
                        uiHost.getToast().show(message);
                    }
                    return success(reqId, "shown", true);
                }

                case "showPopupMenu": {
                    View anchor = uiHost.getRootView();
                    if (anchor == null && uiHost.getUIComponentManager() != null) {
                        anchor = uiHost.getUIComponentManager().getRootView();
                    }
                    if (anchor == null || uiHost.getUIComponentManager() == null) {
                        return success(reqId, "shown", false);
                    }
                    List<PopupMenuManager.PopupMenuItem> items = new ArrayList<>();
                    Object raw = request.getParameters().get("items");
                    if (raw instanceof List) {
                        int index = 0;
                        for (Object value : (List<?>) raw) {
                            final int itemIndex = index++;
                            if (value instanceof Map) {
                                Map<?, ?> map = (Map<?, ?>) value;
                                String title = stringValue(map.get("title"),
                                        stringValue(map.get("label"), ""));
                                int id = (int) longValue(map.get("id"), itemIndex + 1);
                                items.add(new PopupMenuManager.PopupMenuItem(id, title,
                                        () -> emitEvent("ui.popupItemSelected",
                                                "title", title, "index", itemIndex)));
                            }
                        }
                    }
                    uiHost.getUIComponentManager().getPopupMenuManager()
                            .showPopupMenu(anchor.getContext(), anchor, items);
                    return success(reqId, "shown", true);
                }

                case "dismissDialog": {
                    uiHost.getDialogManager().dismissCurrent();
                    return success(reqId, "dismissed", true);
                }

                case "isDialogShowing": {
                    return success(reqId, "showing", uiHost.getDialogManager().isDialogShowing());
                }

                case "dismissBottomSheet": {
                    uiHost.getBottomSheet().dismiss();
                    return success(reqId, "dismissed", true);
                }

                case "isBottomSheetVisible": {
                    return success(reqId, "visible", uiHost.getBottomSheet().isVisible());
                }

                case "dismissToast": {
                    uiHost.getToast().dismiss();
                    return success(reqId, "dismissed", true);
                }

                case "isToastVisible": {
                    return success(reqId, "visible", uiHost.getToast().isVisible());
                }

                case "isLoading": {
                    return success(reqId, "showing", uiHost.getLoadingOverlayManager().isShowing());
                }

                case "dismissActionSheet": {
                    uiHost.getActionSheet().dismiss();
                    uiHost.getBottomSheet().dismiss();
                    return success(reqId, "dismissed", true);
                }

                case "getActionSheetState": {
                    return success(reqId, "visible", uiHost.getActionSheet().isVisible());
                }

                // ==================== Toolbar ====================
                case "setToolbarTitle": {
                    String title = request.getStringParam("title", "");
                    uiHost.getToolbarManager().setTitle(title);
                    return success(reqId, "title", title);
                }

                case "setToolbarSubtitle": {
                    String subtitle = request.getStringParam("subtitle", "");
                    uiHost.getToolbarManager().setSubtitle(subtitle);
                    return success(reqId, "subtitle", subtitle);
                }

                case "setToolbarVisible": {
                    boolean visible = booleanValue(request.getParameters().get("visible"), true);
                    uiHost.getToolbarManager().setVisibility(visible);
                    return success(reqId, "visible", visible);
                }

                case "setNavigationIconVisible": {
                    boolean visible = booleanValue(request.getParameters().get("visible"), true);
                    uiHost.getToolbarManager().setNavigationIconVisible(visible);
                    return success(reqId, "visible", visible);
                }

                case "addToolbarAction": {
                    String id = request.getStringParam("id", "action_" + System.currentTimeMillis());
                    String label = request.getStringParam("label", id);
                    uiHost.getToolbarManager().addAction(id, label,
                            () -> emitEvent("ui.toolbarAction", "id", id, "label", label));
                    return success(reqId, "id", id);
                }

                case "removeToolbarAction": {
                    String id = request.getStringParam("id", "");
                    uiHost.getToolbarManager().removeAction(id);
                    return success(reqId, "removed", true);
                }

                case "clearToolbarActions": {
                    uiHost.getToolbarManager().clearActions();
                    return success(reqId, "cleared", true);
                }

                case "getToolbarActions": {
                    List<Map<String, Object>> actions = new ArrayList<>();

                    for (ToolbarManager.ActionButton toolbarAction
                            : uiHost.getToolbarManager().getActions()) {

                        Map<String, Object> item = new HashMap<>();
                        item.put("id", toolbarAction.getId());
                        item.put("label", toolbarAction.getLabel());

                        actions.add(item);
                    }

                    return NativeApiResponse.success(
                            reqId,
                            wrap("actions", actions)
                    );
                }

                // ==================== Drawer ====================
                case "openDrawer": {
                    uiHost.getNavigationManager().openDrawer();
                    return success(reqId, "open", true);
                }

                case "closeDrawer": {
                    uiHost.getNavigationManager().closeDrawer();
                    return success(reqId, "open", false);
                }

                case "toggleDrawer": {
                    uiHost.getNavigationManager().toggleDrawer();
                    return success(reqId, "open", uiHost.getNavigationManager().isDrawerOpen());
                }

                case "isDrawerOpen": {
                    return success(reqId, "open", uiHost.getNavigationManager().isDrawerOpen());
                }

                case "selectDrawerItem": {
                    String id = request.getStringParam("id", "");
                    uiHost.getNavigationManager().selectDrawerItem(id);
                    return success(reqId, "selected", id);
                }

                case "getSelectedDrawerItem": {
                    return success(reqId, "id",
                            uiHost.getNavigationManager().getSelectedDrawerItemId());
                }

                case "addDrawerItem": {
                    String id = request.getStringParam("id", "drawer_" + System.currentTimeMillis());
                    String title = request.getStringParam("title", id);
                    uiHost.getNavigationManager().addDrawerItem(id, title, 0,
                            () -> emitEvent("ui.drawerItemSelected", "id", id, "title", title));
                    return success(reqId, "id", id);
                }

                case "removeDrawerItem": {
                    String id = request.getStringParam("id", "");
                    uiHost.getNavigationManager().removeDrawerItem(id);
                    return success(reqId, "removed", true);
                }

                // ==================== Bottom navigation ====================
                case "selectTab": {
                    String tabId = request.getStringParam("tabId", "");
                    uiHost.getNavigationManager().selectTab(tabId);
                    Map<String, Object> data = new HashMap<>();
                    data.put("selectedTab", uiHost.getNavigationManager().getSelectedTab());
                    return NativeApiResponse.success(reqId, data);
                }

                case "addTab": {
                    String tabId = request.getStringParam("tabId", "tab_" + System.currentTimeMillis());
                    String label = request.getStringParam("label", tabId);
                    uiHost.getNavigationManager().addTab(tabId, label);
                    return success(reqId, "tabId", tabId);
                }

                case "setBadge": {
                    String tabId = request.getStringParam("tabId", "");
                    int count = (int) longValue(request.getParameters().get("count"), 0);
                    uiHost.getNavigationManager().setBadge(tabId, count);
                    return success(reqId, "count", count);
                }

                case "getBadge": {
                    String tabId = request.getStringParam("tabId", "");
                    return success(reqId, "count", uiHost.getNavigationManager().getBadge(tabId));
                }

                case "getTabs": {
                    List<Map<String, Object>> tabs = new ArrayList<>();
                    for (NavigationManager.TabItem tab : uiHost.getNavigationManager().getTabs()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", tab.getTabId());
                        item.put("label", tab.getLabel());
                        item.put("badge", uiHost.getNavigationManager().getBadge(tab.getTabId()));
                        tabs.add(item);
                    }
                    return NativeApiResponse.success(reqId, wrap("tabs", tabs));
                }

                case "getSelectedTab": {
                    return success(reqId, "tabId",
                            uiHost.getNavigationManager().getSelectedTab());
                }

                // ==================== FAB ====================
                case "setFabVisible": {
                    boolean visible = booleanValue(request.getParameters().get("visible"), true);
                    if (uiHost.getUIComponentManager() != null) {
                        uiHost.getUIComponentManager().setComponentVisibility(ComponentType.FAB, visible);
                        if (uiHost.getUIComponentManager().getFab() != null) {
                            uiHost.getUIComponentManager().getFab()
                                    .setVisibility(visible ? View.VISIBLE : View.GONE);
                        }
                    }
                    return success(reqId, "visible", visible);
                }

                case "isFabVisible": {
                    boolean visible = uiHost.getUIComponentManager() != null
                            && uiHost.getUIComponentManager().getFab() != null
                            && uiHost.getUIComponentManager().getFab().getVisibility() == View.VISIBLE;
                    return success(reqId, "visible", visible);
                }

                // ==================== Components ====================
                case "isComponentEnabled": {
                    String category = request.getStringParam("category", "");
                    String key = request.getStringParam("key", "");
                    boolean enabled = false;
                    UIComponentType type = findComponentType(category, key);
                    if (type != null && uiHost.getConfigurationManager() != null) {
                        enabled = uiHost.getConfigurationManager().isComponentEnabled(type);
                    }
                    return success(reqId, "enabled", enabled);
                }

                case "setComponentEnabled": {
                    String category = request.getStringParam("category", "");
                    String key = request.getStringParam("key", "");
                    boolean enabled = booleanValue(request.getParameters().get("enabled"), true);
                    UIComponentType type = findComponentType(category, key);
                    if (type != null && uiHost.getConfigurationManager() != null) {
                        uiHost.getConfigurationManager().setComponentOverride(type, enabled);
                    }
                    return success(reqId, "enabled", enabled);
                }

                case "setComponentVisible": {
                    String category = request.getStringParam("category", "");
                    String key = request.getStringParam("key", "");
                    boolean visible = booleanValue(request.getParameters().get("visible"), true);
                    UIComponentType type = findComponentType(category, key);
                    if (type != null) {
                        uiHost.getVisibilityManager().setVisible(type, visible);
                    }
                    ComponentType componentType = mapComponentType(key);
                    boolean applied = false;
                    if (componentType != null && uiHost.getUIComponentManager() != null) {
                        uiHost.getUIComponentManager().setComponentVisibility(componentType, visible);
                        applied = true;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("visible", visible);
                    data.put("applied", applied);
                    if (!applied) {
                        data.put("reason", "component not attached");
                    }
                    return NativeApiResponse.success(reqId, data);
                }

                case "getComponentState": {
                    String category = request.getStringParam("category", "");
                    String key = request.getStringParam("key", "");
                    UIComponentType type = findComponentType(category, key);
                    Map<String, Object> data = new HashMap<>();
                    if (type != null) {
                        data.put("key", type.getKey());
                        data.put("category", type.getCategory().name());
                        data.put("enabled", uiHost.getConfigurationManager() != null
                                && uiHost.getConfigurationManager().isComponentEnabled(type));
                        data.put("visible", uiHost.getVisibilityManager() != null
                                && uiHost.getVisibilityManager().isVisible(type));
                    }
                    return NativeApiResponse.success(reqId, data);
                }

                case "getComponentCount": {
                    int count = uiHost.getRegistry() != null
                            ? uiHost.getRegistry().getComponentCount() : 0;
                    return success(reqId, "count", count);
                }

                case "listComponents": {
                    List<Map<String, Object>> components = new ArrayList<>();
                    if (uiHost.getRegistry() != null) {
                        for (IUIComponentDescriptor descriptor : uiHost.getRegistry().getAllDescriptors()) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("key", descriptor.getType().getKey());
                            item.put("name", descriptor.getName());
                            item.put("category", descriptor.getCategory().name());
                            item.put("enabled", uiHost.getConfigurationManager() != null
                                    && uiHost.getConfigurationManager()
                                    .isComponentEnabled(descriptor.getType()));
                            item.put("visible", uiHost.getVisibilityManager() != null
                                    && uiHost.getVisibilityManager()
                                    .isVisible(descriptor.getType()));
                            components.add(item);
                        }
                    }
                    return NativeApiResponse.success(reqId, wrap("components", components));
                }

                // ==================== System UI / theme ====================
                case "setStatusBar": {
                    if (activity == null || uiHost.getUIComponentManager() == null) {
                        return success(reqId, "applied", false);
                    }
                    int color = parseColor(request.getStringParam("color", "#000000"));
                    boolean light = booleanValue(request.getParameters().get("lightIcons"), false);
                    uiHost.getUIComponentManager().getSystemUIManager()
                            .setStatusBar(activity, color, light);
                    return success(reqId, "color", color);
                }

                case "setNavigationBar": {
                    if (activity == null || uiHost.getUIComponentManager() == null) {
                        return success(reqId, "applied", false);
                    }
                    int color = parseColor(request.getStringParam("color", "#000000"));
                    boolean light = booleanValue(request.getParameters().get("lightButtons"), false);
                    uiHost.getUIComponentManager().getSystemUIManager()
                            .setNavigationBar(activity, color, light);
                    return success(reqId, "color", color);
                }

                case "setFullscreen": {
                    if (activity == null || uiHost.getUIComponentManager() == null) {
                        return success(reqId, "applied", false);
                    }
                    boolean enabled = booleanValue(request.getParameters().get("enabled"), true);
                    uiHost.getUIComponentManager().getSystemUIManager()
                            .setFullscreen(activity, enabled);
                    return success(reqId, "enabled", enabled);
                }

                case "setImmersive": {
                    if (activity == null || uiHost.getUIComponentManager() == null) {
                        return success(reqId, "applied", false);
                    }
                    boolean enabled = booleanValue(request.getParameters().get("enabled"), true);
                    uiHost.getUIComponentManager().getSystemUIManager()
                            .setImmersiveMode(activity, enabled);
                    return success(reqId, "enabled", enabled);
                }

                case "setEdgeToEdge": {
                    if (activity == null || uiHost.getUIComponentManager() == null) {
                        return success(reqId, "applied", false);
                    }
                    boolean enabled = booleanValue(request.getParameters().get("enabled"), true);
                    uiHost.getUIComponentManager().getSystemUIManager()
                            .applyEdgeToEdge(activity, enabled);
                    return success(reqId, "enabled", enabled);
                }

                case "getSystemUIState": {
                    Map<String, Object> data = new HashMap<>();
                    if (uiHost.getUIComponentManager() != null) {
                        SystemUIManager systemUI = uiHost.getUIComponentManager().getSystemUIManager();
                        data.put("fullscreen", systemUI.isFullscreen());
                        data.put("immersive", systemUI.isImmersive());
                        data.put("edgeToEdge", systemUI.isEdgeToEdge());
                        data.put("statusBarColor", systemUI.getStatusBarColor());
                        data.put("navBarColor", systemUI.getNavBarColor());
                        data.put("lightStatusBar", systemUI.isLightStatusBar());
                        data.put("lightNavBar", systemUI.isLightNavBar());
                    }
                    return NativeApiResponse.success(reqId, data);
                }

                case "applyTheme": {
                    Map<String, Object> theme = new HashMap<>();
                    for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                        theme.put(entry.getKey(), entry.getValue());
                    }
                    if (uiHost.getUIComponentManager() != null) {
                        uiHost.getUIComponentManager().applyTheme(theme);
                    }
                    return success(reqId, "applied", true);
                }

                case "getTheme": {
                    NativeRuntimeUIConfig config = uiHost.getConfiguration();
                    Map<String, Object> data = new HashMap<>();
                    if (config != null) {
                        data.put("themeMode", config.getThemeMode());
                        data.put("primaryColor", config.getPrimaryColor());
                        data.put("backgroundColor", config.getBackgroundColor());
                        data.put("statusBarColor", config.getStatusBarColor());
                        data.put("navBarColor", config.getNavBarColor());
                        data.put("lightStatusBar", config.isLightStatusBar());
                        data.put("lightNavBar", config.isLightNavBar());
                    }
                    return NativeApiResponse.success(reqId, data);
                }

                case "getConfig": {
                    NativeRuntimeUIConfig config = uiHost.getConfiguration();
                    Map<String, Object> data = new HashMap<>();
                    if (config != null) {
                        data.put("toolbarEnabled", config.isToolbarEnabled());
                        data.put("drawerEnabled", config.isDrawerEnabled());
                        data.put("bottomNavEnabled", config.isBottomNavEnabled());
                        data.put("webViewEnabled", config.isWebViewEnabled());
                        data.put("fabEnabled", config.isFabEnabled());
                        data.put("systemUIEnabled", config.isSystemUIEnabled());
                        data.put("fullScreenWebHost", config.isFullScreenWebHost());
                        data.put("toolbarTitle", config.getToolbarTitle());
                        data.put("toolbarSubtitle", config.getToolbarSubtitle());
                        data.put("drawerHeaderTitle", config.getDrawerHeaderTitle());
                        data.put("drawerHeaderSubtitle", config.getDrawerHeaderSubtitle());
                        data.put("webViewInitialUrl", config.getWebViewInitialUrl());
                        data.put("themeMode", config.getThemeMode());
                        data.put("primaryColor", config.getPrimaryColor());
                        data.put("backgroundColor", config.getBackgroundColor());
                        data.put("statusBarColor", config.getStatusBarColor());
                        data.put("navBarColor", config.getNavBarColor());
                        data.put("lightStatusBar", config.isLightStatusBar());
                        data.put("lightNavBar", config.isLightNavBar());
                        data.put("fullscreen", config.isFullscreen());
                        data.put("immersive", config.isImmersive());
                        data.put("edgeToEdge", config.isEdgeToEdge());
                    }
                    return NativeApiResponse.success(reqId, data);
                }

                // ==================== WebView convenience ====================
                case "reload": {
                    WebViewHost host = getWebViewHost();
                    if (host != null) host.reload();
                    return success(reqId, "reloaded", host != null);
                }

                case "getWebViewState": {
                    WebViewHost host = getWebViewHost();
                    Map<String, Object> data = new HashMap<>();
                    data.put("url", host != null ? host.getCurrentUrl() : "");
                    data.put("canGoBack", host != null && host.canGoBack());
                    data.put("canGoForward", host != null && host.canGoForward());
                    return NativeApiResponse.success(reqId, data);
                }

                case "setWebViewSource": {
                    WebSource source = parseWebSource(request);
                    WebViewHost host = getWebViewHost();
                    if (host != null && source != null) host.loadSource(source);
                    return success(reqId, "loaded", host != null && source != null);
                }

                case "navigateBack": {
                    if (activity != null) activity.onBackPressed();
                    return success(reqId, "navigated", activity != null);
                }

                case "openExternal": {
                    String url = request.getStringParam("url", "");
                    openExternal(url);
                    return success(reqId, "opened", !url.isEmpty());
                }

                default:
                    return NativeApiResponse.error(reqId,
                            new NativeApiError("UNKNOWN_ACTION", "Action not supported: " + action));
            }
        } catch (Throwable t) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("INTERNAL_ERROR", t.getMessage()));
        }
    }

    public void handle(NativeApiRequest request, IApiCallback callback) {
        NativeApiResponse response = handleRequest(request);
        if (callback != null) {
            if (response.isSuccess()) {
                callback.onResponse(response);
            } else {
                callback.onError(response.getError());
            }
        }
    }

    private String normalizeAction(String action) {
        String value = action.trim();
        if (value.startsWith("ui.")) return value.substring(3);
        if (value.startsWith("/ui/")) return value.substring(4);
        return value;
    }

    private NativeApiResponse success(String reqId, String key, Object value) {
        return NativeApiResponse.success(reqId, wrap(key, value));
    }

    private Map<String, Object> wrap(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }

    private NativeToast.Type parseToastType(String value) {
        if (value == null) return NativeToast.Type.INFO;
        switch (value.toLowerCase(Locale.US)) {
            case "success": return NativeToast.Type.SUCCESS;
            case "warning": return NativeToast.Type.WARNING;
            case "error": return NativeToast.Type.ERROR;
            default: return NativeToast.Type.INFO;
        }
    }

    private List<NativeActionSheet.ActionItem> parseActionItems(Object raw) {
        List<NativeActionSheet.ActionItem> items = new ArrayList<>();
        if (!(raw instanceof List)) return items;

        int index = 0;
        for (Object value : (List<?>) raw) {
            final int itemIndex = index++;
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                String title = stringValue(map.get("title"), stringValue(map.get("label"), ""));
                boolean destructive = Boolean.TRUE.equals(map.get("destructive"));
                items.add(new NativeActionSheet.ActionItem(title, destructive,
                        () -> emitEvent("ui.actionSelected", "title", title, "index", itemIndex)));
            } else if (value != null) {
                String title = String.valueOf(value);
                items.add(new NativeActionSheet.ActionItem(title, false,
                        () -> emitEvent("ui.actionSelected", "title", title, "index", itemIndex)));
            }
        }
        return items;
    }

    private void emitEvent(String topic, Object... keyValues) {
        try {
            WebBridge bridge = NativeWebBridgeSDK.getInstance().getBridge();
            if (bridge != null) {
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i + 1 < keyValues.length; i += 2) {
                    data.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
                }
                bridge.emitToWeb(topic, data);
            }
        } catch (Throwable ignored) {
        }
    }

    private UIComponentType findComponentType(String category, String key) {
        if (category == null || key == null) return null;
        try {
            UICategory cat = UICategory.valueOf(category);
            for (IUIComponentDescriptor descriptor : uiHost.getRegistry().getDescriptorsByCategory(cat)) {
                if (descriptor.getType().getKey().equalsIgnoreCase(key)) {
                    return descriptor.getType();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ComponentType mapComponentType(String key) {
        if (key == null) return null;
        try {
            return ComponentType.valueOf(key.toUpperCase(Locale.US));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private WebViewHost getWebViewHost() {
        return uiHost.getUIComponentManager() != null
                ? uiHost.getUIComponentManager().getWebViewHost() : null;
    }

    private WebSource parseWebSource(NativeApiRequest request) {
        String type = request.getStringParam("type", "remote").toLowerCase(Locale.US);
        String location = request.getStringParam("location", "");
        String entryPoint = request.getStringParam("entryPoint", "");
        WebSource source;
        switch (type) {
            case "asset": source = WebSource.asset(location); break;
            case "file": source = WebSource.file(location); break;
            case "zip": source = WebSource.zipAsset(location); break;
            case "zipfile": source = WebSource.zipFile(location); break;
            case "inline": source = WebSource.inlineHtml(request.getStringParam("html", "")); break;
            case "content": source = WebSource.contentUri(location); break;
            default: source = WebSource.remote(location); break;
        }
        if (!entryPoint.isEmpty()) source = source.withEntryPoint(entryPoint);
        return source;
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Activity activity = uiHost.getActivity();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (activity != null) {
                activity.startActivity(intent);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (uiHost.getUIComponentManager() != null) {
                    uiHost.getUIComponentManager().getActivity().startActivity(intent);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private int parseColor(String value) {
        try {
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return Color.BLACK;
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }
}
