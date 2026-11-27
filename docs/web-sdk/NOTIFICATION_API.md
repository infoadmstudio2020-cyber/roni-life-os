# RONiLiFEOS — Notification API

**Native class:** `com.roni.library.platform.notification.NotificationCommandHandler`
**Route:** `/notification` · **Service:** `NotificationService`
**Requirement:** an active project runtime

---

## 1. Preconditions

Every action except `status` calls `requireProject(requestId)`:

```java
String projectId = requireProject(request.getRequestId());
```

`NotificationProjectContext.getActiveProject()` must return a non-empty id. Outside a project runtime the call fails with:

```json
{ "type": "ERROR", "error": "NOTIFICATION_FORBIDDEN: No active project" }
```

Notifications are **project-scoped**: the same `key` in two projects produces two independent notifications, and `notificationId` is derived from `projectId + key`.

---

## 2. Actions

| Action | Aliases | Purpose |
| --- | --- | --- |
| `notification.post` | `show`, `notify` | Create or replace a notification |
| `notification.update` | — | Update an existing notification (same `key`) |
| `notification.schedule` | — | Deliver at `scheduledAt` |
| `notification.cancel` | — | Cancel one notification by `key` |
| `notification.cancelScheduled` | `cancel_scheduled` | Cancel a scheduled delivery |
| `notification.cancelAll` | `cancel_all` | Cancel every notification of this project |
| `notification.list` | `history` | List posted notifications |
| `notification.channels` | — | List notification channels |
| `notification.permission` | — | Read permission state |
| `notification.status` | *(default)* | Service status |

Any unrecognised action falls through to `status` — it never returns `UNKNOWN_ACTION`.

---

## 3. `notification.post`

### 3.1 Parameters

| Parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `key` | string | `"default"` | Stable identity. Re-posting the same key **replaces** the notification. |
| `title` | string | `""` | |
| `body` | string | `""` | `message` is accepted as an alias |
| `style` | string | `"BASIC"` | `BASIC`, `BIG_TEXT`, `BIG_PICTURE`, `INBOX`, `PROGRESS` |
| `bigText` | string | `""` | Used by `BIG_TEXT` |
| `imageUrl` | string | `""` | Used by `BIG_PICTURE` |
| `inboxLines` | array | `[]` | Used by `INBOX` |
| `progress` | int | `0` | Used by `PROGRESS` (0–100) |
| `indeterminate` | boolean | `false` | Used by `PROGRESS` |
| `ongoing` | boolean | `false` | Non-dismissible |
| `autoCancel` | boolean | `true` | Dismiss on tap |
| `groupKey` | string | `""` | Notification group |
| `groupSummary` | boolean | `false` | Mark as the group summary |
| `channelId` | string | `""` | Empty → the project's default channel |
| `priority` | string | `"DEFAULT"` | `MIN`, `LOW`, `DEFAULT`, `HIGH`, `MAX` |
| `visibility` | string | `"PRIVATE"` | `PUBLIC`, `PRIVATE`, `SECRET` |
| `sound` | boolean | `true` | |
| `vibration` | boolean | `true` | |
| `actions` | array | `[]` | `[{ actionId, title, deepLink, foreground }]` |
| `deepLink` | string | `""` | Routed by `NotificationDeepLinkRouter` on tap |
| `scheduledAt` | long | `0` | Epoch ms; `0` = immediate |
| `ttlMs` | long | `0` | Time-to-live; `0` = no expiry |

### 3.2 Returns

```json
{
  "notification": {
    "projectId": "notes-app",
    "key": "daily-reminder",
    "title": "আজকের নোট",
    "body": "৩টি নোট লেখা বাকি",
    "style": "BASIC",
    "priority": "DEFAULT",
    "visibility": "PRIVATE",
    "channelId": "notes-app.default",
    "postedAt": 1732091234567,
    "scheduledAt": 0,
    "deepLink": "",
    "actions": []
  },
  "notificationId": 1042
}
```

`notificationId` is the Android notification id — stable for a given `projectId + key`.

