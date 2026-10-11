package it.dhd.oxygencustomizer.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;

public class FluidMusic extends XposedMods {

    private static final String listenPackage = SYSTEM_UI;

    private boolean mFluidCustomEnabled = false;
    private volatile List<String> mFluidAppsList = new ArrayList<>();

    public FluidMusic(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        mFluidCustomEnabled = Xprefs.getBoolean("fluid_music_custom_switch", false);
        Set<String> mFluidApps = Xprefs.getStringSet("fluid_music_apps", new HashSet<>());
        mFluidAppsList = new ArrayList<>(mFluidApps);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> OplusMediaRusUpdateManager = findClassIfExists("com.oplus.systemui.media.seedling.rus.OplusMediaRusUpdateManager", lpparam.classLoader);

        if (OplusMediaRusUpdateManager != null) {
            hookAllMethods(OplusMediaRusUpdateManager, "getRusWhiteList", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mFluidCustomEnabled) return;

                    List<String> snapshot = new ArrayList<>(mFluidAppsList);
                    List<String> whiteList = (List<String>) getObjectField(param.thisObject, "whiteList");
                    if (whiteList != null) {
                        whiteList.clear();
                        whiteList.addAll(snapshot);
                    }
                    param.setResult(snapshot);
                }
            });
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
