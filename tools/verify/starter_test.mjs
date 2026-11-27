/**
 * Runtime test for examples/webapp-starter (real ES modules, Node ESM + jsdom globals).
 * Validates the starter against the Java-derived routing table.
 */
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ROOT = '/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS/examples/webapp-starter';
const actions = JSON.parse(fs.readFileSync('actions.json', 'utf8'));
const ROUTES = {};
for (const n of ['ui', 'web', 'device', 'storage']) ROUTES[n] = new Set(actions.handlers[n].cases);

let pass = 0, fail = 0;
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log('  PASS  ' + name); }
  else { fail++; console.log('  FAIL  ' + name + (detail ? '  → ' + detail : '')); }
};
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function boot({ bridge, tag }) {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const dom = new JSDOM(html, { url: 'https://appassets.androidplatform.net/' });
  const calls = [];
  const listeners = new Map();

  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.localStorage = dom.window.localStorage;
  globalThis.HTMLElement = dom.window.HTMLElement;

  if (bridge) {
    dom.window.NativeRuntimeBridgeClient = {
      call(action, payload) {
        return new Promise((resolve, reject) => {
          const prefix = String(action).split('.')[0];
          const bare = String(action).slice(prefix.length + 1);
          calls.push({ action, payload });
          if (!ROUTES[prefix] || !ROUTES[prefix].has(bare)) {
            reject(new Error('UNKNOWN_ACTION: ' + action));
            return;
          }
          setTimeout(() => {
            if (action === 'device.getInfo') {
              resolve({ model: 'Pixel 7', manufacturer: 'Google', androidVersion: '14', sdkInt: 34 });
            } else if (action === 'storage.get') {
              resolve({ value: null });
            } else {
              resolve({ ok: true });
            }
          }, 1);
        });
      },
      on(t, fn) { (listeners.get(t) || listeners.set(t, []).get(t)).push(fn); },
      off(t, fn) {
        const a = listeners.get(t) || [];
        const i = a.indexOf(fn);
        if (i >= 0) a.splice(i, 1);
      }
    };
    dom.window.__emit = (t, p) => (listeners.get(t) || []).forEach(fn => fn(p));
  }

  await import(pathToFileURL(path.join(ROOT, 'scripts/app.js')).href + '?v=' + tag);
  await sleep(120);
  return { dom, calls, window: dom.window };
}

console.log('== 1. native mode ==');
{
  const { dom, calls, window } = await boot({ bridge: true, tag: 'native' });
  const $ = (id) => window.document.getElementById(id);

  check('badge shows connected', $('bridge-state').textContent.includes('Native সংযুক্ত'),
    $('bridge-state').textContent);
  check('device.getInfo called', calls.some(c => c.action === 'device.getInfo'));
  check('device model rendered', $('device-info').textContent.includes('Pixel 7'));
  check('counter starts at 0', $('counter').textContent === '0');

  $('inc').click();
  await sleep(60);
  check('counter increments', $('counter').textContent === '1', $('counter').textContent);
  check('storage.set called', calls.some(c => c.action === 'storage.set' && c.payload.value === 1));

  $('toast').click();
  await sleep(40);
  check('ui.showToast called', calls.some(c => c.action === 'ui.showToast'));

  $('confirm').click();
  await sleep(40);
  check('ui.showConfirm called', calls.some(c => c.action === 'ui.showConfirm'));
  window.__emit('ui.confirm', { confirmed: true });
  await sleep(40);
  check('confirm event logged', $('log').textContent.includes('ui.confirm'));

  $('loading').click();
  await sleep(40);
  check('ui.showLoading called', calls.some(c => c.action === 'ui.showLoading'));
  await sleep(1300);
  check('ui.hideLoading called', calls.some(c => c.action === 'ui.hideLoading'));

  $('reset').click();
  await sleep(60);
  window.__emit('ui.confirm', { confirmed: true });
  await sleep(60);
  check('storage.remove called after confirm', calls.some(c => c.action === 'storage.remove'));
  check('counter reset to 0', $('counter').textContent === '0');

  const unknown = calls.filter(c => !ROUTES[c.action.split('.')[0]]?.has(c.action.split('.').slice(1).join('.')));
  check('no unknown actions', unknown.length === 0, JSON.stringify(unknown.map(u => u.action)));
  dom.window.close();
}

console.log('\n== 2. browser fallback (no bridge) ==');
{
  const { dom, window } = await boot({ bridge: false, tag: 'browser' });
  const $ = (id) => window.document.getElementById(id);

  check('badge shows browser mode', $('bridge-state').textContent.includes('ব্রাউজার মোড'),
    $('bridge-state').textContent);
  check('fallback notice rendered', $('device-info').textContent.includes('ব্রাউজার মোড'));

  $('inc').click();
  await sleep(60);
  check('counter works without bridge', $('counter').textContent === '1');
  check('localStorage fallback used', window.localStorage.getItem('starter:counter') === '1',
    String(window.localStorage.getItem('starter:counter')));

  $('toast').click();
  await sleep(40);
  check('toast does not throw without bridge', true);
  dom.window.close();
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
