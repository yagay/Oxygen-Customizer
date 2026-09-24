package it.dhd.oxygencustomizer.utils;

import com.topjohnwu.superuser.Shell;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootUtil {

    public static boolean isDeviceRooted() {
        return Boolean.TRUE.equals(Shell.isAppGrantedRoot());
    }

    public static boolean isMagiskInstalled() {
        return Shell.cmd("magisk -v").exec().isSuccess();
    }

    public static boolean isKSUInstalled() {
        return Shell.cmd("/data/adb/ksud -h").exec().isSuccess();
    }

    public static boolean isApatchInstalled() {
        return Shell.cmd("apd --help").exec().isSuccess();
    }

    public static boolean moduleExists(String moduleId) {
        if (moduleId == null || !moduleId.matches("[A-Za-z0-9._-]+")) {
            return false;
        }
        return folderExists("/data/adb/modules/" + moduleId);
    }

    public static void setPermissions(final int permission, final String filename) {
        if (!isValidPermission(permission) || filename == null) return;
        Shell.cmd("chmod " + permission + " -- " + shellQuote(filename)).exec();
    }

    public static void setPermissionsRecursively(final int permission, final String foldername) {
        if (!isValidPermission(permission) || foldername == null) return;
        Shell.cmd("chmod -R " + permission + " -- " + shellQuote(foldername)).exec();
    }

    public static boolean fileExists(String dir) {
        if (dir == null) return false;
        List<String> lines = Shell.cmd("test -f " + shellQuote(dir) + " && echo '1'").exec().getOut();
        for (String line : lines) {
            if (line.contains("1")) return true;
        }
        return false;
    }

    public static boolean folderExists(String dir) {
        if (dir == null) return false;
        List<String> lines = Shell.cmd("test -d " + shellQuote(dir) + " && echo '1'").exec().getOut();
        for (String line : lines) {
            if (line.contains("1")) return true;
        }
        return false;
    }

    private static boolean isValidPermission(int permission) {
        return permission >= 0 && permission <= 7777;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static boolean deviceProperlyRooted() {
        return isDeviceRooted() && (isMagiskInstalled() || isKSUInstalled() || isApatchInstalled());
    }

    public static boolean requireMetamodule() {
        return isKSUInstalled() && getKsuVersion() >= 3 && !isMetaModuleInstalled();
    }

    public static int getKsuVersion() {
        String ksuVersion = Shell.cmd("ksud -V").exec().getOut().stream()
                .reduce("", (acc, s) -> acc + s).trim();

        Pattern pattern = Pattern.compile("\\b(\\d+)(?=\\.)");
        Matcher matcher = pattern.matcher(ksuVersion);

        if (matcher.find()) {
            String majorVersion = matcher.group(1);
            try {
                return Integer.parseInt(majorVersion);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public static boolean isMetaModuleInstalled() {
        String command = "for d in /data/adb/modules/*; do prop=\"$d/module.prop\"; [ -f \"$prop\" ] && grep -qiE \"^metamodule[[:space:]]*=[[:space:]]*(1|true)$\" \"$prop\" && echo true && exit 0; done; echo false";
        Shell.Result result = Shell.cmd(command).exec();

        List<String> output = result.getOut();
        return !output.isEmpty() && "true".equals(output.get(0));
    }

}
