package it.dhd.oxygencustomizer.xposed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.utils.ExtendedRemotePreferences;

public class XPrefs {

    @SuppressLint("StaticFieldLeak")
    public static ExtendedRemotePreferences Xprefs;
    private static String packageName;

    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> loadEverything(packageName, key);
    public static void init(Context context) {
        packageName = context.getPackageName();

        Xprefs = (ExtendedRemotePreferences) new ExtendedRemotePreferences(context, BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + "_preferences", true);

        Xprefs.registerOnSharedPreferenceChangeListener(listener);
    }


    public static void addInternalBroadcastToken(Intent intent) {
        if (intent == null || Xprefs == null) return;
        try {
            String token = Xprefs.getString(
                    Constants.Preferences.General.PREF_INTERNAL_BROADCAST_TOKEN,
                    ""
            );
            if (token != null && !token.isEmpty()) {
                intent.putExtra(
                        Constants.Preferences.General.EXTRA_INTERNAL_BROADCAST_TOKEN,
                        token
                );
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isTrustedInternalBroadcast(Intent intent) {
        if (intent == null || Xprefs == null) return false;
        try {
            String expected = Xprefs.getString(
                    Constants.Preferences.General.PREF_INTERNAL_BROADCAST_TOKEN,
                    ""
            );
            String provided = intent.getStringExtra(
                    Constants.Preferences.General.EXTRA_INTERNAL_BROADCAST_TOKEN
            );
            return expected != null
                    && !expected.isEmpty()
                    && expected.equals(provided);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void loadEverything(String packageName, String... key) {
        if (key.length > 0 && (key[0] == null || Constants.Preferences.General.PREF_UPDATE_EXCLUSIONS.stream().anyMatch(exclusion -> key[0].startsWith(exclusion))))
            return;

        if (Xprefs == null) return;

        boolean moreLogging;
        try {
            moreLogging = Xprefs.getBoolean(
                    Constants.Preferences.General.PREF_MORE_LOGGING,
                    false
            );
        } catch (Throwable throwable) {
            return;
        }

        for (XposedMods thisMod : XPLauncher.runningMods) {
            try {
                thisMod.mDebug = BuildConfig.VERSION_NAME.contains("nightly") || moreLogging;
                thisMod.updatePrefs(key);
            } catch (Throwable throwable) {
                thisMod.log(throwable);
            }
        }
    }
}
