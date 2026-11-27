package com.roni.life.os.app.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * A source is valid only when the folder, the entry document and the manifest are all present.
 * These tests pin that rule because activation and fallback both depend on it.
 */
public class SourceValidatorTest {

    private File dir;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("source").toFile();
    }

    @After
    public void tearDown() {
        delete(dir);
    }

    @Test
    public void emptyFolder_isInvalid() {
        SourceValidator.Validation validation = SourceValidator.validate(dir, "index.html");

        assertFalse(validation.isValid());
        assertTrue(validation.getMissing().contains("index.html"));
        assertTrue(validation.getMissing().contains(SourceValidator.MANIFEST));
    }

    @Test
    public void missingFolder_isInvalid() {
        SourceValidator.Validation validation =
                SourceValidator.validate(new File(dir, "nope"), "index.html");

        assertFalse(validation.isValid());
        assertFalse(SourceValidator.hasContent(new File(dir, "nope")));
    }

    @Test
    public void entryWithoutManifest_isInvalid() throws IOException {
        write(new File(dir, "index.html"), "<html></html>");

        SourceValidator.Validation validation = SourceValidator.validate(dir, "index.html");

        assertFalse(validation.isValid());
        assertEquals(1, validation.getMissing().size());
        assertEquals(SourceValidator.MANIFEST, validation.getMissing().get(0));
    }

    @Test
    public void manifestWithoutEntry_isInvalid() throws IOException {
        write(new File(dir, SourceValidator.MANIFEST), "{}");

        SourceValidator.Validation validation = SourceValidator.validate(dir, "index.html");

        assertFalse(validation.isValid());
        assertTrue(validation.getMissing().contains("index.html"));
    }

    @Test
    public void completeSource_isValid() throws IOException {
        write(new File(dir, "index.html"), "<html></html>");
        write(new File(dir, SourceValidator.MANIFEST), "{}");

        SourceValidator.Validation validation = SourceValidator.validate(dir, "index.html");

        assertTrue(validation.isValid());
        assertTrue(validation.getMissing().isEmpty());
        assertTrue(SourceValidator.hasContent(dir));
    }

    @Test
    public void nestedEntry_isSupported() throws IOException {
        write(new File(dir, "web/index.html"), "<html></html>");
        write(new File(dir, SourceValidator.MANIFEST), "{}");

        assertTrue(SourceValidator.validate(dir, "web/index.html").isValid());
        assertFalse(SourceValidator.validate(dir, "index.html").isValid());
    }

    @Test
    public void zeroLengthEntry_isInvalid() throws IOException {
        write(new File(dir, "index.html"), "");
        write(new File(dir, SourceValidator.MANIFEST), "{}");

        assertFalse(SourceValidator.validate(dir, "index.html").isValid());
    }

    @Test
    public void blankEntry_defaultsToIndexHtml() throws IOException {
        write(new File(dir, "index.html"), "<html></html>");
        write(new File(dir, SourceValidator.MANIFEST), "{}");

        assertTrue(SourceValidator.validate(dir, "  ").isValid());
    }

    private static void write(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        Files.write(file.toPath(), content.getBytes("UTF-8"));
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
