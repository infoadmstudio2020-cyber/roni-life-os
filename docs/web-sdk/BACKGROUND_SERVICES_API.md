# RONiLiFEOS — Background Services API

**Native class:** `com.roni.library.platform.automation.AutomationCommandHandler`
**Route:** `/automation` · **Service:** `AutomationService`
**Requirement:** `PlatformEngine` initialised

---

## 1. What this route gives you

`automation.*` is the only way a web app can do work that outlives the page. It exposes five subsystems:

| Subsystem | Actions | Purpose |
| --- | --- | --- |
| Tasks | `submitTask`, `cancelTask`, `getTask`, `listTasks`, `clearTasks` | One-shot work with retries |
| Jobs | `scheduleJob`, `cancelJob`, `listJobs` | Repeating work with constraints |
| Alarms | `setAlarm`, `cancelAlarm`, `listAlarms` | Time-based triggers |
| Transfers | `download`, `upload`, `cancelTransfer`, `getTransfer`, `listTransfers` | Background file transfer |
| Events | `publishEvent`, `getEvents`, `clearEvents` | Durable event log |
| Services | `listServices`, `getServiceStatus`, `startService`, `stopService` | Long-running services |
| Introspection | `getStatus`, `getCapabilities` | Capability discovery |

> ⚠ Background work is **not** a JS callback. The page is not resumed when a task finishes. Results are written to the event log and read back with `automation.getEvents`.

---

## 2. Capability discovery

### 2.1 `automation.getStatus`

```json
{
  "initialized": true,
  "running": true,
  "taskCount": 2,
  "jobCount": 1,
  "alarmCount": 0,
  "transferCount": 0,
  "eventCount": 14
}
```

### 2.2 `automation.getCapabilities`

```json
{
  "tasks": true,
  "jobs": true,
  "alarms": true,
  "transfers": true,
  "events": true,
  "services": true,
  "exactAlarms": false,
  "backgroundRestricted": false
}
```

`exactAlarms: false` means the host app lacks `SCHEDULE_EXACT_ALARM`; `setAlarm` with `exact: true` will be downgraded to inexact.

```js
const caps = await Native.call('automation.getCapabilities');
if (!caps.transfers) {
  // fall back to network.request + filemanager.writeFile
}
```

---

## 3. Tasks

A task is a single unit of work executed by a worker type.

### 3.1 `automation.submitTask`

| Parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `name` | string | `"Background task"` | Human label |
| `type` | string | `"noop"` | Worker type |
| `payload` | object | `{}` | Passed to the worker |
| `maxAttempts` | int | `1` | Retry budget |

**Returns** — the task map:

```json
{
  "id": "task_1732091234567_1",
  "name": "Sync notes",
  "type": "noop",
  "state": "QUEUED",
  "attempts": 0,
  "maxAttempts": 3,
  "createdAt": 1732091234567,
  "payload": { "endpoint": "/sync" }
}
```

```js
const task = await Native.call('automation.submitTask', {
  name: 'Sync notes',
  type: 'noop',
  payload: { endpoint: '/sync' },
  maxAttempts: 3
});
```

### 3.2 Task lifecycle

```
QUEUED → RUNNING → SUCCEEDED
                 ↘ FAILED → (retry while attempts < maxAttempts) → QUEUED
                 ↘ CANCELLED
```

### 3.3 Other task actions

| Action | Parameters | Returns |
| --- | --- | --- |
| `automation.getTask` | `taskId` | task map |
| `automation.cancelTask` | `taskId` | `{ cancelled: boolean }` |
| `automation.listTasks` | — | `{ tasks: [...] }` |
| `automation.clearTasks` | — | `{ removed: number }` |

`clearTasks` removes **finished** tasks only; running tasks are untouched.

---

## 4. Jobs

A job is repeating work with execution constraints.

### 4.1 `automation.scheduleJob`

| Parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | `""` | Stable identity; re-scheduling the same id replaces it |
| `name` | string | `"Scheduled job"` | |
| `workerType` | string | `"noop"` | |
| `requiresNetwork` | boolean | `false` | Run only when connected |
| `requiresCharging` | boolean | `false` | Run only while charging |
| `requiresIdle` | boolean | `false` | Run only when the device is idle |
| `initialDelayMs` | long | `0` | First run delay |
| `repeatIntervalMs` | long | `0` | `0` = one-shot |
| `maxAttempts` | int | `1` | |
| `payload` | object | `{}` | |

```js
await Native.call('automation.scheduleJob', {
  id: 'sync-notes',
  name: 'Sync notes',
  workerType: 'noop',
  requiresNetwork: true,
  requiresCharging: false,
  initialDelayMs: 60 * 1000,
  repeatIntervalMs: 15 * 60 * 1000,
  maxAttempts: 3,
  payload: { endpoint: '/sync' }
});
```

### 4.2 Other job actions

| Action | Parameters | Returns |
| --- | --- | --- |
| `automation.cancelJob` | `jobId` | `{ cancelled: boolean }` |
| `automation.listJobs` | — | `{ jobs: [...] }` |

Jobs are persisted across process death and restored by `AutomationService` on boot.

---

## 5. Alarms

Alarms are exact-time triggers, independent of jobs.

### 5.1 `automation.setAlarm`

| Parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | `""` | Stable identity |
| `label` | string | `"Alarm"` | |
| `triggerAt` | long | `0` | Epoch ms |
| `intervalMs` | long | `0` | `0` = one-shot |
| `exact` | boolean | `false` | Requires `SCHEDULE_EXACT_ALARM` |
| `payload` | object | `{}` | |

```js
await Native.call('automation.setAlarm', {
  id: 'morning-review',
  label: 'Morning review',
  triggerAt: Date.now() + 8 * 60 * 60 * 1000,
  intervalMs: 24 * 60 * 60 * 1000,
  exact: false,
  payload: { screen: 'today' }
});
```

### 5.2 Other alarm actions

| Action | Parameters | Returns |
| --- | --- | --- |
| `automation.cancelAlarm` | `alarmId` | `{ cancelled: boolean }` |
| `automation.listAlarms` | — | `{ alarms: [...] }` |

---

## 6. Transfers

### 6.1 `automation.download`

| Parameter | Type | Default |
| --- | --- | --- |
| `id` | string | `""` |
| `url` | string | `""` |
| `path` | string | `""` (local destination) |
| `headers` | object | `{}` |
| `expectedBytes` | long | `0` |

**Returns** — transfer progress map:

```json
{
  "id": "dl_1",
  "direction": "DOWNLOAD",
  "url": "https://example.com/file.zip",
  "path": "downloads/file.zip",
  "state": "RUNNING",
  "bytesTransferred": 524288,
  "totalBytes": 2097152,
  "percent": 25
}
```

### 6.2 `automation.upload`

Same parameters as `download`; `direction` is `UPLOAD`.

### 6.3 Other transfer actions

| Action | Parameters | Returns |
| --- | --- | --- |
| `automation.getTransfer` | `transferId` | transfer map |
| `automation.cancelTransfer` | `transferId` | `{ cancelled: boolean }` |
| `automation.listTransfers` | — | `{ transfers: [...] }` |

Transfers continue while the app is backgrounded. Progress is **not** pushed to the page — poll `getTransfer` or read the event log.

```js
async function waitForTransfer(id, onProgress) {
  for (;;) {
    const t = await Native.call('automation.getTransfer', { transferId: id });
    onProgress(t.percent);
    if (t.state === 'COMPLETED' || t.state === 'FAILED' || t.state === 'CANCELLED') return t;
    await new Promise((r) => setTimeout(r, 1000));
  }
}
```

> Polling stops when the app is paused (`webView.pauseTimers()`). Resume polling on `visibilitychange`.

---

## 7. Event log

The event log is a durable, ordered record of background activity. It is the **only** way a page learns that background work finished.

### 7.1 `automation.publishEvent`

