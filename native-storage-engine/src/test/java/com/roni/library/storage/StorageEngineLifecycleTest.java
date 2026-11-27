package com.roni.library.storage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * S1 / S4 — fail-fast accessors and a re-initialisable, idempotent release.
 */
public class StorageEngineLifecycleTest {

    private File dirA;
    private File dirB;

    @Before
    public void setUp() throws Exception {
        NativeStorageEngine.getInstance().release();
        dirA = Files.createTempDirectory("storage_a").toFile();
        dirB = Files.createTempDirectory("storage_b").toFile();
    }

    @After
    public void tearDown() {
        NativeStorageEngine.getInstance().release();
    }

    private static NativeStorageEngineConfig config(File dir) {
        return new NativeStorageEngineConfig.Builder()
                .storageDirectory(dir)
                .encryptionPassword("LifecycleTestKey!")
                .build();
    }

    @Test
    public void accessorBeforeInitThrows() {
        NativeStorageEngine engine = NativeStorageEngine.getInstance();
        assertFalse(engine.isInitialized());
        try {
            engine.getKeyValueStore();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not initialized"));
        }
    }

    @Test
    public void everyAccessorFailsFast() {
        NativeStorageEngine engine = NativeStorageEngine.getInstance();
        assertThrows(IllegalStateException.class, () -> engine.getPreferences("x"));
        assertThrows(IllegalStateException.class, engine::getDatabase);
        assertThrows(IllegalStateException.class, engine::getEncryptedStore);
        assertThrows(IllegalStateException.class, engine::getDiskCache);
        assertThrows(IllegalStateException.class, engine::getDatabaseHelper);
        assertThrows(IllegalStateException.class, engine::getService);
        assertThrows(IllegalStateException.class, engine::getCommandHandler);
    }

    @Test
    public void initReleaseInitWorks() {
        NativeStorageEngine engine = NativeStorageEngine.getInstance();

        assertTrue(engine.init(null, config(dirA)).isSuccess());
        assertTrue(engine.isInitialized());
        engine.getKeyValueStore().putString("k", "v1");

        engine.release();
        assertFalse(engine.isInitialized());

        assertTrue(engine.init(null, config(dirB)).isSuccess());
        assertTrue(engine.isInitialized());
        // dirB is a fresh root: the value written under dirA must not leak across.
        assertNull(engine.getKeyValueStore().getString("k", null));
        engine.getKeyValueStore().putString("k2", "v2");
        assertTrue(new File(dirB, "kv/kv_store.kv").exists());
    }

    @Test
    public void releaseTwiceIsSafe() {
        NativeStorageEngine engine = NativeStorageEngine.getInstance();
        engine.init(null, config(dirA));
        engine.release();
        engine.release();
        assertFalse(engine.isInitialized());
    }

    @Test
    public void singletonSurvivesRelease() {
        NativeStorageEngine first = NativeStorageEngine.getInstance();
        first.init(null, config(dirA));
        first.release();
        assertSame(first, NativeStorageEngine.getInstance());
    }
}
