package com.roni.library.security.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Standalone checks for {@link VaultCipher} — the format layer, exercised with a software AES key.
 *
 * <p>This is the part of the vault that can be verified without Android: chunking, AAD binding,
 * tag verification, truncation handling and the legacy read path. Key <em>management</em> (Android
 * Keystore) is not covered here and cannot be, on a JVM.</p>
 *
 * <p>Plain {@code main} rather than JUnit so it runs with nothing but a JDK. Exits non-zero on the
 * first failure.</p>
 */
public final class VaultCipherCheck {

    private static final String PROJECT = "11111111-2222-3333-4444-555555555555";
    private static final String OTHER_PROJECT = "99999999-8888-7777-6666-555555555555";

    private static int passed;
    private static SecretKey key;
    private static SecretKey otherKey;

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        key = generator.generateKey();
        otherKey = generator.generateKey();

        roundTrips();
        roundTripsEmpty();
        roundTripsExactChunkMultiple();
        roundTripsMultiChunk();
        bindsToPath();
        bindsToProject();
        bindsToGeneration();
        detectsBodyTampering();
        detectsHeaderTampering();
        detectsTruncation();
        detectsChunkReordering();
        detectsCrossResourceSwap();
        readsLegacyFormat();
        streamingMatchesBuffered();
        streamsLargeResource();
        rejectsNonSealedInput();

