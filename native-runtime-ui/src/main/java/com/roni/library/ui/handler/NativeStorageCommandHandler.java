package com.roni.library.ui.handler;

import android.content.Context;
import android.content.SharedPreferences;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.storage.EncryptedStore;
import com.roni.library.storage.KeyValueStore;
import com.roni.library.storage.StorageEngine;
import com.roni.library.storage.StorageScope;
import com.roni.library.ui.host.NativeUIHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Native key-value storage exposed through the {@code storage.*} bridge namespace.
 *
 * <p>This is a thin adapter over {@code native-storage-engine}: the encrypted tier
 * ({@link EncryptedStore}, AES-GCM) is the default, the plaintext tier
 * ({@link KeyValueStore}) is opt-in. Values survive WebView reloads and app restarts.</p>
 *
 * <p>On first use the legacy {@code native_runtime_web_storage} {@link SharedPreferences} file is
 * copied into the engine store once and then cleared. A {@code __migrated_v1} marker prevents a
 * second copy.</p>
 */
public class NativeStorageCommandHandler implements IApiHandler {

    public static final String[] SUPPORTED_ACTIONS = {
            "get", "set", "remove", "clear", "keys", "has", "getAll"
    };

    private static final String LEGACY_PREFS_NAME = "native_runtime_web_storage";
    private static final String MIGRATION_MARKER = "__migrated_v1";

    private final NativeUIHost uiHost;
    private final boolean encrypted;
    private volatile boolean migrationAttempted;

    public NativeStorageCommandHandler(NativeUIHost uiHost) {
        this(uiHost, true);
    }

    public NativeStorageCommandHandler(NativeUIHost uiHost, boolean encrypted) {
        this.uiHost = uiHost != null ? uiHost : new NativeUIHost();
        this.encrypted = encrypted;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    @Override
    public String getPath() {
        return "storage";
    }

    @Override
    public String getAction() {
        return "*";
    }

    @Override
    public NativeApiResponse handleRequest(NativeApiRequest request) {
        if (request == null) {
            return NativeApiResponse.error("unknown",
                    new NativeApiError("INVALID_REQUEST", "Request is null"));
        }

        String reqId = request.getRequestId();
        String action = normalizeAction(request.getAction());

        if (!StorageEngine.isInitialized()) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("STORAGE_UNAVAILABLE", "StorageEngine is not initialized"));
        }

