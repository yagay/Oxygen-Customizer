package it.dhd.oxygencustomizer.utils.helper;

import com.topjohnwu.superuser.Shell;

import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.RootUtil;

/** Checked, atomic file replacement for module backup and recovery. */
public final class BackupRestore {
    private static final String ROUNDNESS_APK = "OxygenCustomizerComponentCR1.apk";

    private BackupRestore() {}

    public static void backupFiles() {
        checked("mkdir -p -- " + quote(ModuleConstants.BACKUP_DIR), "Prepare backup directory");
        String source = ModuleConstants.OVERLAY_DIR + "/" + ROUNDNESS_APK;
        if (RootUtil.fileExists(source)) {
            replaceAtomically(source, ModuleConstants.BACKUP_DIR + "/" + ROUNDNESS_APK);
        }
    }

    public static void restoreFiles() {
        String backup = ModuleConstants.BACKUP_DIR + "/" + ROUNDNESS_APK;
        if (RootUtil.fileExists(backup)) {
            replaceAtomically(backup, ModuleConstants.TEMP_MODULE_OVERLAY_DIR + "/" + ROUNDNESS_APK);
        }
        checked("rm -rf -- " + quote(ModuleConstants.BACKUP_DIR), "Clean restored backup");
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
