package com.roni.library.storage.crypto;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.roni.library.logger.NativeLogger;

import java.security.Key;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Supplies the AES-256 master key used by the storage engine.
 *
 * <p>On Android the key lives in {@code AndroidKeyStore} and never leaves the TEE/StrongBox.
 * On a plain JVM (unit tests) the keystore is unavailable, {@link #isAvailable()} returns
 * {@code false} and callers fall back to a passphrase-derived key.</p>
 *
 * <p>This class deliberately does not depend on {@code native-security-engine}: that would
 * create an L2↔L2 coupling and a second key-management implementation.</p>
 */
public final class KeystoreKeyProvider {

    private static final String TAG = "NativeStorageEngine";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    /** Default alias of the storage master key. */
    public static final String DEFAULT_ALIAS = "native_storage_master_key";

    private final String alias;
    private volatile SecretKey cachedKey;
    private volatile Boolean available;

    public KeystoreKeyProvider() {
        this(DEFAULT_ALIAS);
    }

    public KeystoreKeyProvider(String alias) {
        this.alias = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : DEFAULT_ALIAS;
    }

    public String getAlias() {
        return alias;
    }

    /**
     * @return true when {@code AndroidKeyStore} can be used on this runtime.
     */
    public boolean isAvailable() {
        Boolean cached = this.available;
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
                ks.load(null);
                result = true;
            }
        } catch (Throwable t) {
            result = false;
        }
        this.available = result;
        return result;
    }

    /**
     * Loads the key for {@link #getAlias()}, generating it on first use.
     *
     * @return the key, or {@code null} when the keystore is unavailable or the operation failed.
     */
    public SecretKey getOrCreateKey() {
        return getOrCreateKey(alias);
    }

    /**
     * Loads or generates an AES-256 GCM key under {@code alias}.
     *
     * @return the key, or {@code null} when the keystore is unavailable or the operation failed.
     */
    public SecretKey getOrCreateKey(String alias) {
        if (!isAvailable()) {
            return null;
        }
        String target = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : this.alias;

        SecretKey cached = this.cachedKey;
        if (cached != null && target.equals(this.alias)) {
            return cached;
        }

        synchronized (this) {
            try {
                KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
                ks.load(null);

                if (ks.containsAlias(target)) {
                    Key existing = ks.getKey(target, null);
                    if (existing instanceof SecretKey) {
                        if (target.equals(this.alias)) {
                            this.cachedKey = (SecretKey) existing;
                        }
                        return (SecretKey) existing;
                    }
                }

                KeyGenerator generator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                generator.init(new KeyGenParameterSpec.Builder(
                        target,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build());

                SecretKey generated = generator.generateKey();
                if (target.equals(this.alias)) {
                    this.cachedKey = generated;
                }
                return generated;
            } catch (Throwable t) {
                logWarn("AndroidKeyStore unavailable for alias '" + target + "': " + t.getMessage());
                return null;
            }
        }
    }

    private static void logWarn(String message) {
        try {
            NativeLogger.getInstance().w(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
