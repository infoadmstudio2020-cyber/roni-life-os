# RONiLiFEOS — Logging and Analytics API

**Native classes:** `LoggerCommandHandler`, `AnalyticsCommandHandler`, `NativeLogger`
**Routes:** `/logger`, `/analytics`

---

## 1. Two different things

| | `logger.*` | `analytics.*` |
| --- | --- | --- |
| Purpose | Debug output to Logcat | Product telemetry |
| Persistence | Logcat only | In-memory + offline queue |
| Levels | 5 | — |
| Readable from JS | `getLevel` only | `getStats` |
| Cost | Free | Bounded buffers |

Use `logger` while developing. Use `analytics` for behaviour you want to measure.

---

## 2. `logger.*`

**Native class:** `com.roni.library.logger.handler.LoggerCommandHandler`
**Route:** `/logger` · **Action pattern:** `*`

### 2.1 `logger.log`

| Parameter | Type | Required | Default |
| --- | --- | --- | --- |
| `level` | string | O | `"INFO"` |
| `tag` | string | O | `"RPC"` |
| `message` | string | O | `""` |

**Returns:** `{ status: "logged", tag }`

```js
await Native.call('logger.log', { level: 'DEBUG', tag: 'NotesApp', message: 'saving note 42' });
```

Levels: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. An unknown level falls back to `INFO`.

### 2.2 Level control

| Action | Aliases | Parameters | Returns |
| --- | --- | --- | --- |
| `logger.setLevel` | `setMinimumLevel` | `level` (O, `"INFO"`) | `{ currentLevel }` |
| `logger.getLevel` | `getMinimumLevel` | — | `{ currentLevel }` |
| `logger.flush` | — | — | `{ flushed: true }` |

```js
await Native.call('logger.setLevel', { level: 'DEBUG' });
const { currentLevel } = await Native.call('logger.getLevel');
```

Messages below the minimum level are dropped before they reach Logcat.

### 2.3 Wrapper

```js
const Log = {
  d: (m, t) => Native.call('logger.log', { level: 'DEBUG', tag: t || 'App', message: m }),
  i: (m, t) => Native.call('logger.log', { level: 'INFO',  tag: t || 'App', message: m }),
  w: (m, t) => Native.call('logger.log', { level: 'WARN',  tag: t || 'App', message: m }),
  e: (m, t) => Native.call('logger.log', { level: 'ERROR', tag: t || 'App', message: m })
};
```

> ⚠ `logger.log` is a bridge round-trip. Do not call it in a tight loop — batch or gate it behind a debug flag.

---

## 3. `analytics.*`

**Native class:** `com.roni.library.analytics.handler.AnalyticsCommandHandler`
**Route:** `/analytics` · **Action pattern:** `*`

### 3.1 `analytics.trackEvent`

| Parameter | Type | Required | Default |
| --- | --- | --- | --- |
| `name` | string | **R** | — |
| `parameters` | object | O | `{}` |

**Returns:** `{ status: "queued", event }`

> ⚠ The key is `parameters`, **not** `properties`. Sending `properties` produces an event with no parameters — silently.

```js
await Native.call('analytics.trackEvent', {
  name: 'note_created',
  parameters: { length: 240, hasImage: false }
});
```

Missing `name` → `INVALID_PARAM`.

### 3.2 `analytics.recordMetric`

| Parameter | Type | Required | Default |
| --- | --- | --- | --- |
| `name` | string | **R** | — |
| `value` | number | **R** | — |
| `unit` | string | O | `"count"` |

**Returns:** `{ status: "recorded", metric }`

`value` must be a finite number; `NaN` and `Infinity` are rejected with `INVALID_PARAM`.

```js
await Native.call('analytics.recordMetric', { name: 'save_duration', value: 143, unit: 'ms' });
```

### 3.3 Sessions

