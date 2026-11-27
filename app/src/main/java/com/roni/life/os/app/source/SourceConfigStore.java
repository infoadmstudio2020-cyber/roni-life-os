package com.roni.life.os.app.source;

import android.content.Context;

import com.roni.life.os.app.core.StorageLayout;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Reads and writes the per-surface source configuration.
 *
 * <p>Splash and Bootstrap each own one file, so changing one never affects the other. Writes go
 * through a temp file and a rename: a crash mid-write leaves the previous configuration intact
 * rather than a truncated one.</p>
 */
public final class SourceConfigStore {

    private static final String TAG = "SourceConfigStore";

    private SourceConfigStore() {
    }

    public static File configFile(Context context, SourceKind kind) {
        return kind == SourceKind.BOOTSTRAP
                ? StorageLayout.bootstrapConfigFile(context)
                : StorageLayout.splashConfigFile(context);
    }

    /** Loads the configuration, returning defaults when the file is missing or unreadable. */
    public static SourceConfig load(Context context, SourceKind kind) {
        File file = configFile(context, kind);
        if (!file.isFile()) {
            return new SourceConfig();
        }
        String raw = readText(file);
        return SourceConfig.decode(raw);
    }

    /** Persists the configuration atomically. */
    public static boolean save(Context context, SourceKind kind, SourceConfig config) {
        if (config == null) {
            return false;
        }
        config.touch();
        File target = configFile(context, kind);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            AppLog.w(TAG, "Cannot create " + parent.getAbsolutePath());
            return false;
        }
        File temp = new File(parent, target.getName() + ".tmp");
        try {
            OutputStream out = new FileOutputStream(temp);
            try {
                out.write(config.encode().getBytes("UTF-8"));
                out.flush();
            } finally {
                out.close();
            }
            if (!temp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!temp.renameTo(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                    AppLog.w(TAG, "Cannot replace " + target.getAbsolutePath());
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            AppLog.w(TAG, "Cannot write " + target.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static String readText(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[(int) Math.min(file.length(), 64 * 1024)];
            int read = in.read(buffer);
            return read > 0 ? new String(buffer, 0, read, "UTF-8") : "";
        } catch (IOException e) {
            AppLog.w(TAG, "Cannot read " + file.getName() + ": " + e.getMessage());
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing useful to do on close failure.
                }
            }
        }
    }
}
