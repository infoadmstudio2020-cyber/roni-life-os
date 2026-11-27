package com.roni.life.os.app.project;

/**
 * Standalone checks for {@link ProjectManifest} path and id validation.
 *
 * <p>Pure Java — no Android, no JUnit, no device. Compile and run with:</p>
 * <pre>
 *   javac -d /tmp/out ProjectManifest.java ProjectManifestCheck.java
 *   java -cp /tmp/out com.roni.life.os.app.project.ProjectManifestCheck
 * </pre>
 *
 * <p>Exits non-zero on the first failure.</p>
 */
public final class ProjectManifestCheck {

    private static int passed;

    public static void main(String[] args) {
        safePaths();
        traversalPaths();
        absolutePaths();
        safeIds();
        unsafeIds();

        System.out.println("ProjectManifestCheck: " + passed + " checks passed");
    }

    private static void safePaths() {
        accept("index.html");
        accept("ui/index.html");
        accept("a/b/c/index.html");
        accept("index.html?x=1");
        accept("my-app/index.html");
    }

    private static void traversalPaths() {
        reject("../index.html");
        reject("ui/../../etc/passwd");
        reject("./index.html");
        reject("ui/./index.html");
        reject("..");
        reject(".");
        reject("a/..");
        reject("a\\..\\b");
        reject("index\0.html");
    }

    private static void absolutePaths() {
        reject("/etc/passwd");
        reject("/index.html");
        reject("C:/Windows/system32");
        reject("c:index.html");
    }

    private static void safeIds() {
        acceptId("my-app");
        acceptId("proj_1731");
        acceptId("A1.b2-c3");
        acceptId("0123456789abcdef0123456789abcdef");
    }

    private static void unsafeIds() {
        rejectId("");
        rejectId(null);
        rejectId("../evil");
        rejectId("a/b");
        rejectId("a b");
        rejectId("a\0b");
        rejectId("a".repeat(65));
    }

    // ==================== harness ====================

    private static void accept(String path) {
        if (!ProjectManifest.isSafeRelativePath(path)) {
            fail("expected a safe path: " + path);
        }
        passed++;
    }

    private static void reject(String path) {
        if (ProjectManifest.isSafeRelativePath(path)) {
            fail("expected an unsafe path: " + path);
        }
        passed++;
    }

    private static void acceptId(String id) {
        if (!ProjectManifest.isSafeId(id)) {
            fail("expected a safe id: " + id);
        }
        passed++;
    }

    private static void rejectId(String id) {
        if (ProjectManifest.isSafeId(id)) {
            fail("expected an unsafe id: " + id);
        }
        passed++;
    }

    private static void fail(String message) {
        System.err.println("FAIL: " + message);
        System.exit(1);
    }
}
