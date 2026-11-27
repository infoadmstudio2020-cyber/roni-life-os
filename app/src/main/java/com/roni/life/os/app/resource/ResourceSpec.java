package com.roni.life.os.app.resource;

/**
 * A single resource a project needs before it can run.
 *
 * <p>A spec is derived from the project configuration on every open — it is never persisted. What
 * is persisted is the {@link ResourceRecord} that proves the spec was satisfied on this device.</p>
 */
public final class ResourceSpec {

    /** Where the bytes come from and what has to happen to them. */
    public enum Kind {
        /** An archive already on the device (a {@code file://} path). */
        LOCAL_ARCHIVE,
        /** An archive bundled inside the APK. */
        ASSET_ARCHIVE,
        /** An archive behind an HTTP URL — the only kind that is ever downloaded. */
        REMOTE_ARCHIVE,
        /** A remote document the WebView loads directly; tracked, never downloaded. */
        REMOTE_DOCUMENT
    }

    private final String id;
    private final Kind kind;
    private final String location;
    private final String relativePath;
    private final String version;
    private final String checksum;
    private final long sizeBytes;
    private final boolean required;
    private final boolean extract;

    public ResourceSpec(String id, Kind kind, String location, String relativePath,
                        String version, String checksum, long sizeBytes,
                        boolean required, boolean extract) {
        this.id = id != null && !id.trim().isEmpty() ? id.trim() : "resource";
        this.kind = kind != null ? kind : Kind.LOCAL_ARCHIVE;
        this.location = location != null ? location.trim() : "";
        this.relativePath = relativePath != null && !relativePath.trim().isEmpty()
                ? relativePath.trim() : this.id;
        this.version = version != null && !version.trim().isEmpty() ? version.trim() : "1";
        this.checksum = checksum != null ? checksum.trim() : "";
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.required = required;
        this.extract = extract;
    }

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getLocation() {
        return location;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getVersion() {
        return version;
    }

    public String getChecksum() {
        return checksum;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isExtract() {
        return extract;
    }

    /** @return {@code true} when the bytes must be fetched over the network */
    public boolean needsDownload() {
        return kind == Kind.REMOTE_ARCHIVE;
    }

    /** @return {@code true} when the bytes live on this device already */
    public boolean isLocal() {
        return kind == Kind.LOCAL_ARCHIVE || kind == Kind.ASSET_ARCHIVE;
    }

    /** @return {@code true} when the resource is a document the WebView loads itself */
    public boolean isDocument() {
        return kind == Kind.REMOTE_DOCUMENT;
    }

    @Override
    public String toString() {
        return "ResourceSpec{id='" + id + "', kind=" + kind + ", version='" + version + "'}";
    }
}
