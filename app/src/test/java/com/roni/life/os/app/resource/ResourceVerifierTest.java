package com.roni.life.os.app.resource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Real-file verification: metadata is never trusted on its own. */
public class ResourceVerifierTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File write(String name, String content) throws Exception {
        File file = folder.newFile(name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes("UTF-8"));
        }
        return file;
    }

    @Test
    public void existingFileWithMatchingSizeIsOk() throws Exception {
        File file = write("a.bin", "hello");
        assertEquals(ResourceVerifier.Result.OK,
                ResourceVerifier.verify(file, file.length(), ""));
    }

    @Test
    public void missingFileIsReported() throws Exception {
        File file = new File(folder.getRoot(), "nope.bin");
        assertEquals(ResourceVerifier.Result.MISSING,
                ResourceVerifier.verify(file, 0L, ""));
    }

    @Test
    public void directoryIsNotAFile() throws Exception {
        File dir = folder.newFolder("dir");
        assertEquals(ResourceVerifier.Result.NOT_A_FILE,
                ResourceVerifier.verify(dir, 0L, ""));
    }

    @Test
    public void sizeMismatchIsDetected() throws Exception {
        File file = write("b.bin", "hello");
        assertEquals(ResourceVerifier.Result.SIZE_MISMATCH,
                ResourceVerifier.verify(file, file.length() + 10, ""));
    }

    @Test
    public void checksumMismatchIsDetected() throws Exception {
        File file = write("c.bin", "hello");
        assertEquals(ResourceVerifier.Result.CHECKSUM_MISMATCH,
                ResourceVerifier.verify(file, file.length(), "deadbeef"));
    }

    @Test
    public void matchingChecksumIsOk() throws Exception {
        File file = write("d.bin", "hello");
        String digest = ResourceVerifier.sha256(file);
        assertNotNull(digest);
        assertEquals(64, digest.length());
        assertEquals(ResourceVerifier.Result.OK,
                ResourceVerifier.verify(file, file.length(), digest));
    }

    @Test
    public void checksumIsStableAndContentSensitive() throws Exception {
        File first = write("e.bin", "hello");
        File second = write("f.bin", "hello");
        File third = write("g.bin", "hello!");
        assertEquals(ResourceVerifier.sha256(first), ResourceVerifier.sha256(second));
        assertTrue(!ResourceVerifier.sha256(first).equals(ResourceVerifier.sha256(third)));
    }

    @Test
    public void sha256OfMissingFileIsNull() {
        assertNull(ResourceVerifier.sha256(new File(folder.getRoot(), "absent")));
    }
}
