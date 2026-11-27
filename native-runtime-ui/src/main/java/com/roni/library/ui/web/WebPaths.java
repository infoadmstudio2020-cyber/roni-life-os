package com.roni.library.ui.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Path and virtual-origin rules for the WebView host.
 *
 * <p>Deliberately free of Android imports so the security-critical parts — traversal rejection
 * and origin derivation — can be exercised on a plain JVM.</p>
 *
 * <h3>Why a per-project origin</h3>
 *
 * <p>A single shared host for every local source means every project shares one browser origin,
 * and therefore one {@code localStorage}, one {@code IndexedDB}, one cookie jar and one
 * service-worker scope. Project A's JavaScript could read what project B persisted. Giving each
 * project its own host makes the browser enforce the isolation the resource provider already
 * enforces in native code.</p>
 *
 * <p>The host is derived from the project id alone, never from the project name: a rename must
 * not move the origin, or the project would silently lose its stored data.</p>
 */
public final class WebPaths {

    /** Suffix shared by every per-project virtual host. */
    public static final String VIRTUAL_SUFFIX = ".runtime.local";

    /** The single host used before per-project origins existed. */
    public static final String LEGACY_HOST = "appassets.androidplatform.net";

    /** Longest slug kept from the project id; the hash suffix carries uniqueness. */
    private static final int MAX_SLUG = 40;

    private WebPaths() {
    }

    /**
     * Normalises a request path: backslashes become slashes, repeated separators collapse, and
     * leading/trailing separators are removed.
     *
     * <p>Does <em>not</em> percent-decode. Decoding is the provider's job, and doing it twice is
     * how {@code %252e%252e} becomes {@code ..}.</p>
     */
    public static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String value = path.trim().replace('\\', '/');
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '/') {
                    out.append('/');
                }
            } else {
                out.append(c);
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '/') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    /**
     * Rejects a request path that could escape the project root.
     *
     * <p>Checked before the provider sees the path, so every provider inherits the same rule
     * instead of each one re-implementing it. Encoded separators are rejected outright: the
     * WebView hands over an already-decoded path, so a surviving {@code %2e} means the request
     * was double-encoded and cannot be trusted.</p>
     */
    public static boolean isSafe(String path) {
        if (path == null) {
            return false;
        }
        if (path.indexOf('\0') >= 0) {
            return false;
        }
        String lower = path.toLowerCase(Locale.US);
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) {
            return false;
        }
        String value = path.replace('\\', '/');
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Derives the virtual host for a project.
     *
     * <p>Format: {@code <slug>-<hash8>.runtime.local}. The slug keeps the origin readable in
     * logs; the hash makes it collision-free even when two ids slugify to the same text.</p>
     *
     * @return the legacy shared host when {@code projectId} is null or blank
     */
    public static String virtualHostFor(String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return LEGACY_HOST;
        }
        String id = projectId.trim();
        StringBuilder slug = new StringBuilder();
        for (int i = 0; i < id.length() && slug.length() < MAX_SLUG; i++) {
            char c = Character.toLowerCase(id.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                slug.append(c);
            } else if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
        if (slug.length() == 0) {
            slug.append("project");
        }
        return slug + "-" + shortHash(id) + VIRTUAL_SUFFIX;
    }

    /** @return {@code https://<host>} */
    public static String originOf(String host) {
        return "https://" + (host != null ? host : LEGACY_HOST);
    }

    /** @return {@code https://<host>/<normalised path>} */
    public static String virtualUrl(String host, String path) {
        return originOf(host) + "/" + normalize(path);
    }

    /** First four bytes of SHA-256, as eight lowercase hex characters. */
    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hex.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception unavailable) {
            // SHA-256 is mandatory on every JVM and Android release; this cannot happen.
            return "00000000";
        }
    }
}
