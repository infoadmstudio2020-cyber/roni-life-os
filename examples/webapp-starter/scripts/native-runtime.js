/**
 * NativeRuntime bridge wrapper — Phase 12 unified API.
 *
 * Every method below maps to a real action registered by a native command handler.
 * Nothing here is speculative: if a capability is missing, the call rejects with a
 * structured error instead of pretending to succeed.
 */

const TIMEOUT = 15000;

/* ------------------------------------------------------------------ */
/* transport                                                           */
/* ------------------------------------------------------------------ */

export function available() {
  return typeof window.NativeRuntimeBridgeClient !== 'undefined'
    && typeof window.NativeRuntimeBridgeClient.call === 'function';
}

function normalizeError(action, error) {
  const raw = error && error.message ? String(error.message) : String(error || 'UNKNOWN');
  const match = raw.match(/^([A-Z_]{3,})/);
  const code = match ? match[1] : 'NATIVE_ERROR';
  const normalized = new Error(raw);
  normalized.code = code;
  normalized.action = action;
  return normalized;
}

export function call(action, payload = {}) {
  if (!available()) {
    const error = new Error('NATIVE_UNAVAILABLE');
    error.code = 'NATIVE_UNAVAILABLE';
    error.action = action;
    return Promise.reject(error);
  }

  return new Promise((resolve, reject) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      const error = new Error('CLIENT_TIMEOUT');
      error.code = 'CLIENT_TIMEOUT';
      error.action = action;
      reject(error);
    }, TIMEOUT);

    window.NativeRuntimeBridgeClient.call(action, payload).then(
      (result) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        resolve(result);
      },
      (error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        reject(normalizeError(action, error));
      }
    );
  });
}

export function on(topic, handler) {
  if (!available() || typeof window.NativeRuntimeBridgeClient.on !== 'function') return;
  window.NativeRuntimeBridgeClient.on(topic, handler);
}

export function off(topic, handler) {
  if (!available() || typeof window.NativeRuntimeBridgeClient.off !== 'function') return;
  window.NativeRuntimeBridgeClient.off(topic, handler);
}

/* ------------------------------------------------------------------ */
/* availability probes                                                 */
/* ------------------------------------------------------------------ */

const PROBES = {
  theme: { action: 'theme.getTheme', payload: {} },
  security: { action: 'security.diagnostic', payload: {} },
  analytics: { action: 'analytics.getStats', payload: {} },
  devtools: { action: 'devtools.getHealth', payload: {} },
  network: { action: 'network.request', payload: { url: '' } },
  platform: { action: 'platform.health', payload: {} },
  logger: { action: 'logger.getLevel', payload: {} },
  service: { action: 'service.list', payload: {} },
  storage: { action: 'storage.keys', payload: {} }
};

const probeCache = new Map();

/**
 * Resolves true when the namespace's route is registered on the native router.
 * A ROUTE_NOT_FOUND rejection means the handler was not installed (for example
 * devtools in a release build).
 */
export async function isSupported(namespace) {
  if (probeCache.has(namespace)) return probeCache.get(namespace);
  const probe = PROBES[namespace];
  if (!probe) return false;
  if (!available()) {
    probeCache.set(namespace, false);
    return false;
  }
  let supported = true;
  try {
    await call(probe.action, probe.payload);
  } catch (error) {
    supported = error.code !== 'ROUTE_NOT_FOUND' && error.code !== 'NATIVE_UNAVAILABLE';
  }
  probeCache.set(namespace, supported);
  return supported;
}

/* ------------------------------------------------------------------ */
/* theme                                                               */
/* ------------------------------------------------------------------ */

export const theme = {
  get: () => call('theme.getTheme'),
  setMode: (mode) => call('theme.setThemeMode', { mode: String(mode).toUpperCase() }),
  apply: (config) => call('theme.applyTheme', config || {}),
  isSystemDark: () => call('theme.isSystemDark'),
  setSystemDark: (dark) => call('theme.setSystemDark', { dark: !!dark }),
  cssVariables: () => call('theme.getCssVariables'),
  onChange(handler) {
    on('theme.changed', handler);
    return () => off('theme.changed', handler);
  }
};

/* ------------------------------------------------------------------ */
/* security                                                            */
/* ------------------------------------------------------------------ */

export const security = {
  capability: () => call('security.biometric.capability'),
  authenticate: (title) => call('security.biometric.authenticate', { title: title || 'Authentication' }),
  onResult(handler) {
    on('security.biometric.result', handler);
    return () => off('security.biometric.result', handler);
  },
  checkPermission: (permission) => call('security.permission.check', { permission }),
  sha256: (text) => call('security.hash.sha256', { text }),
  keystore: {
    generate: (alias, algorithm) => call('security.keystore.generate', { alias, algorithm }),
    hasKey: (alias) => call('security.keystore.hasKey', { alias }),
    delete: (alias) => call('security.keystore.delete', { alias }),
    list: () => call('security.keystore.list'),
    encrypt: (alias, plaintext) => call('security.keystore.encrypt', { alias, plaintext }),
    decrypt: (alias, ciphertext) => call('security.keystore.decrypt', { alias, ciphertext })
  },
  diagnostic: () => call('security.diagnostic')
};

/* ------------------------------------------------------------------ */
/* analytics                                                           */
/* ------------------------------------------------------------------ */

