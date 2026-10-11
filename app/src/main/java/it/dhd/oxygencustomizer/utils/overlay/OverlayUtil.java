package it.dhd.oxygencustomizer.utils.overlay;

import static it.dhd.oxygencustomizer.utils.PreferenceHelper.getModulePrefs;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.dhd.oxygencustomizer.OxygenCustomizer;
import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.Prefs;

public class OverlayUtil {

    public static List<String> getOverlayList() {
        return Shell.cmd("cmd overlay list |  grep -E '....OxygenCustomizerComponent' | sed -E 's/^....//'").exec().getOut();
    }

    public static List<String> getEnabledOverlayList() {
        return Shell.cmd("cmd overlay list |  grep -E '.x..OxygenCustomizerComponent' | sed -E 's/^.x..//'").exec().getOut();
    }

    public static List<String> getDisabledOverlayList() {
        return Shell.cmd("cmd overlay list |  grep -E '. ..OxygenCustomizerComponent' | sed -E 's/^. ..//'").exec().getOut();
    }

    public static List<String> getOverlayForComponent(String componentName) {
        if (!isSafeIdentifier(componentName)) return Collections.emptyList();
        return Shell.cmd("cmd overlay list | grep -F 'OxygenCustomizerComponent" + componentName + "'").exec().getOut();
    }

