package com.roni.life.os.app.project;

import com.roni.library.storage.KeyValueStore;

/**
 * NativeStorageEngine-এর {@link KeyValueStore}-এর অ্যাডাপ্টার।
 *
 * <p>এটি লাইব্রেরির প্রকৃত পাবলিক API — কোনো অনুমান করা মেথড নয়।</p>
 */
public final class KeyValueStoreBackend implements KeyValueBackend {

    private final KeyValueStore store;

    public KeyValueStoreBackend(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String get(String key, String fallback) {
        return store != null ? store.getString(key, fallback) : fallback;
    }

    @Override
    public void put(String key, String value) {
        if (store != null) {
            store.putString(key, value);
        }
    }

    @Override
    public boolean remove(String key) {
        return store != null && store.remove(key);
    }
}