| Parameter | Type | Default |
| --- | --- | --- |
| `topic` | string | `EventEngine.TOPIC_CUSTOM` |
| `payload` | object | `{}` |

```js
await Native.call('automation.publishEvent', {
  topic: 'notes.synced',
  payload: { count: 12 }
});
```

### 7.2 `automation.getEvents`

| Parameter | Type | Default |
| --- | --- | --- |
| `sinceSequence` | long | `0` |
| `limit` | int | `100` |

```json
{
  "events": [
    { "sequence": 15, "topic": "notes.synced", "payload": { "count": 12 }, "timestamp": 1732091234567 }
  ]
}
```

```js
let cursor = 0;

async function drainEvents() {
  const { events } = await Native.call('automation.getEvents', { sinceSequence: cursor, limit: 100 });
  for (const event of events) {
    cursor = Math.max(cursor, event.sequence);
    handle(event);
  }
}

setInterval(drainEvents, 5000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) drainEvents(); });
```

### 7.3 `automation.clearEvents`

Returns `{ cleared: true }`. Clears the whole log — reset your cursor to `0` afterwards.

---

## 8. Services

| Action | Parameters | Returns |
| --- | --- | --- |
| `automation.listServices` | — | `{ services: [{ name, state }] }` |
| `automation.getServiceStatus` | `name` | service status map |
| `automation.startService` | `name` | `{ started: boolean }` |
| `automation.stopService` | `name` | `{ stopped: boolean }` |

`service.*` (see `NATIVE_API_REFERENCE.md` §16) is the read-only registry view; `automation.*Service` can start and stop.

---

## 9. Worker types

`type` / `workerType` selects the worker implementation registered with `AutomationService`. The default `"noop"` worker exists in every build and does nothing — it is useful for testing scheduling, retries and the event log.

An unknown worker type fails the task with `AUTOMATION_UNKNOWN_WORKER`. Discover what is available:

```js
const caps = await Native.call('automation.getCapabilities');
const services = await Native.call('automation.listServices');
```

---

## 10. Error codes

| Code | Cause |
| --- | --- |
| `AUTOMATION_UNAVAILABLE` | `PlatformEngine` not initialised |
| `AUTOMATION_UNKNOWN_WORKER` | Unknown `type` / `workerType` |
| `AUTOMATION_INVALID_TASK` | Missing or malformed task payload |
| `AUTOMATION_INVALID_JOB` | Missing `id` or invalid interval |
| `AUTOMATION_INVALID_ALARM` | `triggerAt` in the past |
| `AUTOMATION_TRANSFER_FAILED` | Network or disk failure |
| `AUTOMATION_NOT_FOUND` | Unknown `taskId` / `jobId` / `alarmId` / `transferId` |
| `AUTOMATION_INTERNAL_ERROR` | Unexpected failure |

---

## 11. Complete example

```js
const Background = {
  async ready() {
    const status = await Native.call('automation.getStatus');
    return !!status.initialized;
  },

  async scheduleSync(intervalMs) {
    return Native.call('automation.scheduleJob', {
      id: 'sync-notes',
      name: 'Sync notes',
      workerType: 'noop',
      requiresNetwork: true,
      repeatIntervalMs: intervalMs,
      maxAttempts: 3,
      payload: { endpoint: '/sync' }
    });
  },

  async stopSync() {
    return Native.call('automation.cancelJob', { jobId: 'sync-notes' });
  },

  async downloadFile(url, path) {
    const transfer = await Native.call('automation.download', {
      id: 'dl-' + Date.now(),
      url,
      path
    });
    return transfer.id;
  },

  async drain(onEvent) {
    const { events } = await Native.call('automation.getEvents', { sinceSequence: 0, limit: 200 });
    events.forEach(onEvent);
  }
};
```

---

## 12. Related documents

- `NATIVE_API_REFERENCE.md` §14 — action table
- `NOTIFICATION_API.md` — user-visible delivery
- `WEB_APP_DEVELOPER_GUIDE.md` §14 — why JS timers stop when paused
