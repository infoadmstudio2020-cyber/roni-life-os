package com.roni.library.bridge;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configuration options for the NativeWebBridgeSDK instance.
 *
 * <p><b>Origin validation is on by default.</b> The default allow-list contains only the
 * virtual app origin {@value #DEFAULT_ALLOWED_ORIGIN}. An empty allow-list denies every
 * origin; use {@code addAllowedOrigin("*")} to opt out explicitly.</p>
 */
public final class BridgeConfig {

    /** Default name of the injected JavaScript interface. */
    public static final String DEFAULT_INTERFACE_NAME = "NativeRuntimeBridge";

    /** Origin served by {@code WebViewAssetLoader} for bundled assets. */
    public static final String DEFAULT_ALLOWED_ORIGIN = "https://appassets.androidplatform.net";

    /** Default maximum accepted message size, in bytes. */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 1_048_576;

    private static final Pattern SAFE_INTERFACE_NAME =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private final String interfaceName;
    private volatile Set<String> allowedOrigins;
    private volatile long timeoutMs;
    private volatile boolean debugLogging;
    private volatile boolean requireOriginValidation;
    private volatile int maxMessageBytes;

    private BridgeConfig(Builder builder) {
        this.interfaceName = sanitizeInterfaceName(builder.interfaceName);
        this.allowedOrigins = Collections.unmodifiableSet(new HashSet<>(builder.allowedOrigins));
        this.timeoutMs = builder.timeoutMs > 0 ? builder.timeoutMs : 10000L;
        this.debugLogging = builder.debugLogging;
        this.requireOriginValidation = builder.requireOriginValidation;
        this.maxMessageBytes = builder.maxMessageBytes > 0
                ? builder.maxMessageBytes
                : DEFAULT_MAX_MESSAGE_BYTES;
    }

    /**
     * Rejects names that cannot be used as a JavaScript identifier, preventing script
     * injection through the interface name.
     */
    public static String sanitizeInterfaceName(String name) {
        if (name == null) {
            return DEFAULT_INTERFACE_NAME;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || !SAFE_INTERFACE_NAME.matcher(trimmed).matches()) {
            return DEFAULT_INTERFACE_NAME;
        }
        return trimmed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BridgeConfig defaultConfig() {
        return builder().build();
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    /**
     * Whether {@code WebBridge} must validate the page origin before dispatching a message.
     */
    public boolean isOriginValidationRequired() {
        return requireOriginValidation;
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    /**
     * Adds an origin to the allow-list at runtime.
     *
     * <p>Used by the WebView host to trust the per-project virtual origin it is about to load.
     * Copy-on-write keeps the volatile set safe to read from the bridge thread while the UI
     * thread adds an origin.</p>
     */
    public void addAllowedOrigin(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return;
        }
        Set<String> next = new HashSet<>(this.allowedOrigins);
        next.add(origin.trim());
        this.allowedOrigins = Collections.unmodifiableSet(next);
    }

    /**
     * @return true when {@code origin} is explicitly allowed. A {@code null} origin is never
     *         allowed, and an empty allow-list allows nothing.
     */
    public boolean isOriginAllowed(String origin) {
        Set<String> origins = this.allowedOrigins;
        if (origins.contains("*")) {
            return true;
        }
        if (origin == null) {
            return false;
        }
        return origins.contains(origin);
    }

    /**
     * Applies the mutable settings of {@code other} to this instance.
     *
     * <p>{@code interfaceName} is deliberately excluded: it is baked into the WebView via
     * {@code addJavascriptInterface} and cannot be changed safely at runtime.</p>
     */
    void applyRuntimeSettings(BridgeConfig other) {
        if (other == null) {
            return;
        }
        this.allowedOrigins = Collections.unmodifiableSet(new HashSet<>(other.allowedOrigins));
        this.timeoutMs = other.timeoutMs;
        this.debugLogging = other.debugLogging;
        this.requireOriginValidation = other.requireOriginValidation;
        this.maxMessageBytes = other.maxMessageBytes;
    }

    public static final class Builder {
        private String interfaceName = DEFAULT_INTERFACE_NAME;
        private final Set<String> allowedOrigins = new HashSet<>();
        private long timeoutMs = 10000L;
        private boolean debugLogging = false;
        private boolean requireOriginValidation = true;
        private int maxMessageBytes = DEFAULT_MAX_MESSAGE_BYTES;

        public Builder() {
            // The bundled-asset origin is trusted by default; everything else must be added.
            this.allowedOrigins.add(DEFAULT_ALLOWED_ORIGIN);
        }

        public Builder interfaceName(String name) {
            if (name != null && !name.trim().isEmpty()) {
                this.interfaceName = name.trim();
            }
            return this;
        }

        public Builder addAllowedOrigin(String origin) {
            if (origin != null && !origin.trim().isEmpty()) {
                this.allowedOrigins.add(origin.trim());
            }
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder debugLogging(boolean enable) {
            this.debugLogging = enable;
            return this;
        }

        /**
         * Enables or disables origin validation. Defaults to {@code true}.
         */
        public Builder requireOriginValidation(boolean required) {
            this.requireOriginValidation = required;
            return this;
        }

        /**
         * Maximum accepted message size in bytes. Defaults to {@value #DEFAULT_MAX_MESSAGE_BYTES}.
         */
        public Builder maxMessageBytes(int maxBytes) {
            this.maxMessageBytes = maxBytes;
            return this;
        }

        public BridgeConfig build() {
            return new BridgeConfig(this);
        }
    }
}
