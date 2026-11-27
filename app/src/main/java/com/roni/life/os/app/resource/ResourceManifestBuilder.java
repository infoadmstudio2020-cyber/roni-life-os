package com.roni.life.os.app.resource;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives the {@link ResourceManifest} for a project from its source configuration.
 *
 * <p>Only a remote archive is ever downloaded. Local and bundled archives are verified in place,
 * a remote document is tracked but loaded by the WebView itself, and every other source type
 * (inline HTML, SAF folder, plain file, asset folder) needs no resources at all — those projects
 * are ready the moment they are registered.</p>
 */
public final class ResourceManifestBuilder {

    /** Identifier of the single archive resource a project can own. */
    public static final String ARCHIVE_ID = "archive";

    /** Identifier of the remote document resource. */
    public static final String DOCUMENT_ID = "document";

    private ResourceManifestBuilder() {
    }

    public static ResourceManifest build(Project project) {
        if (project == null) {
            return ResourceManifest.empty("1");
        }
        String version = project.getResourceVersion() != null
                && !project.getResourceVersion().trim().isEmpty()
                ? project.getResourceVersion().trim() : "1";
        String path = project.getSourcePath() != null ? project.getSourcePath().trim() : "";
        ProjectSourceType type = project.getSourceType();
        if (type == null || path.isEmpty()) {
            return ResourceManifest.empty(version);
        }

        List<ResourceSpec> specs = new ArrayList<>();
        switch (type) {
            case ZIP_FILE:
                specs.add(new ResourceSpec(ARCHIVE_ID, ResourceSpec.Kind.LOCAL_ARCHIVE, path,
                        "archive.zip", version, project.getResourceChecksum(), 0L, true, false));
                break;
            case ZIP_ASSET:
                specs.add(new ResourceSpec(ARCHIVE_ID, ResourceSpec.Kind.ASSET_ARCHIVE, path,
                        "archive.zip", version, project.getResourceChecksum(), 0L, true, false));
                break;
            case REMOTE_URL:
                if (isArchiveUrl(path)) {
                    specs.add(new ResourceSpec(ARCHIVE_ID, ResourceSpec.Kind.REMOTE_ARCHIVE, path,
                            "archive.zip", version, project.getResourceChecksum(), 0L, true, true));
                } else {
                    specs.add(new ResourceSpec(DOCUMENT_ID, ResourceSpec.Kind.REMOTE_DOCUMENT,
                            path, "document", version, project.getResourceChecksum(), 0L,
                            true, false));
                }
                break;
            default:
                // ASSET, FILE, SAF_FOLDER and INLINE_HTML read straight from their source.
                break;
        }
        return new ResourceManifest(version, specs);
    }

    /** @return {@code true} when the URL points at a ZIP archive */
    public static boolean isArchiveUrl(String url) {
        if (url == null) {
            return false;
        }
        String value = url.trim().toLowerCase(Locale.US);
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        return value.endsWith(".zip");
    }
}
