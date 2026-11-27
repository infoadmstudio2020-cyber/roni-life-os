package com.roni.library.ui.web;

import android.Manifest;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;

import com.roni.library.security.SecurityEngine;
import com.roni.library.security.permission.PermissionService;
import com.roni.library.security.permission.PermissionStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link WebPermissionHandler} backed by {@code native-security-engine}.
 *
 * <p>Grants only when every requested Android permission is already {@code GRANTED}. When the
 * security engine is not initialised the request is denied — never silently granted.</p>
 */
public class SecurityWebPermissionHandler implements WebPermissionHandler {

    private static final String TAG = "SecurityWebPermission";

    @Override
    public void onPermissionRequest(PermissionRequest request, PermissionResponder responder) {
        if (request == null || responder == null) {
            return;
        }
        String[] resources = request.getResources();
        if (resources == null || resources.length == 0) {
            responder.deny();
            return;
        }

        List<String> androidPermissions = new ArrayList<>();
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                androidPermissions.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                androidPermissions.add(Manifest.permission.RECORD_AUDIO);
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                // Protected media does not map to a runtime permission.
                continue;
            } else {
                responder.deny();
                return;
            }
        }

        if (androidPermissions.isEmpty()) {
            responder.grant(resources);
            return;
        }
        if (allGranted(androidPermissions)) {
            responder.grant(resources);
        } else {
            responder.deny();
        }
    }

    @Override
    public void onGeolocationRequest(String origin, GeolocationResponder responder) {
        if (responder == null) {
            return;
        }
        boolean granted = allGranted(
                java.util.Collections.singletonList(Manifest.permission.ACCESS_FINE_LOCATION));
        responder.invoke(granted, granted);
    }

    private boolean allGranted(List<String> permissions) {
        PermissionService service = permissionService();
        if (service == null) {
            android.util.Log.w(TAG, "SecurityEngine is not initialized — denying web permission");
            return false;
        }
        for (String permission : permissions) {
            if (service.checkPermission(permission) != PermissionStatus.GRANTED) {
                return false;
            }
        }
        return true;
    }

    private PermissionService permissionService() {
        try {
            SecurityEngine engine = SecurityEngine.getInstance();
            if (engine == null || !engine.isInitialized()) {
                return null;
            }
            return engine.getPermissionService();
        } catch (Throwable t) {
            return null;
        }
    }
}