        System.out.println("VaultCipherCheck: " + passed + " checks passed");
    }

    // ==================== round trips ====================

    private static void roundTrips() throws Exception {
        byte[] plain = "hello vault".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = VaultCipher.seal(PROJECT, "index.html", 0, key, plain);
        check(VaultFormat.hasMagic(sealed), "sealed blob carries the magic");
        eq(plain, VaultCipher.open(PROJECT, "index.html", 0, key, sealed), "round trip");
    }

    private static void roundTripsEmpty() throws Exception {
        byte[] sealed = VaultCipher.seal(PROJECT, "empty.txt", 0, key, new byte[0]);
        eq(new byte[0], VaultCipher.open(PROJECT, "empty.txt", 0, key, sealed),
                "empty file round trip");
        eq(0, VaultFormat.readInt(sealed, 9), "empty file has zero chunks");
    }

    private static void roundTripsExactChunkMultiple() throws Exception {
        int chunk = VaultLimits.getChunkBytes();
        byte[] plain = random(chunk * 2);
        byte[] sealed = VaultCipher.seal(PROJECT, "exact.bin", 0, key, plain);
        eq(2, VaultFormat.readInt(sealed, 9), "exact multiple produces two chunks");
        eq(plain, VaultCipher.open(PROJECT, "exact.bin", 0, key, sealed), "exact multiple round trip");
    }

    private static void roundTripsMultiChunk() throws Exception {
        int chunk = VaultLimits.getChunkBytes();
        byte[] plain = random(chunk * 3 + 17);
        byte[] sealed = VaultCipher.seal(PROJECT, "big.bin", 0, key, plain);
        eq(4, VaultFormat.readInt(sealed, 9), "three full chunks plus a remainder");
        eq(plain, VaultCipher.open(PROJECT, "big.bin", 0, key, sealed), "multi chunk round trip");
    }

    // ==================== AAD binding ====================

    private static void bindsToPath() throws Exception {
        byte[] sealed = VaultCipher.seal(PROJECT, "js/app.js", 0, key, bytes("module"));
        expectFailure("opening under a different path fails",
                () -> VaultCipher.open(PROJECT, "js/other.js", 0, key, sealed));
        expectFailure("opening under a parent path fails",
                () -> VaultCipher.open(PROJECT, "app.js", 0, key, sealed));
        // Normalisation must not break the legitimate case.
        eq(bytes("module"), VaultCipher.open(PROJECT, "/js/app.js", 0, key, sealed),
                "leading slash still opens");
    }

    private static void bindsToProject() throws Exception {
        byte[] sealed = VaultCipher.seal(PROJECT, "index.html", 0, key, bytes("secret"));
        expectFailure("opening under another project id fails",
                () -> VaultCipher.open(OTHER_PROJECT, "index.html", 0, key, sealed));
        expectFailure("opening under another project key fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, otherKey, sealed));
    }

    private static void bindsToGeneration() throws Exception {
        byte[] sealed = VaultCipher.seal(PROJECT, "index.html", 0, key, bytes("secret"));
        expectFailure("opening under another generation fails",
                () -> VaultCipher.open(PROJECT, "index.html", 1, key, sealed));
    }

    // ==================== tampering ====================

    private static void detectsBodyTampering() throws Exception {
        byte[] sealed = VaultCipher.seal(PROJECT, "index.html", 0, key, bytes("authentic"));
        byte[] tampered = sealed.clone();
        tampered[tampered.length - 1] ^= 0x01;
        expectFailure("flipping the last ciphertext byte fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, tampered));

        byte[] ivTampered = sealed.clone();
        ivTampered[VaultFormat.HEADER_BYTES] ^= 0x01;
        expectFailure("flipping an IV byte fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, ivTampered));
    }

    private static void detectsHeaderTampering() throws Exception {
        // A multi-chunk payload, because chunkSize only has meaning when there is more than one
        // chunk: for a single-chunk file the last chunk's length comes from plainLength, so
        // rewriting chunkSize changes nothing and there is nothing to detect.
        byte[] plain = random(VaultLimits.getChunkBytes() * 2);
        byte[] sealed = VaultCipher.seal(PROJECT, "index.html", 0, key, plain);

        byte[] lengthTampered = sealed.clone();
        VaultFormat.writeLong(lengthTampered, 13, 999L);
        expectFailure("a rewritten plaintext length fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, lengthTampered));

        byte[] countTampered = sealed.clone();
        VaultFormat.writeInt(countTampered, 9, 7);
        expectFailure("a rewritten chunk count fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, countTampered));

        byte[] chunkTampered = sealed.clone();
        VaultFormat.writeInt(chunkTampered, 5, 4096);
        expectFailure("a rewritten chunk size fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, chunkTampered));
    }

    private static void detectsTruncation() throws Exception {
        byte[] sealed = VaultCipher.seal(PROJECT, "index.html", 0, key, bytes("authentic"));
        byte[] cut = Arrays.copyOf(sealed, sealed.length - 4);
        expectFailure("a truncated blob fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, cut));
        expectFailure("an empty blob fails",
                () -> VaultCipher.open(PROJECT, "index.html", 0, key, new byte[0]));
    }

    private static void detectsChunkReordering() throws Exception {
        int chunk = VaultLimits.getChunkBytes();
        byte[] plain = random(chunk * 2);
        byte[] sealed = VaultCipher.seal(PROJECT, "two.bin", 0, key, plain);

        int firstStart = VaultFormat.HEADER_BYTES;
        int firstLength = VaultFormat.IV_BYTES + chunk + VaultFormat.TAG_BYTES;
        int secondStart = firstStart + firstLength;
        int secondLength = VaultFormat.IV_BYTES + chunk + VaultFormat.TAG_BYTES;

        byte[] swapped = sealed.clone();
        System.arraycopy(sealed, secondStart, swapped, firstStart, secondLength);
        System.arraycopy(sealed, firstStart, swapped, secondStart, firstLength);

        expectFailure("swapping two chunks fails",
                () -> VaultCipher.open(PROJECT, "two.bin", 0, key, swapped));
    }

    private static void detectsCrossResourceSwap() throws Exception {
        byte[] a = VaultCipher.seal(PROJECT, "a.js", 0, key, bytes("resource A"));
        byte[] b = VaultCipher.seal(PROJECT, "b.js", 0, key, bytes("resource B"));
        expectFailure("A's ciphertext served as B fails",
                () -> VaultCipher.open(PROJECT, "b.js", 0, key, a));
        expectFailure("B's ciphertext served as A fails",
                () -> VaultCipher.open(PROJECT, "a.js", 0, key, b));
    }

    // ==================== legacy ====================

    private static void readsLegacyFormat() throws Exception {
        byte[] plain = bytes("legacy payload");
        byte[] legacy = buildLegacyBlob(PROJECT, key, plain);
        check(!VaultFormat.hasMagic(legacy), "legacy blob has no magic");
        eq(plain, VaultCipher.open(PROJECT, "index.html", 0, key, legacy),
                "legacy blob still opens");
        expectFailure("legacy blob under another project fails",
                () -> VaultCipher.open(OTHER_PROJECT, "index.html", 0, key, legacy));
    }

    // ==================== streaming ====================

    private static void streamingMatchesBuffered() throws Exception {
        byte[] plain = random(VaultLimits.getChunkBytes() * 2 + 5);
        byte[] sealed = VaultCipher.seal(PROJECT, "stream.bin", 0, key, plain);

        VaultCipher.SealedStream stream = VaultCipher.openStream(PROJECT, "stream.bin", 0, key,
                new ByteArrayInputStream(sealed));
        try {
            eq((long) plain.length, stream.plaintextLength(), "stream reports the plaintext length");
            eq(plain, readAll(stream.stream()), "streamed bytes match the buffered bytes");
        } finally {
            stream.close();
        }
    }

    private static void streamsLargeResource() throws Exception {
        int chunk = VaultLimits.getChunkBytes();
        int size = chunk * 40 + 123;          // 40 full chunks plus a remainder
        byte[] plain = random(size);

        ByteArrayOutputStream sealedOut = new ByteArrayOutputStream();
        VaultCipher.sealStream(PROJECT, "large.bin", 0, key, new ByteArrayInputStream(plain),
                plain.length, sealedOut);
        byte[] sealed = sealedOut.toByteArray();

        eq(41, VaultFormat.readInt(sealed, 9), "large resource is split into 41 chunks");

        VaultCipher.SealedStream stream = VaultCipher.openStream(PROJECT, "large.bin", 0, key,
                new ByteArrayInputStream(sealed));
        try {
            eq((long) size, stream.plaintextLength(), "large stream reports the right length");
            eq(plain, readAll(stream.stream()), "large stream round trips");
        } finally {
            stream.close();
        }
    }

    private static void rejectsNonSealedInput() throws Exception {
        expectFailure("plaintext is not accepted as sealed",
                () -> VaultCipher.openStream(PROJECT, "index.html", 0, key,
                        new ByteArrayInputStream(bytes("<!DOCTYPE html>"))));
    }

    // ==================== helpers ====================

    private static byte[] buildLegacyBlob(String projectId, SecretKey secretKey, byte[] plain)
            throws Exception {
        byte[] iv = CryptoHash.generateRandomBytes(VaultFormat.IV_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(VaultFormat.TAG_BITS, iv));
        cipher.updateAAD(projectId.getBytes(StandardCharsets.UTF_8));
        byte[] body = cipher.doFinal(plain);

        byte[] out = new byte[1 + VaultFormat.IV_BYTES + body.length];
        out[0] = VaultFormat.LEGACY_VERSION;
        System.arraycopy(iv, 0, out, 1, VaultFormat.IV_BYTES);
        System.arraycopy(body, 0, out, 1 + VaultFormat.IV_BYTES, body.length);
        return out;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static byte[] random(int length) {
        byte[] data = new byte[length];
        new Random(42L).nextBytes(data);
        return data;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void expectFailure(String label, Action action) {
        try {
            action.run();
        } catch (GeneralSecurityException | java.io.IOException expected) {
            passed++;
            return;
        } catch (Exception other) {
            fail(label + ": wrong exception " + other.getClass().getSimpleName());
            return;
        }
        fail(label + ": no exception was thrown");
    }

    private static void eq(Object want, Object got, String label) {
        boolean equal = want instanceof byte[] && got instanceof byte[]
                ? Arrays.equals((byte[]) want, (byte[]) got)
                : (want == null ? got == null : want.equals(got));
        if (!equal) {
            fail(label + ": expected " + describe(want) + " but got " + describe(got));
        }
        passed++;
    }

    private static String describe(Object value) {
        if (value instanceof byte[]) {
            return "byte[" + ((byte[]) value).length + "]";
        }
        return String.valueOf(value);
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            fail(label);
        }
        passed++;
    }

    private static void fail(String message) {
        System.err.println("FAIL: " + message);
        System.exit(1);
    }
}
