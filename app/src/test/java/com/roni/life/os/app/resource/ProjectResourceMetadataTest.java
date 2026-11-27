package com.roni.life.os.app.resource;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Resource status is part of the project metadata and survives serialization. */
public class ProjectResourceMetadataTest {

    @Test
    public void defaultsDescribeAnUninitializedProject() {
        Project project = new Project();
        assertEquals("not_initialized", project.getResourceStatus());
        assertEquals("1", project.getResourceVersion());
        assertEquals("", project.getResourceChecksum());
        assertEquals(0L, project.getResourceInitializedAt());
        assertEquals(0L, project.getResourceLastVerifiedAt());
        assertFalse(project.isResourceReady());
    }

    @Test
    public void metadataRoundTripsThroughEncoding() {
        Project project = new Project();
        project.setId("p1");
        project.setResourceStatus("ready");
        project.setResourceVersion("4");
        project.setResourceChecksum("abc123");
        project.setResourceInitializedAt(1000L);
        project.setResourceLastVerifiedAt(2000L);

        Project decoded = Project.decode(project.encode());

        assertEquals("ready", decoded.getResourceStatus());
        assertEquals("4", decoded.getResourceVersion());
        assertEquals("abc123", decoded.getResourceChecksum());
        assertEquals(1000L, decoded.getResourceInitializedAt());
        assertEquals(2000L, decoded.getResourceLastVerifiedAt());
        assertTrue(decoded.isResourceReady());
    }

    @Test
    public void legacyRecordWithoutResourceFieldsStillDecodes() {
        Project project = new Project();
        project.setId("legacy");
        String encoded = project.encode();
        // Simulate a record written before the resource fields existed.
        String legacy = encoded.replaceAll("&resourceStatus=[^&]*", "")
                .replaceAll("&resourceVersion=[^&]*", "")
                .replaceAll("&resourceChecksum=[^&]*", "")
                .replaceAll("&resourceInitializedAt=[^&]*", "")
                .replaceAll("&resourceLastVerifiedAt=[^&]*", "");

        Project decoded = Project.decode(legacy);

        assertEquals("legacy", decoded.getId());
        assertEquals("not_initialized", decoded.getResourceStatus());
        assertEquals("1", decoded.getResourceVersion());
    }

    @Test
    public void copyStartsUninitializedButKeepsTheVersion() {
        Project project = new Project();
        project.setResourceStatus("ready");
        project.setResourceVersion("9");
        project.setResourceInitializedAt(500L);

        Project copy = project.copy("Copy");

        assertEquals("not_initialized", copy.getResourceStatus());
        assertEquals("9", copy.getResourceVersion());
        assertEquals(0L, copy.getResourceInitializedAt());
    }

    @Test
    public void manifestUsesTheProjectVersion() {
        Project project = new Project();
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("https://example.com/app.zip");
        project.setResourceVersion("12");

        assertEquals("12", ResourceManifestBuilder.build(project).getVersion());
    }
}
