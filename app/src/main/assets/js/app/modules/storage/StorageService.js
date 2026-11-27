/**
 * StorageService — IndexedDB wrapper.
 * Stores: projects, settings, templates, logs, exports
 */

const DB_NAME    = 'AndroidLibGenDB';
const DB_VERSION = 3;

const STORES = {
  projects:            { keyPath: 'id' },
  settings:            { keyPath: 'key' },
  templates:           { keyPath: 'id' },
  logs:                { keyPath: 'id', autoIncrement: true },
  exports:             { keyPath: 'id' },
  ecosystem_libraries: { keyPath: 'id' },
  ecosystem_groups:    { keyPath: 'id' },
  ecosystem_cache:     { keyPath: 'key' },
  ecosystem_meta:      { keyPath: 'id' },
};

export class StorageService {
  #db = null;
  #memoryStore = {
    projects:            new Map(),
    settings:            new Map(),
    templates:           new Map(),
    logs:                new Map(),
    exports:             new Map(),
    ecosystem_libraries: new Map(),
    ecosystem_groups:    new Map(),
    ecosystem_cache:     new Map(),
    ecosystem_meta:      new Map(),
  };
  /** Fast in-memory L1 cache for instant synchronous-like reads */
  #l1Cache = new Map();

  /** Open / create the IndexedDB database. */
  async init() {
    if (this.#db) return;
    if (typeof indexedDB === 'undefined') {
      return; // fallback to in-memory store in Node / testing environments
    }
    this.#db = await this.#openDB();
  }

  #openDB() {
    return new Promise((resolve) => {
      try {
        if (typeof indexedDB === 'undefined') {
          return resolve(null);
        }
        const req = indexedDB.open(DB_NAME, DB_VERSION);

        req.onupgradeneeded = (e) => {
          const db = e.target.result;
          for (const [name, opts] of Object.entries(STORES)) {
            if (!db.objectStoreNames.contains(name)) {
              db.createObjectStore(name, opts);
            }
          }
        };

        req.onsuccess = (e) => resolve(e.target.result);
        req.onerror   = (e) => {
          console.warn('[StorageService] IndexedDB unavailable, using memory fallback:', e?.target?.error);
          resolve(null);
        };
      } catch (err) {
        console.warn('[StorageService] IndexedDB open error, using memory fallback:', err);
        resolve(null);
      }
    });
  }

  #hasStore(storeName) {
    return !!this.#db && this.#db.objectStoreNames.contains(storeName);
  }

  #tx(storeName, mode = 'readonly') {
    if (!this.#db || !this.#db.objectStoreNames.contains(storeName)) return null;
    try {
      return this.#db.transaction(storeName, mode).objectStore(storeName);
    } catch {
      return null;
    }
  }

  /** Get a single record by key (with L1 memory caching). */
  get(storeName, key) {
    const cacheKey = `${storeName}:${key}`;
    if (this.#l1Cache.has(cacheKey)) {
      return Promise.resolve(this.#l1Cache.get(cacheKey));
    }
    if (!this.#hasStore(storeName)) {
      const val = this.#memoryStore[storeName]?.get(key) ?? null;
      if (val !== null) this.#l1Cache.set(cacheKey, val);
      return Promise.resolve(val);
    }
    return new Promise((resolve, reject) => {
      const tx = this.#tx(storeName);
      if (!tx) {
        const val = this.#memoryStore[storeName]?.get(key) ?? null;
        return resolve(val);
      }
      const req = tx.get(key);
      req.onsuccess = (e) => {
        const val = e.target.result ?? null;
        if (val !== null) this.#l1Cache.set(cacheKey, val);
        resolve(val);
      };
      req.onerror   = (e) => reject(e.target.error);
    });
  }

  /** Get all records in a store. */
  getAll(storeName) {
    if (!this.#hasStore(storeName)) {
      return Promise.resolve(Array.from(this.#memoryStore[storeName]?.values() ?? []));
    }
    return new Promise((resolve, reject) => {
      const tx = this.#tx(storeName);
      if (!tx) {
        return resolve(Array.from(this.#memoryStore[storeName]?.values() ?? []));
      }
      const req = tx.getAll();
      req.onsuccess = (e) => {
        const list = e.target.result ?? [];
        const keyPath = STORES[storeName]?.keyPath || 'id';
        for (const item of list) {
          if (item && item[keyPath] !== undefined) {
            this.#l1Cache.set(`${storeName}:${item[keyPath]}`, item);
          }
        }
        resolve(list);
      };
      req.onerror   = (e) => reject(e.target.error);
    });
  }

  /** Put (upsert) a record (updates L1 cache + database). */
  put(storeName, value) {
    const keyPath = STORES[storeName]?.keyPath || 'id';
    const key = value[keyPath] || `item_${Date.now()}_${Math.random()}`;
    const cacheKey = `${storeName}:${key}`;
    this.#l1Cache.set(cacheKey, value);

    if (!this.#hasStore(storeName)) {
      if (!this.#memoryStore[storeName]) this.#memoryStore[storeName] = new Map();
      this.#memoryStore[storeName].set(key, value);
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const tx = this.#tx(storeName, 'readwrite');
      if (!tx) {
        if (!this.#memoryStore[storeName]) this.#memoryStore[storeName] = new Map();
        this.#memoryStore[storeName].set(key, value);
        return resolve();
      }
      const req = tx.put(value);
      req.onsuccess = () => resolve();
      req.onerror   = (e) => {
        this.#l1Cache.delete(cacheKey);
        reject(e.target.error);
      };
    });
  }

  /** Delete a record by key. */
  delete(storeName, key) {
    const cacheKey = `${storeName}:${key}`;
    this.#l1Cache.delete(cacheKey);

    if (!this.#hasStore(storeName)) {
      this.#memoryStore[storeName]?.delete(key);
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const tx = this.#tx(storeName, 'readwrite');
      if (!tx) {
        this.#memoryStore[storeName]?.delete(key);
        return resolve();
      }
      const req = tx.delete(key);
      req.onsuccess = () => resolve();
      req.onerror   = (e) => reject(e.target.error);
    });
  }

  /** Clear all records in a store. */
  clear(storeName) {
    for (const key of this.#l1Cache.keys()) {
      if (key.startsWith(`${storeName}:`)) {
        this.#l1Cache.delete(key);
      }
    }
    if (!this.#hasStore(storeName)) {
      this.#memoryStore[storeName]?.clear();
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const tx = this.#tx(storeName, 'readwrite');
      if (!tx) {
        this.#memoryStore[storeName]?.clear();
        return resolve();
      }
      const req = tx.clear();
      req.onsuccess = () => resolve();
      req.onerror   = (e) => reject(e.target.error);
    });
  }

  // ── Settings helpers ────────────────────────────────────────────────────

  async getSetting(key, defaultValue = null) {
    const record = await this.get('settings', key);
    return record?.value ?? defaultValue;
  }

  async setSetting(key, value) {
    await this.put('settings', { key, value });
  }

  async getAllSettings() {
    const rows = await this.getAll('settings');
    return Object.fromEntries(rows.map(r => [r.key, r.value]));
  }

  /** Estimate storage usage. */
  async estimateUsage() {
    if (navigator.storage?.estimate) return navigator.storage.estimate();
    return { usage: 0, quota: 0 };
  }
}

export const storage = new StorageService();
