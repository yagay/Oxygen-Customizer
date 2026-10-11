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
        for (int i = 0; i < names.length; i++) {
            if (enabled[i]) {
                if (!it.dhd.oxygencustomizer.utils.overlay.OverlayUtil
                        .applyOverlayStateNow(names[i], true, false)) {
                    Log.e("OC-OverlayCompile", "Could not restore overlay " + names[i]);
                }
            }
        }
    }
}
