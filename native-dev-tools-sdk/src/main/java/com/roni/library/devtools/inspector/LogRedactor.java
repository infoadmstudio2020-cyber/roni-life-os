package com.roni.library.devtools.inspector;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes credentials and private data from anything the Inspector stores or exports.
 *
 * <p>Redaction happens at <b>collection</b> time, not only at export time: a token that never
 * enters the ring buffer cannot leak through a screenshot, a clipboard copy or a crash report.
 * {@link #redact(String)} is therefore applied by the collector to every message, and again by
 * the exporter as a second line of defence.</p>
 */
public final class LogRedactor {

    /** Replacement marker. Kept short so log lines stay readable. */
    public static final String MASK = "***";

    /**
     * {@code key=value} / {@code key: value} / {@code "key":"value"} for sensitive keys.
     * The value is replaced, the key is kept so the log still explains what happened.
     */
    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)([\"']?\\b(?:password|passwd|pwd|secret|token|access_token|refresh_token|"
                    + "id_token|auth|authorization|api[_-]?key|apikey|private[_-]?key|"
                    + "encryption[_-]?key|session[_-]?id|cookie|set-cookie|credential|"
                    + "signature|bearer)\\b[\"']?\\s*[:=]\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s,;&}\\]\\n]+)");

    /** {@code Authorization: Bearer <token>} and similar header forms. */
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=\\-]{8,}");

    /** Query parameters that must never be exported verbatim. */
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)([?&](?:token|access_token|refresh_token|id_token|auth|authorization|"
                    + "api[_-]?key|apikey|key|secret|password|passwd|pwd|signature|sig|"
                    + "session|sessionid|sid)=)([^&#\\s]*)");

    /** Long opaque blobs that look like keys rather than prose. */
    private static final Pattern LONG_SECRET = Pattern.compile(
            "\\b[A-Za-z0-9_\\-]{32,}\\b");

    private LogRedactor() {
    }

    /**
     * Redacts sensitive values in free text.
     *
     * <p>Deliberately conservative: it masks values attached to a sensitive key, bearer/basic
     * credentials and sensitive query parameters. It does not mask every long token, because
     * that would destroy project ids and request ids that the Inspector needs for correlation.</p>
     */
    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Bearer/basic credentials are masked first: the generic key/value rule would otherwise
        // consume the scheme word and leave the token itself in place.
        String output = BEARER.matcher(input).replaceAll("$1 " + MASK);
        output = SENSITIVE_PAIR.matcher(output).replaceAll("$1" + MASK);
        output = SENSITIVE_QUERY.matcher(output).replaceAll("$1" + MASK);
        return output;
    }

    /**
     * Redacts a URL for display and export: sensitive query parameters are masked and the
     * fragment is dropped (it frequently carries an OAuth token).
     */
    public static String redactUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String output = url;
        int hash = output.indexOf('#');
        if (hash >= 0) {
            output = output.substring(0, hash);
        }
        output = SENSITIVE_QUERY.matcher(output).replaceAll("$1" + MASK);
        return redact(output);
    }

    /**
     * Masks a value that is known to be sensitive regardless of its key, keeping a short
     * prefix so the log still shows which credential was used.
     */
    public static String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return MASK;
        }
        if (value.length() <= 4) {
            return MASK;
        }
        return value.substring(0, 2) + MASK;
    }

    /** True when the text still contains something that looks like a raw secret. */
    public static boolean containsSecret(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (SENSITIVE_PAIR.matcher(input).find() || BEARER.matcher(input).find()) {
            return true;
        }
        Matcher matcher = LONG_SECRET.matcher(input);
        while (matcher.find()) {
            String candidate = matcher.group();
            // A UUID or a hex digest is an identifier, not a credential.
            if (!candidate.matches("(?i)[0-9a-f\\-]{32,}")) {
                return true;
            }
        }
        return false;
    }

    /** Lower-cased, trimmed key test used by the bridge collector. */
    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.US);
        return normalized.contains("password") || normalized.contains("passwd")
                || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("apikey") || normalized.contains("api_key")
                || normalized.contains("private_key") || normalized.contains("privatekey")
                || normalized.contains("encryption") || normalized.contains("credential")
                || normalized.equals("auth") || normalized.equals("authorization")
                || normalized.equals("cookie") || normalized.equals("session");
    }
}
