package com.roni.life.os.app;

import android.app.Application;

import com.roni.library.crashguard.CrashGuard;
import com.roni.library.crashguard.CrashGuardConfig;
import com.roni.library.platform.notification.NotificationReceiver;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.notification.NotificationDeepLinkRouter;
import com.roni.life.os.app.util.AppLog;

/**
 * অ্যাপ্লিকেশন এন্ট্রি পয়েন্ট।
 *
 * <p>এখানেই NativeRuntime ইকোসিস্টেম একবার Bootstrap হয়, যাতে তিনটি Activity-ই
 * ready রানটাইম পায় এবং দ্বিতীয়বার Bootstrap না হয়।</p>
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        long startedAt = System.currentTimeMillis();
        ApplicationCore.get().init(this);
        // Notification taps are routed by the host, never by the web page.
        NotificationReceiver.setNavigationHandler(new NotificationDeepLinkRouter(this));
        AppLog.i("App.onCreate complete · " + (System.currentTimeMillis() - startedAt) + "ms");


        CrashGuardConfig config = CrashGuardConfig.newBuilder()
                .setEnabled(true)
                .setTag("my-app")
                .setShowCrashActivity(true)
                .setAutoRestart(false)
                .setMaxStoredReports(25)
                .setEnableBreadcrumbs(true)
                .setMaxBreadcrumbs(50)
                .setFilterSensitiveData(true)
                .setCrashListener((thread, throwable, report) -> {
                    // Upload the report to your backend here.
                })
                .build();

        CrashGuard.install(this, config);
    }
}
