/**
 * Validates the JavaScript wrapper (examples/bangla-notes-app/native.js) in jsdom.
 * Covers: availability detection, promise plumbing, event on/off, error propagation,
 * confirm event handling, loading guard, and browser fallback.
 */
import { JSDOM } from 'jsdom';
import fs from 'node:fs';

const SRC = fs.readFileSync(
  '/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS/examples/bangla-notes-app/native.js', 'utf8');

let pass = 0, fail = 0;
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log('  PASS  ' + name); }
  else { fail++; console.log('  FAIL  ' + name + (detail ? '  ' + detail : '')); }
};
const sleep = ms => new Promise(r => setTimeout(r, ms));

function boot({ bridge = true, timeoutMs = 12000, handler = null } = {}) {
  const code = SRC.replace('var TIMEOUT = 12000;', `var TIMEOUT = ${timeoutMs};`);
  const listeners = new Map();
  const calls = [];
  const dom = new JSDOM('<!DOCTYPE html><body></body>', {
    runScripts: 'dangerously',
    url: 'https://appassets.androidplatform.net/',
    beforeParse(window) {
      window.confirm = () => 'browser-confirm';
      if (!bridge) return;
      window.NativeRuntimeBridgeClient = {
        call(action, payload) {
          calls.push({ action, payload });
          return new window.Promise((resolve, reject) => {
            if (handler) return handler(action, payload, resolve, reject, window);
            resolve({ ok: true, action });
          });
        },
        on(t, fn) { (listeners.get(t) || listeners.set(t, []).get(t)).push(fn); },
        off(t, fn) {
          const a = listeners.get(t) || [];
          const i = a.indexOf(fn);
          if (i >= 0) a.splice(i, 1);
        },
        ping() { return 'pong'; }
      };
      window.__emit = (t, p) => (listeners.get(t) || []).forEach(fn => fn(p));
      window.__listenerCount = t => (listeners.get(t) || []).length;
    }
  });
  const s = dom.window.document.createElement('script');
  s.textContent = code;
  dom.window.document.body.appendChild(s);
  return { dom, window: dom.window, calls };
}

console.log('== 1. availability detection ==');
{
  const { window } = boot({ bridge: false });
  check('available() false without bridge', window.Native.available() === false);
  let rejected = null;
  await window.Native.call('ui.showToast', {}).catch(e => { rejected = e.message; });
  check('call rejects NATIVE_UNAVAILABLE', rejected === 'NATIVE_UNAVAILABLE', String(rejected));
  let threw = false;
  try { await window.Native.toast('hi'); } catch (e) { threw = true; }
  check('toast swallows the rejection', threw === false);
  const ok = await window.Native.confirm('t', 'm');
  check('confirm falls back to window.confirm', ok === 'browser-confirm', String(ok));
  window.close();
}

console.log('\n== 2. promise plumbing ==');
{
  const { window, calls } = boot();
  check('available() true with bridge', window.Native.available() === true);
  const r = await window.Native.call('ui.getConfig', { a: 1 });
  check('call resolves with payload', r.ok === true && r.action === 'ui.getConfig');
  check('payload forwarded', calls[0].payload.a === 1);
  const r2 = await window.Native.call('ui.getConfig');
  check('payload defaults to {}', JSON.stringify(calls[1].payload) === '{}');
  window.close();
}

console.log('\n== 3. error propagation ==');
{
  const { window } = boot({
    handler: (action, payload, resolve, reject) => reject(new Error('BOOM'))
  });
  let msg = null;
  await window.Native.call('ui.showToast', {}).catch(e => { msg = e.message; });
  check('rejection message preserved', msg === 'BOOM', String(msg));
  window.close();
}

console.log('\n== 4. timeout ==');
{
  const { window } = boot({ timeoutMs: 40, handler: () => {} });
  let msg = null;
  await window.Native.call('ui.showToast', {}).catch(e => { msg = e.message; });
  check('silent bridge times out', msg === 'CLIENT_TIMEOUT', String(msg));
  window.close();
}

console.log('\n== 5. event registration and removal ==');
{
  const { window } = boot();
  let hits = 0;
  const fn = () => { hits++; };
  window.Native.on('ui.confirm', fn);
  check('listener registered', window.__listenerCount('ui.confirm') === 1);
  window.__emit('ui.confirm', { confirmed: true });
  check('listener invoked', hits === 1);
  window.Native.off('ui.confirm', fn);
  check('listener removed', window.__listenerCount('ui.confirm') === 0);
  window.__emit('ui.confirm', { confirmed: true });
  check('removed listener not invoked', hits === 1);
  window.close();
}

console.log('\n== 6. confirm event handling ==');
{
  const { window } = boot({
    handler: (action, payload, resolve, reject, w) => {
      resolve({ shown: true });
      setTimeout(() => w.__emit('ui.confirm', { confirmed: true }), 5);
    }
  });
  const yes = await window.Native.confirm('t', 'm');
  check('confirmed=true resolves true', yes === true);
  check('listener cleaned up after resolve', window.__listenerCount('ui.confirm') === 0);
  window.close();
}
{
  const { window } = boot({
    handler: (action, payload, resolve, reject, w) => {
      resolve({ shown: true });
      setTimeout(() => w.__emit('ui.confirm', { confirmed: false }), 5);
    }
  });
  const no = await window.Native.confirm('t', 'm');
  check('confirmed=false resolves false', no === false);
  window.close();
}
{
  const { window } = boot({ handler: (a, p, resolve, reject) => reject(new Error('X')) });
  const r = await window.Native.confirm('t', 'm');
  check('bridge failure resolves false (no hang)', r === false);
  check('listener cleaned up after failure', window.__listenerCount('ui.confirm') === 0);
  window.close();
}

console.log('\n== 7. loading guard ==');
{
  const { window, calls } = boot();
  const loader = window.Native.loading('কাজ চলছে');
  await loader.hide();
  check('hide() before show() sends nothing', calls.length === 0);
  await loader.show();
  check('show() sends ui.showLoading', calls[0].action === 'ui.showLoading');
  check('text forwarded', calls[0].payload.text === 'কাজ চলছে');
  await loader.hide();
  check('hide() sends ui.hideLoading', calls[1].action === 'ui.hideLoading');
  await loader.hide();
  check('second hide() is a no-op', calls.length === 2);
  window.close();
}

console.log('\n== 8. unsupported API fallback ==');
{
  const { window } = boot({ bridge: false });
  let threw = false;
  try {
    window.Native.on('ui.confirm', () => {});
    window.Native.off('ui.confirm', () => {});
  } catch (e) { threw = true; }
  check('on/off are safe without a bridge', threw === false);
  const loader = window.Native.loading('x');
  let loadThrew = false;
  try { await loader.show(); await loader.hide(); } catch (e) { loadThrew = true; }
  check('loading is safe without a bridge', loadThrew === false);
  window.close();
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
