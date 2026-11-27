package com.roni.life.os.app.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safe ZIP extraction shared by the importer and the resource manager.
 *
 * <p>Every entry name is validated before anything is written: absolute paths, Windows drive
 * letters, backslash separators, {@code ..} segments and NUL bytes are rejected, and the resolved
 * canonical destination must stay inside the target directory. Entry count and total uncompressed
 * size are capped to stop archive bombs.</p>
 *
 * <p>Deliberately free of Android imports so the extraction rules can be unit-tested on the JVM.</p>
 */
public final class ArchiveExtractor {

    /** Hard ceiling on the number of entries, to stop archive bombs. */
    public static final int MAX_ENTRIES = 5000;

    /** Hard ceiling on the uncompressed size, to stop archive bombs. */
    public static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L;

    private static final int BUFFER_SIZE = 16 * 1024;

    private ArchiveExtractor() {
    }

    /** @return {@code true} when {@code name} is a safe relative archive path */
    public static boolean isSafeEntryName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0) {
            return false;
        }
        if (name.startsWith("/")) {
            return false;
        }
        if (name.length() >= 2 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':') {
            return false;
        }
        String[] segments = name.split("/");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if ("..".equals(segment) || ".".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /** Extracts {@code archive} into {@code target}. */
    public static long[] extract(File archive, File target) throws Exception {
        if (archive == null || !archive.isFile()) {
            throw new IllegalArgumentException("The archive was not found.");
        }
        try (InputStream raw = new FileInputStream(archive)) {
            return extract(raw, target);
        }
    }

    /**
     * Extracts the ZIP stream into {@code target}.
     *
     * @return {@code {entryCount, totalBytes}}
     */
    public static long[] extract(InputStream zipStream, File target) throws Exception {
        if (zipStream == null) {
            throw new IllegalArgumentException("No archive stream was supplied.");
        }
        if (target == null) {
            throw new IllegalArgumentException("No destination was supplied.");
        }
        if (!target.exists() && !target.mkdirs()) {
            throw new IllegalStateException("Unable to create " + target.getAbsolutePath());
        }
        String rootPrefix = target.getCanonicalPath() + File.separator;

        int entries = 0;
        long totalBytes = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new SecurityException("Archive contains too many entries.");
                }
                String name = entry.getName();
                if (!isSafeEntryName(name)) {
                    throw new SecurityException("Unsafe archive entry: " + name);
                }

                File destination = new File(target, name);
                String canonical = destination.getCanonicalPath();
                if (!canonical.startsWith(rootPrefix)) {
                    throw new SecurityException(
                            "Archive entry escapes the project root: " + name);
                }

                if (entry.isDirectory()) {
                    if (!destination.exists() && !destination.mkdirs()) {
                        throw new IllegalStateException("Unable to create " + name);
                    }
                    continue;
                }

                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Unable to create " + name);
                }

                try (OutputStream out = new FileOutputStream(destination)) {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        totalBytes += read;
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            throw new SecurityException("Archive exceeds the size limit.");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
        return new long[]{entries, totalBytes};
    }

    /** Deletes a directory tree. */
    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
