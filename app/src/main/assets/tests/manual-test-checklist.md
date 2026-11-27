# Manual Testing Checklist: RONi Android Library Generator

## Environment Matrix
- [x] Modern Chrome (Desktop 1920x1080, 1366x768)
- [x] Modern Firefox & Edge
- [x] Android WebView (Chromium-based mobile webview)
- [x] Standalone Offline Execution (Local `file://` or offline localhost server)

---

## 1. Application Startup & Initialization
- [x] Verify app loads with zero console errors.
- [x] Verify local `js/vendor/jszip.min.js` loads immediately without internet access.
- [x] Verify fallback to CDN is only triggered if local script is intentionally absent.
- [x] Verify initial route defaults to Dashboard (`#dashboard`).
- [x] Verify Theme preference (Dark / Light) loads from StorageService safely.
- [x] Verify Language preference (English / Bengali) switches and persists.

---

## 2. Navigation & Shell Layout
- [x] Sidebar expand/collapse toggle functions smoothly with proper animations.
- [x] Mobile bottom navigation responds to viewport changes under 768px.
- [x] All 20+ routes render the appropriate page controller without broken references.
- [x] Console drawer opens, closes, clears logs, and copies logs via `ClipboardUtil`.

---

## 3. UI Component Integrity & Spinner Tests
- [x] Verify `.spinner` component in `css/components/spinners.css` renders circular animation.
- [x] Verify buttons with `.spinner` maintain vertical alignment and do not break layout or wrap unexpectedly.
- [x] Verify modal centered spinners (`.spinner.spinner-lg`) in GitHub Import modal render centered with text.
- [x] Verify testing page spinners (`.ght-spinner`) animate smoothly.
- [x] Verify zero layout shift when async buttons enter loading state.

---

## 4. Templates & AI Specification Generator
- [x] All 5 Android/JVM templates display in Templates catalog with metadata badges.
- [x] "Export AI Specification" modal opens with valid JSON specification.
- [x] "Export Required Information Schema" modal opens with JSON Schema Draft-07 compliant schema.
- [x] Copy button successfully copies schema to clipboard via `ClipboardUtil`.
- [x] Download button exports `.json` file locally.

---

## 5. Project Import, Analysis & AST Gradle Engine
- [x] Open existing Android project directory or upload `.zip`.
- [x] Environment Analyzer inspects AGP, Gradle, JDK, and compileSdk correctly.
- [x] Class Conflict & D8 Guard checks duplicate classes and Kotlin stdlib collisions.
- [x] Gradle AST Helper adds dependencies with strict brace-balance preservation.
- [x] Version Manager scans version definitions across files and supports selective update & rollback.

---

## 6. Offline Usability & WebView Resilience
- [x] Cut off network connection completely: All templates, AST engines, and ZIP exporters function 100%.
- [x] Non-HTTPS context or Android WebView without `navigator.clipboard`: `ClipboardUtil` gracefully falls back to `execCommand('copy')` and succeeds.
- [x] Safe LocalStorage wrapper catches `QuotaExceededError` or private browsing restrictions without unhandled exceptions.
