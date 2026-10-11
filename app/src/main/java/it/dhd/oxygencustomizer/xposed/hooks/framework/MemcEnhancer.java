package it.dhd.oxygencustomizer.xposed.hooks.framework;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static it.dhd.oxygencustomizer.utils.Constants.ACTIONS_MEMC_FEATURE_GET;
import static it.dhd.oxygencustomizer.utils.Constants.ACTIONS_MEMC_FEATURE_RECEIVED;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.FRAMEWORK;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;
import static it.dhd.oxygencustomizer.xposed.utils.ReflectionTools.findClassInArray;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XPrefs;
import it.dhd.oxygencustomizer.xposed.XposedMods;

public class MemcEnhancer extends XposedMods {

    private static final Set<String> OPLUS_MEMC_FEATURES = new HashSet<>(Arrays.asList(
            "oplus.software.display.pixelworks_enable",
            "oplus.software.display.iris_enable",
            "oplus.software.display.memc_enable",
            "oplus.software.display.game.memc_enable"
    ));

    private static final String listenPackage = FRAMEWORK;
    private static final ArrayList<String> sConfigPackage = new ArrayList<>();
    private static final ArrayList<String> sConfigActivity = new ArrayList<>();
    private static final HashMap<String, String> sSdr2hdrCommandMap = new HashMap<>();
    private static final HashMap<String, String> sMemcCommandMap = new HashMap<>();
    private static final HashMap<String, String> sAppScreenRateMap = new HashMap<>();
    /** Snapshot retained by Hooks so preference refresh never mutates a live result. */
    private static final class ConfigSnapshot {
        final boolean force;
        final boolean custom;
        final ArrayList<String> packages;
        final ArrayList<String> activities;
        final HashMap<String, String> sdr2hdr;
        final HashMap<String, String> memc;
        final HashMap<String, String> rates;

        ConfigSnapshot(boolean force, boolean custom, ArrayList<String> packages,
                       ArrayList<String> activities, HashMap<String, String> sdr2hdr,
                       HashMap<String, String> memc, HashMap<String, String> rates) {
            this.force = force;
            this.custom = custom;
            this.packages = new ArrayList<>(packages);
            this.activities = new ArrayList<>(activities);
            this.sdr2hdr = new HashMap<>(sdr2hdr);
            this.memc = new HashMap<>(memc);
            this.rates = new HashMap<>(rates);
        }
    }

