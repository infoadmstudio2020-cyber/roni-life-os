package com.roni.library.ui.download;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Download pipeline shared by every project runtime.
 *
 * <p>Three URL shapes reach a WebView download listener and each needs a different route:</p>
 * <ul>
 *   <li>{@code http}/{@code https} — handed to the system {@link DownloadManager}, which owns the
 *       transfer, the notification and the retry policy.</li>
 *   <li>{@code data:} — decoded in-process and written straight to Downloads.</li>
 *   <li>{@code blob:} — only the page can read it, so {@link #blobScript} converts it to base64
 *       and posts it back through {@link JsBridge}.</li>
 * </ul>
 *
 * <p>Files land in {@code Download/RONiLiFEOS} so they are visible in the Files app and survive
 * uninstall. Any MIME type is accepted; the extension is derived from the URL, the
 * {@code Content-Disposition} header or the MIME type, in that order.</p>
 */
public final class DownloadCenter {

    private static final String TAG = "DownloadCenter";

    /** Folder inside the public Downloads collection. */
    public static final String FOLDER = "RONiLiFEOS";

    /** Name of the JavaScript interface injected into every page. */
    public static final String JS_INTERFACE = "RONiDownload";

    private DownloadCenter() {
    }

    /** Entry point used by the WebView download listener. */
    public static void enqueue(Context context, String url, String userAgent,
                               String contentDisposition, String mimeType, long contentLength) {
        if (context == null || url == null || url.isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                enqueueHttp(app, url, userAgent, contentDisposition, mimeType);
            } else if (url.startsWith("data:")) {
                saveDataUrl(app, url, guessName(url, contentDisposition, mimeType));
            } else {
                Log.w(TAG, "Unsupported download scheme: " + url);
            }
        } catch (Throwable error) {
            Log.e(TAG, "Download failed for " + url, error);
            toast(app, "Download failed");
        }
    }

    private static void enqueueHttp(Context context, String url, String userAgent,
                                    String contentDisposition, String mimeType) {
        String fileName = guessName(url, contentDisposition, mimeType);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        if (mimeType != null && !mimeType.isEmpty()) {
            request.setMimeType(mimeType);
        }
        if (userAgent != null && !userAgent.isEmpty()) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        request.setTitle(fileName);
        request.setDescription("Downloading to " + FOLDER);
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                FOLDER + "/" + fileName);
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            toast(context, "Download manager unavailable");
            return;
        }
        manager.enqueue(request);
        toast(context, "Downloading " + fileName);
    }

    /** Decodes a {@code data:} URL and writes it to Downloads. */
    public static void saveDataUrl(Context context, String dataUrl, String fileName) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return;
        }
        String header = dataUrl.substring(5, comma);
        String payload = dataUrl.substring(comma + 1);
        String mime = header.split(";")[0];
        if (mime.isEmpty()) {
            mime = "application/octet-stream";
        }
        byte[] bytes;
        try {
            bytes = header.contains("base64")
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : Uri.decode(payload).getBytes("UTF-8");
        } catch (Throwable error) {
            Log.e(TAG, "Cannot decode data URL", error);
            toast(context, "Download failed");
            return;
        }
        write(context, bytes, fileName, mime);
    }

    /** Writes raw bytes into {@code Download/RONiLiFEOS}. */
    public static void write(Context context, byte[] bytes, String fileName, String mime) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        String name = sanitize(fileName);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE,
                        mime != null ? mime : "application/octet-stream");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                Uri target = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (target == null) {
                    toast(context, "Download failed");
                    return;
                }
                OutputStream out = context.getContentResolver().openOutputStream(target);
                if (out == null) {
                    toast(context, "Download failed");
                    return;
                }
                try {
                    out.write(bytes);
                    out.flush();
                } finally {
                    out.close();
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), FOLDER);
                if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                    toast(context, "Cannot create the download folder");
                    return;
                }
                FileOutputStream out = new FileOutputStream(new File(dir, name));
                try {
                    out.write(bytes);
                    out.flush();
                } finally {
                    out.close();
                }
            }
            toast(context, "Saved " + name);
        } catch (Throwable error) {
            Log.e(TAG, "Cannot save " + name, error);
            toast(context, "Download failed");
        }
    }

    /**
     * JavaScript that turns a {@code blob:} URL into base64 and posts it to {@link JsBridge}.
     *
     * @return a self-contained script safe to pass to {@code evaluateJavascript}
     */
    public static String blobScript(String url, String fileName, String mime) {
        return "(function(){"
                + "fetch(" + quote(url) + ").then(function(r){return r.blob();})"
                + ".then(function(b){var fr=new FileReader();"
                + "fr.onload=function(){var s=String(fr.result);var i=s.indexOf(',');"
                + "if(i>0){" + JS_INTERFACE + ".saveBase64(s.substring(i+1),"
                + quote(fileName) + "," + quote(mime) + ");}};"
                + "fr.readAsDataURL(b);})"
                + ".catch(function(e){console.error('download failed',e);});})();";
    }

    /** Bridge object exposed to pages as {@code window.RONiDownload}. */
    public static final class JsBridge {

        private final Context context;

        public JsBridge(Context context) {
            this.context = context.getApplicationContext();
        }

        @JavascriptInterface
        public void saveBase64(String base64, String fileName, String mime) {
            try {
                write(context, Base64.decode(base64, Base64.DEFAULT), fileName, mime);
            } catch (Throwable error) {
                Log.e(TAG, "Cannot decode blob download", error);
            }
        }

        @JavascriptInterface
        public void download(String url, String fileName) {
            enqueue(context, url, null, null, null, 0L);
        }
    }

    // ==================== Naming ====================

    /** URL name, then Content-Disposition, then a MIME-derived fallback. */
    public static String guessName(String url, String contentDisposition, String mime) {
        String name = null;
        try {
            name = URLUtil.guessFileName(url, contentDisposition, mime);
        } catch (Throwable ignored) {
            // Fall through to the manual derivation below.
        }
        if (name == null || name.isEmpty() || "downloadfile".equals(name)) {
            String extension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mime != null ? mime : "application/octet-stream");
            name = "download-" + System.currentTimeMillis()
                    + (extension != null ? "." + extension : "");
        }
        return sanitize(name);
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "download-" + System.currentTimeMillis();
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "download-" + System.currentTimeMillis() : cleaned;
    }

    private static String quote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static void toast(Context context, String message) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            // A background context without a window cannot toast; the log still records it.
        }
    }
}
