package com.roni.library.contracts.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative description of an Android notification channel.
 *
 * <p>Channels are created once and then owned by the user: Android does not let an app change
 * importance, sound or vibration after creation. The engine therefore treats this spec as a
 * creation-time request and reports the effective state back to the caller.</p>
 */
public final class NotificationChannelSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String channelId;
    private final String name;
    private final String description;
    private final NotificationPriority priority;
    private final NotificationVisibility visibility;
    private final boolean sound;
    private final boolean vibration;
    private final boolean lights;

    public NotificationChannelSpec(@NonNull String channelId,
                                   @NonNull String name,
                                   @Nullable String description,
                                   @NonNull NotificationPriority priority,
                                   @NonNull NotificationVisibility visibility,
                                   boolean sound,
                                   boolean vibration,
                                   boolean lights) {
        this.channelId = channelId != null ? channelId : "";
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.priority = priority != null ? priority : NotificationPriority.DEFAULT;
        this.visibility = visibility != null ? visibility : NotificationVisibility.PRIVATE;
        this.sound = sound;
        this.vibration = vibration;
        this.lights = lights;
    }

    @NonNull
    public String getChannelId() {
        return channelId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public NotificationPriority getPriority() {
        return priority;
    }

    @NonNull
    public NotificationVisibility getVisibility() {
        return visibility;
    }

    public boolean isSound() {
        return sound;
    }

    public boolean isVibration() {
        return vibration;
    }

    public boolean isLights() {
        return lights;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("channelId", channelId);
        map.put("name", name);
        map.put("description", description);
        map.put("priority", priority.name());
        map.put("visibility", visibility.name());
        map.put("sound", sound);
        map.put("vibration", vibration);
        map.put("lights", lights);
        return map;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationChannelSpec{id='" + channelId + "', priority=" + priority + '}';
    }
}
