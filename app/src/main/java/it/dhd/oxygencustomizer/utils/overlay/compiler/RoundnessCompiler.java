package it.dhd.oxygencustomizer.utils.overlay.compiler;

import static it.dhd.oxygencustomizer.utils.Constants.Packages.FRAMEWORK;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;
import static it.dhd.oxygencustomizer.utils.helper.Logger.writeLog;

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

public class RoundnessCompiler {

    private static final String TAG = RoundnessCompiler.class.getSimpleName();
    private static final String[] mPackages = {FRAMEWORK, SYSTEM_UI};
    private static final String[] mOverlayName = {"CRN1", "CR1"};
    private static boolean mForce = false;

    public static boolean buildOverlay(String[] resources, boolean force) throws IOException {
        OverlayCompileCoordinator.LOCK.lock();
        String[] rollbackNames = {"OxygenCustomizerComponentCRN1.overlay", "OxygenCustomizerComponentCR1.overlay"};
        boolean[] rollbackEnabled = new boolean[rollbackNames.length];
        boolean completed = false;
        try {
        if (force) {
            for (int i = 0; i < rollbackNames.length; i++) {
                rollbackEnabled[i] = OverlayUtil.isOverlayEnabled(rollbackNames[i]);
            }
        }
        mForce = force;

        preExecute();

        for (int i = 0; i < mOverlayName.length; i++) {
            // Create AndroidManifest.xml
            if (OverlayCompiler.createManifest(mOverlayName[i], mPackages[i], ModuleConstants.DATA_DIR + "/Overlays/" + mPackages[i] + "/" + mOverlayName[i])) {
                Log.e(TAG, "Failed to create Manifest for " + mOverlayName[i] + "! Exiting...");
                postExecute(true);
                return true;
            }

            // Write resources
            if (writeResources(ModuleConstants.DATA_DIR + "/Overlays/" + mPackages[i] + "/" + mOverlayName[i], resources[i])) {
                Log.e(TAG, "Failed to write resource for " + mOverlayName[i] + "! Exiting...");
                postExecute(true);
                return true;
            }

            // Build APK using AAPT
            if (OverlayCompiler.runAapt(ModuleConstants.DATA_DIR + "/Overlays/" + mPackages[i] + "/" + mOverlayName[i], mPackages[i])) {
                Log.e(TAG, "Failed to build " + mOverlayName[i] + "! Exiting...");
                postExecute(true);
                return true;
            }

            // ZipAlign the APK
            if (OverlayCompiler.zipAlign(ModuleConstants.UNSIGNED_UNALIGNED_DIR + "/" + mOverlayName[i] + "-unsigned-unaligned.apk")) {
                Log.e(TAG, "Failed to align " + mOverlayName[i] + "-unsigned-unaligned.apk! Exiting...");
                postExecute(true);
                return true;
            }

            // Sign the APK
            if (OverlayCompiler.apkSigner(ModuleConstants.UNSIGNED_DIR + "/" + mOverlayName[i] + "-unsigned.apk")) {
                Log.e(TAG, "Failed to sign " + mOverlayName[i] + "-unsigned.apk! Exiting...");
                postExecute(true);
                return true;
            }
        }

        postExecute(false);
        completed = true;
        return false;
        } finally {
            try {
                if (!completed && force) {
                    OverlayCompileCoordinator.restorePrevious(rollbackNames, rollbackEnabled);
                }
            } finally {
                OverlayCompileCoordinator.LOCK.unlock();
            }
        }
    }

