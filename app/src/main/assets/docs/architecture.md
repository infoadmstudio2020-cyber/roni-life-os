# Architecture Documentation: RONi Android Library Generator

## Overview
RONi Android Library Generator is an offline-first, client-side single-page application (SPA) built with pure HTML5, CSS3, and modern Vanilla JavaScript ES6 Modules. It contains zero external runtime dependencies, zero Node.js server dependencies, and requires no build step to run in modern browsers or Android WebView.

## Architecture Layers

```
┌────────────────────────────────────────────────────────┐
│                   UI Presentation Layer                │
│    (index.html, CSS Modules, DOM Component Pages)       │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│                   Core Application State               │
│   (Router.js, EventBus.js, ClipboardUtil.js, App.js)   │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│                 Domain Business Modules                │
│  - Project Analysis & Environment Inspector             │
│  - Gradle AST Helper & Dependency Brace Engine          │
│  - Class Conflict & D8 Packaging Guard                 │
│  - GitHub Actions Wizard & JitPack Hardening           │
│  - AI-Ready Project Spec & Schema Generator            │
│  - Version Manager & Selective Update Engine           │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│               Storage & Infrastructure Layer           │
│  - StorageService (L1 Memory Cache + LocalStorage)     │
│  - ProjectService (Project State Snapshotting)         │
│  - Local JSZip (v3.10.1 Bundled for Offline Archive)   │
└────────────────────────────────────────────────────────┘
```

## Key Architectural Principles

1. **Zero External Runtime Dependencies**:
   - All core operations run client-side in the user's browser.
   - JSZip is bundled locally under `js/vendor/jszip.min.js`.
   - Fonts and Material Symbols degrade gracefully in complete offline environments.

2. **Android WebView & Cross-Browser Ergonomics**:
   - Dual-mode clipboard support (`ClipboardUtil.js` provides `navigator.clipboard` with `execCommand('copy')` fallback).
   - Viewport supports `viewport-fit=cover` without restrictive `user-scalable=no` meta tags.
   - Safe LocalStorage wrapper ensures zero crashes when cookies or storage are restricted.

3. **Defensive AST & Parsing Engines**:
   - `GradleAstHelper.js`: Modifies Groovy and Kotlin DSL build scripts with strict brace-balance verification.
   - `D8PackagingGuard.js` & `BinaryClassInspector.js`: Detects duplicate classes before packaging.
   - `VersionUpdateEngine.js`: Selective updates with automatic backup and rollback.

4. **Modular CSS Design System**:
   - Atomic CSS layers: `base/`, `layout/`, `components/`, `pages/`, `responsive/`.
   - Dedicated `spinners.css` prevents button/modal UI breaks during async operations.
   - Native CSS variables with instant dark/light theme switching.
