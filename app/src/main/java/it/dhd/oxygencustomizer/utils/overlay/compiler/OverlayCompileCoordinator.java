package it.dhd.oxygencustomizer.utils.overlay.compiler;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Global compiler lock: AAPT2/zipalign/apksigner and the overlay compilers
 * currently share ModuleConstants.TEMP_DIR and mutable compilation state.
 * Do not delete or recreate those directories from overlapping operations.
 */
public final class OverlayCompileCoordinator {
    public static final ReentrantLock LOCK = new ReentrantLock(true);

    private OverlayCompileCoordinator() {}
}
