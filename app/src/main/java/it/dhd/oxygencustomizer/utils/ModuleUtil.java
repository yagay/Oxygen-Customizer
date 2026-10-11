package it.dhd.oxygencustomizer.utils;

import static it.dhd.oxygencustomizer.OxygenCustomizer.getAppContext;
import static it.dhd.oxygencustomizer.utils.Constants.OPLUS_FEATURE_XML;
import static it.dhd.oxygencustomizer.utils.Constants.OPLUS_MEMC_FEATURES;
import static it.dhd.oxygencustomizer.utils.Constants.OPLUS_POCKET_STUDIO_FEATURE;
import static it.dhd.oxygencustomizer.utils.Dynamic.skippedInstallation;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.MODULE_VERSION_CODE;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.MODULE_VERSION_NAME;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.MY_PRODUCT_EXTENSION_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.MY_PRODUCT_PERMISSIONS_DIR;
import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.getStringFromOverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.helper.BackupRestore;
import it.dhd.oxygencustomizer.utils.helper.BinaryInstaller;
import it.dhd.oxygencustomizer.utils.overlay.FabricatedUtil;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;
import it.dhd.oxygencustomizer.utils.overlay.compiler.OverlayCompileCoordinator;

public class ModuleUtil {

    private static final String TAG = ModuleUtil.class.getSimpleName();

    public static void handleModule() {
        OverlayCompileCoordinator.LOCK.lock();
        try {
            if (moduleExists()) {
                Shell.Result clean = Shell.cmd("rm -rf " + ModuleConstants.TEMP_DIR).exec();
                if (!clean.isSuccess()) throw new IllegalStateException("Unable to clean module staging directory");
                BackupRestore.backupFiles();
            }
            installModule();
        } finally {
            OverlayCompileCoordinator.LOCK.unlock();
        }
    }

    public static void handleModuleUninstall() {
        OverlayCompileCoordinator.LOCK.lock();
        java.util.List<String> previous = java.util.Collections.emptyList();
        try {
            if (moduleExists()) {
                previous = new java.util.ArrayList<>(OverlayUtil.getEnabledOverlayListChecked());
                for (String pkg : previous) {
                    if (!OverlayUtil.applyOverlayStateNow(pkg, false, false)) {
                        throw new IllegalStateException("Unable to disable overlay before uninstall: " + pkg);
                    }
                }
            }
            String modulePath = ModuleConstants.INSTALLED_MODULE_DIR;
            String safetyCopy = ModuleConstants.DATA_DIR + "/oc-uninstall-safety-copy";
            if (RootUtil.folderExists(modulePath)) {
                // Keep a complete root-owned snapshot until deletion succeeds.
                Shell.Result saved = Shell.cmd("rm -rf -- " + shellQuote(safetyCopy)
                        + " && cp -a -- " + shellQuote(modulePath)
                        + " " + shellQuote(safetyCopy)).exec();
                if (!saved.isSuccess()) {
                    throw new IllegalStateException("Unable to back up module before uninstall: "
                            + saved.getErr());
                }
            }
            Shell.Result deleted = Shell.cmd("rm -rf -- " + shellQuote(modulePath)).exec();
            if (!deleted.isSuccess()) {
                if (RootUtil.folderExists(safetyCopy)) {
                    Shell.Result restored = Shell.cmd(
                            "mkdir -p -- " + shellQuote(modulePath)
                            + " && cp -a -- " + shellQuote(safetyCopy + "/.")
                            + " " + shellQuote(modulePath + "/")).exec();
                    if (!restored.isSuccess()) {
                        Log.e(TAG, "Module uninstall rollback failed: " + restored.getErr()
                                + "; safety copy kept at " + safetyCopy);
                    }
                }
                throw new IllegalStateException("Root module removal failed: " + deleted.getErr());
            }
            Shell.Result clean = Shell.cmd("rm -rf -- " + shellQuote(safetyCopy)).exec();
            if (!clean.isSuccess()) Log.w(TAG, "Could not clean uninstall safety copy: " + clean.getErr());
        } catch (RuntimeException failure) {
            // A failed disable or file operation must not leave an accidental
            // mixture of selected overlays. Restore those that still exist.
            if (moduleExists()) {
                for (String pkg : previous) {
                    if (!OverlayUtil.isOverlayEnabled(pkg)
                            && !OverlayUtil.applyOverlayStateNow(pkg, true, false)) {
                        Log.e(TAG, "Uninstall rollback could not enable " + pkg);
                    }
                }
            }
            throw failure;
        } finally {
            OverlayCompileCoordinator.LOCK.unlock();
        }
    }


