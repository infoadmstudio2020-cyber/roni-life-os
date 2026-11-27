/**
 * WebViewBridge — Android WebView and Browser Compatibility Bridge.
 *
 * Provides resilient cross-environment abstraction for:
 * 1. Android WebView detection and capabilities probing
 * 2. Safe file downloads (Native Android bridge -> Blob URL with delayed revocation -> Base64 data URI fallback)
 * 3. File picking with DOM attachment protection for Android WebChromeClient
 * 4. Clipboard fallback integration
 * 5. Diagnostic reporting for environment inspection
 */

export class WebViewBridge {
  /**
   * Detect current runtime environment details.
   * @returns {object} Environment information
   */
  static getEnvironment() {
    const ua = typeof navigator !== 'undefined' ? navigator.userAgent || '' : '';
    const isAndroid = /Android/i.test(ua);
    const isWebView = isAndroid && (
      /;\s*wv\b/i.test(ua) || // Android Lollipop+ WebView indicator
      /Version\/[\d.]+\s+Chrome/i.test(ua) || // Legacy Android WebView indicator
      typeof window !== 'undefined' && (!!window.AndroidBridge || !!window.Android || !!window.WebAppInterface)
    );
    const isStandalone = typeof window !== 'undefined' && (
      window.matchMedia?.('(display-mode: standalone)').matches ||
      navigator.standalone === true
    );
    const protocol = typeof window !== 'undefined' && window.location ? window.location.protocol : 'unknown:';
    const isFileProtocol = protocol === 'file:';
    const isAssetLoader = typeof window !== 'undefined' && window.location && (
      window.location.hostname === 'appassets.androidplatform.net' ||
      window.location.host.includes('androidplatform.net')
    );
    const hasBridge = typeof window !== 'undefined' && (
      typeof window.AndroidBridge === 'object' ||
      typeof window.Android === 'object' ||
      typeof window.WebAppInterface === 'object'
    );

    return {
      ua,
      isAndroid,
      isWebView,
      isStandalone,
      protocol,
      isFileProtocol,
      isAssetLoader,
      hasBridge,
      isSecureContext: typeof window !== 'undefined' && !!window.isSecureContext,
      hasIndexedDB: typeof indexedDB !== 'undefined',
      hasJSZip: typeof window !== 'undefined' && typeof window.JSZip !== 'undefined',
      hasFetch: typeof fetch === 'function',
      hasBlob: typeof Blob !== 'undefined' && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function',
      hasClipboard: typeof navigator !== 'undefined' && !!navigator.clipboard,
    };
  }

  /**
   * Download or save a file using the best available mechanism:
   * 1. Native Android JavascriptInterface (if present)
   * 2. Browser Blob download with 60s delayed revocation (prevents premature teardown)
   * 3. Base64 Data URL fallback for WebViews that block blob URLs
   *
   * @param {string} filename - Target file name (e.g. "my-lib.zip")
   * @param {Blob|Uint8Array|string} data - Content to download
   * @param {string} [mimeType='application/octet-stream'] - MIME type
   * @returns {Promise<{success: boolean, method: string, error?: string}>}
   */
  static async downloadFile(filename, data, mimeType = 'application/octet-stream') {
    const env = this.getEnvironment();

    // 1. Try Native Android Bridge if present
    if (env.hasBridge) {
      try {
        const base64Data = await this.toBase64(data);
        const bridge = window.AndroidBridge || window.Android || window.WebAppInterface;

        if (typeof bridge.saveFile === 'function') {
          bridge.saveFile(filename, base64Data, mimeType);
          return { success: true, method: 'android-bridge:saveFile' };
        }
        if (typeof bridge.downloadBlob === 'function') {
          bridge.downloadBlob(filename, base64Data, mimeType);
          return { success: true, method: 'android-bridge:downloadBlob' };
        }
        if (typeof bridge.saveBase64 === 'function') {
          bridge.saveBase64(filename, base64Data);
          return { success: true, method: 'android-bridge:saveBase64' };
        }
      } catch (bridgeErr) {
        console.warn('[WebViewBridge] Native bridge save failed, falling back to browser download:', bridgeErr);
      }
    }

    // 2. Standard Browser Blob Download with delayed URL revocation
    if (env.hasBlob && (data instanceof Blob || typeof Blob !== 'undefined')) {
      try {
        const blob = data instanceof Blob ? data : new Blob([data], { type: mimeType });
        const objectUrl = URL.createObjectURL(blob);

        const anchor = document.createElement('a');
        anchor.href = objectUrl;
        anchor.download = filename;
        anchor.style.position = 'fixed';
        anchor.style.left = '-9999px';
        anchor.style.display = 'none';

        document.body.appendChild(anchor);
        anchor.click();

        // Remove anchor from DOM
        setTimeout(() => {
          if (anchor.parentNode) {
            anchor.parentNode.removeChild(anchor);
          }
        }, 500);

        // DO NOT synchronously revoke! Delay revocation by 60 seconds so Android WebView
        // and background download threads can stream the data reliably.
        setTimeout(() => {
          try {
            URL.revokeObjectURL(objectUrl);
          } catch {
            /* ignore */
          }
        }, 60000);

        return { success: true, method: 'browser:blob-delayed-revoke' };
      } catch (blobErr) {
        console.warn('[WebViewBridge] Blob download failed, attempting data URL fallback:', blobErr);
      }
    }

    // 3. Data URL fallback (useful for smaller files or WebViews with strict blob: restrictions)
    try {
      const base64Data = await this.toBase64(data);
      const dataUrl = `data:${mimeType};base64,${base64Data}`;

      const anchor = document.createElement('a');
      anchor.href = dataUrl;
      anchor.download = filename;
      anchor.style.position = 'fixed';
      anchor.style.left = '-9999px';
      anchor.style.display = 'none';

      document.body.appendChild(anchor);
      anchor.click();

      setTimeout(() => {
        if (anchor.parentNode) {
          anchor.parentNode.removeChild(anchor);
        }
      }, 1000);

      return { success: true, method: 'browser:data-url' };
    } catch (err) {
      console.error('[WebViewBridge] All download mechanisms failed:', err);
      return { success: false, method: 'none', error: err.message };
    }
  }

