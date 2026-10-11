package it.dhd.oxygencustomizer.utils.overlay.manager;

import static it.dhd.oxygencustomizer.utils.Dynamic.TOTAL_NOTIFICATIONS;

import android.util.Log;

import it.dhd.oxygencustomizer.utils.AppUtils;
import it.dhd.oxygencustomizer.utils.PreferenceHelper;
import it.dhd.oxygencustomizer.utils.Prefs;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

/** Synchronous, verified notification overlay changes; callers must run off the UI thread. */
public final class NotificationManager {
    private static final String TAG = "OC-NotificationOverlay";

    private NotificationManager() {}

    private static String overlayName(int style) {
        return "OxygenCustomizerComponentNFN" + style + ".overlay";
    }

    public static boolean enableOverlay(int style) {
        if (style < 1 || style > TOTAL_NOTIFICATIONS) return false;
        OverlayUtil.OPERATION_LOCK.lock();
        try {
            if (!OverlayUtil.applyOverlayStateNow(overlayName(style), true, true)) {
                Log.e(TAG, "Could not activate notification style " + style);
                return false;
            }

            // The two corner resources are independent overlays, not one alias.
            for (String name : new String[]{
                    "OxygenCustomizerComponentCRN1.overlay",
                    "OxygenCustomizerComponentCR1.overlay"}) {
                if (OverlayUtil.isOverlayDisabled(name)
                        && !OverlayUtil.applyOverlayStateNow(name, true, false)) {
                    Log.w(TAG, "Optional corner overlay could not be enabled: " + name);
                }
            }

            for (int index = 1; index <= TOTAL_NOTIFICATIONS; index++) {
                Prefs.putBoolean(overlayName(index), index == style);
            }
            boolean oldEnabled = PreferenceHelper.getModulePrefs() != null
                    && PreferenceHelper.getModulePrefs().getBoolean("hasNotificationOverlays", false);
            if (PreferenceHelper.getModulePrefs() != null) {
                PreferenceHelper.getModulePrefs().edit()
                        .putBoolean("hasNotificationOverlays", true).apply();
                if (!oldEnabled) AppUtils.restartScope("systemui");
            }
            return true;
        } finally {
            OverlayUtil.OPERATION_LOCK.unlock();
        }
    }

    public static boolean disableOverlay(int style) {
        if (style < 1 || style > TOTAL_NOTIFICATIONS) return false;
        OverlayUtil.OPERATION_LOCK.lock();
        try {
            String packageName = overlayName(style);
            if (OverlayUtil.isOverlayEnabled(packageName)
                    && !OverlayUtil.applyOverlayStateNow(packageName, false, false)) {
                Log.e(TAG, "Could not disable notification style " + style);
                return false;
            }
            Prefs.putBoolean(packageName, false);
            boolean anyEnabled = false;
            for (int index = 1; index <= TOTAL_NOTIFICATIONS; index++) {
                if (OverlayUtil.isOverlayEnabled(overlayName(index))) {
                    anyEnabled = true;
                    break;
                }
            }
            if (PreferenceHelper.getModulePrefs() != null) {
                boolean previous = PreferenceHelper.getModulePrefs()
                        .getBoolean("hasNotificationOverlays", false);
                PreferenceHelper.getModulePrefs().edit()
                        .putBoolean("hasNotificationOverlays", anyEnabled).apply();
                if (previous != anyEnabled) AppUtils.restartScope("systemui");
            }
            return true;
        } finally {
            OverlayUtil.OPERATION_LOCK.unlock();
        }
    }
}
