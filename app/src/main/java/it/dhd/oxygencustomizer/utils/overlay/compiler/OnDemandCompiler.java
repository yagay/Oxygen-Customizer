package it.dhd.oxygencustomizer.utils.overlay.compiler;

import static it.dhd.oxygencustomizer.utils.ModuleConstants.BACKUP_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.OVERLAY_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.SIGNED_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.SYSTEM_OVERLAY_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.TEMP_CACHE_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.TEMP_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.TEMP_OVERLAY_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.UNSIGNED_DIR;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.UNSIGNED_UNALIGNED_DIR;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;

import it.dhd.oxygencustomizer.utils.overlay.compiler.OverlayCompileCoordinator;

import it.dhd.oxygencustomizer.utils.FileUtil;
import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.RootUtil;
import it.dhd.oxygencustomizer.utils.SystemUtil;
import it.dhd.oxygencustomizer.utils.helper.BinaryInstaller;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

public class OnDemandCompiler {

    private static final String TAG = OnDemandCompiler.class.getSimpleName();
    private static String mOverlayName = null;
    private static String mPackage = null;
    private static int mStyle = 0;
    private static boolean mForce = false;

    public static boolean buildOverlay(String overlay_name, int style, String targetPackage, boolean force) throws IOException {
        OverlayCompileCoordinator.LOCK.lock();
        String targetOverlay = "OxygenCustomizerComponent" + overlay_name + ".overlay";
        boolean wasEnabled = force && OverlayUtil.isOverlayEnabled(targetOverlay);
        boolean completed = false;
        try {
        mOverlayName = overlay_name;
        mPackage = targetPackage;
        mStyle = style;
        mForce = force;

        preExecute();
        moveOverlaysToCache();

        // Create AndroidManifest.xml
        if (OverlayCompiler.createManifest(overlay_name, targetPackage, TEMP_CACHE_DIR + "/" + targetPackage + "/" + overlay_name)) {
            Log.e(TAG, "Failed to create Manifest for " + overlay_name + "! Exiting...");
            postExecute(true);
            return true;
        }

        // Build APK using AAPT
        if (OverlayCompiler.runAapt(TEMP_CACHE_DIR + "/" + targetPackage + "/" + overlay_name, targetPackage)) {
            Log.e(TAG, "Failed to build " + overlay_name + "! Exiting...");
            postExecute(true);
            return true;
        }

        // ZipAlign the APK
        if (OverlayCompiler.zipAlign(UNSIGNED_UNALIGNED_DIR + "/" + overlay_name + "-unsigned-unaligned.apk")) {
            Log.e(TAG, "Failed to align " + overlay_name + "-unsigned-unaligned.apk! Exiting...");
            postExecute(true);
            return true;
        }

        // Sign the APK
        if (OverlayCompiler.apkSigner(UNSIGNED_DIR + "/" + overlay_name + "-unsigned.apk")) {
            Log.e(TAG, "Failed to sign " + overlay_name + "-unsigned.apk! Exiting...");
            postExecute(true);
            return true;
        }

        postExecute(false);
        completed = true;
        return false;
        } finally {
            if (!completed && wasEnabled) {
                OverlayUtil.applyOverlayStateNow(targetOverlay, true, false);
            }
            OverlayCompileCoordinator.LOCK.unlock();
        }
    }

    private static void preExecute() throws IOException {
        // Create symbolic link
        BinaryInstaller.symLinkBinaries();

        // Clean data directory
        Shell.cmd("rm -rf " + TEMP_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();

        // Extract overlay from assets
        FileUtil.copyAssets("CompileOnDemand/" + mPackage + "/" + mOverlayName + mStyle);

        // Create temp directory
        Shell.cmd("rm -rf " + TEMP_DIR + "; mkdir -p " + TEMP_DIR).exec();
        Shell.cmd("mkdir -p " + TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + SIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + TEMP_CACHE_DIR + "/" + mPackage + "/").exec();
        if (!mForce) {
            Shell.cmd("mkdir -p " + BACKUP_DIR).exec();
        }

        if (mForce) {
            // Disable the overlay in case it is already enabled
            OverlayUtil.applyOverlayStateNow("OxygenCustomizerComponent" + mOverlayName + ".overlay", false, false);
        }
    }

    private static void postExecute(boolean hasErroredOut) throws IOException {
        if (!hasErroredOut) {
            String name = "OxygenCustomizerComponent" + mOverlayName + ".apk";
            String signed = SIGNED_DIR + "/" + name;
            if (mForce) {
                String staging = ModuleConstants.DATA_DIR + "/" + name;
                runChecked("cp -f " + signed + " " + staging, "Stage overlay APK");
                RootUtil.setPermissions(644, staging);
                runChecked("pm install -r " + staging, "Install overlay APK");
                Shell.cmd("rm -f " + staging).exec();
                SystemUtil.mountRW();
                try {
                    runChecked("cp -f " + signed + " " + SYSTEM_OVERLAY_DIR + "/" + name,
                            "Copy overlay to system");
                    RootUtil.setPermissions(644, SYSTEM_OVERLAY_DIR + "/" + name);
                } finally {
                    SystemUtil.mountRO();
                }
                if (!OverlayUtil.applyOverlayStateNow(
                        "OxygenCustomizerComponent" + mOverlayName + ".overlay", true, false)) {
                    throw new IOException("Overlay manager rejected " + mOverlayName);
                }
            } else {
                runChecked("cp -f " + signed + " " + BACKUP_DIR + "/" + name,
                        "Save overlay to backup");
            }
        }
        Shell.cmd("rm -rf " + TEMP_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();
    }

    private static void runChecked(String command, String action) throws IOException {
        Shell.Result result = Shell.cmd(command).exec();
        if (!result.isSuccess()) {
            throw new IOException(action + " failed: " + result.getErr());
        }
    }

    private static void moveOverlaysToCache() throws IOException {
        OverlayCompileCoordinator.runChecked("mv -f \"" + ModuleConstants.DATA_DIR
                    + "/CompileOnDemand/" + mPackage + "/" + mOverlayName + mStyle
                    + "\" \"" + TEMP_CACHE_DIR + "/" + mPackage + "/" + mOverlayName + "\"",
                    "Move overlay source");
    }
}
