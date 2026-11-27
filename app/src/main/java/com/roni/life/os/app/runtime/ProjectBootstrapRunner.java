package com.roni.life.os.app.runtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebSourceType;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectValidator;
import com.roni.life.os.app.resource.ProjectResourceFactory;
import com.roni.life.os.app.resource.ProjectResourceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * প্রজেক্ট-স্তরের Bootstrap প্রক্রিয়া।
 *
 * <p>ধাপগুলো: ভ্যালিডেশন → সোর্স পরীক্ষা → Runtime readyি → ওয়েব সোর্স রেজলভ।
 * সব কাজ ব্যাকগ্রাউন্ড থ্রেডে হয়, ফলাফল মেইন থ্রেডে ফেরত আসে।</p>
 */
public final class ProjectBootstrapRunner {

    /** ধাপ ও ফলাফল জানানোর কলব্যাক। */
    public interface Callback {
        void onStep(String step);

        void onFinished(Outcome outcome);
    }

    /** বুটস্ট্র্যাপের চূড়ান্ত ফলাফল। */
    public static final class Outcome {
        private final boolean success;
        private final String message;
        private final WebSource source;
        private final List<String> steps;

        Outcome(boolean success, String message, WebSource source, List<String> steps) {
            this.success = success;
            this.message = message != null ? message : "";
            this.source = source;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public WebSource getSource() {
            return source;
        }

        public List<String> getSteps() {
            return steps;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void run(final Context context, final Project project, final Callback callback) {
        final Context appContext = context.getApplicationContext();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                execute(appContext, project, callback);
            }
        }, "project-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private void execute(Context context, Project project, Callback callback) {
        List<String> steps = new ArrayList<>();
        try {
            // Resources are the only thing that can require the network. They are prepared once,
            // at first registration, and re-verified (not re-downloaded) on every later open.
            postStep(callback, "Checking resources…");
            ProjectResourceManager.Result resources =
                    ProjectResourceFactory.forApp(context).ensureReady(project, null);
            steps.add("Resources: " + resources.getMessage());
            if (!resources.isReady()) {
                finish(callback, new Outcome(false, resources.getMessage(), null, steps));
                return;
            }

            if (!resources.isFromCache()) {
                // First initialization only: validate the configuration and prove the source is
                // reachable before the runtime is built.
                postStep(callback, "Validating project…");
                ProjectValidator.Result validation = ProjectValidator.validate(project, null);
                if (!validation.isValid()) {
                    steps.add("Validation failed: " + validation.getFirstError());
                    finish(callback, new Outcome(false, validation.getFirstError(), null, steps));
                    return;
                }
                steps.add("Validation passed");

                postStep(callback, "Checking project source…");
                ProjectSourceInspector.Inspection inspection =
                        ProjectSourceInspector.inspect(context, project);
                steps.add("Source: " + inspection.getMessage());
                if (!inspection.isAvailable()) {
                    finish(callback, new Outcome(false, inspection.getMessage(), null, steps));
                    return;
                }
            } else {
                // Clean open: the resources were verified against the real files, so the
                // configuration and source checks are already known to pass.
                steps.add("Validation skipped (resources already verified)");
            }

            postStep(callback, "Preparing runtime…");
            boolean runtimeReady = RuntimeBridge.get().isReady();
            steps.add("Runtime: " + (runtimeReady ? "ready" : "limited mode (no engines)"));

            postStep(callback, "Resolving web source…");
            WebSource source = resources.getLocalRoot() != null
                    ? WebSource.file(project.getId(), project.getName(),
                            resources.getLocalRoot().getAbsolutePath())
                            .withEntryPoint(resources.getEntryPoint())
                    : WebSourceFactory.from(project);
            if (resources.isRefreshRequired() && source.getType() == WebSourceType.REMOTE_URL) {
                // The remote document changed: load it once from the network instead of the
                // cached copy. Only the entry URL is busted; the rest of the cache is kept.
                source = WebSource.remote(project.getId(), project.getName(),
                        withVersion(source.getLocation(), resources.getVersionTag()));
            }
            steps.add("Source resolved: " + source.getType().name());

            finish(callback, new Outcome(true, "Project ready", source, steps));
        } catch (Throwable throwable) {
            String message = throwable.getMessage() != null
                    ? throwable.getMessage() : throwable.getClass().getSimpleName();
            steps.add("Error: " + message);
            finish(callback, new Outcome(false, message, null, steps));
        }
    }

    /**
     * Appends the remote tag as a query parameter so a changed document bypasses the cached copy.
     *
     * <p>Only the entry URL is affected; every other cached asset keeps its own URL and stays
     * cached.</p>
     */
    private static String withVersion(String url, String tag) {
        if (url == null || url.isEmpty() || tag == null || tag.trim().isEmpty()) {
            return url;
        }
        String clean = tag.trim().replace("\"", "").replace(" ", "");
        if (clean.isEmpty()) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? "&" : "?") + "__v=" + clean;
    }

    private void postStep(final Callback callback, final String step) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onStep(step);
            }
        });
    }

    private void finish(final Callback callback, final Outcome outcome) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFinished(outcome);
            }
        });
    }
}
