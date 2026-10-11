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
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
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

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.activity.MainActivity;
import it.dhd.oxygencustomizer.ui.fragments.UpdateFragment;
import it.dhd.oxygencustomizer.utils.Prefs;

/** Persist update jobs and finish them without retaining a UI Fragment. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "OC-UpdateDownload";
    private static final String JOB_STORE = "oc_update_download_jobs";
    private static final String JOB_PREFIX = "job_";
    private static final String READY_PREFIX = "ready_";

    /** Retained so a completed update remains installable when notifications are denied. */
    public static final class PendingInstall {
        public final long id;
        public final String path;
        public final boolean nightly;

        private PendingInstall(long id, String path, boolean nightly) {
            this.id = id;
            this.path = path;
            this.nightly = nightly;
        }
    }

    public static PendingInstall getPendingInstall(Context context) {
        PendingInstall newest = null;
        SharedPreferences store = jobs(context);
        for (String key : store.getAll().keySet()) {
            if (!key.startsWith(READY_PREFIX)) continue;
            try {
                long id = Long.parseLong(key.substring(READY_PREFIX.length()));
                JSONObject saved = new JSONObject(store.getString(key, ""));
                File file = new File(saved.getString("path")).getCanonicalFile();
                File downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).getCanonicalFile();
                if (!file.isFile() || !file.getPath().startsWith(downloads.getPath() + File.separator)) {
                    store.edit().remove(key).apply();
                    continue;
                }
                if (newest == null || id > newest.id) {
                    newest = new PendingInstall(id, file.getAbsolutePath(),
                            saved.optBoolean("nightly", false));
                }
            } catch (Exception badRecord) {
                Log.w(TAG, "Dropping invalid ready-to-install record", badRecord);
                store.edit().remove(key).apply();
            }
        }
        return newest;
    }
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
        // Always schedule a reconciliation after persisting the ID.
        // Even if DownloadManager finishes before its broadcast can be
        // matched, this worker will still see the completed download.
        scheduleCheck(context, id);
        return id;
    }

    public static void recoverPendingDownloads(Context context) {
        for (String key : jobs(context).getAll().keySet()) {
            if (!key.startsWith(JOB_PREFIX)) continue;
            try {
                scheduleCheck(context, Long.parseLong(key.substring(JOB_PREFIX.length())));
            } catch (NumberFormatException badKey) {
                Log.w(TAG, "Ignoring malformed update tracking key", badKey);
            }
        }
    }

    private static void scheduleCheck(Context context, long id) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FinishWorker.class)
                .setInputData(new Data.Builder().putLong(INPUT_ID, id).build()).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "oc-update-download-" + id, ExistingWorkPolicy.REPLACE, request);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id < 0 || !jobs(context).contains(JOB_PREFIX + id)) return;
        scheduleCheck(context, id);
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
                                        && verifyChecksum(context, actual, job.optString("sha256", ""))) {
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

        private static boolean verifyChecksum(Context context, File file, String expected) {
            if (TextUtils.isEmpty(expected) || "null".equals(expected)) {
                // Some legacy update feeds lack SHA-256. For APKs, require
                // exactly the app's installed signing identity instead.
                // ZIPs also undergo APK signature verification after extraction.
                return file.getName().endsWith(".zip") || hasMatchingAppSignature(context, file);
            }
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

        public static boolean hasMatchingAppSignature(Context context, File apk) {
            try {
                PackageManager manager = context.getPackageManager();
                PackageInfo installed = manager.getPackageInfo(
                        BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo archive = manager.getPackageArchiveInfo(
                        apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (archive == null || !BuildConfig.APPLICATION_ID.equals(archive.packageName)
                        || installed.signingInfo == null || archive.signingInfo == null) return false;
                Signature[] originalSigners = installed.signingInfo.getApkContentsSigners();
                Signature[] archiveSigners = archive.signingInfo.getApkContentsSigners();
                if (originalSigners == null || archiveSigners == null
                        || originalSigners.length == 0
                        || originalSigners.length != archiveSigners.length) return false;
                java.util.HashSet<Signature> expected = new java.util.HashSet<>(
                        java.util.Arrays.asList(originalSigners));
                return expected.equals(new java.util.HashSet<>(
                        java.util.Arrays.asList(archiveSigners)));
            } catch (Exception error) {
                Log.e(TAG, "Unable to validate update APK signing identity", error);
                return false;
            }
        }

        private static void notifyInstall(Context context, long id, File file, boolean nightly) {
            // Save a durable install entry BEFORE attempting the notification.
            // POST_NOTIFICATIONS can be denied or the notification channel blocked.
            JSONObject ready = new JSONObject();
            try {
                ready.put("path", file.getCanonicalPath());
                ready.put("nightly", nightly);
            } catch (Exception badPath) {
                throw new IllegalStateException("Cannot preserve downloaded update", badPath);
            }
            if (!jobs(context).edit().putString(READY_PREFIX + id, ready.toString()).commit()) {
                throw new IllegalStateException("Cannot persist ready-to-install update");
            }
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
                    context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Notification permission denied; update remains available in Update screen");
                return;
            }
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
