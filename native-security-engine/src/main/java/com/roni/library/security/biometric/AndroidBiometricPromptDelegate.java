package com.roni.library.security.biometric;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;

/**
 * Concrete {@link BiometricPromptDelegate} backed by {@code androidx.biometric}.
 *
 * <p><b>SEC5:</b> without a concrete delegate the only reachable authentication path was the
 * headless simulation branch. This implementation presents a real system prompt and forwards
 * every outcome to the supplied {@link BiometricAuthCallback}.</p>
 *
 * <p>Must be constructed with a {@link FragmentActivity}; {@code :app} activities extend
 * {@code AppCompatActivity}, which satisfies that requirement.</p>
 */
public class AndroidBiometricPromptDelegate implements BiometricPromptDelegate {

    /** Reported when no host activity is available to show a prompt. */
    public static final int ERROR_NO_HOST = -1000;

    private final FragmentActivity activity;
    private final Executor executor;

    public AndroidBiometricPromptDelegate(@NonNull FragmentActivity activity) {
        this.activity = activity;
        this.executor = ContextCompat.getMainExecutor(activity);
    }

    @Override
    public void showPrompt(BiometricPromptConfig config, Cipher cipher,
                           BiometricAuthCallback callback) {
        if (callback == null) {
            return;
        }
        if (activity == null || activity.isFinishing()
                || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed())) {
            callback.onAuthenticationError(ERROR_NO_HOST,
                    "No foreground activity is available for the biometric prompt");
            return;
        }

        BiometricPromptConfig effective = config != null
                ? config : BiometricPromptConfig.builder().build();

        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        Cipher resultCipher = cipher;
                        if (result.getCryptoObject() != null
                                && result.getCryptoObject().getCipher() != null) {
                            resultCipher = result.getCryptoObject().getCipher();
                        }
                        callback.onAuthenticationSucceeded("BIOMETRIC", resultCipher);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        callback.onAuthenticationError(errorCode, errString.toString());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        callback.onAuthenticationFailed("BIOMETRIC_NOT_RECOGNIZED");
                    }
                });

        try {
            BiometricPrompt.PromptInfo info = buildPromptInfo(effective);
            if (cipher != null) {
                prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
            } else {
                prompt.authenticate(info);
            }
        } catch (Throwable t) {
            callback.onAuthenticationError(ERROR_NO_HOST,
                    t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    /**
     * Maps {@link BiometricPromptConfig} onto {@link BiometricPrompt.PromptInfo}.
     *
     * <p>{@code setNegativeButtonText} must not be combined with
     * {@code DEVICE_CREDENTIAL} — the platform throws {@link IllegalArgumentException}.</p>
     */
    static BiometricPrompt.PromptInfo buildPromptInfo(BiometricPromptConfig config) {
        int authenticators = mapAuthenticators(config.getAllowedAuthenticators());
        boolean deviceCredentialAllowed =
                (authenticators & BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0;

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(config.getTitle() != null && !config.getTitle().isEmpty()
                        ? config.getTitle() : "Authentication Required")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(config.isConfirmationRequired());

        if (config.getSubtitle() != null && !config.getSubtitle().isEmpty()) {
            builder.setSubtitle(config.getSubtitle());
        }
        if (config.getDescription() != null && !config.getDescription().isEmpty()) {
            builder.setDescription(config.getDescription());
        }
        if (!deviceCredentialAllowed) {
            String negative = config.getNegativeButtonText();
            builder.setNegativeButtonText(negative != null && !negative.isEmpty()
                    ? negative : "Cancel");
        }
        return builder.build();
    }

    private static int mapAuthenticators(int requested) {
        int mapped = 0;
        if ((requested & AuthenticatorType.DEVICE_CREDENTIAL) != 0) {
            mapped |= BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        }
        if ((requested & AuthenticatorType.BIOMETRIC_WEAK) != 0) {
            mapped |= BiometricManager.Authenticators.BIOMETRIC_WEAK;
        } else if ((requested & AuthenticatorType.BIOMETRIC_STRONG) != 0) {
            mapped |= BiometricManager.Authenticators.BIOMETRIC_STRONG;
        }
        if (mapped == 0) {
            mapped = BiometricManager.Authenticators.BIOMETRIC_STRONG;
        }
        return mapped;
    }
}
