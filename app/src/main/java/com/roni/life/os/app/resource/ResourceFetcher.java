package com.roni.life.os.app.resource;

import java.io.File;

/**
 * Fetches the bytes of one resource into {@code destination}.
 *
 * <p>This is the seam that keeps {@link ProjectResourceManager} free of Android and network
 * details: production uses {@link DownloadManagerFetcher}, which delegates to the existing
 * {@code DownloadManager}; tests use an in-memory implementation.</p>
 */
public interface ResourceFetcher {

    /**
     * Copies or downloads {@code spec} into {@code destination}, creating parent directories.
     *
     * <p>Implementations must be resumable: a partial file left by an interrupted attempt is
     * continued rather than discarded, and a failed attempt must not leave a file that would pass
     * verification.</p>
     */
    void fetch(ResourceSpec spec, File destination) throws Exception;

    /**
     * @return {@code true} when the remote copy differs from {@code record} and must be fetched
     */
    boolean isChanged(ResourceSpec spec, ResourceRecord record);

    /**
     * Reads the current remote tag (ETag) used for change detection without downloading the body.
     *
     * <p>Returns an empty string when the server exposes no tag — in that case a change cannot be
     * proven, so the working copy is kept instead of re-downloading on every open.</p>
     */
    default String remoteTag(ResourceSpec spec) {
        return "";
    }
}
