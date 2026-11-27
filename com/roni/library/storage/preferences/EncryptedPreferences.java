package com.roni.library.storage.preferences;
public class EncryptedPreferences {
    public String getString(String key, String def) { return def; }
    public void putString(String key, String value) { }
    public boolean getBoolean(String key, boolean def) { return def; }
    public void putBoolean(String key, boolean value) { }
    public int getInt(String key, int def) { return def; }
    public void putInt(String key, int value) { }
}