    static void installModule() {
        Log.d(TAG, "Magisk module does not exist, creating...");

        // Clean temporary directory
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_DIR).exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR).exec(), "Prepare root module files");

        requireModuleCommand(Shell.cmd(
                "printf 'id=OxygenCustomizer\n" +
                        "name=Oxygen Customizer\n" +
                        "version=" + MODULE_VERSION_NAME + "\n" +
                        "versionCode=" + MODULE_VERSION_CODE + "\n" +
                        "author=@DHD2280\n" +
                        "description=Systemless module for Oxygen Customizer.\n" +
                        "banner=banner.png\n" +
                        "' > " + ModuleConstants.TEMP_MODULE_DIR + "/module.prop"
        ).exec(), "Prepare root module files");

        requireModuleCommand(Shell.cmd(
                "printf 'MODDIR=${0%/*}\n\nmount --bind $MODDIR/my_product/etc/extension/com.oplus.oplus-feature.xml /my_product/etc/extension/com.oplus.oplus-feature.xml\nmount --bind $MODDIR/my_product/etc/permissions/oplus.product.display_features.xml /my_product/etc/permissions/oplus.product.display_features.xml' > " + ModuleConstants.TEMP_MODULE_DIR + "/post-fs-data.sh"
        ).exec(), "Prepare root module files");

        Log.d(TAG, "skipped installation: " + skippedInstallation);
        if (!skippedInstallation) {
            requireModuleCommand(Shell.cmd("printf 'MODDIR=${0%/*}\n\nwhile [ \"$(getprop sys.boot_completed | tr -d \"\\r\")\" != \"1\" ]\ndo\n sleep 1\ndone\nsleep 5\n\nsh $MODDIR/post-exec.sh\n\nuntil [ -d /storage/emulated/0/Android ]; do\n  sleep 1\ndone\nsleep 3\n\n" + "sleep 6\n\ntheme=$(cmd overlay list | grep \".x..OxygenCustomizerComponentTH\")\nnum=$(echo $theme | cut -d \"H\" -f 2 | cut -d \".\" -f 1)\nif [ \"${#num}\" -gt 0 ]\nthen\n cmd overlay enable OxygenCustomizerComponentTH$num.overlay\n cmd overlay set-priority OxygenCustomizerComponentTH$num.overlay highest\nfi\n\n' > " + ModuleConstants.TEMP_MODULE_DIR + "/service.sh").exec(), "Prepare root module files");
        } else {
            requireModuleCommand(Shell.cmd("printf 'MODDIR=${0%/*}\n\nwhile [ \"$(getprop sys.boot_completed | tr -d \"\\r\")\" != \"1\" ]\ndo\n sleep 1\ndone\nsleep 5\n\nsh $MODDIR/post-exec.sh\n\n' > " + ModuleConstants.TEMP_MODULE_DIR + "/service.sh").exec(), "Prepare root module files");
        }
        requireModuleCommand(Shell.cmd("touch " + ModuleConstants.TEMP_MODULE_DIR + "/system.prop").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("touch " + ModuleConstants.TEMP_MODULE_DIR + "/auto_mount").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/system").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/system/product").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/system/product/overlay").exec(), "Prepare root module files");
        // Preserve the existing CR1 customization while rebuilding a new module.
        // restoreFiles removes the backup only after its copy succeeds.
        BackupRestore.restoreFiles();
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/my_product").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/my_product/etc").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/my_product/etc/extension").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/my_product/etc/permissions").exec(), "Prepare root module files");
        // The module cannot be safely activated without both firmware XML files.
        copyRequiredSystemFile("/my_product/etc/extension/com.oplus.oplus-feature.xml",
                MY_PRODUCT_EXTENSION_DIR + "/");
        copyRequiredSystemFile("/my_product/etc/extension/com.oplus.oplus-feature.xml",
                MY_PRODUCT_EXTENSION_DIR + "/com.oplus.oplus-feature.xml.bak");
        copyRequiredSystemFile("/my_product/etc/permissions/oplus.product.display_features.xml",
                MY_PRODUCT_PERMISSIONS_DIR + "/");
        copyRequiredSystemFile("/my_product/etc/permissions/oplus.product.display_features.xml",
                MY_PRODUCT_PERMISSIONS_DIR + "/oplus.product.display_features.xml.bak");

        createMETAINF();

        writePostExec();
        BinaryInstaller.symLinkBinaries();

        Log.i(TAG, "Magisk module successfully created.");
    }

    private static void createMETAINF() {
        Log.d(TAG, "Creating META-INF directory");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/META-INF").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/META-INF/com").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/META-INF/com/google").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("mkdir -p " + ModuleConstants.TEMP_MODULE_DIR + "/META-INF/com/google/android").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("printf '" + ModuleConstants.MAGISK_UPDATE_BINARY + "' > " + ModuleConstants.TEMP_MODULE_DIR + "/META-INF/com/google/android/update-binary").exec(), "Prepare root module files");
        requireModuleCommand(Shell.cmd("printf '#MAGISK' > " + ModuleConstants.TEMP_MODULE_DIR + "/META-INF/com/google/android/updater-script").exec(), "Prepare root module files");
        Log.d(TAG, "META-INF directory created");
    }

    private static void requireModuleCommand(Shell.Result result, String operation) {
        if (!result.isSuccess()) {
            throw new IllegalStateException(operation + " failed: " + result.getErr());
        }
    }

    private static void writePostExec() {
        StringBuilder post_exec = new StringBuilder();

        SharedPreferences prefs = getAppContext().getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        Map<String, ?> map = prefs.getAll();
        for (Map.Entry<String, ?> item : map.entrySet()) {
            if (item.getValue() instanceof Boolean && ((Boolean) item.getValue()) && item.getKey().startsWith("fabricated")) {
                String name = item.getKey().substring("fabricated".length());
                try {
                    List<String> commands = FabricatedUtil.buildCommands(
                            Prefs.getString("FOCMDtarget" + name),
                            Prefs.getString("FOCMDname" + name),
                            Prefs.getString("FOCMDtype" + name),
                            Prefs.getString("FOCMDresourceName" + name),
                            Prefs.getString("FOCMDval" + name));
                    if (commands.size() != 2) {
                        throw new IllegalArgumentException("Unexpected fabricated command count");
                    }
                    post_exec.append(commands.get(0)).append('\n').append(commands.get(1)).append('\n');
                } catch (IllegalArgumentException badRule) {
                    Log.w(TAG, "Skipping malformed saved fabricated overlay: " + name, badRule);
                }

            }
        }

        Shell.Result script = Shell.cmd("printf %s " + shellQuote(post_exec.toString()) + " > "
                + shellQuote(ModuleConstants.TEMP_MODULE_DIR + "/post-exec.sh")).exec();
        if (!script.isSuccess()) {
            throw new IllegalStateException("Cannot write fabricated overlay startup commands");
        }
    }

    private static boolean shouldUseDefaultColors() {
        return !OverlayUtil.isOverlayEnabled("OxygenCustomizerComponentAMAC.overlay")
                && !OverlayUtil.isOverlayEnabled("OxygenCustomizerComponentAMGC.overlay")
                && !OverlayUtil.isOverlayEnabled("OxygenCustomizerComponentME.overlay");
    }

    public static boolean moduleExists() {
        return RootUtil.folderExists(ModuleConstants.OVERLAY_DIR);
    }

    public static String createModule(String sourceFolder, String destinationFilePath) throws Exception {
        File input = new File(sourceFolder);
        File output = new File(destinationFilePath);

        ZipParameters parameters = new ZipParameters();
        parameters.setIncludeRootFolder(false);
        parameters.setOverrideExistingFilesInZip(true);
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(CompressionLevel.NORMAL);

        try (ZipFile zipFile = new ZipFile(output)) {
            zipFile.addFolder(input, parameters);

            return zipFile.getFile().getAbsolutePath();
        }
    }

    public static boolean flashModule(String modulePath) throws Exception {
        Shell.Result result = null;

        if (RootUtil.isMagiskInstalled()) {
            result = Shell.cmd("magisk --install-module " + shellQuote(modulePath)).exec();
        } else if (RootUtil.isKSUInstalled()) {
            result = Shell.cmd(RootUtil.getKsuExecutable() + " module install " + shellQuote(modulePath)).exec();
        } else if (RootUtil.isApatchInstalled()) {
            result = Shell.cmd("apd module install " + shellQuote(modulePath)).exec();
        }

        if (result == null) {
            throw new Exception("No supported root found");
        } else if (result.isSuccess()) {
            Log.i(TAG, "Successfully flashed module");
        } else {
            Log.e(TAG, "Failed to flash module");
            throw new Exception(String.join("\n", result.getOut()));
        }

        return result.isSuccess();
    }

    public static boolean checkModuleVersion(Context context) {
        if (RootUtil.deviceProperlyRooted() && OverlayUtil.overlayExists()) {
            String version = getStringFromOverlay(context, "OxygenCustomizerComponentOCV.overlay", "oxygen_customizer_module_version");
            return version != null && version.equals(MODULE_VERSION_NAME);
        }
        return false;
    }

    /**
     * Root XML writers share the overlay/module lock and retain snapshots when rollback
     * fails. Do not overwrite an unresolved recovery snapshot on a later attempt.
     */
    private static void withModuleXmlTransaction(String operation, Runnable change,
                                                 String... paths) {
        OverlayCompileCoordinator.LOCK.lock();
        try {
            if (!moduleExists()) return;
            String[] backups = new String[paths.length];
            for (int i = 0; i < paths.length; i++) {
                backups[i] = paths[i] + ".oc-txn";
                if (!Shell.cmd("test ! -e " + shellQuote(backups[i])
                        + " && test ! -L " + shellQuote(backups[i])).exec().isSuccess()) {
                    throw new IllegalStateException("Unresolved " + operation
                            + " XML rollback snapshot: " + backups[i]);
                }
            }
            for (int i = 0; i < paths.length; i++) {
                Shell.Result saved = Shell.cmd("cp -p -- " + shellQuote(paths[i])
                        + " " + shellQuote(backups[i])).exec();
                if (!saved.isSuccess()) {
                    // Production files have not been changed yet.
                    for (int j = 0; j <= i; j++) {
                        Shell.cmd("rm -f -- " + shellQuote(backups[j])).exec();
                    }
                    throw new IllegalStateException("Unable to back up " + operation
                            + " XML: " + saved.getErr());
                }
            }

            boolean safeToDiscardBackups = false;
            try {
                change.run();
                safeToDiscardBackups = true;
            } catch (RuntimeException | Error failure) {
                boolean recovered = true;
                for (int i = 0; i < paths.length; i++) {
                    Shell.Result restored = Shell.cmd("cp -p -- " + shellQuote(backups[i])
                            + " " + shellQuote(paths[i])).exec();
                    if (!restored.isSuccess()) {
                        recovered = false;
                        Log.e(TAG, operation + " XML rollback failed: " + restored.getErr());
                    }
                }
                safeToDiscardBackups = recovered;
                if (!recovered) Log.e(TAG, operation + " XML snapshots kept for recovery");
                throw failure;
            } finally {
                if (safeToDiscardBackups) {
                    for (String backup : backups) {
                        Shell.Result removed = Shell.cmd("rm -f -- " + shellQuote(backup)).exec();
                        if (!removed.isSuccess())
                            Log.w(TAG, "Unable to clean XML backup: " + removed.getErr());
                    }
                }
            }
        } finally {
            OverlayCompileCoordinator.LOCK.unlock();
        }
    }

    public static void enablePocketStudio(boolean enable) {
        final String featurePath = ModuleConstants.MODULE_DIR
                + "/my_product/etc/extension/com.oplus.oplus-feature.xml";
        withModuleXmlTransaction("Pocket Studio",
                () -> enablePocketStudioUnchecked(enable), featurePath);
    }

    private static void enablePocketStudioUnchecked(boolean enable) {
        if (!moduleExists()) return;

        List<String> fileRead = readXmlLines(ModuleConstants.MODULE_DIR + "/my_product/etc/extension/com.oplus.oplus-feature.xml");
        if (fileRead.isEmpty()) return;
        Log.d(TAG, "com.oplus.oplus-feature: " + fileRead);

        Pattern pattern = Pattern.compile("oplus\\.software\\.pocketstudio\\.support");

        boolean featureAlreadyAdded = fileRead.stream().anyMatch(item -> pattern.matcher(item).find());

        if (enable && !featureAlreadyAdded) {
            if (!insertBeforeRootClosingTag(
                    fileRead,
                    String.format(OPLUS_FEATURE_XML, OPLUS_POCKET_STUDIO_FEATURE)
            )) return;
        } else if (!enable && featureAlreadyAdded) {
            fileRead.removeIf(element -> element.contains(OPLUS_POCKET_STUDIO_FEATURE));
        }

        writeXmlLines(
                "/data/adb/modules/OxygenCustomizer/my_product/etc/extension/com.oplus.oplus-feature.xml",
                fileRead
        );
    }

    public static void enableMemcFeature(boolean enable) {
        final String featurePath = ModuleConstants.MODULE_DIR
                + "/my_product/etc/extension/com.oplus.oplus-feature.xml";
        final String displayPath = ModuleConstants.MODULE_DIR
                + "/my_product/etc/permissions/oplus.product.display_features.xml";
        withModuleXmlTransaction("MEMC", () -> {
            enableMemcOplusFeature(enable);
            enableMemcOplusDisplayFeature(enable);
        }, featurePath, displayPath);
    }

    private static void enableMemcOplusFeature(boolean enable) {
        List<String> fileRead = readXmlLines(ModuleConstants.MODULE_DIR + "/my_product/etc/extension/com.oplus.oplus-feature.xml");
        if (fileRead.isEmpty()) throw new IllegalStateException("MEMC firmware XML is unavailable");
        Log.d(TAG, "com.oplus.oplus-feature: " + fileRead);

        Pattern pattern = Pattern.compile("name=\"(.*?)\"");
        if (!enable) {
            fileRead.removeIf(item -> {
                Matcher matcher = pattern.matcher(item);
                if (matcher.find()) {
                    String extractedText = matcher.group(1);
                    return OPLUS_MEMC_FEATURES.contains(extractedText);
                }
                return false;
            });
        } else {
            for (String memcFeature : OPLUS_MEMC_FEATURES) {
                boolean alreadyPresent = fileRead.stream().anyMatch(item -> {
                    Matcher matcher = pattern.matcher(item);
                    if (matcher.find()) {
                        String extractedText = matcher.group(1);
                        return extractedText.equals(memcFeature);
                    }
                    return false;
                });

                if (!alreadyPresent) {
                    if (!insertBeforeRootClosingTag(
                            fileRead,
                            String.format(OPLUS_FEATURE_XML, memcFeature)
                    )) throw new IllegalStateException("MEMC XML root closing tag is unavailable");
                }
            }
        }

        writeXmlLines(
                "/data/adb/modules/OxygenCustomizer/my_product/etc/extension/com.oplus.oplus-feature.xml",
                fileRead
        );
    }

    private static void enableMemcOplusDisplayFeature(boolean enable) {
        List<String> fileRead = readXmlLines(ModuleConstants.MODULE_DIR + "/my_product/etc/permissions/oplus.product.display_features.xml");
        if (fileRead.isEmpty()) throw new IllegalStateException("MEMC firmware XML is unavailable");
        Log.d(TAG, "oplus.product.display_features: " + fileRead);

        Pattern pattern = Pattern.compile("name=\"(.*?)\"");

        if (!enable) {
            fileRead.removeIf(item -> {
                Matcher matcher = pattern.matcher(item);
                if (matcher.find()) {
                    String extractedText = matcher.group(1);
                    return OPLUS_MEMC_FEATURES.contains(extractedText);
                }
                return false;
            });
        } else {
            for (String memcFeature : OPLUS_MEMC_FEATURES) {
                boolean alreadyPresent = fileRead.stream().anyMatch(item -> {
                    Matcher matcher = pattern.matcher(item);
                    if (matcher.find()) {
                        String extractedText = matcher.group(1);
                        return extractedText.equals(memcFeature);
                    }
                    return false;
                });

                if (!alreadyPresent) {
                    if (!insertBeforeRootClosingTag(
                            fileRead,
                            String.format(OPLUS_FEATURE_XML, memcFeature)
                    )) throw new IllegalStateException("MEMC XML root closing tag is unavailable");
                }
            }
        }

        writeXmlLines(
                "/data/adb/modules/OxygenCustomizer/my_product/etc/permissions/oplus.product.display_features.xml",
                fileRead
        );
    }

    private static List<String> readXmlLines(String sourcePath) {
        Shell.Result result = Shell.cmd("cat " + shellQuote(sourcePath)).exec();
        if (!result.isSuccess() || result.getOut().isEmpty()) {
            Log.e(TAG, "Unable to read XML source: " + sourcePath);
            return new ArrayList<>();
        }
        return new ArrayList<>(result.getOut());
    }

    private static boolean insertBeforeRootClosingTag(List<String> lines, String newLine) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("</") && trimmed.endsWith(">")) {
                lines.add(i, newLine);
                return true;
            }
        }
        Log.e(TAG, "Refusing to modify malformed XML: root closing tag not found");
        return false;
    }

    private static void writeXmlLines(String destinationPath, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            Log.e(TAG, "Refusing to write empty XML: " + destinationPath);
            throw new IllegalStateException("Refusing to write empty module XML: " + destinationPath);
        }
        String xml = String.join("\n", lines);
        Shell.Result result = Shell.cmd(
                "printf %s " + shellQuote(xml) + " > " + shellQuote(destinationPath)
        ).exec();
        if (!result.isSuccess()) {
            Log.e(TAG, "Failed to write XML: " + destinationPath + ", stderr=" + result.getErr());
            throw new IllegalStateException("Failed to write module XML: " + destinationPath);
        }
    }

    private static void copyRequiredSystemFile(String source, String destination) {
        Shell.Result result = Shell.cmd("cp -a " + shellQuote(source) + " " + shellQuote(destination)).exec();
        if (!result.isSuccess()) {
            Log.e(TAG, "Required OPlus firmware file unavailable: " + source
                    + ", stderr=" + result.getErr());
            throw new IllegalStateException("Unable to prepare OPlus system module: " + source);
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

}
