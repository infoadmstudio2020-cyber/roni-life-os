/* Hash router — back button কাজ করে */
(function (global) {
  'use strict';

  var routes = {};
  var current = '/';

  function register(path, handler) { routes[path] = handler; }

  function path() {
    return location.hash.replace(/^#/, '') || '/';
  }

  function render() {
    var p = path();
    var handler = routes[p] || routes['*'];
    var root = document.getElementById('app');
    if (!handler || !root) return;
    current = p;
    root.innerHTML = '';
    handler(root);
    document.querySelectorAll('[data-route]').forEach(function (b) {
      b.classList.toggle('active', b.getAttribute('data-route') === p);
    });
  }

  function navigate(p) {
    if (path() === p) { render(); return; }
    location.hash = '#' + p;
  }

  function start() {
    window.addEventListener('hashchange', render);
    document.addEventListener('click', function (e) {
      var el = e.target.closest('[data-route]');
      if (!el) return;
      e.preventDefault();
      navigate(el.getAttribute('data-route'));
    });
    render();
  }

  global.Router = {
    register: register,
    navigate: navigate,
    start: start,
    current: function () { return current; }
  };
})(window);
