# RONiLiFEOS — Storage API

**Native class:** `com.roni.library.ui.handler.NativeStorageCommandHandler`
**Route:** `/storage` · **Backend:** `EncryptedStore` (AES-GCM) or `KeyValueStore` (plaintext)
**Scoping:** `StorageScope` — `__p:<escaped-project-id>:<user-key>`

---

## 1. Two storage layers, one API

`NativeStorageCommandHandler` resolves its backend at call time:

```java
StorageEngine engine = StorageEngine.getInstance();
if (engine == null || !engine.isInitialized()) {
    return NativeApiResponse.error("STORAGE_UNAVAILABLE", "Storage engine is not initialized");
}
```

`StorageEngine` exposes both an `EncryptedStore` and a `KeyValueStore`. The handler prefers the encrypted store; when encryption is unavailable it falls back to the plaintext store. **Your JavaScript never sees which one answered** — the API is identical.

| Backend | At rest | When used |
| --- | --- | --- |
| `EncryptedStore` | AES-GCM, key in Android Keystore | Default |
| `KeyValueStore` | Plain `SharedPreferences` | Encryption unavailable, or explicitly configured |

---

## 2. Key scoping

Every project shares one store. `StorageScope` prevents collisions:

```
user key   "theme"
project id "notes-app"
stored key "__p:notes-app:theme"
```

### 2.1 Rules

| Rule | Detail |
| --- | --- |
| Prefix | `__p:` |
| Separator | `:` |
| Escaping | `%` → `%25`, `:` → `%3A` (injective — two ids can never collide) |
| No project id | `prefixFor(null)` and `prefixFor("")` return `""` → the **legacy shared namespace** |
| Reserved marker | In the legacy namespace, a user key starting with `__p:` is rejected with `RESERVED_KEY` |
| Cross-project reads | `unscopedKey()` returns `null` for a key belonging to another project, so it is invisible |

### 2.2 What this means for you

```js
// Project "notes-app"
await Native.call('storage.set', { key: 'theme', value: 'dark' });
// physically stored as "__p:notes-app:theme"

// Project "todo-app" reading the same key
const { exists } = await Native.call('storage.has', { key: 'theme' });
// exists === false
```

`storage.keys` and `storage.getAll` only return keys belonging to the **current** project. The legacy claim marker `__p:__legacy_claimed` is never listed.

### 2.3 Reserved keys

```js
try {
  await Native.call('storage.set', { key: '__p:mine', value: 1 });
} catch (error) {
  // error.code === 'RESERVED_KEY'  (only in the legacy namespace)
}
```

Inside a project namespace any user key is legal, because the prefix itself is escaped.

---

## 3. Value types

`EncryptedStore` encodes the type into the stored string:

| JS value | Stored form | Round-trips as |
| --- | --- | --- |
| `"hello"` | `s:hello` | `"hello"` |
| `42` | `i:42` | `42` |
| `3.14` | `d:3.14` | `3.14` |
| `true` | `b:true` | `true` |
| `null` | `n:` | `null` |
| `1234567890123` (long) | `l:1234567890123` | `1234567890123` |

### 3.1 What is **not** supported

| Value | Behaviour |
| --- | --- |
| Object | Stored as `"[object Object]"` — **silently wrong** |
| Array | Stored as `"a,b,c"` — **silently wrong** |
| `undefined` | Treated as `null` |
| `NaN` / `Infinity` | Stored as a string |
| `Date` | Stored as its `toString()` |

**Always serialise structured data yourself:**

```js
await Native.call('storage.set', { key: 'profile', value: JSON.stringify(profile) });

const { value } = await Native.call('storage.get', { key: 'profile' });
const profile = value ? JSON.parse(value) : null;
```

---

## 4. Actions

### 4.1 `storage.set`

| | |
| --- | --- |
| **Parameters** | `key` (O, `""`), `value` (any JSON scalar) |
| **Returns** | `{ saved: true }` |
| **Errors** | `STORAGE_UNAVAILABLE`, `RESERVED_KEY`, `INTERNAL_ERROR` |

```js
await Native.call('storage.set', { key: 'token', value: 'abc123' });
await Native.call('storage.set', { key: 'count', value: 7 });
await Native.call('storage.set', { key: 'enabled', value: true });
```

An empty `key` is accepted and stored under the project prefix alone. Do not rely on it.

### 4.2 `storage.get`

| | |
| --- | --- |
| **Parameters** | `key` (O, `""`) |
| **Returns** | `{ key, value, exists }` |

```js
const { value, exists } = await Native.call('storage.get', { key: 'token' });
if (!exists) { /* first run */ }
```

A missing key returns `{ key: 'token', value: null, exists: false }` — **not** an error.

### 4.3 `storage.has`

| | |
| --- | --- |
| **Parameters** | `key` (O, `""`) |
| **Returns** | `{ exists: boolean }` |

Cheaper than `get` when you only need presence.

### 4.4 `storage.remove`

| | |
| --- | --- |
| **Parameters** | `key` (O, `""`) |
| **Returns** | `{ removed: true }` |

