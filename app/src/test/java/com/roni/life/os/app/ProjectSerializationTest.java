package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;

import org.junit.Test;

/**
 * URL-encoded Key-Value কোডেকের মাধ্যমে প্রজেক্ট সিরিয়ালাইজেশন যাচাই।
 */
public class ProjectSerializationTest {

    @Test
    public void roundTrip_preservesAllFields() {
        Project original = new Project();
        original.setName("বাংলা প্রজেক্ট — টেস্ট");
        original.setDescription("বিবরণে & = % ? / : @ ফুটে থাকতে পারে");
        original.setIconColor(0xFF00B894);
        original.setSourceType(ProjectSourceType.ZIP_ASSET);
        original.setSourcePath("webapp.zip");
        original.setEntryPoint("index.html");
        original.setBaseUrl("https://example.com/base");
        original.setInlineHtml("<html><body>হ্যালো</body></html>");
        original.setSplashEnabled(false);
        original.setBootstrapEnabled(true);
        original.setSplashDurationMs(2500);
        original.setDefault(true);

        String encoded = original.encode();
        Project decoded = Project.decode(encoded);

        assertNotNull(decoded);
        assertEquals(original.getId(), decoded.getId());
        assertEquals(original.getName(), decoded.getName());
        assertEquals(original.getDescription(), decoded.getDescription());
        assertEquals(original.getIconColor(), decoded.getIconColor());
        assertEquals(original.getSourceType(), decoded.getSourceType());
        assertEquals(original.getSourcePath(), decoded.getSourcePath());
        assertEquals(original.getEntryPoint(), decoded.getEntryPoint());
        assertEquals(original.getBaseUrl(), decoded.getBaseUrl());
        assertEquals(original.getInlineHtml(), decoded.getInlineHtml());
        assertEquals(original.isSplashEnabled(), decoded.isSplashEnabled());
        assertEquals(original.isBootstrapEnabled(), decoded.isBootstrapEnabled());
        assertEquals(original.getSplashDurationMs(), decoded.getSplashDurationMs());
        assertEquals(original.isDefault(), decoded.isDefault());
    }

    @Test
    public void encode_isStableForSameProject() {
        Project project = new Project();
        project.setName("স্থিতিশীল");
        assertEquals(project.encode(), project.encode());
    }

    @Test
    public void decodeOfNull_returnsNull() {
        assertEquals(null, Project.decode(null));
    }

    @Test
    public void decodeOfGarbage_returnsNull() {
        assertEquals(null, Project.decode("এইটা কোনো বৈধ এনকোডিং নয়"));
    }

    @Test
    public void copy_generatesNewIdAndName() {
        Project source = new Project();
        source.setName("মূল");
        source.setSourcePath("webapp.zip");

        Project copy = source.copy("কপি");

        assertFalse(source.getId().equals(copy.getId()));
        assertEquals("কপি", copy.getName());
        assertEquals(source.getSourcePath(), copy.getSourcePath());
        assertFalse(copy.isDefault());
    }

    @Test
    public void splashDuration_isClamped() {
        Project project = new Project();
        project.setSplashDurationMs(1);
        assertEquals(Project.MIN_SPLASH_DURATION_MS, project.getSplashDurationMs());

        project.setSplashDurationMs(999999);
        assertEquals(Project.MAX_SPLASH_DURATION_MS, project.getSplashDurationMs());
    }

    @Test
    public void sourceType_fromKey_isCaseInsensitive() {
        assertEquals(ProjectSourceType.ZIP_ASSET, ProjectSourceType.fromKey("ZIP_ASSET"));
        assertEquals(ProjectSourceType.REMOTE_URL, ProjectSourceType.fromKey("remote_url"));
        assertEquals(ProjectSourceType.ASSET, ProjectSourceType.fromKey("অজানা"));
    }

    @Test
    public void entryPointRequirement_matchesSourceType() {
        assertTrue(ProjectSourceType.ASSET.requiresEntryPoint());
        assertTrue(ProjectSourceType.ZIP_FILE.requiresEntryPoint());
        assertFalse(ProjectSourceType.REMOTE_URL.requiresEntryPoint());
        assertFalse(ProjectSourceType.INLINE_HTML.requiresEntryPoint());
    }
}
