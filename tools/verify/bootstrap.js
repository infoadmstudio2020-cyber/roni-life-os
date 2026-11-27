(function() {
  if (window['NativeRuntimeBridgeClient']) return;
  var callbacks = {};
  var eventListeners = {};
  var reqCounter = 0;

  var client = {
    call: function(action, payload) {
      return new Promise(function(resolve, reject) {
        var reqId = 'req_' + Date.now() + '_' + (++reqCounter);
        callbacks[reqId] = { resolve: resolve, reject: reject };
        var msg = JSON.stringify({
          id: reqId,
          type: 'REQUEST',
          action: action,
          payload: payload || {},
          timestamp: Date.now()
        });
        if (window['NativeRuntimeBridge'] && window['NativeRuntimeBridge'].postMessage) {
          window['NativeRuntimeBridge'].postMessage(msg);
        } else {
          delete callbacks[reqId];
          reject(new Error('Native interface NativeRuntimeBridge is not bound'));
        }
      });
    },
    ping: function() {
      if (window['NativeRuntimeBridge'] && window['NativeRuntimeBridge'].ping) {
        return window['NativeRuntimeBridge'].ping();
      }
      return 'pong_fallback';
    },
    on: function(topic, listener) {
      if (!eventListeners[topic]) eventListeners[topic] = [];
      eventListeners[topic].push(listener);
    },
    off: function(topic, listener) {
      if (!eventListeners[topic]) return;
      eventListeners[topic] = eventListeners[topic].filter(function(l) { return l !== listener; });
    },
    _handleNativeMessage: function(rawJson) {
      try {
        var msg = typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson;
        if (msg.type === 'RESPONSE' || msg.type === 'ERROR') {
          var cb = callbacks[msg.id];
          if (cb) {
            delete callbacks[msg.id];
            if (msg.success) { cb.resolve(msg.payload); }
            else { cb.reject(new Error(msg.error || 'Unknown native error')); }
          }
        } else if (msg.type === 'EVENT') {
          var listeners = eventListeners[msg.action] || [];
          listeners.forEach(function(l) { try { l(msg.payload); } catch(e){} });
        }
      } catch (err) {
        console.error('Failed to parse message from native:', err);
      }
    }
  };
  window['NativeRuntimeBridgeClient'] = client;
})();