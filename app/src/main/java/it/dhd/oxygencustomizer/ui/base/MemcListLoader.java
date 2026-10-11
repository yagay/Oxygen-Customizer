package it.dhd.oxygencustomizer.ui.base;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import it.dhd.oxygencustomizer.ui.models.MemcAppModel;

/** Shared, view-lifecycle-aware MEMC application/activity loader. */
public final class MemcListLoader {
    private static final String TAG = "MemcListLoader";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
            1, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            runnable -> new Thread(runnable, "OC-MemcListLoader"));
    private static final AtomicLong NEXT_REQUEST = new AtomicLong();
    private static final Map<LifecycleOwner, Long> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    static {
        WORKERS.allowCoreThreadTimeOut(true);
    }

    private MemcListLoader() {}

    public static void load(@NonNull LifecycleOwner owner, @NonNull PackageManager manager,
                            @NonNull Map<String, String> configured, boolean activities,
                            Runnable onStart, Consumer<List<MemcAppModel>> onComplete) {
        final Map<String, String> snapshot = new HashMap<>(configured);
        final long request = NEXT_REQUEST.incrementAndGet();
        ACTIVE.put(owner, request);
        if (onStart != null) {
            MAIN.post(() -> {
                if (isCurrent(owner, request)) onStart.run();
            });
        }
        WORKERS.execute(() -> {
            List<MemcAppModel> result = new ArrayList<>();
            try {
                for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                    if (!isCurrent(owner, request)) return;
                    String key = entry.getKey();
                    if (key == null || key.isBlank()) continue;
                    String packageName = key;
                    String activityName = "";
                    if (activities) {
                        int slash = key.indexOf('/');
                        if (slash <= 0 || slash >= key.length() - 1) continue;
                        packageName = key.substring(0, slash);
                        activityName = key.substring(slash + 1);
                    }
                    try {
                        ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
                        String label = manager.getApplicationLabel(info).toString();
                        Drawable icon = manager.getApplicationIcon(info);
                        if (activities) {
                            result.add(new MemcAppModel(label, packageName, activityName, icon,
                                    true, -4, entry.getValue() == null ? "258-40-0-0" : entry.getValue()));
                        } else {
                            String value = entry.getValue() == null ? "" : entry.getValue();
                            int rate = 120;
                            String config = "267-3-1";
                            int dollar = value.indexOf('$');
                            if (dollar > 0 && dollar < value.length() - 1) {
                                try {
                                    rate = Integer.parseInt(value.substring(0, dollar));
                                    if (rate <= 0) rate = 120;
                                } catch (NumberFormatException ignored) {
                                    rate = 120;
                                }
                                config = value.substring(dollar + 1);
                            }
                            result.add(new MemcAppModel(label, packageName, "", icon,
                                    false, rate, config));
                        }
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // A removed app should not invalidate the rest of the list.
                    }
                }
            } catch (RuntimeException failure) {
                Log.e(TAG, "Unable to load MEMC entries", failure);
            }
            MAIN.post(() -> {
                if (isCurrent(owner, request)) {
                    ACTIVE.remove(owner);
                    if (onComplete != null) onComplete.accept(result);
                }
            });
        });
    }

    private static boolean isCurrent(LifecycleOwner owner, long request) {
        Long latest = ACTIVE.get(owner);
        return latest != null && latest == request
                && owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED);
    }
}
