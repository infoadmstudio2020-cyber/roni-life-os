package com.roni.life.os.app.activity;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.ProjectStore;

/**
 * তিনটি Activity-এর সাধারণ ভিত্তি: কেন্দ্রীয় কোর, স্টোর, কনফিগ ও টোস্ট সহায়ক।
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected ApplicationCore core() {
        return ApplicationCore.get();
    }

    protected ProjectStore store() {
        return core().getStore();
    }

    protected AppConfig config() {
        return core().getConfig();
    }

    protected void toast(String message) {
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
