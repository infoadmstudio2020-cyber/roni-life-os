
package com.roni.library.storage.crypto;

import android.util.Base64;

import com.roni.library.storage.StorageException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
        // Utility class
    }

    public static byte[] generateSalt(int length) {
        if (length <= 0) {
            length = SALT_LENGTH;
        }

        byte[] salt = new byte[length];
        SECURE_RANDOM.nextBytes(salt);

        return salt;
    }

    public static SecretKey deriveKey(char[] passphrase, byte[] salt) {
        if (passphrase == null || passphrase.length == 0) {
            throw new StorageException(
                    "CRYPTO_INVALID_KEY",
                    "Passphrase cannot be null or empty"
            );
        }

        if (salt == null || salt.length == 0) {
            throw new StorageException(
                    "CRYPTO_INVALID_SALT",
                    "Salt cannot be null or empty"
            );
        }

        PBEKeySpec spec = null;

        try {
            spec = new PBEKeySpec(
                    passphrase,
                    salt,
                    ITERATION_COUNT,
                    KEY_LENGTH
            );

            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] keyBytes = factory.generateSecret(spec).getEncoded();

            return new SecretKeySpec(keyBytes, ALGORITHM);

        } catch (Exception e) {
            throw new StorageException(
                    "KEY_DERIVATION_FAILED",
                    "Failed to derive AES key: " + e.getMessage(),
                    e
            );

        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
        }
    }

    /**
     * Password-based AES-GCM encryption.
     *
     * Payload:
     * [saltLength][salt][ivLength][iv][cipherText]
     */
    public static String encrypt(String plaintext, String password) {

        if (plaintext == null) {
            return null;
        }

        if (password == null || password.isEmpty()) {
            throw new StorageException(
                    "INVALID_PASSWORD",
                    "Password cannot be empty"
            );
        }

        try {
            byte[] salt = generateSalt(SALT_LENGTH);

            SecretKey secretKey = deriveKey(
                    password.toCharArray(),
                    salt
            );

            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            byte[] cipherText = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer byteBuffer = ByteBuffer.allocate(
                    1 + salt.length + 1 + iv.length + cipherText.length
            );

            byteBuffer.put((byte) salt.length);
            byteBuffer.put(salt);

            byteBuffer.put((byte) iv.length);
            byteBuffer.put(iv);

            byteBuffer.put(cipherText);

            return Base64.encodeToString(
                    byteBuffer.array(),
                    Base64.NO_WRAP
            );

        } catch (StorageException se) {
            throw se;

        } catch (Exception e) {
            throw new StorageException(
                    "ENCRYPTION_FAILED",
                    "Failed to encrypt payload: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Password-based AES-GCM decryption.
     */
    public static String decrypt(
            String base64Payload,
            String password
    ) {

        if (base64Payload == null) {
            return null;
        }

        if (password == null || password.isEmpty()) {
            throw new StorageException(
                    "INVALID_PASSWORD",
                    "Password cannot be empty"
            );
        }

        try {
            byte[] decoded = Base64.decode(
                    base64Payload,
                    Base64.NO_WRAP
            );

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            if (byteBuffer.remaining() < 2) {
                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Malformed encrypted payload"
                );
            }

            int saltLength = byteBuffer.get() & 0xFF;

            if (saltLength != SALT_LENGTH ||
                    saltLength > byteBuffer.remaining()) {

                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Invalid salt length"
                );
            }

            byte[] salt = new byte[saltLength];
            byteBuffer.get(salt);

            if (byteBuffer.remaining() < 1) {
                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Missing IV length"
                );
            }

            int ivLength = byteBuffer.get() & 0xFF;

            if (ivLength != IV_LENGTH ||
                    ivLength > byteBuffer.remaining()) {

                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Invalid IV length"
                );
            }

            byte[] iv = new byte[ivLength];
            byteBuffer.get(iv);

            if (byteBuffer.remaining() <= 0) {
                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Missing ciphertext"
                );
            }

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            SecretKey secretKey = deriveKey(
                    password.toCharArray(),
                    salt
            );

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            byte[] plainTextBytes = cipher.doFinal(cipherText);

            return new String(
                    plainTextBytes,
                    StandardCharsets.UTF_8
            );

        } catch (StorageException se) {
            throw se;

        } catch (Exception e) {
            throw new StorageException(
                    "DECRYPTION_FAILED",
                    "Failed to decrypt payload: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Encrypts with a caller-provided key,
     * typically generated by AndroidKeyStore.
     *
     * Payload:
     * [ivLength][iv][cipherText]
     *
     * AndroidKeyStore generates the IV automatically.
     */
    public static String encrypt(
            String plaintext,
            SecretKey key
    ) {

        if (plaintext == null) {
            return null;
        }

        if (key == null) {
            throw new StorageException(
                    "MISSING_KEY",
                    "SecretKey cannot be null"
            );
        }

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

            // Allow AndroidKeyStore to generate the IV.
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] iv = cipher.getIV();

            if (iv == null || iv.length != IV_LENGTH) {
                throw new StorageException(
                        "ENCRYPTION_FAILED",
                        "Generated GCM IV is invalid"
                );
            }

            byte[] cipherText = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer byteBuffer = ByteBuffer.allocate(
                    1 + iv.length + cipherText.length
            );

            byteBuffer.put((byte) iv.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.encodeToString(
                    byteBuffer.array(),
                    Base64.NO_WRAP
            );

        } catch (StorageException se) {
            throw se;

        } catch (Exception e) {
            throw new StorageException(
                    "ENCRYPTION_FAILED",
                    "Failed to encrypt payload: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Decrypts a payload produced by encrypt(String, SecretKey).
     */
    public static String decrypt(
            String base64Payload,
            SecretKey key
    ) {

        if (base64Payload == null) {
            return null;
        }

        if (key == null) {
            throw new StorageException(
                    "MISSING_KEY",
                    "SecretKey cannot be null"
            );
        }

        try {
            byte[] decoded = Base64.decode(
                    base64Payload,
                    Base64.NO_WRAP
            );

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            if (byteBuffer.remaining() < 1) {
                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Malformed encrypted payload"
                );
            }

            int ivLength = byteBuffer.get() & 0xFF;

            if (ivLength != IV_LENGTH ||
                    ivLength > byteBuffer.remaining()) {

                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Invalid IV length"
                );
            }

            byte[] iv = new byte[ivLength];
            byteBuffer.get(iv);

            if (byteBuffer.remaining() <= 0) {
                throw new StorageException(
                        "DECRYPTION_FAILED",
                        "Missing ciphertext"
                );
            }

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    parameterSpec
            );

            byte[] plainTextBytes = cipher.doFinal(cipherText);

            return new String(
                    plainTextBytes,
                    StandardCharsets.UTF_8
            );

        } catch (StorageException se) {
            throw se;

        } catch (Exception e) {
            throw new StorageException(
                    "DECRYPTION_FAILED",
                    "Failed to decrypt payload: " + e.getMessage(),
                    e
            );
        }
    }
}
