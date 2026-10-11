package it.dhd.oxygencustomizer.utils.overlay;

import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.Prefs;
import it.dhd.oxygencustomizer.utils.helper.TypedValueUtil;

public class FabricatedUtil {

    public static List<String> getOverlayList() {
        return Shell.cmd("cmd overlay list |  grep -E '....com.android.shell:OxygenCustomizerComponent' | sed -E 's/^....com.android.shell:OxygenCustomizerComponent//'").exec().getOut();
    }

    public static List<String> getEnabledOverlayList() {
        return Shell.cmd("cmd overlay list |  grep -E '.x..com.android.shell:OxygenCustomizerComponent' | sed -E 's/^.x..com.android.shell:OxygenCustomizerComponent//'").exec().getOut();
    }

    public static List<String> getDisabledOverlayList() {
        return Shell.cmd("cmd overlay list |  grep -E '. ..com.android.shell:OxygenCustomizerComponent' | sed -E 's/^. ..com.android.shell:OxygenCustomizerComponent//'").exec().getOut();
    }

    private static final ExecutorService OVERLAY_WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OC-FabricatedOverlayWorker");
        thread.setDaemon(true);
        return thread;
    });

    public static void buildAndEnableOverlay(String target, String name, String type,
                                             String resourceName, String val) {
        List<String> commands = buildCommands(target, name, type, resourceName, val);
        OVERLAY_WRITER.execute(() -> applyOverlay(target, name, type, resourceName, val, commands));
    }

    public static void buildAndEnableOverlays(Object[]... args) {
        List<String[]> entries = new ArrayList<>();
        List<List<String>> commands = new ArrayList<>();
        for (Object[] argsSet : args) {
            if (argsSet == null || argsSet.length != 5) {
                throw new IllegalArgumentException("Fabricated overlay requires five parameters");
            }
            String[] entry = new String[5];
            for (int i = 0; i < 5; i++) {
                if (!(argsSet[i] instanceof String)) {
                    throw new IllegalArgumentException("Invalid fabricated overlay parameter");
                }
                entry[i] = (String) argsSet[i];
            }
            commands.add(buildCommands(entry[0], entry[1], entry[2], entry[3], entry[4]));
            entries.add(entry);
        }
        OVERLAY_WRITER.execute(() -> {
            for (int i = 0; i < entries.size(); i++) {
                String[] value = entries.get(i);
                applyOverlay(value[0], value[1], value[2], value[3], value[4], commands.get(i));
            }
        });
    }

    private static void applyOverlay(String target, String name, String type,
                                     String resourceName, String val, List<String> commands) {
        try {
            Shell.Result fabricate = Shell.cmd(commands.get(0)).exec();
            if (!fabricate.isSuccess()) {
                Log.e("FabricatedUtil", "Fabricate failed: " + name + " " + fabricate.getErr());
                return;
            }
            Shell.Result enabled = Shell.cmd(commands.get(1)).exec();
            if (!enabled.isSuccess()) {
                Log.e("FabricatedUtil", "Enable failed: " + name + " " + enabled.getErr());
                return;
            }
            Prefs.putString("FOCMDtarget" + name, target);
            Prefs.putString("FOCMDname" + name, name);
            Prefs.putString("FOCMDtype" + name, type);
            Prefs.putString("FOCMDresourceName" + name, resourceName);
            Prefs.putString("FOCMDval" + name, val);
            Prefs.putBoolean("fabricated" + name, true);
            refreshPostExecScript();
        } catch (Throwable e) {
            Log.e("FabricatedUtil", "Fabricated overlay failed: " + name, e);
        }
    }

    private static void refreshPostExecScript() {
        StringBuilder script = new StringBuilder();
        for (Map.Entry<String, ?> entry : Prefs.prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("fabricated") || !Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            String name = entry.getKey().substring("fabricated".length());
            try {
                List<String> commands = buildCommands(
                        Prefs.getString("FOCMDtarget" + name, ""),
                        Prefs.getString("FOCMDname" + name, ""),
                        Prefs.getString("FOCMDtype" + name, ""),
                        Prefs.getString("FOCMDresourceName" + name, ""),
                        Prefs.getString("FOCMDval" + name, ""));
                script.append(commands.get(0)).append('\n').append(commands.get(1)).append('\n');
            } catch (IllegalArgumentException badSetting) {
                Log.e("FabricatedUtil", "Skipping invalid saved overlay: " + name, badSetting);
            }
        }
        String path = ModuleConstants.MODULE_DIR + "/post-exec.sh";
        Shell.Result written = Shell.cmd("printf %s " + shellQuote(script.toString())
                + " > " + shellQuote(path + ".tmp")
                + " && mv -f " + shellQuote(path + ".tmp") + " " + shellQuote(path)).exec();
        if (!written.isSuccess()) {
            Log.e("FabricatedUtil", "Unable to persist reboot overlay script: " + written.getErr());
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void validateOverlayArguments(
            String target, String name, String type, String resourceName, String val) {
        if (target == null || name == null || type == null || resourceName == null || val == null) {
            throw new IllegalArgumentException("Fabricated overlay arguments must not be null");
        }
        if (!target.matches("[A-Za-z0-9._]+")
                && !"systemui".equals(target)
                && !"sysui".equals(target)) {
            throw new IllegalArgumentException("Invalid fabricated overlay target");
        }
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid fabricated overlay name");
        }
        if (!resourceName.matches("[A-Za-z0-9._]+")) {
            throw new IllegalArgumentException("Invalid fabricated resource name");
        }

        boolean validValue = switch (type) {
            case "color" -> val.matches("(?:0[xX][0-9A-Fa-f]{1,8}|-?[0-9]+)");
            case "dimen" -> val.matches("-?[0-9]+(?:dp|dip|sp|px|in|pt|mm)");
            case "bool" -> val.matches("(?:0|1|true|false)");
            case "integer" -> val.matches("-?[0-9]+");
            default -> false;
        };
        if (!validValue) {
            throw new IllegalArgumentException("Invalid fabricated overlay type/value");
        }
    }

    public static List<String> buildCommands(String target, String name, String type, String resourceName, String val) {
        validateOverlayArguments(target, name, type, resourceName, val);
        String resourceType = "0x1c";

        if (target.equals("systemui") || target.equals("sysui")) target = "com.android.systemui";

        switch (type) {
            case "color":
                resourceType = "0x1c";
                break;
            case "dimen":
                resourceType = "0x05";
                break;
            case "bool":
                resourceType = "0x12";
                break;
            case "integer":
                resourceType = "0x10";
                break;
        }

        if (type.equals("dimen")) {
            int valType = -1;

            if (val.contains("dp") || val.contains("dip")) {
                valType = TypedValue.COMPLEX_UNIT_DIP;
                val = val.replace("dp", "").replace("dip", "");
            } else if (val.contains("sp")) {
                valType = TypedValue.COMPLEX_UNIT_SP;
                val = val.replace("sp", "");
            } else if (val.contains("px")) {
                valType = TypedValue.COMPLEX_UNIT_PX;
                val = val.replace("px", "");
            } else if (val.contains("in")) {
                valType = TypedValue.COMPLEX_UNIT_IN;
                val = val.replace("in", "");
            } else if (val.contains("pt")) {
                valType = TypedValue.COMPLEX_UNIT_PT;
                val = val.replace("pt", "");
            } else if (val.contains("mm")) {
                valType = TypedValue.COMPLEX_UNIT_MM;
                val = val.replace("mm", "");
            }

            val = String.valueOf(TypedValueUtil.createComplexDimension(Integer.parseInt(val), valType));
        }

        List<String> commands = new ArrayList<>();
        commands.add("cmd overlay fabricate --target " + target + " --name OxygenCustomizerComponent" + name + " " + target + ":" + type + "/" + resourceName + " " + resourceType + " " + val);
        commands.add("cmd overlay enable --user current com.android.shell:OxygenCustomizerComponent" + name);

        Log.w("FabricatedUtil", "Commands: " + commands.toString());

        return commands;
    }

    public static void disableOverlay(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+")) return;
        OVERLAY_WRITER.execute(() -> {
            Shell.Result result = Shell.cmd(
                    "cmd overlay disable --user current com.android.shell:OxygenCustomizerComponent" + name).exec();
            if (!result.isSuccess()) {
                Log.e("FabricatedUtil", "Disable failed: " + name + " " + result.getErr());
                return;
            }
            Prefs.putBoolean("fabricated" + name, false);
            Prefs.clearPrefs("FOCMDtarget" + name, "FOCMDname" + name,
                    "FOCMDtype" + name, "FOCMDresourceName" + name, "FOCMDval" + name);
            refreshPostExecScript();
        });
    }

    public static void disableOverlays(String... names) {
        for (String name : names) disableOverlay(name);
    }

    public static boolean isOverlayEnabled(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+")) return false;
        List<String> output = Shell.cmd(
                "[[ $(cmd overlay list | grep -F '[x] com.android.shell:OxygenCustomizerComponent"
                        + name + "') ]] && echo 1 || echo 0"
        ).exec().getOut();
        return !output.isEmpty() && "1".equals(output.get(0));
    }

    public static boolean isOverlayDisabled(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+")) return false;
        List<String> output = Shell.cmd(
                "[[ $(cmd overlay list | grep -F '[ ] com.android.shell:OxygenCustomizerComponent"
                        + name + "') ]] && echo 1 || echo 0"
        ).exec().getOut();
        return !output.isEmpty() && "1".equals(output.get(0));
    }
}
