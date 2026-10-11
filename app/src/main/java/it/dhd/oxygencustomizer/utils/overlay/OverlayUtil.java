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
import java.util.concurrent.locks.ReentrantLock;

import it.dhd.oxygencustomizer.OxygenCustomizer;
import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.Dynamic;
import it.dhd.oxygencustomizer.utils.Prefs;

public class OverlayUtil {

    /** Read installed Oxygen Customizer overlays from the same user-scoped snapshot. */
    private static List<String> queryOverlayPackages(Boolean enabled) {
        Shell.Result result = Shell.cmd("cmd overlay list --user current").exec();
        if (!result.isSuccess()) {
            Log.w("OverlayUtil", "Unable to list overlays: " + result.getErr());
            return Collections.emptyList();
        }
        java.util.ArrayList<String> packages = new java.util.ArrayList<>();
        for (String line : result.getOut()) {
            String item = line.trim();
            if (!item.startsWith("[x] ") && !item.startsWith("[ ] ")) continue;
            boolean isEnabled = item.startsWith("[x] ");
            String name = item.substring(4).trim();
            if (name.startsWith("OxygenCustomizerComponent")
                    && (enabled == null || enabled == isEnabled)) {
                packages.add(name);
            }
        }
        return packages;
    }

    public static List<String> getOverlayList() {
        return queryOverlayPackages(null);
    }

    public static List<String> getEnabledOverlayList() {
        return queryOverlayPackages(true);
    }

    public static List<String> getDisabledOverlayList() {
        return queryOverlayPackages(false);
    }

    public static List<String> getOverlayForComponent(String componentName) {
        if (!isSafeIdentifier(componentName)) return Collections.emptyList();
        Shell.Result result = Shell.cmd("cmd overlay list --user current").exec();
        if (!result.isSuccess()) return Collections.emptyList();
        java.util.ArrayList<String> matches = new java.util.ArrayList<>();
        for (String line : result.getOut()) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("[x] ") && !trimmed.startsWith("[ ] ")) continue;
            String packageName = trimmed.substring(4);
            if (matchesComponentOverlay(packageName, componentName)) matches.add(line);
        }
        return matches;
    }

        // Keep writes ordered; never report the requested state as a successful operation
    // before Android's overlay manager confirms it.
    public static final ReentrantLock OPERATION_LOCK = new ReentrantLock(true);

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
        // Absence is unknown, not the same as an installed but disabled overlay.
        return null;
    }

    public static boolean isOverlayEnabled(String pkgName) {
        return Boolean.TRUE.equals(queryOverlayState(pkgName));
    }

    public static boolean isOverlayDisabled(String pkgName) {
        return Boolean.FALSE.equals(queryOverlayState(pkgName));
    }

    /**
     * "TH" and "IPSUI" are numbered overlay families, but "TH1" is an
     * individual component and must never accidentally select "TH10".
     */
    private static boolean matchesComponentOverlay(String packageName, String componentName) {
        String prefix = "OxygenCustomizerComponent" + componentName;
        if (!packageName.startsWith(prefix) || !packageName.endsWith(".overlay")) return false;
        String suffix = packageName.substring(prefix.length(),
                packageName.length() - ".overlay".length());
        if (suffix.isEmpty()) return true;
        if (Character.isDigit(componentName.charAt(componentName.length() - 1))) return false;
        return suffix.matches("[0-9]+");
    }

    public static void checkOverlayEnabledAndEnable(String componentName) {
        if (!isSafeIdentifier(componentName)) return;
        for (String pkgName : getOverlayList()) {
            if (matchesComponentOverlay(pkgName, componentName) && isOverlayDisabled(pkgName)) {
                enableOverlay(pkgName);
                return;
            }
        }
    }

        static boolean isOverlayInstalled(List<String> enabledOverlays, String pkgName) {
        return enabledOverlays.contains(pkgName);
    }

    /**
     * Serialize related overlay changes as one transaction, so another click
     * cannot interleave a disable and the matching enable operation.
     */
    public static void executeOverlayBatch(Runnable changes) {
        OVERLAY_EXECUTOR.execute(() -> {
            OPERATION_LOCK.lock();
            try {
                changes.run();
            } catch (Throwable failure) {
                Log.e("OverlayUtil", "Overlay batch failed", failure);
            } finally {
                OPERATION_LOCK.unlock();
            }
        });
    }

    private static void requestOverlayState(String pkgName, boolean enabled, boolean exclusive) {
        if (!isSafeOverlayPackage(pkgName)) return;
        executeOverlayBatch(() -> applyOverlayStateNow(pkgName, enabled, exclusive));
    }

    /**
     * Synchronous overlay transition for installation tasks. Do not call on
     * the UI thread. Unlike the public click helpers, it waits for the
     * OverlayManager and checks the observed result before persisting state.
     */
    public static boolean applyOverlayStateNow(String pkgName, boolean enabled, boolean exclusive) {
        if (!isSafeOverlayPackage(pkgName)) return false;
        OPERATION_LOCK.lock();
        try {
        String command = enabled
                ? "cmd overlay " + (exclusive ? "enable-exclusive --user current --category " : "enable --user current ")
                    + pkgName + " && cmd overlay set-priority --user current " + pkgName + " highest"
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
            if (result.isSuccess() && actualState == enabled) {
                // Refresh legacy counters after an observed, successful change.
                Dynamic.refreshOverlayInventory();
            }
        }
        return result.isSuccess() && actualState != null && actualState == enabled;
        } finally {
            OPERATION_LOCK.unlock();
        }
    }

    public static void enableOverlay(String pkgName) {
        requestOverlayState(pkgName, true, false);
    }

    public static void enableOverlays(String... pkgNames) {
        executeOverlayBatch(() -> {
            for (String pkgName : pkgNames) {
                if (isSafeOverlayPackage(pkgName)) applyOverlayStateNow(pkgName, true, false);
            }
        });
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
        executeOverlayBatch(() -> {
            for (String pkgName : pkgNames) {
                if (isSafeOverlayPackage(pkgName)) applyOverlayStateNow(pkgName, false, false);
            }
        });
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
