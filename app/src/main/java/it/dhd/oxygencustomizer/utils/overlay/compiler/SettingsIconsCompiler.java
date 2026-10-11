package it.dhd.oxygencustomizer.utils.overlay.compiler;

import static it.dhd.oxygencustomizer.utils.Constants.Packages.SETTINGS;
import static it.dhd.oxygencustomizer.utils.FileUtil.copyAssets;
import static it.dhd.oxygencustomizer.utils.RootUtil.setPermissions;
import static it.dhd.oxygencustomizer.utils.SystemUtil.mountRO;
import static it.dhd.oxygencustomizer.utils.SystemUtil.mountRW;
import static it.dhd.oxygencustomizer.utils.helper.BinaryInstaller.symLinkBinaries;
import static it.dhd.oxygencustomizer.utils.helper.Logger.writeLog;
import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.disableOverlay;
import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.enableOverlays;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;

import it.dhd.oxygencustomizer.utils.overlay.compiler.OverlayCompileCoordinator;
import java.util.ArrayList;
import java.util.List;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

public class SettingsIconsCompiler {

    private final static String TAG = SettingsIconsCompiler.class.getSimpleName();

    private static int mIconSet;
    private static boolean mForce;

    private static final List<String> mPackages = new ArrayList<>() {{
        add(SETTINGS);
        add(BuildConfig.APPLICATION_ID.replaceAll(".debug", ""));
    }};

    public static boolean buildOverlay(int iconSet, String resources, boolean force) throws IOException {
        OverlayCompileCoordinator.LOCK.lock();
        String[] rollbackNames = {"OxygenCustomizerComponentSIP1.overlay", "OxygenCustomizerComponentSIP2.overlay"};
        boolean[] rollbackEnabled = new boolean[rollbackNames.length];
        if (force) {
            for (int i = 0; i < rollbackNames.length; i++) rollbackEnabled[i] = OverlayUtil.isOverlayEnabled(rollbackNames[i]);
        }
        boolean completed = false;
        try {
        mIconSet = iconSet;
        mForce = force;

        preExecute();
        moveOverlaysToCache();

        for (int i = 0; i < mPackages.size(); i++) {
            String overlayName = "SIP" + (i + 1);

            // Create AndroidManifest.xml
            if (OverlayCompiler.createManifest(
                    overlayName,
                    mPackages.get(i),
                    ModuleConstants.TEMP_CACHE_DIR + "/" + mPackages.get(i) + "/" + overlayName
            )) {
                Log.e(TAG, "Failed to create Manifest for " + overlayName + "! Exiting...");
                postExecute(true);
                return true;
            }

            // Write resources
            if (!resources.isEmpty() && writeResources(
                    ModuleConstants.TEMP_CACHE_DIR + "/" + mPackages.get(i) + "/" + overlayName,
                    resources
            )) {
                Log.e(TAG, "Failed to write resource for " + overlayName + "! Exiting...");
                postExecute(true);
                return true;
            }

            // Build APK using AAPT
            if (OverlayCompiler.runAapt(
                    ModuleConstants.TEMP_CACHE_DIR + "/" + mPackages.get(i) + "/" + overlayName,
                    mPackages.get(i)
            )) {
                Log.e(TAG, "Failed to build " + overlayName + "! Exiting...");
                postExecute(true);
                return true;
            }

            // ZipAlign the APK
            if (OverlayCompiler.zipAlign(ModuleConstants.UNSIGNED_UNALIGNED_DIR + "/" + overlayName + "-unsigned-unaligned.apk")) {
                Log.e(TAG, "Failed to align " + overlayName + "-unsigned-unaligned.apk! Exiting...");
                postExecute(true);
                return true;
            }

            // Sign the APK
            if (OverlayCompiler.apkSigner(ModuleConstants.UNSIGNED_DIR + "/" + overlayName + "-unsigned.apk")) {
                Log.e(TAG, "Failed to sign " + overlayName + "-unsigned.apk! Exiting...");
                postExecute(true);
                return true;
            }
        }

        postExecute(false);
        completed = true;
        return false;
        } finally {
            if (!completed && force) OverlayCompileCoordinator.restorePrevious(rollbackNames, rollbackEnabled);
            OverlayCompileCoordinator.LOCK.unlock();
        }
    }

