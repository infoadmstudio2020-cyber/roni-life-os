# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.1 | ✅ |

## Reporting a Vulnerability

Please report security vulnerabilities by opening a GitHub issue.
Include steps to reproduce and potential impact.
We will respond within 72 hours.

## Automation platform

- **Project isolation** — every task, job, alarm, transfer and event is keyed by the project from
  `AutomationProjectContext`. The `/automation` route ignores any `projectId` sent by JavaScript,
  so a Web App cannot read or cancel another project's work.
- **No direct service control** — Web Apps can only call `startService` / `stopService` on names
  already registered in `NativeServiceSDK`; they cannot launch Android components, bind services
  or start activities.
- **Path confinement** — transfer paths are resolved through `FileManagerRepository`, which
  rejects traversal outside the project's app-private directory. Only relative paths are exposed
  back to the bridge.
- **URL allowlist** — transfers accept `http` and `https` only; `file:`, `content:` and other
  schemes are rejected before any connection is opened.
- **Bounded resources** — limits on tasks, jobs, alarms, transfers, event buffer size, URL length
  and payload size prevent a Web App from exhausting memory or storage.
- **Encrypted metadata** — persisted automation state uses `EncryptedPreferences`
  (`automation_meta`); no plain-text files and no second storage engine.
- **Permissions** — `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`,
  `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`. Exact alarms degrade to inexact when denied.
- **Threading** — all blocking work runs on executors; the main thread only renders snapshots.
