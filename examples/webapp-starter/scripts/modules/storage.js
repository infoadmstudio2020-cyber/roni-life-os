/**
 * storage.* — project-scoped key/value.
 * Native না থাকলে localStorage-এ fallback (শুধু ব্রাউজার প্রিভিউর জন্য)।
 */
import { available, call } from '../native-runtime.js';

const PREFIX = 'starter:';

export async function get(key, fallback = null) {
  if (!available()) {
    const raw = localStorage.getItem(PREFIX + key);
    return raw === null ? fallback : JSON.parse(raw);
  }
  try {
    const result = await call('storage.get', { key });
    const value = result && result.value;
    return value === undefined || value === null ? fallback : value;
  } catch (error) {
    return fallback;
  }
}

export async function set(key, value) {
  if (!available()) {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
    return true;
  }
  try {
    await call('storage.set', { key, value });
    return true;
  } catch (error) {
    return false;
  }
}

export async function remove(key) {
  if (!available()) {
    localStorage.removeItem(PREFIX + key);
    return true;
  }
  try {
    await call('storage.remove', { key });
    return true;
  } catch (error) {
    return false;
  }
}
