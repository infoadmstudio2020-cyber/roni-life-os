package com.roni.life.os.app.camera;

import androidx.annotation.Nullable;

import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.camera.CameraService;

import java.io.File;

/** Resolves media files through the engine so no absolute path is ever built by the UI. */
final class CameraMediaFiles {

    private CameraMediaFiles() {
    }

    @Nullable
    static File resolve(CameraMediaItem item) {
        CameraService service = PlatformEngine.getInstance().getCameraService();
        return service != null ? service.resolveMedia(item.getId()) : null;
    }

    @Nullable
    static File resolveThumbnail(CameraMediaItem item) {
        CameraService service = PlatformEngine.getInstance().getCameraService();
        return service != null ? service.resolveThumbnail(item.getId()) : null;
    }
}
