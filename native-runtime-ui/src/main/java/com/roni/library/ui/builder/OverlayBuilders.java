package com.roni.library.ui.builder;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import java.util.ArrayList;
import java.util.List;

/**
 * Basic to Advanced Builders for Overlay Components (Group 4).
 * Components:
 * 16. Dialog
 * 17. Alert Dialog
 * 18. Bottom Sheet
 * 19. Popup Menu
 * 20. Snackbar
 * 21. Toast
 * 22. Loading Overlay
 */
public final class OverlayBuilders {

    private OverlayBuilders() {}

    // ==========================================
    // 16 & 17. Dialog & Alert Dialog Config & Builder
    // ==========================================
    public static class DialogConfig {
        private final String title;
        private final String message;
        private final String positiveButtonText;
        private final Runnable positiveAction;
        private final String negativeButtonText;
        private final Runnable negativeAction;
        private final String neutralButtonText;
        private final Runnable neutralAction;
        private final boolean cancelable;
        private final int themeResId;

        private DialogConfig(Builder b) {
            this.title = b.title;
            this.message = b.message;
            this.positiveButtonText = b.positiveButtonText;
            this.positiveAction = b.positiveAction;
            this.negativeButtonText = b.negativeButtonText;
            this.negativeAction = b.negativeAction;
            this.neutralButtonText = b.neutralButtonText;
            this.neutralAction = b.neutralAction;
            this.cancelable = b.cancelable;
            this.themeResId = b.themeResId;
        }

        public static Builder builder() { return new Builder(); }
        public static DialogConfig basic(String title, String message) {
            return builder().title(title).message(message).build();
        }

        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getPositiveButtonText() { return positiveButtonText; }
        public Runnable getPositiveAction() { return positiveAction; }
        public String getNegativeButtonText() { return negativeButtonText; }
        public Runnable getNegativeAction() { return negativeAction; }
        public String getNeutralButtonText() { return neutralButtonText; }
        public Runnable getNeutralAction() { return neutralAction; }
        public boolean isCancelable() { return cancelable; }
        public int getThemeResId() { return themeResId; }

        public static class Builder {
            private String title = "";
            private String message = "";
            private String positiveButtonText = "OK";
            private Runnable positiveAction = null;
            private String negativeButtonText = null;
            private Runnable negativeAction = null;
            private String neutralButtonText = null;
            private Runnable neutralAction = null;
            private boolean cancelable = true;
            private int themeResId = 0;

            public Builder title(String title) { this.title = title != null ? title : ""; return this; }
            public Builder message(String message) { this.message = message != null ? message : ""; return this; }
            public Builder positiveButton(String text, @Nullable Runnable action) {
                this.positiveButtonText = text;
                this.positiveAction = action;
                return this;
            }
            public Builder negativeButton(String text, @Nullable Runnable action) {
                this.negativeButtonText = text;
                this.negativeAction = action;
                return this;
            }
            public Builder neutralButton(String text, @Nullable Runnable action) {
                this.neutralButtonText = text;
                this.neutralAction = action;
                return this;
            }
            public Builder cancelable(boolean cancelable) { this.cancelable = cancelable; return this; }
            public Builder themeResId(@StyleRes int themeResId) { this.themeResId = themeResId; return this; }
            public DialogConfig build() { return new DialogConfig(this); }
        }
    }

    // ==========================================
    // 18. Bottom Sheet Config & Builder
    // ==========================================
    public static class BottomSheetConfig {
        public static class ActionItem {
            private final String id;
            private final String title;
            private final Runnable action;
            private final boolean destructive;

            public ActionItem(String id, String title, Runnable action, boolean destructive) {
                this.id = id;
                this.title = title;
                this.action = action;
                this.destructive = destructive;
            }
            public String getId() { return id; }
            public String getTitle() { return title; }
            public Runnable getAction() { return action; }
            public boolean isDestructive() { return destructive; }
        }

        private final String title;
        private final List<ActionItem> actions;
        private final boolean cancelable;
        private final int peekHeightDp;
        private final boolean draggable;
        private final boolean hideable;

        private BottomSheetConfig(Builder b) {
            this.title = b.title;
            this.actions = new ArrayList<>(b.actions);
            this.cancelable = b.cancelable;
            this.peekHeightDp = b.peekHeightDp;
            this.draggable = b.draggable;
            this.hideable = b.hideable;
        }

        public static Builder builder() { return new Builder(); }
        public static BottomSheetConfig basic(String title) {
            return builder().title(title).build();
        }

        public String getTitle() { return title; }
        public List<ActionItem> getActions() { return actions; }
        public boolean isCancelable() { return cancelable; }
        public int getPeekHeightDp() { return peekHeightDp; }
        public boolean isDraggable() { return draggable; }
        public boolean isHideable() { return hideable; }

