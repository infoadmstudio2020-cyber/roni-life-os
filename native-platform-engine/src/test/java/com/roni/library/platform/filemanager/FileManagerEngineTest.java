package com.roni.library.platform.filemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.roni.library.contracts.filemanager.ArchiveEntry;
import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.SearchQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** JVM tests for the file manager engine: paths, listing, operations, isolation, archives. */
public final class FileManagerEngineTest {

    private File root;
    private FileManagerRepository repository;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("filemanager-test").toFile();
        repository = new FileManagerRepository(root, null);
    }

    @After
    public void tearDown() {
        deleteRecursively(root);
        FileManagerProjectContext.clear();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ------------------------------------------------------------------
    // path policy
    // ------------------------------------------------------------------

    @Test
    public void normalizeRejectsTraversalAndAbsolutePaths() {
        assertRejected("../escape");
        assertRejected("a/../../escape");
        assertRejected("/etc/passwd");
        assertRejected("C:/windows");
        assertRejected("con");
        assertRejected("bad\u0000name");
    }

    private void assertRejected(String path) {
        try {
            FileManagerPaths.normalize(path);
            fail("Expected rejection for " + path);
        } catch (FileManagerException expected) {
            assertTrue(expected.getCode().equals(FileManagerErrorCodes.INVALID_PATH)
                    || expected.getCode().equals(FileManagerErrorCodes.PATH_TRAVERSAL));
        }
    }

    @Test
    public void normalizeAcceptsNestedRelativePaths() throws Exception {
        assertEquals("docs/report.txt", FileManagerPaths.normalize("docs/report.txt"));
        assertEquals("docs/report.txt", FileManagerPaths.normalize("./docs//report.txt"));
        assertEquals("", FileManagerPaths.normalize(""));
    }

    @Test
    public void projectIdIsRequired() {
        try {
            FileManagerPaths.requireProjectId("   ");
            fail("Blank project id must be rejected");
        } catch (FileManagerException expected) {
            assertEquals(FileManagerErrorCodes.NO_ACTIVE_PROJECT, expected.getCode());
        }
    }

    // ------------------------------------------------------------------
    // listing and metadata
    // ------------------------------------------------------------------

    @Test
    public void createListAndSortEntries() throws Exception {
        repository.createFolder("p1", "docs");
        repository.createFile("p1", "docs/notes.txt", "hello");
        repository.createFile("p1", "alpha.txt", "a");
        repository.createFile("p1", "zeta.txt", "zzz");

        List<FileEntry> entries = repository.list("p1", "", false,
                SearchQuery.SORT_NAME, false, 0, 50);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).isDirectory());
        assertEquals("alpha.txt", entries.get(1).getName());
        assertEquals("zeta.txt", entries.get(2).getName());

        List<FileEntry> descending = repository.list("p1", "", false,
                SearchQuery.SORT_NAME, true, 0, 50);
        assertEquals("zeta.txt", descending.get(1).getName());

        FileEntry notes = repository.entry("p1", "docs/notes.txt");
        assertEquals("text/plain", notes.getMimeType());
        assertEquals(FileCategory.DOCUMENT, notes.getCategory());
        assertEquals(5L, notes.getSizeBytes());
    }

    @Test
    public void hiddenFilesAreFilteredByDefault() throws Exception {
        repository.createFile("p1", ".secret", "x");
        repository.createFile("p1", "visible.txt", "y");
        assertEquals(1, repository.list("p1", "", false,
                SearchQuery.SORT_NAME, false, 0, 50).size());
        assertEquals(2, repository.list("p1", "", true,
                SearchQuery.SORT_NAME, false, 0, 50).size());
    }

    @Test
    public void paginationLimitsResults() throws Exception {
        for (int i = 0; i < 10; i++) {
            repository.createFile("p1", "file" + i + ".txt", "x");
        }
        List<FileEntry> page = repository.list("p1", "", false,
                SearchQuery.SORT_NAME, false, 3, 4);
        assertEquals(4, page.size());
        assertEquals("file3.txt", page.get(0).getName());
    }

    // ------------------------------------------------------------------
    // operations
    // ------------------------------------------------------------------

    @Test
    public void renameCopyMoveDelete() throws Exception {
        repository.createFile("p1", "a.txt", "content");
        repository.rename("p1", "a.txt", "b.txt");
        assertTrue(repository.entry("p1", "b.txt").getSizeBytes() > 0);

        repository.createFolder("p1", "target");
        repository.copy("p1", "b.txt", "target/b.txt", null);
        assertEquals("content", new String(repository.read("p1", "target/b.txt", 1024),
                StandardCharsets.UTF_8));

        repository.move("p1", "target/b.txt", "target/moved.txt", null);
        assertTrue(repository.entry("p1", "target/moved.txt").getSizeBytes() > 0);

        repository.delete("p1", "target");
        try {
            repository.entry("p1", "target");
            fail("Deleted folder must not resolve");
        } catch (FileManagerException expected) {
            assertEquals(FileManagerErrorCodes.NOT_FOUND, expected.getCode());
        }
    }

    @Test
    public void copyIntoItselfIsRejected() throws Exception {
        repository.createFolder("p1", "docs");
        try {
            repository.copy("p1", "docs", "docs", null);
            fail("Copying a folder into itself must fail");
        } catch (FileManagerException expected) {
            assertEquals(FileManagerErrorCodes.INVALID_PATH, expected.getCode());
        }
    }

    @Test
    public void duplicateNamesAreResolvedWithoutOverwrite() throws Exception {
        repository.createFile("p1", "a.txt", "one");
        repository.createFolder("p1", "target");
        repository.copy("p1", "a.txt", "target/a.txt", null);
        try {
            repository.copy("p1", "a.txt", "target/a.txt", null);
            fail("Existing targets must never be overwritten");
        } catch (FileManagerException expected) {
            assertEquals(FileManagerErrorCodes.ALREADY_EXISTS, expected.getCode());
        }
        assertEquals("one", new String(repository.read("p1", "target/a.txt", 64),
                StandardCharsets.UTF_8));
    }

    @Test
    public void writeRejectsOversizedPayload() throws Exception {
        try {
            repository.write("p1", "big.bin", new byte[64], 16);
            fail("Oversized write must be rejected");
        } catch (FileManagerException expected) {
            assertEquals(FileManagerErrorCodes.FILE_TOO_LARGE, expected.getCode());
        }
    }

    // ------------------------------------------------------------------
    // isolation
    // ------------------------------------------------------------------

    @Test
    public void projectsCannotSeeEachOther() throws Exception {
        repository.createFile("p1", "private.txt", "p1");
        repository.createFile("p2", "private.txt", "p2");
        assertEquals("p1", new String(repository.read("p1", "private.txt", 64),
                StandardCharsets.UTF_8));
        assertEquals("p2", new String(repository.read("p2", "private.txt", 64),
                StandardCharsets.UTF_8));
        assertEquals(1, repository.list("p1", "", false,
                SearchQuery.SORT_NAME, false, 0, 20).size());
    }

    @Test
    public void traversalCannotEscapeProjectRoot() throws Exception {
        repository.createFile("p1", "safe.txt", "x");
        try {
            repository.read("p1", "../../p2/private.txt", 64);
            fail("Traversal must be rejected");
        } catch (FileManagerException expected) {
            assertTrue(expected.getCode().equals(FileManagerErrorCodes.INVALID_PATH)
                    || expected.getCode().equals(FileManagerErrorCodes.PATH_TRAVERSAL));
        }
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @Test
    public void searchFiltersByNameExtensionAndCategory() throws Exception {
        repository.createFile("p1", "docs/report.pdf", "pdf");
        repository.createFile("p1", "docs/notes.txt", "txt");
        repository.createFile("p1", "images/photo.png", "png");

        assertEquals(1, repository.search("p1", "",
                new SearchQuery.Builder().name("report").build()).size());
        assertEquals(1, repository.search("p1", "",
                new SearchQuery.Builder().extension("txt").build()).size());
        assertEquals(1, repository.search("p1", "",
                new SearchQuery.Builder().extension("pdf").build()).size());
        assertEquals(1, repository.search("p1", "",
                new SearchQuery.Builder().category(FileCategory.IMAGE).build()).size());
        assertEquals(1, repository.search("p1", "docs",
                new SearchQuery.Builder().name("notes").build()).size());
    }

    // ------------------------------------------------------------------
    // archives
    // ------------------------------------------------------------------

    @Test
    public void archiveCreateListAndExtract() throws Exception {
        repository.createFile("p1", "docs/a.txt", "alpha");
        repository.createFile("p1", "docs/b.txt", "beta");
        ArchiveManager archives = new ArchiveManager(repository);

        FileEntry archive = archives.create("p1", Arrays.asList("docs"), "bundle.zip", null);
        assertTrue(archive.getSizeBytes() > 0);

        List<ArchiveEntry> entries = archives.list("p1", "bundle.zip");
        assertEquals(2, entries.size());

        int extracted = archives.extract("p1", "bundle.zip", "out", null, null);
        assertEquals(2, extracted);
        assertEquals("alpha", new String(repository.read("p1", "out/docs/a.txt", 64),
                StandardCharsets.UTF_8));
    }

    @Test
    public void zipSlipEntriesAreRejected() throws Exception {
        File projectDir = repository.projectDir("p1");
        File evil = new File(projectDir, "evil.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(evil))) {
            out.putNextEntry(new ZipEntry("../../escaped.txt"));
            out.write("pwned".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        ArchiveManager archives = new ArchiveManager(repository);
        try {
            archives.extract("p1", "evil.zip", "out", null, null);
            fail("ZIP Slip entry must be rejected");
        } catch (FileManagerException expected) {
            assertEquals(FileManagerErrorCodes.ARCHIVE_SLIP, expected.getCode());
        }
        assertFalse(new File(root, "escaped.txt").exists());
    }

    // ------------------------------------------------------------------
    // analyzer
    // ------------------------------------------------------------------

    @Test
    public void analyzerFindsLargeOldEmptyAndDuplicates() throws Exception {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            large.append('x');
        }
        repository.createFile("p1", "big.bin", large.toString());
        repository.createFile("p1", "copy1.txt", "same-content");
        repository.createFile("p1", "copy2.txt", "same-content");
        repository.createFolder("p1", "empty");
        StorageAnalyzer analyzer = new StorageAnalyzer(repository);

        assertTrue(analyzer.info("p1").getProjectBytes() > 0);
        assertEquals(1, analyzer.largeFiles("p1", 50, 10).size());
        assertEquals(1, analyzer.emptyFolders("p1").size());
        List<List<FileEntry>> duplicates = analyzer.duplicates("p1", 10);
        assertEquals(1, duplicates.size());
        assertEquals(2, duplicates.get(0).size());
    }

    @Test
    public void favoritesAndRecentAreProjectScoped() throws Exception {
        repository.createFile("p1", "a.txt", "a");
        repository.addFavorite("p1", "a.txt");
        repository.recordRecent("p1", "a.txt");
        assertEquals(1, repository.favorites("p1").size());
        assertEquals(1, repository.recent("p1", 10).size());
        assertEquals(0, repository.favorites("p2").size());
        repository.removeFavorite("p1", "a.txt");
        assertEquals(0, repository.favorites("p1").size());
    }

    @Test
    public void operationRegistryTracksProgressAndCancellation() throws Exception {
        FileOperationRegistry registry = new FileOperationRegistry();
        String id = registry.start("COPY", 100, 2);
        assertNotNull(registry.get(id));
        registry.progress(id, 50, 1);
        assertEquals(50, registry.get(id).getProgressPercent());
        registry.cancel(id);
        assertTrue(registry.isCancelled(id));
        registry.complete(id, "done");
        assertTrue(registry.get(id).isFinished());
    }

    @Test
    public void batchDeleteRemovesAllTargets() throws Exception {
        repository.createFile("p1", "one.txt", "1");
        repository.createFile("p1", "two.txt", "2");
        List<String> paths = new ArrayList<>(Arrays.asList("one.txt", "two.txt"));
        for (String path : paths) {
            repository.delete("p1", path);
        }
        assertEquals(0, repository.list("p1", "", false,
                SearchQuery.SORT_NAME, false, 0, 20).size());
    }
}
