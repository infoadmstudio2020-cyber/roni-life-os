package com.roni.library.ui.manager;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.roni.library.ui.CustomBottomSheet;
import com.roni.library.ui.NativeRuntimeActivity;
import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.NativeToast;
import com.roni.library.ui.content.ContentContainerManager;
import com.roni.library.ui.content.ContentGroup;
import com.roni.library.ui.customization.CustomizationGroup;
import com.roni.library.ui.dialog.DialogManager;
import com.roni.library.ui.interaction.InteractionGroup;
import com.roni.library.ui.navigation.AppNavigationGroup;
import com.roni.library.ui.navigation.NavigationManager;
import com.roni.library.ui.overlay.LoadingOverlayManager;
import com.roni.library.ui.overlay.OverlayGroup;
import com.roni.library.ui.overlay.PopupMenuManager;
import com.roni.library.ui.overlay.SnackbarManager;
import com.roni.library.ui.system.SystemUIGroup;
import com.roni.library.ui.system.SystemUIManager;
import com.roni.library.ui.theme.UIThemeBridge;
import com.roni.library.ui.toolbar.ToolbarManager;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;
import java.util.Map;

/**
 * Centralized Native UI Management Architecture for NativeRuntimeUI.
 * Coordinates System UI, Navigation UI, Content Containers, Overlays,
 * State, Theme, and Layout Management with zero duplicate managers.
 */
public class NativeUIComponentManager {

    private final Activity activity;
    private final NativeRuntimeUIConfig config;

    // Component Managers (Single source of truth)
    private final SystemUIManager systemUIManager;
    private final ToolbarManager toolbarManager;
    private final NavigationManager navigationManager;
    private final ContentContainerManager contentContainerManager;
    private final DialogManager dialogManager;
    private final CustomBottomSheet bottomSheet;
    private final NativeToast toast;
    private WebViewHost webViewHost;
    private final LoadingOverlayManager loadingOverlayManager;
    private final SnackbarManager snackbarManager;
    private final PopupMenuManager popupMenuManager;
    private final UIStateManager stateManager;

    // 6 Functional Component Groups
    private final SystemUIGroup systemUIGroup;
    private final AppNavigationGroup appNavigationGroup;
    private final ContentGroup contentGroup;
    private final OverlayGroup overlayGroup;
    private final CustomizationGroup customizationGroup;
    private final InteractionGroup interactionGroup;

    // View Hierarchy Elements
    private DrawerLayout drawerLayout;
    private CoordinatorLayout coordinatorLayout;
    private AppBarLayout appBarLayout;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private NavigationView navigationView;
    private FloatingActionButton fab;
    private FrameLayout rootContentHolder;
    private NativeToast nativeToast;

    @Nullable
    private View rootView;
    public NativeUIComponentManager(@NonNull Activity activity, @NonNull NativeRuntimeUIConfig config) {
        this.activity = activity;
        this.config = config;

        this.systemUIManager = new SystemUIManager();
        this.toolbarManager = new ToolbarManager();
        this.navigationManager = new NavigationManager();
        this.contentContainerManager = new ContentContainerManager(activity);
        this.dialogManager = new DialogManager();
        this.bottomSheet = new CustomBottomSheet();
        this.toast = new NativeToast();
        this.loadingOverlayManager = new LoadingOverlayManager();
        this.snackbarManager = new SnackbarManager();
        this.popupMenuManager = new PopupMenuManager();
        this.stateManager = new UIStateManager();

        this.systemUIGroup = new SystemUIGroup(this.systemUIManager);
        this.appNavigationGroup = new AppNavigationGroup(this.toolbarManager, this.navigationManager);
        this.contentGroup = new ContentGroup(this.contentContainerManager);
        this.overlayGroup = new OverlayGroup(this.dialogManager, this.bottomSheet,
                this.popupMenuManager, this.snackbarManager, this.toast, this.loadingOverlayManager);
        this.customizationGroup = new CustomizationGroup();
        this.interactionGroup = new InteractionGroup(this.stateManager);
    }

