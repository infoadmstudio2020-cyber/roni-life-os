package com.roni.library.platform.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.camera.CameraErrorCodes;
import com.roni.library.contracts.camera.CameraLimits;
import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Project isolated media storage.
 *
 * <p>Files live under {@code <app-private>/camera/<projectId>/}. Web pages only ever receive a
 * relative path; {@link #resolve(String, String)} refuses anything that escapes the project
 * directory.</p>
 */
public final class CameraMediaRepository {

    private static final String INDEX_KEY = "media_index";
    private static final String ROOT_DIR = "camera";

    private final File root;
    @Nullable
    private final EncryptedPreferences preferences;
    private final List<CameraMediaItem> memoryIndex = new ArrayList<>();
    private final Object lock = new Object();

    public CameraMediaRepository(@NonNull Context context,
                                 @Nullable EncryptedPreferences preferences) {
        this.root = new File(resolveBaseDir(context), ROOT_DIR);
        this.preferences = preferences;
        if (!root.exists()) {
            //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        }
        load();
    }

    @NonNull
    private static File resolveBaseDir(@NonNull Context context) {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return external != null ? external : context.getFilesDir();
    }

    @NonNull
    public File getRoot() {
        return root;
    }

    /** Directory that owns every file of one project. */
    @NonNull
    public File projectDir(@NonNull String projectId) {
        File dir = new File(root, sanitize(projectId));
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public File createPhotoFile(@NonNull String projectId, @NonNull String key)
            throws CameraException {
        return createFile(projectId, key, "IMG", "jpg");
    }

    @NonNull
    public File createVideoFile(@NonNull String projectId, @NonNull String key)
            throws CameraException {
        return createFile(projectId, key, "VID", "mp4");
    }

    @NonNull
    private File createFile(@NonNull String projectId, @NonNull String key,
                            @NonNull String prefix, @NonNull String extension)
            throws CameraException {
        File dir = projectDir(projectId);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String safeKey = sanitize(key);
        if (safeKey.isEmpty()) {
            safeKey = "capture";
        }
        File candidate = new File(dir, prefix + "_" + stamp + "_" + safeKey + "." + extension);
        int counter = 1;
        while (candidate.exists()) {
            candidate = new File(dir, prefix + "_" + stamp + "_" + safeKey + "_" + counter
                    + "." + extension);
            counter++;
        }
        try {
            if (!candidate.createNewFile()) {
                throw new IOException("Unable to create " + candidate.getName());
            }
        } catch (IOException error) {
            throw new CameraException(CameraErrorCodes.CAPTURE_FAILED,
                    "Unable to create the media file", error);
        }
        return candidate;
    }

    /** Registers or replaces a media record. */
    @NonNull
    public CameraMediaItem register(@NonNull CameraMediaItem item) {
        synchronized (lock) {
            memoryIndex.removeIf(existing -> existing.getId().equals(item.getId()));
            memoryIndex.add(0, item);
            while (memoryIndex.size() > CameraLimits.MAX_MEDIA_ITEMS) {
                memoryIndex.remove(memoryIndex.size() - 1);
            }
            persist();
        }
        return item;
    }

    @NonNull
    public List<CameraMediaItem> list(@NonNull String projectId, @Nullable String type, int limit) {
        int effectiveLimit = limit <= 0 ? CameraLimits.MAX_PAGE_SIZE
                : Math.min(limit, CameraLimits.MAX_PAGE_SIZE);
        List<CameraMediaItem> result = new ArrayList<>();
        synchronized (lock) {
            for (CameraMediaItem item : memoryIndex) {
                if (!item.getProjectId().equals(projectId)) {
                    continue;
                }
                if (type != null && !type.isEmpty() && !item.getType().equalsIgnoreCase(type)) {
                    continue;
                }
                result.add(item);
                if (result.size() >= effectiveLimit) {
                    break;
                }
            }
        }
        Collections.sort(result, new Comparator<CameraMediaItem>() {
            @Override
            public int compare(CameraMediaItem left, CameraMediaItem right) {
                return Long.compare(right.getCreatedAt(), left.getCreatedAt());
            }
        });
        return result;
    }

    @Nullable
    public CameraMediaItem get(@NonNull String projectId, @NonNull String id) {
        synchronized (lock) {
            for (CameraMediaItem item : memoryIndex) {
                if (item.getId().equals(id) && item.getProjectId().equals(projectId)) {
                    return item;
                }
            }
        }
        return null;
    }

    /** Deletes a record and its files. A foreign project id can never match. */
    public boolean delete(@NonNull String projectId, @NonNull String id) throws CameraException {
        CameraMediaItem item = get(projectId, id);
        if (item == null) {
            return false;
        }
        File file = resolve(projectId, item.getRelativePath());
        if (file.exists() && !file.delete()) {
            throw new CameraException(CameraErrorCodes.CAPTURE_FAILED,
                    "Unable to delete " + item.getFileName());
        }
        if (!item.getThumbnailPath().isEmpty()) {
            File thumb = resolve(projectId, item.getThumbnailPath());
            if (thumb.exists()) {
                //noinspection ResultOfMethodCallIgnored
                thumb.delete();
            }
        }
        synchronized (lock) {
            memoryIndex.remove(item);
            persist();
        }
        return true;
    }

    /**
     * Resolves a relative path inside a project directory.
     *
     * @throws CameraException when the path escapes the project root.
     */
    @NonNull
    public File resolve(@NonNull String projectId, @NonNull String relativePath)
            throws CameraException {
        if (relativePath.isEmpty() || relativePath.contains("..")) {
            throw new CameraException(CameraErrorCodes.MEDIA_NOT_FOUND, "Invalid media path");
        }
        try {
            File dir = projectDir(projectId);
            File candidate = new File(dir, relativePath);
            String dirPath = dir.getCanonicalPath();
            String filePath = candidate.getCanonicalPath();
            if (!filePath.startsWith(dirPath + File.separator)) {
                throw new CameraException(CameraErrorCodes.MEDIA_NOT_FOUND,
                        "Media path escapes the project directory");
            }
            return candidate;
        } catch (IOException error) {
            throw new CameraException(CameraErrorCodes.MEDIA_NOT_FOUND,
                    "Unable to resolve the media path", error);
        }
    }

    /** Relative path of a file inside its project directory. */
    @NonNull
    public String relativePath(@NonNull String projectId, @NonNull File file) {
        try {
            String dirPath = projectDir(projectId).getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(dirPath + File.separator)) {
                return filePath.substring(dirPath.length() + 1);
            }
        } catch (IOException ignored) {
        }
        return file.getName();
    }

    public long freeSpaceBytes() {
        return root.getUsableSpace();
    }

    /** @throws CameraException when the device cannot hold the requested payload. */
    public void ensureCapacity(long requiredBytes) throws CameraException {
        long free = freeSpaceBytes();
        if (free < Math.max(requiredBytes, CameraLimits.MIN_FREE_SPACE_BYTES)) {
            throw new CameraException(CameraErrorCodes.STORAGE_FULL,
                    "Not enough free storage for this capture");
        }
    }

    public long totalSize(@NonNull String projectId) {
        long total = 0L;
        synchronized (lock) {
            for (CameraMediaItem item : memoryIndex) {
                if (item.getProjectId().equals(projectId)) {
                    total += item.getSizeBytes();
                }
            }
        }
        return total;
    }

    /**
     * Generates a bounded thumbnail next to the source file.
     *
     * @return the relative thumbnail path, or an empty string when generation failed.
     */
    @NonNull
    public String generateThumbnail(@NonNull String projectId, @NonNull File source,
                                    boolean video) {
        Bitmap bitmap = null;
        try {
            if (video) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(source.getAbsolutePath());
                    bitmap = retriever.getFrameAtTime(0);
                } finally {
                    retriever.release();
                }
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
                bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
            }
            if (bitmap == null) {
                return "";
            }
            Bitmap scaled = scale(bitmap);
            File thumb = new File(source.getParentFile(), source.getName() + ".thumb.jpg");
            FileOutputStream output = new FileOutputStream(thumb);
            try {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, output);
            } finally {
                output.close();
            }
            if (scaled != bitmap) {
                scaled.recycle();
            }
            return relativePath(projectId, thumb);
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private static int sampleSize(int width, int height) {
        int largest = Math.max(width, height);
        int sample = 1;
        while (largest / sample > CameraLimits.MAX_THUMBNAIL_SIZE * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scale(@NonNull Bitmap source) {
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= CameraLimits.MAX_THUMBNAIL_SIZE) {
            return source;
        }
        float ratio = (float) CameraLimits.MAX_THUMBNAIL_SIZE / largest;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(source.getWidth() * ratio)),
                Math.max(1, Math.round(source.getHeight() * ratio)), true);
    }

    /**
     * Repairs records left behind by a crash or a killed process.
     *
     * @return number of records that were repaired or removed.
     */
    public int recoverInterrupted(@NonNull String projectId) {
        int repaired = 0;
        synchronized (lock) {
            List<CameraMediaItem> snapshot = new ArrayList<>(memoryIndex);
            for (CameraMediaItem item : snapshot) {
                if (!item.getProjectId().equals(projectId)
                        || !CameraMediaItem.STATE_INTERRUPTED.equals(item.getState())) {
                    continue;
                }
                try {
                    File file = resolve(projectId, item.getRelativePath());
                    if (file.exists() && file.length() > 0) {
                        memoryIndex.remove(item);
                        memoryIndex.add(0, item.toBuilder()
                                .state(CameraMediaItem.STATE_READY)
                                .sizeBytes(file.length())
                                .build());
                    } else {
                        memoryIndex.remove(item);
                    }
                    repaired++;
                } catch (CameraException ignored) {
                    memoryIndex.remove(item);
                    repaired++;
                }
            }
            if (repaired > 0) {
                persist();
            }
        }
        return repaired;
    }

    private void load() {
        if (preferences == null) {
            return;
        }
        String raw = preferences.getString(INDEX_KEY, "");
        if (raw.isEmpty()) {
            return;
        }
        for (String line : raw.split("\n")) {
            CameraMediaItem item = CameraCodec.decode(line);
            if (item != null) {
                memoryIndex.add(item);
            }
        }
    }

    private void persist() {
        if (preferences == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (CameraMediaItem item : memoryIndex) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(CameraCodec.encode(item));
        }
        preferences.putString(INDEX_KEY, builder.toString());
    }

    @NonNull
    private static String sanitize(@Nullable String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
