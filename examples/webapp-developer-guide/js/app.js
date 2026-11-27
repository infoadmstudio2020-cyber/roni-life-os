/**
 * RONiLiFEOS ডেভেলপার গাইড — ডেমো লজিক
 *
 * প্রতিটি হ্যান্ডলার একটি বাস্তব native action কল করে।
 * প্যারামিটারের নাম Java handler থেকে নেওয়া — অনুমান নয়।
 */
(function () {
  'use strict';

  var output = document.getElementById('output');
  var statusDot = document.getElementById('status-dot');
  var statusText = document.getElementById('status-text');
  var toolbarHint = document.getElementById('toolbar-hint');

  var bridgeReady = false;

  /* ---------------------------------------------------------------- */
  /* output                                                            */
  /* ---------------------------------------------------------------- */

  function print(label, value, isError) {
    var text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    output.classList.toggle('error', !!isError);
    output.textContent = '▸ ' + label + '\n' + text;
  }

  function fail(label, error) {
    print(label + ' — ব্যর্থ [' + (error.code || 'ERROR') + ']', error.message || String(error), true);
  }

  function setStatus(ok, text) {
    statusDot.className = 'dot ' + (ok ? 'ok' : 'bad');
    statusText.textContent = text;
  }

  /* ---------------------------------------------------------------- */
  /* boot                                                              */
  /* ---------------------------------------------------------------- */

  async function boot() {
    try {
      await Native.ready(4000);
      bridgeReady = true;
    } catch (error) {
      setStatus(false, 'ব্রিজ পাওয়া যায়নি — সাধারণ ব্রাউজারে চলছে');
      print('Bridge', 'NativeRuntimeBridgeClient পাওয়া যায়নি।\n' +
        'এটি স্বাভাবিক যদি আপনি ডেস্কটপ ব্রাউজারে খুলেছেন।\n' +
        'RONiLiFEOS-এ প্রজেক্ট হিসেবে খুললে সব বাটন কাজ করবে।');
      return;
    }

    var app = await Native.call('device.getAppInfo');
    setStatus(true, app.appLabel + ' · v' + app.versionName + ' · bridge প্রস্তুত');

    await Native.ui.title('ডেভেলপার গাইড', 'RONiLiFEOS Web SDK');
    await syncTheme();

    Native.on('theme.changed', syncTheme);
    Native.on('ui.toolbarAction', function (payload) {
      print('ui.toolbarAction', payload);
    });
    Native.on('ui.drawerItemSelected', function (payload) {
      print('ui.drawerItemSelected', payload);
    });
  }

  async function syncTheme() {
    try {
      var theme = await Native.theme.get();
      document.body.classList.toggle('theme-light', !theme.darkMode);
      var css = await Native.theme.cssVariables();
      document.getElementById('native-theme').textContent = css.css || '';
    } catch (error) {
      /* থিম না থাকলে ডিফল্ট ডার্ক থাকবে */
    }
  }

  /* ---------------------------------------------------------------- */
  /* handlers                                                          */
  /* ---------------------------------------------------------------- */

  var handlers = {

    /* ---- UI ---- */

    toast: async function () {
      var result = await Native.ui.toast('হ্যালো RONiLiFEOS!', 'success');
      print('ui.showToast', result);
    },

    alert: async function () {
      var result = await Native.ui.alert('স্বাগতম', 'এটি native AlertDialog — WebView-এর DOM নয়।');
      print('ui.showAlert', result);
    },

    confirm: async function () {
      var confirmed = await Native.ui.confirm('মুছে ফেলবেন?', 'এই কাজটি ফিরিয়ে আনা যাবে না।', 'মুছুন', 'বাতিল');
      print('ui.showConfirm → ui.confirm', { confirmed: confirmed });
    },

    sheet: async function () {
      var selected = await Native.ui.actionSheet('একটি অপশন বেছে নিন', [
        { title: 'সম্পাদনা' },
        { title: 'শেয়ার' },
        { title: 'মুছে ফেলুন', destructive: true }
      ]);
      print('ui.showActionSheet → ui.actionSelected', selected);
    },

    snackbar: async function () {
      var result = await Native.ui.snackbar('নোট সংরক্ষিত হয়েছে', 'ফিরিয়ে আনুন');
      print('ui.showSnackbar', result);
    },

    loading: async function () {
      var loading = Native.ui.loading('অনুগ্রহ করে অপেক্ষা করুন…');
      await loading.show();
      print('ui.showLoading', { shown: true });
      setTimeout(async function () {
        await loading.hide();
        print('ui.hideLoading', { hidden: true });
      }, 2000);
    },

    /* ---- toolbar ---- */

    title: async function () {
      var stamp = new Date().toLocaleTimeString('bn-BD');
      var result = await Native.ui.title('ডেভেলপার গাইড', 'আপডেট: ' + stamp);
      print('ui.setToolbarTitle + ui.setToolbarSubtitle', result);
    },

    'toolbar-action': async function () {
      var result = await Native.call('ui.addToolbarAction', { id: 'demo', label: 'ডেমো' });
      toolbarHint.textContent = 'টুলবারে "ডেমো" যোগ হয়েছে — চেপে দেখুন।';
      print('ui.addToolbarAction', result);
    },

    tab: async function () {
      await Native.call('ui.addTab', { tabId: 'home', label: 'হোম' });
      await Native.call('ui.addTab', { tabId: 'settings', label: 'সেটিংস' });
      var result = await Native.call('ui.selectTab', { tabId: 'home' });
      print('ui.addTab ×2 + ui.selectTab', result);
    },

    badge: async function () {
      var result = await Native.call('ui.setBadge', { tabId: 'home', count: 3 });
      print('ui.setBadge', result);
    },

    /* ---- device ---- */

    'device-info': async function () {
      var info = await Native.device.info();
      print('device.getInfo', info);
    },

    battery: async function () {
      var battery = await Native.device.battery();
      print('device.getBattery', battery);
    },

    network: async function () {
      var state = await Native.device.network();
      print('device.getNetworkState', state);
    },

    clipboard: async function () {
      await Native.device.copy('RONiLiFEOS থেকে কপি করা টেক্সট');
      var pasted = await Native.device.paste();
      print('device.copyToClipboard → device.readClipboard', pasted);
    },

    share: async function () {
      var result = await Native.device.share('RONiLiFEOS Web SDK দিয়ে বানানো অ্যাপ', 'RONiLiFEOS');
      print('device.shareText', result);
    },

    vibrate: async function () {
      var result = await Native.device.vibrate();
      print('device.vibrate', result);
    },

    /* ---- storage ---- */

    'storage-set': async function () {
      var key = document.getElementById('storage-key').value.trim();
      var value = document.getElementById('storage-value').value;
      if (!key) return print('storage.set', 'কী খালি রাখা যাবে না', true);
      var result = await Native.storage.set(key, value);
      print('storage.set', { key: key, value: value, result: result });
    },

    'storage-get': async function () {
      var key = document.getElementById('storage-key').value.trim();
      var result = await Native.storage.get(key);
      print('storage.get', result);
    },

    'storage-keys': async function () {
      var keys = await Native.storage.keys();
      var all = await Native.storage.all();
      print('storage.keys + storage.getAll', { keys: keys.keys, all: all });
    },

    'storage-clear': async function () {
      var confirmed = await Native.ui.confirm('স্টোরেজ মুছবেন?', 'এই প্রজেক্টের সব কী মুছে যাবে।');
      if (!confirmed) return print('storage.clear', 'বাতিল করা হয়েছে');
      var result = await Native.storage.clear();
      print('storage.clear', result);
    },

    /* ---- theme ---- */

    'theme-light': async function () {
      var result = await Native.theme.setMode('LIGHT');
      document.body.classList.add('theme-light');
      print('theme.setThemeMode', result);
    },

    'theme-dark': async function () {
      var result = await Native.theme.setMode('DARK');
      document.body.classList.remove('theme-light');
      print('theme.setThemeMode', result);
    },

    'theme-system': async function () {
      var result = await Native.theme.setMode('SYSTEM');
      print('theme.setThemeMode', result);
    },

    'theme-css': async function () {
      var css = await Native.theme.cssVariables();
      print('theme.getCssVariables', css);
    },

    /* ---- security ---- */

    sha256: async function () {
      var result = await Native.security.sha256('RONiLiFEOS');
      print('security.hash.sha256 (input: "RONiLiFEOS")', result);
    },

    biometric: async function () {
      var capability = await Native.security.capability();
      if (!capability.available) {
        return print('security.biometric.capability', capability);
      }
      var outcome = await Native.security.authenticate('ডেমো যাচাই');
      print('security.biometric.authenticate → security.biometric.result', outcome);
    },

    keystore: async function () {
      var alias = 'guide_demo_key';
      var has = await Native.security.keystore.hasKey(alias);
      if (!has.hasKey) {
        await Native.security.keystore.generate(alias, 'AES_GCM_256');
      }
      var encrypted = await Native.security.keystore.encrypt(alias, 'গোপন বার্তা');
      var decrypted = await Native.security.keystore.decrypt(alias, encrypted.ciphertext, encrypted.iv);
      print('security.keystore.encrypt → decrypt', {
        alias: alias,
        ciphertext: encrypted.ciphertext,
        iv: encrypted.iv,
        plaintext: decrypted.plaintext
      });
    },

    permission: async function () {
      var result = await Native.security.checkPermission('android.permission.CAMERA');
      print('security.permission.check', result);
    },

    /* ---- notification ---- */

    'notif-permission': async function () {
      var result = await Native.notification.permission();
      print('notification.permission', result);
    },

    'notif-post': async function () {
      var result = await Native.notification.post({
        key: 'guide-demo',
        title: 'RONiLiFEOS ডেমো',
        body: 'এই নোটিফিকেশনটি notification.post দিয়ে তৈরি।',
        style: 'BIG_TEXT',
        bigText: 'key একই থাকলে পুনরায় post করলে আগের নোটিফিকেশনটি প্রতিস্থাপিত হয়।',
        priority: 'DEFAULT',
        autoCancel: true,
        deepLink: 'guide://demo'
      });
      print('notification.post', result);
    },

    'notif-cancel': async function () {
      var result = await Native.notification.cancel('guide-demo');
      print('notification.cancel', result);
    },

    /* ---- network ---- */

    http: async function () {
      var response = await Native.network.get('https://httpbin.org/json', { timeoutMs: 15000 });
      var preview = response.body ? response.body.slice(0, 400) : '';
      print('network.request', {
        statusCode: response.statusCode,
        latencyMs: response.latencyMs,
        bodyPreview: preview
      });
    },

    echo: async function () {
      var result = await Native.call('echo.echo', { ping: Date.now() });
      print('echo.echo', result);
    },

    inventory: async function () {
      var routes = ['ui', 'web', 'device', 'storage', 'theme', 'security', 'analytics',
                    'devtools', 'network', 'platform', 'notification', 'camera',
                    'filemanager', 'automation', 'logger', 'service', 'runtime'];
      var report = {};
      for (var i = 0; i < routes.length; i++) {
        report[routes[i]] = (await Native.isSupported(routes[i])) ? 'উপলব্ধ' : 'অনুপস্থিত';
      }
      print('রুট ইনভেন্টরি', report);
    },

    'clear-output': function () {
      output.classList.remove('error');
      output.textContent = 'আউটপুট মুছে ফেলা হয়েছে।';
    }
  };

  /* ---------------------------------------------------------------- */
  /* wiring                                                            */
  /* ---------------------------------------------------------------- */

  document.addEventListener('click', async function (event) {
    var button = event.target.closest('button[data-action]');
    if (!button) return;

    var name = button.getAttribute('data-action');
    var handler = handlers[name];
    if (!handler) return;

    if (!bridgeReady && name !== 'clear-output') {
      return print(name, 'Bridge প্রস্তুত নয়। RONiLiFEOS-এ প্রজেক্ট হিসেবে খুলুন।', true);
    }

    button.disabled = true;
    try {
      await handler();
    } catch (error) {
      fail(name, error);
    } finally {
      button.disabled = false;
    }
  });

  document.addEventListener('DOMContentLoaded', boot);
  if (document.readyState !== 'loading') boot();
})();
