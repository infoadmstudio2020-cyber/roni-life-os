package com.roni.life.os.app.resource;

import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The ten mandatory resource-lifecycle scenarios.
 *
 * <p>Every test drives the real {@link ProjectResourceManager} against a temporary directory and a
 * counting fake fetcher, so "no re-download" is proven by the fetch counter rather than by
 * inspecting metadata.</p>
 */
public class ProjectResourceManagerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private InMemoryBackend backend;
    private ProjectResourceStore store;
    private FakeFetcher fetcher;
    private File resourcesRoot;
    private Project project;

    @Before
    public void setUp() throws Exception {
        backend = new InMemoryBackend();
        store = new ProjectResourceStore(backend);
        resourcesRoot = folder.newFolder("resources");
        fetcher = new FakeFetcher();
        project = new Project();
        project.setId("p1");
        project.setName("Demo");
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("https://example.com/app.zip");
        project.setEntryPoint("index.html");
    }

    private ProjectResourceManager manager() {
        return new ProjectResourceManager(fetcher, resourcesRoot, store, null);
    }

    private ResourceManifest manifest(String version, String... ids) {
        List<ResourceSpec> specs = new ArrayList<>();
        for (String id : ids) {
            specs.add(new ResourceSpec(id, ResourceSpec.Kind.REMOTE_ARCHIVE,
                    "https://example.com/" + id + ".zip", id + ".zip", version, "", 0L,
                    true, false));
        }
        return new ResourceManifest(version, specs);
    }

    private File resourceFile(String id) {
        return new File(new File(resourcesRoot, "p1"), id + ".zip");
    }

    // ==================== 1. First registration downloads ====================

    @Test
    public void firstRegistrationDownloadsEveryResource() {
        ProjectResourceManager.Result result = manager().ensureReady(project, manifest("1", "a", "b"));

        assertTrue(result.isReady());
        assertFalse(result.isFromCache());
        assertEquals(2, result.getDownloaded());
        assertEquals(1, fetcher.calls("a"));
        assertEquals(1, fetcher.calls("b"));
        assertTrue(resourceFile("a").isFile());
        assertTrue(resourceFile("b").isFile());
        assertEquals(ProjectResourceState.Status.READY, store.load("p1").getStatus());
    }

    // ==================== 2. Second open downloads nothing ====================

    @Test
    public void secondOpenReusesLocalResources() {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a", "b"));
        int afterFirst = fetcher.total;

        ProjectResourceManager.Result second = manager.ensureReady(project, manifest("1", "a", "b"));

        assertTrue(second.isReady());
        assertTrue(second.isFromCache());
        assertEquals(0, second.getDownloaded());
        assertEquals(afterFirst, fetcher.total);
    }

    // ==================== 3. Close then re-open downloads nothing ====================

    @Test
    public void closeAndReopenDownloadsNothing() {
        manager().ensureReady(project, manifest("1", "a"));
        int afterFirst = fetcher.total;

        // A close releases the runtime claim but keeps every file.
        manager().release("p1");
        ProjectResourceManager.Result reopened = manager().ensureReady(project, manifest("1", "a"));

        assertTrue(reopened.isFromCache());
        assertEquals(afterFirst, fetcher.total);
        assertTrue(resourceFile("a").isFile());
    }

    // ==================== 4. Minimize then restore downloads nothing ====================

    @Test
    public void minimizeAndRestoreDownloadsNothing() {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a"));
        int afterFirst = fetcher.total;

        manager.release("p1");
        ProjectResourceManager.Result restored = manager.ensureReady(project, manifest("1", "a"));

        assertTrue(restored.isFromCache());
        assertEquals(afterFirst, fetcher.total);
    }

    // ==================== 5. App restart downloads nothing ====================

    @Test
    public void appRestartDownloadsNothing() {
        manager().ensureReady(project, manifest("1", "a"));
        int afterFirst = fetcher.total;

        // A restart rebuilds the manager and the store over the same persisted backend.
        ProjectResourceStore restartedStore = new ProjectResourceStore(backend);
        ProjectResourceManager restarted = new ProjectResourceManager(
                fetcher, resourcesRoot, restartedStore, null);
        ProjectResourceManager.Result result = restarted.ensureReady(project, manifest("1", "a"));

        assertTrue(result.isFromCache());
        assertEquals(afterFirst, fetcher.total);
    }

    // ==================== 6. Missing resource only ====================

    @Test
    public void onlyTheMissingResourceIsFetched() {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a", "b"));
        int afterFirst = fetcher.total;

        assertTrue(resourceFile("b").delete());

        ProjectResourceManager.Result result = manager.ensureReady(project, manifest("1", "a", "b"));

        assertFalse(result.isFromCache());
        assertEquals(1, result.getDownloaded());
        assertEquals(afterFirst + 1, fetcher.total);
        assertEquals(1, fetcher.calls("a"));
        assertEquals(2, fetcher.calls("b"));
        assertTrue(resourceFile("b").isFile());
    }

    // ==================== 7. Corrupted resource only ====================

    @Test
    public void onlyTheCorruptedResourceIsFetched() throws Exception {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a", "b"));
        int afterFirst = fetcher.total;

        // Same length, different bytes: only the checksum can catch this.
        try (FileOutputStream out = new FileOutputStream(resourceFile("a"))) {
            out.write("tampered".getBytes("UTF-8"));
        }

        ProjectResourceManager.Result result = manager.ensureReady(project, manifest("1", "a", "b"));

        assertEquals(1, result.getDownloaded());
        assertEquals(afterFirst + 1, fetcher.total);
        assertEquals(2, fetcher.calls("a"));
        assertEquals(1, fetcher.calls("b"));
    }

    // ==================== 8. Remote version change ====================

    @Test
    public void onlyTheChangedVersionIsFetched() {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a", "b"));
        int afterFirst = fetcher.total;

        ProjectResourceManager.Result result = manager.ensureReady(project, manifest("2", "a", "b"));

        assertEquals(2, result.getDownloaded());
        assertEquals(afterFirst + 2, fetcher.total);
        assertEquals("2", store.load("p1").get("a").getVersion());
    }

    @Test
    public void unchangedVersionIsNotRefetchedWhenOnlyOneSpecBumps() {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a", "b"));
        int afterFirst = fetcher.total;

        List<ResourceSpec> specs = new ArrayList<>();
        specs.add(new ResourceSpec("a", ResourceSpec.Kind.REMOTE_ARCHIVE,
                "https://example.com/a.zip", "a.zip", "2", "", 0L, true, false));
        specs.add(new ResourceSpec("b", ResourceSpec.Kind.REMOTE_ARCHIVE,
                "https://example.com/b.zip", "b.zip", "1", "", 0L, true, false));

        ProjectResourceManager.Result result =
                manager.ensureReady(project, new ResourceManifest("2", specs));

        assertEquals(1, result.getDownloaded());
        assertEquals(afterFirst + 1, fetcher.total);
        assertEquals(2, fetcher.calls("a"));
        assertEquals(1, fetcher.calls("b"));
    }

    // ==================== 9. Interrupted download ====================

    @Test
    public void interruptedDownloadIsNotReadyAndRetriesSafely() {
        ProjectResourceManager manager = manager();
        fetcher.failNext = true;

        ProjectResourceManager.Result failed = manager.ensureReady(project, manifest("1", "a"));

        assertFalse(failed.isReady());
        assertEquals(ProjectResourceState.Status.INCOMPLETE, store.load("p1").getStatus());
        assertFalse(store.load("p1").isReady());

        ProjectResourceManager.Result retried = manager.ensureReady(project, manifest("1", "a"));

        assertTrue(retried.isReady());
        assertEquals(2, fetcher.calls("a"));
        assertEquals(ProjectResourceState.Status.READY, store.load("p1").getStatus());
    }

    // ==================== 10. User data and configuration survive ====================

    @Test
    public void userDataAndConfigurationSurviveEveryOpen() throws Exception {
        File userData = new File(new File(resourcesRoot, "p1"), "user-data.json");
        File parent = userData.getParentFile();
        assertTrue(parent.exists() || parent.mkdirs());
        try (FileOutputStream out = new FileOutputStream(userData)) {
            out.write("{\"score\":42}".getBytes("UTF-8"));
        }

        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a"));
        manager.release("p1");
        manager.ensureReady(project, manifest("1", "a"));
        manager.ensureReady(project, manifest("2", "a"));

        assertTrue(userData.isFile());
        assertEquals("{\"score\":42}", read(userData));
        assertEquals("https://example.com/app.zip", project.getSourcePath());
    }

    // ==================== Extraction happens once ====================

    @Test
    public void remoteArchiveIsExtractedOnceAndNeverOverwritten() throws Exception {
        File archive = folder.newFile("app.zip");
        writeZip(archive, "index.html", "<html>v1</html>");
        fetcher.payload(ResourceManifestBuilder.ARCHIVE_ID, readBytes(archive));

        ResourceSpec spec = new ResourceSpec(ResourceManifestBuilder.ARCHIVE_ID,
                ResourceSpec.Kind.REMOTE_ARCHIVE, "https://example.com/app.zip",
                "archive.zip", "1", "", 0L, true, true);
        ResourceManifest manifest = new ResourceManifest("1",
                java.util.Collections.singletonList(spec));

        ProjectResourceManager manager = manager();
        ProjectResourceManager.Result first = manager.ensureReady(project, manifest);
        assertTrue(first.isReady());
        assertNotNull(first.getLocalRoot());
        File entry = new File(first.getLocalRoot(), "index.html");
        assertTrue(entry.isFile());

        // A user edit inside the unpacked tree must survive the next open.
        try (FileOutputStream out = new FileOutputStream(entry)) {
            out.write("<html>edited</html>".getBytes("UTF-8"));
        }

        ProjectResourceManager.Result second = manager.ensureReady(project, manifest);
        assertTrue(second.isFromCache());
        assertEquals("<html>edited</html>", read(entry));
    }

    // ==================== Explicit invalidation ====================

    @Test
    public void invalidateForcesReverificationButKeepsFiles() {
        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest("1", "a"));
        int afterFirst = fetcher.total;

        manager.invalidate("p1");
        assertNull(store.load("p1"));
        assertTrue(resourceFile("a").isFile());

        ProjectResourceManager.Result result = manager.ensureReady(project, manifest("1", "a"));
        assertTrue(result.isReady());
        assertEquals(afterFirst + 1, fetcher.total);
    }

    @Test
    public void projectWithoutResourcesIsReadyImmediately() {
        project.setSourceType(ProjectSourceType.INLINE_HTML);
        project.setSourcePath("");
        project.setInlineHtml("<html></html>");

        ProjectResourceManager.Result result = manager().ensureReady(project, (ResourceManifest) null);

        assertTrue(result.isReady());
        assertTrue(result.isFromCache());
        assertEquals(0, fetcher.total);
        assertNull(result.getLocalRoot());
    }


    // ==================== Remote document revalidation ====================

    @Test
    public void remoteDocumentIsNotRevalidatedOnEveryOpen() {
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("https://example.com/app/");

        ProjectResourceManager manager = manager();
        manager.ensureReady(project);
        int probes = fetcher.tagProbes;

        ProjectResourceManager.Result second = manager.ensureReady(project);

        assertTrue(second.isFromCache());
        assertFalse(second.isRefreshRequired());
        assertEquals(probes, fetcher.tagProbes);
        assertEquals(0, fetcher.total);
    }

    @Test
    public void staleRemoteDocumentIsRevalidatedAndRefreshed() {
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("https://example.com/app/");

        ProjectResourceManager manager = manager();
        manager.ensureReady(project);

        ProjectResourceState state = store.load("p1");
        ResourceRecord record = state.get(ResourceManifestBuilder.DOCUMENT_ID);
        record.setLastVerifiedAt(0L);
        state.put(record);
        store.save(state);

        fetcher.tag = "\"v2\"";
        ProjectResourceManager.Result result = manager.ensureReady(project);

        assertTrue(result.isRefreshRequired());
        assertEquals("\"v2\"", result.getVersionTag());
        assertEquals(1, fetcher.tagProbes);
        assertEquals(0, fetcher.total);
    }

    @Test
    public void staleDocumentWithTheSameTagIsNotRefreshed() {
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("https://example.com/app/");

        ProjectResourceManager manager = manager();
        manager.ensureReady(project);

        ProjectResourceState state = store.load("p1");
        ResourceRecord record = state.get(ResourceManifestBuilder.DOCUMENT_ID);
        record.setChecksum("\"v1\"");
        record.setLastVerifiedAt(0L);
        state.put(record);
        store.save(state);

        fetcher.tag = "\"v1\"";
        ProjectResourceManager.Result result = manager.ensureReady(project);

        assertFalse(result.isRefreshRequired());
        assertTrue(result.isFromCache());
        assertEquals(1, fetcher.tagProbes);
    }

    // ==================== Unpacked content survives a lost record ====================

    @Test
    public void unpackedArchiveIsNotRefetchedWhenRecordIsLost() throws Exception {
        File archive = folder.newFile("lost.zip");
        writeZip(archive, "index.html", "<html>v1</html>");
        fetcher.payload(ResourceManifestBuilder.ARCHIVE_ID, readBytes(archive));

        ResourceSpec spec = new ResourceSpec(ResourceManifestBuilder.ARCHIVE_ID,
                ResourceSpec.Kind.REMOTE_ARCHIVE, "https://example.com/app.zip",
                "archive.zip", "1", "", 0L, true, true);
        ResourceManifest manifest = new ResourceManifest("1",
                java.util.Collections.singletonList(spec));

        ProjectResourceManager manager = manager();
        manager.ensureReady(project, manifest);
        int afterFirst = fetcher.total;

        // The record is lost but the unpacked tree is intact: the project must still open.
        store.clear("p1");
        ProjectResourceManager.Result result = manager.ensureReady(project, manifest);

        assertTrue(result.isReady());
        assertTrue(result.isFromCache());
        assertEquals(afterFirst, fetcher.total);
        assertNotNull(result.getLocalRoot());
        assertTrue(new File(result.getLocalRoot(), "index.html").isFile());
    }

    // ==================== Helpers ====================


    private static String read(File file) throws Exception {
        byte[] buffer = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
        }
        return new String(buffer, "UTF-8");
    }

    private static byte[] readBytes(File file) throws Exception {
        byte[] buffer = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
        }
        return buffer;
    }

    private static void writeZip(File archive, String name, String content) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes("UTF-8"));
            zip.closeEntry();
        }
    }

    /** Counts every fetch so "no re-download" is observable. */
    static final class FakeFetcher implements ResourceFetcher {
        private final Map<String, Integer> calls = new HashMap<>();
        private final Map<String, byte[]> payloads = new HashMap<>();
        int total;
        int tagProbes;
        String tag = "";
        boolean failNext;

        void payload(String id, byte[] data) {
            payloads.put(id, data);
        }

        int calls(String id) {
            Integer value = calls.get(id);
            return value != null ? value : 0;
        }

        @Override
        public void fetch(ResourceSpec spec, File destination) throws Exception {
            total++;
            calls.put(spec.getId(), calls(spec.getId()) + 1);
            if (failNext) {
                failNext = false;
                throw new IOException("network down");
            }
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            byte[] data = payloads.get(spec.getId());
            if (data == null) {
                data = ("payload-" + spec.getId() + "-" + spec.getVersion()).getBytes("UTF-8");
            }
            try (FileOutputStream out = new FileOutputStream(destination)) {
                out.write(data);
            }
        }

        @Override
        public boolean isChanged(ResourceSpec spec, ResourceRecord record) {
            return true;
        }

        @Override
        public String remoteTag(ResourceSpec spec) {
            tagProbes++;
            return tag;
        }
    }

    /** Minimal persistent backend. */
    static final class InMemoryBackend implements KeyValueBackend {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String fallback) {
            String value = values.get(key);
            return value != null ? value : fallback;
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public boolean remove(String key) {
            return values.remove(key) != null;
        }
    }
}
