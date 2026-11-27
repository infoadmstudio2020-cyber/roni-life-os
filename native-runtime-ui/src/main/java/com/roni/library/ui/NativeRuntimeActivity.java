package com.roni.library.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.roni.library.ui.host.NativeUIHost;
import com.roni.library.ui.manager.NativeUIComponentManager;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;
import com.roni.library.ui.content.ContentGroup;
import com.roni.library.ui.customization.CustomizationGroup;
import com.roni.library.ui.interaction.InteractionGroup;
import com.roni.library.ui.navigation.AppNavigationGroup;
import com.roni.library.ui.overlay.OverlayGroup;
import com.roni.library.ui.system.SystemUIGroup;

import java.util.List;
import java.util.Map;

/**
 * Flagship Consumer Entry Point for the NativeRuntimeUI framework.
 * Consumers extend this class and supply configuration via {@link #onConfigureUI(NativeRuntimeUIConfig.Builder)}.
 *
 * <p>NativeRuntimeUI automatically:
 * <ul>
 *     <li>Initializes SDK & UI subsystems</li>
 *     <li>Builds and inflates required Android Native Views</li>
 *     <li>Manages Layout, System UI, Toolbar, Drawer, Bottom Nav, WebView, and Overlays</li>
 *     <li>Coordinates Activity Lifecycles and Back Navigation</li>
 *     <li>Cleans up all references to prevent memory leaks</li>
 * </ul>
 */
public abstract class NativeRuntimeActivity extends AppCompatActivity {

    private NativeUIComponentManager componentManager;
    private NativeRuntimeUIConfig config;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Obtain Consumer Configuration first so the UI singleton is initialized
        //    with the real configuration (not the default shell).
        NativeRuntimeUIConfig.Builder builder = NativeRuntimeUIConfig.builder();
        this.config = onConfigureUI(builder);
        if (this.config == null) {
            this.config = builder.build();
        }

        // 2. Initialize NativeRuntimeUI Singleton with the consumer configuration.
        NativeRuntimeUI.getInstance().init(this, this.config);

        // 3. Initialize Centralized Component Manager
        this.componentManager = new NativeUIComponentManager(this, this.config);

        // 4. Automatically Build and Attach Native View Hierarchy
        View rootView = this.componentManager.buildAndAttachUI();
        NativeRuntimeUI.getInstance().getUiHost().setUIComponentManager(this.componentManager);
        NativeRuntimeUI.getInstance().getUiHost().setActiveRootView(rootView);
        super.setContentView(rootView);

