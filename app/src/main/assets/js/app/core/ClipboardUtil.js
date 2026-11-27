/**
 * ClipboardUtil — Resilient cross-browser clipboard utility.
 *
 * Supports:
 * - Modern secure browsers (Chrome, Edge, Firefox, Safari) via navigator.clipboard.writeText
 * - Android WebView & local file:// offline contexts via textarea + document.execCommand('copy')
 * - Safe exception handling preventing uncaught DOMException/NotAllowedError
 */

export class ClipboardUtil {
  /**
   * Copy arbitrary text to system clipboard with robust fallback for Android WebView.
   *
   * @param {string} text - Text to copy
   * @returns {Promise<boolean>} True if copy was successful
   */
  static async copyText(text) {
    if (typeof text !== 'string') {
      text = String(text ?? '');
    }

    // Attempt 0: Native Android Bridge if present (e.g. Android WebView host app interface)
    if (typeof window !== 'undefined') {
      const bridge = window.AndroidBridge || window.Android || window.WebAppInterface;
      if (bridge && typeof bridge.copyToClipboard === 'function') {
        try {
          bridge.copyToClipboard(text);
          return true;
        } catch {
          // Continue to web fallbacks
        }
      }
    }

    // Attempt 1: Modern asynchronous Clipboard API (when available and in secure context)
    if (typeof navigator !== 'undefined' && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch (err) {
        // May fail in Android WebView or insecure contexts (file://, http://)
        // Gracefully continue to fallback
      }
    }

    // Attempt 2: Document execCommand fallback (works in Android WebView, local file://, iframes)
    if (typeof document !== 'undefined' && typeof document.createElement === 'function') {
      try {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', '');
        textarea.style.position = 'fixed';
        textarea.style.top = '0';
        textarea.style.left = '-9999px';
        textarea.style.width = '2em';
        textarea.style.height = '2em';
        textarea.style.padding = '0';
        textarea.style.border = 'none';
        textarea.style.outline = 'none';
        textarea.style.boxShadow = 'none';
        textarea.style.background = 'transparent';
        textarea.style.opacity = '0';

        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();

        // For mobile/iOS/WebView selection range support
        if (typeof textarea.setSelectionRange === 'function') {
          textarea.setSelectionRange(0, textarea.value.length);
        }

        const success = document.execCommand('copy');
        document.body.removeChild(textarea);
        return !!success;
      } catch (fallbackErr) {
        console.warn('[ClipboardUtil] Copy fallback failed:', fallbackErr);
        return false;
      }
    }

    return false;
  }
}


export const copyToClipboard = (text) => ClipboardUtil.copyText(text);