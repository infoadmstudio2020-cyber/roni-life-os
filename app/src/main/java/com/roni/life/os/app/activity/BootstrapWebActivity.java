package com.roni.life.os.app.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.os.activity.NativeRuntimeActivity;
import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.runtime.SafWebSourceProvider;
import com.roni.life.os.app.runtime.WebSourceFactory;
import com.roni.life.os.app.util.AppLog;

/**
 * Hosts a {@link com.roni.life.os.app.project.ProjectType#BOOTSTRAP} project that renders
 * through a WebView.
 *
 * <p>This is not a second WebView implementation: it extends the same
 * {@link NativeRuntimeActivity} that {@code ProjectActivity} uses, so the WebViewHost,
 * WebSourceResolver, resource providers and NativeWebBridgeSDK are all shared. Only the
 * lifecycle differs — a Bootstrap project initialises the runtime and then hands off, whereas a
 * WEB project is the destination itself.</p>
 *
 * <p>Back navigation follows the WebView history first and then returns to whatever opened this
 * screen (the dashboard for a manual launch, the startup destination otherwise). It never
 * re-enters Splash.</p>
 */
public class BootstrapWebActivity extends NativeRuntimeActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private Project project;
    private boolean loaded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // onConfigureUI() runs inside super.onCreate(), so the project must be resolved first.
        String id = getIntent() != null ? getIntent().getStringExtra(EXTRA_PROJECT_ID) : null;
        project = ApplicationCore.get().getStore().findById(id);

        super.onCreate(savedInstanceState);

        if (project == null) {
            AppLog.w("BootstrapWebActivity: project not found — closing");
            finish();
            return;
        }
        if (!project.isBootstrapProject()) {
            AppLog.w("BootstrapWebActivity: project " + project.getId()
                    + " is not a BOOTSTRAP project — closing");
            finish();
            return;
        }
        if (!project.isWebBootstrap()) {
            AppLog.w("BootstrapWebActivity: project " + project.getId()
                    + " renders natively — closing");
            finish();
            return;
        }

        loadBootstrapContent();
    }

    private void loadBootstrapContent() {
        if (loaded) {
            return;
        }
        WebSource source = resolveSource();
        if (source == null) {
            return;
        }
        loaded = true;
        try {
            loadWebApp(source);
            AppLog.i("Bootstrap web project loaded: " + project.getName());
        } catch (Throwable error) {
            AppLog.e("Unable to load bootstrap web project", error);
            showToast("Unable to load the bootstrap project");
        }
    }

    /** Mirrors the SAF validation used by the application project host. */
    private WebSource resolveSource() {
        if (project.getSourceType() == ProjectSourceType.SAF_FOLDER) {
            SafWebSourceProvider.Prepared prepared = SafWebSourceProvider.prepare(this, project);
            if (!prepared.isReady()) {
                showToast(prepared.getMessage());
                return null;
            }
            return prepared.getSource();
        }
        return WebSourceFactory.from(project);
    }

    @Override
    protected NativeRuntimeUIConfig onConfigureUI(@NonNull NativeRuntimeUIConfig.Builder builder) {
        String title = project != null ? project.getName() : "Bootstrap";
        builder.toolbarTitle(title).toolbarSubtitle("Bootstrap");
        builder.enableWebView(true);
        return builder.build();
    }

    @Override
    public void onBackPressed() {
        try {
            WebViewHost host = getWebViewHost();
            if (host != null && host.canGoBack()) {
                host.goBack();
                return;
            }
        } catch (Throwable ignored) {
            // WebView unavailable — fall through and close.
        }
        // Returning to the caller is the correct behaviour: the dashboard for a manual launch,
        // the startup destination otherwise. Splash is never re-entered.
        finish();
    }
}
