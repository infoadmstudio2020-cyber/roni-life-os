package com.roni.life.os.app.runtime;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebSourceType;
import com.roni.life.os.app.R;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Hands a project to another application.
 *
 * <p>A remote project opens in the user's browser. A local project lives in app-private storage
 * that no browser can read, so it is exported as a ZIP and offered through the system share
 * sheet instead — the same archive can be re-imported later.</p>
 */
public final class ProjectBrowserLauncher {

    private static final String TAG = "ProjectBrowser";

    private ProjectBrowserLauncher() {
    }

    /** @return true when another application accepted the project */
    public static boolean open(Activity activity, Project project) {
        if (activity == null || project == null) {
            return false;
        }
        WebSource source = WebSourceFactory.from(project);
        if (source != null && source.getType() == WebSourceType.REMOTE_URL) {
            return openUrl(activity, source.getLocation());
        }
        return share(activity, project);
    }

    private static boolean openUrl(Activity activity, String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(Intent.createChooser(intent,
                    activity.getString(R.string.pm_open_browser_chooser)));
            return true;
        } catch (ActivityNotFoundException e) {
            AppLog.w(TAG, "No browser accepts " + url);
            Toast.makeText(activity, R.string.pm_open_browser_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Zips the project folder and offers it through the share sheet. */
    public static boolean share(Activity activity, Project project) {
        File dir = projectDir(project);
        if (dir == null || !dir.isDirectory()) {
            Toast.makeText(activity, R.string.pm_share_unavailable, Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            File archive = zip(activity, project, dir);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", archive);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, project.getName());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent,
                    activity.getString(R.string.pm_share_chooser)));
            return true;
        } catch (Throwable error) {
            AppLog.e(TAG, "Unable to share " + project.getName(), error);
            Toast.makeText(activity, R.string.pm_share_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static File projectDir(Project project) {
        String path = project.getSourcePath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        File dir = new File(path);
        return dir.isDirectory() ? dir : null;
    }

    private static File zip(Activity activity, Project project, File dir) throws IOException {
        File outDir = new File(activity.getCacheDir(), "share");
        if (!outDir.isDirectory() && !outDir.mkdirs() && !outDir.isDirectory()) {
            throw new IOException("Cannot create " + outDir.getAbsolutePath());
        }
        File archive = new File(outDir, safeName(project.getId()) + ".zip");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive));
        try {
            addTree(zip, dir, "");
        } finally {
            zip.close();
        }
        return archive;
    }

    private static void addTree(ZipOutputStream zip, File dir, String prefix) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
            if (child.isDirectory()) {
                addTree(zip, child, name);
                continue;
            }
            zip.putNextEntry(new ZipEntry(name));
            InputStream in = new FileInputStream(child);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zip.write(buffer, 0, read);
                }
            } finally {
                in.close();
            }
            zip.closeEntry();
        }
    }

    private static String safeName(String value) {
        if (value == null || value.isEmpty()) {
            return "project";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
