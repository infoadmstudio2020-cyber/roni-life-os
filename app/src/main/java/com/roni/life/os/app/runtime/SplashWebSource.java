package com.roni.life.os.app.runtime;

import android.content.Context;

import com.roni.life.os.app.source.SourceKind;
import com.roni.life.os.app.source.SourceResolver;
import com.roni.life.os.app.source.SourceSlot;
import com.roni.life.os.app.util.AppLog;

/**
 * Resolves the splash page through the centralized source system.
 *
 * <p>The splash is a web page stored under {@code system/splash/default} (protected, shipped in
 * the APK) or {@code system/splash/runtime} (user-controlled). {@link SourceResolver} decides
 * which one is active, validates it and falls back to Default when the runtime copy is broken.</p>
 *
 * <p>Returns {@code null} only when neither copy validates; the caller then shows the inline
 * HTML splash so the app still starts.</p>
 */
public final class SplashWebSource {

    private static final String TAG = "SplashWebSource";

    /** Entry document of a splash source. */
    public static final String ENTRY = "index.html";

    private SplashWebSource() {
    }

    /** Where the resolved splash page came from. */
    public enum Origin {
        /** Protected built-in source. */
        DEFAULT,
        /** User-controlled runtime source. */
        RUNTIME
    }

    /** A loadable splash page. */
    public static final class Source {
        private final String url;
        private final String baseUrl;
        private final Origin origin;

        Source(String url, String baseUrl, Origin origin) {
            this.url = url;
            this.baseUrl = baseUrl;
            this.origin = origin;
        }

        /** Absolute {@code file://} URL of the entry document. */
        public String getUrl() {
            return url;
        }

        /** Directory the page was loaded from, with a trailing slash. */
        public String getBaseUrl() {
            return baseUrl;
        }

        public Origin getOrigin() {
            return origin;
        }

        @Override
        public String toString() {
            return "SplashWebSource{" + origin + ", " + url + "}";
        }
    }

    /**
     * Resolves the splash page.
     *
     * @return the resolved source, or {@code null} when no source validates
     */
    public static Source resolve(Context context) {
        if (context == null) {
            return null;
        }
        SourceResolver.Result result = SourceResolver.resolve(context, SourceKind.SPLASH);
        if (!result.isSuccess()) {
            AppLog.w(TAG, result.getMessage());
            return null;
        }
        SourceResolver.Resolved resolved = result.getSource();
        if (resolved.isFallbackUsed()) {
            AppLog.w(TAG, resolved.getMessage());
        }
        Origin origin = resolved.getSlot() == SourceSlot.RUNTIME ? Origin.RUNTIME : Origin.DEFAULT;
        return new Source(resolved.getUrl(), resolved.getBaseUrl(), origin);
    }
}
