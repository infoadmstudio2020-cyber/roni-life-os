package com.roni.library.storage;

import com.roni.library.storage.preferences.EncryptedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

/**
 * S2 / S6 — key-backed preferences, missing-key failure and non-throwing persistence.
 */
public class EncryptedPreferencesKeyTest {

    private File dir;
    private SecretKey key;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("enc_pref_test").toFile();
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 3);
        }
        key = new SecretKeySpec(raw, "AES");
    }

    @Test
    public void nullPassphraseThrowsMissingKey() {
        try {
            new EncryptedPreferences("p", dir, (String) null);
            fail("expected StorageException");
        } catch (StorageException e) {
            assertEquals("MISSING_KEY", e.getErrorCode());
        }
    }

    @Test
    public void emptyPassphraseThrowsMissingKey() {
        try {
            new EncryptedPreferences("p", dir, "");
            fail("expected StorageException");
        } catch (StorageException e) {
            assertEquals("MISSING_KEY", e.getErrorCode());
        }
    }

    @Test
    public void nullSecretKeyThrowsMissingKey() {
        try {
            new EncryptedPreferences("p", dir, (SecretKey) null);
            fail("expected StorageException");
        } catch (StorageException e) {
            assertEquals("MISSING_KEY", e.getErrorCode());
        }
    }

    @Test
    public void secretKeyConstructorRoundTrips() {
        EncryptedPreferences prefs = new EncryptedPreferences("session", dir, key);
        assertTrue(prefs.isKeyBacked());

        prefs.putString("token", "jwt_abc");
        prefs.putInt("uid", 42);
        prefs.putLong("ts", 1700000000000L);
        prefs.putBoolean("premium", true);

        assertEquals("jwt_abc", prefs.getString("token", null));
        assertEquals(42, prefs.getInt("uid", 0));
        assertEquals(1700000000000L, prefs.getLong("ts", 0L));
        assertTrue(prefs.getBoolean("premium", false));
        assertTrue(prefs.flush());
    }

    @Test
    public void getAllDecryptsEveryEntry() {
        EncryptedPreferences prefs = new EncryptedPreferences("session", dir, key);
        prefs.putString("a", "1");
        prefs.putString("b", "2");

        Map<String, Object> all = prefs.getAll();
        assertEquals("1", all.get("a"));
        assertEquals("2", all.get("b"));
    }

    @Test
    public void corruptedEntryYieldsMarkerWithoutThrowing() throws Exception {
        File file = new File(dir, "broken.encpref");
        Properties props = new Properties();
        props.setProperty("good", "not-valid-ciphertext");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "broken");
        }

        EncryptedPreferences prefs = new EncryptedPreferences("broken", dir, key);
        Map<String, Object> all = prefs.getAll();
        assertEquals("[DECRYPTION_ERROR]", all.get("good"));
        assertNull(prefs.getString("good", null));
    }
}
