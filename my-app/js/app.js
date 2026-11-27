import { greet, FIXTURE_VERSION } from './greet.js';

const statusEl = document.getElementById('status');
const dataEl = document.getElementById('data');

// 1. ES6 module import
statusEl.textContent = greet('world');

// 2. fetch() against the virtual origin
async function loadData() {
  try {
    const response = await fetch('./data.json');
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const data = await response.json();
    dataEl.textContent = `${data.message} (v${FIXTURE_VERSION})`;
  } catch (error) {
    dataEl.textContent = `fetch failed: ${error.message}`;
  }
}

loadData();

// 3. Native bridge — present only when served inside RONiLiFEOS.
if (window.NativeRuntime && window.NativeRuntime.ui && window.NativeRuntime.ui.toast) {
  window.NativeRuntime.ui.toast('Loaded');
} else {
  console.info('[my-app] NativeRuntime bridge not present (browser preview)');
}
