(function () {
  "use strict";
  // The host injects the configured colours and logo text after load. This script only keeps
  // the page honest when it is opened directly (for example during a preview).
  var root = document.documentElement;
  if (!root.style.getPropertyValue("--accent")) {
    root.style.setProperty("--accent", "#6c5ce7");
  }
})();
