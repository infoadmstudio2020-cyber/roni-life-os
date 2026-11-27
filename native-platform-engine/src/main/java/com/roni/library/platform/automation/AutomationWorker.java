package com.roni.library.platform.automation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** WorkManager entry point that forwards deferred work to the background task engine. */
public final class AutomationWorker extends Worker {

    public static final String KEY_JOB_ID = "jobId";
    public static final String KEY_PROJECT_ID = "projectId";
    public static final String KEY_WORKER_TYPE = "workerType";
    public static final String KEY_PAYLOAD = "payload";

    /** Bridge installed by {@link AutomationService}. */
    public interface Bridge {
        boolean run(@NonNull String projectId, @NonNull String workerType,
                    @NonNull Map<String, Object> payload);
    }

    private static volatile Bridge bridge;

    public static void setBridge(@Nullable Bridge value) {
        bridge = value;
    }

    public AutomationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Bridge current = bridge;
        if (current == null) {
            return Result.retry();
        }
        String projectId = getInputData().getString(KEY_PROJECT_ID);
        if (projectId == null || projectId.trim().isEmpty()) {
            return Result.failure();
        }
        String workerType = getInputData().getString(KEY_WORKER_TYPE);
        String rawPayload = getInputData().getString(KEY_PAYLOAD);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (rawPayload != null && !rawPayload.isEmpty()) {
            try {
                Type type = new TypeToken<Map<String, Object>>() {
                }.getType();
                Map<String, Object> parsed = new Gson().fromJson(rawPayload, type);
                if (parsed != null) {
                    payload.putAll(parsed);
                }
            } catch (Throwable ignored) {
                // A malformed payload must not crash the worker.
            }
        }
        try {
            return current.run(projectId, workerType == null ? "generic" : workerType, payload)
                    ? Result.success() : Result.failure();
        } catch (Throwable error) {
            return Result.retry();
        }
    }
}
