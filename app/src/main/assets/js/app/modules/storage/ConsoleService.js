/**
 * ConsoleService — manages in-memory + persisted log entries.
 */
import { eventBus } from '../../core/EventBus.js';
import { storage } from './StorageService.js';
import { WebViewBridge } from '../../core/WebViewBridge.js';

const MAX_ENTRIES = 1000;
const LEVELS = ['info', 'success', 'warning', 'error', 'debug'];

export class ConsoleService {
  #entries = [];

  constructor() { this.#init(); }

  async #init() {
    try {
      await storage.init();
      const saved = await storage.getAll('logs');
      this.#entries = saved.sort((a, b) => a.ts - b.ts);
    } catch { /* DB not ready yet — start empty */ }
  }

  #add(level, message) {
    const entry = {
      id:      `log_${Date.now()}_${Math.random().toString(36).slice(2,6)}`,
      level,
      message: String(message),
      time:    new Date().toLocaleTimeString(),
      ts:      Date.now(),
    };
    this.#entries.push(entry);
    if (this.#entries.length > MAX_ENTRIES) this.#entries.shift();
    eventBus.emit('console:entry', entry);
    // Persist asynchronously
    storage.put('logs', entry).catch(() => {});
    return entry;
  }

  info(msg)    { return this.#add('info', msg);    }
  success(msg) { return this.#add('success', msg); }
  warn(msg)    { return this.#add('warning', msg); }
  warning(msg) { return this.#add('warning', msg); }
  error(msg)   { return this.#add('error', msg);   }
  debug(msg)   { return this.#add('debug', msg);   }

  /** Return entries, optionally filtered by level. */
  getEntries(level = null) {
    if (!level) return [...this.#entries];
    return this.#entries.filter(e => e.level === level);
  }

  /** Clear in-memory entries and IndexedDB logs. */
  clear() {
    this.#entries = [];
    storage.clear('logs').catch(() => {});
    eventBus.emit('console:cleared');
  }

  /** Download entries as a plain-text file. */
  exportLogs() {
    const text = this.#entries
      .map(e => `[${e.time}] [${e.level.toUpperCase()}] ${e.message}`)
      .join('\n');
    WebViewBridge.downloadFile(`android-lib-gen-logs-${Date.now()}.txt`, text, 'text/plain;charset=utf-8');
  }
}

export const consoleService = new ConsoleService();
