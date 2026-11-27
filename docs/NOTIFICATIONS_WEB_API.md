# Notification Web API

Route: `/notification` · Transport: the existing Native API / WebBridge request envelope.

Web apps never touch Android notification APIs. Every call is validated natively against the
active project, the user's preferences and the rate limit.

## Request envelope

```json
{
  "requestId": "req-1",
  "path": "/notification",
  "action": "post",
  "parameters": { }
}
```

The owning project is **not** a parameter. It is taken from the native project runtime that is
currently on screen. A call made outside a project fails with `NOTIFICATION_FORBIDDEN`.

## Actions

| Action | Purpose |
| --- | --- |
| `post` (`show`, `notify`) | Create or replace a notification |
| `update` | Update an existing notification in place |
| `schedule` | Deliver at `scheduledAt` (epoch ms) |
| `cancel` | Cancel by `key` |
| `cancelScheduled` | Disarm a scheduled notification by `key` |
| `cancelAll` | Cancel every notification of the active project |
| `list` (`history`) | History of the active project |
| `channels` | Channels visible to the app |
| `permission` | Permission + global switch state |
| `status` | Engine status and unread count |

## `post` parameters

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `key` | string | `default` | Stable id; re-posting the same key replaces the notification |
| `title` | string | — | Required, ≤ 120 chars |
| `body` | string | `""` | ≤ 1000 chars (`message` is accepted as an alias) |
| `style` | enum | `BASIC` | `BASIC`, `BIG_TEXT`, `BIG_PICTURE`, `INBOX`, `PROGRESS`, `GROUP_SUMMARY` |
| `bigText` | string | `""` | ≤ 2000 chars |
| `imageUrl` | string | `""` | `http`/`https` only, ≤ 2048 chars |
| `inboxLines` | string[] | `[]` | ≤ 7 lines, 200 chars each |
| `progress` | int | `0` | 0–100 |
| `indeterminate` | bool | `false` | |
| `ongoing` | bool | `false` | Not dismissible by the user |
| `autoCancel` | bool | `true` | |
| `groupKey` | string | `""` | ≤ 64 chars |
| `groupSummary` | bool | `false` | |
| `channelId` | string | `default` | ≤ 64 chars, project-scoped |
| `priority` | enum | `DEFAULT` | `MIN`, `LOW`, `DEFAULT`, `HIGH`, `MAX` |
| `visibility` | enum | `PRIVATE` | `SECRET`, `PRIVATE`, `PUBLIC` |
| `sound` | bool | `true` | |
| `vibration` | bool | `true` | |
| `actions` | array | `[]` | ≤ 3 entries |
| `deepLink` | string | `""` | `http`, `https` or app scheme |
| `scheduledAt` | long | `0` | Epoch ms, 1 s – 30 days ahead |
| `ttlMs` | long | `0` | Optional expiry |

### Actions

```json
"actions": [
  { "actionId": "open", "title": "Open", "deepLink": "https://app.example/order/1" },
  { "actionId": "dismiss", "title": "Dismiss" }
]
```

A string form `"open|Open|https://app.example/order/1"` is also accepted.

## Responses

Success:

```json
{ "success": true, "data": { "notification": { }, "notificationId": 12345 } }
```

Failure:

```json
{ "success": false, "error": { "code": "NOTIFICATION_RATE_LIMITED", "message": "..." } }
```

## Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `NOTIFICATION_INVALID_REQUEST` | 400 | Malformed payload |
| `NOTIFICATION_INVALID_KEY` | 400 | Missing or over-long key |
| `NOTIFICATION_INVALID_TITLE` | 400 | Empty or over-long title |
| `NOTIFICATION_INVALID_BODY` | 400 | Over-long body |
| `NOTIFICATION_INVALID_STYLE` | 400 | Unknown style |
| `NOTIFICATION_INVALID_IMAGE` | 400 | Non-http image URL |
| `NOTIFICATION_INVALID_DEEP_LINK` | 400 | Unsafe scheme |
| `NOTIFICATION_INVALID_ACTION` | 400 | Malformed action |
| `NOTIFICATION_TOO_MANY_ACTIONS` | 400 | More than 3 actions |
| `NOTIFICATION_TOO_MANY_LINES` | 400 | More than 7 inbox lines |
| `NOTIFICATION_SCHEDULE_INVALID` | 400 | Outside the 1 s – 30 d window |
| `NOTIFICATION_SCHEDULE_LIMIT` | 429 | Too many scheduled notifications |
| `NOTIFICATION_FORBIDDEN` | 403 | No active project |
| `NOTIFICATION_PROJECT_BLOCKED` | 403 | User blocked the project |
| `NOTIFICATION_CHANNEL_BLOCKED` | 403 | User blocked the channel |
| `NOTIFICATION_PERMISSION_DENIED` | 403 | Runtime permission missing |
| `NOTIFICATION_NOT_FOUND` | 404 | Unknown key, or owned by another project |
| `NOTIFICATION_RATE_LIMITED` | 429 | More than 20 per 60 s |
| `NOTIFICATION_DISABLED` | 503 | Global switch off |
| `NOTIFICATION_ENGINE_UNAVAILABLE` | 503 | Engine not initialised |

## Examples

```js
// Simple notification
await native.request('/notification', 'post', {
  key: 'order-42', title: 'Order shipped', body: 'Arriving tomorrow'
});

// Progress
await native.request('/notification', 'update', {
  key: 'upload', title: 'Uploading', style: 'PROGRESS', progress: 42, ongoing: true
});

// Scheduled with an action
await native.request('/notification', 'schedule', {
  key: 'reminder', title: 'Stand up', body: 'Time to stretch',
  scheduledAt: Date.now() + 3600000,
  actions: [{ actionId: 'done', title: 'Done' }]
});

// Cancel
await native.request('/notification', 'cancel', { key: 'order-42' });
```

## Guarantees

1. A project can only read, update or cancel its own notifications.
2. Blocked projects and channels are silently suppressed — no error is leaked to the page.
3. Every payload is validated before it reaches Android.
4. Deep links are re-validated natively; the page cannot choose a component to launch.
