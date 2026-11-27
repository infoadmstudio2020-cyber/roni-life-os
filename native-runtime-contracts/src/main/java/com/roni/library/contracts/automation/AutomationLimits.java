package com.roni.library.contracts.automation;

/** Hard limits enforced by the automation engines before any work is accepted. */
public final class AutomationLimits {

    public static final int MAX_TASKS = 200;
    public static final int MAX_JOBS = 100;
    public static final int MAX_ALARMS = 100;
    public static final int MAX_TRANSFERS = 50;
    public static final int MAX_EVENTS = 500;
    public static final int EVENT_REPLAY = 100;
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_PATH_LENGTH = 512;
    public static final int MAX_RETRIES = 5;
    public static final long RETRY_BASE_DELAY_MS = 2_000L;
    public static final long MAX_RETRY_DELAY_MS = 60_000L;
    public static final int TRANSFER_BUFFER_BYTES = 64 * 1024;
    public static final long MIN_FREE_SPACE_BYTES = 2L * 1024 * 1024;
    public static final long MIN_REPEAT_INTERVAL_MS = 15L * 60 * 1000;
    public static final long MAX_ALARM_HORIZON_MS = 365L * 24 * 60 * 60 * 1000;

    private AutomationLimits() {
    }
}