        public static class Builder {
            private String title = "";
            private final List<ActionItem> actions = new ArrayList<>();
            private boolean cancelable = true;
            private int peekHeightDp = 0; // wrap_content
            private boolean draggable = true;
            private boolean hideable = true;

            public Builder title(String title) { this.title = title != null ? title : ""; return this; }
            public Builder addAction(String id, String title, Runnable action) {
                this.actions.add(new ActionItem(id, title, action, false));
                return this;
            }
            public Builder addDestructiveAction(String id, String title, Runnable action) {
                this.actions.add(new ActionItem(id, title, action, true));
                return this;
            }
            public Builder cancelable(boolean cancelable) { this.cancelable = cancelable; return this; }
            public Builder peekHeightDp(int dp) { this.peekHeightDp = dp; return this; }
            public Builder draggable(boolean draggable) { this.draggable = draggable; return this; }
            public Builder hideable(boolean hideable) { this.hideable = hideable; return this; }
            public BottomSheetConfig build() { return new BottomSheetConfig(this); }
        }
    }

    // ==========================================
    // 19. Popup Menu Config & Builder
    // ==========================================
    public static class PopupMenuConfig {
        public static class MenuItem {
            private final int id;
            private final String title;
            private final Runnable action;

            public MenuItem(int id, String title, Runnable action) {
                this.id = id;
                this.title = title;
                this.action = action;
            }
            public int getId() { return id; }
            public String getTitle() { return title; }
            public Runnable getAction() { return action; }
        }

        private final int gravity;
        private final List<MenuItem> items;

        private PopupMenuConfig(Builder b) {
            this.gravity = b.gravity;
            this.items = new ArrayList<>(b.items);
        }

        public static Builder builder() { return new Builder(); }
        public int getGravity() { return gravity; }
        public List<MenuItem> getItems() { return items; }

        public static class Builder {
            private int gravity = 0;
            private final List<MenuItem> items = new ArrayList<>();

            public Builder gravity(int gravity) { this.gravity = gravity; return this; }
            public Builder addItem(int id, String title, Runnable action) {
                this.items.add(new MenuItem(id, title, action));
                return this;
            }
            public PopupMenuConfig build() { return new PopupMenuConfig(this); }
        }
    }

    // ==========================================
    // 20. Snackbar Config & Builder
    // ==========================================
    public static class SnackbarConfig {
        private final String message;
        private final int duration; // -2 = indefinite, -1 = short, 0 = long
        private final String actionText;
        private final Runnable action;
        private final int backgroundColor;
        private final int textColor;

        private SnackbarConfig(Builder b) {
            this.message = b.message;
            this.duration = b.duration;
            this.actionText = b.actionText;
            this.action = b.action;
            this.backgroundColor = b.backgroundColor;
            this.textColor = b.textColor;
        }

        public static Builder builder() { return new Builder(); }
        public static SnackbarConfig basic(String message) {
            return builder().message(message).build();
        }

        public String getMessage() { return message; }
        public int getDuration() { return duration; }
        public String getActionText() { return actionText; }
        public Runnable getAction() { return action; }
        public int getBackgroundColor() { return backgroundColor; }
        public int getTextColor() { return textColor; }

        public static class Builder {
            private String message = "";
            private int duration = -1; // SHORT
            private String actionText = null;
            private Runnable action = null;
            private int backgroundColor = Color.TRANSPARENT;
            private int textColor = Color.TRANSPARENT;

            public Builder message(String message) { this.message = message != null ? message : ""; return this; }
            public Builder duration(int duration) { this.duration = duration; return this; }
            public Builder action(String text, @Nullable Runnable action) {
                this.actionText = text;
                this.action = action;
                return this;
            }
            public Builder backgroundColor(@ColorInt int color) { this.backgroundColor = color; return this; }
            public Builder textColor(@ColorInt int color) { this.textColor = color; return this; }
            public SnackbarConfig build() { return new SnackbarConfig(this); }
        }
    }

    // ==========================================
    // 21. Toast Config & Builder
    // ==========================================
    public static class ToastConfig {
        public enum ToastType { INFO, SUCCESS, WARNING, ERROR }

        private final String message;
        private final int duration; // 0=short, 1=long
        private final ToastType type;
        private final int gravity;
        private final int backgroundColor;

        private ToastConfig(Builder b) {
            this.message = b.message;
            this.duration = b.duration;
            this.type = b.type;
            this.gravity = b.gravity;
            this.backgroundColor = b.backgroundColor;
        }

        public static Builder builder() { return new Builder(); }
        public static ToastConfig basic(String message) {
            return builder().message(message).build();
        }

