package it.dhd.oxygencustomizer.utils;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

/**
 * Root mount operations must never silently claim success. Callers invoke
 * mountRO in finally when writing mounted overlays.
 */
public class SystemUtil {
    private static final String TAG = "OC-SystemMount";

    public static void mountRW() {
        // The legacy "-mm -c magic_remount_rw" is not an executable command.
        if (RootUtil.moduleExists("magisk_overlayfs")) {
            execute("command -v magic_remount_rw >/dev/null 2>&1 && magic_remount_rw",
                    "magisk_overlayfs remount RW");
        } else if (RootUtil.moduleExists("overlayfs")) {
            execute("test -x /data/overlayfs/tmp/overlayrw && "
                    + "/data/overlayfs/tmp/overlayrw -rw /system/product/overlay",
                    "overlayfs remount RW");
        } else {
            execute("mount -o remount,rw /", "root filesystem remount RW");
        }
    }

    public static void mountRO() {
        if (RootUtil.moduleExists("magisk_overlayfs")) {
            execute("command -v magic_remount_ro >/dev/null 2>&1 && magic_remount_ro",
                    "magisk_overlayfs remount RO");
        } else if (RootUtil.moduleExists("overlayfs")) {
            execute("test -x /data/overlayfs/tmp/overlayrw && "
                    + "/data/overlayfs/tmp/overlayrw -ro /system/product/overlay",
                    "overlayfs remount RO");
        } else {
            execute("mount -o remount,ro /", "root filesystem remount RO");
        }
    }

    private static void execute(String command, String operation) {
        Shell.Result result = Shell.cmd(command).exec();
        if (!result.isSuccess()) {
            Log.e(TAG, operation + " failed: " + result.getErr());
            throw new IllegalStateException(operation + " failed");
        }
    }
}
