package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.filemanager.ArchiveEntry;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerError;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.FileOperation;
import com.roni.library.contracts.filemanager.SearchQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native bridge route {@code /filemanager}.
 *
 * <p>The owning project always comes from {@link FileManagerProjectContext}; a {@code projectId}
 * inside the payload is ignored. Every action validates its input before touching storage.</p>
 */
public final class FileCommandHandler implements IApiHandler {

    public static final String ROUTE = "filemanager";

    private final FileManagerService service;

    public FileCommandHandler(@NonNull FileManagerService service) {
        this.service = service;
    }

    @NonNull
    @Override
    public String getPath() {
        return ROUTE;
    }

    @NonNull
    @Override
    public String getAction() {
        return "*";
    }

    @NonNull
    @Override
    public NativeApiResponse handleRequest(@NonNull NativeApiRequest request) {
        String action = request.getAction() != null ? request.getAction() : "";
        try {
            switch (action) {
                case "getStorageInfo":
                    return ok(request, service.getStorageInfo().toMap());
                case "listFiles":
                    return listFiles(request);
                case "getFileInfo":
                    return ok(request, service.getFileInfo(path(request)).toMap());
                case "createFile":
                    return ok(request, service.createFile(path(request),
                            request.getStringParam("content", "")).toMap());
                case "createFolder":
                    return ok(request, service.createFolder(path(request)).toMap());
                case "readFile":
                    return ok(request, service.readFile(path(request)));
                case "writeFile":
                    service.writeFile(path(request), request.getStringParam("base64", ""));
                    return ok(request, empty());
                case "copy":
                    return ok(request, operation(service.copy(paths(request),
                            request.getStringParam("targetDir", ""))));
                case "move":
                    return ok(request, operation(service.move(paths(request),
                            request.getStringParam("targetDir", ""))));
                case "rename":
                    return ok(request, service.rename(path(request),
                            request.getStringParam("newName", "")).toMap());
                case "delete":
                    return ok(request, operation(service.delete(paths(request))));
                case "search":
                    return search(request);
                case "getRecent":
                    return list(request, service.getRecent((int) number(request, "limit", 20)));
                case "addFavorite":
                    service.addFavorite(path(request));
                    return ok(request, empty());
                case "removeFavorite":
                    service.removeFavorite(path(request));
                    return ok(request, empty());
                case "getFavorites":
                    return list(request, service.getFavorites());
                case "import":
                    return ok(request, service.importFromSaf(
                            request.getStringParam("treeId", ""),
                            request.getStringParam("documentId", ""),
                            request.getStringParam("targetDir", "")).toMap());
                case "export":
                    return ok(request, service.exportToSaf(
                            request.getStringParam("treeId", ""),
                            request.getStringParam("parentDocumentId", ""),
                            path(request)).toMap());
                case "pickDirectory":
                    return pickDirectory(request);
                case "getPermissions":
                    return ok(request, service.getPermissions());
                case "requestAccess":
                    return ok(request, service.getPermissions());
                case "getThumbnail":
                    return ok(request, service.getThumbnail(path(request)));
                case "getCategories":
                    return ok(request, service.getCategories());
                case "getStorageAnalysis":
                    return ok(request, service.getStorageAnalysis());
                case "createArchive":
                    return ok(request, operation(service.createArchive(paths(request),
                            request.getStringParam("archivePath", ""))));
                case "extractArchive":
                    return ok(request, operation(service.extractArchive(
                            path(request), request.getStringParam("targetDir", ""),
                            stringList(request, "entries"))));
                case "listArchive":
                    return listArchive(request);
                case "getOperationStatus":
                    return operationStatus(request);
                case "cancelOperation":
                    return ok(request, cancel(request));
                default:
                    return failure(request, new FileManagerError(
                            FileManagerErrorCodes.INVALID_REQUEST,
                            "Unknown file manager action: " + action));
            }
        } catch (FileManagerException error) {
            return failure(request, error.toError());
        } catch (Throwable error) {
            return failure(request, new FileManagerError(FileManagerErrorCodes.IO_ERROR,
                    "File manager request failed: " + error.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    @NonNull
    private NativeApiResponse listFiles(@NonNull NativeApiRequest request)
            throws FileManagerException {
        List<FileEntry> entries = service.listFiles(path(request),
                request.getBooleanParam("includeHidden", false),
                request.getStringParam("sortBy", SearchQuery.SORT_NAME),
                request.getBooleanParam("descending", false),
                (int) number(request, "offset", 0),
                (int) number(request, "limit", 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", entries(entries));
        data.put("count", entries.size());
        return ok(request, data);
    }

    @NonNull
    private NativeApiResponse search(@NonNull NativeApiRequest request)
            throws FileManagerException {
        SearchQuery query = SearchQuery.fromMap(request.getParameters());
        List<FileEntry> entries = service.search(path(request), query);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", entries(entries));
        data.put("count", entries.size());
        return ok(request, data);
    }

    @NonNull
    private NativeApiResponse listArchive(@NonNull NativeApiRequest request)
            throws FileManagerException {
        List<ArchiveEntry> entries = service.listArchive(path(request));
        List<Map<String, Object>> maps = new ArrayList<>();
        for (ArchiveEntry entry : entries) {
            maps.add(entry.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", maps);
        data.put("count", maps.size());
        return ok(request, data);
    }

    @NonNull
    private NativeApiResponse pickDirectory(@NonNull NativeApiRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requiresHostPicker", true);
        data.put("roots", service.getSafRoots());
        return ok(request, data);
    }

    @NonNull
    private NativeApiResponse operationStatus(@NonNull NativeApiRequest request) {
        FileOperation operation = service.getOperationStatus(
                request.getStringParam("operationId", ""));
        if (operation == null) {
            return failure(request, new FileManagerError(FileManagerErrorCodes.NOT_FOUND,
                    "Unknown operation"));
        }
        return ok(request, operation.toMap());
    }

    @NonNull
    private Map<String, Object> cancel(@NonNull NativeApiRequest request) {
        boolean cancelled = service.cancelOperation(
                request.getStringParam("operationId", ""));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cancelled", cancelled);
        return data;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @NonNull
    private static NativeApiResponse list(@NonNull NativeApiRequest request,
                                          @NonNull List<FileEntry> entries) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", entries(entries));
        data.put("count", entries.size());
        return ok(request, data);
    }

    @NonNull
    private static Map<String, Object> operation(@NonNull String operationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operationId", operationId);
        data.put("accepted", true);
        return data;
    }

    @NonNull
    private static Map<String, Object> empty() {
        return new LinkedHashMap<>();
    }

    @NonNull
    private static List<Map<String, Object>> entries(@NonNull List<FileEntry> entries) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (FileEntry entry : entries) {
            maps.add(entry.toMap());
        }
        return maps;
    }

    @NonNull
    private static String path(@NonNull NativeApiRequest request) {
        String value = request.getStringParam("path", "");
        return value;
    }

    @NonNull
    private static List<String> paths(@NonNull NativeApiRequest request) {
        List<String> values = stringList(request, "paths");
        if (values.isEmpty()) {
            String single = request.getStringParam("path", "");
            if (!single.isEmpty()) {
                values.add(single);
            }
        }
        return values;
    }

    @NonNull
    private static List<String> stringList(@NonNull NativeApiRequest request,
                                           @NonNull String key) {
        List<String> values = new ArrayList<>();
        Object raw = request.getParameter(key);
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (raw instanceof String && !((String) raw).isEmpty()) {
            values.add((String) raw);
        }
        return values;
    }

    private static long number(@NonNull NativeApiRequest request, @NonNull String key,
                               long fallback) {
        Object value = request.getParameter(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    @NonNull
    private static NativeApiResponse ok(@NonNull NativeApiRequest request,
                                        @Nullable Map<String, Object> data) {
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private static NativeApiResponse failure(@NonNull NativeApiRequest request,
                                             @NonNull FileManagerError error) {
        return NativeApiResponse.error(request.getRequestId(),
                new NativeApiError(error.getCode(), error.getMessage()));
    }
}
