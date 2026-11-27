package com.roni.life.os.app.project;

/**
 * স্টোরেজ অ্যাবস্ট্রাকশন। ডিফল্ট ইমপ্লিমেন্টেশন NativeStorageEngine-এর
 * {@code KeyValueStore}-কে মুড়ে দেয়; টেস্টে ইন-মেমরি ইমপ্লিমেন্টেশন ব্যবহার হয়।
 */
public interface KeyValueBackend {

    String get(String key, String fallback);

    void put(String key, String value);

    boolean remove(String key);
}
