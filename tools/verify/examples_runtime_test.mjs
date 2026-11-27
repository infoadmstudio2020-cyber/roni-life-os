/**
 * Runtime integration test for the 26 example files.
 *
 * Loads each HTML file in jsdom, injects a mock NativeRuntimeBridgeClient that enforces the
 * REAL routing rules extracted from the Java handlers (prefix route + SUPPORTED_ACTIONS),
 * clicks every button, and reports:
 *   - ROUTE_NOT_FOUND  : prefix is not registered on the bridge
 *   - UNKNOWN_ACTION   : prefix registered, action missing from SUPPORTED_ACTIONS
 *   - runtime errors   : uncaught exceptions / unhandled rejections
 */
import { JSDOM, VirtualConsole } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { RESPONSES } from './responses.mjs';

const EXAMPLES = '/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS/examples';
const actions = JSON.parse(fs.readFileSync('actions.json', 'utf8'));

// Real dispatch model: registerHandler() installs a PREFIX route ("ui"), so any "ui.*"
// reaches the handler, which then switches on the bare action name. SUPPORTED_ACTIONS is
// only used for the extra bare-name routes (native callers), not for JS dispatch.
const ROUTES = {};
const EXPLICIT = {};
for (const name of ['ui', 'web', 'device', 'storage']) {
  ROUTES[name] = new Set(actions.handlers[name].cases);
  EXPLICIT[name] = new Set(actions.handlers[name].supported);
}

const files = [
  ...fs.readdirSync(path.join(EXAMPLES, 'bangla-demos'))
    .filter(f => f.endsWith('.html')).sort()
    .map(f => path.join(EXAMPLES, 'bangla-demos', f)),
  path.join(EXAMPLES, 'bangla-notes-app', 'index.html')
];

const sleep = ms => new Promise(r => setTimeout(r, ms));

function inlineScripts(html, dir) {
  return html.replace(/<script\s+src="([^"]+)"\s*><\/script>/g, (m, src) => {
    const p = path.join(dir, src);
    if (!fs.existsSync(p)) return `<!-- missing ${src} -->`;
    return `<script>\n${fs.readFileSync(p, 'utf8')}\n</script>`;
  });
}

