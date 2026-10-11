package it.dhd.oxygencustomizer.utils.helper;

import com.topjohnwu.superuser.Shell;

import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.RootUtil;

/** Checked, atomic file replacement for module backup and recovery. */
public final class BackupRestore {
    private static final String ROUNDNESS_APK = "OxygenCustomizerComponentCR1.apk";
    // The public backup directory is shared with the overlay compilers.
    // Rebuilding the module must never delete their unrelated APK backups.
    private static final String MODULE_BACKUP_DIR = ModuleConstants.BACKUP_DIR + "/module-rebuild";

    private BackupRestore() {}

    public static void backupFiles() {
        checked("mkdir -p -- " + quote(MODULE_BACKUP_DIR), "Prepare CR1 backup directory");
        String source = ModuleConstants.OVERLAY_DIR + "/" + ROUNDNESS_APK;
        if (RootUtil.fileExists(source)) {
            replaceAtomically(source, MODULE_BACKUP_DIR + "/" + ROUNDNESS_APK);
        }
    }

    public static void restoreFiles() {
        String backup = MODULE_BACKUP_DIR + "/" + ROUNDNESS_APK;
        if (!RootUtil.fileExists(backup)) return;
        replaceAtomically(backup, ModuleConstants.TEMP_MODULE_OVERLAY_DIR + "/" + ROUNDNESS_APK);
        // Remove only this rebuild's CR1 snapshot. Never touch shared backups.
        checked("rm -f -- " + quote(backup), "Clean restored CR1 backup");
    }

    private static void replaceAtomically(String source, String destination) {
        String pending = destination + ".pending";
        String parent = destination.substring(0, destination.lastIndexOf('/'));
        try {
            checked("mkdir -p -- " + quote(parent)
                    + " && cp -p -- " + quote(source) + " " + quote(pending)
                    + " && mv -f -- " + quote(pending) + " " + quote(destination),
                    "Replace backed up APK");
        } catch (RuntimeException failure) {
            Shell.cmd("rm -f -- " + quote(pending)).exec();
            throw failure;
        }
    }

    private static void checked(String command, String operation) {
        Shell.Result result = Shell.cmd(command).exec();
        if (!result.isSuccess()) {
            throw new IllegalStateException(operation + " failed: " + result.getErr());
        }
    }

    private static String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
