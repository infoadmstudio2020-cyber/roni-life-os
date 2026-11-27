package com.roni.library.ui.registry;

import android.content.Context;
import androidx.annotation.NonNull;

import com.roni.library.contracts.ui.Animation;
import com.roni.library.contracts.ui.AppBar;
import com.roni.library.contracts.ui.Content;
import com.roni.library.contracts.ui.Dialog;
import com.roni.library.contracts.ui.Feedback;
import com.roni.library.contracts.ui.IUIComponent;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.contracts.ui.Input;
import com.roni.library.contracts.ui.Lifecycle;
import com.roni.library.contracts.ui.Navigation;
import com.roni.library.contracts.ui.Overlay;
import com.roni.library.contracts.ui.SystemUI;
import com.roni.library.contracts.ui.Theme;
import com.roni.library.contracts.ui.UICategory;
import com.roni.library.contracts.ui.UIComponentType;
import com.roni.library.contracts.ui.WebHost;
import com.roni.library.ui.components.animation.ComponentAnimationComponent;
import com.roni.library.ui.components.animation.EnterAnimationComponent;
import com.roni.library.ui.components.animation.ExitAnimationComponent;
import com.roni.library.ui.components.animation.LoadingAnimationComponent;
import com.roni.library.ui.components.animation.MotionControllerComponent;
import com.roni.library.ui.components.animation.SharedElementTransitionComponent;
import com.roni.library.ui.components.animation.TransitionAnimationComponent;
import com.roni.library.ui.components.appbar.ActionButtonComponent;
import com.roni.library.ui.components.appbar.ActionMenuComponent;
import com.roni.library.ui.components.appbar.BackButtonComponent;
import com.roni.library.ui.components.appbar.CenterAlignedTopAppBarComponent;
import com.roni.library.ui.components.appbar.CollapsingToolbarComponent;
import com.roni.library.ui.components.appbar.OverflowMenuComponent;
import com.roni.library.ui.components.appbar.SearchBarComponent;
import com.roni.library.ui.components.appbar.SubtitleComponent;
import com.roni.library.ui.components.appbar.TitleComponent;
import com.roni.library.ui.components.appbar.ToolbarComponent;
import com.roni.library.ui.components.appbar.TopAppBarComponent;
import com.roni.library.ui.components.content.ContentContainerComponent;
import com.roni.library.ui.components.content.CustomViewContainerComponent;
import com.roni.library.ui.components.content.FragmentContainerComponent;
import com.roni.library.ui.components.content.FrameContainerComponent;
import com.roni.library.ui.components.content.LinearContainerComponent;
import com.roni.library.ui.components.content.NestedScrollContainerComponent;
import com.roni.library.ui.components.content.RootContainerComponent;
import com.roni.library.ui.components.content.SafeAreaContainerComponent;
import com.roni.library.ui.components.content.ScrollContainerComponent;
import com.roni.library.ui.components.content.WebContentContainerComponent;
import com.roni.library.ui.components.dialog.ActionSheetComponent;
import com.roni.library.ui.components.dialog.AlertDialogComponent;
import com.roni.library.ui.components.dialog.BottomSheetDialogComponent;
import com.roni.library.ui.components.dialog.ConfirmationDialogComponent;
import com.roni.library.ui.components.dialog.CustomBottomSheetComponent;
import com.roni.library.ui.components.dialog.CustomDialogComponent;
import com.roni.library.ui.components.dialog.FullScreenDialogComponent;
import com.roni.library.ui.components.dialog.MultiChoiceDialogComponent;
import com.roni.library.ui.components.dialog.PermissionDialogComponent;
import com.roni.library.ui.components.dialog.SingleChoiceDialogComponent;
import com.roni.library.ui.components.feedback.CircularProgressComponent;
import com.roni.library.ui.components.feedback.EmptyStateComponent;
import com.roni.library.ui.components.feedback.ErrorMessageComponent;
import com.roni.library.ui.components.feedback.ErrorStateComponent;
import com.roni.library.ui.components.feedback.LinearProgressComponent;
import com.roni.library.ui.components.feedback.LoadingIndicatorComponent;
import com.roni.library.ui.components.feedback.ProgressIndicatorComponent;
import com.roni.library.ui.components.feedback.SnackbarComponent;
import com.roni.library.ui.components.feedback.SuccessMessageComponent;
import com.roni.library.ui.components.feedback.ToastComponent;
import com.roni.library.ui.components.feedback.WarningMessageComponent;
import com.roni.library.ui.components.input.CheckboxComponent;
import com.roni.library.ui.components.input.DatePickerComponent;
import com.roni.library.ui.components.input.DropdownComponent;
import com.roni.library.ui.components.input.MultilineTextFieldComponent;
import com.roni.library.ui.components.input.NumberInputComponent;
import com.roni.library.ui.components.input.PasswordFieldComponent;
import com.roni.library.ui.components.input.RadioButtonComponent;
import com.roni.library.ui.components.input.RadioGroupComponent;
import com.roni.library.ui.components.input.RangeSliderComponent;
import com.roni.library.ui.components.input.SearchInputComponent;
import com.roni.library.ui.components.input.SliderComponent;
import com.roni.library.ui.components.input.SwitchComponent;
import com.roni.library.ui.components.input.TextFieldComponent;
import com.roni.library.ui.components.input.TimePickerComponent;
import com.roni.library.ui.components.lifecycle.ComponentLifecycleComponent;
import com.roni.library.ui.components.lifecycle.ConfigurationChangeHandlerComponent;
import com.roni.library.ui.components.lifecycle.ScreenStateControllerComponent;
import com.roni.library.ui.components.lifecycle.StateRestorationComponent;
import com.roni.library.ui.components.lifecycle.UIStateControllerComponent;
import com.roni.library.ui.components.lifecycle.VisibilityControllerComponent;
import com.roni.library.ui.components.navigation.BackNavigationComponent;
import com.roni.library.ui.components.navigation.BottomNavigationComponent;
import com.roni.library.ui.components.navigation.NavigationBadgeComponent;
import com.roni.library.ui.components.navigation.NavigationControllerComponent;
import com.roni.library.ui.components.navigation.NavigationDrawerComponent;
import com.roni.library.ui.components.navigation.NavigationIndicatorComponent;
import com.roni.library.ui.components.navigation.NavigationRailComponent;
import com.roni.library.ui.components.navigation.NavigationStackComponent;
import com.roni.library.ui.components.navigation.TabLayoutComponent;
import com.roni.library.ui.components.navigation.TabNavigationComponent;
import com.roni.library.ui.components.overlay.BlockingOverlayComponent;
import com.roni.library.ui.components.overlay.CustomOverlayComponent;
import com.roni.library.ui.components.overlay.DropdownPopupComponent;
import com.roni.library.ui.components.overlay.ExtendedFloatingActionButtonComponent;
import com.roni.library.ui.components.overlay.FloatingActionButtonComponent;
import com.roni.library.ui.components.overlay.FloatingMenuComponent;
import com.roni.library.ui.components.overlay.LoadingOverlayComponent;
import com.roni.library.ui.components.overlay.PopupWindowComponent;
import com.roni.library.ui.components.overlay.SpotlightOverlayComponent;
import com.roni.library.ui.components.overlay.TooltipComponent;
import com.roni.library.ui.components.systemui.DisplayCutoutComponent;
import com.roni.library.ui.components.systemui.EdgeToEdgeComponent;
import com.roni.library.ui.components.systemui.FullScreenComponent;
import com.roni.library.ui.components.systemui.ImmersiveModeComponent;
import com.roni.library.ui.components.systemui.KeepScreenOnComponent;
import com.roni.library.ui.components.systemui.NavigationBarComponent;
import com.roni.library.ui.components.systemui.ScreenOrientationComponent;
import com.roni.library.ui.components.systemui.StatusBarComponent;
import com.roni.library.ui.components.systemui.WindowInsetsComponent;
import com.roni.library.ui.components.theme.ColorSchemeComponent;
import com.roni.library.ui.components.theme.ComponentStyleComponent;
import com.roni.library.ui.components.theme.DynamicThemeComponent;
import com.roni.library.ui.components.theme.ShapeStyleComponent;
import com.roni.library.ui.components.theme.SystemBarThemeComponent;
import com.roni.library.ui.components.theme.ThemeControllerComponent;
import com.roni.library.ui.components.theme.ThemeModeComponent;
import com.roni.library.ui.components.theme.ThemeTransitionComponent;
import com.roni.library.ui.components.theme.TypographyComponent;
import com.roni.library.ui.components.webhost.WebErrorViewComponent;
import com.roni.library.ui.components.webhost.WebLoadingViewComponent;
import com.roni.library.ui.components.webhost.WebNavigationControlsComponent;
import com.roni.library.ui.components.webhost.WebOfflineViewComponent;
import com.roni.library.ui.components.webhost.WebPermissionViewComponent;
import com.roni.library.ui.components.webhost.WebProgressIndicatorComponent;
import com.roni.library.ui.components.webhost.WebViewContainerComponent;

