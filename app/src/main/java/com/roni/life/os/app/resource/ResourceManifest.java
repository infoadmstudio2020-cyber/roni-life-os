package com.roni.life.os.app.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of resources a project needs, plus the manifest version they belong to.
 *
 * <p>The manifest is rebuilt from the project configuration on every open, so it always reflects
 * the current source type and version. Comparing it against the persisted
 * {@link ProjectResourceState} is what tells the manager whether anything actually changed.</p>
 */
public final class ResourceManifest {

    private final String version;
    private final List<ResourceSpec> specs;

    public ResourceManifest(String version, List<ResourceSpec> specs) {
        this.version = version != null && !version.trim().isEmpty() ? version.trim() : "1";
        this.specs = Collections.unmodifiableList(
                specs != null ? new ArrayList<>(specs) : new ArrayList<ResourceSpec>());
    }

    public static ResourceManifest empty(String version) {
        return new ResourceManifest(version, Collections.<ResourceSpec>emptyList());
    }

    public String getVersion() {
        return version;
    }

    public List<ResourceSpec> getSpecs() {
        return specs;
    }

    public boolean isEmpty() {
        return specs.isEmpty();
    }

    public ResourceSpec find(String id) {
        for (ResourceSpec spec : specs) {
            if (spec.getId().equals(id)) {
                return spec;
            }
        }
        return null;
    }

    /** @return {@code true} when at least one resource has to be unpacked after it is verified */
    public boolean hasExtractable() {
        for (ResourceSpec spec : specs) {
            if (spec.isExtract()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ResourceManifest{version='" + version + "', specs=" + specs.size() + "}";
    }
}
