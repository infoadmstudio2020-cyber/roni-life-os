package com.roni.library.contracts.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User-owned notification preferences.
 *
 * <p>The consumer app is the only writer; the engine is the only reader. Every delivery decision
 * (global switch, per-project block, per-project mute, per-channel block, sound, vibration) is
 * answered from this immutable snapshot, so a preference change takes effect on the very next
 * request without restarting anything.</p>
 */
public final class NotificationPreferences implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean globalEnabled;
    private final boolean soundEnabled;
    private final boolean vibrationEnabled;
    private final Set<String> blockedProjects;
    private final Set<String> mutedProjects;
    private final Set<String> blockedChannels;

    public NotificationPreferences(boolean globalEnabled,
                                   boolean soundEnabled,
                                   boolean vibrationEnabled,
                                   @Nullable Set<String> blockedProjects,
                                   @Nullable Set<String> mutedProjects,
                                   @Nullable Set<String> blockedChannels) {
        this.globalEnabled = globalEnabled;
        this.soundEnabled = soundEnabled;
        this.vibrationEnabled = vibrationEnabled;
        this.blockedProjects = immutable(blockedProjects);
        this.mutedProjects = immutable(mutedProjects);
        this.blockedChannels = immutable(blockedChannels);
    }

    @NonNull
    private static Set<String> immutable(@Nullable Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /** Defaults: everything on, nothing blocked. */
    @NonNull
    public static NotificationPreferences defaults() {
        return new NotificationPreferences(true, true, true, null, null, null);
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isVibrationEnabled() {
        return vibrationEnabled;
    }

    @NonNull
    public Set<String> getBlockedProjects() {
        return blockedProjects;
    }

    @NonNull
    public Set<String> getMutedProjects() {
        return mutedProjects;
    }

    @NonNull
    public Set<String> getBlockedChannels() {
        return blockedChannels;
    }

    /** @return {@code true} when the project may deliver notifications at all */
    public boolean isProjectAllowed(@Nullable String projectId) {
        return projectId != null && !blockedProjects.contains(projectId);
    }

    /** @return {@code true} when the project is allowed but silenced */
    public boolean isProjectMuted(@Nullable String projectId) {
        return projectId != null && mutedProjects.contains(projectId);
    }

    /** @return {@code true} when the channel is not blocked for the project */
    public boolean isChannelAllowed(@Nullable String projectId, @Nullable String channelId) {
        if (projectId == null || channelId == null || channelId.isEmpty()) {
            return true;
        }
        return !blockedChannels.contains(channelKey(projectId, channelId));
    }

    /** @return {@code true} when the request may produce sound */
    public boolean isSoundAllowed(@Nullable String projectId) {
        return soundEnabled && !isProjectMuted(projectId);
    }

    /** @return {@code true} when the request may vibrate */
    public boolean isVibrationAllowed(@Nullable String projectId) {
        return vibrationEnabled && !isProjectMuted(projectId);
    }

    @NonNull
    public static String channelKey(@NonNull String projectId, @NonNull String channelId) {
        return projectId + "|" + channelId;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder()
                .globalEnabled(globalEnabled)
                .soundEnabled(soundEnabled)
                .vibrationEnabled(vibrationEnabled)
                .blockedProjects(blockedProjects)
                .mutedProjects(mutedProjects)
                .blockedChannels(blockedChannels);
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("globalEnabled", globalEnabled);
        map.put("soundEnabled", soundEnabled);
        map.put("vibrationEnabled", vibrationEnabled);
        map.put("blockedProjects", new java.util.ArrayList<>(blockedProjects));
        map.put("mutedProjects", new java.util.ArrayList<>(mutedProjects));
        map.put("blockedChannels", new java.util.ArrayList<>(blockedChannels));
        return map;
    }

    /** Mutable builder used by the consumer app's settings screen. */
    public static final class Builder {

        private boolean globalEnabled = true;
        private boolean soundEnabled = true;
        private boolean vibrationEnabled = true;
        private final Set<String> blockedProjects = new LinkedHashSet<>();
        private final Set<String> mutedProjects = new LinkedHashSet<>();
        private final Set<String> blockedChannels = new LinkedHashSet<>();

        @NonNull
        public Builder globalEnabled(boolean value) {
            this.globalEnabled = value;
            return this;
        }

        @NonNull
        public Builder soundEnabled(boolean value) {
            this.soundEnabled = value;
            return this;
        }

        @NonNull
        public Builder vibrationEnabled(boolean value) {
            this.vibrationEnabled = value;
            return this;
        }

        @NonNull
        public Builder blockedProjects(@Nullable Set<String> value) {
            this.blockedProjects.clear();
            if (value != null) {
                this.blockedProjects.addAll(value);
            }
            return this;
        }

        @NonNull
        public Builder mutedProjects(@Nullable Set<String> value) {
            this.mutedProjects.clear();
            if (value != null) {
                this.mutedProjects.addAll(value);
            }
            return this;
        }

        @NonNull
        public Builder blockedChannels(@Nullable Set<String> value) {
            this.blockedChannels.clear();
            if (value != null) {
                this.blockedChannels.addAll(value);
            }
            return this;
        }

        @NonNull
        public Builder blockProject(@NonNull String projectId, boolean blocked) {
            if (blocked) {
                blockedProjects.add(projectId);
            } else {
                blockedProjects.remove(projectId);
            }
            return this;
        }

        @NonNull
        public Builder muteProject(@NonNull String projectId, boolean muted) {
            if (muted) {
                mutedProjects.add(projectId);
            } else {
                mutedProjects.remove(projectId);
            }
            return this;
        }

        @NonNull
        public Builder blockChannel(@NonNull String projectId, @NonNull String channelId, boolean blocked) {
            String key = channelKey(projectId, channelId);
            if (blocked) {
                blockedChannels.add(key);
            } else {
                blockedChannels.remove(key);
            }
            return this;
        }

        @NonNull
        public NotificationPreferences build() {
            return new NotificationPreferences(globalEnabled, soundEnabled, vibrationEnabled,
                    blockedProjects, mutedProjects, blockedChannels);
        }
    }
}
