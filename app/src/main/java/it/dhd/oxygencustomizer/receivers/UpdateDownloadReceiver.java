package it.dhd.oxygencustomizer.receivers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.UUID;

import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.activity.MainActivity;
import it.dhd.oxygencustomizer.ui.fragments.UpdateFragment;
import it.dhd.oxygencustomizer.utils.Prefs;

/** Persist update jobs and finish them without retaining a UI Fragment. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "OC-UpdateDownload";
    private static final String JOB_STORE = "oc_update_download_jobs";
    private static final String JOB_PREFIX = "job_";
    private static final String INPUT_ID = "downloadId";

    private static SharedPreferences jobs(Context context) {
        return context.getApplicationContext().getSharedPreferences(JOB_STORE, Context.MODE_PRIVATE);
    }

    public static long enqueueDownload(Context context, String url,
                                       HashMap<String, Object> metadata, boolean nightly) {
        if (metadata == null || TextUtils.isEmpty(url) ||
                !"https".equalsIgnoreCase(Uri.parse(url).getScheme())) {
            throw new IllegalArgumentException("An HTTPS update source is required");
        }
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("DownloadManager is unavailable");
        String version = nightly ? "nightly-" + metadata.get("versionCode") + "-" + metadata.get("devBuild")
                : metadata.get("version") + "-" + metadata.get("versionCode");
        version = version.replaceAll("[^A-Za-z0-9._-]", "_");
        if (version.length() > 72) version = version.substring(0, 72);
        String name = "OxygenCustomizer-" + version + "-"
                + UUID.randomUUID().toString().substring(0, 8) + (nightly ? ".zip" : ".apk");
        JSONObject job = new JSONObject();
        try {
            File target = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), name);
            job.put("path", target.getCanonicalPath());
            job.put("sha256", String.valueOf(metadata.getOrDefault("sha256", "")));
            job.put("nightly", nightly);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid update destination", failure);
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Oxygen Customizer Update Package")
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        long id = manager.enqueue(request);
        if (!jobs(context).edit().putString(JOB_PREFIX + id, job.toString()).commit()) {
            manager.remove(id);
            throw new IllegalStateException("Unable to persist update job");
        }
        return id;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id < 0 || !jobs(context).contains(JOB_PREFIX + id)) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FinishWorker.class)
                .setInputData(new Data.Builder().putLong(INPUT_ID, id).build()).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "oc-update-download-" + id, ExistingWorkPolicy.KEEP, request);
    }

    public static final class FinishWorker extends Worker {
        public FinishWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
            super(context, parameters);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();
            long id = getInputData().getLong(INPUT_ID, -1);
            if (id < 0) return Result.failure();
            String saved = jobs(context).getString(JOB_PREFIX + id, null);
            if (saved == null) return Result.success();
            boolean success = false;
            boolean terminal = false;
            try {
                JSONObject job = new JSONObject(saved);
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) return Result.retry();
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        terminal = true;
                    } else {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (status == DownloadManager.STATUS_PENDING
                                || status == DownloadManager.STATUS_RUNNING
                                || status == DownloadManager.STATUS_PAUSED) return Result.retry();
                        terminal = true;
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            String source = cursor.getString(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                            Uri uri = Uri.parse(source == null ? "" : source);
                            if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                                File actual = new File(uri.getPath()).getCanonicalFile();
                                File expected = new File(job.getString("path")).getCanonicalFile();
                                File downloads = Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS).getCanonicalFile();
                                if (actual.equals(expected) && actual.isFile()
                                        && actual.getPath().startsWith(downloads.getPath() + File.separator)
                                        && verifyChecksum(actual, job.optString("sha256", ""))) {
                                    notifyInstall(context, id, actual, job.optBoolean("nightly", false));
                                    success = true;
                                } else {
                                    Log.e(TAG, "Update path or checksum verification failed");
                                }
                            }
                        }
                    }
                }
            } catch (Exception failure) {
                Log.e(TAG, "Update finalization error", failure);
                terminal = true;
            } finally {
                if (terminal) jobs(context).edit().remove(JOB_PREFIX + id).apply();
            }
            if (!success) showFailure(context, id);
            return success ? Result.success() : Result.failure();
        }

        private static boolean verifyChecksum(File file, String expected) {
            if (TextUtils.isEmpty(expected) || "null".equals(expected)) return true;
            if (!expected.matches("(?i)[0-9a-f]{64}")) return false;
            try (InputStream input = new FileInputStream(file)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
                StringBuilder value = new StringBuilder();
                for (byte b : digest.digest()) value.append(String.format("%02x", b & 255));
                return expected.equalsIgnoreCase(value.toString());
            } catch (Exception error) {
                Log.e(TAG, "Unable to verify downloaded package", error);
                return false;
            }
        }

        private static void notifyInstall(Context context, long id, File file, boolean nightly) {
            String token = UUID.randomUUID().toString();
            Prefs.putString("pending_update_token_" + id, token);
            Intent launch = new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_RUN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra("updateTapped", true).putExtra("filePath", file.getAbsolutePath())
                    .putExtra("isNightly", nightly).putExtra("updateDownloadId", id)
                    .putExtra("updateToken", token);
            PendingIntent pending = PendingIntent.getActivity(context, requestCode(id), launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            post(context, id, new NotificationCompat.Builder(context, UpdateFragment.UPDATES_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_foreground)
                    .setContentTitle(context.getText(R.string.update_notification_title))
                    .setContentText(context.getText(R.string.update_notification_text))
                    .setContentIntent(pending).setAutoCancel(true));
        }

        private static void showFailure(Context context, long id) {
            post(context, id, new NotificationCompat.Builder(context, UpdateFragment.UPDATES_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_foreground)
                    .setContentTitle(context.getText(R.string.download_failed))
                    .setContentText(context.getText(R.string.try_again_later)));
        }

        private static int requestCode(long id) {
            return (int) (id ^ (id >>> 32));
        }

        @SuppressLint("MissingPermission")
        private static void post(Context context, long id, NotificationCompat.Builder notification) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            NotificationManager manager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(new NotificationChannel(
                        UpdateFragment.UPDATES_CHANNEL_ID, "Oxygen Customizer Updates",
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
            try {
                NotificationManagerCompat.from(context).notify(requestCode(id), notification.build());
            } catch (SecurityException error) {
                Log.w(TAG, "Unable to post update notification", error);
            }
        }
    }
}