async function runFile(file) {
  const dir = path.dirname(file);
  const html = inlineScripts(fs.readFileSync(file, 'utf8'), dir);

  const calls = [];
  const errors = [];
  const vc = new VirtualConsole();
  vc.on('jsdomError', e => errors.push('jsdomError: ' + e.message));

  const dom = new JSDOM(html, {
    runScripts: 'dangerously',
    url: 'https://appassets.androidplatform.net/' + path.basename(file),
    virtualConsole: vc,
    beforeParse(window) {
      const listeners = new Map();
      const emit = (topic, payload) => {
        (listeners.get(topic) || []).forEach(fn => {
          try { fn(payload); } catch (e) { errors.push('listener ' + topic + ': ' + e.message); }
        });
      };

      window.NativeRuntimeBridgeClient = {
        call(action, payload) {
          return new window.Promise((resolve, reject) => {
            const prefix = String(action).split('.')[0];
            const record = { action, payload, outcome: 'ok' };
            calls.push(record);

            if (!ROUTES[prefix]) {
              record.outcome = 'ROUTE_NOT_FOUND';
              reject(new Error('ROUTE_NOT_FOUND: ' + action));
              return;
            }
            const bare = String(action).slice(prefix.length + 1);
            if (!ROUTES[prefix].has(bare)) {
              record.outcome = 'UNKNOWN_ACTION';
              reject(new Error('UNKNOWN_ACTION: ' + action));
              return;
            }
            record.explicitRoute = EXPLICIT[prefix].has(bare);
            const body = RESPONSES[action] !== undefined ? RESPONSES[action] : {};
            setTimeout(() => {
              resolve(JSON.parse(JSON.stringify(body)));
              if (action === 'ui.showConfirm') {
                setTimeout(() => emit('ui.confirm', { confirmed: true }), 2);
              }
            }, 1);
          });
        },
        ping() { return 'pong'; },
        on(topic, fn) {
          if (!listeners.has(topic)) listeners.set(topic, []);
          listeners.get(topic).push(fn);
        },
        off(topic, fn) {
          const arr = listeners.get(topic) || [];
          const i = arr.indexOf(fn);
          if (i >= 0) arr.splice(i, 1);
        }
      };

      window.URL.createObjectURL = () => 'blob:mock';
      window.URL.revokeObjectURL = () => {};
      window.HTMLAnchorElement.prototype.click = function () {
        calls.push({ action: 'dom.anchorClick', payload: { download: this.download }, outcome: 'ok' });
      };
      window.alert = m => calls.push({ action: 'dom.alert', payload: m, outcome: 'ok' });
      window.confirm = () => true;
      window.onerror = (msg) => { errors.push('onerror: ' + msg); };
    }
  });

  await new Promise(resolve => {
    if (dom.window.document.readyState === 'complete') resolve();
    else dom.window.addEventListener('load', resolve);
  });
  await sleep(60);

  // exercise file inputs (demo 14) with a synthetic File
  const fileInputs = Array.from(dom.window.document.querySelectorAll('input[type=file]'));
  for (const input of fileInputs) {
    try {
      const file = new dom.window.File(['hello,world\n1,2'], 'sample.csv', { type: 'text/csv' });
      Object.defineProperty(input, 'files', { value: [file], configurable: true });
      input.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
      await sleep(30);
    } catch (e) { errors.push('file input: ' + e.message); }
  }

  const buttons = Array.from(dom.window.document.querySelectorAll('button'));
  for (const b of buttons) {
    try { b.click(); } catch (e) { errors.push('click "' + (b.textContent || '').trim() + '": ' + e.message); }
    await sleep(6);
  }
  await sleep(120);

  // exercise hash routing for the notes app
  if (file.includes('bangla-notes-app')) {
    for (const route of ['#/notes', '#/settings', '#/']) {
      dom.window.location.hash = route;
      dom.window.dispatchEvent(new dom.window.Event('hashchange'));
      await sleep(40);
    }
  }

  // DOM-level assertions: proves the handler actually ran, not just that no error was thrown
  const doc = dom.window.document;
  const checks = [];
  const expect = (name, cond) => checks.push({ name, ok: !!cond });
  const base = path.basename(file);

  if (base === '14-upload.html') {
    expect('file name rendered', (doc.getElementById('one-info')?.textContent || '').includes('sample.csv'));
    expect('text file read', (doc.getElementById('txt-out')?.value || '').length > 0);
  }
  if (base === '12-download.html') {
    expect('3 downloads triggered', calls.filter(c => c.action === 'dom.anchorClick').length >= 3);
  }
  if (base === '11-storage.html') {
    expect('counter rendered', /\d/.test(doc.getElementById('counter')?.textContent || ''));
  }
  if (base === '01-toast.html') {
    expect('log has entries', (doc.getElementById('log')?.children.length || 0) > 0);
  }
  if (base === '02-dialog.html') {
    expect('confirm event handled', (doc.getElementById('log')?.textContent || '').includes('ui.confirm'));
  }
  if (base === '08-device-info.html') {
    expect('device fields rendered', (doc.getElementById('kv')?.children.length || 0) > 4);
  }
  if (base === '09-battery-network.html') {
    expect('battery percent rendered', /%/.test(doc.getElementById('batt-pct')?.textContent || ''));
  }
  if (base === '13-navigation.html') {
    expect('route view rendered', (doc.getElementById('view')?.textContent || '').length > 0);
  }
  if (file.includes('bangla-notes-app')) {
    expect('app rendered', (doc.getElementById('app')?.children.length || 0) > 0);
    expect('tabbar active', !!doc.querySelector('.tabbar button.active'));
  }

  const unknown = calls.filter(c => c.outcome === 'UNKNOWN_ACTION');
  const notFound = calls.filter(c => c.outcome === 'ROUTE_NOT_FOUND');
  const unique = [...new Set(calls.map(c => c.action))];

  dom.window.close();

  return {
    file: path.relative(EXAMPLES, file),
    buttons: buttons.length,
    fileInputs: fileInputs.length,
    calls: calls.length,
    unique,
    unknown,
    notFound,
    errors,
    checks
  };
}

