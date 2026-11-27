package com.roni.library.ui.web;

import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;

/**
 * Host-supplied policy for WebView permission prompts.
 *
 * <p>When no handler is configured, {@link WebViewHost} denies every request — the historical
 * behaviour. A handler decides whether to grant camera, microphone or geolocation access.</p>
 */
public interface WebPermissionHandler {

    /**
     * Called for {@code PermissionRequest} (camera, microphone, protected media).
     */
    void onPermissionRequest(PermissionRequest request, PermissionResponder responder);

    /**
     * Called for geolocation prompts.
     */
    void onGeolocationRequest(String origin, GeolocationResponder responder);

    interface PermissionResponder {
        void grant(String[] resources);
        void deny();
    }

    interface GeolocationResponder {
        void invoke(boolean allow, boolean retain);
    }
}
