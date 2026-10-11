package it.dhd.oxygencustomizer.utils.overlay.compiler;

import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
import it.dhd.oxygencustomizer.utils.ModuleConstants;
import it.dhd.oxygencustomizer.utils.SystemUtil;
import android.util.Log;
import com.topjohnwu.superuser.Shell;

/**
 * Global compiler lock: AAPT2/zipalign/apksigner and the overlay compilers
 * currently share ModuleConstants.TEMP_DIR and mutable compilation state.
 * Do not delete or recreate those directories from overlapping operations.
 */
public final class OverlayCompileCoordinator {
    public static final ReentrantLock LOCK = new ReentrantLock(true);

    private OverlayCompileCoordinator() {}

    public static void runChecked(String command, String operation) throws IOException {
        Shell.Result result = Shell.cmd(command).exec();
        if (!result.isSuccess()) {
            Log.e("OC-OverlayCompile", operation + ": " + result.getErr());
            throw new IOException(operation + " failed: " + result.getErr());
        }
    }

    public static void restorePrevious(String[] names, boolean[] enabled) {
        if (names.length != enabled.length) {
            throw new IllegalArgumentException("Overlay rollback state length mismatch");
        }
        for (int i = 0; i < names.length; i++) {
            boolean active = it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.isOverlayEnabled(names[i]);
            if (active == enabled[i]) continue;
            if (!it.dhd.oxygencustomizer.utils.overlay.OverlayUtil
                    .applyOverlayStateNow(names[i], enabled[i], false)) {
                Log.e("OC-OverlayCompile", "Could not restore overlay " + names[i]
                        + " to enabled=" + enabled[i]);
            }
        }
    }

    /** Always clear shared compiler staging, even if extraction or signing throws. */
    public static void cleanupStaging(String... paths) {
        for (String path : paths) {
            if (path == null || path.isBlank()) continue;
            try {
                Shell.Result removed = Shell.cmd("rm -rf -- " + shellQuote(path)).exec();
                if (!removed.isSuccess()) {
                    Log.w("OC-OverlayCompile", "Unable to clean staging: " + path
                            + " " + removed.getErr());
                }
            } catch (Throwable failure) {
                Log.e("OC-OverlayCompile", "Staging cleanup failed: " + path, failure);
            }
        }
    }

    /**
     * Snapshot only the overlay APK destinations we may overwrite.
     * Backups live under app-private storage and are retained until the
     * compiler finishes or its rollback completes.
     */
    public static FileSnapshot snapshotOverlayFiles(String... destinations) throws IOException {
        String directory = ModuleConstants.DATA_DIR + "/overlay-rollback";
        runChecked("rm -rf -- " + shellQuote(directory)
                + " && mkdir -p -- " + shellQuote(directory), "Prepare overlay rollback");
        String[] copies = new String[destinations.length];
        boolean[] existed = new boolean[destinations.length];
        for (int i = 0; i < destinations.length; i++) {
            String destination = destinations[i];
            if (destination == null || !destination.endsWith(".apk")
                    || (!destination.startsWith(ModuleConstants.OVERLAY_DIR + "/")
                        && !destination.startsWith(ModuleConstants.SYSTEM_OVERLAY_DIR + "/"))) {
                throw new IOException("Refusing to snapshot unknown overlay path");
            }
            String backup = directory + "/old-" + i + ".apk";
            Shell.Result probe = Shell.cmd("if [ -f " + shellQuote(destination)
                    + " ]; then echo exists; else echo absent; fi").exec();
            if (!probe.isSuccess() || probe.getOut().isEmpty()) {
                throw new IOException("Unable to inspect overlay target: " + destination);
            }
            existed[i] = "exists".equals(probe.getOut().get(0).trim());
            copies[i] = backup;
            if (existed[i]) {
                runChecked("cp -p -- " + shellQuote(destination) + " " + shellQuote(backup),
                        "Snapshot existing overlay");
            }
        }
        return new FileSnapshot(destinations.clone(), copies, existed);
    }

    public static final class FileSnapshot {
        private final String[] destinations;
        private final String[] backups;
        private final boolean[] existed;

        private FileSnapshot(String[] destinations, String[] backups, boolean[] existed) {
            this.destinations = destinations;
            this.backups = backups;
            this.existed = existed;
        }

        private boolean needsRestore(int index) {
            String path = destinations[index];
            if (!existed[index]) {
                return Shell.cmd("test -f " + shellQuote(path)).exec().isSuccess();
            }
            return !Shell.cmd("cmp -s " + shellQuote(backups[index])
                    + " " + shellQuote(path)).exec().isSuccess();
        }

        private void restoreFile(int index) {
            String command = existed[index]
                    ? "cp -p -- " + shellQuote(backups[index]) + " " + shellQuote(destinations[index])
                    : "rm -f -- " + shellQuote(destinations[index]);
            try {
                Shell.Result result = Shell.cmd(command).exec();
                if (!result.isSuccess()) {
                    Log.e("OC-OverlayCompile", "Failed to restore " + destinations[index]
                            + ": " + result.getErr());
                }
            } catch (Throwable t) {
                Log.e("OC-OverlayCompile", "Failed to restore " + destinations[index], t);
            }
        }

        /** Best-effort restore; never mask the original compilation exception. */
        public void restore() {
            boolean needSystem = false;
            boolean[] dirty = new boolean[destinations.length];
            for (int i = 0; i < destinations.length; i++) {
                dirty[i] = needsRestore(i);
                if (dirty[i] && destinations[i].startsWith(ModuleConstants.SYSTEM_OVERLAY_DIR + "/")) {
                    needSystem = true;
                }
            }
            for (int i = 0; i < destinations.length; i++) {
                if (dirty[i] && !destinations[i].startsWith(ModuleConstants.SYSTEM_OVERLAY_DIR + "/")) {
                    restoreFile(i);
                }
            }
            if (needSystem) {
                boolean mounted = false;
                try {
                    SystemUtil.mountRW();
                    mounted = true;
                    for (int i = 0; i < destinations.length; i++) {
                        if (dirty[i] && destinations[i].startsWith(ModuleConstants.SYSTEM_OVERLAY_DIR + "/")) {
                            restoreFile(i);
                        }
                    }
                } catch (Throwable failure) {
                    Log.e("OC-OverlayCompile", "Could not remount for overlay rollback", failure);
                } finally {
                    if (mounted) {
                        try {
                            SystemUtil.mountRO();
                        } catch (Throwable failure) {
                            Log.e("OC-OverlayCompile", "Could not restore read-only mount", failure);
                        }
                    }
                }
                // A prior pm install -r may have registered the new APK.
                // Reinstall the original system APK where available.
                for (int i = 0; i < destinations.length; i++) {
                    if (dirty[i] && existed[i]
                            && destinations[i].startsWith(ModuleConstants.SYSTEM_OVERLAY_DIR + "/")) {
                        Shell.Result result = Shell.cmd("pm install -r " + shellQuote(backups[i])).exec();
                        if (!result.isSuccess()) {
                            Log.e("OC-OverlayCompile", "Could not reinstall prior overlay: "
                                    + destinations[i] + " " + result.getErr());
                        }
                    }
                }
            }
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