    private static void preExecute() throws IOException {
        // Create symbolic link
        symLinkBinaries();

        // Clean data directory
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();

        // Extract overlay from assets
        for (String packageName : mPackages) {
            copyAssets("CompileOnDemand/" + packageName + "/ICS" + mIconSet);
        }

        // Create temp directory
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_DIR + "; mkdir -p " + ModuleConstants.TEMP_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.SIGNED_DIR).exec();

        for (String packageName : mPackages) {
            Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR + "/" + packageName + "/").exec();
        }

        if (!mForce) {
            Shell.cmd("mkdir -p " + ModuleConstants.BACKUP_DIR).exec();
        } else {
            OverlayUtil.applyOverlayStateNow("OxygenCustomizerComponentSIP1.overlay", false, false);
            OverlayUtil.applyOverlayStateNow("OxygenCustomizerComponentSIP2.overlay", false, false);
        }
    }

    private static void moveOverlaysToCache() {
        for (int i = 0; i < mPackages.size(); i++) {
            Shell.cmd(
                    "mv -f \"" + ModuleConstants.DATA_DIR + "/CompileOnDemand/" + mPackages.get(i) + "/" + "ICS" + mIconSet + "\" \"" + ModuleConstants.TEMP_CACHE_DIR + "/" + mPackages.get(i) + "/" + "SIP" + (i + 1) + "\""
            ).exec();
        }
    }

    private static boolean writeResources(String source, String resources) {
        Shell.Result result = Shell.cmd(
                "rm -rf " + source + "/res/values/OxygenCustomizer.xml",
                "printf '" + resources + "' > " + source + "/res/values/OxygenCustomizer.xml;"
        ).exec();

        if (result.isSuccess()) {
            Log.i(TAG + " - WriteResources", "Successfully written resources for SettingsIcons");
        } else {
            Log.e(TAG + " - WriteResources", "Failed to write resources for SettingsIcons\n" +
                    String.join("\n", result.getOut()));
            writeLog(TAG + " - WriteResources", "Failed to write resources for SettingsIcons", result.getOut());
        }

        return !result.isSuccess();
    }

    private static void postExecute(boolean hasErroredOut) throws IOException {
        if (!hasErroredOut) {
            for (int i = 1; i <= mPackages.size(); i++) {
                String name = "OxygenCustomizerComponentSIP" + i + ".apk";
                String signed = ModuleConstants.SIGNED_DIR + "/" + name;
                OverlayCompileCoordinator.runChecked("cp -f " + signed + " " + ModuleConstants.OVERLAY_DIR + "/" + name,
                        "Save settings icon overlay");
                OverlayCompileCoordinator.runChecked("chmod 644 " + ModuleConstants.OVERLAY_DIR + "/" + name,
                        "Set settings icon permissions");
                if (mForce) {
                    String staging = ModuleConstants.DATA_DIR + "/" + name;
                    OverlayCompileCoordinator.runChecked("cp -f " + signed + " " + staging, "Stage settings icons");
                    try {
                        OverlayCompileCoordinator.runChecked("pm install -r " + staging, "Install settings icons");
                    } finally {
                        Shell.cmd("rm -f " + staging).exec();
                    }
                }
            }
            if (mForce) {
                mountRW();
                try {
                    for (int i = 1; i <= mPackages.size(); i++) {
                        String name = "OxygenCustomizerComponentSIP" + i + ".apk";
                        OverlayCompileCoordinator.runChecked("cp -f " + ModuleConstants.SIGNED_DIR + "/" + name + " "
                                + ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + name, "Install settings icons to system");
                        OverlayCompileCoordinator.runChecked("chmod 644 " + ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + name,
                                "Set system overlay permissions");
                    }
                } finally {
                    mountRO();
                }
                for (int i = 1; i <= mPackages.size(); i++) {
                    String pkg = "OxygenCustomizerComponentSIP" + i + ".overlay";
                    if (!OverlayUtil.applyOverlayStateNow(pkg, true, false)) {
                        throw new IOException("Overlay manager rejected " + pkg);
                    }
                }
            } else {
                for (int i = 1; i <= mPackages.size(); i++) {
                    String name = "OxygenCustomizerComponentSIP" + i + ".apk";
                    OverlayCompileCoordinator.runChecked("cp -f " + ModuleConstants.SIGNED_DIR + "/" + name + " "
                            + ModuleConstants.BACKUP_DIR + "/" + name, "Backup settings icon overlay");
                }
            }
        }
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();
    }

}
