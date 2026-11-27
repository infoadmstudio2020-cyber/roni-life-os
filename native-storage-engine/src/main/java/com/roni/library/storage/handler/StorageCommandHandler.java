package com.roni.library.storage.handler;

import com.roni.library.contracts.api.IApiCallback;
import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.storage.NativeStorageEngine;
import com.roni.library.storage.database.Document;
import com.roni.library.storage.database.QueryFilter;
import com.roni.library.storage.database.StorageCollection;
import com.roni.library.storage.preferences.EncryptedPreferences;
import com.roni.library.storage.service.NativeStorageService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical {@code storage.*} command handler.
 *
 * <p>Supports the seven actions exposed to the web bridge — {@code get}, {@code set},
 * {@code remove}, {@code clear}, {@code keys}, {@code has}, {@code getAll} — in all three
 * action forms the bridge produces ({@code storage.get}, {@code /storage/get}, {@code get}).
 * The legacy {@code pref.*} and {@code db.*} actions are retained for backward compatibility.</p>
 */
public class StorageCommandHandler implements IApiHandler {

    /** Canonical actions, in the order they are documented. */
    public static final String[] SUPPORTED_ACTIONS = {
            "get", "set", "remove", "clear", "keys", "has", "getAll"
    };

    private static final String DEFAULT_PREF_NAME = "default";

    private final NativeStorageService storageService;

    /**
     * Resolves the service from the initialised engine.
     *
     * @throws IllegalStateException when the engine has not been initialised.
     */
    public StorageCommandHandler() {
        this(NativeStorageEngine.getInstance().getService());
    }

    public StorageCommandHandler(NativeStorageService storageService) {
        this.storageService = storageService;
    }

    public String getRoutePrefix() {
        return "storage";
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
            return NativeApiResponse.error("req_null", 400,
                    new NativeApiError("NULL_REQUEST", "Request is null"));
        }
        if (storageService == null) {
            return NativeApiResponse.error(request.getRequestId(), 503,
                    new NativeApiError("STORAGE_NOT_INITIALIZED",
                            "NativeStorageEngine is not initialized"));
        }

        String rawAction = request.getAction() != null ? request.getAction() : request.getPath();
        String action = normalize(rawAction);

        Map<String, Object> params = request.getParameters();
        if (params == null) {
            params = new HashMap<>();
        }

