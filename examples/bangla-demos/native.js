/* RONiLiFEOS bridge wrapper — সব ডেমোতে ব্যবহৃত */
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

  function log(el, label, value) {
    var node = typeof el === 'string' ? document.getElementById(el) : el;
    if (!node) return;
    var line = document.createElement('div');
    line.className = 'log-line';
    line.textContent = '[' + new Date().toLocaleTimeString('bn-BD') + '] ' + label + ': '
      + (typeof value === 'string' ? value : JSON.stringify(value));
    node.insertBefore(line, node.firstChild);
    while (node.childNodes.length > 40) node.removeChild(node.lastChild);
  }

  function guard(fn) {
    return function () {
      if (!available()) {
        alert('Native bridge নেই — ব্রাউজার মোডে চলছে');
        return;
      }
      try { fn.apply(null, arguments); } catch (e) { console.error(e); }
    };
  }

  global.Native = {
    available: available,
    call: call,
    on: on,
    off: off,
    toast: toast,
    log: log,
    guard: guard
  };

  document.addEventListener('DOMContentLoaded', function () {
    var badge = document.getElementById('bridge-state');
    if (badge) {
      badge.textContent = available() ? 'Bridge: সংযুক্ত' : 'Bridge: নেই (ব্রাউজার)';
      badge.className = 'badge ' + (available() ? 'ok' : 'warn');
    }
  });
})(window);
