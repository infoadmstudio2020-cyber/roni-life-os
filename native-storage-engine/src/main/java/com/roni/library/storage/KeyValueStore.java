package com.roni.library.storage;

import com.roni.library.logger.NativeLogger;
import com.roni.library.storage.crypto.CryptoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

/**
 * Persistent key/value store backed by a {@link Properties} file.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>plaintext</b> — the 2-arg constructor. Values are written as-is and a one-time
 *       warning is logged.</li>
 *   <li><b>AES-GCM</b> — the 3-arg constructor with a {@link SecretKey}. Values are encrypted
 *       before being written and decrypted on read.</li>
 * </ul>
 *
 * <p>Opening a legacy plaintext file with a key triggers a one-time migration: every value is
 * re-encrypted and the file is rewritten with a {@code __format__=2} marker.</p>
 */
public class KeyValueStore {

    private static final String TAG = "NativeStorageEngine";
    private static final String FORMAT_KEY = "__format__";
    private static final String FORMAT_VERSION = "2";

    private final String name;
    private final File file;
    private final SecretKey key;
    private final ConcurrentHashMap<String, String> memory = new ConcurrentHashMap<>();

    public KeyValueStore(String name, File baseDir) {
        this(name, baseDir, null);
    }

    public KeyValueStore(String name, File baseDir, SecretKey key) {
        this.name = name;
        this.file = baseDir != null ? new File(baseDir, name + ".kv") : null;
        this.key = key;
        if (key == null) {
            logWarn("KeyValueStore '" + name + "' is plaintext. Pass a SecretKey to enable AES-GCM.");
        }
        load();
    }

    public String getName() {
        return name;
    }

    /**
     * @return true when values are encrypted with AES-GCM.
     */
    public boolean isEncrypted() {
        return key != null;
    }

    public void putString(String key, String value) {
        if (key == null) return;
        if (value == null) {
            memory.remove(key);
        } else {
            memory.put(key, this.key != null ? CryptoUtils.encrypt(value, this.key) : value);
        }
        persist();
    }

    public String getString(String key, String defaultValue) {
        if (key == null) return defaultValue;
        String stored = memory.get(key);
        if (stored == null) return defaultValue;
        if (this.key == null) return stored;
        try {
            return CryptoUtils.decrypt(stored, this.key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void putInt(String key, int value) {
        putString(key, String.valueOf(value));
    }

    public int getInt(String key, int defaultValue) {
        String s = getString(key, null);
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void putBoolean(String key, boolean value) {
        putString(key, String.valueOf(value));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String s = getString(key, null);
        if (s == null) return defaultValue;
        return Boolean.parseBoolean(s);
    }

    public boolean contains(String key) {
        return key != null && memory.containsKey(key);
    }

    public boolean remove(String key) {
        if (key == null) return false;
        boolean removed = memory.remove(key) != null;
        if (removed) persist();
        return removed;
    }

    public void clear() {
        memory.clear();
        persist();
    }

    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : memory.entrySet()) {
            if (key == null) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                try {
                    result.put(entry.getKey(), CryptoUtils.decrypt(entry.getValue(), key));
                } catch (Exception e) {
                    result.put(entry.getKey(), "[DECRYPTION_ERROR]");
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Forces a write to disk.
     *
     * @return true when the file was written.
     */
    public synchronized boolean flush() {
        return persist();
    }

    /**
     * Writes the store atomically: the payload goes to a sibling {@code .tmp} file which is
     * fsynced and then renamed over the target. A crash mid-write therefore leaves the previous
     * file intact instead of truncating it — critical during the plaintext-to-encrypted
     * migration, where a truncated file would destroy every value.
     */
    private synchronized boolean persist() {
        if (file == null) return false;
        File tmp = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Properties props = new Properties();
            props.putAll(memory);
            if (key != null) {
                props.setProperty(FORMAT_KEY, FORMAT_VERSION);
            }
            tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                props.store(fos, "KeyValueStore");
                fos.flush();
                fos.getFD().sync();
            }
            if (!tmp.renameTo(file)) {
                // Some file systems refuse to rename over an existing file.
                if (!file.delete() || !tmp.renameTo(file)) {
                    throw new IllegalStateException("Unable to replace " + file.getName());
                }
            }
            tmp = null;
            return true;
        } catch (Exception e) {
            logWarn("Failed to persist KeyValueStore '" + name + "': " + e.getMessage());
            return false;
        } finally {
            if (tmp != null && tmp.exists() && !tmp.delete()) {
                logWarn("Unable to delete temporary file for KeyValueStore '" + name + "'");
            }
        }
    }

    private synchronized void load() {
        if (file == null || !file.exists()) return;

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        } catch (Exception e) {
            logWarn("Failed to load KeyValueStore '" + name + "': " + e.getMessage());
            return;
        }

        boolean legacyPlaintext = !FORMAT_VERSION.equals(props.getProperty(FORMAT_KEY));

        if (key == null && !legacyPlaintext) {
            // An encrypted file opened without a key: values would be returned as ciphertext.
            logWarn("KeyValueStore '" + name + "' is encrypted but was opened without a key; "
                    + "values are unavailable.");
            return;
        }

        boolean migrationFailed = false;
        for (String k : props.stringPropertyNames()) {
            if (FORMAT_KEY.equals(k)) continue;
            String value = props.getProperty(k);
            if (key != null && legacyPlaintext) {
                // One-time migration: the file predates encryption.
                try {
                    memory.put(k, CryptoUtils.encrypt(value, key));
                } catch (Exception e) {
                    // Abort the whole migration: a partially rewritten file would mix plaintext
                    // and ciphertext and could not be read back reliably.
                    migrationFailed = true;
                    logWarn("Migration of KeyValueStore '" + name + "' aborted: " + e.getMessage());
                    break;
                }
            } else {
                memory.put(k, value);
            }
        }

        if (migrationFailed) {
            memory.clear();
            return;
        }

        if (key != null && legacyPlaintext) {
            // Always rewrite so the format marker is written even for an empty store.
            persist();
        }
    }

    private static void logWarn(String message) {
        try {
            NativeLogger.getInstance().w(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
