/**
 * Core Layer Entry Point
 * Exports all fundamental application infrastructure components.
 */

export { eventBus, EventBus } from './EventBus.js';
export { Router } from './Router.js';
export { i18n, I18n } from './I18n.js';
export { WebViewBridge, WebViewBridge as webViewBridge } from './WebViewBridge.js';
export { ClipboardUtil, copyToClipboard } from './ClipboardUtil.js';
export { escapeHtml, stripHtml, createElement } from './DomUtil.js';