  /**
   * Helper to convert Blob, Uint8Array, or string to a Base64 string.
   * @param {Blob|Uint8Array|string} data
   * @returns {Promise<string>} Base64 string
   */
  static toBase64(data) {
    if (typeof data === 'string') {
      try {
        return Promise.resolve(btoa(unescape(encodeURIComponent(data))));
      } catch {
        return Promise.resolve(btoa(data));
      }
    }
    if (data instanceof Uint8Array) {
      let binary = '';
      const len = data.byteLength;
      for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(data[i]);
      }
      return Promise.resolve(btoa(binary));
    }
    if (data instanceof Blob) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          const res = reader.result;
          if (typeof res === 'string') {
            const commaIdx = res.indexOf(',');
            resolve(commaIdx >= 0 ? res.slice(commaIdx + 1) : res);
          } else {
            reject(new Error('FileReader returned non-string result'));
          }
        };
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(data);
      });
    }
    return Promise.resolve('');
  }

  /**
   * Trigger file picker safely with DOM attachment so WebChromeClient.onShowFileChooser
   * is guaranteed to catch the intent.
   *
   * @param {string} accept - Accept attribute (e.g. ".zip,application/zip")
   * @param {function(File): void} onFileSelected - Callback with selected file
   */
  static openFilePicker(accept = '.zip', onFileSelected) {
    // 1. Check if Android Bridge has native picker
    const env = this.getEnvironment();
    if (env.hasBridge) {
      const bridge = window.AndroidBridge || window.Android || window.WebAppInterface;
      if (typeof bridge.openFilePicker === 'function') {
        window.__onAndroidFilePicked = (base64Content, fileName) => {
          delete window.__onAndroidFilePicked;
          try {
            const binary = atob(base64Content);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            const file = new File([bytes], fileName, { type: 'application/zip' });
            if (typeof onFileSelected === 'function') onFileSelected(file);
          } catch (e) {
            console.error('[WebViewBridge] Failed decoding native picked file:', e);
          }
        };
        bridge.openFilePicker(accept, '__onAndroidFilePicked');
        return;
      }
    }

    // 2. Browser DOM File Input with body attachment protection
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.style.position = 'fixed';
    input.style.left = '-9999px';
    input.style.top = '-9999px';
    input.style.opacity = '0';
    input.style.pointerEvents = 'none';

    const cleanup = () => {
      setTimeout(() => {
        if (input.parentNode) {
          input.parentNode.removeChild(input);
        }
      }, 1000);
    };

    input.addEventListener('change', () => {
      const file = input.files?.[0];
      if (file && typeof onFileSelected === 'function') {
        onFileSelected(file);
      }
      cleanup();
    });

    document.body.appendChild(input);
    input.click();
  }

  /**
   * Safe native toast invocation if available.
   * @param {string} message
   */
  static showNativeToast(message) {
    const env = this.getEnvironment();
    if (env.hasBridge) {
      const bridge = window.AndroidBridge || window.Android || window.WebAppInterface;
      if (typeof bridge.showToast === 'function') {
        try {
          bridge.showToast(message);
          return true;
        } catch {
          /* ignore */
        }
      }
    }
    return false;
  }
}
