package com.roni.library.ui.web;

/**
 * Standalone checks for {@link WebSource}'s virtual-host plumbing.
 *
 * <p>The host must survive every builder round-trip. If {@code withEntryPoint} or
 * {@code withTitle} silently dropped it, the project would fall back to the shared legacy origin
 * and lose its isolation — a bug that would only show up as two projects sharing localStorage.</p>
 *
 * <p>Plain {@code main} rather than JUnit so it runs with nothing but a JDK.</p>
 */
public final class WebSourceCheck {

    private static int passed;

    public static void main(String[] args) {
        defaultsToNoHost();
        carriesHostThroughBuilders();
        keepsHostAcrossDerivedCopies();
        keepsHostThroughSerializationFields();

        System.out.println("WebSourceCheck: " + passed + " checks passed");
    }

    private static void defaultsToNoHost() {
        eq("", WebSource.file("/tmp/app").getVirtualHost(), "no host by default");
        eq("", WebSource.asset("www").getVirtualHost(), "asset has no host by default");
        eq("", WebSource.zipFile("/tmp/app.zip").getVirtualHost(), "zip has no host by default");
    }

    private static void carriesHostThroughBuilders() {
        WebSource source = WebSource.file("id-1", "My App", "/tmp/app")
                .withVirtualHost("my-app-1a2b3c4d.runtime.local");
        eq("my-app-1a2b3c4d.runtime.local", source.getVirtualHost(), "host is stored");
        eq("id-1", source.getId(), "id survives");
        eq("/tmp/app", source.getLocation(), "location survives");
    }

    private static void keepsHostAcrossDerivedCopies() {
        WebSource base = WebSource.file("id-1", "My App", "/tmp/app")
                .withVirtualHost("my-app-1a2b3c4d.runtime.local");

        eq("my-app-1a2b3c4d.runtime.local", base.withEntryPoint("main.html").getVirtualHost(),
                "withEntryPoint keeps the host");
        eq("my-app-1a2b3c4d.runtime.local", base.withTitle("Renamed").getVirtualHost(),
                "withTitle keeps the host");
        eq("my-app-1a2b3c4d.runtime.local", base.withBaseUrl("https://x/").getVirtualHost(),
                "withBaseUrl keeps the host");
        eq("my-app-1a2b3c4d.runtime.local", base.withHeader("X", "1").getVirtualHost(),
                "withHeader keeps the host");
        eq("my-app-1a2b3c4d.runtime.local", base.toBuilder().build().getVirtualHost(),
                "toBuilder keeps the host");

        WebSource renamed = base.withTitle("Renamed");
        eq("main.html", renamed.withEntryPoint("main.html").getEntryPoint(),
                "entry point is applied");
    }

    private static void keepsHostThroughSerializationFields() {
        WebSource source = WebSource.builder(WebSourceType.ZIP)
                .id("id-2")
                .title("Bundle")
                .location("/tmp/bundle.zip")
                .fromAssets(false)
                .virtualHost("bundle-9f8e7d6c.runtime.local")
                .entryPoint("index.html")
                .build();
        eq("bundle-9f8e7d6c.runtime.local", source.getVirtualHost(), "builder sets the host");
        eq(WebSourceType.ZIP, source.getType(), "type survives");
        eq("index.html", source.getEntryPoint(), "entry point survives");
    }

    // ==================== harness ====================

    private static void eq(Object want, Object got, String label) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(label + ": expected " + want + " but got " + got);
        }
        passed++;
    }

    private static void fail(String message) {
        System.err.println("FAIL: " + message);
        System.exit(1);
    }
}
