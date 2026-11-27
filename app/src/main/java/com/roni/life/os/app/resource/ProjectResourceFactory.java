package com.roni.life.os.app.resource;

import android.content.Context;

import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.ProjectImporter;
import com.roni.life.os.app.project.ProjectStore;

/**
 * Builds a {@link ProjectResourceManager} bound to the running application.
 *
 * <p>Kept separate from the manager so the manager itself stays free of Android imports and can be
 * unit-tested on the JVM with a fake fetcher and a temporary directory.</p>
 */
public final class ProjectResourceFactory {

    private ProjectResourceFactory() {
    }

    public static ProjectResourceManager forApp(Context context) {
        ProjectStore store = ApplicationCore.get().getStore();
        return new ProjectResourceManager(
                new DownloadManagerFetcher(context),
                ProjectImporter.resourcesRoot(context),
                new ProjectResourceStore(store != null ? store.getBackend() : null),
                store);
    }
}
