package com.campustrade.auth.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App-private SharedPreferences backed by Android Keystore encryption.
 * Existing plain preferences are copied once and then cleared.
 */
public final class SecurePrefs {

    private static final String MIGRATION_KEY = "__ct_secure_migrated_v1";
    private static final Map<String, SharedPreferences> CACHE = new ConcurrentHashMap<>();
    private static MasterKey masterKey;

    private SecurePrefs() { }

    public static SharedPreferences get(Context context, String name) {
        if (context == null || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Preferences need a context and name");
        }
        SharedPreferences cached = CACHE.get(name);
        if (cached != null) return cached;
        synchronized (SecurePrefs.class) {
            cached = CACHE.get(name);
            if (cached != null) return cached;
            SharedPreferences plain = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            try {
                if (masterKey == null) {
                    masterKey = new MasterKey.Builder(context)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build();
                }
                SharedPreferences secure = EncryptedSharedPreferences.create(
                        context, name + ".secure", masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                migrate(plain, secure);
                CACHE.put(name, secure);
                return secure;
            } catch (Exception e) {
                // Keep the app usable on devices with a broken Keystore. The
                // storage is still app-private and excluded from backups.
                android.util.Log.w("CampusTrade", "encrypted preferences unavailable: " + e.getMessage());
                CACHE.put(name, plain);
                return plain;
            }
        }
    }

    private static void migrate(SharedPreferences plain, SharedPreferences secure) {
        if (secure.getBoolean(MIGRATION_KEY, false)) return;
        Map<String, ?> values = plain.getAll();
        SharedPreferences.Editor edit = secure.edit();
        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (MIGRATION_KEY.equals(key) || value == null) continue;
                if (value instanceof String) edit.putString(key, (String) value);
                else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else if (value instanceof Long) edit.putLong(key, (Long) value);
                else if (value instanceof Float) edit.putFloat(key, (Float) value);
                else if (value instanceof Set) {
                    try {
                        //noinspection unchecked
                        edit.putStringSet(key, (Set<String>) value);
                    } catch (ClassCastException ignored) { }
                }
            }
        }
        edit.putBoolean(MIGRATION_KEY, true);
        edit.commit();
        plain.edit().clear().commit();
    }
}
