package it.dhd.oxygencustomizer.utils;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

/**
 * Preference import/export helper.
 *
 * New backups are JSON with explicit value types. Legacy Java-serialized backups
 * are accepted only when the stream has the Java serialization magic header.
 */
public class PrefManager {
    private static final String TAG = "Pref Exporter";
    private static final int SCHEMA_VERSION = 2;

    @SuppressWarnings("UnusedReturnValue")
    public static boolean exportPrefs(SharedPreferences preferences,
                                      final @NonNull OutputStream outputStream) throws IOException {
        try (outputStream) {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("appVersion", BuildConfig.VERSION_CODE);

            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                JSONObject item = encodeValue(entry.getValue());
                if (item != null) {
                    values.put(entry.getKey(), item);
                }
            }
            root.put("preferences", values);

            outputStream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error serializing preferences", BuildConfig.DEBUG ? e : null);
            return false;
        }
    }

    private static JSONObject encodeValue(Object value) throws Exception {
        JSONObject item = new JSONObject();
        if (value instanceof Boolean) {
            item.put("type", "boolean");
            item.put("value", value);
        } else if (value instanceof String) {
            item.put("type", "string");
            item.put("value", value);
        } else if (value instanceof Integer) {
            item.put("type", "int");
            item.put("value", value);
        } else if (value instanceof Float) {
            item.put("type", "float");
            item.put("value", ((Float) value).doubleValue());
        } else if (value instanceof Long) {
            item.put("type", "long");
            item.put("value", value);
        } else if (value instanceof Set<?>) {
            item.put("type", "stringSet");
            JSONArray array = new JSONArray();
            for (Object itemValue : (Set<?>) value) {
                if (itemValue instanceof String) {
                    array.put(itemValue);
                }
            }
            item.put("value", array);
        } else {
            return null;
        }
        return item;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean importPath(SharedPreferences sharedPreferences,
                                     final @NonNull InputStream inputStream) throws IOException {
        byte[] data;
        try (InputStream source = inputStream;
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = source.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            data = buffer.toByteArray();
        }

        if (data.length == 0) {
            return false;
        }

        try {
            String json = new String(data, StandardCharsets.UTF_8).trim();
            if (json.startsWith("{")) {
                return importJson(sharedPreferences, json);
            }

            if (isLegacySerializedStream(data)) {
                return importLegacy(sharedPreferences, data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing preferences", BuildConfig.DEBUG ? e : null);
        }
        return false;
    }

    private static boolean importJson(SharedPreferences sharedPreferences, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int schemaVersion = root.optInt("schemaVersion", -1);
        if (schemaVersion <= 0 || schemaVersion > SCHEMA_VERSION) {
            Log.e(TAG, "Unsupported preference schema: " + schemaVersion);
            return false;
        }

        JSONObject values = root.optJSONObject("preferences");
        if (values == null) {
            return false;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();

        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = values.getJSONObject(key);
            String type = item.getString("type");

            switch (type) {
                case "boolean" -> {
                    boolean value = item.getBoolean("value");
                    editor.putBoolean(key, value);
                    maybeEnableOverlay(key, value);
                }
                case "string" -> editor.putString(key, item.getString("value"));
                case "int" -> editor.putInt(key, item.getInt("value"));
                case "float" -> editor.putFloat(key, (float) item.getDouble("value"));
                case "long" -> editor.putLong(key, item.getLong("value"));
                case "stringSet" -> {
                    JSONArray array = item.getJSONArray("value");
                    Set<String> set = new java.util.HashSet<>();
                    for (int i = 0; i < array.length(); i++) {
                        set.add(array.getString(i));
                    }
                    editor.putStringSet(key, set);
                }
                default -> Log.w(TAG, "Skipping unknown preference type " + type + " for " + key);
            }
        }
        return editor.commit();
    }

    private static boolean isLegacySerializedStream(byte[] data) {
        return data.length >= 2
                && (data[0] & 0xff) == 0xac
                && (data[1] & 0xff) == 0xed;
    }

    @SuppressWarnings("unchecked")
    private static boolean importLegacy(SharedPreferences sharedPreferences, byte[] data) {
        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new ByteArrayInputStream(data))) {
            Object object = objectInputStream.readObject();
            if (!(object instanceof Map<?, ?> rawMap)) {
                return false;
            }

            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String) {
                    map.put((String) entry.getKey(), entry.getValue());
                }
            }
            return applyLegacyMap(sharedPreferences, map);
        } catch (Exception e) {
            Log.e(TAG, "Error importing legacy preferences", BuildConfig.DEBUG ? e : null);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean applyLegacyMap(SharedPreferences sharedPreferences,
                                          Map<String, Object> map) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
                maybeEnableOverlay(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Set<?>) {
                editor.putStringSet(entry.getKey(), (Set<String>) value);
            }
        }
        return editor.commit();
    }

    private static void maybeEnableOverlay(String key, boolean value) {
        if (value && key.contains("overlay")) {
            OverlayUtil.enableOverlay(key);
        }
    }

    public static void clearPrefs(SharedPreferences preferences) {
        preferences.edit().clear().commit();
    }
}
