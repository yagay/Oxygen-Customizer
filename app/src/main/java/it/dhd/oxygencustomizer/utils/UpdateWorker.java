package it.dhd.oxygencustomizer.utils;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.activity.MainActivity;
import it.dhd.oxygencustomizer.ui.fragments.UpdateFragment;

public class UpdateWorker extends ListenableWorker {
    final Context mContext;
    public UpdateWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        mContext = appContext;
    }
    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        SharedPreferences prefs = getDefaultSharedPreferences(mContext.createDeviceProtectedStorageContext());

        boolean UpdateWifiOnly = prefs.getBoolean("checkOnWifi", true);

        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null || connectivityManager.getActiveNetwork() == null) {
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(Result.retry());
                return completer;
            });
        }

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

        boolean isGoodNetwork = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (!UpdateWifiOnly
                    || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));

        if (!isGoodNetwork) {
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(Result.retry());
                return completer;
            });
        }

        return CallbackToFutureAdapter.getFuture(completer -> {
            new UpdateFragment.updateChecker(result -> {
                onCheckedCallback.onFinished(result);
                Object versionCode = result != null ? result.get("versionCode") : null;
                if (versionCode instanceof Integer && (Integer) versionCode >= 0) {
                    completer.set(Result.success());
                } else {
                    completer.set(Result.retry());
                }
            }, UpdateFragment.Flavor.ALL).start();
            return "Oxygen Customizer update check";
        });
    }

    private void showUpdateNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w("OxygenCustomizer", "Skipping update notification: POST_NOTIFICATIONS not granted");
            return;
        }

        Intent notificationIntent = new Intent(mContext, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.putExtra("newUpdate", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, mContext.getString(R.string.notification_channel_update))
                .setSmallIcon(R.drawable.ic_notification_foreground)
                .setContentTitle(mContext.getString(R.string.new_update_title))
                .setContentText(mContext.getString(R.string.new_update_desc))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.w("OxygenCustomizer", "NotificationManager unavailable");
            return;
        }
        createChannel(notificationManager);
        notificationManager.notify(0, notificationBuilder.build());
    }

    public void createChannel(NotificationManager notificationManager) {
        NotificationChannel channel = new NotificationChannel(mContext.getString(R.string.notification_channel_update), mContext.getString(R.string.notification_channel_update), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(mContext.getString(R.string.notification_channel_update_desc));
        notificationManager.createNotificationChannel(channel);
    }

    UpdateFragment.TaskDoneCallback onCheckedCallback = result -> {
        try {
            String versionName = BuildConfig.VERSION_NAME;
            int currentVersionCode = BuildConfig.VERSION_CODE;
            boolean isBeta = versionName.contains("beta");
            boolean isNightly = versionName.contains("nightly");
            boolean isStable = !isBeta && !isNightly;

            int currentNightly = isNightly ?
                    Integer.parseInt(versionName.substring(versionName.indexOf("#") + 1, versionName.lastIndexOf(")"))) :
                    -1;

            Integer latestVersionCode = (Integer) result.get("versionCode");

            if (latestVersionCode != null && latestVersionCode > currentVersionCode) {
                showUpdateNotification();
            } else if (isNightly) {
                if (result.get("versionType").equals(UpdateFragment.NIGHTLY)) {
                    int devBuild = (int) result.get("devBuild");
                    if (devBuild > currentNightly) {
                        showUpdateNotification();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("OxygenCustomizer", "Error while checking for updates", e);
        }
    };
}
