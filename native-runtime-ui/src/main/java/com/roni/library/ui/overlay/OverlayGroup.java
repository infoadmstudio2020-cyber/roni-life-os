package com.roni.library.ui.overlay;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.ui.CustomBottomSheet;
import com.roni.library.ui.NativeActionSheet;
import com.roni.library.ui.NativeToast;
import com.roni.library.ui.dialog.DialogManager;
import java.util.List;

/**
 * Group 4: Overlay Coordinator.
 * Coordinates all 7 Overlay UI components:
 *  16. Dialog
 *  17. Alert Dialog
 *  18. Bottom Sheet
 *  19. Popup Menu
 *  20. Snackbar
 *  21. Toast
 *  22. Loading Overlay
 *
 * Each overlay component can be invoked separately or dismissed in hierarchy.
 */
public class OverlayGroup {

    private final DialogManager dialogManager;
    private final CustomBottomSheet bottomSheet;
    private final PopupMenuManager popupMenuManager;
    private final SnackbarManager snackbarManager;
    private final NativeToast toast;
    private final LoadingOverlayManager loadingOverlayManager;

    public OverlayGroup() {
        this(new DialogManager(), new CustomBottomSheet(), new PopupMenuManager(),
                new SnackbarManager(), new NativeToast(), new LoadingOverlayManager());
    }

    public OverlayGroup(@NonNull DialogManager dialogManager,
                        @NonNull CustomBottomSheet bottomSheet,
                        @NonNull PopupMenuManager popupMenuManager,
                        @NonNull SnackbarManager snackbarManager,
                        @NonNull NativeToast toast,
                        @NonNull LoadingOverlayManager loadingOverlayManager) {
        this.dialogManager = dialogManager;
        this.bottomSheet = bottomSheet;
        this.popupMenuManager = popupMenuManager;
        this.snackbarManager = snackbarManager;
        this.toast = toast;
        this.loadingOverlayManager = loadingOverlayManager;
    }

    // Component 16: Dialog
    public DialogManager getDialogManager() { return dialogManager; }

    // Component 17: Alert Dialog
    public void showAlert(@NonNull Activity activity, String title, String message, @Nullable Runnable onConfirm) {
        dialogManager.showNativeDialog(activity, title, message, "OK", null, true, onConfirm, null);
    }

    public void showConfirm(@NonNull Activity activity, String title, String message,
                            @Nullable Runnable onConfirm, @Nullable Runnable onCancel) {
        dialogManager.showNativeDialog(activity, title, message, "Confirm", "Cancel", false, onConfirm, onCancel);
    }

    // Component 18: Bottom Sheet
    public CustomBottomSheet getBottomSheet() { return bottomSheet; }

    public void showBottomSheet(@NonNull Activity activity, String title, List<NativeActionSheet.ActionItem> actions) {
        bottomSheet.showNativeBottomSheet(activity, title, actions, null, null);
    }

    // Component 19: Popup Menu
    public PopupMenuManager getPopupMenuManager() { return popupMenuManager; }

    public void showPopupMenu(@NonNull Context context, @NonNull View anchor, @NonNull List<PopupMenuManager.PopupMenuItem> items) {
        popupMenuManager.showPopupMenu(context, anchor, items);
    }

    // Component 20: Snackbar
    public SnackbarManager getSnackbarManager() { return snackbarManager; }

    public void showSnackbar(@NonNull View parent, String message) {
        snackbarManager.showShort(parent, message);
    }

    public void showSnackbar(@NonNull View parent, String message, String actionLabel, Runnable onAction) {
        snackbarManager.show(parent, message, 3500, actionLabel, onAction);
    }

    // Component 21: Toast
    public NativeToast getToast() { return toast; }

    public void showToast(@NonNull Context context, String message) {
        toast.showNativeToast(context, message, NativeToast.Duration.SHORT, NativeToast.Type.INFO);
    }

    public void showToast(@NonNull Context context, String message, NativeToast.Duration duration, NativeToast.Type type) {
        toast.showNativeToast(context, message, duration, type);
    }

    // Component 22: Loading Overlay
    public LoadingOverlayManager getLoadingOverlayManager() { return loadingOverlayManager; }

    public void showLoading(@NonNull Activity activity, String message) {
        loadingOverlayManager.showNativeLoading(activity, message, false);
    }

    public void hideLoading() {
        loadingOverlayManager.hideNativeLoading();
    }
}
