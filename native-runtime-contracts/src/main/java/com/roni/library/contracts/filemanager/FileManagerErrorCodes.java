package com.roni.library.contracts.filemanager;

/** Stable error codes returned by the file manager engine and the {@code /filemanager} route. */
public final class FileManagerErrorCodes {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_PATH = "INVALID_PATH";
    public static final String PATH_TRAVERSAL = "PATH_TRAVERSAL";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
    public static final String NOT_A_DIRECTORY = "NOT_A_DIRECTORY";
    public static final String NOT_A_FILE = "NOT_A_FILE";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String NO_ACTIVE_PROJECT = "NO_ACTIVE_PROJECT";
    public static final String CROSS_PROJECT_ACCESS = "CROSS_PROJECT_ACCESS";
    public static final String STORAGE_FULL = "STORAGE_FULL";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String UNSUPPORTED_OPERATION = "UNSUPPORTED_OPERATION";
    public static final String ARCHIVE_INVALID = "ARCHIVE_INVALID";
    public static final String ARCHIVE_LIMIT = "ARCHIVE_LIMIT";
    public static final String ARCHIVE_SLIP = "ARCHIVE_SLIP";
    public static final String OPERATION_CANCELLED = "OPERATION_CANCELLED";
    public static final String OPERATION_BUSY = "OPERATION_BUSY";
    public static final String IO_ERROR = "IO_ERROR";
    public static final String SAF_PERMISSION_REQUIRED = "SAF_PERMISSION_REQUIRED";

    private FileManagerErrorCodes() {
    }
}
