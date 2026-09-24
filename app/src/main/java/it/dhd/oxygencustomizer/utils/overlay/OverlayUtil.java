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

    public static boolean isOverlayEnabled(String pkgName) {
        if (!isSafeOverlayPackage(pkgName)) return false;
        List<String> output = Shell.cmd(
                "[[ $(cmd overlay list | grep -F '[x] " + pkgName + "') ]] && echo 1 || echo 0"
        ).exec().getOut();
        return !output.isEmpty() && "1".equals(output.get(0));
    }

    public static boolean isOverlayDisabled(String pkgName) {
        return !isOverlayEnabled(pkgName);
    }

    public static void checkOverlayEnabledAndEnable(String componentName) {
        if (!isSafeIdentifier(componentName)) return;
        List<String> component = Shell.cmd(
                "cmd overlay list | grep \".x..OxygenCustomizerComponent" + componentName + "\""
        ).exec().getOut();
        if (!component.isEmpty()) {
            String num = component.get(0).split("OxygenCustomizerComponent" + componentName)[1].split("\\.overlay")[0];
            enableOverlay("OxygenCustomizerComponent" + componentName + num + ".overlay");
            Log.d("OverlayUtil", "checkOverlayEnabledAndEnable: Enabled " + componentName + num);
        }
    }

    static boolean isOverlayInstalled(List<String> enabledOverlays, String pkgName) {
        for (String line : enabledOverlays) {
            if (line.equals(pkgName)) return true;
        }
        return false;
    }

    public static void enableOverlay(String pkgName) {
        if (!isSafeOverlayPackage(pkgName)) return;
        Prefs.putBoolean(pkgName, true);
        if (getModulePrefs() != null) {
            getModulePrefs().edit().putBoolean(pkgName, true).apply();
        }
        Shell.cmd("cmd overlay enable --user current " + pkgName, "cmd overlay set-priority " + pkgName + " highest").submit();
    }

    public static void enableOverlays(String... pkgNames) {
        StringBuilder command = new StringBuilder();

        for (String pkgName : pkgNames) {
            if (!isSafeOverlayPackage(pkgName)) continue;
            Prefs.putBoolean(pkgName, true);
            command.append("cmd overlay enable --user current ").append(pkgName).append("; cmd overlay set-priority ").append(pkgName).append(" highest; ");
        }

        if (!command.isEmpty()) {
            Shell.cmd(command.toString().trim()).submit();
        }
    }

    public static void enableOverlayExclusiveInCategory(String pkgName) {
        if (!isSafeOverlayPackage(pkgName)) return;
        Prefs.putBoolean(pkgName, true);
        Shell.cmd("cmd overlay enable-exclusive --user current --category " + pkgName, "cmd overlay set-priority " + pkgName + " highest").submit();
    }

    public static void enableOverlaysExclusiveInCategory(String... pkgNames) {
        StringBuilder command = new StringBuilder();

        for (String pkgName : pkgNames) {
            if (!isSafeOverlayPackage(pkgName)) continue;
            Prefs.putBoolean(pkgName, true);
            command.append("cmd overlay enable-exclusive --user current --category ")
                    .append(pkgName)
                    .append("; cmd overlay set-priority ")
                    .append(pkgName)
                    .append(" highest; ");
        }

        if (!command.isEmpty()) {
            Shell.cmd(command.toString().trim()).submit();
        }
    }

    public static void disableOverlay(String pkgName) {
        if (!isSafeOverlayPackage(pkgName)) return;
        Prefs.putBoolean(pkgName, false);
        if (getModulePrefs() != null) {
            getModulePrefs().edit().putBoolean(pkgName, false).apply();
        }
        Shell.cmd("cmd overlay disable --user current " + pkgName).submit();
    }

    public static void disableOverlays(String... pkgNames) {
        StringBuilder command = new StringBuilder();

        for (String pkgName : pkgNames) {
            if (!isSafeOverlayPackage(pkgName)) continue;
            Prefs.putBoolean(pkgName, false);
            command.append("cmd overlay disable --user current ").append(pkgName).append("; ");
        }

        if (!command.isEmpty()) {
            Shell.cmd(command.toString().trim()).submit();
        }
    }

    public static void changeOverlayState(Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments must be even.");
        }

        StringBuilder command = new StringBuilder();

        for (int i = 0; i < args.length; i += 2) {
            String pkgName = (String) args[i];
            boolean state = (boolean) args[i + 1];
            if (!isSafeOverlayPackage(pkgName)) continue;

            Prefs.putBoolean(pkgName, state);

            if (state) {
                command.append("cmd overlay enable --user current ").append(pkgName).append("; cmd overlay set-priority ").append(pkgName).append(" highest; ");
            } else {
                command.append("cmd overlay disable --user current ").append(pkgName).append("; ");
            }
        }

        if (!command.isEmpty()) {
            Shell.cmd(command.toString().trim()).submit();
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
