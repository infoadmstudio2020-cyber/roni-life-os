package com.roni.library.os.activity;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.os.LifecycleObserver;
import com.roni.library.os.NativeRuntime;
import com.roni.library.os.RuntimeSession;
import com.roni.library.os.integration.NativeApiIntegration;
import com.roni.library.ui.NativeRuntimeUIConfig;

/**
 * Standard host Activity tying Activity lifecycle events directly into NativeRuntimeSDK
 * and acting as the single consumer entry point for declarative UI configuration.
 * Extends com.roni.ui.life.NativeRuntimeActivity so consumers enjoy rich, fully functional
 * live UI components (Toolbar, Drawer, Bottom Nav, Toasts, Dialogs, Loading Overlay, etc.).
 */
public class NativeRuntimeActivity extends com.roni.library.ui.NativeRuntimeActivity implements LifecycleObserver {

    protected NativeRuntime runtime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 1. Let AppCompatActivity finish attaching before any subsystem touches the Activity.
        super.onCreate(savedInstanceState);

        // 2. Initialize Master Runtime
        runtime = NativeRuntime.getInstance();
        if (!runtime.isInitialized()) {
            runtime.init(this);
        }
        runtime.addObserver(this);

        // 3. Register the nine engine handlers on the bridge router.
        //    super.onCreate() already initialised NativeRuntimeUI, so the bridge exists.
        NativeApiIntegration.getInstance().install(this, this);
    }

    @Override
    @Nullable
    protected NativeRuntimeUIConfig onConfigureUI(
            @NonNull NativeRuntimeUIConfig.Builder builder) {

        return builder.build();
    }
    @NonNull
    public NativeRuntime getRuntime() {
        if (runtime == null) {
            runtime = NativeRuntime.getInstance();
        }
        return runtime;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (runtime != null && runtime.isInitialized()) {
            runtime.resumeSession();
        }
        // Re-attach the biometric host after an Activity recreation.
        NativeApiIntegration.getInstance().attachActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (runtime != null && runtime.isInitialized()) {
            runtime.pauseSession();
        }
    }

    @Override
    protected void onDestroy() {
        if (runtime != null) {
            runtime.removeObserver(this);
        }
        super.onDestroy();
    }

    @Override
    public void onInitialized(NativeRuntime runtime) {}

    @Override
    public void onSessionStarted(RuntimeSession session) {}

    @Override
    public void onSessionPaused(RuntimeSession session) {}

    @Override
    public void onSessionResumed(RuntimeSession session) {}

    @Override
    public void onSessionStopped(RuntimeSession session) {}

    @Override
    public void onReleased() {}

    @Override
    public void onError(int errorCode, String errorMessage) {}
}
