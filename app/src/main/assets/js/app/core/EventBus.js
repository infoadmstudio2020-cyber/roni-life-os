/**
 * EventBus — high-performance pub/sub event system.
 * Features:
 * - Unsubscribe functions returned by on()
 * - Listener count tracking for memory leak diagnostics
 * - Safe exception handling per listener
 * - Debounced event dispatching support
 */
export class EventBus {
  /** @type {Map<string, Set<Function>>} */
  #handlers = new Map();
  /** @type {Map<string, number>} */
  #debounceTimers = new Map();

  /**
   * Subscribe to an event.
   * @param {string} event
   * @param {Function} handler
   * @returns {Function} unsubscribe function
   */
  on(event, handler) {
    if (typeof handler !== 'function') return () => {};
    let set = this.#handlers.get(event);
    if (!set) {
      set = new Set();
      this.#handlers.set(event, set);
    }
    set.add(handler);
    return () => this.off(event, handler);
  }

  /**
   * Subscribe once — auto-removes after first call.
   * @param {string} event
   * @param {Function} handler
   * @returns {Function} unsubscribe function
   */
  once(event, handler) {
    if (typeof handler !== 'function') return () => {};
    const wrapper = (...args) => {
      this.off(event, wrapper);
      try { handler(...args); }
      catch (e) { console.error(`[EventBus] Error in once handler for "${event}":`, e); }
    };
    return this.on(event, wrapper);
  }

  /**
   * Unsubscribe from an event.
   * @param {string} event
   * @param {Function} handler
   */
  off(event, handler) {
    const set = this.#handlers.get(event);
    if (set) {
      set.delete(handler);
      if (set.size === 0) {
        this.#handlers.delete(event);
      }
    }
  }

  /**
   * Emit an event with optional payload synchronously.
   * @param {string} event
   * @param {*} payload
   */
  emit(event, payload) {
    const set = this.#handlers.get(event);
    if (!set || set.size === 0) return;
    // Iterate over a snapshot in case a listener unsubscribes itself during iteration
    const listeners = Array.from(set);
    for (let i = 0; i < listeners.length; i++) {
      try {
        listeners[i](payload);
      } catch (e) {
        console.error(`[EventBus] Error in handler for "${event}":`, e);
      }
    }
  }

  /**
   * Emit an event debounced by ms milliseconds.
   * @param {string} event
   * @param {*} payload
   * @param {number} delay
   */
  emitDebounced(event, payload, delay = 50) {
    if (this.#debounceTimers.has(event)) {
      clearTimeout(this.#debounceTimers.get(event));
    }
    const timer = setTimeout(() => {
      this.#debounceTimers.delete(event);
      this.emit(event, payload);
    }, delay);
    this.#debounceTimers.set(event, timer);
  }

  /**
   * Get the number of active listeners for an event.
   * @param {string} event
   * @returns {number}
   */
  listenerCount(event) {
    return this.#handlers.get(event)?.size ?? 0;
  }

  /** Remove all handlers for all events. */
  clear() {
    for (const timer of this.#debounceTimers.values()) {
      clearTimeout(timer);
    }
    this.#debounceTimers.clear();
    this.#handlers.clear();
  }
}

export const eventBus = new EventBus();
