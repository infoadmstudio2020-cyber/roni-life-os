# Requirements Document

## 1. Application Overview

### 1.1 Application Name
RONi Android Library Generator (v0.1.3.6)

### 1.2 Application Description
A single-page web application (SPA) built on native frontend technologies (Vanilla JavaScript ES6+ Native Modules, HTML5, CSS3) designed for generating Android library and module boilerplate code, managing build configurations, packaging resources, and local export. Version v0.1.3.6 enhances mobile responsiveness across ecosystem version views and modal dialogs, introduces an automated and manual retry mechanism for JitPack build logs, and updates project versioning across all application components while preserving IndexedDB persistence and Android WebView compatibility.

---

## 2. Users and Use Cases

### 2.1 Target Users
Android application and library developers, mobile software engineers, and technical integrators using desktop browsers or embedded Android WebViews.

### 2.2 Core Use Cases
- Configure Android library metadata, module dependencies, and Gradle scripts.
- Browse and manage ecosystem versions with responsive UI support across both desktop and mobile viewports.
- Monitor and re-fetch JitPack build logs with a dedicated retry mechanism when log loading fails or times out.
- Preview generated source files and download the complete project as a standard ZIP archive client-side.
- Persist configurations in local IndexedDB with JSON import/export backup capabilities.

---

## 3. Page Structure and Functional Specifications

### 3.1 Page and Directory Structure
```text
RONi Android Library Generator (v0.1.3.6)
├── Main Workspace
│   ├── Project Basic Configuration Module
│   ├── Library & Dependency Configuration Module
│   ├── Code Preview & Generation Module
│   └── Packaging & Local ZIP Export
├── Ecosystem Version Management Page
│   ├── Version Overview & Graph Topology
│   ├── Version Stack Cards (Mobile Responsive)
│   └── Responsive Action Bar
├── JitPack Integration & Log Viewer Modal
│   ├── Build Status & Stream Display
│   └── Retry Action Button & Auto-Retry Trigger
├── Local Data & Project Management Panel
│   ├── Stored Configuration List (IndexedDB)
│   └── Configuration Import/Export & Reset
└── Common UI Components (Modal Dialog System)
```

### 3.2 Core Functional Specifications

#### 3.2.1 Project Basic Configuration
- Input and edit Android library metadata (library name, package name, version, min SDK, target SDK, compile SDK).
- Real-time form validation for package naming conventions and required identifiers.

#### 3.2.2 Library & Dependency Configuration
- Add, edit, and remove module components and AndroidX/external dependencies.
- Generate corresponding `build.gradle` and `settings.gradle` configuration scripts.

#### 3.2.3 Code Preview & Generation
- Real-time directory tree and source code syntax preview based on active configuration.
- One-click clipboard copy for individual files and snippets.

#### 3.2.4 Packaging & Client-Side Export
- Generate and download a standard Android library project ZIP archive directly on the client side without backend build servers.

#### 3.2.5 Ecosystem Version Management Page UI Enhancements
- Display version overview and dependency topology with adaptive scaling for mobile screens.
- Implement mobile-first card stacking layout for version entries, optimized typography, and touch target sizing (minimum 44x44px).
- Provide responsive bottom/inline action bars adapting between desktop row layouts and mobile stacked buttons.

#### 3.2.6 JitPack Build Log Viewer & Retry System
- Dedicated modal dialog displaying real-time or fetched JitPack build logs.
- Dedicated Retry button in the log viewer header/footer to manually re-request build status and logs upon network timeout or fetch failure.
- State management for log polling: loading, success, failure/timeout, and retrying states.

#### 3.2.7 Modal Dialog & Popup System Enhancements
- Unified modal dialog component centered precisely on both desktop and mobile viewports.
- Proper viewport padding and margin constraints on mobile screens to prevent content overflow.
- Backdrop overlay with smooth transition animations and accessible dismiss/close controls.

#### 3.2.8 Local Storage & System Synchronization
- Persist and load project configurations in browser IndexedDB without data loss across version upgrades.
- JSON file export and import for full configuration backup.
- Synchronize application version identifier to `v0.1.3.6` across UI headers, footers, `package.json`, `version.json`, and documentation.

---

## 4. Business Rules and Core Logic

1. **Vanilla Architecture Rule**: Pure ES6 Native Modules, HTML5, and CSS3 must be utilized without introduction of heavy JavaScript frameworks or external bundle tooling.
2. **Version Synchronization Rule**: Application version must consistently reflect `v0.1.3.6` across UI displays and configuration manifests.
3. **JitPack Log Retry Policy**: When a log fetch request fails or times out, the UI must transition to a failure state and activate the Retry button to enable immediate manual re-polling.
4. **Modal Layout Rule**: All modal dialogs must enforce maximum height constraints (e.g., 90vh) with internal scrolling for overflowing content, ensuring action buttons remain visible and screen-centered.
5. **WebView Compatibility Rule**: Asset paths, native module loading, and client-side ZIP generation must operate reliably in modern desktop browsers and Android System WebViews.

---

## 5. Exceptions and Edge Cases

| Scenario | Exception / Edge Case | Handling Policy |
| :--- | :--- | :--- |
| JitPack Log Fetch | Network failure or JitPack 404/timeout | Display descriptive error state in log modal, keep Retry button active for user re-trigger |
| Small Screen Modal | Dialog content exceeds screen dimensions | Constrain modal to viewport height, enable internal vertical scroll, keep close/action buttons accessible |
| Local Storage | IndexedDB unavailable or quota exceeded | Display warning banner, fallback to in-memory state, and prompt user to export JSON backup |
| File Import | Invalid or corrupted JSON configuration uploaded | Abort import process, retain existing workspace state, and show error notification |

---

## 6. Acceptance Criteria

1. The application version is updated to v0.1.3.6 across UI headers, footers, package.json, version.json, and documentation.
2. The Ecosystem Version Management page displays stacked responsive cards, legible typography, properly scaled graph topology, and touch targets on mobile viewports.
3. The JitPack build log viewer provides a functioning Retry button that re-triggers the log fetching workflow upon failure or timeout.
4. All modal dialogs are screen-centered with smooth backdrops, proper mobile margins, non-overflowing internal scroll containers, and clear close buttons.
5. Project generation, IndexedDB storage, JSON import/export, and client-side ZIP packaging function correctly without errors.
6. The application loads and functions seamlessly using native ES Modules in both standard browsers and Android WebView environments.

---

## 7. Out of Scope

1. Server-side remote compilation of APK or AAR binaries.
2. Cloud-based user account authentication and remote database sync.
3. Full-featured in-browser code editor IDE and debugging runtime.