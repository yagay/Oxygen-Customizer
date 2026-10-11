package it.dhd.oxygencustomizer.xposed.utils;

import android.content.Context;

import com.crossbowffs.remotepreferences.RemotePreferences;

import java.util.List;

import it.dhd.oneplusui.preference.OplusSliderPreference;

public class ExtendedRemotePreferences extends RemotePreferences {
    public ExtendedRemotePreferences(Context context, String authority, String prefFileName) {
        super(context, authority, prefFileName);
    }

    public ExtendedRemotePreferences(Context context, String authority, String prefFileName, boolean strictMode) {
        super(context, authority, prefFileName, strictMode);
    }

    /**
     * A restored backup or an older UI can contain an invalid choice value.
     * Never let a single malformed integer disable an entire SystemUI Hook pack.
     */
    public int getIntFromString(String key, String defaultValue) {
        int fallback;
        try {
            fallback = Integer.parseInt(defaultValue);
        } catch (NumberFormatException ignored) {
            fallback = 0;
        }
        try {
            String value = getString(key, defaultValue);
            return Integer.parseInt(value);
        } catch (NumberFormatException | ClassCastException invalid) {
            android.util.Log.w("OC-PreferenceParse", "Invalid integer preference: " + key
                    + "; falling back to " + fallback, invalid);
            return fallback;
        }
    }

    public int getSliderInt(String key, int defaultVal) {
        return OplusSliderPreference.getSingleIntValue(this, key, defaultVal);
    }

    public float getSliderFloat(String key, float defaultVal) {
        return OplusSliderPreference.getSingleFloatValue(this, key, defaultVal);
    }

    public List<Float> getSliderValues(String key, float defaultValue) {
        return OplusSliderPreference.getValues(this, key, defaultValue);
    }
}
