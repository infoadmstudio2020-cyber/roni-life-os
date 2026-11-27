package com.roni.library.security.crypto;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Encryption at rest for project files: key management and tree operations.
 *
 * <p>Each project gets its own AES-256 key in the Android Keystore, aliased by project id, so a key
 * never leaves the secure element and one project's key cannot open another's files. The byte
 * format and the actual encryption live in {@link VaultCipher}, which has no Android dependency and
 * is therefore testable on a plain JVM.</p>
 *
 * <p>Two formats are readable, one is written — see {@link VaultFormat}. v2 authenticates the
 * project id, the normalised relative path, the format version, the key generation and the chunk
 * index, and splits the body into independently tagged chunks so a large resource can be streamed
 * without ever releasing unauthenticated plaintext.</p>
 *
 * <p>Keystore-backed AES/GCM needs API 23. Below that {@link #isAvailable()} returns false and
 * callers must fall back to plain storage rather than silently writing unencrypted data under an
 * encrypted name.</p>
 */
public final class ProjectVault {

    private static final String TAG = "ProjectVault";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS_PREFIX = "ronilifeos.project.";
    private static final String GENERATION_SUFFIX = ".g";

    /** Marker written once a tree has been fully sealed. Its presence means "do not seal again". */
    public static final String MARKER_SEALED = ".vault";

    /**
     * Marker written <em>before</em> the first file is encrypted.
     *
     * <p>Present without {@link #MARKER_SEALED} means the pass was interrupted: the tree is part
     * plaintext and part ciphertext, and there is no record of which file is which. Such a tree
     * must be discarded, never resumed and never served.</p>
     */
    public static final String MARKER_PENDING = ".vault.pending";

    private static final int MARKER_READ_LIMIT = 512;

    private ProjectVault() {
    }

    /** @return true when Keystore-backed AES/GCM can be used on this device */
    public static boolean isAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    // ==================== crypto, delegated ====================

    /** Encrypts {@code plain} as a v2 blob bound to {@code path}. */
    public static byte[] seal(String projectId, String path, int generation, byte[] plain)
            throws GeneralSecurityException, IOException {
        requireAvailable();
        requireProjectId(projectId);
        return VaultCipher.seal(projectId, path, generation, keyFor(projectId, generation), plain);
    }

    /** Decrypts a v2 blob. Falls back to the legacy format when the magic is absent. */
    public static byte[] open(String projectId, String path, int generation, byte[] sealed)
            throws GeneralSecurityException, IOException {
        requireAvailable();
        requireProjectId(projectId);
        return VaultCipher.open(projectId, path, generation, keyFor(projectId, generation), sealed);
    }

    /** Decrypts a v1 blob: single tag, AAD is the project id alone. */
    public static byte[] openLegacy(String projectId, byte[] sealed)
            throws GeneralSecurityException, IOException {
        requireAvailable();
        requireProjectId(projectId);
        return VaultCipher.openLegacy(projectId, keyFor(projectId, 0), sealed);
    }

    /** Encrypts {@code plain} into {@code out} as a v2 blob, one chunk at a time. */
    public static void sealStream(String projectId, String path, int generation,
                                  InputStream plain, long length, OutputStream out)
            throws GeneralSecurityException, IOException {
        requireAvailable();
        requireProjectId(projectId);
        VaultCipher.sealStream(projectId, path, generation, keyFor(projectId, generation),
                plain, length, out);
    }

    /** Opens a v2 sealed stream. The caller owns the returned stream. */
    public static VaultCipher.SealedStream openStream(String projectId, String path, int generation,
                                                      InputStream sealed)
            throws GeneralSecurityException, IOException {
        requireAvailable();
        requireProjectId(projectId);
        return VaultCipher.openStream(projectId, path, generation, keyFor(projectId, generation),
                sealed);
    }

    // ==================== files ====================

    /**
     * Seals {@code source} into {@code target} without buffering the whole file.
     *
     * <p>Writes to {@code <target>.sealing}, syncs, then renames. A crash mid-write leaves the
     * temporary file behind and the original untouched, so a single file can never end up
     * truncated — which the tree-level pending marker alone could not guarantee.</p>
     */
    public static void sealFile(String projectId, String path, int generation,
                                File source, File target)
            throws GeneralSecurityException, IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Missing file " + source);
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent);
        }
        File temp = new File(parent, target.getName() + ".sealing");
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(temp);
        try {
            sealStream(projectId, path, generation, in, source.length(), out);
            out.flush();
            out.getFD().sync();
        } catch (Throwable error) {
            closeQuietly(out);
            closeQuietly(in);
            deleteQuietly(temp);
            throw asIoOrSecurity(error, "Sealing failed for " + source);
        } finally {
            closeQuietly(out);
            closeQuietly(in);
        }
        replace(temp, target);
    }

    /** Seals an in-memory blob into {@code target}. */
    public static void sealFile(String projectId, String path, int generation,
                                byte[] plain, File target)
            throws GeneralSecurityException, IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent);
        }
        File temp = new File(parent, target.getName() + ".sealing");
        FileOutputStream out = new FileOutputStream(temp);
        try {
            byte[] data = plain != null ? plain : new byte[0];
            sealStream(projectId, path, generation, new java.io.ByteArrayInputStream(data),
                    data.length, out);
            out.flush();
            out.getFD().sync();
        } catch (Throwable error) {
            closeQuietly(out);
            deleteQuietly(temp);
            throw asIoOrSecurity(error, "Sealing failed for " + target);
        } finally {
            closeQuietly(out);
        }
        replace(temp, target);
    }

    /** Reads and decrypts a sealed file. Bounded by {@link VaultLimits#getMaxResourceBytes()}. */
    public static byte[] openFile(String projectId, String path, int generation, File file)
            throws GeneralSecurityException, IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Missing file " + file);
        }
        if (file.length() > VaultLimits.getMaxResourceBytes() + VaultFormat.HEADER_BYTES
                + VaultFormat.TAG_BYTES * 2L) {
            throw new GeneralSecurityException("Sealed file exceeds the resource limit");
        }
        return open(projectId, path, generation, readAll(file));
    }

    // ==================== trees ====================

    /**
     * Seals every file under {@code root} in place, keeping the directory shape identical so the
     * resource provider needs no path changes.
     *
     * <p>Idempotent twice over: the {@code .vault} marker short-circuits the whole tree, and each
     * individual file is skipped when it already carries the v2 magic. The second check is what
     * makes a lost marker harmless — without it, a re-run would encrypt ciphertext.</p>
     */
    public static void sealTree(String projectId, File root)
            throws GeneralSecurityException, IOException {
        sealTree(projectId, root, currentGeneration(root));
    }

    public static void sealTree(String projectId, File root, int generation)
            throws GeneralSecurityException, IOException {
        if (root == null || !root.isDirectory()) {
            throw new IOException("Not a directory: " + root);
        }
        requireProjectId(projectId);
        if (isSealed(root)) {
            return;
        }
        sealDirectory(projectId, root, root, generation);
        writeMarker(root, generation);
    }

    private static void sealDirectory(String projectId, File root, File dir, int generation)
            throws GeneralSecurityException, IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                sealDirectory(projectId, root, child, generation);
            } else if (child.isFile() && !isMarker(child.getName())) {
                if (isSealedFile(child)) {
                    continue;
                }
                sealFile(projectId, relativePath(root, child), generation, child, child);
            }
        }
    }

    /** @return true when {@code file} already carries the v2 magic */
    public static boolean isSealedFile(File file) {
        if (file == null || !file.isFile() || file.length() < VaultFormat.HEADER_BYTES) {
            return false;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] head = new byte[VaultFormat.MAGIC.length];
            int read = 0;
            while (read < head.length) {
                int step = in.read(head, read, head.length - read);
                if (step < 0) {
                    return false;
                }
                read += step;
            }
            return VaultFormat.hasMagic(head);
        } catch (IOException error) {
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    /** @return true when {@code root} has already been sealed */
    public static boolean isSealed(File root) {
        return root != null && new File(root, MARKER_SEALED).isFile();
    }

    /**
     * @return true when a sealing pass started but never finished
     *
     * <p>A pending marker with no sealed marker is the signature of an interrupted pass. The tree
     * cannot be resumed (there is no per-file record) and cannot be served (half the bytes are
     * ciphertext), so callers must discard it.</p>
     */
    public static boolean isSealInterrupted(File root) {
        return root != null
                && new File(root, MARKER_PENDING).isFile()
                && !new File(root, MARKER_SEALED).isFile();
    }

    /** @return the key generation recorded in the {@code .vault} marker, or 0 */
    public static int currentGeneration(File root) {
        if (root == null) {
            return 0;
        }
        File marker = new File(root, MARKER_SEALED);
        if (!marker.isFile()) {
            return 0;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(marker);
            byte[] buffer = new byte[MARKER_READ_LIMIT];
            int read = 0;
            while (read < buffer.length) {
                int step = in.read(buffer, read, buffer.length - read);
                if (step < 0) {
                    break;
                }
                read += step;
            }
            String text = new String(buffer, 0, read, "UTF-8");
            int at = text.indexOf("gen=");
            if (at < 0) {
                return 0;
            }
            int end = at + 4;
            while (end < text.length() && Character.isDigit(text.charAt(end))) {
                end++;
            }
            if (end == at + 4) {
                return 0;
            }
            return Integer.parseInt(text.substring(at + 4, end));
        } catch (Throwable error) {
            return 0;
        } finally {
            closeQuietly(in);
        }
    }

    /** Writes the {@code .vault} marker, recording the generation and the format version. */
    public static void writeMarker(File root, int generation) throws IOException {
        File marker = new File(root, MARKER_SEALED);
        FileOutputStream out = new FileOutputStream(marker);
        try {
            String body = "gen=" + generation + "\nversion=" + VaultFormat.VERSION
                    + "\nsealedAt=" + System.currentTimeMillis() + "\n";
            out.write(body.getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
        } finally {
            closeQuietly(out);
        }
    }

    // ==================== migration and rotation ====================

    /**
     * Re-seals every v1 file under {@code root} as v2, in place.
     *
     * <p>Idempotent: files that already carry the v2 magic are skipped, so a second run is a no-op.
     * A file that is neither v1 nor v2 is left alone — it is plaintext, which means the tree was
     * never sealed and migration does not apply.</p>
     *
     * @return the number of files migrated
     */
    public static int migrate(String projectId, File root)
            throws GeneralSecurityException, IOException {
        if (root == null || !root.isDirectory()) {
            throw new IOException("Not a directory: " + root);
        }
        requireProjectId(projectId);
        int generation = currentGeneration(root);
        int migrated = migrateDirectory(projectId, root, root, generation);
        if (isSealed(root)) {
            writeMarker(root, generation);
        }
        return migrated;
    }

    private static int migrateDirectory(String projectId, File root, File dir, int generation)
            throws GeneralSecurityException, IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        int migrated = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                migrated += migrateDirectory(projectId, root, child, generation);
            } else if (child.isFile() && !isMarker(child.getName()) && !isSealedFile(child)) {
                byte[] raw = readAll(child);
                if (!VaultFormat.isLegacy(raw)) {
                    continue;
                }
                byte[] plain = openLegacy(projectId, raw);
                sealFile(projectId, relativePath(root, child), generation, plain, child);
                migrated++;
            }
        }
        return migrated;
    }

    /**
     * Re-seals a whole tree under a fresh key generation.
     *
     * <p>Used when a key may have been compromised. The old key is deleted only after every file
     * has been re-sealed, so a failure leaves the old key available for a retry. A pending marker
     * is written first: an interrupted rotation leaves files split across two generations, which
     * is exactly the state the pending marker exists to make detectable.</p>
     */
    public static void rotate(String projectId, File root)
            throws GeneralSecurityException, IOException {
        if (root == null || !root.isDirectory()) {
            throw new IOException("Not a directory: " + root);
        }
        requireProjectId(projectId);
        if (!isSealed(root)) {
            throw new IllegalStateException("Project is not sealed");
        }
        int current = currentGeneration(root);
        int next = current + 1;

        File pending = new File(root, MARKER_PENDING);
        FileOutputStream pendingOut = new FileOutputStream(pending);
        try {
            pendingOut.write((projectId + "\nrotate\n" + System.currentTimeMillis())
                    .getBytes("UTF-8"));
            pendingOut.flush();
            pendingOut.getFD().sync();
        } finally {
            closeQuietly(pendingOut);
        }

        try {
            rotateDirectory(projectId, root, root, current, next);
            writeMarker(root, next);
        } catch (Throwable error) {
            // The pending marker stays: the tree is split across generations and must be
            // re-imported rather than served.
            throw asIoOrSecurity(error, "Rotation failed for " + projectId);
        }

        deleteQuietly(pending);
        forgetGeneration(projectId, current);
    }

    private static void rotateDirectory(String projectId, File root, File dir,
                                        int from, int to)
            throws GeneralSecurityException, IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                rotateDirectory(projectId, root, child, from, to);
            } else if (child.isFile() && !isMarker(child.getName())) {
                String path = relativePath(root, child);
                File temp = new File(child.getParentFile(), child.getName() + ".rotating");
                VaultCipher.SealedStream sealed =
                        openStream(projectId, path, from, new FileInputStream(child));
                FileOutputStream out = new FileOutputStream(temp);
                try {
                    sealStream(projectId, path, to, sealed.stream(), sealed.plaintextLength(), out);
                    out.flush();
                    out.getFD().sync();
                } finally {
                    closeQuietly(out);
                    sealed.close();
                }
                replace(temp, child);
            }
        }
    }

    // ==================== keys ====================

    /** @return true when a Keystore key exists for {@code projectId} at generation 0 */
    public static boolean hasKey(String projectId) {
        return hasKey(projectId, 0);
    }

    public static boolean hasKey(String projectId, int generation) {
        if (projectId == null || projectId.isEmpty()) {
            return false;
        }
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            return store.containsAlias(aliasFor(projectId, generation));
        } catch (Throwable error) {
            return false;
        }
    }

    /** @return the Keystore alias used for {@code projectId} at generation 0 */
    public static String aliasFor(String projectId) {
        return aliasFor(projectId, 0);
    }

    /**
     * @return the Keystore alias for a generation
     *
     * <p>Generation 0 keeps the original alias so projects sealed before rotation existed still
     * open. Later generations append {@code .gN}.</p>
     */
    public static String aliasFor(String projectId, int generation) {
        if (generation <= 0) {
            return ALIAS_PREFIX + projectId;
        }
        return ALIAS_PREFIX + projectId + GENERATION_SUFFIX + generation;
    }

    /** Removes every generation of a project's key. Called when a project is deleted for good. */
    public static void forget(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return;
        }
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            java.util.Enumeration<String> aliases = store.aliases();
            String prefix = ALIAS_PREFIX + projectId;
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (alias.equals(prefix) || alias.startsWith(prefix + GENERATION_SUFFIX)) {
                    store.deleteEntry(alias);
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot delete key for " + projectId, error);
        }
    }

    private static void forgetGeneration(String projectId, int generation) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            String alias = aliasFor(projectId, generation);
            if (store.containsAlias(alias)) {
                store.deleteEntry(alias);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot delete generation " + generation + " for " + projectId, error);
        }
    }

    /**
     * Content fingerprint used to detect tampering outside the vault.
     *
     * <p>Hashes the raw bytes. The previous implementation round-tripped through a String, which
     * made the digest depend on the charset used to re-encode it.</p>
     */
    public static String fingerprint(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data != null ? data : new byte[0]);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (GeneralSecurityException error) {
            return "";
        }
    }

    // ==================== internals ====================

    static SecretKey keyFor(String projectId, int generation)
            throws GeneralSecurityException, IOException {
        String alias = aliasFor(projectId, generation);
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(alias)) {
            java.security.Key existing = store.getKey(alias, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static void requireAvailable() throws GeneralSecurityException {
        if (!isAvailable()) {
            throw new GeneralSecurityException("Keystore AES/GCM requires API 23");
        }
    }

    private static void requireProjectId(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            throw new IllegalArgumentException("projectId is required");
        }
    }

    private static boolean isMarker(String name) {
        return MARKER_SEALED.equals(name) || MARKER_PENDING.equals(name);
    }

    /** @return {@code file}'s path relative to {@code root}, normalised for the AAD */
    static String relativePath(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            return VaultFormat.normalizePath(filePath.substring(rootPath.length()));
        }
        return VaultFormat.normalizePath(file.getName());
    }

    private static void replace(File temp, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            deleteQuietly(temp);
            throw new IOException("Cannot replace " + target);
        }
        if (!temp.renameTo(target)) {
            deleteQuietly(temp);
            throw new IOException("Cannot move " + temp + " to " + target);
        }
    }

    private static byte[] readAll(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Missing file " + file);
        }
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    (int) Math.max(64L, Math.min(file.length(), 1 << 20)));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            closeQuietly(in);
        }
    }

    private static IOException asIoOrSecurity(Throwable error, String message)
            throws GeneralSecurityException {
        if (error instanceof GeneralSecurityException) {
            throw (GeneralSecurityException) error;
        }
        if (error instanceof IOException) {
            return (IOException) error;
        }
        return new IOException(message, error);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignored) {
            // Nothing useful to do; the caller is already unwinding.
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Cannot delete " + file.getAbsolutePath());
        }
    }
}
