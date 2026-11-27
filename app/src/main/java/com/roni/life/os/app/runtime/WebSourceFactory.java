package com.roni.life.os.app.runtime;

import com.roni.library.ui.web.WebPaths;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebSourceType;
import com.roni.life.os.app.project.Project;

/**
 * {@link Project} → NativeRuntimeUI-এর {@link WebSource}-এ রূপান্তর।
 *
 * <p>প্রতিটি {@code ProjectSourceType} কে লাইব্রেরির প্রকৃত ফ্যাক্টরি মেথডে ম্যাপ করা হয়।</p>
 *
 * <p><b>প্রতি প্রজেক্টের আলাদা Virtual Origin।</b> প্রতিটি সোর্সে
 * {@link WebPaths#virtualHostFor(String)} দিয়ে প্রজেক্ট আইডি থেকে একটি স্থায়ী হোস্ট বসানো হয়
 * (যেমন {@code https://my-app-1a2b3c4d.runtime.local})। হোস্ট প্রজেক্টের <em>নাম</em> থেকে নয়,
 * <em>আইডি</em> থেকে তৈরি হয় — নাম বদলালে origin বদলাবে না, ফলে প্রজেক্টের localStorage ও
 * IndexedDB হারিয়ে যাবে না।</p>
 *
 * <p>আলাদা হোস্ট না থাকলে সব প্রজেক্ট একই origin শেয়ার করত, এবং একটি প্রজেক্টের JavaScript
 * আরেকটির সংরক্ষিত ডেটা পড়তে পারত।</p>
 */
public final class WebSourceFactory {

    private WebSourceFactory() {
    }

    public static WebSource from(Project project) {
        if (project == null) {
            return WebSource.remote("about:blank");
        }
        return build(project).withVirtualHost(WebPaths.virtualHostFor(project.getId()));
    }

    private static WebSource build(Project project) {
        String id = project.getId();
        String title = project.getName();
        String entry = project.getEntryPoint() != null && !project.getEntryPoint().trim().isEmpty()
                ? project.getEntryPoint().trim() : "index.html";
        String path = project.getSourcePath() != null ? project.getSourcePath().trim() : "";

        switch (project.getSourceType()) {
            case ASSET:
                return WebSource.asset(id, title, path).withEntryPoint(entry);
            case ZIP_ASSET:
                return WebSource.zipAsset(id, title, path).withEntryPoint(entry);
            case FILE:
                return WebSource.file(id, title, path).withEntryPoint(entry);
            case ZIP_FILE:
                return WebSource.zipFile(id, title, path).withEntryPoint(entry);
            case REMOTE_URL:
                return WebSource.remote(id, title, path);
            case SAF_FOLDER:
                // SAF ফোল্ডার কপি না করে content:// URI দিয়ে সরাসরি সার্ভ করা হয়।
                return WebSource.contentUri(id, title, path).withEntryPoint(entry);
            case INLINE_HTML:
                WebSource.Builder builder = WebSource.builder(WebSourceType.INLINE_HTML)
                        .id(id)
                        .title(title)
                        .html(project.getInlineHtml());
                if (project.getBaseUrl() != null && !project.getBaseUrl().trim().isEmpty()) {
                    builder.baseUrl(project.getBaseUrl().trim());
                }
                return builder.build();
            default:
                return WebSource.remote("about:blank");
        }
    }
}
