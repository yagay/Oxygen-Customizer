package it.dhd.oxygencustomizer.xposed.utils;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
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

public class CalendarProvider extends ContentProvider {

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".calendarprovider";
    public static final Uri CONTENT_URI_EVENTS = Uri.parse("content://" + AUTHORITY + "/events");

    private static final Set<String> ALLOWED_CALLERS = new HashSet<>(Arrays.asList(
            BuildConfig.APPLICATION_ID,
            "android",
            "com.android.systemui"
    ));

    @Override
    public boolean onCreate() {
        return true;
    }

    private void enforceAllowedCaller() {
        final int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return;
        }

        final String[] packages = getContext() != null
                ? getContext().getPackageManager().getPackagesForUid(callingUid)
                : null;
        if (packages != null) {
            for (String packageName : packages) {
                if (ALLOWED_CALLERS.contains(packageName)) {
                    return;
                }
            }
        }

        throw new SecurityException("Caller is not allowed to access Oxygen Customizer calendar data");
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        enforceAllowedCaller();

        if (getContext() == null ||
                getContext().checkSelfPermission(Manifest.permission.READ_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission READ_CALENDAR not granted");
        }

        ContentResolver resolver = getContext().getContentResolver();
        return resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        );
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        enforceAllowedCaller();
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".events";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        enforceAllowedCaller();
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        enforceAllowedCaller();
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        enforceAllowedCaller();
        return 0;
    }
}