### 3.3 Example

```js
const { notificationId } = await Native.call('notification.post', {
  key: 'daily-reminder',
  title: 'আজকের নোট',
  body: '৩টি নোট লেখা বাকি',
  style: 'BIG_TEXT',
  bigText: 'আজ আপনি ২টি নোট লিখেছেন। আরও ৩টি বাকি আছে।',
  priority: 'HIGH',
  autoCancel: true,
  deepLink: 'notes://today'
});
```

---

## 4. `notification.update`

Identical parameters to `post`. Requires the same `key`; the existing notification is replaced in place (no duplicate, no new sound if `sound: false`).

```js
await Native.call('notification.update', {
  key: 'upload-progress',
  title: 'আপলোড হচ্ছে',
  body: '৪৫%',
  style: 'PROGRESS',
  progress: 45,
  ongoing: true,
  autoCancel: false,
  sound: false
});
```

---

## 5. `notification.schedule`

Same parameters as `post`, plus a meaningful `scheduledAt`.

| | |
| --- | --- |
| **Returns** | `{ scheduled: true, notificationId, scheduledAt }` |
| **Errors** | `NOTIFICATION_INVALID_SCHEDULE` when `scheduledAt` is in the past |

```js
await Native.call('notification.schedule', {
  key: 'evening-review',
  title: 'সন্ধ্যার রিভিউ',
  body: 'আজকের নোটগুলো দেখে নিন',
  scheduledAt: Date.now() + 60 * 60 * 1000,
  deepLink: 'notes://review'
});
```

Scheduling uses `AlarmManager` through the platform engine. Exact alarms require the host app to hold `SCHEDULE_EXACT_ALARM`; otherwise the delivery is inexact.

---

## 6. Cancellation

### 6.1 `notification.cancel`

| | |
| --- | --- |
| **Parameters** | `key` (**R**) |
| **Returns** | `{ cancelled: true, notification }` |
| **Errors** | `NOTIFICATION_INVALID_KEY` when `key` is empty |

```js
await Native.call('notification.cancel', { key: 'daily-reminder' });
```

### 6.2 `notification.cancelScheduled`

| | |
| --- | --- |
| **Parameters** | `key` (**R**) |
| **Returns** | `{ cancelled: boolean }` |

### 6.3 `notification.cancelAll`

| | |
| --- | --- |
| **Parameters** | — |
| **Returns** | `{ cancelledCount: number }` |

Cancels every notification belonging to the **current project** only.

---

## 7. Reading state

### 7.1 `notification.list` / `notification.history`

```json
{ "notifications": [ { "key": "...", "title": "...", "postedAt": 0 } ], "count": 1 }
```

### 7.2 `notification.channels`

```json
{ "channels": [ { "id": "notes-app.default", "name": "Notes", "importance": 3 } ], "count": 1 }
```

### 7.3 `notification.permission`

```json
{ "state": "GRANTED", "canPost": true, "notificationsEnabled": true, "globalEnabled": true }
```

| `state` | Meaning |
| --- | --- |
| `GRANTED` | `POST_NOTIFICATIONS` granted and notifications enabled |
| `DENIED` | Permission refused |
| `NOT_DETERMINED` | Never asked |
| `BLOCKED` | Disabled in system settings |

> ⚠ This action is **read-only**. There is no `notification.requestPermission`. The host app owns the runtime permission prompt. When `canPost` is `false`, direct the user to `device.openAppSettings`.

### 7.4 `notification.status`

```json
{
  "initialized": true,
  "enabled": true,
  "permission": "GRANTED",
  "notificationsEnabled": true,
  "activeProject": "notes-app",
  "unread": 3
}
```

---

## 8. Notification styles

| Style | Required extras | Renders |
| --- | --- | --- |
| `BASIC` | — | Title + body |
| `BIG_TEXT` | `bigText` | Expandable long text |
| `BIG_PICTURE` | `imageUrl` | Large image (remote URL, downloaded by the service) |
| `INBOX` | `inboxLines` (array of strings) | Up to 5 lines |
| `PROGRESS` | `progress`, `indeterminate` | Progress bar; pair with `ongoing: true` |

