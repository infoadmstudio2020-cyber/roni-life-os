package com.roni.library.platform.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.notification.NotificationActionSpec;
import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.contracts.notification.NotificationPriority;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;
import com.roni.library.contracts.notification.NotificationStyle;
import com.roni.library.contracts.notification.NotificationVisibility;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Line-oriented codec for the notification store.
 *
 * <p>Records are stored one per line with tab-separated fields. Tabs, newlines and backslashes
 * inside user text are escaped, so a title containing a tab can never corrupt the file. The
 * format is deliberately dependency-free: the ecosystem has no shared JSON utility below the
 * bridge layer, and adding one for four fields would be worse than escaping them.</p>
 */
public final class NotificationCodec {

    private static final char FIELD = '\t';
    private static final char LIST = '\u0001';
    private static final char ACTION = '\u0002';
    private static final char ACTION_FIELD = '~';

    private NotificationCodec() {
    }

    // ------------------------------------------------------------------
    // escaping
    // ------------------------------------------------------------------

    @NonNull
    public static String escape(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case LIST:
                    out.append("\\u0001");
                    break;
                case ACTION:
                    out.append("\\u0002");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    @NonNull
    public static String unescape(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 't':
                    out.append('\t');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try {
                            out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            out.append("\\u");
                        }
                    } else {
                        out.append("\\u");
                    }
                    break;
                default:
                    out.append(next);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // records
    // ------------------------------------------------------------------

    @NonNull
    public static String encodeRecord(@NonNull NotificationRecord record) {
        StringBuilder out = new StringBuilder(160);
        out.append(record.getId()).append(FIELD)
                .append(escape(record.getProjectId())).append(FIELD)
                .append(escape(record.getKey())).append(FIELD)
                .append(escape(record.getTitle())).append(FIELD)
                .append(escape(record.getBody())).append(FIELD)
                .append(record.getStyle().name()).append(FIELD)
                .append(escape(record.getChannelId())).append(FIELD)
                .append(record.getPriority().name()).append(FIELD)
                .append(record.getPostedAt()).append(FIELD)
                .append(record.isRead() ? '1' : '0').append(FIELD)
                .append(record.isCancelled() ? '1' : '0').append(FIELD)
                .append(escape(record.getGroupKey())).append(FIELD)
                .append(escape(record.getDeepLink())).append(FIELD)
                .append(record.getActionCount()).append(FIELD)
                .append(record.isOngoing() ? '1' : '0').append(FIELD)
                .append(record.getScheduledAt());
        return out.toString();
    }

    @Nullable
    public static NotificationRecord decodeRecord(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(String.valueOf(FIELD), -1);
        if (parts.length < 16) {
            return null;
        }
        try {
            return new NotificationRecord(
                    Long.parseLong(parts[0]),
                    unescape(parts[1]),
                    unescape(parts[2]),
                    unescape(parts[3]),
                    unescape(parts[4]),
                    NotificationStyle.parse(parts[5]),
                    unescape(parts[6]),
                    NotificationPriority.parse(parts[7]),
                    Long.parseLong(parts[8]),
                    "1".equals(parts[9]),
                    "1".equals(parts[10]),
                    unescape(parts[11]),
                    unescape(parts[12]),
                    Integer.parseInt(parts[13]),
                    "1".equals(parts[14]),
                    Long.parseLong(parts[15]));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // requests (scheduled queue)
    // ------------------------------------------------------------------

    @NonNull
    public static String encodeRequest(@NonNull NotificationRequest request) {
        StringBuilder actions = new StringBuilder();
        for (NotificationActionSpec action : request.getActions()) {
            if (actions.length() > 0) {
                actions.append(ACTION);
            }
            actions.append(escape(action.getActionId())).append(ACTION_FIELD)
                    .append(escape(action.getTitle())).append(ACTION_FIELD)
                    .append(escape(action.getDeepLink())).append(ACTION_FIELD)
                    .append(action.isForeground() ? '1' : '0');
        }
        StringBuilder lines = new StringBuilder();
        for (String line : request.getInboxLines()) {
            if (lines.length() > 0) {
                lines.append(LIST);
            }
            lines.append(escape(line));
        }
        StringBuilder out = new StringBuilder(256);
        out.append(escape(request.getProjectId())).append(FIELD)
                .append(escape(request.getKey())).append(FIELD)
                .append(escape(request.getTitle())).append(FIELD)
                .append(escape(request.getBody())).append(FIELD)
                .append(request.getStyle().name()).append(FIELD)
                .append(escape(request.getBigText())).append(FIELD)
                .append(escape(request.getImageUrl())).append(FIELD)
                .append(lines).append(FIELD)
                .append(request.getProgress()).append(FIELD)
                .append(request.isIndeterminate() ? '1' : '0').append(FIELD)
                .append(request.isOngoing() ? '1' : '0').append(FIELD)
                .append(request.isAutoCancel() ? '1' : '0').append(FIELD)
                .append(escape(request.getGroupKey())).append(FIELD)
                .append(request.isGroupSummary() ? '1' : '0').append(FIELD)
                .append(escape(request.getChannelId())).append(FIELD)
                .append(request.getPriority().name()).append(FIELD)
                .append(request.getVisibility().name()).append(FIELD)
                .append(request.isSound() ? '1' : '0').append(FIELD)
                .append(request.isVibration() ? '1' : '0').append(FIELD)
                .append(actions).append(FIELD)
                .append(escape(request.getDeepLink())).append(FIELD)
                .append(request.getScheduledAt()).append(FIELD)
                .append(request.getTtlMs());
        return out.toString();
    }

    @Nullable
    public static NotificationRequest decodeRequest(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(String.valueOf(FIELD), -1);
        if (parts.length < 23) {
            return null;
        }
        try {
            List<String> inbox = new ArrayList<>();
            if (!parts[7].isEmpty()) {
                for (String raw : parts[7].split(String.valueOf(LIST), -1)) {
                    inbox.add(unescape(raw));
                }
            }
            List<NotificationActionSpec> actions = new ArrayList<>();
            if (!parts[19].isEmpty()) {
                for (String raw : parts[19].split(String.valueOf(ACTION), -1)) {
                    String[] fields = raw.split(String.valueOf(ACTION_FIELD), -1);
                    if (fields.length >= 4) {
                        actions.add(new NotificationActionSpec(unescape(fields[0]), unescape(fields[1]),
                                unescape(fields[2]), "1".equals(fields[3])));
                    }
                }
            }
            return new NotificationRequest.Builder()
                    .projectId(unescape(parts[0]))
                    .key(unescape(parts[1]))
                    .title(unescape(parts[2]))
                    .body(unescape(parts[3]))
                    .style(NotificationStyle.parse(parts[4]))
                    .bigText(unescape(parts[5]))
                    .imageUrl(unescape(parts[6]))
                    .inboxLines(inbox)
                    .progress(Integer.parseInt(parts[8]), "1".equals(parts[9]))
                    .ongoing("1".equals(parts[10]))
                    .autoCancel("1".equals(parts[11]))
                    .groupKey(unescape(parts[12]))
                    .groupSummary("1".equals(parts[13]))
                    .channelId(unescape(parts[14]))
                    .priority(NotificationPriority.parse(parts[15]))
                    .visibility(NotificationVisibility.parse(parts[16]))
                    .sound("1".equals(parts[17]))
                    .vibration("1".equals(parts[18]))
                    .actions(actions)
                    .deepLink(unescape(parts[20]))
                    .scheduledAt(Long.parseLong(parts[21]))
                    .ttlMs(Long.parseLong(parts[22]))
                    .build();
        } catch (NumberFormatException error) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // string sets (preferences)
    // ------------------------------------------------------------------

    @NonNull
    public static String encodeSet(@Nullable Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(escape(value));
        }
        return out.toString();
    }

    @NonNull
    public static Set<String> decodeSet(@Nullable String raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String line : raw.split("\n", -1)) {
            String value = unescape(line);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    @NonNull
    public static String encodeChannels(@NonNull List<NotificationChannelSpec> channels) {
        StringBuilder out = new StringBuilder();
        for (NotificationChannelSpec channel : channels) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(escape(channel.getChannelId())).append(FIELD)
                    .append(escape(channel.getName())).append(FIELD)
                    .append(escape(channel.getDescription())).append(FIELD)
                    .append(channel.getPriority().name()).append(FIELD)
                    .append(channel.getVisibility().name()).append(FIELD)
                    .append(channel.isSound() ? '1' : '0').append(FIELD)
                    .append(channel.isVibration() ? '1' : '0').append(FIELD)
                    .append(channel.isLights() ? '1' : '0');
        }
        return out.toString();
    }

    @NonNull
    public static List<NotificationChannelSpec> decodeChannels(@Nullable String raw) {
        List<NotificationChannelSpec> channels = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return channels;
        }
        for (String line : raw.split("\n", -1)) {
            String[] parts = line.split(String.valueOf(FIELD), -1);
            if (parts.length < 8) {
                continue;
            }
            channels.add(new NotificationChannelSpec(unescape(parts[0]), unescape(parts[1]),
                    unescape(parts[2]), NotificationPriority.parse(parts[3]),
                    NotificationVisibility.parse(parts[4]), "1".equals(parts[5]),
                    "1".equals(parts[6]), "1".equals(parts[7])));
        }
        return channels;
    }

    @NonNull
    public static NotificationPreferences decodePreferences(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return NotificationPreferences.defaults();
        }
        String[] parts = raw.split(String.valueOf(FIELD), -1);
        if (parts.length < 6) {
            return NotificationPreferences.defaults();
        }
        return new NotificationPreferences.Builder()
                .globalEnabled("1".equals(parts[0]))
                .soundEnabled("1".equals(parts[1]))
                .vibrationEnabled("1".equals(parts[2]))
                .blockedProjects(decodeSet(unescape(parts[3])))
                .mutedProjects(decodeSet(unescape(parts[4])))
                .blockedChannels(decodeSet(unescape(parts[5])))
                .build();
    }

    @NonNull
    public static String encodePreferences(@NonNull NotificationPreferences preferences) {
        return (preferences.isGlobalEnabled() ? "1" : "0") + FIELD
                + (preferences.isSoundEnabled() ? "1" : "0") + FIELD
                + (preferences.isVibrationEnabled() ? "1" : "0") + FIELD
                + escape(encodeSet(preferences.getBlockedProjects())) + FIELD
                + escape(encodeSet(preferences.getMutedProjects())) + FIELD
                + escape(encodeSet(preferences.getBlockedChannels()));
    }
}
