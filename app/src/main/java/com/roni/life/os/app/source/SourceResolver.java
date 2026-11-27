package com.roni.life.os.app.source;

import android.content.Context;

import com.roni.life.os.app.core.StorageLayout;
import com.roni.life.os.app.util.AppLog;

import java.io.File;

/**
 * Resolves which source a surface should load, with validation and fallback.
 *
 * <p>Order: read the configuration, validate the active source, fall back to Default when the
 * Runtime copy is missing or broken and fallback is enabled, and report a diagnostic when even
 * Default cannot be validated.</p>
 */
public final class SourceResolver {

    private static final String TAG = "SourceResolver";

    private SourceResolver() {
    }

    /** A source that passed validation and can be loaded. */
    public static final class Resolved {

        private final SourceKind kind;
        private final SourceSlot slot;
        private final File dir;
        private final String entry;
        private final boolean fallbackUsed;
        private final String message;

        Resolved(SourceKind kind, SourceSlot slot, File dir, String entry, boolean fallbackUsed,
                 String message) {
            this.kind = kind;
            this.slot = slot;
            this.dir = dir;
            this.entry = entry;
            this.fallbackUsed = fallbackUsed;
            this.message = message != null ? message : "";
        }

        public SourceKind getKind() {
            return kind;
        }

        public SourceSlot getSlot() {
            return slot;
        }

        public File getDir() {
            return dir;
        }

        public String getEntry() {
            return entry;
        }

        public File getEntryFile() {
            return dir != null ? new File(dir, entry) : null;
        }

        /** True when the configured Runtime source was rejected and Default took over. */
        public boolean isFallbackUsed() {
            return fallbackUsed;
        }

        public String getMessage() {
            return message;
        }

        /** {@code file://} URL of the entry document. */
        public String getUrl() {
            File file = getEntryFile();
            return file != null ? "file://" + file.getAbsolutePath() : null;
        }

        /** Directory URL with a trailing slash, for relative imports and ES modules. */
        public String getBaseUrl() {
            return dir != null ? "file://" + dir.getAbsolutePath() + "/" : null;
        }

        @Override
        public String toString() {
            return "Resolved{" + kind.getKey() + ", " + slot.getKey()
                    + (fallbackUsed ? ", fallback" : "") + ", " + dir + "}";
        }
    }

    /** Outcome when nothing could be resolved. */
    public static final class Failure {

        private final SourceKind kind;
        private final String message;

        Failure(SourceKind kind, String message) {
            this.kind = kind;
            this.message = message != null ? message : "";
        }

        public SourceKind getKind() {
            return kind;
        }

        public String getMessage() {
            return message;
        }
    }

    /** Combined result: exactly one of {@link #getSource()} / {@link #getFailure()} is set. */
    public static final class Result {

        private final Resolved source;
        private final Failure failure;

        Result(Resolved source, Failure failure) {
            this.source = source;
            this.failure = failure;
        }

        public boolean isSuccess() {
            return source != null;
        }

        public Resolved getSource() {
            return source;
        }

        public Failure getFailure() {
            return failure;
        }

        public String getMessage() {
            return source != null ? source.getMessage() : failure.getMessage();
        }
    }

    // ==================== Paths ====================

    public static File defaultDir(Context context, SourceKind kind) {
        return kind == SourceKind.BOOTSTRAP
                ? StorageLayout.bootstrapDefaultDir(context)
                : StorageLayout.splashDefaultDir(context);
    }

    public static File runtimeDir(Context context, SourceKind kind) {
        return kind == SourceKind.BOOTSTRAP
                ? StorageLayout.bootstrapRuntimeDir(context)
                : StorageLayout.splashRuntimeDir(context);
    }

    // ==================== Resolution ====================

    /** Resolves the surface using its stored configuration. */
    public static Result resolve(Context context, SourceKind kind) {
        if (context == null) {
            return new Result(null, new Failure(kind, "No context supplied."));
        }
        Context app = context.getApplicationContext();
        SourceConfig config = SourceConfigStore.load(app, kind);
        return resolve(app, kind, config);
    }

    /** Resolves the surface using an explicit configuration (used by preview and tests). */
    public static Result resolve(Context context, SourceKind kind, SourceConfig config) {
        Context app = context.getApplicationContext();
        SourceConfig effective = config != null ? config : new SourceConfig();
        String entry = effective.getRuntimeEntry();

        if (effective.getActiveSource().isRuntime()) {
            File runtime = runtimeDir(app, kind);
            SourceValidator.Validation validation = SourceValidator.validate(runtime, entry);
            if (validation.isValid()) {
                return new Result(new Resolved(kind, SourceSlot.RUNTIME, runtime, entry, false,
                        "Runtime source active."), null);
            }
            if (!effective.isFallbackEnabled()) {
                return new Result(null, new Failure(kind,
                        "The runtime " + kind.getLabel() + " source is invalid ("
                                + validation.getMessage() + ") and fallback is disabled."));
            }
            AppLog.w(TAG, kind.getLabel() + " runtime source invalid — falling back: "
                    + validation.getMessage());
            File fallback = defaultDir(app, kind);
            SourceValidator.Validation fallbackValidation = SourceValidator.validate(fallback, entry);
            if (fallbackValidation.isValid()) {
                return new Result(new Resolved(kind, SourceSlot.DEFAULT, fallback, entry, true,
                        "Runtime source invalid (" + validation.getMessage()
                                + "); using the default source."), null);
            }
            return new Result(null, new Failure(kind,
                    "Neither the runtime nor the default " + kind.getLabel()
                            + " source is valid. Default: " + fallbackValidation.getMessage()));
        }

        File dir = defaultDir(app, kind);
        SourceValidator.Validation validation = SourceValidator.validate(dir, entry);
        if (validation.isValid()) {
            return new Result(new Resolved(kind, SourceSlot.DEFAULT, dir, entry, false,
                    "Default source active."), null);
        }
        return new Result(null, new Failure(kind,
                "The default " + kind.getLabel() + " source is invalid: "
                        + validation.getMessage()));
    }

    /** True when the runtime copy exists and passes validation. */
    public static boolean isRuntimeValid(Context context, SourceKind kind) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        return SourceValidator.validate(runtimeDir(context, kind), config.getRuntimeEntry())
                .isValid();
    }
}
