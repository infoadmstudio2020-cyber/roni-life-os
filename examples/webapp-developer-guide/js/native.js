/**
 * RONiLiFEOS — রেফারেন্স ব্রিজ র‍্যাপার
 *
 * এই ফাইলটিই একমাত্র জায়গা যেখানে native bridge সরাসরি ব্যবহৃত হয়।
 * প্রতিটি প্যারামিটারের নাম Java handler-এর `getParameter("...")` কল থেকে নেওয়া।
 *
 * প্রকৃত transport:
 *   window.NativeRuntimeBridgeClient.call(action, payload) -> Promise
 *   window.NativeRuntimeBridgeClient.on(topic, listener)
 *   window.NativeRuntimeBridgeClient.off(topic, listener)
 */
(function (global) {
  'use strict';

  var DEFAULT_TIMEOUT = 15000;

  /* ---------------------------------------------------------------- */
  /* transport                                                         */
  /* ---------------------------------------------------------------- */

  function available() {
    return typeof global.NativeRuntimeBridgeClient !== 'undefined'
        && typeof global.NativeRuntimeBridgeClient.call === 'function';
  }

  function codeOf(text) {
    var match = String(text).match(/^([A-Z][A-Z0-9_]{2,})/);
    return match ? match[1] : 'NATIVE_ERROR';
  }

  function call(action, payload, timeoutMs) {
    if (!available()) {
      var missing = new Error('NATIVE_UNAVAILABLE');
      missing.code = 'NATIVE_UNAVAILABLE';
      missing.action = action;
      return Promise.reject(missing);
    }

    return new Promise(function (resolve, reject) {
      var settled = false;
      var timer = setTimeout(function () {
        if (settled) return;
        settled = true;
        var timeout = new Error('CLIENT_TIMEOUT');
        timeout.code = 'CLIENT_TIMEOUT';
        timeout.action = action;
        reject(timeout);
      }, timeoutMs || DEFAULT_TIMEOUT);

      global.NativeRuntimeBridgeClient.call(action, payload || {}).then(
        function (result) {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve(result);
        },
        function (error) {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          var text = String(error && error.message ? error.message : error);
          var normalized = new Error(text);
          normalized.code = codeOf(text);
          normalized.action = action;
          reject(normalized);
        }
      );
    });
  }

  function on(topic, handler) {
    if (!available()) return function () {};
    global.NativeRuntimeBridgeClient.on(topic, handler);
    return function () {
      if (available()) global.NativeRuntimeBridgeClient.off(topic, handler);
    };
  }

  /** bridge client ইনজেক্ট হওয়া পর্যন্ত অপেক্ষা করে (onPageFinished-এর পরে)। */
  function ready(timeoutMs) {
    var deadline = Date.now() + (timeoutMs || 5000);
    return new Promise(function (resolve, reject) {
      (function poll() {
        if (available()) return resolve(true);
        if (Date.now() > deadline) {
          var error = new Error('BRIDGE_NOT_READY');
          error.code = 'BRIDGE_NOT_READY';
          return reject(error);
        }
        setTimeout(poll, 50);
      })();
    });
  }

  /** একটি ইভেন্টের জন্য একবার অপেক্ষা করে। */
  function once(topic, timeoutMs) {
    return new Promise(function (resolve, reject) {
      var settled = false;
      var timer = setTimeout(function () {
        if (settled) return;
        settled = true;
        off();
        var error = new Error('EVENT_TIMEOUT');
        error.code = 'EVENT_TIMEOUT';
        error.topic = topic;
        reject(error);
      }, timeoutMs || 10000);

      var off = on(topic, function (payload) {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        off();
        resolve(payload);
      });
    });
  }

  /* ---------------------------------------------------------------- */
  /* feature probing                                                   */
  /* ---------------------------------------------------------------- */

  var PROBES = {
    ui: ['ui.getConfig', {}],
    web: ['web.getState', {}],
    device: ['device.getAppInfo', {}],
    storage: ['storage.keys', {}],
    theme: ['theme.getTheme', {}],
    security: ['security.diagnostic', {}],
    analytics: ['analytics.getStats', {}],
    devtools: ['devtools.getHealth', {}],
    network: ['network.request', { url: 'https://example.com' }],
    platform: ['platform.diagnostic', {}],
    notification: ['notification.status', {}],
    camera: ['camera.getCapabilities', {}],
    filemanager: ['filemanager.getStorageInfo', {}],
    automation: ['automation.getStatus', {}],
    logger: ['logger.getLevel', {}],
    service: ['service.list', {}],
    runtime: ['runtime.getVitals', {}]
  };

  var probeCache = {};

  function isSupported(namespace) {
    if (Object.prototype.hasOwnProperty.call(probeCache, namespace)) {
      return Promise.resolve(probeCache[namespace]);
    }
    var probe = PROBES[namespace];
    if (!probe) return Promise.resolve(false);

    return call(probe[0], probe[1]).then(
      function () {
        probeCache[namespace] = true;
        return true;
      },
      function (error) {
        var supported = error.code !== 'ROUTE_NOT_FOUND' && error.code !== 'NATIVE_UNAVAILABLE';
        probeCache[namespace] = supported;
        return supported;
      }
    );
  }

  /* ---------------------------------------------------------------- */
  /* typed helpers — প্যারামিটারের নাম হুবহু native অনুযায়ী            */
  /* ---------------------------------------------------------------- */

  var ui = {
    toast: function (message, type, duration) {
      return call('ui.showToast', { message: message, type: type || 'info', duration: duration || 'short' });
    },
    alert: function (title, message) {
      return call('ui.showAlert', { title: title, message: message });
    },
    confirm: function (title, message, confirmText, cancelText) {
      var answer = once('ui.confirm');
      return call('ui.showConfirm', {
        title: title,
        message: message,
        confirmText: confirmText || 'OK',
        cancelText: cancelText || 'Cancel'
      }).then(function () {
        return answer.then(function (payload) { return !!payload.confirmed; });
      });
    },
    loading: function (text) {
      var shown = false;
      return {
        show: function () {
          if (shown) return Promise.resolve();
          shown = true;
          return call('ui.showLoading', { text: text || 'লোড হচ্ছে...' });
        },
        hide: function () {
          if (!shown) return Promise.resolve();
          shown = false;
          return call('ui.hideLoading');
        }
      };
    },
    title: function (title, subtitle) {
      var chain = call('ui.setToolbarTitle', { title: title });
      if (subtitle !== undefined) chain = chain.then(function () { return call('ui.setToolbarSubtitle', { subtitle: subtitle }); });
      return chain;
    },
    actionSheet: function (title, items) {
      var answer = once('ui.actionSelected');
      return call('ui.showActionSheet', { title: title, items: items }).then(function () {
        return answer;
      });
    },
    snackbar: function (message, actionTitle) {
      return call('ui.showSnackbar', { message: message, actionTitle: actionTitle || '' });
    }
  };

  var device = {
    info: function () { return call('device.getInfo'); },
    app: function () { return call('device.getAppInfo'); },
    battery: function () { return call('device.getBattery'); },
    network: function () { return call('device.getNetworkState'); },
    screen: function () { return call('device.getScreenInfo'); },
    locale: function () { return call('device.getLocale'); },
    safeArea: function () { return call('device.getSafeArea'); },
    copy: function (text) { return call('device.copyToClipboard', { text: text }); },
    paste: function () { return call('device.readClipboard'); },
    share: function (text, subject) { return call('device.shareText', { text: text, subject: subject || '' }); },
    vibrate: function () { return call('device.vibrate'); },
    openUrl: function (url) { return call('device.openExternal', { url: url }); },
    orientation: function (value) { return call('device.setOrientation', { orientation: value }); },
    keepScreenOn: function (enabled) { return call('device.setKeepScreenOn', { enabled: !!enabled }); },
    brightness: function (value) { return call('device.setBrightness', { value: value }); },
    openSettings: function () { return call('device.openAppSettings'); }
  };

  var storage = {
    set: function (key, value) { return call('storage.set', { key: key, value: value }); },
    get: function (key) { return call('storage.get', { key: key }); },
    has: function (key) { return call('storage.has', { key: key }); },
    remove: function (key) { return call('storage.remove', { key: key }); },
    keys: function () { return call('storage.keys'); },
    all: function () { return call('storage.getAll'); },
    clear: function () { return call('storage.clear'); },
    setJSON: function (key, object) {
      return call('storage.set', { key: key, value: JSON.stringify(object) });
    },
    getJSON: function (key, fallback) {
      return call('storage.get', { key: key }).then(function (result) {
        if (!result.exists || result.value === null) return fallback === undefined ? null : fallback;
        try { return JSON.parse(result.value); } catch (e) { return fallback === undefined ? null : fallback; }
      });
    }
  };

  var theme = {
    get: function () { return call('theme.getTheme'); },
    setMode: function (mode) { return call('theme.setThemeMode', { mode: String(mode).toUpperCase() }); },
    apply: function (config) { return call('theme.applyTheme', config || {}); },
    isSystemDark: function () { return call('theme.isSystemDark'); },
    cssVariables: function () { return call('theme.getCssVariables'); },
    onChange: function (handler) { return on('theme.changed', handler); }
  };

  var security = {
    capability: function () { return call('security.biometric.capability'); },
    authenticate: function (title) {
      var result = once('security.biometric.result', 60000);
      return call('security.biometric.authenticate', { title: title || 'Authentication' }).then(function () {
        return result;
      });
    },
    checkPermission: function (permission) { return call('security.permission.check', { permission: permission }); },
    sha256: function (input) { return call('security.hash.sha256', { input: input }); },
    keystore: {
      generate: function (alias, algorithm) {
        return call('security.keystore.generate', { alias: alias, algorithm: algorithm || 'AES_GCM_256' });
      },
      hasKey: function (alias) { return call('security.keystore.hasKey', { alias: alias }); },
      delete: function (alias) { return call('security.keystore.delete', { alias: alias }); },
      list: function () { return call('security.keystore.list'); },
      encrypt: function (alias, plaintext) {
        return call('security.keystore.encrypt', { alias: alias, plaintext: plaintext });
      },
      decrypt: function (alias, ciphertext, iv) {
        return call('security.keystore.decrypt', { alias: alias, ciphertext: ciphertext, iv: iv });
      }
    },
    diagnostic: function () { return call('security.diagnostic'); }
  };

  var analytics = {
    track: function (name, parameters) {
      return call('analytics.trackEvent', { name: name, parameters: parameters || {} });
    },
    metric: function (name, value, unit) {
      return call('analytics.recordMetric', { name: name, value: value, unit: unit || 'count' });
    },
    startSession: function () { return call('analytics.startSession'); },
    endSession: function () { return call('analytics.endSession'); },
    startTrace: function (name) { return call('analytics.startTrace', { name: name }); },
    stopTrace: function (name) { return call('analytics.stopTrace', { name: name }); },
    screenView: function (screenName) { return call('analytics.startScreenView', { screenName: screenName }); },
    endScreenView: function (screenName) { return call('analytics.endScreenView', { screenName: screenName }); },
    stats: function () { return call('analytics.getStats'); },
    flush: function () { return call('analytics.flush'); },
    clear: function () { return call('analytics.clear'); }
  };

  var network = {
    request: function (url, options) {
      options = options || {};
      return call('network.request', {
        url: url,
        method: options.method || 'GET',
        headers: options.headers || {},
        body: options.body,
        timeoutMs: options.timeoutMs
      });
    },
    get: function (url, options) { return network.request(url, Object.assign({}, options, { method: 'GET' })); },
    post: function (url, body, options) { return network.request(url, Object.assign({}, options, { method: 'POST', body: body })); }
  };

  var notification = {
    permission: function () { return call('notification.permission'); },
    status: function () { return call('notification.status'); },
    post: function (options) { return call('notification.post', options); },
    update: function (options) { return call('notification.update', options); },
    schedule: function (options) { return call('notification.schedule', options); },
    cancel: function (key) { return call('notification.cancel', { key: key }); },
    cancelAll: function () { return call('notification.cancelAll'); },
    list: function () { return call('notification.list'); },
    channels: function () { return call('notification.channels'); }
  };

  var automation = {
    status: function () { return call('automation.getStatus'); },
    capabilities: function () { return call('automation.getCapabilities'); },
    submitTask: function (options) { return call('automation.submitTask', options); },
    getTask: function (taskId) { return call('automation.getTask', { taskId: taskId }); },
    cancelTask: function (taskId) { return call('automation.cancelTask', { taskId: taskId }); },
    listTasks: function () { return call('automation.listTasks'); },
    scheduleJob: function (options) { return call('automation.scheduleJob', options); },
    cancelJob: function (jobId) { return call('automation.cancelJob', { jobId: jobId }); },
    listJobs: function () { return call('automation.listJobs'); },
    getEvents: function (sinceSequence, limit) {
      return call('automation.getEvents', { sinceSequence: sinceSequence || 0, limit: limit || 100 });
    }
  };

  var logger = {
    debug: function (message, tag) { return call('logger.log', { level: 'DEBUG', tag: tag || 'JS', message: message }); },
    info: function (message, tag) { return call('logger.log', { level: 'INFO', tag: tag || 'JS', message: message }); },
    warn: function (message, tag) { return call('logger.log', { level: 'WARN', tag: tag || 'JS', message: message }); },
    error: function (message, tag) { return call('logger.log', { level: 'ERROR', tag: tag || 'JS', message: message }); },
    setLevel: function (level) { return call('logger.setLevel', { level: String(level).toUpperCase() }); },
    getLevel: function () { return call('logger.getLevel'); }
  };

  var web = {
    loadUrl: function (url) { return call('web.loadUrl', { url: url }); },
    reload: function () { return call('web.reload'); },
    state: function () { return call('web.getState'); },
    evaluate: function (script) { return call('web.evaluate', { script: script }); },
    setZoom: function (zoom) { return call('web.setZoom', { zoom: zoom }); },
    emitEvent: function (topic, data) { return call('web.emitEvent', { topic: topic, data: data || {} }); }
  };

  /* ---------------------------------------------------------------- */
  /* export                                                            */
  /* ---------------------------------------------------------------- */

  global.Native = {
    available: available,
    ready: ready,
    call: call,
    on: on,
    once: once,
    isSupported: isSupported,
    ui: ui,
    device: device,
    storage: storage,
    theme: theme,
    security: security,
    analytics: analytics,
    network: network,
    notification: notification,
    automation: automation,
    logger: logger,
    web: web
  };
})(window);