    // Keep writes ordered; never report the requested state as a successful operation
    // before Android's overlay manager confirms it.
    private static final ExecutorService OVERLAY_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OC-OverlayWorker");
        thread.setDaemon(true);
        return thread;
    });

    /** Null means the system overlay manager could not be queried. */
    private static Boolean queryOverlayState(String pkgName) {
        if (!isSafeOverlayPackage(pkgName)) return null;
        Shell.Result result = Shell.cmd("cmd overlay list --user current").exec();
        if (!result.isSuccess()) {
            Log.e("OverlayUtil", "Overlay query failed: " + result.getErr());
            return null;
        }
        for (String line : result.getOut()) {
            String item = line.trim();
            if (item.equals("[x] " + pkgName)) return true;
            if (item.equals("[ ] " + pkgName)) return false;
        }
        // The package was not listed: absent is not an enabled overlay.
        return false;
    }

    public static boolean isOverlayEnabled(String pkgName) {
        return Boolean.TRUE.equals(queryOverlayState(pkgName));
    }

    public static boolean isOverlayDisabled(String pkgName) {
        return Boolean.FALSE.equals(queryOverlayState(pkgName));
    }

    public static void checkOverlayEnabledAndEnable(String componentName) {
        if (!isSafeIdentifier(componentName)) return;
        List<String> component = Shell.cmd(
                "cmd overlay list | grep \".x..OxygenCustomizerComponent" + componentName + "\""
        ).exec().getOut();
        if (!component.isEmpty()) {
            String num = component.get(0).split("OxygenCustomizerComponent" + componentName)[1].split("\\.overlay")[0];
            enableOverlay("OxygenCustomizerComponent" + componentName + num + ".overlay");
        }
    }

    static boolean isOverlayInstalled(List<String> enabledOverlays, String pkgName) {
        return enabledOverlays.contains(pkgName);
    }

    private static void requestOverlayState(String pkgName, boolean enabled, boolean exclusive) {
        if (!isSafeOverlayPackage(pkgName)) return;
        OVERLAY_EXECUTOR.execute(() -> applyOverlayStateNow(pkgName, enabled, exclusive));
    }

    /**
     * Synchronous overlay transition for installation tasks. Do not call on
     * the UI thread. Unlike the public click helpers, it waits for the
     * OverlayManager and checks the observed result before persisting state.
     */
    public static boolean applyOverlayStateNow(String pkgName, boolean enabled, boolean exclusive) {
        if (!isSafeOverlayPackage(pkgName)) return false;
        String command = enabled
                ? "cmd overlay " + (exclusive ? "enable-exclusive --user current --category " : "enable --user current ")
                    + pkgName + " && cmd overlay set-priority " + pkgName + " highest"
                : "cmd overlay disable --user current " + pkgName;
        Shell.Result result = Shell.cmd(command).exec();
        Boolean actualState = queryOverlayState(pkgName);
        if (!result.isSuccess() || actualState == null || actualState != enabled) {
            Log.e("OverlayUtil", "Overlay operation failed: " + pkgName
                    + ", requested=" + enabled + ", actual=" + actualState
                    + ", stderr=" + result.getErr());
        }
        if (actualState != null) {
            Prefs.putBoolean(pkgName, actualState);
            if (getModulePrefs() != null) {
                getModulePrefs().edit().putBoolean(pkgName, actualState).apply();
            }
        }
        return result.isSuccess() && actualState != null && actualState == enabled;
    }

    public static void enableOverlay(String pkgName) {
        requestOverlayState(pkgName, true, false);
    }

    public static void enableOverlays(String... pkgNames) {
        for (String pkgName : pkgNames) enableOverlay(pkgName);
    }

    public static void enableOverlayExclusiveInCategory(String pkgName) {
        requestOverlayState(pkgName, true, true);
    }

    public static void enableOverlaysExclusiveInCategory(String... pkgNames) {
        for (String pkgName : pkgNames) enableOverlayExclusiveInCategory(pkgName);
    }

    public static void disableOverlay(String pkgName) {
        requestOverlayState(pkgName, false, false);
    }

    public static void disableOverlays(String... pkgNames) {
        for (String pkgName : pkgNames) disableOverlay(pkgName);
    }

    public static void disableOverlays(List<String> overlays) {
        disableOverlays(overlays.toArray(new String[0]));
    }

    public static void changeOverlayState(Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments must be even.");
        }
        for (int i = 0; i < args.length; i += 2) {
            requestOverlayState((String) args[i], (boolean) args[i + 1], false);
        }
    }

    public static boolean overlayExists() {
        List<String> output = Shell.cmd(
                "[ -f /system/product/overlay/OxygenCustomizerComponentOCV.apk ] && echo found || echo missing"
        ).exec().getOut();
        return !output.isEmpty() && "found".equals(output.get(0));
    }

    public static boolean overlayExist(String overlayName) {
        if (!isSafeIdentifier(overlayName)) return false;
        List<String> output = Shell.cmd(
                "[ -f /system/product/overlay/OxygenCustomizerComponent" + overlayName
                        + ".apk ] && echo found || echo missing"
        ).exec().getOut();
        return !output.isEmpty() && "found".equals(output.get(0));
    }

    @SuppressWarnings("unused")
    public static boolean matchOverlayAgainstAssets() {
        try {
            String[] packages = OxygenCustomizer.getAppContext().getAssets().list("Overlays");
            int numberOfOverlaysInAssets = 0;

            assert packages != null;
            for (String overlay : packages) {
                numberOfOverlaysInAssets += Objects.requireNonNull(OxygenCustomizer.getAppContext().getAssets().list("Overlays/" + overlay)).length;
            }

            List<String> output = Shell.cmd(
                    "find /" + ModuleConstants.OVERLAY_DIR + "/ -maxdepth 1 -type f -print | wc -l"
            ).exec().getOut();
            if (output.isEmpty()) return false;
            int numberOfOverlaysInstalled = Integer.parseInt(output.get(0).trim());
            return numberOfOverlaysInAssets <= numberOfOverlaysInstalled;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isSafeOverlayPackage(String value) {
        return value != null && value.matches("[A-Za-z0-9._:]+");
    }

    private static boolean isSafeIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]+");
    }

    public static Drawable getDrawableFromOverlay(Context context, String pkg, String drawableName) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pm.getResourcesForApplication(pkg);
            int resId = res.getIdentifier(drawableName, "drawable", pkg);
            if (resId != 0X0)
                return res.getDrawable(resId);
            else
                return null;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d("OverlayUtil", "getDrawableFromOverlay: Package Not Found " + e.getMessage());
            return null;
        }
    }

    public static String getStringFromOverlay(Context context, String pkg, String stringName) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pm.getResourcesForApplication(pkg);
            int resId = res.getIdentifier(stringName, "string", pkg);
            if (resId != 0X0)
                return res.getString(resId);
            else
                return null;
        }
        catch (PackageManager.NameNotFoundException e) {
            Log.e("OverlayUtil", "getStringFromOverlay: Package Not Found" + e.getMessage());
            return null;
        }
    }


}
