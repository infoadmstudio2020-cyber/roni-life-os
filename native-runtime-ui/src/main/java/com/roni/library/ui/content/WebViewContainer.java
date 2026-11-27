package com.roni.library.ui.content;

import android.content.Context;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.ui.web.WebHostConfig;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Backward-compatible WebView container used by the in-shell content area.
 *
 * <p>All loading, bridge, multi-source and lifecycle behaviour is implemented by
 * {@link WebViewHost}; this class only keeps the legacy page-status listener API.</p>
 */
public class WebViewContainer extends WebViewHost {

    public interface OnPageStatusListener {
        void onPageStarted(String url);
        void onPageFinished(String url);
        void onProgressChanged(int progress);
        void onReceivedError(int errorCode, String description, String failingUrl);
        void onTitleReceived(String title);
    }

    private final CopyOnWriteArrayList<OnPageStatusListener> statusListeners =
            new CopyOnWriteArrayList<>();

    public WebViewContainer(@NonNull Context context) {
        this(context, WebHostConfig.defaultConfig());
    }

    public WebViewContainer(@NonNull Context context, @Nullable WebHostConfig config) {
        super(context, config);
        setListener(new Listener() {
            @Override
            public void onPageStarted(String url) {
                for (OnPageStatusListener listener : statusListeners) {
                    listener.onPageStarted(url);
                }
            }

            @Override
            public void onPageFinished(String url) {
                for (OnPageStatusListener listener : statusListeners) {
                    listener.onPageFinished(url);
                }
            }

            @Override
            public void onProgressChanged(int progress) {
                for (OnPageStatusListener listener : statusListeners) {
                    listener.onProgressChanged(progress);
                }
            }

            @Override
            public void onTitleReceived(String title) {
                for (OnPageStatusListener listener : statusListeners) {
                    listener.onTitleReceived(title);
                }
            }

            @Override
            public void onReceivedError(int errorCode, String description, String failingUrl) {
                for (OnPageStatusListener listener : statusListeners) {
                    listener.onReceivedError(errorCode, description, failingUrl);
                }
            }

            @Override
            public void onExternalLink(String url) {
                // Legacy listener has no external-link callback.
            }
        });
    }

    public void loadSource(@NonNull WebSource source) {
        super.loadSource(source);
    }

    public void loadUrl(@NonNull String url) {
        super.loadUrl(url);
    }

    public void loadHtml(@NonNull String html, @Nullable String baseUrl) {
        super.loadHtml(html, baseUrl);
    }

    public void evaluateJavascript(@NonNull String script, @Nullable ValueCallback<String> callback) {
        super.evaluateJavascript(script, callback);
    }

    public void setShowProgressBar(boolean show) {
        setProgressBarVisible(show);
    }

    public void addStatusListener(@NonNull OnPageStatusListener listener) {
        statusListeners.add(listener);
    }

    public void removeStatusListener(@NonNull OnPageStatusListener listener) {
        statusListeners.remove(listener);
    }

    /** Legacy alias for {@link #onDestroy()}. */
    public void destroy() {
        onDestroy();
    }
}
