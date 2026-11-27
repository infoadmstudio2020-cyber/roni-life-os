package com.roni.life.os.app.core;

import android.content.Context;

import com.roni.library.storage.KeyValueStore;
import com.roni.library.storage.StorageEngine;
import com.roni.life.os.app.project.KeyValueStoreBackend;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectFactory;
import com.roni.life.os.app.project.ProjectRegistrationService;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.runtime.RuntimeBridge;
import com.roni.life.os.app.util.AppLog;

import java.io.File;

/**
 * অ্যাপের সম্পূর্ণ ইনিশিয়ালাইজেশন পাইপলাইন।
 *
 * <p>ক্রম: NativeRuntime Bootstrap → স্টোরেজ ব্যাকএন্ড → প্রজেক্ট স্টোর → প্রথম-রান সিডিং।</p>
 */
public final class AppInitializer {

    private static final String STORE_NAME = "roni_life_projects";
    private static final String STORE_DIR = "runtime";

    private AppInitializer() {
    }

    /** ইনিশিয়ালাইজেশনের ফলাফল। */
    public static final class Result {
        private final ProjectStore store;
        private final AppConfig config;
        private final boolean runtimeReady;
        private final String runtimeMessage;
        private final long durationMs;
        private final RONiLiFEOSStorageInitializer.Result storage;

        Result(ProjectStore store, AppConfig config, boolean runtimeReady,
               String runtimeMessage, long durationMs) {
            this(store, config, runtimeReady, runtimeMessage, durationMs, null);
        }

        Result(ProjectStore store, AppConfig config, boolean runtimeReady,
               String runtimeMessage, long durationMs,
               RONiLiFEOSStorageInitializer.Result storage) {
            this.store = store;
            this.config = config;
            this.runtimeReady = runtimeReady;
            this.runtimeMessage = runtimeMessage;
            this.durationMs = durationMs;
            this.storage = storage;
        }

        /** Storage initialisation outcome; {@code null} only for legacy call sites. */
        public RONiLiFEOSStorageInitializer.Result getStorage() {
            return storage;
        }

        public ProjectStore getStore() {
            return store;
        }

        public AppConfig getConfig() {
            return config;
        }

        public boolean isRuntimeReady() {
            return runtimeReady;
        }

