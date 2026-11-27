package com.roni.library.storage.preferences;

import com.roni.library.logger.NativeLogger;
import com.roni.library.storage.StorageException;
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
 * AES-GCM encrypted key/value preferences.
 *
 * <p>Two key sources are supported:</p>
 * <ul>
 *   <li>{@link SecretKey} — the preferred path, backed by {@code AndroidKeyStore}.</li>
 *   <li>a passphrase {@link String} — legacy path, PBKDF2-derived per value.</li>
 * </ul>
 *
 * <p>There is no default passphrase. Constructing this class without key material throws
 * {@link StorageException} with code {@code MISSING_KEY}.</p>
 */
public class EncryptedPreferences {

    private static final String TAG = "NativeStorageEngine";

    private final String name;
    private final File storageFile;
    private final SecretKey secretKey;
    private final String passphrase;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Legacy passphrase constructor.
     *
     * @throws StorageException {@code MISSING_KEY} when {@code secretKey} is null or empty.
     */
    public EncryptedPreferences(String name, File storageDir, String secretKey) {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new StorageException("MISSING_KEY",
                    "EncryptedPreferences requires a non-empty passphrase or a SecretKey");
        }
        this.name = name;
        this.storageFile = storageDir != null ? new File(storageDir, name + ".encpref") : null;
        this.secretKey = null;
        this.passphrase = secretKey;
        load();
    }

    /**
     * Key-backed constructor.
     *
     * @throws StorageException {@code MISSING_KEY} when {@code key} is null.
     */
    public EncryptedPreferences(String name, File storageDir, SecretKey key) {
        if (key == null) {
            throw new StorageException("MISSING_KEY",
                    "EncryptedPreferences requires a non-null SecretKey");
        }
        this.name = name;
        this.storageFile = storageDir != null ? new File(storageDir, name + ".encpref") : null;
        this.secretKey = key;
        this.passphrase = null;
        load();
    }

    public String getName() {
        return name;
    }

    public boolean isKeyBacked() {
        return secretKey != null;
    }

    public synchronized void putString(String key, String value) {
        if (key == null) return;
        if (value == null) {
            remove(key);
            return;
        }
        cache.put(key, encrypt(value));
        persist();
    }

    public String getString(String key, String defaultValue) {
        if (key == null) return defaultValue;
        String encrypted = cache.get(key);
        if (encrypted == null) return defaultValue;
        try {
            return decrypt(encrypted);
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

    public void putLong(String key, long value) {
        putString(key, String.valueOf(value));
    }

    public long getLong(String key, long defaultValue) {
        String s = getString(key, null);
        if (s == null) return defaultValue;
        try {
            return Long.parseLong(s);
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
        return key != null && cache.containsKey(key);
    }

    public synchronized boolean remove(String key) {
        if (key == null) return false;
        boolean removed = cache.remove(key) != null;
        if (removed) persist();
        return removed;
    }

    public synchronized void clear() {
        cache.clear();
        persist();
    }

    /**
     * Forces a write to disk.
     *
     * @return true when the file was written.
     */
    public synchronized boolean flush() {
        return persist();
    }

    public Map<String, Object> getAll() {
        Map<String, Object> res = new HashMap<>();
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            try {
                res.put(entry.getKey(), decrypt(entry.getValue()));
            } catch (Exception e) {
                res.put(entry.getKey(), "[DECRYPTION_ERROR]");
            }
        }
        return Collections.unmodifiableMap(res);
    }

    private String encrypt(String plaintext) {
        return secretKey != null
                ? CryptoUtils.encrypt(plaintext, secretKey)
                : CryptoUtils.encrypt(plaintext, passphrase);
    }

    private String decrypt(String payload) {
        return secretKey != null
                ? CryptoUtils.decrypt(payload, secretKey)
                : CryptoUtils.decrypt(payload, passphrase);
    }

    /**
     * Writes the cache to disk. Never throws: a disk-full or permission failure is logged and
     * reported through the return value, leaving the in-memory cache consistent.
     */
    private synchronized boolean persist() {
        if (storageFile == null) return false;
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Properties props = new Properties();
            props.putAll(cache);
            try (FileOutputStream fos = new FileOutputStream(storageFile)) {
                props.store(fos, "EncryptedPreferences");
            }
            return true;
        } catch (Exception e) {
            logWarn("Failed to persist preferences '" + name + "': " + e.getMessage());
            return false;
        }
    }

    private synchronized void load() {
        if (storageFile == null || !storageFile.exists()) return;
        try (FileInputStream fis = new FileInputStream(storageFile)) {
            Properties props = new Properties();
            props.load(fis);
            for (String key : props.stringPropertyNames()) {
                cache.put(key, props.getProperty(key));
            }
        } catch (Exception e) {
            logWarn("Failed to load preferences '" + name + "': " + e.getMessage());
        }
    }

    private static void logWarn(String message) {
        try {
            NativeLogger.getInstance().w(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