    private static void preExecute() throws IOException {
        // Create symbolic link
        BinaryInstaller.symLinkBinaries();

        // Clean data directory
        OverlayCompileCoordinator.runChecked("rm -rf " + ModuleConstants.TEMP_DIR, "Prepare overlay staging");
        OverlayCompileCoordinator.runChecked("rm -rf " + ModuleConstants.DATA_DIR + "/Overlays", "Prepare overlay staging");

        // Extract overlay from assets
        for (int i = 0; i < mOverlayName.length; i++)
            FileUtil.copyAssets("Overlays/" + mPackages[i] + "/" + mOverlayName[i]);

        // Create temp directory
        OverlayCompileCoordinator.runChecked("rm -rf " + ModuleConstants.TEMP_DIR + "; mkdir -p " + ModuleConstants.TEMP_DIR, "Prepare overlay staging");
        OverlayCompileCoordinator.runChecked("mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR, "Prepare overlay staging");
        OverlayCompileCoordinator.runChecked("mkdir -p " + ModuleConstants.UNSIGNED_UNALIGNED_DIR, "Prepare overlay staging");
        OverlayCompileCoordinator.runChecked("mkdir -p " + ModuleConstants.UNSIGNED_DIR, "Prepare overlay staging");
        OverlayCompileCoordinator.runChecked("mkdir -p " + ModuleConstants.SIGNED_DIR, "Prepare overlay staging");
        if (!mForce) {
            OverlayCompileCoordinator.runChecked("mkdir -p " + ModuleConstants.BACKUP_DIR, "Prepare overlay staging");
        }

        if (mForce) {
            // Disable the overlay in case it is already enabled
            String[] overlayNames = new String[mOverlayName.length];
            for (int i = 0; i < mOverlayName.length; i++) {
                    overlayNames[i] = "OxygenCustomizerComponent" + mOverlayName[i] + ".overlay";
                }
            for (String overlay : overlayNames) {
                OverlayUtil.applyOverlayStateNow(overlay, false, false);
            }
        }
    }

    private static void postExecute(boolean hasErroredOut) throws IOException {
        if (!hasErroredOut) {
            for (String overlayName : mOverlayName) {
                String file = "OxygenCustomizerComponent" + overlayName + ".apk";
                String signed = ModuleConstants.SIGNED_DIR + "/" + file;
                OverlayCompileCoordinator.runChecked("cp -f " + signed + " " + ModuleConstants.OVERLAY_DIR + "/" + file, "Copy compiled roundness overlay");
                OverlayCompileCoordinator.runChecked("chmod 644 " + ModuleConstants.OVERLAY_DIR + "/" + file, "Fix roundness overlay permissions");
                if (mForce) {
                    String staging = ModuleConstants.DATA_DIR + "/" + file;
                    OverlayCompileCoordinator.runChecked("cp -f " + signed + " " + staging, "Stage roundness APK");
                    try {
                        OverlayCompileCoordinator.runChecked("pm install -r " + staging, "Install roundness APK");
                    } finally {
                        Shell.cmd("rm -f " + staging).exec();
                    }
                }
            }
            if (mForce) {
                SystemUtil.mountRW();
                try {
                    for (String overlayName : mOverlayName) {
                        String file = "OxygenCustomizerComponent" + overlayName + ".apk";
                        OverlayCompileCoordinator.runChecked("cp -f " + ModuleConstants.SIGNED_DIR + "/" + file + " "
                                + ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + file, "Install system roundness overlay");
                        OverlayCompileCoordinator.runChecked("chmod 644 " + ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + file, "Fix system overlay mode");
                    }
                } finally {
                    SystemUtil.mountRO();
                }
                for (String overlayName : mOverlayName) {
                    String pkg = "OxygenCustomizerComponent" + overlayName + ".overlay";
                    if (!OverlayUtil.applyOverlayStateNow(pkg, true, false)) {
                        throw new IOException("Overlay manager rejected " + pkg);
                    }
                }
            } else {
                for (String overlayName : mOverlayName) {
                    String file = "OxygenCustomizerComponent" + overlayName + ".apk";
                    OverlayCompileCoordinator.runChecked("cp -f " + ModuleConstants.SIGNED_DIR + "/" + file + " "
                            + ModuleConstants.BACKUP_DIR + "/" + file, "Backup roundness overlay");
                }
            }
        }
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/Overlays").exec();
    }

    private static boolean writeResources(String source, String resources) {
        Shell.Result result = Shell.cmd("rm -rf " + source + "/res/values/dimens.xml", "printf '" + resources + "' > " + source + "/res/values/dimens.xml;").exec();

        if (result.isSuccess())
            Log.i(TAG + " - WriteResources", "Successfully written resources for UiRoundness");
        else {
            Log.e(TAG + " - WriteResources", "Failed to write resources for UiRoundness" + '\n' + String.join("\n", result.getOut()));
            writeLog(TAG + " - WriteResources", "Failed to write resources for UiRoundness", result.getOut());
        }

        return !result.isSuccess();
    }
}
