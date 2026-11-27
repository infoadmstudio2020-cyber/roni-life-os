package com.roni.life.os.app.project;

/**
 * Standalone checks for {@link SealDecision}.
 *
 * <p>Pure Java — no Android, no JUnit, no device. Compile and run with:</p>
 * <pre>
 *   javac -d /tmp/out SealDecision.java SealDecisionCheck.java
 *   java -cp /tmp/out com.roni.life.os.app.project.SealDecisionCheck
 * </pre>
 *
 * <p>Exits non-zero on the first failure.</p>
 */
public final class SealDecisionCheck {

    private static int passed;

    public static void main(String[] args) {
        freshTreeSeals();
        alreadySealedIsSkipped();
        interruptedIsRefused();
        sealedWinsOverStalePending();
        noFilesIsSkipped();
        unavailableIsRefused();
        refusalAndSkipClassification();
        encryptedAtRestClassification();

        System.out.println("SealDecisionCheck: " + passed + " checks passed");
    }

    // A clean tree on a capable device must be sealed.
    private static void freshTreeSeals() {
        expect("fresh tree seals",
                SealDecision.Action.SEAL,
                SealDecision.decide(true, false, false, true));
    }

    // Double encryption is prevented: a sealed tree is never sealed again.
    private static void alreadySealedIsSkipped() {
        expect("already sealed is skipped",
                SealDecision.Action.SKIP_ALREADY_SEALED,
                SealDecision.decide(true, true, false, true));
    }

    // A pending marker with no sealed marker means the pass died mid-way.
    private static void interruptedIsRefused() {
        expect("interrupted is refused",
                SealDecision.Action.REFUSE_INTERRUPTED,
                SealDecision.decide(true, false, true, true));
    }

    // Sealed + stale pending = the pass completed, only the cleanup died. Must not refuse.
    private static void sealedWinsOverStalePending() {
        expect("sealed wins over stale pending",
                SealDecision.Action.SKIP_ALREADY_SEALED,
                SealDecision.decide(true, true, true, true));
    }

    // A remote-URL project has no local tree.
    private static void noFilesIsSkipped() {
        expect("no files is skipped",
                SealDecision.Action.SKIP_NO_FILES,
                SealDecision.decide(true, false, false, false));
    }

    // API < 23: refuse rather than write plaintext under an encrypted-looking name.
    private static void unavailableIsRefused() {
        expect("unavailable is refused",
                SealDecision.Action.REFUSE_UNAVAILABLE,
                SealDecision.decide(false, false, false, true));
    }

    private static void refusalAndSkipClassification() {
        check("REFUSE_INTERRUPTED is a refusal",
                SealDecision.isRefusal(SealDecision.Action.REFUSE_INTERRUPTED));
        check("REFUSE_UNAVAILABLE is a refusal",
                SealDecision.isRefusal(SealDecision.Action.REFUSE_UNAVAILABLE));
        check("SEAL is not a refusal", !SealDecision.isRefusal(SealDecision.Action.SEAL));
        check("SKIP_ALREADY_SEALED is a skip",
                SealDecision.isSkip(SealDecision.Action.SKIP_ALREADY_SEALED));
        check("SKIP_NO_FILES is a skip",
                SealDecision.isSkip(SealDecision.Action.SKIP_NO_FILES));
        check("SEAL is not a skip", !SealDecision.isSkip(SealDecision.Action.SEAL));
    }

    private static void encryptedAtRestClassification() {
        check("SEAL is encrypted at rest",
                SealDecision.isEncryptedAtRest(SealDecision.Action.SEAL));
        check("SKIP_ALREADY_SEALED is encrypted at rest",
                SealDecision.isEncryptedAtRest(SealDecision.Action.SKIP_ALREADY_SEALED));
        check("SKIP_NO_FILES is not encrypted at rest",
                !SealDecision.isEncryptedAtRest(SealDecision.Action.SKIP_NO_FILES));
        check("REFUSE_UNAVAILABLE is not encrypted at rest",
                !SealDecision.isEncryptedAtRest(SealDecision.Action.REFUSE_UNAVAILABLE));
    }

    // ==================== harness ====================

    private static void expect(String label, SealDecision.Action want, SealDecision.Action got) {
        if (want != got) {
            fail(label + ": expected " + want + " but got " + got);
        }
        passed++;
    }

    private static void check(String label, boolean condition) {
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