Removing a non-existent key succeeds.

### 4.5 `storage.keys`

| | |
| --- | --- |
| **Parameters** | — |
| **Returns** | `{ keys: string[] }` |

Returns **user keys** (prefix stripped), current project only.

### 4.6 `storage.getAll`

| | |
| --- | --- |
| **Parameters** | — |
| **Returns** | flat `{ key: value }` map |

```js
const all = await Native.call('storage.getAll');
// { token: 'abc123', count: 7, enabled: true }
```

### 4.7 `storage.clear`

| | |
| --- | --- |
| **Parameters** | — |
| **Returns** | `{ cleared: <number> }` |

Removes **only** the current project's keys. Other projects are untouched.

---

## 5. Complete example

```js
const Store = {
  async set(key, value) {
    await Native.call('storage.set', { key, value });
  },

  async get(key, fallback = null) {
    const { value, exists } = await Native.call('storage.get', { key });
    return exists ? value : fallback;
  },

  async setJSON(key, object) {
    await Native.call('storage.set', { key, value: JSON.stringify(object) });
  },

  async getJSON(key, fallback = null) {
    const { value, exists } = await Native.call('storage.get', { key });
    if (!exists || value === null) return fallback;
    try { return JSON.parse(value); } catch (e) { return fallback; }
  },

  async remove(key) {
    await Native.call('storage.remove', { key });
  },

  async keys() {
    const { keys } = await Native.call('storage.keys');
    return keys;
  },

  async clear() {
    const { cleared } = await Native.call('storage.clear');
    return cleared;
  }
};

// usage
await Store.setJSON('settings', { theme: 'dark', lang: 'bn' });
const settings = await Store.getJSON('settings', { theme: 'light', lang: 'bn' });
```

---

## 6. `localStorage` vs `storage.*`

| | `localStorage` | `storage.*` |
| --- | --- | --- |
| Isolation | Per virtual origin (`https://<project>.runtime.local`) | Per project id |
| Encryption | ❌ plain text | ✅ AES-GCM |
| Size limit | ~5 MB per origin | Bounded by app storage |
| Sync | Synchronous | Asynchronous |
| Survives WebView data clear | ❌ | ✅ |
| Survives app reinstall | ❌ | ❌ |
| Accessible from native code | ❌ | ✅ |
| Works before bridge ready | ✅ | ❌ |

**Guidance**

- Drafts, UI state, scroll positions → `localStorage` (fast, synchronous).
- Tokens, user data, anything you would not want in a plaintext file → `storage.*`.
- Large blobs → `filemanager.writeFile`.

---

## 7. Migration from the legacy namespace

Before project scoping existed, every project shared one namespace. `StorageScope` keeps those keys readable:

```java
public static final String LEGACY_CLAIM_KEY = "__p:__legacy_claimed";
```

The first project to open after the upgrade claims the legacy keys; later projects see an empty store. There is **no** automatic per-project migration — if you need to move data, do it explicitly:

```js
async function migrateLegacyKeys() {
  const marker = await Native.call('storage.get', { key: '__legacy_migrated' });
  if (marker.exists) return;

  // Read whatever legacy keys you know about, then re-write them scoped.
  const legacy = await Native.call('storage.getAll');
  for (const [key, value] of Object.entries(legacy)) {
    if (key.startsWith('__p:')) continue;
    await Native.call('storage.set', { key, value });
  }
  await Native.call('storage.set', { key: '__legacy_migrated', value: true });
}
```

---

## 8. Errors

| Code | Cause | Fix |
| --- | --- | --- |
| `STORAGE_UNAVAILABLE` | `StorageEngine` not initialised | Ensure the host app called `StorageEngine.initialize()` |
| `RESERVED_KEY` | Key starts with `__p:` in the legacy namespace | Rename the key, or run inside a project |
| `INVALID_REQUEST` | Null request object | Pass a payload |
| `INTERNAL_ERROR` | Keystore failure, disk full | Check `logger` output |

---

## 9. Limits and performance

| Concern | Guidance |
| --- | --- |
| Value size | Keep under ~100 KB per key. Larger values slow every `getAll`. |
| Key count | `getAll` decrypts every value — keep it under a few hundred keys. |
| Write frequency | Each `set` is a synchronous disk write on the native side. Debounce rapid updates. |
| Binary data | Base64-encode and store in `filemanager`, not `storage`. |

```js
// Debounced writer
function debouncedSet(key, delay = 400) {
  let timer = null;
  return function (value) {
    clearTimeout(timer);
    timer = setTimeout(() => Native.call('storage.set', { key, value }), delay);
  };
}

const saveDraft = debouncedSet('draft');
editor.addEventListener('input', () => saveDraft(editor.value));
```

---

## 10. Related documents

- `NATIVE_API_REFERENCE.md` §4 — action signatures
- `SECURITY_AND_PERMISSIONS.md` — encryption model and Keystore
- `WEB_APP_DEVELOPER_GUIDE.md` §6 — choosing a persistence layer
