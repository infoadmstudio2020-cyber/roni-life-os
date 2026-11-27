package com.roni.library.platform.notification;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Bounded, best-effort loader for BigPicture images.
 *
 * <p>Only {@code http(s)} URLs reach this class (the request validator already rejected every
 * other scheme). The loader caps the download at {@link #MAX_BYTES}, decodes with a sample size
 * derived from the target width, caches the last few bitmaps and silently gives up on any error —
 * a broken image must degrade to BigText, never crash the host.</p>
 */
final class NotificationImageLoader {

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int TARGET_WIDTH = 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;

    interface Callback {
        void onLoaded(@NonNull String url, @Nullable Bitmap bitmap);
    }

    private final LruCache<String, Bitmap> cache = new LruCache<>(8);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor;

    NotificationImageLoader() {
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable runnable) {
                Thread thread = new Thread(runnable, "NotificationImageLoader");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Nullable
    Bitmap cached(@NonNull String url) {
        return cache.get(url);
    }

    void load(@NonNull final String url, @NonNull final Callback callback) {
        Bitmap cached = cache.get(url);
        if (cached != null) {
            callback.onLoaded(url, cached);
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = download(url);
                if (bitmap != null) {
                    cache.put(url, bitmap);
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onLoaded(url, bitmap);
                    }
                });
            }
        });
    }

    @Nullable
    private Bitmap download(@NonNull String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            int length = connection.getContentLength();
            if (length > MAX_BYTES) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream probe = connection.getInputStream()) {
                BitmapFactory.decodeStream(probe, null, bounds);
            }
            if (bounds.outWidth <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Throwable error) {
            return null;
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    void shutdown() {
        cache.evictAll();
        executor.shutdownNow();
    }
}
