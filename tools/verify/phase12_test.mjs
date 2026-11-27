/**
 * Phase 12 verification: JS wrapper contract + runtime dispatch.
 * Static part: every call('x.y') literal must exist in a Java handler.
 * Runtime part: jsdom + mock bridge, real ES module import.
 */
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ROOT = '/workspace/app-fu3r65ukcnwh/tasks/p12/RONiLiFEOS/examples/webapp-starter';
const WRAPPER = path.join(ROOT, 'scripts/native-runtime.js');
const actions = JSON.parse(fs.readFileSync('actions.json', 'utf8'));

let pass = 0, fail = 0;
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log('  PASS  ' + name); }
  else { fail++; console.log('  FAIL  ' + name + (detail ? '  → ' + detail : '')); }
};

/* ---------- 1. build the authoritative action set from Java ---------- */
const REPO = '/workspace/app-fu3r65ukcnwh/tasks/p12/RONiLiFEOS';
const HANDLERS = {
  theme: 'native-theme-engine/src/main/java/com/roni/library/theme/handler/ThemeCommandHandler.java',
  security: 'native-security-engine/src/main/java/com/roni/library/security/handler/SecurityCommandHandler.java',
  analytics: 'native-analytics-engine/src/main/java/com/roni/library/analytics/handler/AnalyticsCommandHandler.java',
  devtools: 'native-dev-tools-sdk/src/main/java/com/roni/library/devtools/handler/DevToolsCommandHandler.java',
  network: 'native-network-engine/src/main/java/com/roni/library/network/handler/NetworkCommandHandler.java',
  platform: 'native-platform-engine/src/main/java/com/roni/library/platform/handler/PlatformCommandHandler.java',
  logger: 'native-logger-sdk/src/main/java/com/roni/library/logger/handler/LoggerCommandHandler.java',
  service: 'native-service-sdk/src/main/java/com/roni/library/service/handler/ServiceCommandHandler.java',
  storage: 'native-storage-engine/src/main/java/com/roni/library/storage/handler/StorageCommandHandler.java'
};