public final class StandardUIComponentRegistryInitializer {

    private StandardUIComponentRegistryInitializer() {}

    public static void initializeDefaults(@NonNull UIComponentRegistry registry) {
        // SystemUI
        register(registry, SystemUI.STATUS_BAR, StatusBarComponent::new, 100);
        register(registry, SystemUI.NAVIGATION_BAR, NavigationBarComponent::new, 99);
        register(registry, SystemUI.EDGE_TO_EDGE, EdgeToEdgeComponent::new, 98);
        register(registry, SystemUI.FULL_SCREEN, FullScreenComponent::new, 97);
        register(registry, SystemUI.IMMERSIVE_MODE, ImmersiveModeComponent::new, 96);
        register(registry, SystemUI.WINDOW_INSETS, WindowInsetsComponent::new, 95);
        register(registry, SystemUI.DISPLAY_CUTOUT, DisplayCutoutComponent::new, 94);
        register(registry, SystemUI.SCREEN_ORIENTATION, ScreenOrientationComponent::new, 93);
        register(registry, SystemUI.KEEP_SCREEN_ON, KeepScreenOnComponent::new, 92);

        // AppBar
        register(registry, AppBar.TOOLBAR, ToolbarComponent::new, 90);
        register(registry, AppBar.TOP_APP_BAR, TopAppBarComponent::new, 89);
        register(registry, AppBar.CENTER_ALIGNED_TOP_APP_BAR, CenterAlignedTopAppBarComponent::new, 88);
        register(registry, AppBar.COLLAPSING_TOOLBAR, CollapsingToolbarComponent::new, 87);
        register(registry, AppBar.SEARCH_BAR, SearchBarComponent::new, 86);
        register(registry, AppBar.TITLE, TitleComponent::new, 85);
        register(registry, AppBar.SUBTITLE, SubtitleComponent::new, 84);
        register(registry, AppBar.BACK_BUTTON, BackButtonComponent::new, 83);
        register(registry, AppBar.ACTION_MENU, ActionMenuComponent::new, 82);
        register(registry, AppBar.ACTION_BUTTON, ActionButtonComponent::new, 81);
        register(registry, AppBar.OVERFLOW_MENU, OverflowMenuComponent::new, 80);

        // Navigation
        register(registry, Navigation.BOTTOM_NAVIGATION, BottomNavigationComponent::new, 75);
        register(registry, Navigation.NAVIGATION_DRAWER, NavigationDrawerComponent::new, 74);
        register(registry, Navigation.NAVIGATION_RAIL, NavigationRailComponent::new, 73);
        register(registry, Navigation.TAB_NAVIGATION, TabNavigationComponent::new, 72);
        register(registry, Navigation.TAB_LAYOUT, TabLayoutComponent::new, 71);
        register(registry, Navigation.NAVIGATION_CONTROLLER, NavigationControllerComponent::new, 70);
        register(registry, Navigation.NAVIGATION_STACK, NavigationStackComponent::new, 69);
        register(registry, Navigation.BACK_NAVIGATION, BackNavigationComponent::new, 68);
        register(registry, Navigation.NAVIGATION_BADGE, NavigationBadgeComponent::new, 67);
        register(registry, Navigation.NAVIGATION_INDICATOR, NavigationIndicatorComponent::new, 66);

        // Content
        register(registry, Content.ROOT_CONTAINER, RootContainerComponent::new, 60);
        register(registry, Content.CONTENT_CONTAINER, ContentContainerComponent::new, 59);
        register(registry, Content.FRAME_CONTAINER, FrameContainerComponent::new, 58);
        register(registry, Content.LINEAR_CONTAINER, LinearContainerComponent::new, 57);
        register(registry, Content.SCROLL_CONTAINER, ScrollContainerComponent::new, 56);
        register(registry, Content.NESTED_SCROLL_CONTAINER, NestedScrollContainerComponent::new, 55);
        register(registry, Content.FRAGMENT_CONTAINER, FragmentContainerComponent::new, 54);
        register(registry, Content.CUSTOM_VIEW_CONTAINER, CustomViewContainerComponent::new, 53);
        register(registry, Content.SAFE_AREA_CONTAINER, SafeAreaContainerComponent::new, 52);
        register(registry, Content.WEB_CONTENT_CONTAINER, WebContentContainerComponent::new, 51);

        // Dialog
        register(registry, Dialog.ALERT_DIALOG, AlertDialogComponent::new, 45);
        register(registry, Dialog.CONFIRMATION_DIALOG, ConfirmationDialogComponent::new, 44);
        register(registry, Dialog.CUSTOM_DIALOG, CustomDialogComponent::new, 43);
        register(registry, Dialog.SINGLE_CHOICE_DIALOG, SingleChoiceDialogComponent::new, 42);
        register(registry, Dialog.MULTI_CHOICE_DIALOG, MultiChoiceDialogComponent::new, 41);
        register(registry, Dialog.BOTTOM_SHEET_DIALOG, BottomSheetDialogComponent::new, 40);
        register(registry, Dialog.CUSTOM_BOTTOM_SHEET, CustomBottomSheetComponent::new, 39);
        register(registry, Dialog.ACTION_SHEET, ActionSheetComponent::new, 38);
        register(registry, Dialog.FULL_SCREEN_DIALOG, FullScreenDialogComponent::new, 37);
        register(registry, Dialog.PERMISSION_DIALOG, PermissionDialogComponent::new, 36);

        // Feedback
        register(registry, Feedback.TOAST, ToastComponent::new, 30);
        register(registry, Feedback.SNACKBAR, SnackbarComponent::new, 29);
        register(registry, Feedback.LOADING_INDICATOR, LoadingIndicatorComponent::new, 28);
        register(registry, Feedback.PROGRESS_INDICATOR, ProgressIndicatorComponent::new, 27);
        register(registry, Feedback.LINEAR_PROGRESS, LinearProgressComponent::new, 26);
        register(registry, Feedback.CIRCULAR_PROGRESS, CircularProgressComponent::new, 25);
        register(registry, Feedback.SUCCESS_MESSAGE, SuccessMessageComponent::new, 24);
        register(registry, Feedback.ERROR_MESSAGE, ErrorMessageComponent::new, 23);
        register(registry, Feedback.WARNING_MESSAGE, WarningMessageComponent::new, 22);
        register(registry, Feedback.EMPTY_STATE, EmptyStateComponent::new, 21);
        register(registry, Feedback.ERROR_STATE, ErrorStateComponent::new, 20);

        // Overlay
        register(registry, Overlay.LOADING_OVERLAY, LoadingOverlayComponent::new, 15);
        register(registry, Overlay.BLOCKING_OVERLAY, BlockingOverlayComponent::new, 14);
        register(registry, Overlay.FLOATING_ACTION_BUTTON, FloatingActionButtonComponent::new, 13);
        register(registry, Overlay.EXTENDED_FLOATING_ACTION_BUTTON, ExtendedFloatingActionButtonComponent::new, 12);
        register(registry, Overlay.FLOATING_MENU, FloatingMenuComponent::new, 11);
        register(registry, Overlay.POPUP_WINDOW, PopupWindowComponent::new, 10);
        register(registry, Overlay.TOOLTIP, TooltipComponent::new, 9);
        register(registry, Overlay.DROPDOWN_POPUP, DropdownPopupComponent::new, 8);
        register(registry, Overlay.CUSTOM_OVERLAY, CustomOverlayComponent::new, 7);
        register(registry, Overlay.SPOTLIGHT_OVERLAY, SpotlightOverlayComponent::new, 6);

        // Input
        register(registry, Input.TEXT_FIELD, TextFieldComponent::new, 5);
        register(registry, Input.PASSWORD_FIELD, PasswordFieldComponent::new, 5);
        register(registry, Input.MULTILINE_TEXT_FIELD, MultilineTextFieldComponent::new, 5);
        register(registry, Input.SEARCH_INPUT, SearchInputComponent::new, 5);
        register(registry, Input.CHECKBOX, CheckboxComponent::new, 5);
        register(registry, Input.RADIO_BUTTON, RadioButtonComponent::new, 5);
        register(registry, Input.RADIO_GROUP, RadioGroupComponent::new, 5);
        register(registry, Input.SWITCH, SwitchComponent::new, 5);
        register(registry, Input.SLIDER, SliderComponent::new, 5);
        register(registry, Input.RANGE_SLIDER, RangeSliderComponent::new, 5);
        register(registry, Input.DROPDOWN, DropdownComponent::new, 5);
        register(registry, Input.DATE_PICKER, DatePickerComponent::new, 5);
        register(registry, Input.TIME_PICKER, TimePickerComponent::new, 5);
        register(registry, Input.NUMBER_INPUT, NumberInputComponent::new, 5);

        // WebHost
        register(registry, WebHost.WEB_VIEW_CONTAINER, WebViewContainerComponent::new, 4);
        register(registry, WebHost.WEB_LOADING_VIEW, WebLoadingViewComponent::new, 4);
        register(registry, WebHost.WEB_ERROR_VIEW, WebErrorViewComponent::new, 4);
        register(registry, WebHost.WEB_PERMISSION_VIEW, WebPermissionViewComponent::new, 4);
        register(registry, WebHost.WEB_NAVIGATION_CONTROLS, WebNavigationControlsComponent::new, 4);
        register(registry, WebHost.WEB_PROGRESS_INDICATOR, WebProgressIndicatorComponent::new, 4);
        register(registry, WebHost.WEB_OFFLINE_VIEW, WebOfflineViewComponent::new, 4);

        // Theme
        register(registry, Theme.THEME_CONTROLLER, ThemeControllerComponent::new, 3);
        register(registry, Theme.THEME_MODE, ThemeModeComponent::new, 3);
        register(registry, Theme.COLOR_SCHEME, ColorSchemeComponent::new, 3);
        register(registry, Theme.TYPOGRAPHY, TypographyComponent::new, 3);
        register(registry, Theme.SHAPE_STYLE, ShapeStyleComponent::new, 3);
        register(registry, Theme.COMPONENT_STYLE, ComponentStyleComponent::new, 3);
        register(registry, Theme.SYSTEM_BAR_THEME, SystemBarThemeComponent::new, 3);
        register(registry, Theme.DYNAMIC_THEME, DynamicThemeComponent::new, 3);
        register(registry, Theme.THEME_TRANSITION, ThemeTransitionComponent::new, 3);

        // Lifecycle
        register(registry, Lifecycle.UI_STATE_CONTROLLER, UIStateControllerComponent::new, 2);
        register(registry, Lifecycle.COMPONENT_LIFECYCLE, ComponentLifecycleComponent::new, 2);
        register(registry, Lifecycle.CONFIGURATION_CHANGE_HANDLER, ConfigurationChangeHandlerComponent::new, 2);
        register(registry, Lifecycle.VISIBILITY_CONTROLLER, VisibilityControllerComponent::new, 2);
        register(registry, Lifecycle.STATE_RESTORATION, StateRestorationComponent::new, 2);
        register(registry, Lifecycle.SCREEN_STATE_CONTROLLER, ScreenStateControllerComponent::new, 2);

        // Animation
        register(registry, Animation.TRANSITION_ANIMATION, TransitionAnimationComponent::new, 1);
        register(registry, Animation.ENTER_ANIMATION, EnterAnimationComponent::new, 1);
        register(registry, Animation.EXIT_ANIMATION, ExitAnimationComponent::new, 1);
        register(registry, Animation.SHARED_ELEMENT_TRANSITION, SharedElementTransitionComponent::new, 1);
        register(registry, Animation.LOADING_ANIMATION, LoadingAnimationComponent::new, 1);
        register(registry, Animation.COMPONENT_ANIMATION, ComponentAnimationComponent::new, 1);
        register(registry, Animation.MOTION_CONTROLLER, MotionControllerComponent::new, 1);
    }

