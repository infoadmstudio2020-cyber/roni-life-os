package com.roni.library.platform.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.notification.NotificationActionSpec;
import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationErrors;
import com.roni.library.contracts.notification.NotificationPriority;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;
import com.roni.library.contracts.notification.NotificationStyle;
import com.roni.library.contracts.notification.NotificationVisibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge route {@code /notification}.
 *
 * <p>This is the only way a web app can reach the notification engine. The handler never trusts a
 * {@code projectId} from the payload: the owning project is taken from
 * {@link NotificationProjectContext}, which the native host sets when a project runtime opens.
 * A request that arrives with no active project is rejected outright.</p>
 *
 * <p>Supported actions: {@code post}, {@code update}, {@code cancel}, {@code cancelAll},
 * {@code schedule}, {@code cancelScheduled}, {@code list}, {@code channels}, {@code permission},
 * {@code status}.</p>
 */
public final class NotificationCommandHandler implements IApiHandler {

    public static final String ROUTE = "/notification";

    private final NotificationService service;

    public NotificationCommandHandler(@NonNull NotificationService service) {
        this.service = service;
    }

    @NonNull
    @Override
    public String getPath() {
        return ROUTE;
    }

    @NonNull
    @Override
    public String getAction() {
        return "*";
    }

    @NonNull
    @Override
    public NativeApiResponse handleRequest(@NonNull NativeApiRequest request) {
        String requestId = request.getRequestId();
        String action = request.getAction() != null ? request.getAction().toLowerCase() : "";
        try {
            switch (action) {
                case "post":
                case "show":
                case "notify":
                    return post(request, false);
                case "update":
                    return post(request, true);
                case "schedule":
                    return schedule(request);
                case "cancel":
                    return cancel(request);
                case "cancelscheduled":
                case "cancel_scheduled":
                    return cancelScheduled(request);
                case "cancelall":
                case "cancel_all":
                    return cancelAll(request);
                case "list":
                case "history":
                    return list(request);
                case "channels":
                    return channels(request);
                case "permission":
                    return permission(request);
                case "status":
                default:
                    return status(request);
            }
        } catch (NotificationException error) {
            return NativeApiResponse.error(requestId, statusFor(error.getCode()), error.toApiError());
        } catch (Throwable error) {
            return NativeApiResponse.error(requestId, 500,
                    new NativeApiError(NotificationErrors.INVALID_REQUEST,
                            error.getMessage() != null ? error.getMessage() : "Notification request failed"));
        }
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    @NonNull
    private NativeApiResponse post(@NonNull NativeApiRequest request, boolean update)
            throws NotificationException {
        String projectId = requireProject(request.getRequestId());
        NotificationRequest payload = parse(request, projectId);
        NotificationRecord record = update ? service.update(payload) : service.post(payload);
        Map<String, Object> data = new HashMap<>();
        data.put("notification", record.toMap());
        data.put("notificationId", NotificationService.notificationId(record.getProjectId(), record.getKey()));
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse schedule(@NonNull NativeApiRequest request) throws NotificationException {
        String projectId = requireProject(request.getRequestId());
        NotificationRequest payload = parse(request, projectId);
        int id = service.schedule(payload);
        Map<String, Object> data = new HashMap<>();
        data.put("scheduled", true);
        data.put("notificationId", id);
        data.put("scheduledAt", payload.getScheduledAt());
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse cancel(@NonNull NativeApiRequest request) throws NotificationException {
        String projectId = requireProject(request.getRequestId());
        String key = string(request, "key", "");
        if (key.isEmpty()) {
            throw new NotificationException(NotificationErrors.INVALID_KEY, "key is required");
        }
        NotificationRecord record = service.cancel(projectId, key);
        Map<String, Object> data = new HashMap<>();
        data.put("cancelled", true);
        data.put("notification", record.toMap());
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse cancelScheduled(@NonNull NativeApiRequest request) {
        String projectId = NotificationProjectContext.getActiveProject();
        String key = string(request, "key", "");
        boolean cancelled = !projectId.isEmpty() && !key.isEmpty()
                && service.cancelScheduled(projectId, key);
        Map<String, Object> data = new HashMap<>();
        data.put("cancelled", cancelled);
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse cancelAll(@NonNull NativeApiRequest request) {
        String projectId = NotificationProjectContext.getActiveProject();
        int count = projectId.isEmpty() ? 0 : service.cancelAll(projectId);
        Map<String, Object> data = new HashMap<>();
        data.put("cancelledCount", count);
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse list(@NonNull NativeApiRequest request) {
        String projectId = NotificationProjectContext.getActiveProject();
        boolean includeCancelled = bool(request, "includeCancelled", false);
        List<Map<String, Object>> items = new ArrayList<>();
        if (!projectId.isEmpty()) {
            for (NotificationRecord record : service.getHistory(projectId, includeCancelled)) {
                items.add(record.toMap());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("notifications", items);
        data.put("count", items.size());
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse channels(@NonNull NativeApiRequest request) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (NotificationChannelSpec channel : service.getChannels()) {
            items.add(channel.toMap());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("channels", items);
        data.put("count", items.size());
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse permission(@NonNull NativeApiRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("state", service.getPermissionState().name());
        data.put("canPost", service.getPermissionState().canPost());
        data.put("notificationsEnabled", service.areNotificationsEnabled());
        data.put("globalEnabled", service.getPreferences().isGlobalEnabled());
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private NativeApiResponse status(@NonNull NativeApiRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("initialized", service.isInitialized());
        data.put("enabled", service.isEnabled());
        data.put("permission", service.getPermissionState().name());
        data.put("notificationsEnabled", service.areNotificationsEnabled());
        data.put("activeProject", NotificationProjectContext.getActiveProject());
        data.put("unread", service.getUnreadCount(NotificationProjectContext.getActiveProject()));
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    // ------------------------------------------------------------------
    // parsing
    // ------------------------------------------------------------------

    @NonNull
    private String requireProject(@NonNull String requestId) throws NotificationException {
        String projectId = NotificationProjectContext.getActiveProject();
        if (projectId.isEmpty()) {
            throw new NotificationException(NotificationErrors.FORBIDDEN,
                    "No active project runtime; notifications are only available inside a project");
        }
        return projectId;
    }

    @NonNull
    private NotificationRequest parse(@NonNull NativeApiRequest request, @NonNull String projectId) {
        String key = string(request, "key", "");
        if (key.isEmpty()) {
            key = "default";
        }
        return new NotificationRequest.Builder()
                .projectId(projectId)
                .key(key)
                .title(string(request, "title", ""))
                .body(string(request, "body", string(request, "message", "")))
                .style(NotificationStyle.parse(string(request, "style", "BASIC")))
                .bigText(string(request, "bigText", ""))
                .imageUrl(string(request, "imageUrl", ""))
                .inboxLines(lines(request.getParameter("inboxLines")))
                .progress(integer(request, "progress", 0), bool(request, "indeterminate", false))
                .ongoing(bool(request, "ongoing", false))
                .autoCancel(bool(request, "autoCancel", true))
                .groupKey(string(request, "groupKey", ""))
                .groupSummary(bool(request, "groupSummary", false))
                .channelId(string(request, "channelId", ""))
                .priority(NotificationPriority.parse(string(request, "priority", "DEFAULT")))
                .visibility(NotificationVisibility.parse(string(request, "visibility", "PRIVATE")))
                .sound(bool(request, "sound", true))
                .vibration(bool(request, "vibration", true))
                .actions(actions(request.getParameter("actions")))
                .deepLink(string(request, "deepLink", ""))
                .scheduledAt(longValue(request, "scheduledAt", 0L))
                .ttlMs(longValue(request, "ttlMs", 0L))
                .build();
    }

    @NonNull
    private static String string(@NonNull NativeApiRequest request, @NonNull String key,
                                 @NonNull String fallback) {
        Object value = request.getParameter(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    private static boolean bool(@NonNull NativeApiRequest request, @NonNull String key,
                                boolean fallback) {
        Object value = request.getParameter(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return fallback;
    }

    private static int integer(@NonNull NativeApiRequest request, @NonNull String key, int fallback) {
        Object value = request.getParameter(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longValue(@NonNull NativeApiRequest request, @NonNull String key, long fallback) {
        Object value = request.getParameter(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @NonNull
    private static List<String> lines(@Nullable Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (value instanceof String) {
            for (String line : ((String) value).split("\n", -1)) {
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    @NonNull
    private static List<NotificationActionSpec> actions(@Nullable Object value) {
        List<NotificationActionSpec> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    Object id = map.get("actionId") != null ? map.get("actionId") : map.get("id");
                    Object title = map.get("title");
                    Object link = map.get("deepLink");
                    Object foreground = map.get("foreground");
                    result.add(new NotificationActionSpec(
                            id != null ? String.valueOf(id) : "",
                            title != null ? String.valueOf(title) : "",
                            link != null ? String.valueOf(link) : "",
                            foreground instanceof Boolean && (Boolean) foreground));
                } else if (item != null) {
                    String[] parts = String.valueOf(item).split("\\|", -1);
                    result.add(new NotificationActionSpec(
                            parts.length > 0 ? parts[0] : "",
                            parts.length > 1 ? parts[1] : "",
                            parts.length > 2 ? parts[2] : "",
                            false));
                }
            }
        }
        return result;
    }

    private static int statusFor(@NonNull String code) {
        switch (code) {
            case NotificationErrors.FORBIDDEN:
            case NotificationErrors.PROJECT_BLOCKED:
            case NotificationErrors.PROJECT_MUTED:
            case NotificationErrors.CHANNEL_BLOCKED:
            case NotificationErrors.PERMISSION_DENIED:
                return 403;
            case NotificationErrors.NOT_FOUND:
                return 404;
            case NotificationErrors.RATE_LIMITED:
            case NotificationErrors.SCHEDULE_LIMIT:
                return 429;
            case NotificationErrors.ENGINE_UNAVAILABLE:
            case NotificationErrors.DISABLED:
                return 503;
            default:
                return 400;
        }
    }
}
