package com.roni.life.os.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies the storage contract: the required tree, the completeness check and the fact that a
 * partially built tree is reported instead of silently accepted.
 */
public class StorageSpecTest {

    private File root;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("ronilifeos-spec").toFile();
    }

    @After
    public void tearDown() {
        delete(root);
    }

    @Test
    public void requiredPaths_areUnique() {
        Set<String> seen = new HashSet<>();
        for (String path : StorageSpec.REQUIRED_DIRS) {
            assertTrue("duplicate directory: " + path, seen.add(path));
        }
        seen.clear();
        for (String path : StorageSpec.REQUIRED_FILES) {
            assertTrue("duplicate file: " + path, seen.add(path));
        }
    }

    @Test
    public void requiredPaths_useForwardSlashesOnly() {
        for (String path : StorageSpec.REQUIRED_DIRS) {
            assertFalse(path, path.contains("\\"));
            assertFalse(path, path.startsWith("/"));
        }
        for (String path : StorageSpec.REQUIRED_FILES) {
            assertFalse(path, path.contains("\\"));
            assertFalse(path, path.startsWith("/"));
        }
    }

    @Test
    public void emptyRoot_reportsEveryRequiredPath() {
        List<String> dirs = StorageSpec.missingDirectories(root);
        List<String> files = StorageSpec.missingFiles(root);

        assertEquals(StorageSpec.REQUIRED_DIRS.length, dirs.size());
        assertEquals(StorageSpec.REQUIRED_FILES.length, files.size());
        assertFalse(StorageSpec.isComplete(root));
    }

    @Test
    public void completeTree_isAccepted() throws IOException {
        for (String path : StorageSpec.REQUIRED_DIRS) {
            assertTrue(new File(root, path).mkdirs());
        }
        for (String path : StorageSpec.REQUIRED_FILES) {
            File file = new File(root, path);
            assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
            assertTrue(file.createNewFile());
            Files.write(file.toPath(), "{}".getBytes("UTF-8"));
        }

        assertTrue(StorageSpec.missingDirectories(root).isEmpty());
        assertTrue(StorageSpec.missingFiles(root).isEmpty());
        assertTrue(StorageSpec.isComplete(root));
    }

    @Test
    public void emptyFile_countsAsMissing() throws IOException {
        for (String path : StorageSpec.REQUIRED_DIRS) {
            assertTrue(new File(root, path).mkdirs());
        }
        for (String path : StorageSpec.REQUIRED_FILES) {
            File file = new File(root, path);
            assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
            assertTrue(file.createNewFile());
        }
        // Every file exists but is zero-length: the tree is not usable yet.
        assertEquals(StorageSpec.REQUIRED_FILES.length, StorageSpec.missingFiles(root).size());
        assertFalse(StorageSpec.isComplete(root));
    }

    @Test
    public void deletedDirectory_isReportedForRecovery() throws IOException {
        for (String path : StorageSpec.REQUIRED_DIRS) {
            assertTrue(new File(root, path).mkdirs());
        }
        for (String path : StorageSpec.REQUIRED_FILES) {
            File file = new File(root, path);
            assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
            assertTrue(file.createNewFile());
            Files.write(file.toPath(), "{}".getBytes("UTF-8"));
        }
        assertTrue(StorageSpec.isComplete(root));

        assertTrue(new File(root, "runtime/logs").delete());

        List<String> missing = StorageSpec.missingDirectories(root);
        assertEquals(1, missing.size());
        assertEquals("runtime/logs", missing.get(0));
    }

    @Test
    public void nullRoot_isNeverComplete() {
        assertFalse(StorageSpec.isComplete(null));
        assertEquals(StorageSpec.REQUIRED_DIRS.length, StorageSpec.missingDirectories(null).size());
        assertEquals(StorageSpec.REQUIRED_FILES.length, StorageSpec.missingFiles(null).size());
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