        public String getMessage() { return message; }
        public int getDuration() { return duration; }
        public ToastType getType() { return type; }
        public int getGravity() { return gravity; }
        public int getBackgroundColor() { return backgroundColor; }

        public static class Builder {
            private String message = "";
            private int duration = 0; // SHORT
            private ToastType type = ToastType.INFO;
            private int gravity = 80; // BOTTOM
            private int backgroundColor = 0;

            public Builder message(String message) { this.message = message != null ? message : ""; return this; }
            public Builder duration(int duration) { this.duration = duration; return this; }
            public Builder type(ToastType type) { this.type = type != null ? type : ToastType.INFO; return this; }
            public Builder gravity(int gravity) { this.gravity = gravity; return this; }
            public Builder backgroundColor(@ColorInt int color) { this.backgroundColor = color; return this; }
            public ToastConfig build() { return new ToastConfig(this); }
        }
    }

    // ==========================================
    // 22. Loading Overlay Config & Builder
    // ==========================================
    public static class LoadingOverlayConfig {
        private final String message;
        private final boolean cancelable;
        private final float dimAmount;
        private final int spinnerColor;

        private LoadingOverlayConfig(Builder b) {
            this.message = b.message;
            this.cancelable = b.cancelable;
            this.dimAmount = b.dimAmount;
            this.spinnerColor = b.spinnerColor;
        }

        public static Builder builder() { return new Builder(); }
        public static LoadingOverlayConfig basic(String message) {
            return builder().message(message).build();
        }

        public String getMessage() { return message; }
        public boolean isCancelable() { return cancelable; }
        public float getDimAmount() { return dimAmount; }
        public int getSpinnerColor() { return spinnerColor; }

        public static class Builder {
            private String message = "Loading...";
            private boolean cancelable = false;
            private float dimAmount = 0.5f;
            private int spinnerColor = Color.TRANSPARENT;

            public Builder message(String message) { this.message = message != null ? message : "Loading..."; return this; }
            public Builder cancelable(boolean cancelable) { this.cancelable = cancelable; return this; }
            public Builder dimAmount(float dim) { this.dimAmount = dim; return this; }
            public Builder spinnerColor(@ColorInt int color) { this.spinnerColor = color; return this; }
            public LoadingOverlayConfig build() { return new LoadingOverlayConfig(this); }
        }
    }

    // ==========================================
    // Complete Overlay Group Config & Builder
    // ==========================================
    public static class OverlayGroupConfig {
        private final DialogConfig defaultDialog;
        private final BottomSheetConfig defaultBottomSheet;
        private final ToastConfig defaultToast;
        private final LoadingOverlayConfig defaultLoading;

        private OverlayGroupConfig(Builder b) {
            this.defaultDialog = b.defaultDialog != null ? b.defaultDialog : DialogConfig.builder().build();
            this.defaultBottomSheet = b.defaultBottomSheet != null ? b.defaultBottomSheet : BottomSheetConfig.builder().build();
            this.defaultToast = b.defaultToast != null ? b.defaultToast : ToastConfig.builder().build();
            this.defaultLoading = b.defaultLoading != null ? b.defaultLoading : LoadingOverlayConfig.builder().build();
        }

        public static Builder builder() { return new Builder(); }

        public DialogConfig getDefaultDialog() { return defaultDialog; }
        public BottomSheetConfig getDefaultBottomSheet() { return defaultBottomSheet; }
        public ToastConfig getDefaultToast() { return defaultToast; }
        public LoadingOverlayConfig getDefaultLoading() { return defaultLoading; }

        public static class Builder {
            private DialogConfig defaultDialog;
            private BottomSheetConfig defaultBottomSheet;
            private ToastConfig defaultToast;
            private LoadingOverlayConfig defaultLoading;

            public Builder dialog(@NonNull DialogConfig dialog) { this.defaultDialog = dialog; return this; }
            public Builder dialog(@NonNull DialogConfig.Builder builder) { this.defaultDialog = builder.build(); return this; }
            public Builder bottomSheet(@NonNull BottomSheetConfig sheet) { this.defaultBottomSheet = sheet; return this; }
            public Builder bottomSheet(@NonNull BottomSheetConfig.Builder builder) { this.defaultBottomSheet = builder.build(); return this; }
            public Builder toast(@NonNull ToastConfig toast) { this.defaultToast = toast; return this; }
            public Builder toast(@NonNull ToastConfig.Builder builder) { this.defaultToast = builder.build(); return this; }
            public Builder loading(@NonNull LoadingOverlayConfig loading) { this.defaultLoading = loading; return this; }
            public Builder loading(@NonNull LoadingOverlayConfig.Builder builder) { this.defaultLoading = builder.build(); return this; }
            public OverlayGroupConfig build() { return new OverlayGroupConfig(this); }
        }
    }
}