const known = new Set();
const perHandler = {};
for (const [ns, rel] of Object.entries(HANDLERS)) {
  const src = fs.readFileSync(path.join(REPO, rel), 'utf8');
  const found = new Set();
  for (const m of src.matchAll(/case\s+"([^"]+)"\s*:/g)) found.add(m[1]);
  for (const m of src.matchAll(/"([a-zA-Z0-9_.]+)"\.equalsIgnoreCase\(action\)/g)) found.add(m[1]);
  for (const m of src.matchAll(/action\.equalsIgnoreCase\("([^"]+)"\)/g)) found.add(m[1]);
  if (ns === 'network') found.add('request');
  perHandler[ns] = found.size;
  for (const c of found) known.add(ns + '.' + c);
}
// existing 152 actions stay valid
for (const [prefix, h] of Object.entries(actions.handlers)) {
  for (const c of h.cases) known.add(prefix + '.' + c);
}

console.log('== 1. Java action inventory ==');
console.log('  namespaces: ' + Object.keys(perHandler).length + ', actions: ' + known.size);
for (const ns of Object.keys(HANDLERS)) {
  check('handler present: ' + ns, perHandler[ns] > 0, String(perHandler[ns]));
}

/* ---------- 2. wrapper literals ---------- */
console.log('\n== 2. wrapper -> Java contract ==');
const source = fs.readFileSync(WRAPPER, 'utf8');
const literals = [...source.matchAll(/call\('([a-zA-Z0-9_.]+)'/g)].map(m => m[1]);
const unique = [...new Set(literals)];
check('wrapper has call sites', unique.length > 40, String(unique.length));
const unknown = unique.filter(a => !known.has(a));
check('every wrapper action exists in Java', unknown.length === 0, unknown.join(', '));

const namespaces = ['theme', 'security', 'analytics', 'devtools', 'network', 'platform', 'logger', 'service', 'storage'];
for (const ns of namespaces) {
  check('exported namespace: ' + ns, new RegExp('export const ' + ns + ' = \\{').test(source));
}

/* ---------- 3. runtime dispatch ---------- */
console.log('\n== 3. runtime dispatch (jsdom) ==');
const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', { url: 'https://appassets.androidplatform.net/' });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.localStorage = dom.window.localStorage;

const calls = [];
const listeners = new Map();
dom.window.NativeRuntimeBridgeClient = {
  call(action, payload) {
    calls.push({ action, payload });
    return new Promise((resolve, reject) => {
      if (!known.has(action)) {
        setTimeout(() => reject(new Error('ROUTE_NOT_FOUND: no handler for ' + action)), 1);
        return;
      }
      setTimeout(() => {
        if (action === 'theme.getTheme') resolve({ themeName: 'DefaultLight', darkMode: false });
        else if (action === 'theme.getCssVariables') resolve({ css: ':root{--color-primary:#fff}' });
        else if (action === 'platform.device.info') resolve({ deviceInfo: { model: 'Pixel' } });
        else if (action === 'analytics.getStats') resolve({ total: 0 });
        else if (action === 'devtools.getHealth') reject(new Error('ROUTE_NOT_FOUND: no handler'));
        else if (action === 'network.request') resolve({ statusCode: 200, body: '{}' });
        else resolve({ ok: true });
      }, 1);
    });
  },
  on(t, fn) { (listeners.get(t) || listeners.set(t, []).get(t)).push(fn); },
  off(t, fn) { const a = listeners.get(t) || []; const i = a.indexOf(fn); if (i >= 0) a.splice(i, 1); }
};

const mod = await import(pathToFileURL(WRAPPER).href + '?v=phase12');
const { theme, analytics, platform, logger, storage, network, security, isSupported, Native } = mod;

check('Native namespace exported', typeof Native === 'object' && typeof Native.theme === 'object');

await theme.get();
check('theme.get -> theme.getTheme', calls.some(c => c.action === 'theme.getTheme'));

await theme.setMode('dark');
const modeCall = calls.find(c => c.action === 'theme.setThemeMode');
check('theme.setMode uppercases', modeCall && modeCall.payload.mode === 'DARK', JSON.stringify(modeCall && modeCall.payload));

await analytics.track('x', { a: 1 });
const trackCall = calls.find(c => c.action === 'analytics.trackEvent');
check('analytics.track payload', trackCall && trackCall.payload.name === 'x' && trackCall.payload.properties.a === 1);

await platform.device();
check('platform.device -> platform.device.info', calls.some(c => c.action === 'platform.device.info'));

await logger.warn('w', 'T');
const logCall = calls.find(c => c.action === 'logger.log');
check('logger.warn level', logCall && logCall.payload.level === 'WARN' && logCall.payload.tag === 'T');

await storage.set('k', 1);
check('storage.set payload', calls.some(c => c.action === 'storage.set' && c.payload.key === 'k'));

await network.patch('https://example.com', '{}');
const patchCall = calls.find(c => c.action === 'network.request' && c.payload.method === 'PATCH');
check('network.patch method', !!patchCall);

await security.authenticate('T');
check('security.authenticate', calls.some(c => c.action === 'security.biometric.authenticate'));

check('isSupported(theme) true', await isSupported('theme') === true);
check('isSupported(devtools) false on ROUTE_NOT_FOUND', await isSupported('devtools') === false);

/* error normalisation */
let caught = null;
try { await mod.call('nope.missing', {}); } catch (e) { caught = e; }
check('unknown action rejects', caught !== null);
check('error carries code', caught && typeof caught.code === 'string', caught && caught.code);

/* event lifecycle */
let fired = 0;
const off = theme.onChange(() => { fired++; });
(listeners.get('theme.changed') || []).forEach(fn => fn({ themeName: 'X', darkMode: true }));
off();
(listeners.get('theme.changed') || []).forEach(fn => fn({ themeName: 'Y', darkMode: true }));
check('theme.onChange unsubscribe works', fired === 1, String(fired));

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