export const analytics = {
  track: (name, properties) => call('analytics.trackEvent', { name, properties: properties || {} }),
  metric: (name, value) => call('analytics.recordMetric', { name, value }),
  startSession: () => call('analytics.startSession'),
  endSession: () => call('analytics.endSession'),
  startTrace: (name) => call('analytics.startTrace', { name }),
  stopTrace: (name) => call('analytics.stopTrace', { name }),
  screenView: (screen) => call('analytics.startScreenView', { screen }),
  endScreenView: (screen) => call('analytics.endScreenView', { screen }),
  stats: () => call('analytics.getStats'),
  flush: () => call('analytics.flush'),
  clear: () => call('analytics.clear')
};

/* ------------------------------------------------------------------ */
/* devtools (debug builds only)                                        */
/* ------------------------------------------------------------------ */

export const devtools = {
  metrics: () => call('devtools.getMetrics'),
  health: () => call('devtools.getHealth'),
  bridgeMetrics: () => call('devtools.getBridgeMetrics'),
  manifest: () => call('devtools.getManifest'),
  exportHar: () => call('devtools.exportHar'),
  clearTraffic: () => call('devtools.clearTraffic'),
  startProfiling: () => call('devtools.startProfiling'),
  stopProfiling: () => call('devtools.stopProfiling'),
  dispatchCdp: (domain, method, params) =>
    call('devtools.dispatchCdp', { domain, method, params: params || {} })
};

/* ------------------------------------------------------------------ */
/* network                                                             */
/* ------------------------------------------------------------------ */

export const network = {
  request: (url, options = {}) => call('network.request', {
    url,
    method: options.method || 'GET',
    headers: options.headers || {},
    body: options.body,
    timeoutMs: options.timeoutMs
  }),
  get: (url, options = {}) => network.request(url, { ...options, method: 'GET' }),
  post: (url, body, options = {}) => network.request(url, { ...options, method: 'POST', body }),
  put: (url, body, options = {}) => network.request(url, { ...options, method: 'PUT', body }),
  patch: (url, body, options = {}) => network.request(url, { ...options, method: 'PATCH', body }),
  delete: (url, options = {}) => network.request(url, { ...options, method: 'DELETE' })
};

/* ------------------------------------------------------------------ */
/* platform                                                            */
/* ------------------------------------------------------------------ */

export const platform = {
  device: () => call('platform.device.info'),
  battery: () => call('platform.battery.status'),
  networkStatus: () => call('platform.network.status'),
  storageStats: () => call('platform.storage.stats'),
  clipboard: {
    get: () => call('platform.clipboard.get'),
    set: (text) => call('platform.clipboard.set', { text })
  },
  toast: (text) => call('platform.toast.show', { text }),
  vibrate: (duration) => call('platform.toast.vibrate', { duration }),
  diagnostic: () => call('platform.diagnostic'),
  health: () => call('platform.health')
};

/* ------------------------------------------------------------------ */
/* logger                                                              */
/* ------------------------------------------------------------------ */

export const logger = {
  debug: (message, tag) => call('logger.log', { level: 'DEBUG', tag: tag || 'JS', message }),
  info: (message, tag) => call('logger.log', { level: 'INFO', tag: tag || 'JS', message }),
  warn: (message, tag) => call('logger.log', { level: 'WARN', tag: tag || 'JS', message }),
  error: (message, tag) => call('logger.log', { level: 'ERROR', tag: tag || 'JS', message }),
  setLevel: (level) => call('logger.setLevel', { level: String(level).toUpperCase() }),
  getLevel: () => call('logger.getLevel'),
  flush: () => call('logger.flush')
};

/* ------------------------------------------------------------------ */
/* service                                                             */
/* ------------------------------------------------------------------ */

export const service = {
  list: () => call('service.list'),
  status: (id) => call('service.status', { serviceId: id })
};

/* ------------------------------------------------------------------ */
/* storage (project-scoped)                                            */
/* ------------------------------------------------------------------ */

export const storage = {
  get: (key) => call('storage.get', { key }),
  set: (key, value) => call('storage.set', { key, value }),
  remove: (key) => call('storage.remove', { key }),
  clear: () => call('storage.clear'),
  keys: () => call('storage.keys'),
  has: (key) => call('storage.has', { key }),
  getAll: () => call('storage.getAll')
};

/* ------------------------------------------------------------------ */
/* convenience helpers (existing behaviour, unchanged)                 */
/* ------------------------------------------------------------------ */

export async function toast(text) {
  try {
    await call('ui.showToast', { text });
  } catch (error) {
    console.info('[toast fallback]', text);
  }
}

export function confirm(title, message) {
  if (!available()) return Promise.resolve(window.confirm(message));

  return new Promise((resolve) => {
    let settled = false;
    const handler = (event) => {
      if (settled) return;
      settled = true;
      off('ui.confirm', handler);
      resolve(!!(event && event.confirmed));
    };

    on('ui.confirm', handler);
    call('ui.showConfirm', { title, message }).catch(() => {
      if (settled) return;
      settled = true;
      off('ui.confirm', handler);
      resolve(false);
    });
  });
}

export function loading(text) {
  let shown = false;
  return {
    async show() {
      if (shown) return;
      shown = true;
      try { await call('ui.showLoading', { text }); } catch (error) { shown = false; }
    },
    async hide() {
      if (!shown) return;
      shown = false;
      try { await call('ui.hideLoading'); } catch (error) { /* ignore */ }
    }
  };
}

/* ------------------------------------------------------------------ */
/* unified namespace                                                   */
/* ------------------------------------------------------------------ */

export const Native = {
  available,
  call,
  on,
  off,
  isSupported,
  theme,
  security,
  analytics,
  devtools,
  network,
  platform,
  logger,
  service,
  storage,
  toast,
  confirm,
  loading
};

export default Native;
