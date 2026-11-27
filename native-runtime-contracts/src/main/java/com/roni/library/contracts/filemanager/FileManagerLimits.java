package com.roni.library.contracts.filemanager;

/** Hard limits enforced by the file manager before any I/O happens. */
public final class FileManagerLimits {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_NAME_LENGTH = 128;
    public static final int MAX_PATH_LENGTH = 512;
    public static final int MAX_SEARCH_RESULTS = 500;
    public static final int MAX_SEARCH_DEPTH = 12;
    public static final long MAX_READ_BYTES = 2L * 1024L * 1024L;
    public static final long MAX_WRITE_BYTES = 8L * 1024L * 1024L;
    public static final long MAX_ARCHIVE_ENTRIES = 2000L;
    public static final long MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L;
    public static final long MAX_ARCHIVE_ENTRY_BYTES = 128L * 1024L * 1024L;
    public static final long MIN_FREE_SPACE_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_THUMBNAIL_SIZE = 512;
    public static final int MAX_OPERATION_HISTORY = 50;
    public static final int MAX_RECENT_ITEMS = 50;
    public static final int MAX_FAVORITES = 50;
    public static final long LARGE_FILE_BYTES = 50L * 1024L * 1024L;
    public static final long OLD_FILE_AGE_MS = 90L * 24L * 60L * 60L * 1000L;

    private FileManagerLimits() {
    }
}
