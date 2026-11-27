package com.roni.library.security.permission;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subsystem service for managing and querying Android runtime permissions.
 *
 * <p><b>Fail-closed contract:</b> {@link #checkPermission(String)} never returns
 * {@link PermissionStatus#GRANTED} unless the platform (or an explicitly attached
 * {@link PermissionDelegate}) reports the permission as granted. An unknown permission, a
 * missing {@link Context} and a missing delegate all resolve to
 * {@link PermissionStatus#NOT_REQUESTED} or {@link PermissionStatus#DENIED}.</p>
 *
 * @author infoadmstudio2020-cyber
 */
public class PermissionService {

    private final Map<String, PermissionStatus> permissionCache = new ConcurrentHashMap<>();
    private final List<PermissionCallback> globalListeners = new CopyOnWriteArrayList<>();
    private volatile PermissionDelegate permissionDelegate;
    private volatile Context appContext;

    /**
     * Binds the Android context used to read real permission state.
     */
    public void attachContext(Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    public Context getContext() {
        return appContext;
    }

    public void setPermissionDelegate(PermissionDelegate delegate) {
        this.permissionDelegate = delegate;
    }

    /**
     * Resolves the current status of a permission.
     *
     * @param permission the Android permission string
     * @return the real status; never a default {@code GRANTED}
     */
    public PermissionStatus checkPermission(String permission) {
        if (permission == null) {
            return PermissionStatus.NOT_REQUESTED;
        }

        Context ctx = appContext;
        if (ctx != null) {
            return readPlatformStatus(ctx, permission);
        }

        PermissionStatus cached = permissionCache.get(permission);
        return cached != null ? cached : PermissionStatus.NOT_REQUESTED;
    }

    private PermissionStatus readPlatformStatus(Context ctx, String permission) {
        try {
            boolean granted;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                granted = ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
            } else {
                granted = ctx.getPackageManager()
                        .checkPermission(permission, ctx.getPackageName())
                        == PackageManager.PERMISSION_GRANTED;
            }
            PermissionStatus status = granted ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
            permissionCache.put(permission, status);
            return status;
        } catch (Throwable t) {
            return PermissionStatus.NOT_REQUESTED;
        }
    }

    public boolean isPermissionGranted(String permission) {
        return checkPermission(permission).isGranted();
    }

    public void setPermissionStatus(String permission, PermissionStatus status) {
        if (permission != null && status != null) {
            permissionCache.put(permission, status);
        }
    }

    public boolean shouldShowRequestPermissionRationale(String permission) {
        if (permissionDelegate != null) {
            return permissionDelegate.shouldShowRationale(permission);
        }
        return false;
    }

    public void requestPermission(String permission, PermissionCallback callback) {
        requestMultiplePermissions(Collections.singletonList(permission), callback);
    }

    public void requestMultiplePermissions(List<String> permissions, PermissionCallback callback) {
        if (permissions == null || permissions.isEmpty()) {
            if (callback != null) {
                callback.onMultiplePermissionsResult(Collections.emptyMap());
            }
            return;
        }

        if (permissionDelegate != null) {
            permissionDelegate.requestPermissions(permissions, callback);
            return;
        }

        // No delegate attached: report the real platform status. Never simulate a grant.
        Map<String, PermissionStatus> results = new HashMap<>();
        for (String p : permissions) {
            PermissionStatus current = checkPermission(p);
            results.put(p, current);
            onSinglePermissionResult(p, current);
        }
        onMultiplePermissionsResult(results);
        if (callback != null) {
            if (permissions.size() == 1) {
                String p = permissions.get(0);
                callback.onSinglePermissionResult(p, results.get(p));
            }
            callback.onMultiplePermissionsResult(results);
        }
    }

    public void onSinglePermissionResult(String permission, PermissionStatus status) {
        if (permission != null && status != null) {
            permissionCache.put(permission, status);
            for (PermissionCallback listener : globalListeners) {
                try {
                    listener.onSinglePermissionResult(permission, status);
                } catch (Exception ignored) {}
            }
        }
    }

    public void onMultiplePermissionsResult(Map<String, PermissionStatus> results) {
        if (results != null) {
            permissionCache.putAll(results);
            for (PermissionCallback listener : globalListeners) {
                try {
                    listener.onMultiplePermissionsResult(results);
                } catch (Exception ignored) {}
            }
        }
    }

    public void addListener(PermissionCallback listener) {
        if (listener != null && !globalListeners.contains(listener)) {
            globalListeners.add(listener);
        }
    }

    public void removeListener(PermissionCallback listener) {
        if (listener != null) {
            globalListeners.remove(listener);
        }
    }

    public void clear() {
        permissionCache.clear();
        globalListeners.clear();
        permissionDelegate = null;
    }

    /**
     * Full teardown, including the bound context. Used by {@code SecurityEngine.release()}.
     */
    public void release() {
        clear();
        appContext = null;
    }
}
