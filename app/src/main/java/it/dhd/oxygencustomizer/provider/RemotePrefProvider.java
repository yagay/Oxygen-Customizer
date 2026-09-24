package it.dhd.oxygencustomizer.provider;

import android.os.Binder;
import android.os.Process;

import com.crossbowffs.remotepreferences.RemotePreferenceFile;
import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import it.dhd.oxygencustomizer.BuildConfig;

/**
 * Cross-process preference provider used by the hooked system processes.
 *
 * <p>The upstream RemotePreferences implementation allows every caller by
 * default. This provider is intentionally exported because SystemUI/Settings/
 * Launcher run under a different UID, so access is restricted here instead of
 * relying on the manifest export flag.</p>
 */
public class RemotePrefProvider extends RemotePreferenceProvider {

    private static final Set<String> READ_ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher"
    ));

    public RemotePrefProvider() {
        super(BuildConfig.APPLICATION_ID,
                new RemotePreferenceFile[]{
                        new RemotePreferenceFile(BuildConfig.APPLICATION_ID + "_preferences", true)
                });
    }

    @Override
    protected boolean checkAccess(String prefFileName, String prefKey, boolean write) {
        final int callingUid = Binder.getCallingUid();

        // The app itself may read and write its own preferences.
        if (callingUid == Process.myUid()) {
            return true;
        }

        final String[] packages = getContext() != null
                ? getContext().getPackageManager().getPackagesForUid(callingUid)
                : null;
        if (packages == null) {
            return false;
        }

        boolean allowedHost = false;
        for (String packageName : packages) {
            if (READ_ALLOWED_PACKAGES.contains(packageName)) {
                allowedHost = true;
                break;
            }
        }
        if (!allowedHost) {
            return false;
        }

        if (!write) {
            return true;
        }

        // Host processes may only maintain boot-loop health counters. All user
        // configuration remains app-owned and read-only from injected processes.
        return prefKey != null
                && (prefKey.startsWith("packageLastLoad_")
                    || prefKey.startsWith("packageStrike_"));

    }
}
