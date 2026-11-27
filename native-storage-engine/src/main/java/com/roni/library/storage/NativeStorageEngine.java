package com.roni.library.storage;

import android.content.Context;

import com.roni.library.contracts.common.RuntimeState;
import com.roni.library.logger.NativeLogger;
import com.roni.library.service.ServiceRegistry;
import com.roni.library.storage.crypto.KeystoreKeyProvider;
import com.roni.library.storage.database.StorageDatabase;
import com.roni.library.storage.handler.StorageCommandHandler;
import com.roni.library.storage.preferences.EncryptedPreferences;
import com.roni.library.storage.service.NativeStorageService;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

/**
 * Storage engine singleton.
 *
 * <p>Accessors fail fast with {@link IllegalStateException} when the engine has not been
 * initialised. There is no silent auto-initialisation: a store created without a real
 * {@code Context} would write to a non-writable directory and lose every value.</p>
 */
public class NativeStorageEngine {

    private static final String TAG = "NativeStorageEngine";

    private static volatile NativeStorageEngine sInstance;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile NativeStorageEngineConfig config;
    private volatile Context context;
    private volatile NativeStorageService storageService;
    private volatile StorageCommandHandler commandHandler;
    private volatile EncryptedStore encryptedStore;
    private volatile KeyValueStore keyValueStore;
    private volatile LruDiskCache diskCache;
    private volatile DatabaseHelper databaseHelper;
    private volatile RuntimeState runtimeState = RuntimeState.UNINITIALIZED;

    private NativeStorageEngine() {}

    public static NativeStorageEngine getInstance() {
        if (sInstance == null) {
            synchronized (NativeStorageEngine.class) {
                if (sInstance == null) {
                    sInstance = new NativeStorageEngine();
                }
            }
        }
        return sInstance;
    }

    public synchronized NativeStorageEngineResult<Boolean> init(Context context,
                                                                NativeStorageEngineConfig config) {
        if (initialized.get()) {
            return NativeStorageEngineResult.success(true);
        }

        this.context = context != null ? context.getApplicationContext() : null;
        this.config = config != null ? config : new NativeStorageEngineConfig.Builder().build();

        File rootDir = this.config.getStorageDirectory();
        if (rootDir == null) {
            if (this.context != null && this.context.getFilesDir() != null) {
                rootDir = new File(this.context.getFilesDir(), "native_storage");
            } else {
                rootDir = new File(System.getProperty("java.io.tmpdir", "/tmp"), "native_storage");
            }
        }
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            logWarn("Storage root is not writable: " + rootDir.getAbsolutePath());
        }

        // S2 — the master key comes from AndroidKeyStore. A passphrase is only a fallback for
        // environments without a keystore (JVM unit tests).
        KeystoreKeyProvider keyProvider =
                new KeystoreKeyProvider(this.config.getEncryptionKeyAlias());
        SecretKey masterKey = keyProvider.getOrCreateKey();
        String passphrase = this.config.getEncryptionPassword();

        if (masterKey == null) {
            if (passphrase == null || passphrase.isEmpty()) {
                this.runtimeState = RuntimeState.ERROR;
                return NativeStorageEngineResult.error(500,
                        "MISSING_KEY: AndroidKeyStore is unavailable and no encryptionPassword "
                                + "was configured");
            }
            logWarn("AndroidKeyStore unavailable; falling back to the configured passphrase.");
        }

        this.storageService = new NativeStorageService(rootDir, masterKey, passphrase);
        this.storageService.onInitialize();
        this.storageService.onStart();

        this.commandHandler = new StorageCommandHandler(this.storageService);
        this.encryptedStore = new EncryptedStore(this.storageService.getPreferences("secure_store"));
        this.keyValueStore = new KeyValueStore("kv_store", new File(rootDir, "kv"), masterKey);
        this.diskCache = new LruDiskCache(new File(rootDir, "cache"), 20 * 1024 * 1024);
        this.databaseHelper = new DatabaseHelper(new File(rootDir, "databases"));

        try {
            ServiceRegistry.getInstance().registerService(NativeStorageService.class, this.storageService);
        } catch (Throwable ignored) {}

        this.initialized.set(true);
        this.runtimeState = RuntimeState.READY;

        return NativeStorageEngineResult.success(true);
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public RuntimeState getRuntimeState() {
        return runtimeState;
    }

    public NativeStorageEngineConfig getConfig() {
        return config;
    }

    public NativeStorageService getService() {
        checkInitialized();
        return storageService;
    }

    public StorageCommandHandler getCommandHandler() {
        checkInitialized();
        return commandHandler;
    }

    public EncryptedPreferences getPreferences(String name) {
        checkInitialized();
        return storageService.getPreferences(name);
    }

    public StorageDatabase getDatabase() {
        checkInitialized();
        return storageService.getDatabase();
    }

    public EncryptedStore getEncryptedStore() {
        checkInitialized();
        return encryptedStore;
    }

    public KeyValueStore getKeyValueStore() {
        checkInitialized();
        return keyValueStore;
    }

    public LruDiskCache getDiskCache() {
        checkInitialized();
        return diskCache;
    }

    public DatabaseHelper getDatabaseHelper() {
        checkInitialized();
        return databaseHelper;
    }

    /**
     * Releases every sub-object and returns the engine to {@link RuntimeState#UNINITIALIZED}.
     *
     * <p>Idempotent. The singleton reference is retained so callers holding it keep observing
     * the same engine; {@link #init(Context, NativeStorageEngineConfig)} rebuilds every
     * sub-object.</p>
     */
    public synchronized void release() {
        if (!initialized.getAndSet(false)) {
            return;
        }

        try {
            if (databaseHelper != null) {
                databaseHelper.checkpoint();
            }
        } catch (Throwable t) {
            logWarn("Database checkpoint failed during release: " + t.getMessage());
        }

        try {
            if (storageService != null) {
                storageService.onStop();
                storageService.onDispose();
            }
        } catch (Throwable t) {
            logWarn("Storage service shutdown failed: " + t.getMessage());
        }

        this.storageService = null;
        this.commandHandler = null;
        this.encryptedStore = null;
        this.keyValueStore = null;
        this.diskCache = null;
        this.databaseHelper = null;
        this.runtimeState = RuntimeState.UNINITIALIZED;
    }

    private void checkInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException(
                    "NativeStorageEngine is not initialized. Call StorageEngine.init(context, config) first.");
        }
    }

    private static void logWarn(String message) {
        try {
            NativeLogger.getInstance().w(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
