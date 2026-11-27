package com.roni.life.os.app.resource;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The manifest is derived from the project configuration, never persisted. */
public class ResourceManifestBuilderTest {

    private Project project(ProjectSourceType type, String path) {
        Project project = new Project();
        project.setId("p1");
        project.setSourceType(type);
        project.setSourcePath(path);
        return project;
    }

    @Test
    public void localArchiveIsVerifiedButNotDownloaded() {
        ResourceManifest manifest = ResourceManifestBuilder.build(
                project(ProjectSourceType.ZIP_FILE, "/sdcard/app.zip"));
        assertEquals(1, manifest.getSpecs().size());
        ResourceSpec spec = manifest.getSpecs().get(0);
        assertEquals(ResourceSpec.Kind.LOCAL_ARCHIVE, spec.getKind());
        assertFalse(spec.needsDownload());
        assertFalse(spec.isExtract());
    }

    @Test
    public void assetArchiveIsVerifiedButNotDownloaded() {
        ResourceManifest manifest = ResourceManifestBuilder.build(
                project(ProjectSourceType.ZIP_ASSET, "web/app.zip"));
        assertEquals(ResourceSpec.Kind.ASSET_ARCHIVE, manifest.getSpecs().get(0).getKind());
        assertFalse(manifest.getSpecs().get(0).needsDownload());
    }

    @Test
    public void remoteArchiveIsDownloadedAndExtracted() {
        ResourceManifest manifest = ResourceManifestBuilder.build(
                project(ProjectSourceType.REMOTE_URL, "https://example.com/app.zip"));
        ResourceSpec spec = manifest.getSpecs().get(0);
        assertEquals(ResourceSpec.Kind.REMOTE_ARCHIVE, spec.getKind());
        assertTrue(spec.needsDownload());
        assertTrue(spec.isExtract());
        assertTrue(manifest.hasExtractable());
    }

    @Test
    public void remoteDocumentIsTrackedButNotDownloaded() {
        ResourceManifest manifest = ResourceManifestBuilder.build(
                project(ProjectSourceType.REMOTE_URL, "https://example.com/index.html"));
        ResourceSpec spec = manifest.getSpecs().get(0);
        assertEquals(ResourceSpec.Kind.REMOTE_DOCUMENT, spec.getKind());
        assertFalse(spec.needsDownload());
        assertTrue(spec.isDocument());
    }

    @Test
    public void inlineHtmlNeedsNoResources() {
        assertTrue(ResourceManifestBuilder.build(
                project(ProjectSourceType.INLINE_HTML, "")).isEmpty());
    }

    @Test
    public void safFolderNeedsNoResources() {
        assertTrue(ResourceManifestBuilder.build(
                project(ProjectSourceType.SAF_FOLDER, "content://tree/x")).isEmpty());
    }

    @Test
    public void plainFileNeedsNoResources() {
        assertTrue(ResourceManifestBuilder.build(
                project(ProjectSourceType.FILE, "/sdcard/site")).isEmpty());
    }

    @Test
    public void archiveUrlDetectionIgnoresQueryAndFragment() {
        assertTrue(ResourceManifestBuilder.isArchiveUrl("https://x.dev/a.zip"));
        assertTrue(ResourceManifestBuilder.isArchiveUrl("https://x.dev/a.ZIP?token=1"));
        assertTrue(ResourceManifestBuilder.isArchiveUrl("https://x.dev/a.zip#frag"));
        assertFalse(ResourceManifestBuilder.isArchiveUrl("https://x.dev/a.html"));
        assertFalse(ResourceManifestBuilder.isArchiveUrl(null));
    }

    @Test
    public void manifestVersionComesFromTheProject() {
        Project project = project(ProjectSourceType.REMOTE_URL, "https://x.dev/a.zip");
        project.setResourceVersion("7");
        assertEquals("7", ResourceManifestBuilder.build(project).getVersion());
    }
}
