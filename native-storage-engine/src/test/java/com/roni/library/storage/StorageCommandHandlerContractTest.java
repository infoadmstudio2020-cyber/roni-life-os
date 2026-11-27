package com.roni.library.storage;

import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.storage.handler.StorageCommandHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * S5 — the seven canonical {@code storage.*} actions and their exact response shapes.
 */
public class StorageCommandHandlerContractTest {

    private File dir;
    private StorageCommandHandler handler;

    @Before
    public void setUp() throws Exception {
        NativeStorageEngine.getInstance().release();
        dir = Files.createTempDirectory("storage_handler_test").toFile();
        NativeStorageEngine.getInstance().init(null, new NativeStorageEngineConfig.Builder()
                .storageDirectory(dir)
                .encryptionPassword("HandlerTestKey!")
                .build());
        handler = NativeStorageEngine.getInstance().getCommandHandler();
    }

    @After
    public void tearDown() {
        NativeStorageEngine.getInstance().release();
    }

    private NativeApiResponse call(String action, Map<String, Object> params) {
        return handler.handleRequest(new NativeApiRequest(
                "req_" + action, "storage", action, params, System.currentTimeMillis()));
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    @Test
    public void supportedActionsAreTheSevenCanonicalOnes() {
        assertArrayEquals(new String[]{"get", "set", "remove", "clear", "keys", "has", "getAll"},
                StorageCommandHandler.SUPPORTED_ACTIONS);
    }

    @Test
    public void setReturnsSaved() {
        NativeApiResponse response = call("set", params("key", "token", "value", "abc"));
        assertTrue(response.isSuccess());
        assertEquals(Boolean.TRUE, response.getData().get("saved"));
    }

    @Test
    public void getReturnsKeyValueExists() {
        call("set", params("key", "token", "value", "abc"));
        NativeApiResponse response = call("get", params("key", "token"));

        assertTrue(response.isSuccess());
        assertEquals("token", response.getData().get("key"));
        assertEquals("abc", response.getData().get("value"));
        assertEquals(Boolean.TRUE, response.getData().get("exists"));
    }

    @Test
    public void getMissingKeyReportsNotExists() {
        NativeApiResponse response = call("get", params("key", "absent"));
        assertEquals(Boolean.FALSE, response.getData().get("exists"));
        assertNull(response.getData().get("value"));
    }

    @Test
    public void hasReturnsExists() {
        call("set", params("key", "k1", "value", "v1"));
        assertEquals(Boolean.TRUE, call("has", params("key", "k1")).getData().get("exists"));
        assertEquals(Boolean.FALSE, call("has", params("key", "nope")).getData().get("exists"));
    }

    @Test
    public void keysReturnsList() {
        call("set", params("key", "a", "value", "1"));
        call("set", params("key", "b", "value", "2"));

        Object keys = call("keys", params()).getData().get("keys");
        assertTrue(keys instanceof List);
        assertTrue(((List<?>) keys).contains("a"));
        assertTrue(((List<?>) keys).contains("b"));
    }

    @Test
    public void removeReturnsRemoved() {
        call("set", params("key", "a", "value", "1"));
        NativeApiResponse response = call("remove", params("key", "a"));
        assertEquals(Boolean.TRUE, response.getData().get("removed"));
        assertEquals(Boolean.FALSE, call("has", params("key", "a")).getData().get("exists"));
    }

    @Test
    public void clearReturnsCleared() {
        call("set", params("key", "a", "value", "1"));
        NativeApiResponse response = call("clear", params());
        assertEquals(Boolean.TRUE, response.getData().get("cleared"));
        assertEquals(Boolean.FALSE, call("has", params("key", "a")).getData().get("exists"));
    }

    @Test
    public void getAllReturnsFlatMap() {
        call("set", params("key", "a", "value", "1"));
        call("set", params("key", "b", "value", "2"));

        Map<String, Object> data = call("getAll", params()).getData();
        assertEquals("1", data.get("a"));
        assertEquals("2", data.get("b"));
    }

    @Test
    public void allThreeActionFormsResolve() {
        call("storage.set", params("key", "form", "value", "dot"));
        assertEquals("dot", call("/storage/get", params("key", "form")).getData().get("value"));
        assertEquals("dot", call("get", params("key", "form")).getData().get("value"));
    }

    @Test
    public void unknownActionReturnsUnknownAction() {
        NativeApiResponse response = call("storage.explode", params());
        assertFalse(response.isSuccess());
        assertEquals("UNKNOWN_ACTION", response.getError().getCode());
    }

    @Test
    public void noArgConstructorResolvesInitialisedEngine() {
        StorageCommandHandler resolved = new StorageCommandHandler();
        assertNotNull(resolved);
        assertTrue(resolved.handleRequest(new NativeApiRequest(
                "r", "storage", "keys", new HashMap<>(), System.currentTimeMillis())).isSuccess());
    }
}
