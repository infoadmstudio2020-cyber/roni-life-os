package com.roni.life.os.app.core;

import android.content.Context;

import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.runtime.RuntimeBridge;

/**
 * অ্যাপ-প্রসেসের একক কেন্দ্রীয় বিন্দু। Application, Activity ও হেল্পার সবাই এখান থেকেই
 * কনফিগারেশন, প্রজেক্ট স্টোর ও রানটাইম স্ট্যাটাস পায়।
 */
public final class ApplicationCore {

    private static volatile ApplicationCore instance;

    private Context appContext;
    private ProjectStore store;
    private AppConfig config;
    private boolean runtimeReady;
    private String runtimeMessage = "";
    private long initDurationMs;
    private boolean initialized;
    private RONiLiFEOSStorageInitializer.Result storageResult;

    private ApplicationCore() {
    }

    public static ApplicationCore get() {
        if (instance == null) {
            synchronized (ApplicationCore.class) {
                if (instance == null) {
                    instance = new ApplicationCore();
                }
            }
        }
        return instance;
    }

    /** লগিং থেকে নিরাপদে অ্যাক্সেসের জন্য — কখনো নতুন ইনস্ট্যান্স তৈরি করে না। */
    public static ApplicationCore peek() {
        return instance;
    }

    public synchronized void init(Context context) {
        if (initialized) {
            return;
        }
        this.appContext = context.getApplicationContext();
        AppInitializer.Result result = AppInitializer.initialize(this.appContext);
        this.store = result.getStore();
        this.config = result.getConfig();
        this.runtimeReady = result.isRuntimeReady();
        this.runtimeMessage = result.getRuntimeMessage();
        this.initDurationMs = result.getDurationMs();
        this.storageResult = result.getStorage();
        this.initialized = true;
    }

    /** Storage initialisation outcome, or {@code null} before {@link #init(Context)} runs. */
    public RONiLiFEOSStorageInitializer.Result getStorageResult() {
        return storageResult;
    }

    /** True when the storage tree was created and validated on this launch. */
    public boolean isStorageReady() {
        return storageResult != null && storageResult.isSuccess();
    }

    public Context getContext() {
        return appContext;
    }

    public ProjectStore getStore() {
        return store;
    }

    public AppConfig getConfig() {
        if (config == null) {
            config = store != null ? store.loadConfig() : new AppConfig();
        }
        return config;
    }

    public void saveConfig(AppConfig newConfig) {
        if (newConfig == null) {
            return;
        }
        this.config = newConfig;
        if (store != null) {
            store.saveConfig(newConfig);
        }
    }

    public boolean isRuntimeReady() {
        return runtimeReady && RuntimeBridge.get().isReady();
    }

    public String getRuntimeMessage() {
        return runtimeMessage;
    }

    public long getInitDurationMs() {
        return initDurationMs;
    }

    public void log(int priority, String message) {
        RuntimeBridge.get().log(priority, message);
    }

    public void track(String event, java.util.Map<String, Object> params) {
        if (getConfig().isTelemetryEnabled()) {
            RuntimeBridge.get().track(event, params);
        }
    }

    /** টেস্ট বা প্রসেস রিস্টার্টের জন্য সিঙ্গেলটন রিসেট। */
    public static synchronized void reset() {
        instance = null;
    }
}
