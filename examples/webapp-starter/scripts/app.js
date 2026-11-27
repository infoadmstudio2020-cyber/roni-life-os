import {
  available, call, on, toast, confirm, loading,
  theme, security, analytics, devtools, network, platform, logger, service, storage, isSupported
} from './native-runtime.js';

const $ = (id) => document.getElementById(id);

function log(message) {
  const li = document.createElement('li');
  li.textContent = new Date().toLocaleTimeString('bn-BD') + ' — ' + message;
  $('log').prepend(li);
  while ($('log').children.length > 12) $('log').lastElementChild.remove();
}

function renderDevice(info) {
  const dl = $('device-info');
  dl.textContent = '';
  if (!info) {
    const dt = document.createElement('dt');
    dt.textContent = 'Native bridge নেই — ব্রাউজার মোড';
    dl.appendChild(dt);
    return;
  }
  const fields = [
    ['মডেল', info.model],
    ['প্রস্তুতকারক', info.manufacturer],
    ['Android', info.androidVersion],
    ['SDK', info.sdkInt]
  ];
  for (const [label, value] of fields) {
    if (value === undefined || value === null) continue;
    const dt = document.createElement('dt');
    dt.textContent = label;
    const dd = document.createElement('dd');
    dd.textContent = String(value);
    dl.append(dt, dd);
  }
}

function applyThemeVars(css) {
  if (!css) return;
  let style = document.getElementById('__native_theme__');
  if (!style) {
    style = document.createElement('style');
    style.id = '__native_theme__';
    document.head.appendChild(style);
  }
  style.textContent = css;
}

async function boot() {
  const badge = $('bridge-state');
  if (available()) {
    badge.textContent = 'Native সংযুক্ত';
    badge.className = 'badge ok';
    log('bridge পাওয়া গেছে');
  } else {
    badge.textContent = 'ব্রাউজার মোড';
    badge.className = 'badge off';
    log('bridge নেই — fallback চালু');
  }

  /* ---- platform ---- */
  let info = null;
  if (available() && await isSupported('platform')) {
    try {
      const result = await platform.device();
      info = result.deviceInfo || result;
    } catch (error) {
      log('platform.device ব্যর্থ: ' + error.code);
    }
  }
  renderDevice(info);

  /* ---- theme ---- */
  if (available() && await isSupported('theme')) {
    try {
      const current = await theme.get();
      $('theme-state').textContent = current.themeName + (current.darkMode ? ' (dark)' : ' (light)');
      const css = await theme.cssVariables();
      applyThemeVars(css.css);
    } catch (error) {
      log('theme.get ব্যর্থ: ' + error.code);
    }
    theme.onChange((event) => {
      $('theme-state').textContent = event.themeName + (event.darkMode ? ' (dark)' : ' (light)');
      log('ইভেন্ট theme.changed → ' + event.themeName);
    });
  } else {
    $('theme-state').textContent = 'অনুপলব্ধ';
  }

  /* ---- storage counter ---- */
  let count = 0;
  try {
    const stored = await storage.get('counter');
    count = Number(stored && stored.value) || 0;
  } catch (error) {
    count = Number(localStorage.getItem('starter:counter')) || 0;
  }
  $('counter').textContent = String(count);

  $('inc').addEventListener('click', async () => {
    count += 1;
    $('counter').textContent = String(count);
    try {
      await storage.set('counter', count);
    } catch (error) {
      localStorage.setItem('starter:counter', String(count));
      log('storage.set fallback');
    }
  });

  $('reset').addEventListener('click', async () => {
    const yes = await confirm('রিসেট', 'কাউন্টার শূন্য করবেন?');
    if (!yes) return;
    count = 0;
    $('counter').textContent = '0';
    try { await storage.remove('counter'); } catch (error) { localStorage.removeItem('starter:counter'); }
    log('কাউন্টার রিসেট');
  });

  /* ---- theme buttons ---- */
  $('theme-light').addEventListener('click', async () => {
    try {
      await theme.setMode('LIGHT');
      const css = await theme.cssVariables();
      applyThemeVars(css.css);
      log('theme.setThemeMode LIGHT');
    } catch (error) { log('theme ব্যর্থ: ' + error.code); }
  });

  $('theme-dark').addEventListener('click', async () => {
    try {
      await theme.setMode('DARK');
      const css = await theme.cssVariables();
      applyThemeVars(css.css);
      log('theme.setThemeMode DARK');
    } catch (error) { log('theme ব্যর্থ: ' + error.code); }
  });

  /* ---- analytics ---- */
  $('track').addEventListener('click', async () => {
    try {
      await analytics.track('starter_button', { screen: 'home' });
      log('analytics.trackEvent');
    } catch (error) { log('analytics ব্যর্থ: ' + error.code); }
  });

  /* ---- logger ---- */
  $('log-native').addEventListener('click', async () => {
    try {
      await logger.info('starter ping', 'STARTER');
      log('logger.log INFO');
    } catch (error) { log('logger ব্যর্থ: ' + error.code); }
  });

  /* ---- network ---- */
  $('fetch').addEventListener('click', async () => {
    try {
      const result = await network.get('https://httpbin.org/get', { timeoutMs: 8000 });
      log('network.request → ' + result.statusCode);
    } catch (error) { log('network ব্যর্থ: ' + error.code); }
  });

  /* ---- security ---- */
  $('biometric').addEventListener('click', async () => {
    try {
      const capability = await security.capability();
      if (!capability.available) {
        log('বায়োমেট্রিক অনুপলব্ধ');
        return;
      }
      await security.authenticate('Starter যাচাই');
      log('বায়োমেট্রিক প্রম্পট দেখানো হয়েছে');
    } catch (error) { log('security ব্যর্থ: ' + error.code); }
  });

  security.onResult((event) => log('biometric → ' + event.status));

  /* ---- devtools ---- */
  $('diagnostics').addEventListener('click', async () => {
    if (!await isSupported('devtools')) {
      log('devtools শুধু debug build-এ');
      return;
    }
    try {
      const health = await devtools.health();
      log('devtools.getHealth → ' + JSON.stringify(health).slice(0, 60));
    } catch (error) { log('devtools ব্যর্থ: ' + error.code); }
  });

  /* ---- ui ---- */
  $('toast').addEventListener('click', () => {
    toast('নমস্কার — এটি একটি toast');
    log('ui.showToast');
  });

  $('confirm').addEventListener('click', async () => {
    const yes = await confirm('প্রশ্ন', 'আপনি কি সম্মত?');
    log('confirm → ' + yes);
  });

  $('loading').addEventListener('click', async () => {
    const loader = loading('অপেক্ষা করুন…');
    await loader.show();
    setTimeout(() => loader.hide(), 1200);
    log('ui.showLoading');
  });

  on('ui.confirm', (event) => log('ইভেন্ট ui.confirm → ' + event.confirmed));
}

boot().catch((error) => log('boot ব্যর্থ: ' + error.message));
