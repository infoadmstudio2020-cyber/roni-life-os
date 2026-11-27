package com.roni.life.os.app.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ক্ষুদ্র কী-ভ্যালু কোডেক। Android নির্ভরতা ছাড়াই কাজ করে, তাই ইউনিট টেস্টে সরাসরি ব্যবহারযোগ্য।
 */
public final class Codec {

    private Codec() {
    }

    public static String encode(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(escape(entry.getKey())).append('=').append(escape(entry.getValue()));
        }
        return builder.toString();
    }

    public static Map<String, String> decode(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            result.put(unescape(key), unescape(value));
        }
        return result;
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    public static String unescape(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public static String str(Map<String, String> map, String key, String fallback) {
        String value = map.get(key);
        return value != null ? value : fallback;
    }

    public static boolean bool(Map<String, String> map, String key, boolean fallback) {
        String value = map.get(key);
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public static int integer(Map<String, String> map, String key, int fallback) {
        String value = map.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long lng(Map<String, String> map, String key, long fallback) {
        String value = map.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