| Action | Parameters | Returns |
| --- | --- | --- |
| `analytics.startSession` | — | `{ sessionId, startTime }` |
| `analytics.endSession` | — | `{ status: "ended", activeDurationMs }` |

```js
const { sessionId } = await Native.call('analytics.startSession');
window.addEventListener('pagehide', () => Native.call('analytics.endSession'));
```

### 3.4 Traces

| Action | Parameters | Returns |
| --- | --- | --- |
| `analytics.startTrace` | `name` (**R**) | `{ trace, status: "started" }` |
| `analytics.stopTrace` | `name` (**R**) | `{ trace, durationMs }` |

```js
await Native.call('analytics.startTrace', { name: 'render_list' });
renderList();
const { durationMs } = await Native.call('analytics.stopTrace', { name: 'render_list' });
```

### 3.5 Screen views

| Action | Parameters | Returns |
| --- | --- | --- |
| `analytics.startScreenView` | `screenName` (**R**) | `{ screenName }` |
| `analytics.endScreenView` | `screenName` (**R**) | `{ screenName, durationMs }` |

> ⚠ The key is `screenName`, **not** `screen`. Sending `screen` returns `INVALID_PARAM`.

```js
await Native.call('analytics.startScreenView', { screenName: 'notes_list' });
// … user navigates …
await Native.call('analytics.endScreenView', { screenName: 'notes_list' });
```

### 3.6 Inspection

| Action | Returns |
| --- | --- |
| `analytics.getStats` | `{ sessionId, sessionActive, totalActiveDurationMs, memoryEventCount, memoryMetricCount, offlineQueueCount }` |
| `analytics.flush` | `{ status: "flushed" }` |
| `analytics.clear` | `{ status: "cleared" }` |

```js
const stats = await Native.call('analytics.getStats');
if (stats.offlineQueueCount > 500) await Native.call('analytics.flush');
```

---

## 4. Complete example

```js
const Telemetry = {
  enabled: true,

  async init() {
    if (!this.enabled) return;
    try {
      await Native.call('analytics.startSession');
      await Native.call('analytics.startScreenView', { screenName: 'home' });
    } catch (e) {
      this.enabled = false;   // analytics route absent — degrade silently
    }
  },

  track(name, parameters) {
    if (!this.enabled) return Promise.resolve();
    return Native.call('analytics.trackEvent', { name, parameters: parameters || {} })
      .catch(() => {});
  },

  metric(name, value, unit) {
    if (!this.enabled) return Promise.resolve();
    return Native.call('analytics.recordMetric', { name, value, unit: unit || 'count' })
      .catch(() => {});
  },

  async screen(name) {
    if (!this.enabled) return;
    await Native.call('analytics.endScreenView', { screenName: this.current }).catch(() => {});
    this.current = name;
    await Native.call('analytics.startScreenView', { screenName: name }).catch(() => {});
  }
};
```

---

## 5. What is **not** implemented

| Assumed | Reality |
| --- | --- |
| `analytics.setUserId` | Not implemented |
| `analytics.setUserProperty` | Not implemented |
| `analytics.logScreen` | Use `analytics.startScreenView` |
| `analytics.crash` / `analytics.recordError` | Not implemented |
| `analytics.setEnabled` | Not implemented — gate in JS |
| `logger.getLogs` / `logger.read` | Not implemented — use the Runtime Inspector |
| `logger.clear` | Not implemented |

---

## 6. Reading logs during development

`logger.*` writes to Logcat. To read it inside the app, use the Runtime Inspector:

```java
RuntimeInspector.get().init(context);
RuntimeInspectorSheet sheet = RuntimeInspector.get().install(activity);
```

The inspector captures console messages, page events, resource errors, bridge requests/responses and rejections. See `DEVELOPER_TOOLS.md`.

---

## 7. Related documents

- `NATIVE_API_REFERENCE.md` §7 and §15
- `DEVELOPER_TOOLS.md` — Runtime Inspector
- `ERROR_CODES.md` — `INVALID_PARAM` and friends