    /**
     * Builds and renders the complete native UI layout hierarchy automatically
     * based on the provided configuration.
     */
    public View buildAndAttachUI() {
        // 1. Configure System UI
        if (config.isSystemUIEnabled()) {
            if (config.isEdgeToEdge()) {
                systemUIManager.applyEdgeToEdge(activity, true);
            }
            if (config.isFullscreen()) {
                systemUIManager.setFullscreen(activity, true);
            } else if (config.isImmersive()) {
                systemUIManager.setImmersiveMode(activity, true);
            } else {
                if (config.getStatusBarColor() != 0) {
                    systemUIManager.setStatusBar(activity, config.getStatusBarColor(), config.isLightStatusBar());
                }
                if (config.getNavBarColor() != 0) {
                    systemUIManager.setNavigationBar(activity, config.getNavBarColor(), config.isLightNavBar());
                }
            }
        }

        // Full-screen web host mode: no toolbar, no bottom navigation, no drawer.
        if (config.isFullScreenWebHost() && config.getWebSource() != null) {
            webViewHost = new WebViewHost(activity, config.getWebHostConfig());
            webViewHost.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webViewHost.loadSource(config.getWebSource());
            rootView = webViewHost;
            return rootView;
        }

        // 2. Create Layout Container Structure
        coordinatorLayout = new CoordinatorLayout(activity);
        coordinatorLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout verticalBox = new LinearLayout(activity);
        verticalBox.setOrientation(LinearLayout.VERTICAL);
        verticalBox.setLayoutParams(new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 3. Toolbar Setup
        if (config.isToolbarEnabled()) {
            appBarLayout = new AppBarLayout(activity);
            appBarLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            toolbar = new MaterialToolbar(activity);
            toolbar.setLayoutParams(new AppBarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            toolbar.setTitle(config.getToolbarTitle());
            if (!config.getToolbarSubtitle().isEmpty()) {
                toolbar.setSubtitle(config.getToolbarSubtitle());
            }

            if (config.isDrawerEnabled()) {
                toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size);
                toolbarManager.bindToolbar(toolbar, activity, () -> navigationManager.toggleDrawer());
            } else {
                toolbarManager.bindToolbar(toolbar, activity, () -> activity.onBackPressed());
            }

            appBarLayout.addView(toolbar);
            verticalBox.addView(appBarLayout);
        }

        // 4. Content Holder Setup
        rootContentHolder = contentContainerManager.getRootContainer();
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        rootContentHolder.setLayoutParams(contentParams);

        if (config.isWebViewEnabled()) {
            WebSource source = config.getWebSource();
            if (source != null) {
                contentContainerManager.showWebView(activity, source);
            } else {
                String initialUrl = config.getWebViewInitialUrl();
                if (initialUrl != null && !initialUrl.trim().isEmpty()) {
                    contentContainerManager.showWebView(activity, initialUrl);
                } else {
                    // Attach the container without navigating. Loading a placeholder here would
                    // leave "about:blank" in the history, so the first back press would land on
                    // a blank white page instead of the host's own navigation.
                    contentContainerManager.showWebView(activity, (WebSource) null);
                }
            }
            webViewHost = contentContainerManager.getWebViewHost();
        } else if (config.getCustomContentView() != null) {
            contentContainerManager.showCustomView(config.getCustomContentView());
        } else if (config.getContentLayoutResId() != 0) {
            View inflated = activity.getLayoutInflater().inflate(config.getContentLayoutResId(), rootContentHolder, false);
            contentContainerManager.showCustomView(inflated);
        }

        verticalBox.addView(rootContentHolder);

        // 5. Bottom Navigation Setup
        if (config.isBottomNavEnabled()) {
            bottomNav = new BottomNavigationView(activity);
            bottomNav.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            navigationManager.bindBottomNavigationView(bottomNav, activity, (tabId, pos) -> {
                stateManager.setActiveTabId(tabId);
                if (activity instanceof NativeRuntimeActivity) {
                    ((NativeRuntimeActivity) activity).onNavigationItemSelected(tabId, "bottom_nav");
                }
            });
            verticalBox.addView(bottomNav);
        }

        coordinatorLayout.addView(verticalBox);

        // 6. FAB Setup
        if (config.isFabEnabled()) {
            fab = new FloatingActionButton(activity);
            CoordinatorLayout.LayoutParams fabParams = new CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            fabParams.gravity = Gravity.BOTTOM | Gravity.END;
            fabParams.setMargins(48, 48, 48, config.isBottomNavEnabled() ? 180 : 48);
            fab.setLayoutParams(fabParams);
            fab.setImageResource(android.R.drawable.ic_input_add);
            navigationManager.bindFloatingActionButton(fab, activity, null);
            coordinatorLayout.addView(fab);
        }

        // 7. Drawer Layout Setup (Optional wrapper)
        View finalRootView;
        if (config.isDrawerEnabled()) {
            drawerLayout = new DrawerLayout(activity);
            drawerLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            drawerLayout.addView(coordinatorLayout);

            navigationView = new NavigationView(activity);
            DrawerLayout.LayoutParams navParams = new DrawerLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            navParams.gravity = GravityCompat.START;
            navigationView.setLayoutParams(navParams);

            if (!config.getDrawerHeaderTitle().isEmpty()) {
                LinearLayout header = new LinearLayout(activity);
                header.setOrientation(LinearLayout.VERTICAL);
                header.setPadding(48, 64, 48, 32);
                header.setBackgroundColor(Color.parseColor("#1E293B"));

                TextView tvHeader = new TextView(activity);
                tvHeader.setText(config.getDrawerHeaderTitle());
                tvHeader.setTextColor(Color.WHITE);
                tvHeader.setTextSize(18f);
                header.addView(tvHeader);

                if (!config.getDrawerHeaderSubtitle().isEmpty()) {
                    TextView tvSub = new TextView(activity);
                    tvSub.setText(config.getDrawerHeaderSubtitle());
                    tvSub.setTextColor(Color.parseColor("#94A3B8"));
                    tvSub.setTextSize(13f);
                    tvSub.setPadding(0, 8, 0, 0);
                    header.addView(tvSub);
                }
                navigationView.addHeaderView(header);
            }

            navigationManager.bindDrawerLayout(drawerLayout, navigationView, activity, (itemId) -> {
                if (activity instanceof NativeRuntimeActivity) {
                    ((NativeRuntimeActivity) activity).onNavigationItemSelected(itemId, "drawer");
                }
            });
            drawerLayout.addView(navigationView);
            finalRootView = drawerLayout;
        } else {
            finalRootView = coordinatorLayout;
        }

        // Apply theme tokens
        applyTheme(UIThemeBridge.getActiveThemeColors());

        rootView = finalRootView;
        return rootView;
    }



    /**
     * Applies dynamic theme colors to all attached native UI components.
     */
    public void applyTheme(Map<String, Object> themeColors) {
        if (themeColors == null || activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            try {
                Object primaryHex = themeColors.get("primaryColor");
                Object surfaceHex = themeColors.get("surfaceColor");
                Object backgroundHex = themeColors.get("backgroundColor");
                Object textHex = themeColors.get("textColor");
                Object accentHex = themeColors.get("accentColor");
                Object onPrimaryHex = themeColors.get("onPrimaryColor");

                if (primaryHex instanceof String) {
                    int pColor = Color.parseColor((String) primaryHex);
                    if (toolbar != null) {
                        toolbar.setBackgroundColor(pColor);
                    }
                    if (fab != null) {
                        fab.setBackgroundColor(pColor);
                    }
                }

                if (surfaceHex instanceof String && coordinatorLayout != null) {
                    coordinatorLayout.setBackgroundColor(Color.parseColor((String) surfaceHex));
                }

                if (backgroundHex instanceof String && rootView != null) {
                    rootView.setBackgroundColor(Color.parseColor((String) backgroundHex));
                }

                if (textHex instanceof String && toolbar != null) {
                    int tColor = Color.parseColor((String) textHex);
                    toolbar.setTitleTextColor(tColor);
                    toolbar.setSubtitleTextColor(tColor);
                }

                if (onPrimaryHex instanceof String && toolbar != null) {
                    toolbar.setTitleTextColor(Color.parseColor((String) onPrimaryHex));
                }

                if (accentHex instanceof String && bottomNav != null) {
                    int aColor = Color.parseColor((String) accentHex);
                    android.content.res.ColorStateList tint =
                            android.content.res.ColorStateList.valueOf(aColor);
                    bottomNav.setItemIconTintList(tint);
                    bottomNav.setItemTextColor(tint);
                }
                stateManager.notifyThemeChanged(themeColors);
            } catch (Throwable ignored) {}
        });
    }

