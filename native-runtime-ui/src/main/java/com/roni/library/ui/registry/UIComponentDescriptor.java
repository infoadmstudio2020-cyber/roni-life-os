package com.roni.library.ui.registry;

import androidx.annotation.NonNull;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.contracts.ui.UICategory;
import com.roni.library.contracts.ui.UIComponentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UIComponentDescriptor implements IUIComponentDescriptor {

    private static final long serialVersionUID = 1L;

    private final UIComponentType type;
    private final String name;
    private final String description;
    private final boolean enabledByDefault;
    private final int priority;
    private final boolean visual;
    private final List<UIComponentType> dependencies;

    public UIComponentDescriptor(@NonNull UIComponentType type,
                                 @NonNull String name,
                                 @NonNull String description,
                                 boolean enabledByDefault,
                                 int priority,
                                 boolean visual,
                                 @NonNull List<UIComponentType> dependencies) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.enabledByDefault = enabledByDefault;
        this.priority = priority;
        this.visual = visual;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(dependencies, "dependencies cannot be null")));
    }

    public UIComponentDescriptor(@NonNull UIComponentType type,
                                 @NonNull String name,
                                 @NonNull String description,
                                 boolean enabledByDefault,
                                 int priority,
                                 boolean visual) {
        this(type, name, description, enabledByDefault, priority, visual, Collections.emptyList());
    }

    public UIComponentDescriptor(@NonNull UIComponentType type,
                                 @NonNull String name,
                                 @NonNull String description,
                                 boolean enabledByDefault,
                                 int priority) {
        this(type, name, description, enabledByDefault, priority, true, Collections.emptyList());
    }

    @NonNull
    @Override
    public UIComponentType getType() {
        return type;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String getDescription() {
        return description;
    }

    @NonNull
    @Override
    public UICategory getCategory() {
        return type.getCategory();
    }

    @Override
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isVisual() {
        return visual;
    }

    @NonNull
    @Override
    public List<UIComponentType> getDependencies() {
        return dependencies;
    }
}
