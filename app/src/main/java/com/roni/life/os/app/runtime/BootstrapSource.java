package com.roni.life.os.app.runtime;

import android.content.Context;

import com.roni.life.os.app.source.SourceConfig;
import com.roni.life.os.app.source.SourceConfigStore;
import com.roni.life.os.app.source.SourceKind;
import com.roni.life.os.app.source.SourceResolver;
import com.roni.life.os.app.source.SourceSlot;

import java.io.File;

/**
 * Resolves the Bootstrap surface: which source is active and how it should render.
 *
 * <p>Source selection ({@code default}/{@code runtime}) and execution mode
 * ({@code native}/{@code web}) are independent. A runtime source can render natively, and the
 * default source can render as a web app.</p>
 */
public final class BootstrapSource {

    private BootstrapSource() {
    }

    /** The resolved bootstrap surface. */
    public static final class Selection {

        private final SourceConfig config;
        private final SourceResolver.Resolved source;
        private final String error;

        Selection(SourceConfig config, SourceResolver.Resolved source, String error) {
            this.config = config;
            this.source = source;
            this.error = error != null ? error : "";
        }

        public boolean isUsable() {
            return source != null;
        }

        public boolean isWebMode() {
            return config != null && config.isWebMode();
        }

        public boolean isNativeMode() {
            return !isWebMode();
        }

        public boolean isRuntime() {
            return source != null && source.getSlot() == SourceSlot.RUNTIME;
        }

        public boolean isFallbackUsed() {
            return source != null && source.isFallbackUsed();
        }

        public File getDir() {
            return source != null ? source.getDir() : null;
        }

        public String getEntry() {
            return source != null ? source.getEntry() : "index.html";
        }

        public String getUrl() {
            return source != null ? source.getUrl() : null;
        }

        public String getBaseUrl() {
            return source != null ? source.getBaseUrl() : null;
        }

        /** "Default", "Runtime" or "unavailable". */
        public String getSourceLabel() {
            return source != null ? source.getSlot().getLabel() : "unavailable";
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return source != null ? source.getMessage() : error;
        }
    }

    /** Resolves the bootstrap surface from {@code bootstrap-config.json}. */
    public static Selection resolve(Context context) {
        if (context == null) {
            return new Selection(new SourceConfig(), null, "No context supplied.");
        }
        SourceConfig config = SourceConfigStore.load(context, SourceKind.BOOTSTRAP);
        SourceResolver.Result result = SourceResolver.resolve(context, SourceKind.BOOTSTRAP, config);
        if (!result.isSuccess()) {
            return new Selection(config, null, result.getMessage());
        }
        return new Selection(config, result.getSource(), "");
    }

    /** True when a runtime bootstrap web app is configured, valid and set to web mode. */
    public static boolean hasRuntimeWebApp(Context context) {
        Selection selection = resolve(context);
        return selection.isUsable() && selection.isRuntime() && selection.isWebMode();
    }
}