        Store store = openStore();
        if (store == null) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("STORAGE_UNAVAILABLE", "StorageEngine is not initialized"));
        }
        migrateLegacyOnce(store);

        try {
            String projectId = projectId();
            switch (action) {
                case "get": {
                    String key = request.getStringParam("key", "");
                    String raw = store.get(StorageScope.scopedKey(projectId, key));
                    Map<String, Object> data = new HashMap<>();
                    data.put("key", key);
                    data.put("value", decode(raw));
                    data.put("exists", raw != null);
                    return NativeApiResponse.success(reqId, data);
                }
                case "set": {
                    String key = request.getStringParam("key", "");
                    if (StorageScope.isReserved(projectId, key)) {
                        return NativeApiResponse.error(reqId, new NativeApiError("RESERVED_KEY",
                                "Keys starting with __p: are reserved for project scoping"));
                    }
                    Object value = request.getParameters().get("value");
                    store.put(StorageScope.scopedKey(projectId, key), encode(value));
                    return success(reqId, "saved", true);
                }
                case "remove": {
                    store.remove(StorageScope.scopedKey(projectId,
                            request.getStringParam("key", "")));
                    return success(reqId, "removed", true);
                }
                case "clear": {
                    // Only this project's keys are removed; other projects keep their data.
                    List<String> doomed = new ArrayList<>();
                    for (String stored : store.getAll().keySet()) {
                        if (StorageScope.belongsTo(projectId, stored)) {
                            doomed.add(stored);
                        }
                    }
                    for (String stored : doomed) {
                        store.remove(stored);
                    }
                    return success(reqId, "cleared", doomed.size());
                }
                case "keys": {
                    List<String> keys = new ArrayList<>();
                    for (String stored : store.getAll().keySet()) {
                        String plain = StorageScope.unscopedKey(projectId, stored);
                        if (plain != null && !MIGRATION_MARKER.equals(plain)) {
                            keys.add(plain);
                        }
                    }
                    return NativeApiResponse.success(reqId, wrap("keys", keys));
                }
                case "has": {
                    return success(reqId, "exists", store.contains(
                            StorageScope.scopedKey(projectId,
                                    request.getStringParam("key", ""))));
                }
                case "getAll": {
                    Map<String, Object> all = new HashMap<>();
                    for (Map.Entry<String, String> entry : store.getAll().entrySet()) {
                        String plain = StorageScope.unscopedKey(projectId, entry.getKey());
                        if (plain != null && !MIGRATION_MARKER.equals(plain)) {
                            all.put(plain, decode(entry.getValue()));
                        }
                    }
                    return NativeApiResponse.success(reqId, all);
                }
                default:
                    return NativeApiResponse.error(reqId,
                            new NativeApiError("UNKNOWN_ACTION", "Action not supported: " + action));
            }
        } catch (Throwable t) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("INTERNAL_ERROR", t.getMessage()));
        }
    }

    private Store openStore() {
        try {
            if (encrypted) {
                EncryptedStore store = StorageEngine.getEncryptedStore();
                return store != null ? new EncryptedStoreAdapter(store) : null;
            }
            KeyValueStore store = StorageEngine.getKeyValueStore();
            return store != null ? new KeyValueStoreAdapter(store) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Copies the legacy plaintext preferences into the engine store exactly once.
     *
     * <p>The legacy file predates project scoping, so it has no project information. The first
     * project that runs after the upgrade claims it; a global marker
     * ({@link StorageScope#LEGACY_CLAIM_KEY}) stops every later project from claiming it again.
     * The copied keys land inside the claiming project's namespace.</p>
     */
    private void migrateLegacyOnce(Store store) {
        if (migrationAttempted) {
            return;
        }
        migrationAttempted = true;
        String projectId = projectId();
        String marker = StorageScope.scopedKey(projectId, MIGRATION_MARKER);
        if (store.contains(marker)) {
            return;
        }
        if (store.contains(StorageScope.LEGACY_CLAIM_KEY)) {
            store.put(marker, "1");
            return;
        }
        Context context = resolveContext();
        if (context == null) {
            return;
        }
        try {
            SharedPreferences legacy =
                    context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            Map<String, ?> entries = legacy.getAll();
            int copied = 0;
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                if (MIGRATION_MARKER.equals(entry.getKey())) {
                    continue;
                }
                store.put(StorageScope.scopedKey(projectId, entry.getKey()),
                        encode(entry.getValue()));
                copied++;
            }
            store.put(StorageScope.LEGACY_CLAIM_KEY, "1");
            store.put(marker, "1");
            legacy.edit().clear().apply();
            if (copied > 0) {
                android.util.Log.i("NativeStorageCommandHandler",
                        "Migrated " + copied + " legacy storage entries into project scope");
            }
        } catch (Throwable ignored) {
            // Migration is best effort; the marker is only written on success.
        }
    }

    /** @return the bound project id, or {@code null} for the legacy shared namespace */
    private String projectId() {
        return uiHost != null ? uiHost.getProjectId() : null;
    }

    private Context resolveContext() {
        Context context = uiHost.getActivity();
        if (context == null && uiHost.getUIComponentManager() != null) {
            context = uiHost.getUIComponentManager().getActivity();
        }
        return context;
    }

    private static String encode(Object value) {
        if (value == null) {
            return "s:";
        }
        if (value instanceof Boolean) {
            return "b:" + value;
        }
        if (value instanceof Integer) {
            return "i:" + value;
        }
        if (value instanceof Long) {
            return "l:" + value;
        }
        if (value instanceof Float) {
            return "f:" + value;
        }
        if (value instanceof Double) {
            return "d:" + value;
        }
        return "s:" + value;
    }

    private static Object decode(String raw) {
        if (raw == null || raw.length() < 2) {
            return raw;
        }
        String body = raw.substring(2);
        try {
            switch (raw.charAt(0)) {
                case 'b':
                    return Boolean.parseBoolean(body);
                case 'i':
                    return Integer.parseInt(body);
                case 'l':
                    return Long.parseLong(body);
                case 'f':
                    return Float.parseFloat(body);
                case 'd':
                    return Double.parseDouble(body);
                default:
                    return body;
            }
        } catch (NumberFormatException e) {
            return body;
        }
    }

    private String normalizeAction(String action) {
        if (action == null) return "";
        String value = action.trim();
        if (value.startsWith("storage.")) return value.substring(8);
        if (value.startsWith("/storage/")) return value.substring(9);
        return value;
    }

    private NativeApiResponse success(String reqId, String key, Object value) {
        return NativeApiResponse.success(reqId, wrap(key, value));
    }

    private Map<String, Object> wrap(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }

    private interface Store {
        String get(String key);
        void put(String key, String value);
        boolean contains(String key);
        boolean remove(String key);
        void clear();
        Map<String, String> getAll();
    }

    private static final class EncryptedStoreAdapter implements Store {
        private final EncryptedStore delegate;

        EncryptedStoreAdapter(EncryptedStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public String get(String key) {
            Object value = delegate.get(key, null);
            return value != null ? String.valueOf(value) : null;
        }

        @Override
        public void put(String key, String value) {
            delegate.put(key, value);
        }

        @Override
        public boolean contains(String key) {
            return delegate.contains(key);
        }

        @Override
        public boolean remove(String key) {
            return delegate.remove(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Map<String, String> getAll() {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : delegate.getAll().entrySet()) {
                result.put(entry.getKey(),
                        entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
            }
            return result;
        }
    }

    private static final class KeyValueStoreAdapter implements Store {
        private final KeyValueStore delegate;

        KeyValueStoreAdapter(KeyValueStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public String get(String key) {
            return delegate.getString(key, null);
        }

        @Override
        public void put(String key, String value) {
            delegate.putString(key, value);
        }

        @Override
        public boolean contains(String key) {
            return delegate.contains(key);
        }

        @Override
        public boolean remove(String key) {
            return delegate.remove(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Map<String, String> getAll() {
            return delegate.getAll();
        }
    }
}
