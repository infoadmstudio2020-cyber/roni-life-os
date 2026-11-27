package com.roni.library.storage;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

/**
 * S3 — KeyValueStore encryption and one-time plaintext migration.
 */
public class KeyValueStoreEncryptionTest {

    private File dir;
    private SecretKey key;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("kv_enc_test").toFile();
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1);
        }
        key = new SecretKeySpec(raw, "AES");
    }

    @Test
    public void roundTripWithSecretKey() {
        KeyValueStore store = new KeyValueStore("secure", dir, key);
        assertTrue(store.isEncrypted());

        store.putString("token", "abc123");
        store.putInt("count", 7);
        store.putBoolean("flag", true);

        assertEquals("abc123", store.getString("token", null));
        assertEquals(7, store.getInt("count", 0));
        assertTrue(store.getBoolean("flag", false));
    }

    @Test
    public void ciphertextOnDiskIsNotPlaintext() throws Exception {
        KeyValueStore store = new KeyValueStore("secure", dir, key);
        store.putString("token", "super-secret-value");

        File file = new File(dir, "secure.kv");
        assertTrue(file.exists());

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        }
        String stored = props.getProperty("token");
        assertNotNull(stored);
        assertNotEquals("super-secret-value", stored);
        assertFalse(stored.contains("super-secret-value"));
        assertEquals("2", props.getProperty("__format__"));
    }

    @Test
    public void plaintextConstructorStillReadsLegacyFile() throws Exception {
        File legacy = new File(dir, "legacy.kv");
        Properties props = new Properties();
        props.setProperty("user", "alice");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(legacy)) {
            props.store(fos, "legacy");
        }

        KeyValueStore store = new KeyValueStore("legacy", dir);
        assertFalse(store.isEncrypted());
        assertEquals("alice", store.getString("user", null));
    }

    @Test
    public void migrationReEncryptsOnceAndIsIdempotent() throws Exception {
        File legacy = new File(dir, "migrate.kv");
        Properties props = new Properties();
        props.setProperty("user", "alice");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(legacy)) {
            props.store(fos, "legacy");
        }

        KeyValueStore first = new KeyValueStore("migrate", dir, key);
        assertEquals("alice", first.getString("user", null));

        Properties afterMigration = new Properties();
        try (FileInputStream fis = new FileInputStream(legacy)) {
            afterMigration.load(fis);
        }
        assertEquals("2", afterMigration.getProperty("__format__"));
        assertNotEquals("alice", afterMigration.getProperty("user"));

        // Re-opening must not double-encrypt.
        KeyValueStore second = new KeyValueStore("migrate", dir, key);
        assertEquals("alice", second.getString("user", null));

        String raw = new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.ISO_8859_1);
        assertEquals(1, countOccurrences(raw, "__format__"));
    }

    @Test
    public void getAllDecryptsEveryEntry() {
        KeyValueStore store = new KeyValueStore("secure", dir, key);
        store.putString("a", "1");
        store.putString("b", "2");

        assertEquals("1", store.getAll().get("a"));
        assertEquals("2", store.getAll().get("b"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
