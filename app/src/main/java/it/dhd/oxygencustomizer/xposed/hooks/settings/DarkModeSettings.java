package it.dhd.oxygencustomizer.xposed.hooks.settings;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SETTINGS;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.utils.PackageModeParser;

public class DarkModeSettings extends XposedMods {

    private static final String listenPackage = SETTINGS;

    private boolean enableCustomDarkMode = false;
    private Set<String> darkModeApps = new HashSet<>();

    public DarkModeSettings(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        enableCustomDarkMode = Xprefs.getBoolean("custom_dark_mode_switch", false);
        darkModeApps = Xprefs.getStringSet("custom_dark_mode", new HashSet<>());
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> DarkModeFileUtils = findClassIfExists("com.oplus.settings.feature.display.darkmode.utils.DarkModeFileUtils", lpparam.classLoader);

        final Class<?> AppEntity = findClassIfExists("com.oplus.settings.feature.display.darkmode.utils.DarkModeFileUtils$AppEntity", lpparam.classLoader);

        if (DarkModeFileUtils != null && AppEntity != null) {
            hookAllMethods(DarkModeFileUtils, "parseManagedList", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableCustomDarkMode) return;

                    Map<String, Object> mEnabledApps = new ArrayMap<>();
                    for (Map.Entry<String, Integer> app : PackageModeParser.parse(darkModeApps, 4).entrySet()) {
                        final Object entity = AppEntity.getConstructor().newInstance();
                        if (app.getValue() != 0) {
                            setObjectField(entity, "curType", app.getValue());
                        }
                        mEnabledApps.put(app.getKey(), entity);
                    }
                    setObjectField(param.thisObject, "mAppsMap", mEnabledApps);
                    param.setResult(null);
                }
            });
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
