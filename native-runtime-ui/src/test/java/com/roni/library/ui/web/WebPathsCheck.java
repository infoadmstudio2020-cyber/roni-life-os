package com.roni.library.ui.web;

/**
 * Standalone checks for {@link WebPaths} — the two security-critical rules of the WebView host:
 * which request paths are allowed, and which origin a project is served from.
 *
 * <p>Plain {@code main} rather than JUnit so it runs with nothing but a JDK. Exits non-zero on the
 * first failure.</p>
 */
public final class WebPathsCheck {

    private static int passed;

    public static void main(String[] args) {
        normalizes();
        rejectsTraversal();
        rejectsEncodedTraversal();
        derivesStableHosts();
        derivesDistinctHosts();
        producesDnsSafeHosts();
        fallsBackToLegacyHost();
        buildsUrls();

        System.out.println("WebPathsCheck: " + passed + " checks passed");
    }

    private static void normalizes() {
        eq("js/app.js", WebPaths.normalize("/js/app.js"), "leading slash stripped");
        eq("js/app.js", WebPaths.normalize("js//app.js"), "duplicate slash collapsed");
        eq("js/app.js", WebPaths.normalize("\\js\\app.js"), "backslashes normalised");
        eq("js/app.js", WebPaths.normalize("  /js/app.js  "), "whitespace trimmed");
        eq("js", WebPaths.normalize("js/"), "trailing slash stripped");
        eq("", WebPaths.normalize(""), "empty stays empty");
        eq("", WebPaths.normalize(null), "null becomes empty");
        eq("", WebPaths.normalize("/"), "root becomes empty");
        eq("a/b/c", WebPaths.normalize("///a///b///c///"), "repeated separators collapse");
    }

    private static void rejectsTraversal() {
        check(!WebPaths.isSafe("../etc/passwd"), "parent traversal rejected");
        check(!WebPaths.isSafe("/js/../../etc/passwd"), "nested traversal rejected");
        check(!WebPaths.isSafe("js\\..\\..\\secret"), "backslash traversal rejected");
        check(!WebPaths.isSafe(".."), "bare parent rejected");
        check(!WebPaths.isSafe("a/.."), "trailing parent rejected");
        check(!WebPaths.isSafe("a\0b"), "NUL byte rejected");
        check(!WebPaths.isSafe(null), "null rejected");

        check(WebPaths.isSafe("/index.html"), "plain file allowed");
        check(WebPaths.isSafe("/js/app.js"), "nested file allowed");
        check(WebPaths.isSafe("/assets/logo.svg"), "asset allowed");
        check(WebPaths.isSafe("/a..b/c.js"), "a name containing dots is not traversal");
        check(WebPaths.isSafe("/"), "root allowed");
    }

    private static void rejectsEncodedTraversal() {
        // The WebView hands over an already-decoded path, so a surviving %2e means the request
        // was double-encoded and cannot be trusted.
        check(!WebPaths.isSafe("/js/%2e%2e/secret"), "encoded dot rejected");
        check(!WebPaths.isSafe("/js/%2E%2E/secret"), "uppercase encoded dot rejected");
        check(!WebPaths.isSafe("/js%2fsecret"), "encoded slash rejected");
        check(!WebPaths.isSafe("/js%5csecret"), "encoded backslash rejected");
    }

    private static void derivesStableHosts() {
        String id = "11111111-2222-3333-4444-555555555555";
        eq(WebPaths.virtualHostFor(id), WebPaths.virtualHostFor(id), "same id gives same host");
        eq(WebPaths.virtualHostFor(id), WebPaths.virtualHostFor("  " + id + "  "),
                "surrounding whitespace does not change the host");
        check(WebPaths.virtualHostFor(id).endsWith(WebPaths.VIRTUAL_SUFFIX),
                "host carries the runtime suffix");
    }

    private static void derivesDistinctHosts() {
        String a = WebPaths.virtualHostFor("11111111-2222-3333-4444-555555555555");
        String b = WebPaths.virtualHostFor("99999999-8888-7777-6666-555555555555");
        check(!a.equals(b), "different ids give different hosts");

        // Two ids that slugify identically must still differ, or they would share an origin.
        String c = WebPaths.virtualHostFor("my app");
        String d = WebPaths.virtualHostFor("my-app");
        check(!c.equals(d), "ids that slugify alike still get distinct hosts");
    }

    private static void producesDnsSafeHosts() {
        String[] ids = {
                "11111111-2222-3333-4444-555555555555",
                "My App / v2 (beta)",
                "___",
                "a",
                "プロジェクト-1",
                "UPPER_CASE_ID"
        };
        for (String id : ids) {
            String host = WebPaths.virtualHostFor(id);
            check(host.length() <= 63, "host fits a DNS label: " + host);
            check(host.endsWith(WebPaths.VIRTUAL_SUFFIX), "host has the suffix: " + host);
            String label = host.substring(0, host.length() - WebPaths.VIRTUAL_SUFFIX.length());
            check(!label.isEmpty(), "label is not empty: " + host);
            check(label.charAt(0) != '-', "label does not start with a hyphen: " + host);
            check(label.charAt(label.length() - 1) != '-',
                    "label does not end with a hyphen: " + host);
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
                check(ok, "label character '" + c + "' is DNS-safe in " + host);
            }
        }
    }

    private static void fallsBackToLegacyHost() {
        eq(WebPaths.LEGACY_HOST, WebPaths.virtualHostFor(null), "null id uses the legacy host");
        eq(WebPaths.LEGACY_HOST, WebPaths.virtualHostFor(""), "empty id uses the legacy host");
        eq(WebPaths.LEGACY_HOST, WebPaths.virtualHostFor("   "), "blank id uses the legacy host");
    }

    private static void buildsUrls() {
        eq("https://example.runtime.local", WebPaths.originOf("example.runtime.local"),
                "origin is https");
        eq("https://example.runtime.local/js/app.js",
                WebPaths.virtualUrl("example.runtime.local", "/js/app.js"),
                "url joins host and normalised path");
        eq("https://example.runtime.local/index.html",
                WebPaths.virtualUrl("example.runtime.local", "index.html"),
                "url works without a leading slash");
        eq("https://" + WebPaths.LEGACY_HOST, WebPaths.originOf(null),
                "null host falls back to legacy");
    }

    // ==================== harness ====================

    private static void eq(Object want, Object got, String label) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(label + ": expected " + want + " but got " + got);
        }
        passed++;
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            fail(label);
        }
        passed++;
    }

    private static void fail(String message) {
        System.err.println("FAIL: " + message);
        System.exit(1);
    }
}