const results = [];
for (const f of files) {
  try {
    results.push(await runFile(f));
  } catch (e) {
    results.push({ file: path.relative(EXAMPLES, f), buttons: 0, calls: 0, unique: [],
                   unknown: [], notFound: [], errors: ['harness: ' + e.message], checks: [] });
  }
}

let bad = 0;
const failedChecks = [];
console.log('file'.padEnd(34) + 'btn'.padStart(4) + 'call'.padStart(6) + 'uniq'.padStart(6)
  + '  unknown  notfound  errors');
for (const r of results) {
  r.checks.filter(c => !c.ok).forEach(c => failedChecks.push(r.file + ': ' + c.name));
  const flag = (r.unknown.length || r.notFound.length || r.errors.length
    || r.checks.some(c => !c.ok)) ? '  <-- FAIL' : '';
  if (flag) bad++;
  console.log(r.file.padEnd(34) + String(r.buttons).padStart(4) + String(r.calls).padStart(6)
    + String(r.unique.length).padStart(6) + String(r.unknown.length).padStart(9)
    + String(r.notFound.length).padStart(10) + String(r.errors.length).padStart(8) + flag);
}

const allUnknown = new Map();
const allNotFound = new Map();
const allErrors = [];
for (const r of results) {
  r.unknown.forEach(c => allUnknown.set(c.action, (allUnknown.get(c.action) || 0) + 1));
  r.notFound.forEach(c => allNotFound.set(c.action, (allNotFound.get(c.action) || 0) + 1));
  r.errors.forEach(e => allErrors.push(r.file + ': ' + e));
}

console.log('\n--- distinct actions exercised ---');
const every = new Set(results.flatMap(r => r.unique));
console.log([...every].sort().join(', '));
console.log(`\ntotal distinct actions: ${every.size}`);

if (allUnknown.size) {
  console.log('\n--- UNKNOWN_ACTION (prefix registered, action not in SUPPORTED_ACTIONS) ---');
  for (const [a, n] of allUnknown) console.log(`  ${a}  x${n}`);
}
if (allNotFound.size) {
  console.log('\n--- ROUTE_NOT_FOUND (prefix not registered) ---');
  for (const [a, n] of allNotFound) console.log(`  ${a}  x${n}`);
}
if (allErrors.length) {
  console.log('\n--- runtime errors ---');
  allErrors.forEach(e => console.log('  ' + e));
}

const totalChecks = results.reduce((n, r) => n + r.checks.length, 0);
const passedChecks = results.reduce((n, r) => n + r.checks.filter(c => c.ok).length, 0);
console.log(`\nDOM assertions: ${passedChecks}/${totalChecks} passed`);
if (failedChecks.length) {
  console.log('--- failed assertions ---');
  failedChecks.forEach(c => console.log('  ' + c));
}

const prefixOnly = [...every].filter(a => {
  const prefix = a.split('.')[0];
  const bare = a.slice(prefix.length + 1);
  return ROUTES[prefix] && !EXPLICIT[prefix].has(bare);
});
console.log(`\nactions reachable only via prefix routing (not in SUPPORTED_ACTIONS): ${prefixOnly.length}`);
if (prefixOnly.length) console.log('  ' + prefixOnly.join(', '));

console.log(`\n${results.length} files, ${bad} with problems`);
process.exit(bad ? 1 : 0);
