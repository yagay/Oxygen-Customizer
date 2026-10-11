package it.dhd.oxygencustomizer.xposed.hooks.framework;

import static android.content.Context.RECEIVER_EXPORTED;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.FRAMEWORK;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XPrefs;
import it.dhd.oxygencustomizer.xposed.XposedMods;

/** Keep system_server's high-frequency window hook free of shell I/O. */
public class OplusStartingWindowManager extends XposedMods {
    private static final String LISTEN_PACKAGE = FRAMEWORK;

    private volatile boolean mEnableLagFix;
    private volatile boolean mForceAllApps;
    private volatile Set<String> mLagFixApps = Collections.emptySet();
    private boolean broadcastRegistered;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Constants.ACTION_SETTINGS_CHANGED.equals(intent.getAction())
                    || !XPrefs.isTrustedInternalBroadcast(intent)) return;
            if (!OplusStartingWindowManager.class.getSimpleName().equals(
                    intent.getStringExtra("class"))) return;
            try {
                updatePrefs();
            } catch (Throwable failure) {
                log(failure);
            }
        }
    };

    public OplusStartingWindowManager(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... keys) {
        try {
            Set<String> configured = Xprefs.getStringSet("lag_fix_apps", null);
            Set<String> safe = new HashSet<>();
            if (configured != null) {
                for (String name : configured) {
                    if (!TextUtils.isEmpty(name)) safe.add(name);
                }
            }
            mLagFixApps = Collections.unmodifiableSet(safe);
            mForceAllApps = Xprefs.getBoolean("fix_lag_force_all_apps", false);
            mEnableLagFix = Xprefs.getBoolean("fix_lag_switch", false);
            // No Root IPC or shell logging: this Hook runs in system_server.
            log("Lag Fix preferences refreshed: enabled=" + mEnableLagFix
                    + ", allApps=" + mForceAllApps + ", selectedApps=" + safe.size());
        } catch (Throwable failure) {
            log(failure);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (Build.VERSION.SDK_INT < 34) return;
        if (!broadcastRegistered) {
            IntentFilter filter = new IntentFilter(Constants.ACTION_SETTINGS_CHANGED);
            mContext.registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED);
            broadcastRegistered = true;
        }

        try {
            Class<?> clazz = findClass(
                    "com.android.server.wm.OplusStartingWindowManager", lpparam.classLoader);
            Method method = clazz.getDeclaredMethod("isLayerMatchToStartingWindow", String.class);
            hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!mEnableLagFix) return;
                    if (mForceAllApps) {
                        param.setResult(true);
                        return;
                    }
                    if (param.args.length == 0 || !(param.args[0] instanceof String layer)
                            || TextUtils.isEmpty(layer)) return;
                    for (String packageName : mLagFixApps) {
                        if (layer.contains(packageName)) {
                            param.setResult(true);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable failure) {
            log(failure);
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return LISTEN_PACKAGE.equals(packageName);
    }
}
