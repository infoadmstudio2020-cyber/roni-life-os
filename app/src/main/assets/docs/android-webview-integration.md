# Android WebView & WebViewAssetLoader Integration Guide

**RONi Android Library Generator** is 100% client-side Vanilla JavaScript (ES6 Modules) and is engineered to run seamlessly inside modern desktop browsers, mobile browsers, and native **Android WebView**.

This guide provides full instructions and ready-to-use **Kotlin & Java** code snippets to host the web app inside an Android application using the official AndroidX `WebViewAssetLoader`.

---

## 1. Why `WebViewAssetLoader` Instead of `file://`

When Chromium / Android WebView loads local files via `file:///android_asset/`, Chromium enforces strict CORS restrictions:
- JavaScript ES Modules (`import ... from '...'` or `<script type="module">`) are **blocked** on `file://`.
- `fetch()` requests and relative AJAX requests fail due to origin `null`.

**Solution:** AndroidX `WebViewAssetLoader` intercepts requests to a virtual domain (e.g., `https://appassets.androidplatform.net/assets/`) and streams assets directly from the app's `assets/` folder without network overhead.
- Origin is a secure `https://` origin.
- Full ES Modules (`<script type="module">`) support.
- Full IndexedDB and Web Storage support.
- Zero CORS errors.

---

## 2. Dependencies (`app/build.gradle`)

Add the AndroidX WebKit dependency:

```groovy
dependencies {
    implementation 'androidx.webkit:webkit:1.11.0'
    implementation 'androidx.appcompat:appcompat:1.7.0'
}
```

---

## 3. Asset Placement

Extract the RONi Android Library Generator project files into:
```
app/src/main/assets/
├── index.html
├── assets/
├── css/
├── js/
├── docs/
├── version.json
└── ...
```

---

## 4. Kotlin Implementation (`MainActivity.kt`)

```kotlin
package com.roni.libgen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Register File Picker Activity Result
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results: Array<Uri>? = when {
                data?.dataString != null -> arrayOf(Uri.parse(data.dataString))
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                else -> null
            }
            fileUploadCallback?.onReceiveValue(results)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        // 1. Configure AndroidX WebViewAssetLoader
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // 2. Configure WebView Settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false // False for security; assets served via virtual https
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // 3. Attach JavaScript Interface (AndroidBridge)
        webView.addJavascriptInterface(AndroidWebBridge(this), "AndroidBridge")

        // 4. Configure WebViewClient with AssetLoader interceptor
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        // 5. Configure WebChromeClient for File Uploads & JS Dialogs
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                mWebView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    return false
                }
                return true
            }
        }

        // 6. Load Application via virtual secure domain
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Native JavaScript Interface exposed as window.AndroidBridge
     */
    inner class AndroidWebBridge(private val activity: Activity) {

        @JavascriptInterface
        fun saveFile(filename: String, base64Data: String, mimeType: String) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val downloadsDir = activity.getExternalFilesDir(null)
                val targetFile = File(downloadsDir, filename)
                FileOutputStream(targetFile).use { it.write(bytes) }

                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Saved to ${targetFile.name} (${targetFile.length() / 1024} KB)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

---

## 5. Summary of JavaScript Bridge APIs

The web application automatically detects `window.AndroidBridge` if provided by your host Android app:

| Method | Parameters | Description |
|---|---|---|
| `saveFile(filename, base64Data, mimeType)` | `String, String, String` | Called when exporting ZIP or saving generated build files. |
| `showToast(message)` | `String` | Called for native Android toast feedback. |
| `copyToClipboard(text)` | `String` | Optional native clipboard handler. |

If `window.AndroidBridge` is omitted, the web application automatically falls back to:
- Standard Browser Blob downloads with delayed object URL revocation (60s).
- Base64 Data URL downloads.
- Standard HTML5 File Input picker (`onShowFileChooser`).
- Web Clipboard API with `document.execCommand('copy')` fallback.