        public String getRuntimeMessage() {
            return runtimeMessage;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    public static Result initialize(Context context) {
        long startedAt = System.currentTimeMillis();
        Context appContext = context.getApplicationContext();

        // ০. প্রথম-রান স্টোরেজ ইনিশিয়ালাইজেশন — Bootstrap-এর আগে অবশ্যই সফল হতে হবে।
        //    কাঠামো, টেমপ্লেট, ডিফল্ট প্রজেক্ট ও ভ্যালিডেশন এখানেই শেষ হয়।
        ProjectStore bootstrapStore = new ProjectStore(new KeyValueStoreBackend(
                createKeyValueStore(appContext)));
        RONiLiFEOSStorageInitializer.Result storage =
                RONiLiFEOSStorageInitializer.initialize(appContext, bootstrapStore);
        if (!storage.isSuccess()) {
            AppLog.e("Storage initialisation failed: " + storage.getMessage());
            return new Result(bootstrapStore, bootstrapStore.loadConfig(), false,
                    "Storage initialisation failed: " + storage.getMessage(),
                    System.currentTimeMillis() - startedAt, storage);
        }

        // ১. প্রাথমিক কনফিগ পড়া (রানটাইম কনফিগারেশনের জন্য প্রয়োজন)।
        AppConfig config = bootstrapStore.loadConfig();

        // ২. NativeRuntime ইকোসিস্টেম বুটস্ট্র্যাপ।
        boolean runtimeReady = RuntimeBridge.get().initialize(appContext, config);
        String runtimeMessage = RuntimeBridge.get().getRuntimeSummary();

        // ৩. Runtime ready হলে স্টোরেজ ইঞ্জিনের KeyValueStore ব্যবহার, নাহলে ফাইল-ভিত্তিক ফলব্যাক।
        ProjectStore store = new ProjectStore(new KeyValueStoreBackend(
                createKeyValueStore(appContext)));

        // ৩.১ রানটাইমের authoritative স্টোরে ডিফল্ট প্রজেক্ট নিশ্চিত করা। রানটাইমের আগে
        //     ব্যবহৃত ফলব্যাক স্টোর ও ইঞ্জিন স্টোর আলাদা ফাইল ব্যবহার করে, তাই রেজিস্ট্রি
        //     দুই জায়গায় মিলিয়ে নেওয়া হয় — ফাইল কপি কিছুই পুনরাবৃত্ত হয় না।
        RONiLiFEOSStorageInitializer.ensureRegistered(appContext, store);

        // ৪. প্রথম ইনস্টলেশনে ডেমো প্রজেক্ট রেজিস্টার — বান্ডল করা webapp.zip অভ্যন্তরীণ
        //    প্রজেক্ট ফোল্ডারে এক্সট্র্যাক্ট করা হয়, তাই রেকর্ডটি APK অ্যাসেটের উপর নির্ভর করে না।
        config = store.loadConfig();
        if (!config.isSeeded() && store.count() == 0) {
            ProjectRegistrationService.Result seeded = ProjectRegistrationService.registerBundledZip(
                    appContext, store, ProjectFactory.DEMO_ASSET_PATH,
                    "Demo web app",
                    "Bundled demo web application for the NativeRuntime ecosystem.",
                    0xFF00B894);
            if (seeded.isSuccess()) {
                config.setSeeded(true);
                store.saveConfig(config);
                // Route through the authoritative default setter so the flag and both config
                // pointers agree from the very first launch.
                store.setDefault(seeded.getProject().getId());
                AppLog.i("First run: demo project registered at "
                        + seeded.getProject().getSourcePath());
            } else {
                // Extraction failed — keep a working project by referencing the APK asset.
                AppLog.w("First run: demo extraction failed — " + seeded.getMessage());
                Project demo = ProjectFactory.createDemoProject();
                store.save(demo);
                config.setSeeded(true);
                store.saveConfig(config);
                store.setDefault(demo.getId());
                AppLog.i("First run: demo project referenced from assets (" + demo.getId() + ")");
            }
        }

        // ৫. স্টোরেজ মিরর — registry ও config-এর পঠনযোগ্য JSON ভিউ ডিস্কে লেখা হয়।
        //    ProjectStore-ই authoritative; মিরর কেবল তার প্রতিচ্ছবি।
        StorageMirror mirror = new StorageMirror(appContext, store);
        store.setChangeListener(mirror::syncAll);
        mirror.syncAll();

        long duration = System.currentTimeMillis() - startedAt;
        AppLog.i("App initialisation complete · " + duration + "ms · runtime=" + runtimeReady);
        return new Result(store, config, runtimeReady, runtimeMessage, duration, storage);
    }

    /**
     * NativeStorageEngine ready থাকলে তার KeyValueStore ব্যবহার করে,
     * নাহলে একই লাইব্রেরির KeyValueStore সরাসরি ইনস্ট্যান্স তৈরি করে।
     */
    private static KeyValueStore createKeyValueStore(Context context) {
        try {
            if (StorageEngine.isInitialized()) {
                KeyValueStore engineStore = StorageEngine.getKeyValueStore();
                if (engineStore != null) {
                    return engineStore;
                }
            }
        } catch (Throwable ignored) {
            // ইঞ্জিন স্টোর অনুপলব্ধ — ফলব্যাক ব্যবহার হবে।
        }
        File baseDir = new File(context.getFilesDir(), STORE_DIR);
        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }
        return new KeyValueStore(STORE_NAME, baseDir);
    }
}