```js
await Native.call('notification.post', {
  key: 'inbox',
  title: 'নতুন বার্তা',
  style: 'INBOX',
  inboxLines: ['রহিম: কাল দেখা হবে', 'করিম: ফাইল পাঠিয়েছি', 'সুমি: ধন্যবাদ']
});
```

---

## 9. Actions and deep links

```js
await Native.call('notification.post', {
  key: 'task-assigned',
  title: 'নতুন কাজ',
  body: 'আপনাকে একটি কাজ দেওয়া হয়েছে',
  deepLink: 'notes://task/42',
  actions: [
    { actionId: 'open', title: 'খুলুন', deepLink: 'notes://task/42', foreground: true },
    { actionId: 'done', title: 'সম্পন্ন', deepLink: 'notes://task/42/done', foreground: false }
  ]
});
```

| Field | Meaning |
| --- | --- |
| `actionId` | Identifier (`id` is accepted as an alias) |
| `title` | Button label |
| `deepLink` | URI opened when tapped |
| `foreground` | `true` → bring the app to the foreground |

Deep links are routed **natively** by `NotificationDeepLinkRouter`. The page is **not** notified — there is no `notification.clicked` event. If your web app must react, encode the intent in the URL and read `location.href` on load.

---

## 10. Channels

Channels are created per project. `channelId` defaults to `<projectId>.default`.

```js
await Native.call('notification.post', {
  key: 'sync-error',
  title: 'সিঙ্ক ব্যর্থ',
  body: 'ইন্টারনেট সংযোগ পরীক্ষা করুন',
  channelId: 'notes-app.errors',
  priority: 'HIGH'
});
```

Channel importance is fixed at creation time on Android — changing `priority` later does not raise an existing channel's importance.

---

## 11. Error codes

| Code | Cause |
| --- | --- |
| `NOTIFICATION_FORBIDDEN` | No active project |
| `NOTIFICATION_INVALID_KEY` | Empty `key` on cancel |
| `NOTIFICATION_INVALID_SCHEDULE` | `scheduledAt` in the past |
| `NOTIFICATION_PERMISSION_DENIED` | `POST_NOTIFICATIONS` not granted |
| `NOTIFICATION_CHANNEL_ERROR` | Channel creation failed |
| `NOTIFICATION_INVALID_REQUEST` | Malformed payload |
| `NOTIFICATION_INTERNAL_ERROR` | Unexpected failure |

All are returned as `NativeApiResponse.error(...)` with HTTP status mapped by `statusFor(code)`.

---

## 12. Complete example

```js
const Notify = {
  async ensurePermission() {
    const state = await Native.call('notification.permission');
    if (state.canPost) return true;
    await Native.call('ui.showAlert', {
      title: 'নোটিফিকেশন বন্ধ',
      message: 'সেটিংস থেকে নোটিফিকেশন চালু করুন।'
    });
    await Native.call('device.openAppSettings');
    return false;
  },

  async remind(title, body, whenMs) {
    if (!(await this.ensurePermission())) return null;
    const { notificationId } = await Native.call('notification.schedule', {
      key: 'reminder-' + whenMs,
      title,
      body,
      scheduledAt: whenMs,
      priority: 'HIGH',
      deepLink: 'notes://today'
    });
    return notificationId;
  },

  async progress(key, percent) {
    await Native.call('notification.update', {
      key,
      title: 'সিঙ্ক হচ্ছে',
      body: percent + '%',
      style: 'PROGRESS',
      progress: percent,
      ongoing: true,
      autoCancel: false,
      sound: false
    });
  },

  async done(key) {
    await Native.call('notification.cancel', { key });
  }
};
```

---

## 13. Related documents

- `NATIVE_API_REFERENCE.md` §11 — action table
- `BACKGROUND_SERVICES_API.md` — scheduled jobs and alarms
- `SECURITY_AND_PERMISSIONS.md` — `POST_NOTIFICATIONS` handling
