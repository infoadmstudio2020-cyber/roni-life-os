package com.roni.life.os.app.project;

/**
 * Pure decision logic for the sealing step.
 *
 * <p>Kept free of Android imports on purpose: this is the part of the registration pipeline that
 * decides whether a project may be encrypted, and it must be verifiable on a plain JVM without a
 * device, a Keystore or a WebView.</p>
 *
 * <p>The order of the checks is the security contract:</p>
 * <ol>
 *   <li><b>already sealed</b> — a completed tree is never re-encrypted, so a second import pass
 *       cannot double-encrypt and destroy the project;</li>
 *   <li><b>pending marker without a sealed marker</b> — a previous pass started and never
 *       finished, so the tree is part plaintext and part ciphertext. It cannot be served and it
 *       cannot be resumed, because there is no record of which files were already converted.
 *       Refuse, and let the caller discard it;</li>
 *   <li><b>no files</b> — a remote-URL project has no local tree, so there is nothing to seal;</li>
 *   <li><b>vault unavailable</b> — below API 23 there is no Keystore AES/GCM. Refuse rather than
 *       write plaintext under an encrypted-looking name.</li>
 * </ol>
 */
public final class SealDecision {

    /** What the sealer should do for one project directory. */
    public enum Action {
        /** Encrypt the tree now. */
        SEAL,
        /** The tree is already encrypted; do nothing. */
        SKIP_ALREADY_SEALED,
        /** There is no local tree to encrypt. */
        SKIP_NO_FILES,
        /** A previous pass was interrupted; the tree is unusable. */
        REFUSE_INTERRUPTED,
        /** Keystore AES/GCM is not available on this device. */
        REFUSE_UNAVAILABLE
    }

    private SealDecision() {
    }

    /**
     * @param vaultAvailable {@code ProjectVault.isAvailable()}
     * @param sealed         {@code ProjectVault.isSealed(dir)}
     * @param pending        {@code ProjectVault.isSealInterrupted(dir)}
     * @param hasFiles       whether the directory holds at least one sealable file
     */
    public static Action decide(boolean vaultAvailable, boolean sealed, boolean pending,
                                boolean hasFiles) {
        if (sealed) {
            return Action.SKIP_ALREADY_SEALED;
        }
        if (pending) {
            return Action.REFUSE_INTERRUPTED;
        }
        if (!hasFiles) {
            return Action.SKIP_NO_FILES;
        }
        if (!vaultAvailable) {
            return Action.REFUSE_UNAVAILABLE;
        }
        return Action.SEAL;
    }

    /** @return true when the action means "do not register this project" */
    public static boolean isRefusal(Action action) {
        return action == Action.REFUSE_INTERRUPTED || action == Action.REFUSE_UNAVAILABLE;
    }

    /** @return true when the action means "nothing to do, but the project is usable" */
    public static boolean isSkip(Action action) {
        return action == Action.SKIP_ALREADY_SEALED || action == Action.SKIP_NO_FILES;
    }

    /** @return true when the project's bytes are encrypted at rest */
    public static boolean isEncryptedAtRest(Action action) {
        return action == Action.SEAL || action == Action.SKIP_ALREADY_SEALED;
    }
}