    @Nullable
    public View getRootView() {
        return rootView;
    }


    /**
     * Handles hierarchical back navigation:
     * 1. If Drawer is open -> close it
     * 2. If Loading Overlay is active -> dismiss it
     * 3. If Bottom Sheet is visible -> dismiss it
     * 4. If Dialog is visible -> dismiss it
     * 5. If WebView can go back -> navigate back
     * 6. Returns false to allow Activity to finish / default behavior.
     */
    public boolean handleBackNavigation() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        if (loadingOverlayManager.isShowing()) {
            loadingOverlayManager.hide();
            return true;
        }
        if (bottomSheet.isVisible()) {
            bottomSheet.dismiss();
            return true;
        }
        if (dialogManager.isDialogShowing()) {
            dialogManager.cancel();
            return true;
        }
        WebViewHost host = getWebViewHost();
        if (host != null && host.canGoBack()) {
            host.goBack();
            return true;
        }
        return false;
    }

    // Dynamic Visibility Control
    public void setComponentVisibility(ComponentType type, boolean visible) {
        stateManager.setComponentVisibility(type, visible);
        if (activity == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            int v = visible ? View.VISIBLE : View.GONE;
            switch (type) {
                case TOOLBAR:
                    if (appBarLayout != null) appBarLayout.setVisibility(v);
                    toolbarManager.setVisibility(visible);
                    break;
                case BOTTOM_NAV:
                    if (bottomNav != null) bottomNav.setVisibility(v);
                    break;
                case FAB:
                    if (fab != null) {
                        if (visible) fab.show(); else fab.hide();
                    }
                    break;
                case STATUS_BAR:
                    systemUIManager.setFullscreen(activity, !visible);
                    break;
                case WEB_VIEW:
                    if (webViewHost != null) webViewHost.setVisibility(v);
                    break;
                default:
                    break;
            }
        });
    }

    // 6 Functional Component Groups
    public SystemUIGroup getSystemUIGroup() { return systemUIGroup; }
    public AppNavigationGroup getNavigationGroup() { return appNavigationGroup; }
    public ContentGroup getContentGroup() { return contentGroup; }
    public OverlayGroup getOverlayGroup() { return overlayGroup; }
    public CustomizationGroup getCustomizationGroup() { return customizationGroup; }
    public InteractionGroup getInteractionGroup() { return interactionGroup; }

    // Standalone Component Managers
    public SystemUIManager getSystemUIManager() { return systemUIManager; }
    public ToolbarManager getToolbarManager() { return toolbarManager; }
    public NavigationManager getNavigationManager() { return navigationManager; }
    public ContentContainerManager getContentContainerManager() { return contentContainerManager; }

    @Nullable
    public WebViewHost getWebViewHost() {
        if (webViewHost != null) return webViewHost;
        return contentContainerManager != null ? contentContainerManager.getWebViewHost() : null;
    }

    @NonNull
    public Activity getActivity() { return activity; }

    /** Replaces the content area without destroying the NativeRuntime shell. */
    public void showContent(@NonNull View view) {
        if (contentContainerManager != null && contentContainerManager.getRootContainer() != null) {
            contentContainerManager.showCustomView(view);
        } else if (rootView instanceof ViewGroup) {
            ((ViewGroup) rootView).addView(view);
        }
    }

    public void onResume() {
        WebViewHost host = getWebViewHost();
        if (host != null) host.onResume();
    }

    public void onPause() {
        WebViewHost host = getWebViewHost();
        if (host != null) host.onPause();
    }

    public void saveState(@Nullable Bundle outState) {
        WebViewHost host = getWebViewHost();
        if (host != null) host.saveState(outState);
    }

    public void restoreState(@Nullable Bundle inState) {
        WebViewHost host = getWebViewHost();
        if (host != null) host.restoreState(inState);
    }
    public DialogManager getDialogManager() { return dialogManager; }
    public CustomBottomSheet getBottomSheet() { return bottomSheet; }
    public NativeToast getToast() { return toast; }
    public LoadingOverlayManager getLoadingOverlayManager() { return loadingOverlayManager; }
    public SnackbarManager getSnackbarManager() { return snackbarManager; }
    public PopupMenuManager getPopupMenuManager() { return popupMenuManager; }
    public UIStateManager getStateManager() { return stateManager; }

    public DrawerLayout getDrawerLayout() { return drawerLayout; }
    public CoordinatorLayout getCoordinatorLayout() { return coordinatorLayout; }
    public MaterialToolbar getToolbar() { return toolbar; }
    public BottomNavigationView getBottomNav() { return bottomNav; }
    public NavigationView getNavigationView() { return navigationView; }
    public FloatingActionButton getFab() { return fab; }

    public synchronized void release() {
        WebViewHost host = webViewHost;
        webViewHost = null;
        if (host != null && (contentContainerManager == null
                || host != contentContainerManager.getWebViewHost())) {
            host.onDestroy();
        }
        if (contentContainerManager != null) {
            contentContainerManager.release();
        }
        if (toolbarManager != null) {
            toolbarManager.reset();
        }
        if (navigationManager != null) {
            navigationManager.reset();
        }
        if (dialogManager != null) {
            dialogManager.dismissCurrent();
        }
        if (loadingOverlayManager != null) {
            loadingOverlayManager.hide();
        }
        if (bottomSheet != null) {
            bottomSheet.dismiss();
        }
        if (stateManager != null) {
            stateManager.reset();
        }
        drawerLayout = null;
        coordinatorLayout = null;
        appBarLayout = null;
        toolbar = null;
        bottomNav = null;
        navigationView = null;
        fab = null;
        rootContentHolder = null;
        rootView = null;
    }
    public void showToast(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                android.widget.Toast.makeText(
                        activity,
                        message,
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    public void showToast(
            @Nullable String message,
            @Nullable NativeToast.Duration duration,
            @Nullable NativeToast.Type type) {

        if (message == null || message.trim().isEmpty()) {
            return;
        }

        if (nativeToast == null) {
            nativeToast = new NativeToast();
        }

        nativeToast.showNativeToast(
                activity,
                message,
                duration != null ? duration : NativeToast.Duration.SHORT,
                type != null ? type : NativeToast.Type.INFO
        );
    }

    public void showAlert(
            @Nullable String title,
            @Nullable String message,
            @Nullable Object config) {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        dialogManager.showAlert(
                title != null ? title : "",
                message != null ? message : "",
                null
        );
    }
}
