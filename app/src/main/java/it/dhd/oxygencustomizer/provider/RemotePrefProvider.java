package it.dhd.oxygencustomizer.provider;

import android.content.Context;
import android.os.Binder;
import android.os.Process;

import com.crossbowffs.remotepreferences.RemotePreferenceFile;
import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.R;

public class RemotePrefProvider extends RemotePreferenceProvider {

    public RemotePrefProvider() {
        super(BuildConfig.APPLICATION_ID, new RemotePreferenceFile[]{
                new RemotePreferenceFile(BuildConfig.APPLICATION_ID + "_preferences", true)
        });
    }

    @Override
    protected boolean checkAccess(String prefFileName, String prefKey, boolean write) {
        final int callingUid = Binder.getCallingUid();

        // Only the app itself may mutate hook preferences.
        if (callingUid == Process.myUid()) {
            return true;
        }
        if (write) {
            return false;
        }

        final Context context = getContext();
        if (context == null) {
            return false;
        }

        final String[] callerPackages = context.getPackageManager().getPackagesForUid(callingUid);
        if (callerPackages == null || callerPackages.length == 0) {
            return false;
        }

        // Hooked processes only need read access. Keep this list in sync with LSPosed scope.
        final Set<String> allowedReaders = new HashSet<>(
                Arrays.asList(context.getResources().getStringArray(R.array.xposed_scope))
        );
        allowedReaders.add(BuildConfig.APPLICATION_ID);

        for (String packageName : callerPackages) {
            if (allowedReaders.contains(packageName)) {
                return true;
            }
        }
        return false;
    }
}
