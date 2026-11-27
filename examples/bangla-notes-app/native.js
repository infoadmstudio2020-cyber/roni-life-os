/* Bridge wrapper — guard + timeout + fallback */
(function (global) {
  'use strict';

  var TIMEOUT = 12000;

  function available() {
    return typeof NativeRuntimeBridgeClient !== 'undefined'
        && typeof NativeRuntimeBridgeClient.call === 'function';
  }

  function call(action, payload) {
    if (!available()) return Promise.reject(new Error('NATIVE_UNAVAILABLE'));
    return new Promise(function (resolve, reject) {
      var done = false;
      var timer = setTimeout(function () {
        if (!done) { done = true; reject(new Error('CLIENT_TIMEOUT')); }
      }, TIMEOUT);

      NativeRuntimeBridgeClient.call(action, payload || {}).then(function (r) {
        if (!done) { done = true; clearTimeout(timer); resolve(r); }
      }).catch(function (e) {
        if (!done) { done = true; clearTimeout(timer); reject(e); }
      });
    });
  }

  function on(topic, fn) {
    if (available()) NativeRuntimeBridgeClient.on(topic, fn);
  }

  function off(topic, fn) {
    if (available()) NativeRuntimeBridgeClient.off(topic, fn);
  }

  function toast(message, type) {
    return call('ui.showToast', { message: message, type: type || 'info' })
      .catch(function () {});
  }

  function confirm(title, message) {
    return new Promise(function (resolve) {
      if (!available()) { resolve(window.confirm(message)); return; }
      var settled = false;
      function handler(p) {
        if (settled) return;
        settled = true;
        off('ui.confirm', handler);
        resolve(!!p.confirmed);
      }
      on('ui.confirm', handler);
      call('ui.showConfirm', { title: title, message: message })
        .catch(function () {
          if (settled) return;
          settled = true;
          off('ui.confirm', handler);
          resolve(false);
        });
    });
  }

  function loading(text) {
    var shown = false;
    return {
      show: function () {
        return call('ui.showLoading', { text: text || 'অপেক্ষা করুন…', cancelable: false })
          .then(function () { shown = true; })
          .catch(function () {});
      },
      hide: function () {
        if (!shown) return Promise.resolve();
        shown = false;
        return call('ui.hideLoading').catch(function () {});
      }
    };
  }

  global.Native = {
    available: available,
    call: call,
    on: on,
    off: off,
    toast: toast,
    confirm: confirm,
    loading: loading
  };
})(window);
