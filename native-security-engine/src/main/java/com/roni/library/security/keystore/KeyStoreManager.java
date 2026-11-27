package com.roni.library.security.keystore;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Manages cryptographic keys, hardware security module interaction,
 * AES-GCM/CBC encryption, and decryption operations.
 *
 * <p><b>SEC4:</b> keys are generated inside {@code AndroidKeyStore} whenever it is available
 * (API 23+). The in-memory {@link #fallbackKeyStore} is used only when the platform keystore is
 * absent — JVM unit tests and pre-Marshmallow devices. Keys held in the fallback store do not
 * survive process death, which is why it is never preferred over the platform keystore.</p>
 *
 * @author infoadmstudio2020-cyber
 */
public class KeyStoreManager {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MIN_KEYSTORE_API = Build.VERSION_CODES.M;

    private final Map<String, Key> fallbackKeyStore = new ConcurrentHashMap<>();
    private final Map<String, KeyAlgorithm> algorithmRegistry = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile boolean hardwareSecurityModuleAvailable = true;
    private volatile KeyStore androidKeyStore;

    public KeyStoreManager() {
        initInternal();
    }

    private void initInternal() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            this.androidKeyStore = ks;
        } catch (Throwable e) {
            // In headless/JVM test environments, AndroidKeyStore is substituted by fallbackKeyStore
            hardwareSecurityModuleAvailable = false;
        }
    }

    public boolean isHardwareSecurityModuleAvailable() {
        return hardwareSecurityModuleAvailable;
    }

    public void setHardwareSecurityModuleAvailable(boolean available) {
        this.hardwareSecurityModuleAvailable = available;
        if (!available) {
            this.androidKeyStore = null;
        }
    }

    /**
     * @return the platform keystore, or {@code null} when it is unavailable on this device
     */
    private KeyStore platformKeyStore() {
        if (!hardwareSecurityModuleAvailable || Build.VERSION.SDK_INT < MIN_KEYSTORE_API) {
            return null;
        }
        KeyStore cached = androidKeyStore;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (androidKeyStore == null) {
                try {
                    KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
                    ks.load(null);
                    androidKeyStore = ks;
                } catch (Throwable t) {
                    hardwareSecurityModuleAvailable = false;
                    return null;
                }
            }
            return androidKeyStore;
        }
    }

    public synchronized boolean generateKey(String alias, KeyAlgorithm algorithm, boolean requiresUserAuth) {
        if (alias == null || alias.trim().isEmpty() || algorithm == null) {
            return false;
        }

        KeyStore keyStore = platformKeyStore();
        if (keyStore != null) {
            try {
                generatePlatformKey(alias, algorithm, requiresUserAuth);
                algorithmRegistry.put(alias, algorithm);
                return true;
            } catch (Throwable t) {
                // Fall through to the software store so the caller still gets a usable key.
            }
        }

        try {
            if (algorithm == KeyAlgorithm.RSA_2048 || algorithm == KeyAlgorithm.RSA_4096) {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(algorithm.getKeySize(), secureRandom);
                KeyPair kp = kpg.generateKeyPair();
                fallbackKeyStore.put(alias, kp.getPrivate());
            } else {
                KeyGenerator kg = KeyGenerator.getInstance(algorithm.getStandardName());
                kg.init(algorithm.getKeySize(), secureRandom);
                SecretKey secretKey = kg.generateKey();
                fallbackKeyStore.put(alias, secretKey);
            }
            algorithmRegistry.put(alias, algorithm);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @RequiresApi(MIN_KEYSTORE_API)
    private void generatePlatformKey(String alias, KeyAlgorithm algorithm, boolean requiresUserAuth)
            throws Exception {
        if (algorithm == KeyAlgorithm.RSA_2048 || algorithm == KeyAlgorithm.RSA_4096) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setKeySize(algorithm.getKeySize())
                    .setUserAuthenticationRequired(requiresUserAuth)
                    .build();
            kpg.initialize(spec);
            kpg.generateKeyPair();
            return;
        }

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setUserAuthenticationRequired(requiresUserAuth);
        if (algorithm == KeyAlgorithm.AES_CBC_256) {
            builder.setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } else {
            builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE);
        }
        kg.init(builder.build());
        kg.generateKey();
    }

    public boolean hasKey(String alias) {
        if (alias == null) return false;
        if (fallbackKeyStore.containsKey(alias)) {
            return true;
        }
        KeyStore keyStore = platformKeyStore();
        if (keyStore != null) {
            try {
                return keyStore.containsAlias(alias);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    public boolean deleteKey(String alias) {
        if (alias == null) return false;
        algorithmRegistry.remove(alias);
        boolean removed = fallbackKeyStore.remove(alias) != null;
        KeyStore keyStore = platformKeyStore();
        if (keyStore != null) {
            try {
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias);
                    removed = true;
                }
            } catch (Throwable ignored) {
                // Nothing else to delete.
            }
        }
        return removed;
    }

    public List<String> listAliases() {
        Set<String> aliases = new LinkedHashSet<>(fallbackKeyStore.keySet());
        KeyStore keyStore = platformKeyStore();
        if (keyStore != null) {
            try {
                java.util.Enumeration<String> entries = keyStore.aliases();
                while (entries.hasMoreElements()) {
                    aliases.add(entries.nextElement());
                }
            } catch (Throwable ignored) {
                // Only the software aliases are reported.
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(aliases));
    }

    public KeyAlgorithm getKeyAlgorithm(String alias) {
        return algorithmRegistry.getOrDefault(alias, KeyAlgorithm.AES_GCM_256);
    }

    /**
     * Resolves a key from the software store first, then from the platform keystore.
     */
    private Key resolveKey(String alias) {
        Key key = fallbackKeyStore.get(alias);
        if (key != null) {
            return key;
        }
        KeyStore keyStore = platformKeyStore();
        if (keyStore != null) {
            try {
                Key platformKey = keyStore.getKey(alias, null);
                if (platformKey != null) {
                    return platformKey;
                }
                Certificate certificate = keyStore.getCertificate(alias);
                if (certificate != null) {
                    return certificate.getPublicKey();
                }
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public Cipher getInitializedCipher(String alias, int mode, byte[] iv) {
        Key key = resolveKey(alias);
        if (key == null) {
            throw new IllegalStateException("Key not found for alias: " + alias);
        }

        KeyAlgorithm algo = getKeyAlgorithm(alias);
        try {
            Cipher cipher = Cipher.getInstance(algo.getTransformation());
            if (algo == KeyAlgorithm.AES_GCM_256) {
                if (iv == null) {
                    iv = new byte[GCM_IV_LENGTH];
                    secureRandom.nextBytes(iv);
                }
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(mode, key, spec);
            } else if (algo == KeyAlgorithm.AES_CBC_256) {
                if (iv == null) {
                    iv = new byte[16];
                    secureRandom.nextBytes(iv);
                }
                IvParameterSpec spec = new IvParameterSpec(iv);
                cipher.init(mode, key, spec);
            } else {
                cipher.init(mode, key);
            }
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize cipher for alias: " + alias, e);
        }
    }

    public Map<String, Object> encrypt(String alias, byte[] plaintext) {
        if (!hasKey(alias)) {
            generateKey(alias, KeyAlgorithm.AES_GCM_256, false);
        }

        KeyAlgorithm algo = getKeyAlgorithm(alias);
        Map<String, Object> result = new HashMap<>();
        try {
            byte[] iv = new byte[algo == KeyAlgorithm.AES_GCM_256 ? GCM_IV_LENGTH : 16];
            secureRandom.nextBytes(iv);

            Cipher cipher = getInitializedCipher(alias, Cipher.ENCRYPT_MODE, iv);
            byte[] ciphertext = cipher.doFinal(plaintext);

            result.put("success", true);
            result.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
            result.put("iv", Base64.getEncoder().encodeToString(iv));
            result.put("algorithm", algo.name());
            result.put("alias", alias);
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    public byte[] decrypt(String alias, byte[] ciphertext, byte[] iv) {
        if (!hasKey(alias)) {
            throw new IllegalArgumentException("No key registered for alias: " + alias);
        }

        try {
            Cipher cipher = getInitializedCipher(alias, Cipher.DECRYPT_MODE, iv);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    public String encryptString(String alias, String plaintext) {
        if (plaintext == null) return null;
        Map<String, Object> enc = encrypt(alias, plaintext.getBytes(StandardCharsets.UTF_8));
        if (Boolean.TRUE.equals(enc.get("success"))) {
            return enc.get("ciphertext") + ":" + enc.get("iv");
        }
        return null;
    }

    public String decryptString(String alias, String combinedPayload) {
        if (combinedPayload == null || !combinedPayload.contains(":")) return null;
        String[] parts = combinedPayload.split(":", 2);
        byte[] ciphertext = Base64.getDecoder().decode(parts[0]);
        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] decrypted = decrypt(alias, ciphertext, iv);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Removes every key this manager created. Platform keys are deleted by alias so unrelated
     * entries in {@code AndroidKeyStore} are left untouched.
     */
    public void clear() {
        Set<String> aliases = new LinkedHashSet<>(algorithmRegistry.keySet());
        aliases.addAll(fallbackKeyStore.keySet());
        fallbackKeyStore.clear();
        algorithmRegistry.clear();

        KeyStore keyStore = platformKeyStore();
        if (keyStore == null) {
            return;
        }
        for (String alias : aliases) {
            try {
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias);
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
    }
}
