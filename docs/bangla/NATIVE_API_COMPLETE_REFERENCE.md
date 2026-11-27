# Native API Complete Reference — Phase 12

## ১. স্তর

```
JavaScript
  └─ Native.theme / .security / .analytics / .devtools
     .network / .platform / .logger / .service / .storage
        └─ NativeRuntimeBridgeClient.call(action, payload)
             └─ WebBridge → NativeRouter → IApiHandler
                  └─ ThemeManager / SecurityEngine / NativeAnalyticsService / ...
```

## ২. নয়টি namespace

| Namespace | Action | Handler | নিবন্ধনকারী |
|---|---|---|---|
| `theme` | ৬ | `ThemeCommandHandler` | `NativeApiIntegration` |
| `security` | ১১ | `SecurityCommandHandler` | `NativeApiIntegration` |
| `analytics` | ১১ | `AnalyticsCommandHandler` | `NativeApiIntegration` |
| `devtools` | ৯ | `DevToolsCommandHandler` | `NativeApiIntegration` (debug only) |
| `network` | ১ | `NetworkCommandHandler` | `NativeApiIntegration` |
| `platform` | ১৫ | `PlatformCommandHandler` | `NativeApiIntegration` |
| `logger` | ৪ | `LoggerCommandHandler` | `NativeApiIntegration` |
| `service` | ২ | `ServiceCommandHandler` | `NativeApiIntegration` |
| `storage` | ৭ | `NativeStorageCommandHandler` | `NativeRuntimeUI` |

আগের ১৫২টি action (ui/web/device/storage) অপরিবর্তিত।

## ৩. JS API

```js
import Native, { theme, security, analytics, devtools,
                 network, platform, logger, service, storage } from './native-runtime.js';

Native.available();          // bridge আছে?
await Native.isSupported('devtools');  // route নিবন্ধিত?
```

### theme
`get()` `setMode(mode)` `apply(config)` `isSystemDark()` `setSystemDark(dark)` `cssVariables()` `onChange(fn)`

### security
`capability()` `authenticate(title)` `onResult(fn)` `checkPermission(p)` `sha256(text)`
`keystore.generate/hasKey/delete/list/encrypt/decrypt` `diagnostic()`

### analytics
`track(name, props)` `metric(name, value)` `startSession()` `endSession()`
`startTrace(name)` `stopTrace(name)` `screenView(s)` `endScreenView(s)`
`stats()` `flush()` `clear()`

### devtools
`metrics()` `health()` `bridgeMetrics()` `manifest()` `exportHar()` `clearTraffic()`
`startProfiling()` `stopProfiling()` `dispatchCdp(domain, method, params)`

### network
`request(url, opts)` `get` `post` `put` `patch` `delete`

### platform
`device()` `battery()` `networkStatus()` `storageStats()` `clipboard.get/set`
`toast(text)` `vibrate(ms)` `diagnostic()` `health()`

### logger
`debug` `info` `warn` `error` `setLevel` `getLevel` `flush`

### service
`list()` `status(id)`

### storage
`get` `set` `remove` `clear` `keys` `has` `getAll`

## ৪. ত্রুটি

প্রতিটি rejection-এ:

```js
{ name: 'Error', message: 'CODE: detail', code: 'CODE', action: 'theme.getTheme' }
```

| Code | অর্থ |
|---|---|
| `NATIVE_UNAVAILABLE` | bridge নেই |
| `CLIENT_TIMEOUT` | ১৫s অতিক্রম |
| `ROUTE_NOT_FOUND` | handler নিবন্ধিত নয় |
| `UNKNOWN_ACTION` | handler আছে, action নেই |
| `INVALID_URL` / `UNSUPPORTED_SCHEME` | network |
| `FORBIDDEN_HEADER` | network |

## ৫. ইভেন্ট

| Event | কখন | Payload |
|---|---|---|
| `theme.changed` | theme বদলালে | theme state |
| `security.biometric.result` | biometric শেষে | `{status, method, errorCode, message}` |
| `ui.confirm` | confirm ডায়ালগ | `{confirmed}` |

## ৬. যা নেই

`biometric.cancel`, `network.cancel`, `service.start/stop`, `logger` retrieval,
`analytics` consent, project-scoped theme, WebSocket/SSL-pinning JS action।
