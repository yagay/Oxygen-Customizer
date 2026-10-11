package it.dhd.oxygencustomizer.utils.overlay.compiler;

import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
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

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
