package com.roni.library.ui.host;

import android.app.Activity;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.INativeUIHost;
import com.roni.library.contracts.ui.IUIComponentRegistry;
import com.roni.library.contracts.ui.IUICompositionManager;
import com.roni.library.contracts.ui.IUIConfigurationManager;
import com.roni.library.contracts.ui.IUIEventDispatcher;
import com.roni.library.contracts.ui.IUIRenderer;
import com.roni.library.contracts.ui.IUIStateManager;
import com.roni.library.contracts.ui.IUIVisibilityManager;
import com.roni.library.contracts.ui.UICategory;
import com.roni.library.contracts.ui.UIComponentType;
import com.roni.library.ui.CustomBottomSheet;
import com.roni.library.ui.NativeActionSheet;
import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.NativeToast;
import com.roni.library.ui.composition.UICompositionManager;
import com.roni.library.ui.config.UIConfigurationManager;
import com.roni.library.ui.dialog.DialogManager;
import com.roni.library.ui.event.UIEventDispatcher;
import com.roni.library.ui.manager.NativeUIComponentManager;
import com.roni.library.ui.navigation.NavigationManager;
import com.roni.library.ui.overlay.LoadingOverlayManager;
import com.roni.library.ui.registry.StandardUIComponentRegistryInitializer;
import com.roni.library.ui.registry.UIComponentRegistry;
import com.roni.library.ui.renderer.UIRenderer;
import com.roni.library.ui.state.UIStateManager;
import com.roni.library.ui.toolbar.ToolbarManager;
import com.roni.library.ui.visibility.UIVisibilityManager;
import java.util.Map;

/**
 * Aggregates UI managers for native dialogs, toolbars, bottom navigation, and overlays.
 * Implements INativeUIHost adhering to Layer 0 universal contracts.
 */
public class NativeUIHost implements INativeUIHost {

    private final DialogManager dialogManager;
    private final LoadingOverlayManager loadingOverlayManager;
    private final NavigationManager navigationManager;
    private final ToolbarManager toolbarManager;
    private final NativeToast toast;
    private final CustomBottomSheet bottomSheet;
    private final NativeActionSheet actionSheet;

    // Contracts subsystems
    private final IUIComponentRegistry registry;
    private final IUIConfigurationManager configurationManager;
    private final IUICompositionManager compositionManager;
    private final IUIVisibilityManager visibilityManager;
    private final IUIStateManager stateManager;
    private final IUIEventDispatcher eventDispatcher;
    private final IUIRenderer renderer;

    private volatile NativeUIComponentManager componentManager;
    private volatile View activeRootView;
    private volatile NativeRuntimeUIConfig configuration;
    private volatile String projectId;

    public NativeUIHost() {
        this.dialogManager = new DialogManager();
        this.loadingOverlayManager = new LoadingOverlayManager();
        this.navigationManager = new NavigationManager();
        this.toolbarManager = new ToolbarManager();
        this.toast = new NativeToast();
        this.bottomSheet = new CustomBottomSheet();
        this.actionSheet = new NativeActionSheet();

        // Initialize concrete registry implementation
        UIComponentRegistry componentRegistry = new UIComponentRegistry();

        // Register all standard UI components
        StandardUIComponentRegistryInitializer.initializeDefaults(componentRegistry);

        // Assign implementation to interface
        this.registry = componentRegistry;

        this.configurationManager = new UIConfigurationManager();
        this.compositionManager = new UICompositionManager();
        this.visibilityManager = new UIVisibilityManager(this.registry);
        this.stateManager = new UIStateManager();
        this.eventDispatcher = new UIEventDispatcher();
        this.renderer = new UIRenderer();
    }
    public DialogManager getDialogManager() {
        if (componentManager != null && componentManager.getDialogManager() != null) {
            return componentManager.getDialogManager();
        }
        return dialogManager;
    }

    public LoadingOverlayManager getLoadingOverlayManager() {
        if (componentManager != null && componentManager.getLoadingOverlayManager() != null) {
            return componentManager.getLoadingOverlayManager();
        }
        return loadingOverlayManager;
    }

    public NavigationManager getNavigationManager() {
        if (componentManager != null && componentManager.getNavigationManager() != null) {
            return componentManager.getNavigationManager();
        }
        return navigationManager;
    }

    public ToolbarManager getToolbarManager() {
        if (componentManager != null && componentManager.getToolbarManager() != null) {
            return componentManager.getToolbarManager();
        }
        return toolbarManager;
    }

    public NativeToast getToast() {
        if (componentManager != null && componentManager.getToast() != null) {
            return componentManager.getToast();
        }
        return toast;
    }

    public CustomBottomSheet getBottomSheet() {
        if (componentManager != null && componentManager.getBottomSheet() != null) {
            return componentManager.getBottomSheet();
        }
        return bottomSheet;
    }

    public NativeActionSheet getActionSheet() {
        return actionSheet;
    }

    @Nullable
    public Activity getActivity() {
        return componentManager != null ? componentManager.getActivity() : null;
    }

    public void setUIComponentManager(@Nullable NativeUIComponentManager manager) {
        this.componentManager = manager;
    }

    @Nullable
    public NativeUIComponentManager getUIComponentManager() {
        return componentManager;
    }

    public void setActiveRootView(@Nullable View view) {
        this.activeRootView = view;
    }

    @NonNull
    @Override
    public IUIComponentRegistry getRegistry() { return registry; }

    @NonNull
    @Override
    public IUIConfigurationManager getConfigurationManager() { return configurationManager; }

    @NonNull
    @Override
    public IUICompositionManager getCompositionManager() { return compositionManager; }

    @NonNull
    @Override
    public IUIVisibilityManager getVisibilityManager() { return visibilityManager; }

    @NonNull
    @Override
    public IUIStateManager getStateManager() { return stateManager; }

    @NonNull
    @Override
    public IUIEventDispatcher getEventDispatcher() { return eventDispatcher; }

    @NonNull
    @Override
    public IUIRenderer getRenderer() { return renderer; }

    @Nullable
    @Override
    public View getRootView() { return activeRootView; }

    @Nullable
    /**
     * @return the id of the Web Project currently hosted, or {@code null} for the legacy
     *         shared namespace
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Sets the project whose storage namespace the bridge should use.
     *
     * <p>Must be called before the first {@code storage.*} request of a project. Passing
     * {@code null} or a blank string keeps the legacy shared namespace, which is what
     * pre-scoping installs already use.</p>
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public NativeRuntimeUIConfig getConfiguration() { return configuration; }

    public synchronized void updateConfiguration(@NonNull NativeRuntimeUIConfig config) {
        this.configuration = config;
        configurationManager.setGlobalDefault(config.getGlobalDefault());
        for (Map.Entry<UICategory, Boolean> entry : config.getCategoryDefaults().entrySet()) {
            configurationManager.setCategoryDefault(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UIComponentType, Boolean> entry : config.getComponentOverrides().entrySet()) {
            configurationManager.setComponentOverride(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void reset() {
        dialogManager.dismissCurrent();
        loadingOverlayManager.hide();
        navigationManager.clear();
        toolbarManager.clearActions();
        toast.dismiss();
        bottomSheet.dismiss();
        actionSheet.clear();
        componentManager = null;
        activeRootView = null;
    }
}
