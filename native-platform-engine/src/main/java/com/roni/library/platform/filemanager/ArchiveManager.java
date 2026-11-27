package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.ArchiveEntry;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.FileManagerLimits;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * ZIP archive support with ZIP Slip, entry-count and size protection.
 *
 * <p>Extraction never overwrites an existing file and never writes outside the target folder:
 * every entry name is normalised and canonicalised before the stream is opened.</p>
 */
public final class ArchiveManager {

    private final FileManagerRepository repository;

    public ArchiveManager(@NonNull FileManagerRepository repository) {
        this.repository = repository;
    }

    @NonNull
    public FileEntry create(@NonNull String projectId, @NonNull List<String> sourcePaths,
                            @NonNull String archivePath,
                            @Nullable FileManagerRepository.ProgressListener listener)
            throws FileManagerException {
        if (sourcePaths.isEmpty()) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "At least one source is required");
        }
        File archive = repository.resolve(projectId, archivePath);
        if (archive.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "The archive already exists");
        }
        File parent = archive.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw FileManagerException.io("Cannot create the archive folder", null);
        }
        List<File> sources = new ArrayList<>();
        long total = 0L;
        for (String path : sourcePaths) {
            File source = repository.resolve(projectId, path);
            if (!source.exists()) {
                throw FileManagerException.notFound("Source does not exist: " + path);
            }
            sources.add(source);
            total += repository.sizeOf(source);
        }
        long[] processed = {0L};
        int[] items = {0};
        try (ZipOutputStream output = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(archive)))) {
            for (File source : sources) {
                addToArchive(output, source, source.getName(), total, processed, items, listener);
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
            throw FileManagerException.io("Archive creation failed: " + error.getMessage(), error);
        }
        return repository.entry(projectId, archivePath);
    }

    private void addToArchive(@NonNull ZipOutputStream output, @NonNull File file,
                              @NonNull String entryName, long total, @NonNull long[] processed,
                              @NonNull int[] items,
                              @Nullable FileManagerRepository.ProgressListener listener)
            throws IOException, FileManagerException {
        if (listener != null && listener.isCancelled()) {
            throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                    "Operation cancelled");
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToArchive(output, child, entryName + "/" + child.getName(), total,
                            processed, items, listener);
                }
            }
            return;
        }
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        output.putNextEntry(entry);
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (listener != null && listener.isCancelled()) {
                    throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                            "Operation cancelled");
                }
                output.write(buffer, 0, read);
                processed[0] += read;
                if (listener != null) {
                    listener.onProgress(processed[0], total, items[0], 0);
                }
            }
        }
        output.closeEntry();
        items[0]++;
    }

    @NonNull
    public List<ArchiveEntry> list(@NonNull String projectId, @NonNull String archivePath)
            throws FileManagerException {
        File archive = repository.resolve(projectId, archivePath);
        if (!archive.exists() || archive.isDirectory()) {
            throw FileManagerException.notFound("Archive does not exist");
        }
        List<ArchiveEntry> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            int count = 0;
            while (enumeration.hasMoreElements()) {
                if (++count > FileManagerLimits.MAX_ARCHIVE_ENTRIES) {
                    throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_LIMIT,
                            "Archive contains more than "
                                    + FileManagerLimits.MAX_ARCHIVE_ENTRIES + " entries");
                }
                ZipEntry entry = enumeration.nextElement();
                entries.add(new ArchiveEntry(entry.getName(), entry.getSize(),
                        entry.getCompressedSize(), entry.isDirectory(), entry.getTime()));
            }
        } catch (IOException error) {
            throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_INVALID,
                    "Cannot read the archive: " + error.getMessage(), error);
        }
        return entries;
    }

    /**
     * Extracts the archive into {@code targetDir}.
     *
     * @param selected entry names to extract, or {@code null}/empty for every entry
     * @return number of extracted files
     */
    public int extract(@NonNull String projectId, @NonNull String archivePath,
                       @NonNull String targetDir, @Nullable List<String> selected,
                       @Nullable FileManagerRepository.ProgressListener listener)
            throws FileManagerException {
        File archive = repository.resolve(projectId, archivePath);
        if (!archive.exists() || archive.isDirectory()) {
            throw FileManagerException.notFound("Archive does not exist");
        }
        File target = repository.resolve(projectId, targetDir);
        if (!target.exists() && !target.mkdirs() && !target.isDirectory()) {
            throw FileManagerException.io("Cannot create the target folder", null);
        }
        String targetBase;
        try {
            targetBase = target.getCanonicalPath();
        } catch (IOException error) {
            throw FileManagerException.io("Cannot resolve the target folder", error);
        }
        int extracted = 0;
        long totalUncompressed = 0L;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            int count = 0;
            while (enumeration.hasMoreElements()) {
                if (listener != null && listener.isCancelled()) {
                    throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                            "Operation cancelled");
                }
                ZipEntry entry = enumeration.nextElement();
                if (++count > FileManagerLimits.MAX_ARCHIVE_ENTRIES) {
                    throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_LIMIT,
                            "Archive contains too many entries");
                }
                if (selected != null && !selected.isEmpty() && !selected.contains(entry.getName())) {
                    continue;
                }
                String safeName;
                try {
                    safeName = FileManagerPaths.normalize(entry.getName());
                } catch (FileManagerException error) {
                    throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_SLIP,
                            "Archive entry has an unsafe name: " + entry.getName(), error);
                }
                if (safeName.isEmpty()) {
                    continue;
                }
                File destination = new File(target, safeName);
                String canonical = destination.getCanonicalPath();
                if (!canonical.equals(targetBase)
                        && !canonical.startsWith(targetBase + File.separator)) {
                    throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_SLIP,
                            "Archive entry escapes the target folder: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!destination.exists() && !destination.mkdirs()
                            && !destination.isDirectory()) {
                        throw FileManagerException.io("Cannot create a folder from the archive",
                                null);
                    }
                    continue;
                }
                long declared = entry.getSize();
                if (declared > FileManagerLimits.MAX_ARCHIVE_ENTRY_BYTES) {
                    throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_LIMIT,
                            "Archive entry is too large: " + entry.getName());
                }
                totalUncompressed += Math.max(0L, declared);
                if (totalUncompressed > FileManagerLimits.MAX_ARCHIVE_BYTES) {
                    throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_LIMIT,
                            "Archive expands beyond the allowed size");
                }
                if (destination.exists()) {
                    throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                            "Refusing to overwrite: " + safeName);
                }
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()
                        && !parent.isDirectory()) {
                    throw FileManagerException.io("Cannot create the target folder", null);
                }
                try (InputStream input = zip.getInputStream(entry);
                     BufferedOutputStream output = new BufferedOutputStream(
                             new FileOutputStream(destination))) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    long written = 0L;
                    while ((read = input.read(buffer)) > 0) {
                        written += read;
                        if (written > FileManagerLimits.MAX_ARCHIVE_ENTRY_BYTES) {
                            throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_LIMIT,
                                    "Archive entry expands beyond the allowed size");
                        }
                        output.write(buffer, 0, read);
                        if (listener != null) {
                            listener.onProgress(written, Math.max(declared, written),
                                    extracted, 0);
                        }
                    }
                }
                extracted++;
            }
        } catch (IOException error) {
            throw new FileManagerException(FileManagerErrorCodes.ARCHIVE_INVALID,
                    "Extraction failed: " + error.getMessage(), error);
        }
        return extracted;
    }
}
