package com.roni.library.storage;

import com.roni.library.storage.crypto.KeystoreKeyProvider;

import org.junit.Test;

import javax.crypto.SecretKey;

import static org.junit.Assert.*;

/**
 * S2 — on a plain JVM the AndroidKeyStore is unavailable and the provider reports so.
 */
public class KeystoreKeyProviderTest {

    @Test
    public void keystoreIsUnavailableOnJvm() {
        KeystoreKeyProvider provider = new KeystoreKeyProvider();
        assertFalse(provider.isAvailable());
    }

    @Test
    public void getOrCreateKeyReturnsNullWithoutKeystore() {
        KeystoreKeyProvider provider = new KeystoreKeyProvider();
        assertNull(provider.getOrCreateKey());
    }

    @Test
    public void resultIsStableAcrossCalls() {
        KeystoreKeyProvider provider = new KeystoreKeyProvider("custom_alias");
        SecretKey first = provider.getOrCreateKey();
        SecretKey second = provider.getOrCreateKey();
        assertNull(first);
        assertNull(second);
        assertFalse(provider.isAvailable());
        assertEquals("custom_alias", provider.getAlias());
    }

    @Test
    public void defaultAliasIsStable() {
        assertEquals("native_storage_master_key", new KeystoreKeyProvider().getAlias());
        assertEquals("native_storage_master_key", KeystoreKeyProvider.DEFAULT_ALIAS);
    }
}
