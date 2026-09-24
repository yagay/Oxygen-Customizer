package it.dhd.oxygencustomizer.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

import it.dhd.oxygencustomizer.IRootProviderProxy;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.utils.BitmapSubjectSegmenter;

public class RootProviderProxy extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new RootPoviderProxyIPC(this);
    }

    class RootPoviderProxyIPC extends IRootProviderProxy.Stub
    {
        /** @noinspection unused*/
        String TAG = getClass().getSimpleName();

        private final List<String> rootAllowedPacks;

        private RootPoviderProxyIPC(Context context)
        {
            rootAllowedPacks = Arrays.asList(context.getResources().getStringArray(R.array.root_requirement));
        }

        /** @noinspection RedundantThrows*/
        @Override
        public String[] runCommand(String command) throws RemoteException {
            try {
                ensureEnvironment();

                List<String> result = Shell.cmd(command).exec().getOut();
                return result.toArray(new String[0]);
            }
            catch (Throwable t)
            {
                return new String[0];
            }
        }

        @Override
        public void applyTheme(String theme) throws RemoteException {
            ensureEnvironment();

            if (theme == null || !theme.matches("[A-Za-z0-9._]+")) {
                throw new RemoteException("Invalid overlay package name");
            }

            try {
                Shell.cmd("cmd overlay enable --user current " + theme,
                        "cmd overlay set-priority " + theme + " highest").submit();
            } catch (Throwable t) {
                Log.e(TAG, "applyTheme: ", t);
            }
        }

        @Override
        public void extractSubject(Bitmap input, String resultPath) throws RemoteException {
            ensureEnvironment();

            try {
                new BitmapSubjectSegmenter(getApplicationContext()).segmentSubject(input, new BitmapSubjectSegmenter.SegmentResultListener() {
                    @Override
                    public void onSuccess(Bitmap result) {
                        try {
                            File tempFile = File.createTempFile("lswt", ".png");

                            Log.d(TAG,"DepthWallpaper extractSubject: " + tempFile.getAbsolutePath() + " -> " + resultPath);

                            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                                result.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            } finally {
                                result.recycle();
                            }

                            String quotedTemp = shellQuote(tempFile.getAbsolutePath());
                            String quotedResult = shellQuote(resultPath);
                            Shell.cmd("cp -F " + quotedTemp + " " + quotedResult).exec();
                            Shell.cmd("chmod 644 " + quotedResult).exec();
                            //noinspection ResultOfMethodCallIgnored
                            tempFile.delete();
                            Log.d(TAG, "DepthWallpaper onSuccess: BitmapSubjectSegmenter " + resultPath);
                        } catch (Throwable t) {
                            Log.e(TAG, "onSuccess: BitmapSubjectSegmenter", t);
                        }
                    }

                    @Override
                    public void onFail() {
                        Log.d(TAG, "onFail: BitmapSubjectSegmenter");
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "extractSubject: BitmapSubjectSegmenter", t);
            }
        }

        private String shellQuote(String value) throws RemoteException {
            if (value == null || value.indexOf('\0') >= 0) {
                throw new RemoteException("Invalid shell path");
            }
            return "'" + value.replace("'", "'\\''") + "'";
        }

        private void ensureEnvironment() throws RemoteException {
            // Verify the caller before touching libsu. Merely binding an exported
            // service must never be enough to trigger a root prompt.
            ensureSecurity(Binder.getCallingUid());

            try {
                Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER));
                if (!Shell.getShell().isRoot()) {
                    throw new RemoteException("Root permission denied");
                }
            } catch (RemoteException e) {
                throw e;
            } catch (Throwable t) {
                RemoteException error = new RemoteException("Unable to initialize root shell");
                error.initCause(t);
                throw error;
            }
        }

        private void ensureSecurity(int uid) throws RemoteException {
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages != null) {
                for (String packageName : packages) {
                    if (rootAllowedPacks.contains(packageName)) {
                        return;
                    }
                }
            }
            throw new RemoteException("You do know you're not supposed to use this service. So...");
        }
    }
}