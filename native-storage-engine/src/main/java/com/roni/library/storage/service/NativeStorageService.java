package com.roni.library.storage.service;

import com.roni.library.service.BaseService;
import com.roni.library.storage.StorageException;
import com.roni.library.storage.crypto.KeystoreKeyProvider;
import com.roni.library.storage.database.StorageDatabase;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

/**
 * Owns the storage root directory, the document database and the encrypted preference files.
 *
 * <p>Preferences are encrypted with the {@code AndroidKeyStore} master key when one is
 * available; otherwise the explicitly configured passphrase is used. There is no built-in
 * default passphrase.</p>
 */
public class NativeStorageService extends BaseService {
    private final File storageRootDirectory;
    private final SecretKey secretKey;
    private final String passphrase;
    private final StorageDatabase database;
    private final ConcurrentHashMap<String, EncryptedPreferences> preferencesMap = new ConcurrentHashMap<>();

    public NativeStorageService() {
        this(new File(System.getProperty("java.io.tmpdir", "/tmp"), "NativeStorageEngine"),
                new KeystoreKeyProvider().getOrCreateKey(), null);
    }

    /**
     * Legacy passphrase constructor.
     */
    public NativeStorageService(File storageRootDirectory, String secretKey) {
        this(storageRootDirectory, null, secretKey);
    }

    /**
     * @param storageRootDirectory root directory, created when missing
     * @param secretKey            keystore-backed key, or {@code null}
     * @param passphrase           fallback passphrase, or {@code null}
     */
    public NativeStorageService(File storageRootDirectory, SecretKey secretKey, String passphrase) {
        super("NativeStorageService", 10);
        this.storageRootDirectory = storageRootDirectory;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        if (this.storageRootDirectory != null && !this.storageRootDirectory.exists()) {
            this.storageRootDirectory.mkdirs();
        }
        File dbDir = new File(this.storageRootDirectory, "databases");
        this.database = new StorageDatabase(dbDir);
    }

    @Override
    public void onInitialize() {
        try {
            super.onInitialize();
        } catch (Exception ignored) {}
    }

    @Override
    public void onDispose() {
        try {
            super.onDispose();
        } catch (Exception ignored) {}
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        if (database != null) {
            database.checkpointAll();
        }
        super.onStop();
    }

    public EncryptedPreferences getPreferences(String name) {
        String safeName = (name != null && !name.trim().isEmpty()) ? name.trim() : "default";
        return preferencesMap.computeIfAbsent(safeName, n -> {
            File prefDir = new File(storageRootDirectory, "preferences");
            if (secretKey != null) {
                return new EncryptedPreferences(n, prefDir, secretKey);
            }
            if (passphrase != null && !passphrase.isEmpty()) {
                return new EncryptedPreferences(n, prefDir, passphrase);
            }
            throw new StorageException("MISSING_KEY",
                    "No encryption key available for preferences '" + n + "'");
        });
    }

    public StorageDatabase getDatabase() {
        return database;
    }

    public File getStorageRootDirectory() {
        return storageRootDirectory;
    }

    public boolean isKeyBacked() {
        return secretKey != null;
    }
}
