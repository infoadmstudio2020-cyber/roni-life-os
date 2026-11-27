package com.roni.library.contracts.automation;

/** Stable error codes shared by the automation engines and the bridge. */
public final class AutomationErrorCodes {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String NO_ACTIVE_PROJECT = "NO_ACTIVE_PROJECT";
    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    public static final String TASK_LIMIT = "TASK_LIMIT";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_LIMIT = "JOB_LIMIT";
    public static final String ALARM_NOT_FOUND = "ALARM_NOT_FOUND";
    public static final String ALARM_LIMIT = "ALARM_LIMIT";
    public static final String TRANSFER_NOT_FOUND = "TRANSFER_NOT_FOUND";
    public static final String TRANSFER_LIMIT = "TRANSFER_LIMIT";
    public static final String TRANSFER_FAILED = "TRANSFER_FAILED";
    public static final String NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE";
    public static final String LOW_STORAGE = "LOW_STORAGE";
    public static final String OPERATION_CANCELLED = "OPERATION_CANCELLED";
    public static final String UNSUPPORTED = "UNSUPPORTED";
    public static final String IO_ERROR = "IO_ERROR";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String SERVICE_NOT_FOUND = "SERVICE_NOT_FOUND";
    public static final String EVENT_LIMIT = "EVENT_LIMIT";
    public static final String WORK_UNAVAILABLE = "WORK_UNAVAILABLE";

    private AutomationErrorCodes() {
    }
}
