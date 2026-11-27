package com.roni.life.os.app.quickball;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.roni.life.os.app.project.Project;

/**
 * One entry of the Quick Ball action registry.
 *
 * <p>Actions are immutable descriptors: the registry owns identity and ordering, the Quick Ball
 * panel only renders them. A handler receives the host {@link Activity} and the active
 * {@link Project} so no engine or Activity reference is retained by the registry.</p>
 */
public final class NativeAction {

    /** Grouping used by the panel and the customization screen. */
    public enum Category {
        RUNTIME("Runtime"),
        TOOLS("Native tools"),
        AUTOMATION("Automation"),
        FILES("Files"),
        MEDIA("Media"),
        SYSTEM("System");

        private final String title;

        Category(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    /** Executes the action. Always called on the UI thread. */
    public interface Handler {
        void run(Activity activity, Project project);
    }

    private final String id;
    private final String label;
    private final String description;
    private final int iconRes;
    private final Category category;
    private final String permission;
    private final Handler handler;

    private NativeAction(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.description = builder.description;
        this.iconRes = builder.iconRes;
        this.category = builder.category;
        this.permission = builder.permission;
        this.handler = builder.handler;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getIconRes() {
        return iconRes;
    }

    public Category getCategory() {
        return category;
    }

    /** Runtime availability: a declared permission must be granted. */
    public boolean isAvailable(Context context) {
        if (permission == null || permission.isEmpty() || context == null) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void run(Activity activity, Project project) {
        if (handler != null) {
            handler.run(activity, project);
        }
    }

    /** Returns a copy with a user-defined label, keeping every other property. */
    public NativeAction withLabel(String customLabel) {
        if (customLabel == null || customLabel.trim().isEmpty()
                || customLabel.equals(label)) {
            return this;
        }
        Builder builder = new Builder(id, customLabel.trim(), handler)
                .description(description)
                .icon(iconRes)
                .category(category)
                .permission(permission);
        return builder.build();
    }

    /** Returns a copy with a user-selected icon, keeping every other property. */
    public NativeAction withIcon(int customIcon) {
        if (customIcon == 0 || customIcon == iconRes) {
            return this;
        }
        Builder builder = new Builder(id, label, handler)
                .description(description)
                .icon(customIcon)
                .category(category)
                .permission(permission);
        return builder.build();
    }

    public static final class Builder {

        private final String id;
        private final String label;
        private final Handler handler;
        private String description = "";
        private int iconRes;
        private Category category = Category.TOOLS;
        private String permission = "";

        public Builder(String id, String label, Handler handler) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("action id is required");
            }
            if (label == null || label.trim().isEmpty()) {
                throw new IllegalArgumentException("action label is required");
            }
            this.id = id.trim();
            this.label = label.trim();
            this.handler = handler;
        }

        public Builder description(String value) {
            this.description = value == null ? "" : value;
            return this;
        }

        public Builder icon(int value) {
            this.iconRes = value;
            return this;
        }

        public Builder category(Category value) {
            this.category = value == null ? Category.TOOLS : value;
            return this;
        }

        public Builder permission(String value) {
            this.permission = value == null ? "" : value;
            return this;
        }

        public NativeAction build() {
            return new NativeAction(this);
        }
    }
}
