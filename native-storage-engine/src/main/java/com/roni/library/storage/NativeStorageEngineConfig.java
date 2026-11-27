package com.roni.library.storage;

import com.roni.library.storage.crypto.KeystoreKeyProvider;

import java.io.File;

/**
 * Configuration for {@link NativeStorageEngine}.
 *
 * <p>There is no default encryption passphrase. On Android the master key comes from
 * {@code AndroidKeyStore} under {@link #getEncryptionKeyAlias()}. A passphrase is only used
 * when the keystore is unavailable (JVM tests) and must be supplied explicitly.</p>
 */
public class NativeStorageEngineConfig {
    private final boolean debugEnabled;
    private final String tag;
    private final int timeout;
    private final File storageDirectory;
    private final String encryptionPassword;
    private final String encryptionKeyAlias;

    private NativeStorageEngineConfig(Builder builder) {
        this.debugEnabled = builder.debugEnabled;
        this.tag = builder.tag != null ? builder.tag : "NativeStorageEngine";
        this.timeout = builder.timeout > 0 ? builder.timeout : 30000;
        this.storageDirectory = builder.storageDirectory;
        this.encryptionPassword = builder.encryptionPassword;
        this.encryptionKeyAlias = (builder.encryptionKeyAlias != null
                && !builder.encryptionKeyAlias.trim().isEmpty())
                ? builder.encryptionKeyAlias.trim()
                : KeystoreKeyProvider.DEFAULT_ALIAS;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public String getTag() {
        return tag;
    }

    public int getTimeout() {
        return timeout;
    }

    public File getStorageDirectory() {
        return storageDirectory;
    }

    /**
     * @return the explicitly configured passphrase, or {@code null} when none was supplied.
     */
    public String getEncryptionPassword() {
        return encryptionPassword;
    }

    /**
     * @return the {@code AndroidKeyStore} alias of the storage master key.
     */
    public String getEncryptionKeyAlias() {
        return encryptionKeyAlias;
    }

    public static class Builder {
        private boolean debugEnabled = false;
        private String tag = "NativeStorageEngine";
        private int timeout = 30000;
        private File storageDirectory = null;
        private String encryptionPassword = null;
        private String encryptionKeyAlias = KeystoreKeyProvider.DEFAULT_ALIAS;

        public Builder debugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
            return this;
        }

        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder storageDirectory(File storageDirectory) {
            this.storageDirectory = storageDirectory;
            return this;
        }

        /**
         * Fallback passphrase used only when {@code AndroidKeyStore} is unavailable.
         */
        public Builder encryptionPassword(String encryptionPassword) {
            this.encryptionPassword = encryptionPassword;
            return this;
        }

        /**
         * Alias of the AES-256 master key in {@code AndroidKeyStore}.
         */
        public Builder encryptionKeyAlias(String alias) {
            this.encryptionKeyAlias = alias;
            return this;
        }

        public NativeStorageEngineConfig build() {
            return new NativeStorageEngineConfig(this);
        }
    }
}
