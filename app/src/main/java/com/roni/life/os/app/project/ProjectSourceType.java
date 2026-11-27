package com.roni.life.os.app.project;

/**
 * একটি প্রজেক্টের ওয়েব সোর্সের ধরন। প্রতিটি মান NativeRuntimeUI-এর
 * {@code WebSourceType}-এ ম্যাপ করা হয়।
 */
public enum ProjectSourceType {

    /** APK-এর assets ফোল্ডারে বান্ডল করা ওয়েব অ্যাপ। */
    ASSET("asset", true, true),

    /** APK-এর assets ফোল্ডারে থাকা .zip ওয়েব বান্ডল। */
    ZIP_ASSET("zip_asset", true, true),

    /** ডিভাইস ফাইল সিস্টেমে থাকা ফোল্ডার বা HTML ফাইল। */
    FILE("file", true, true),

    /** ডিভাইস ফাইল সিস্টেমে থাকা .zip ওয়েব বান্ডল। */
    ZIP_FILE("zip_file", true, true),

    /** রিমোট http/https URL। */
    REMOTE_URL("remote_url", true, false),

    /** সরাসরি ইনলাইন HTML। */
    INLINE_HTML("inline_html", false, false),

    /**
     * Android Storage Access Framework দিয়ে নির্বাচিত ফোল্ডার।
     *
     * <p>ফাইল কপি করা হয় না — মূল {@code content://} ট্রি URI সংরক্ষিত থাকে এবং
     * persisted URI permission-এর মাধ্যমে রানটাইমে সরাসরি সার্ভ করা হয়।</p>
     */
    SAF_FOLDER("saf_folder", true, true);

    private final String key;
    private final boolean requiresPath;
    private final boolean local;

    ProjectSourceType(String key, boolean requiresPath, boolean local) {
        this.key = key;
        this.requiresPath = requiresPath;
        this.local = local;
    }

    public String getKey() {
        return key;
    }

    public boolean requiresPath() {
        return requiresPath;
    }

    public boolean isLocal() {
        return local;
    }

    public boolean requiresEntryPoint() {
        return this == ASSET || this == ZIP_ASSET || this == FILE || this == ZIP_FILE
                || this == SAF_FOLDER;
    }

    /** SAF ফোল্ডার কপি না করে সরাসরি সার্ভ করা হয়। */
    public boolean isSaf() {
        return this == SAF_FOLDER;
    }

    public static ProjectSourceType fromKey(String key) {
        if (key != null) {
            for (ProjectSourceType type : values()) {
                if (type.key.equalsIgnoreCase(key) || type.name().equalsIgnoreCase(key)) {
                    return type;
                }
            }
        }
        return ASSET;
    }
}
