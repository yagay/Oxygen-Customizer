package it.dhd.oxygencustomizer.xposed.utils;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.provider.CalendarContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.R;

public class CalendarProvider extends ContentProvider {

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".calendarprovider";
    public static final Uri CONTENT_URI_EVENTS = Uri.parse("content://" + AUTHORITY + "/events");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final Context context = getContext();
        if (context == null) {
            throw new SecurityException("Provider context unavailable");
        }
        if (!isTrustedCaller(context, Binder.getCallingUid())) {
            throw new SecurityException("Caller is not allowed to query calendar data");
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission READ_CALENDAR not granted");
        }

        ContentResolver resolver = context.getContentResolver();
        return resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        );
    }

    private boolean isTrustedCaller(Context context, int callingUid) {
        if (callingUid == Process.myUid()) {
            return true;
        }
        String[] callerPackages = context.getPackageManager().getPackagesForUid(callingUid);
        if (callerPackages == null || callerPackages.length == 0) {
            return false;
        }
        Set<String> allowedReaders = new HashSet<>(
                Arrays.asList(context.getResources().getStringArray(R.array.xposed_scope))
        );
        for (String packageName : callerPackages) {
            if (allowedReaders.contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".events";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