        try {
            String prefName = asString(params.get("prefName"), DEFAULT_PREF_NAME);
            String key = asString(params.get("key"), null);

            switch (action) {
                case "get": {
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    boolean exists = pref.contains(key);
                    String value = pref.getString(key, asString(params.get("defaultValue"), null));
                    Map<String, Object> data = new HashMap<>();
                    data.put("key", key);
                    data.put("value", value);
                    data.put("exists", exists);
                    return NativeApiResponse.success(request.getRequestId(), data);
                }
                case "set": {
                    if (key == null) {
                        return NativeApiResponse.error(request.getRequestId(), 400,
                                new NativeApiError("INVALID_ARG", "Key required"));
                    }
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    pref.putString(key, asString(params.get("value"), null));
                    Map<String, Object> data = new HashMap<>();
                    data.put("saved", true);
                    return NativeApiResponse.success(request.getRequestId(), data);
                }
                case "remove": {
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    pref.remove(key);
                    Map<String, Object> data = new HashMap<>();
                    data.put("removed", true);
                    return NativeApiResponse.success(request.getRequestId(), data);
                }
                case "clear": {
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    pref.clear();
                    Map<String, Object> data = new HashMap<>();
                    data.put("cleared", true);
                    return NativeApiResponse.success(request.getRequestId(), data);
                }
                case "keys": {
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    List<String> keys = new ArrayList<>(pref.getAll().keySet());
                    Map<String, Object> data = new HashMap<>();
                    data.put("keys", keys);
                    return NativeApiResponse.success(request.getRequestId(), data);
                }
                case "has": {
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    Map<String, Object> data = new HashMap<>();
                    data.put("exists", pref.contains(key));
                    return NativeApiResponse.success(request.getRequestId(), data);
                }
                case "getAll": {
                    EncryptedPreferences pref = storageService.getPreferences(prefName);
                    return NativeApiResponse.success(request.getRequestId(),
                            new HashMap<>(pref.getAll()));
                }
                default:
                    break;
            }

            // ---- Legacy actions (pre-1.0.2 contract) ----
            Map<String, Object> resultData = new HashMap<>();

            if ("pref.set".equalsIgnoreCase(action)) {
                if (key == null) {
                    return NativeApiResponse.error(request.getRequestId(), 400,
                            new NativeApiError("INVALID_ARG", "Key required"));
                }
                storageService.getPreferences(prefName).putString(key, asString(params.get("value"), null));
                resultData.put("success", true);
                resultData.put("key", key);
                return NativeApiResponse.success(request.getRequestId(), resultData);

            } else if ("pref.get".equalsIgnoreCase(action)) {
                String val = storageService.getPreferences(prefName)
                        .getString(key, asString(params.get("defaultValue"), null));
                resultData.put("key", key);
                resultData.put("value", val);
                return NativeApiResponse.success(request.getRequestId(), resultData);

            } else if ("pref.remove".equalsIgnoreCase(action)) {
                boolean removed = storageService.getPreferences(prefName).remove(key);
                resultData.put("success", removed);
                return NativeApiResponse.success(request.getRequestId(), resultData);

            } else if ("db.insert".equalsIgnoreCase(action)) {
                String colName = asString(params.get("collection"), DEFAULT_PREF_NAME);
                String docId = asString(params.get("id"), null);
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) params.get("data");
                StorageCollection col = storageService.getDatabase().getCollection(colName);
                Document doc = new Document(docId, data);
                col.insert(doc);
                resultData.put("success", true);
                resultData.put("documentId", doc.getId());
                return NativeApiResponse.success(request.getRequestId(), resultData);

            } else if ("db.findById".equalsIgnoreCase(action)) {
                String colName = asString(params.get("collection"), DEFAULT_PREF_NAME);
                String docId = asString(params.get("id"), null);
                StorageCollection col = storageService.getDatabase().getCollection(colName);
                Document doc = col.findById(docId);
                resultData.put("found", doc != null);
                if (doc != null) {
                    resultData.put("document", doc.toMap());
                }
                return NativeApiResponse.success(request.getRequestId(), resultData);

            } else if ("db.count".equalsIgnoreCase(action)) {
                String colName = asString(params.get("collection"), DEFAULT_PREF_NAME);
                StorageCollection col = storageService.getDatabase().getCollection(colName);
                resultData.put("count", col.count(QueryFilter.all()));
                return NativeApiResponse.success(request.getRequestId(), resultData);
            }

            return NativeApiResponse.error(request.getRequestId(), 404,
                    new NativeApiError("UNKNOWN_ACTION", "Action not supported: " + rawAction));

        } catch (Exception e) {
            return NativeApiResponse.error(request.getRequestId(), 500,
                    new NativeApiError("EXECUTION_ERROR", e.getMessage()));
        }
    }

    /**
     * Accepts {@code storage.get}, {@code /storage/get} and {@code get}.
     */
    static String normalize(String action) {
        if (action == null) {
            return "";
        }
        String a = action.trim();
        if (a.startsWith("/")) {
            a = a.substring(1);
        }
        if (a.startsWith("storage/")) {
            a = a.substring("storage/".length());
        } else if (a.startsWith("storage.")) {
            a = a.substring("storage.".length());
        }
        return a;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    public void handle(NativeApiRequest request, IApiCallback callback) {
        if (callback == null) return;
        NativeApiResponse response = handleRequest(request);
        if (response.isSuccess()) {
            callback.onResponse(response);
        } else {
            callback.onError(response.getError());
        }
    }
}
