/* Storage helper — project prefix + JSON */
(function (global) {
  'use strict';

  var PREFIX = 'notes:';

  function key(k) { return PREFIX + k; }

  var Store = {
    get: function (k, fallback) {
      return Native.call('storage.get', { key: key(k) }).then(function (r) {
        return r.exists ? r.value : fallback;
      }).catch(function () {
        var ls = localStorage.getItem(key(k));
        return ls === null ? fallback : ls;
      });
    },
    set: function (k, v) {
      return Native.call('storage.set', { key: key(k), value: v })
        .then(function () { return true; })
        .catch(function () {
          try { localStorage.setItem(key(k), v); return true; } catch (e) { return false; }
        });
    },
    remove: function (k) {
      return Native.call('storage.remove', { key: key(k) })
        .catch(function () { localStorage.removeItem(key(k)); });
    },
    getJson: function (k, fallback) {
      return Store.get(k, null).then(function (raw) {
        if (raw === null) return fallback;
        try { return JSON.parse(raw); } catch (e) { return fallback; }
      });
    },
    setJson: function (k, obj) {
      return Store.set(k, JSON.stringify(obj));
    }
  };

  global.Store = Store;
})(window);
