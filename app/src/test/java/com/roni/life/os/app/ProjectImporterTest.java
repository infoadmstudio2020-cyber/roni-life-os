package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.project.ProjectImporter;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * Path-traversal and entry-point rules for ZIP import (EX-01).
 */
public class ProjectImporterTest {

    @Test
    public void acceptsOrdinaryRelativePaths() {
        assertTrue(ProjectImporter.isSafeEntryName("index.html"));
        assertTrue(ProjectImporter.isSafeEntryName("assets/css/app.css"));
        assertTrue(ProjectImporter.isSafeEntryName("js/modules/../main.js") == false);
        assertTrue(ProjectImporter.isSafeEntryName("a/b/c/d.png"));
    }

    @Test
    public void rejectsParentTraversal() {
        assertFalse(ProjectImporter.isSafeEntryName("../secret.txt"));
        assertFalse(ProjectImporter.isSafeEntryName("assets/../../secret.txt"));
        assertFalse(ProjectImporter.isSafeEntryName("a/../b"));
        assertFalse(ProjectImporter.isSafeEntryName(".."));
    }

    @Test
    public void rejectsAbsoluteAndWindowsPaths() {
        assertFalse(ProjectImporter.isSafeEntryName("/etc/passwd"));
        assertFalse(ProjectImporter.isSafeEntryName("C:/Windows/system32"));
        assertFalse(ProjectImporter.isSafeEntryName("c:file.txt"));
        assertFalse(ProjectImporter.isSafeEntryName("assets\\css\\app.css"));
    }

    @Test
    public void rejectsEmptyAndNulNames() {
        assertFalse(ProjectImporter.isSafeEntryName(null));
        assertFalse(ProjectImporter.isSafeEntryName(""));
        assertFalse(ProjectImporter.isSafeEntryName("bad\0name.html"));
    }

    @Test
    public void detectsRootIndexHtml() throws Exception {
        File dir = Files.createTempDirectory("import-test").toFile();
        assertTrue(new File(dir, "index.html").createNewFile());
        assertEquals("index.html", ProjectImporter.detectEntryPoint(dir));
    }

    @Test
    public void detectsNestedHtmlWhenNoRootIndex() throws Exception {
        File dir = Files.createTempDirectory("import-test").toFile();
        File nested = new File(dir, "site");
        assertTrue(nested.mkdirs());
        assertTrue(new File(nested, "main.html").createNewFile());
        assertEquals("site/main.html", ProjectImporter.detectEntryPoint(dir));
    }

    @Test
    public void fallsBackToIndexHtmlWhenNoHtmlExists() throws Exception {
        File dir = Files.createTempDirectory("import-test").toFile();
        assertEquals("index.html", ProjectImporter.detectEntryPoint(dir));
    }
}
