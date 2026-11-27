/* নোটখাতা — integrated demo */
(function () {
  'use strict';

  var TITLES = { '/': 'হোম', '/notes': 'নোট', '/settings': 'সেটিংস' };
  var SUBS = { '/': 'নোটখাতা', '/notes': 'আপনার সব নোট', '/settings': 'পছন্দ ও তথ্য' };

  /* ---------- helpers ---------- */

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function fmtDate(ts) {
    try {
      return new Date(ts).toLocaleString('bn-BD', {
        day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
      });
    } catch (e) {
      return new Date(ts).toISOString();
    }
  }

  function syncShell(path) {
    var t = TITLES[path] || 'নোটখাতা';
    var s = SUBS[path] || '';
    document.getElementById('page-title').textContent = t;
    document.getElementById('page-sub').textContent = s;
    document.title = t;
    Native.call('ui.setToolbarTitle', { title: t }).catch(function () {});
    Native.call('ui.setToolbarSubtitle', { subtitle: s }).catch(function () {});
  }

  /* ---------- data ---------- */

  function loadNotes() {
    return Store.getJson('items', []);
  }

  function saveNotes(list) {
    return Store.setJson('items', list);
  }

  /* ---------- views ---------- */

  function viewHome(root) {
    var card = el('section', 'card');
    card.appendChild(el('h2', null, 'স্বাগতম'));
    var line = el('p', 'muted', 'ডিভাইস তথ্য লোড হচ্ছে…');
    card.appendChild(line);
    root.appendChild(card);

    var stats = el('section', 'card');
    stats.appendChild(el('h2', null, 'পরিসংখ্যান'));
    var statBox = el('div', 'stats');
    stats.appendChild(statBox);
    root.appendChild(stats);

    var quick = el('section', 'card');
    quick.appendChild(el('h2', null, 'দ্রুত কাজ'));
    var row = el('div', 'row');
    var bNew = el('button', null, 'নতুন নোট');
    bNew.addEventListener('click', function () { Router.navigate('/notes'); });
    var bExport = el('button', 'secondary', 'CSV এক্সপোর্ট');
    bExport.addEventListener('click', exportCsv);
    row.appendChild(bNew);
    row.appendChild(bExport);
    quick.appendChild(row);
    root.appendChild(quick);

    Native.call('device.getInfo').then(function (info) {
      line.textContent = (info.model || 'ডিভাইস') + ' · Android '
        + (info.androidVersion || '?') + ' · ' + (info.width || window.innerWidth)
        + '×' + (info.height || window.innerHeight);
    }).catch(function () {
      line.textContent = 'ব্রাউজার মোড · ' + window.innerWidth + '×' + window.innerHeight;
    });

    loadNotes().then(function (list) {
      statBox.innerHTML = '';
      statBox.appendChild(stat('মোট নোট', list.length));
      statBox.appendChild(stat('আজকের', list.filter(function (n) {
        return new Date(n.at).toDateString() === new Date().toDateString();
      }).length));
      statBox.appendChild(stat('অক্ষর', list.reduce(function (a, n) {
        return a + (n.body ? n.body.length : 0);
      }, 0)));
    });
  }

  function stat(label, value) {
    var d = el('div', 'stat');
    d.appendChild(el('strong', null, String(value)));
    d.appendChild(el('span', null, label));
    return d;
  }

  function viewNotes(root) {
    var form = el('section', 'card');
    form.appendChild(el('h2', null, 'নতুন নোট'));
    var title = el('input');
    title.placeholder = 'শিরোনাম';
    title.id = 'note-title';
    var body = el('textarea');
    body.placeholder = 'নোটের 내용…';
    body.rows = 4;
    body.id = 'note-body';
    var save = el('button', null, 'সেভ করুন');
    form.appendChild(title);
    form.appendChild(body);
    form.appendChild(save);
    root.appendChild(form);

    var listCard = el('section', 'card');
    listCard.appendChild(el('h2', null, 'সব নোট'));
    var listBox = el('div', 'list');
    listCard.appendChild(listBox);
    root.appendChild(listCard);

    function refresh() {
      loadNotes().then(function (list) {
        listBox.innerHTML = '';
        if (!list.length) {
          listBox.appendChild(el('p', 'muted', 'এখনো কোনো নোট নেই।'));
          return;
        }
        list.slice().reverse().forEach(function (n) {
          var item = el('div', 'note');
          var head = el('div', 'note-head');
          head.appendChild(el('strong', null, n.title || '(শিরোনামহীন)'));
          head.appendChild(el('span', 'muted small', fmtDate(n.at)));
          item.appendChild(head);
          if (n.body) item.appendChild(el('p', 'note-body', n.body));

          var actions = el('div', 'row');
          var del = el('button', 'danger small-btn', 'মুছুন');
          del.addEventListener('click', function () {
            Native.confirm('মুছবেন?', n.title || 'এই নোট').then(function (ok) {
              if (!ok) return;
              loadNotes().then(function (all) {
                return saveNotes(all.filter(function (x) { return x.id !== n.id; }));
              }).then(function () {
                Native.toast('মুছে ফেলা হয়েছে', 'success');
                refresh();
              });
            });
          });
          actions.appendChild(del);
          item.appendChild(actions);
          listBox.appendChild(item);
        });
      });
    }

    save.addEventListener('click', function () {
      var t = title.value.trim();
      var b = body.value.trim();
      if (!t && !b) {
        Native.toast('শিরোনাম বা 내용 লিখুন', 'warning');
        return;
      }
      var loader = Native.loading('সেভ হচ্ছে…');
      loader.show().then(function () {
        return loadNotes();
      }).then(function (list) {
        list.push({ id: 'n' + Date.now(), title: t, body: b, at: Date.now() });
        return saveNotes(list);
      }).then(function (ok) {
        return loader.hide().then(function () {
          Native.toast(ok ? 'সেভ হয়েছে' : 'সেভ ব্যর্থ', ok ? 'success' : 'error');
          title.value = '';
          body.value = '';
          refresh();
        });
      }).catch(function (e) {
        loader.hide();
        Native.toast('ব্যর্থ: ' + e.message, 'error');
      });
    });

    refresh();
  }

  function viewSettings(root) {
    var themeCard = el('section', 'card');
    themeCard.appendChild(el('h2', null, 'থিম'));
    var row = el('div', 'row');
    ['light', 'dark'].forEach(function (mode) {
      var b = el('button', mode === 'light' ? 'secondary' : 'secondary', mode === 'light' ? 'লাইট' : 'ডার্ক');
      b.addEventListener('click', function () {
        document.documentElement.dataset.theme = mode;
        Store.set('theme', mode);
        Native.toast('থিম: ' + mode, 'info');
      });
      row.appendChild(b);
    });
    themeCard.appendChild(row);
    root.appendChild(themeCard);

    var infoCard = el('section', 'card');
    infoCard.appendChild(el('h2', null, 'ডিভাইস'));
    var kv = el('dl', 'kv');
    infoCard.appendChild(kv);
    root.appendChild(infoCard);

    var dataCard = el('section', 'card');
    dataCard.appendChild(el('h2', null, 'ডেটা'));
    var drow = el('div', 'row');
    var exp = el('button', 'secondary', 'CSV এক্সপোর্ট');
    exp.addEventListener('click', exportCsv);
    var clr = el('button', 'danger', 'সব মুছুন');
    clr.addEventListener('click', function () {
      Native.confirm('সব ডেটা মুছবেন?', 'সব নোট চলে যাবে').then(function (ok) {
        if (!ok) return;
        Native.call('storage.clear').catch(function () {}).then(function () {
          Native.toast('মুছে ফেলা হয়েছে', 'success');
          Router.navigate('/');
        });
      });
    });
    drow.appendChild(exp);
    drow.appendChild(clr);
    dataCard.appendChild(drow);
    root.appendChild(dataCard);

    Native.call('device.getInfo').then(function (info) {
      kv.innerHTML = '';
      [['মডেল', info.model], ['ব্র্যান্ড', info.brand], ['Android', info.androidVersion],
       ['স্ক্রিন', (info.width || '?') + '×' + (info.height || '?')],
       ['ভাষা', info.language], ['অ্যাপ', info.appName], ['ভার্সন', info.versionName]
      ].forEach(function (pair) {
        if (pair[1] == null) return;
        kv.appendChild(el('dt', null, pair[0]));
        kv.appendChild(el('dd', null, String(pair[1])));
      });
    }).catch(function () {
      kv.innerHTML = '';
      kv.appendChild(el('dt', null, 'মোড'));
      kv.appendChild(el('dd', null, 'ব্রাউজার'));
    });
  }

  /* ---------- export ---------- */

  function exportCsv() {
    loadNotes().then(function (list) {
      if (!list.length) {
        Native.toast('এক্সপোর্ট করার নোট নেই', 'warning');
        return;
      }
      function esc(v) {
        var s = String(v == null ? '' : v);
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
      }
      var lines = ['id,title,body,at'];
      list.forEach(function (n) {
        lines.push([n.id, n.title, n.body, new Date(n.at).toISOString()].map(esc).join(','));
      });
      var csv = '\uFEFF' + lines.join('\r\n');
      var blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'notes-' + Date.now() + '.csv';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
      Native.toast('CSV ডাউনলোড শুরু হয়েছে', 'success');
    });
  }

  /* ---------- network ---------- */

  function watchNetwork() {
    function check() {
      Native.call('device.getNetworkState').then(function (n) {
        document.body.classList.toggle('offline', !n.connected);
      }).catch(function () {
        document.body.classList.toggle('offline', !navigator.onLine);
      });
    }
    check();
    setInterval(check, 10000);
    window.addEventListener('online', check);
    window.addEventListener('offline', check);
  }

  /* ---------- drawer ---------- */

  function initDrawer() {
    if (!Native.available()) return;
    Native.call('ui.getConfig').then(function (cfg) {
      if (!cfg.drawerEnabled) return;
      var items = [
        { id: 'home', title: 'হোম' },
        { id: 'notes', title: 'নোট' },
        { id: 'settings', title: 'সেটিংস' }
      ];
      items.forEach(function (it) {
        Native.call('ui.addDrawerItem', it).catch(function () {});
      });
      Native.on('ui.drawerItemSelected', function (p) {
        Router.navigate(p.id === 'home' ? '/' : '/' + p.id);
        Native.call('ui.closeDrawer').catch(function () {});
      });
    }).catch(function () {});
  }

  /* ---------- boot ---------- */

  Router.register('/', viewHome);
  Router.register('/notes', viewNotes);
  Router.register('/settings', viewSettings);
  Router.register('*', function (root) {
    var c = el('section', 'card');
    c.appendChild(el('h2', null, '৪০৪'));
    c.appendChild(el('p', 'muted', 'এই পাতা নেই।'));
    root.appendChild(c);
  });

  document.addEventListener('DOMContentLoaded', function () {
    Store.get('theme', 'light').then(function (t) {
      document.documentElement.dataset.theme = t === 'dark' ? 'dark' : 'light';
    });

    var origRender = Router.navigate;
    window.addEventListener('hashchange', function () {
      syncShell(location.hash.replace(/^#/, '') || '/');
    });

    Router.start();
    syncShell(location.hash.replace(/^#/, '') || '/');
    watchNetwork();
    initDrawer();

    document.getElementById('theme-btn').addEventListener('click', function () {
      var next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = next;
      Store.set('theme', next);
    });

    document.getElementById('menu').addEventListener('click', function () {
      Native.call('ui.toggleDrawer').catch(function () {
        Native.toast('ড্রয়ার নেই', 'info');
      });
    });

    Native.call('device.getSafeArea').then(function (sa) {
      document.documentElement.style.setProperty('--safe-top', (sa.top || 0) + 'px');
      document.documentElement.style.setProperty('--safe-bottom', (sa.bottom || 0) + 'px');
    }).catch(function () {});
  });
})();