    private interface ComponentFactory {
        IUIComponent create(IUIComponentDescriptor descriptor);
    }

    private static void register(UIComponentRegistry registry,
                                 UIComponentType type,
                                 ComponentFactory factory,
                                 int priority) {
        // Visual category check: Theme, Lifecycle, Animation and pure controllers are non-visual
        boolean visual = type.getCategory() != UICategory.THEME
                      && type.getCategory() != UICategory.LIFECYCLE 
                      && type.getCategory() != UICategory.ANIMATION;
        if (type.equals(Navigation.NAVIGATION_CONTROLLER) || type.equals(Navigation.NAVIGATION_STACK)
                || type.equals(SystemUI.STATUS_BAR) || type.equals(SystemUI.NAVIGATION_BAR)
                || type.equals(SystemUI.EDGE_TO_EDGE) || type.equals(SystemUI.FULL_SCREEN)
                || type.equals(SystemUI.IMMERSIVE_MODE) || type.equals(SystemUI.SCREEN_ORIENTATION)
                || type.equals(SystemUI.KEEP_SCREEN_ON) || type.equals(Dialog.ALERT_DIALOG)
                || type.equals(Feedback.TOAST) || type.equals(Feedback.SNACKBAR)) {
            visual = false;
        }

        UIComponentDescriptor desc = new UIComponentDescriptor(
                type,
                type.getDisplayName(),
                "Standard NativeRuntime UI Component: " + type.getDisplayName(),
                false, // default disabled unless enabled via config
                priority,
                visual
        );
        registry.registerDescriptor(desc);
        registry.registerProvider(new UIComponentProvider(type) {
            @NonNull
            @Override
            public IUIComponent createComponent(@NonNull Context context, @NonNull IUIComponentDescriptor descriptor) {
                return factory.create(descriptor);
            }
        });
    }
}