    private volatile ConfigSnapshot configSnapshot = new ConfigSnapshot(
            false, false, new ArrayList<>(), new ArrayList<>(),
            new HashMap<>(), new HashMap<>(), new HashMap<>());
    private boolean mBroadcastRegistered = false;
    private boolean mSettingsBroadcastRegistered = false;
    private volatile boolean settingsUpdated = false;
    private boolean mIsFeatureOn = false;
    private boolean mIsPwX7Enable = false;
    private boolean mMemcEnable = false;
    private boolean mSdr2hdrEnable = false;
    private boolean mVideoOsieSupport = false;
    private Object mOplusFeatureMEMC = null;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            if (intent == null || !ACTIONS_MEMC_FEATURE_GET.equals(intent.getAction())
                    || !XPrefs.isTrustedInternalBroadcast(intent)) return;
            getAndSendBroadcast();
        }
    };
    private boolean enableMemcFeature = false;
    private boolean useCustomMemcConfig = false;
    final BroadcastReceiver mSettingsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!XPrefs.isTrustedInternalBroadcast(intent)) return;
            try {
                String action = intent.getAction();
                if (action == null) return;
                String className = intent.getStringExtra("class");
                if (action.equals(Constants.ACTION_SETTINGS_CHANGED)) {
                    if (!TextUtils.isEmpty(className) && className.equals(MemcEnhancer.class.getSimpleName())) {
                        log("Intent received - will update preferences");
                        settingsUpdated = false;
                        updatePrefs();
                    }
                }
            } catch (Throwable t) {
                log("error: " + t.getMessage());
            }
        }
    };

    public MemcEnhancer(Context context) {
        super(context);
    }

    @Override
    public synchronized void updatePrefs(String... Key) {
        if (settingsUpdated) return;

        enableMemcFeature = Xprefs.getBoolean("force_memc_enabled", false);
        useCustomMemcConfig = Xprefs.getBoolean("custom_memc_config", false);

        clearLists();

        Set<String> memcConfig = Xprefs.getStringSet("custom_memc_applications", new HashSet<>());
        for (String config : memcConfig) {
            if (config == null) continue;
            String[] columns = config.split("\\|", 2);
            if (columns.length != 2 || columns[0].isBlank()) continue;
            String[] values = columns[1].split("\\$", 2);
            if (values.length != 2 || values[0].isBlank() || values[1].isBlank()) continue;
            try {
                if (Integer.parseInt(values[0]) <= 0) continue;
            } catch (NumberFormatException ignored) {
                continue;
            }
            sConfigPackage.add(columns[0]);
            sAppScreenRateMap.put(columns[0], values[0]);
            sSdr2hdrCommandMap.put(columns[0], values[1]);
        }

        Set<String> memcActivityConfig = Xprefs.getStringSet("custom_memc_activities", new HashSet<>());
        for (String config : memcActivityConfig) {
            if (config == null) continue;
            String[] columns = config.split("\\|", 2);
            if (columns.length != 2 || columns[1].isBlank()) continue;
            int slash = columns[0].indexOf('/');
            if (slash <= 0 || slash >= columns[0].length() - 1) continue;
            sConfigActivity.add(columns[0].substring(slash + 1));
            sMemcCommandMap.put(columns[0], columns[1]);
        }

        logLists();

        configSnapshot = new ConfigSnapshot(
                enableMemcFeature, useCustomMemcConfig, sConfigPackage, sConfigActivity,
                sSdr2hdrCommandMap, sMemcCommandMap, sAppScreenRateMap);
        settingsUpdated = true;
    }

    private void clearLists() {
        sConfigPackage.clear();
        sConfigActivity.clear();
        sSdr2hdrCommandMap.clear();
        sMemcCommandMap.clear();
        sAppScreenRateMap.clear();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!mBroadcastRegistered) {
            mBroadcastRegistered = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTIONS_MEMC_FEATURE_GET);
            mContext.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
        }
        if (!mSettingsBroadcastRegistered) {
            mSettingsBroadcastRegistered = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Constants.ACTION_SETTINGS_CHANGED);
            mContext.registerReceiver(mSettingsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        hookFeatureManager(lpparam);
        hookSystemProperties(lpparam);
        hookOplusFeatureMEMC(lpparam);
        hookOplusMemcHelper(lpparam);

    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }

    private void getAndSendBroadcast() {
        if (mOplusFeatureMEMC != null) {
            mIsFeatureOn = getBooleanField(mOplusFeatureMEMC, "mIsFeatureOn");
            mIsPwX7Enable = getBooleanField(mOplusFeatureMEMC, "mIsPwX7Enable");
            mMemcEnable = getBooleanField(mOplusFeatureMEMC, "mMemcEnable");
            mSdr2hdrEnable = getBooleanField(mOplusFeatureMEMC, "mSdr2hdrEnable");
            mVideoOsieSupport = getBooleanField(mOplusFeatureMEMC, "mVideoOsieSupport");
        }
        Intent intent = new Intent(ACTIONS_MEMC_FEATURE_RECEIVED)
                .setPackage(BuildConfig.APPLICATION_ID);
        intent.putExtra("feature_on", mIsFeatureOn);
        intent.putExtra("pw_x7_enable", mIsPwX7Enable);
        intent.putExtra("memc_enable", mMemcEnable);
        intent.putExtra("sdr2hdr_enable", mSdr2hdrEnable);
        intent.putExtra("video_osie_support", mVideoOsieSupport);
        XPrefs.addInternalBroadcastToken(intent);
        mContext.sendBroadcast(intent);
    }

    private void hookFeatureManager(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> OplusFeatureConfigManagerService = findClassIfExists("com.android.server.content.OplusFeatureConfigManagerService", lpparam.classLoader);
        Class<?> OplusFeatureConfigManager = findClassIfExists("com.oplus.content.OplusFeatureConfigManager", lpparam.classLoader);

        if (OplusFeatureConfigManagerService == null) {
            log(new Throwable("OplusFeatureManager not found"));
        }
        if (OplusFeatureConfigManager == null) {
            log(new Throwable("OplusFeatureManager not found"));
        }

        XC_MethodHook featureHooker = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                /*
                        oplus.software.display.pixelworks_enable
                        oplus.software.display.iris_enable
                        oplus.software.display.memc_enable
                        oplus.software.display.game.memc_enable
                    */
                String requestedFeature = (String) param.args[0];
                if (OPLUS_MEMC_FEATURES.contains(requestedFeature) && configSnapshot.force) {
                    param.setResult(true);
                }
            }
        };

        if (OplusFeatureConfigManager != null) {
            hookAllMethods(OplusFeatureConfigManager, "hasFeature", featureHooker);
        }
        if (OplusFeatureConfigManagerService != null) {
            hookAllMethods(OplusFeatureConfigManagerService, "hasFeature", featureHooker);
        }
    }

    private void hookSystemProperties(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> SystemProperties;
        SystemProperties = findClassIfExists("android.os.SystemProperties", lpparam.classLoader);

        if (SystemProperties == null) {
            log("SystemProperties not found");
            return;
        }

        hookAllMethods(SystemProperties, "getBoolean", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.force) return;
                if (param.args[0].equals("ro.oplus.display.memc_video_refreshrate") ||
                        param.args[0].equals("vendor.display.show_memc_tomast")) {
                    log("get: " + param.args[0] + " called, returning true");
                    param.setResult(true);
                }
            }
        });
    }

    private void hookOplusFeatureMEMC(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> OplusFeatureMEMC;
        OplusFeatureMEMC = findClassIfExists("com.android.server.display.OplusFeatureMEMC", lpparam.classLoader);

        if (OplusFeatureMEMC == null) {
            log("OplusFeatureMEMC not found");
            return;
        }

        hookAllConstructors(OplusFeatureMEMC, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mOplusFeatureMEMC = param.thisObject;
                mIsFeatureOn = getBooleanField(param.thisObject, "mIsFeatureOn");
                mIsPwX7Enable = getBooleanField(param.thisObject, "mIsPwX7Enable");
                mMemcEnable = getBooleanField(param.thisObject, "mMemcEnable");
                mSdr2hdrEnable = getBooleanField(param.thisObject, "mSdr2hdrEnable");
                mVideoOsieSupport = getBooleanField(param.thisObject, "mVideoOsieSupport");
            }
        });

    }

    private void hookOplusMemcHelper(XC_LoadPackage.LoadPackageParam lpparam) {

        Class<?> OplusMemcHelper = findClassInArray(lpparam,
                "com.android.server.display.feature.vrr.memc.OplusMemcHelper", // OOS16
                "com.android.server.display.memc.OplusMemcHelper");
        if (OplusMemcHelper == null) {
            log("OplusMemcHelper not found on this firmware");
            return;
        }

        hookAllMethods(OplusMemcHelper, "getConfigAppList", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.custom) return;
                log("getConfigAppList called");
                param.setResult(new ArrayList<>(configSnapshot.packages));
            }
        });

        hookAllMethods(OplusMemcHelper, "getConfigActivityList", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.custom) return;
                log("getConfigActivityList called");
                param.setResult(new ArrayList<>(configSnapshot.activities));
            }
        });

        hookAllMethods(OplusMemcHelper, "getSdr2hdrCommandMap", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.custom) return;
                log("getSdr2hdrCommandMap called");
                param.setResult(new HashMap<>(configSnapshot.sdr2hdr));
            }
        });

        hookAllMethods(OplusMemcHelper, "getMemcCommandMap", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.custom) return;
                log("getMemcCommandMap called");
                param.setResult(new HashMap<>(configSnapshot.memc));
            }
        });

        hookAllMethods(OplusMemcHelper, "getAppScreenRateMap", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.custom) return;
                log("getAppScreenRateMap called");
                param.setResult(new HashMap<>(configSnapshot.rates));
            }
        });

        /*
        public boolean isInConfigpackageList(String packageName) {
            Iterator<String> it = sConfigPackage.iterator();
            while (it.hasNext()) {
                String name = it.next();
                if (packageName.equals(name)) {
                    return true;
                }
            }
            return false;
        }
         */

        hookAllMethods(OplusMemcHelper, "isInConfigpackageList", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!configSnapshot.custom) return;
                String packageName = (String) param.args[0];
                log("isInConfigpackageList called with: " + packageName + " returning: " + configSnapshot.packages.contains(packageName));
                param.setResult(configSnapshot.packages.contains(packageName));
            }
        });
    }

    private void logLists() {
        if (!mDebug) return;
        log("logLists: " + "\n" +
                "enableMemcFeature: " + enableMemcFeature + "\n" +
                "useCustomMemcConfig: " + useCustomMemcConfig + "\n" +
                "sConfigPackage: " + Arrays.toString(sConfigPackage.toArray()) + "\n" +
                "sConfigActivity: " + Arrays.toString(sConfigActivity.toArray()) + "\n" +
                "sSdr2hdrCommandMap: " + sSdr2hdrCommandMap.toString() + "\n" +
                "sMemcCommandMap: " + sMemcCommandMap.toString() + "\n" +
                "sAppScreenRateMap: " + sAppScreenRateMap.toString());
    }

}
