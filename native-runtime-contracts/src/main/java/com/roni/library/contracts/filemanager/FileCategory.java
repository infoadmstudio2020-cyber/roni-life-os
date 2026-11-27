package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/** Coarse file categories used by the explorer, analyzer and bridge filters. */
public final class FileCategory {

    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";
    public static final String AUDIO = "AUDIO";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String ARCHIVE = "ARCHIVE";
    public static final String OTHER = "OTHER";

    private FileCategory() {
    }

    @NonNull
    public static String fromMime(@Nullable String mimeType) {
        if (mimeType == null) {
            return OTHER;
        }
        String mime = mimeType.toLowerCase(Locale.US);
        if (mime.startsWith("image/")) {
            return IMAGE;
        }
        if (mime.startsWith("video/")) {
            return VIDEO;
        }
        if (mime.startsWith("audio/")) {
            return AUDIO;
        }
        if (mime.startsWith("text/") || mime.contains("pdf") || mime.contains("word")
                || mime.contains("excel") || mime.contains("powerpoint")
                || mime.contains("presentation") || mime.contains("spreadsheet")
                || mime.contains("json") || mime.contains("xml")) {
            return DOCUMENT;
        }
        if (mime.contains("zip") || mime.contains("compressed") || mime.contains("tar")
                || mime.contains("rar") || mime.contains("7z")) {
            return ARCHIVE;
        }
        return OTHER;
    }

    @NonNull
    public static String fromName(@Nullable String name) {
        String extension = extensionOf(name);
        switch (extension) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "bmp":
            case "heic":
            case "heif":
                return IMAGE;
            case "mp4":
            case "mkv":
            case "mov":
            case "avi":
            case "webm":
            case "3gp":
                return VIDEO;
            case "mp3":
            case "wav":
            case "ogg":
            case "flac":
            case "m4a":
            case "aac":
                return AUDIO;
            case "pdf":
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
            case "txt":
            case "md":
            case "json":
            case "xml":
            case "csv":
            case "html":
            case "js":
            case "css":
                return DOCUMENT;
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
                return ARCHIVE;
            default:
                return OTHER;
        }
    }

    @NonNull
    public static String extensionOf(@Nullable String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }
}
