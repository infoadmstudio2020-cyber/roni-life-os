
package com.roni.life.os.app.util;

import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.logger.NativeLogger;

/**
 * App-level log helper.
 *
 * <p>Every call goes to NativeLogger and nowhere else. The logger fans the
 * entry out to its sinks — logcat and the Runtime Inspector — so the
 * application has exactly one logging pipeline.</p>
 *
 * <p>The inspector receives these entries through its ILogSink, which is
 * why the app does not need a second, parallel collector call here.</p>
 */
public final class AppLog {

    private static final String TAG = "RONiLiFEOS";

    static {
        // AppLog emits DEBUG and above. Ensure NativeLogger does not filter
        // those entries before they reach its sinks.
        // An explicit NativeLogger.init(...) later still takes precedence.
        try {
            NativeLogger logger = NativeLogger.getInstance();
            LogLevel current = logger.getMinimumLevel();

            if (current == null
                    || current.getPriority() > LogLevel.DEBUG.getPriority()) {
                logger.setMinimumLevel(LogLevel.DEBUG);
            }
        } catch (Throwable ignored) {
            // Logging setup must never break class loading.
        }
    }

    private AppLog() {
        // Utility class.
    }

    // ---------------------------------------------------------------------
    // Default-tag logging methods
    // ---------------------------------------------------------------------

    public static void d(String message) {
        write(LogLevel.DEBUG, TAG, message, null);
    }

    public static void i(String message) {
        write(LogLevel.INFO, TAG, message, null);
    }

    public static void w(String message) {
        write(LogLevel.WARN, TAG, message, null);
    }

    public static void e(String message) {
        write(LogLevel.ERROR, TAG, message, null);
    }

    public static void e(String message, Throwable throwable) {
        write(LogLevel.ERROR, TAG, message, throwable);
    }

    // ---------------------------------------------------------------------
    // Custom-tag logging methods
    // ---------------------------------------------------------------------

    public static void d(String tag, String message) {
        write(LogLevel.DEBUG, tag, message, null);
    }

    public static void d(String tag, String message, Throwable throwable) {
        write(LogLevel.DEBUG, tag, message, throwable);
    }

    public static void i(String tag, String message) {
        write(LogLevel.INFO, tag, message, null);
    }

    public static void i(String tag, String message, Throwable throwable) {
        write(LogLevel.INFO, tag, message, throwable);
    }

    public static void w(String tag, String message) {
        write(LogLevel.WARN, tag, message, null);
    }

    /**
     * Logs a warning with its associated exception.
     */
    public static void w(
            String tag,
            String message,
            Throwable throwable
    ) {
        write(LogLevel.WARN, tag, message, throwable);
    }

    public static void e(String tag, String message) {
        write(LogLevel.ERROR, tag, message, null);
    }

    public static void e(
            String tag,
            String message,
            Throwable throwable
    ) {
        write(LogLevel.ERROR, tag, message, throwable);
    }

    // ---------------------------------------------------------------------
    // Internal logging pipeline
    // ---------------------------------------------------------------------

    private static void write(
            LogLevel level,
            String tag,
            String message,
            Throwable throwable
    ) {
        try {
            NativeLogger logger = NativeLogger.getInstance();

            String safeTag = (tag == null || tag.trim().isEmpty())
                    ? TAG
                    : tag;

            String safeMessage = message == null
                    ? "null"
                    : message;

            switch (level) {
                case DEBUG:
                    logger.d(safeTag, safeMessage, throwable);
                    break;

                case WARN:
                    logger.w(safeTag, safeMessage, throwable);
                    break;

                case ERROR:
                case FATAL:
                    logger.e(safeTag, safeMessage, throwable);
                    break;

                case INFO:
                default:
                    logger.i(safeTag, safeMessage, throwable);
                    break;
            }
        } catch (Throwable ignored) {
            // A logging failure must never affect application execution.
        }
    }
}