        // 5. Notify Consumer UI is Ready
        onUIRendered(this.componentManager);
    }

    /**
     * Override to provide declarative UI configuration.
     *
     * @param builder pre-configured builder with sensible defaults
     * @return customized NativeRuntimeUIConfig
     */
    protected abstract NativeRuntimeUIConfig onConfigureUI(@NonNull NativeRuntimeUIConfig.Builder builder);

    /**
     * Called after the native UI hierarchy is constructed and attached.
     *
     * @param manager centralized component manager
     */
    protected void onUIRendered(@NonNull NativeUIComponentManager manager) {
        // Optional hook for subclasses
    }

    /**
     * Backward-compatible setContentView: if called, routes view into ContentContainer.
     */
    @Override
    public void setContentView(int layoutResID) {
        if (componentManager != null) {
            View view = getLayoutInflater().inflate(layoutResID, null, false);
            componentManager.showContent(view);
        } else {
            super.setContentView(layoutResID);
        }
    }

    @Override
    public void setContentView(View view) {
        if (componentManager != null) {
            componentManager.showContent(view);
        } else {
            super.setContentView(view);
        }
    }

    // ==================== Dynamic Runtime Management APIs ====================

    public NativeUIComponentManager getUIComponentManager() {
        return componentManager;
    }

    public NativeUIHost getUIHost() {
        return NativeRuntimeUI.getInstance().getUiHost();
    }

    public View getUIRootView() {
        return componentManager != null ? componentManager.getRootView() : null;
    }

    public NativeRuntimeUIConfig getUIConfig() {
        return config;
    }

    // ==================== Web application APIs ====================

    @Nullable
    public WebViewHost getWebViewHost() {
        return componentManager != null ? componentManager.getWebViewHost() : null;
    }

    /** Loads any supported web source into the active WebView host. */
    public void loadWebApp(@NonNull WebSource source) {
        WebViewHost host = getWebViewHost();
        if (host != null) {
            host.loadSource(source);
        } else if (componentManager != null) {
            componentManager.getContentContainerManager().showWebView(this, source);
        }
    }

    /** Loads a URL/path; asset and file URLs are auto-detected. */
    public void loadWebApp(@NonNull String url) {
        loadWebApp(WebSource.fromUrl(url));
    }

    public void reloadWebApp() {
        WebViewHost host = getWebViewHost();
        if (host != null) host.reload();
    }

    public void evaluateJavascript(@NonNull String script, @Nullable ValueCallback<String> callback) {
        WebViewHost host = getWebViewHost();
        if (host != null) host.evaluateJavascript(script, callback);
    }

    public void emitWebEvent(@NonNull String topic, @Nullable Map<String, Object> data) {
        WebViewHost host = getWebViewHost();
        if (host != null) host.emitEvent(topic, data);
    }

    // 6 Functional Component Groups
    public SystemUIGroup getSystemUIGroup() {
        return componentManager != null ? componentManager.getSystemUIGroup() : null;
    }

    public AppNavigationGroup getNavigationGroup() {
        return componentManager != null ? componentManager.getNavigationGroup() : null;
    }

    public ContentGroup getContentGroup() {
        return componentManager != null ? componentManager.getContentGroup() : null;
    }

    public OverlayGroup getOverlayGroup() {
        return componentManager != null ? componentManager.getOverlayGroup() : null;
    }

    public CustomizationGroup getCustomizationGroup() {
        return componentManager != null ? componentManager.getCustomizationGroup() : null;
    }

    public InteractionGroup getInteractionGroup() {
        return componentManager != null ? componentManager.getInteractionGroup() : null;
    }

    public void setToolbarTitle(String title) {
        if (componentManager != null) {
            componentManager.getToolbarManager().setTitle(title);
        }
    }

    public void setToolbarSubtitle(String subtitle) {
        if (componentManager != null) {
            componentManager.getToolbarManager().setSubtitle(subtitle);
        }
    }



    public void addToolbarAction(String id, String label, Runnable action) {
        if (componentManager != null) {
            componentManager.getToolbarManager().addAction(id, label, action);
        }
    }

    public void addToolbarAction(String id, String label, int iconResId, Runnable action) {
        if (componentManager != null) {
            componentManager.getToolbarManager().addAction(id, label, iconResId, action);
        }
    }

    public void addBottomNavTab(
            @NonNull String tabId,
            @NonNull String label,
            int iconResId) {

        if (componentManager != null
                && componentManager.getNavigationManager() != null) {

            componentManager.getNavigationManager()
                    .addTab(tabId, label, iconResId);
        }
    }

    public void addDrawerItem(String itemId, String label, int iconResId, Runnable action) {
        if (componentManager != null) {
            componentManager.getNavigationManager().addDrawerItem(itemId, label, iconResId, action);
        }
    }

    public void linkDrawerAndBottomNav(String drawerItemId, String bottomNavTabId) {
        if (componentManager != null) {
            componentManager.getNavigationManager().linkDrawerAndBottomNav(drawerItemId, bottomNavTabId);
        }
    }

    public void showToast(String message) {
        if (componentManager != null) {
            componentManager.showToast(message);
        }
    }

    public void showToast(String message, NativeToast.Duration duration, NativeToast.Type type) {
        if (componentManager != null) {
            componentManager.showToast(message, duration, type);
        }
    }

    public void showAlert(String title, String message) {
        if (componentManager != null) {
            componentManager.showAlert(title, message, null);
        }
    }
    public void showConfirm(
            String title,
            String message,
            Runnable onConfirm,
            Runnable onCancel) {

        if (componentManager != null) {
            componentManager.getDialogManager()
                    .showConfirm(title, message, onConfirm, onCancel);
        }
    }

    public void showBottomSheet(
            String title,
            List<NativeActionSheet.ActionItem> items) {

        if (componentManager == null || items == null) {
            return;
        }

        componentManager.getBottomSheet().showNativeBottomSheet(
                this,
                title != null ? title : "",
                items,
                null,
                null
        );
    }
    public void showLoading(String message) {
        if (componentManager != null) {
            componentManager.getLoadingOverlayManager()
                    .show(message);
        }
    }

    public void hideLoading() {
        if (componentManager != null) {
            componentManager.getLoadingOverlayManager()
                    .hide();
        }
    }
    public void applyTheme(Map<String, Object> themeColors) {
        if (componentManager != null) {
            componentManager.applyTheme(themeColors);
        }
    }
    public void onNavigationItemSelected(String itemId, String source) {
        // Hook for subclasses
    }

    @Override
    public void onBackPressed() {
        if (componentManager != null
                && componentManager.handleBackNavigation()) {
            return;
        }
        super.onBackPressed();
    }
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (componentManager != null) componentManager.onResume();
    }

    @Override
    protected void onPause() {
        if (componentManager != null) componentManager.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (componentManager != null) componentManager.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (componentManager != null) componentManager.restoreState(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (componentManager != null && componentManager.getWebViewHost() != null) {
            componentManager.getWebViewHost().handleActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        if (componentManager != null) {
            componentManager.release();
            componentManager = null;
        }

        NativeRuntimeUI ui = NativeRuntimeUI.getInstance();
        if (ui.isInitialized()) {
            ui.getUiHost().setUIComponentManager(null);
            ui.detachActivity(this);
        }

        super.onDestroy();
    }
}
