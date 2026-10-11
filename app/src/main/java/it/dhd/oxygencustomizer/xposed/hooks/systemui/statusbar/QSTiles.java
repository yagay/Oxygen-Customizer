package it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar;

import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.findFieldIfExists;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_COLUMNS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_COLUMNS_LANDSCAPE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_CUSTOMIZE_TILES;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_QUICK_TILES;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_ROWS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_TILES_PREFS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_NEW_GRID_ENABLED;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_NEW_GRID_ROWS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_NEW_GRID_COLUMNS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_NEW_GRID_LANDSCAPE_COLUMNS;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import it.dhd.oxygencustomizer.BuildConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.utils.SystemUtils;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;

public class QSTiles extends XposedMods {

    public static final String listenPackage = Constants.Packages.SYSTEM_UI;

    private static final int NOT_SET = 0;
    private static final int QS_COL_NOT_SET = 4;
    private static final int QS_ROW_NOT_SET = 3;
    private static final int QQS_NOT_SET = 5;

    private static int QSRowQty = NOT_SET;
    private static int QSColQty = QS_COL_NOT_SET;
    private static int QQSTileQty = QQS_NOT_SET;

    private static int QSColQtyL = QS_COL_NOT_SET;

    private boolean mCustomizeQSTiles = false;
    private boolean mCustomizeNewGrid = false;
    private int newGridRows = 3;
    private int newGridColumns = 4;
    private int newGridLandscapeColumns = 5;
    private final AtomicBoolean aospGridSeen = new AtomicBoolean(false);
    private final AtomicBoolean oplusGridSeen = new AtomicBoolean(false);
    private final AtomicBoolean calculatorSeen = new AtomicBoolean(false);
    private final AtomicBoolean newGridInstalled = new AtomicBoolean(false);
    private final AtomicBoolean newGridUsed = new AtomicBoolean(false);
    private final AtomicBoolean spanManagerSeen = new AtomicBoolean(false);
    private final AtomicBoolean pagerManagerSeen = new AtomicBoolean(false);
    private static final String NEW_GRID_CLASS = "com.oplus.systemui.plugins.qs.customize.data.model.GridInfoEntity";

    public QSTiles(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mCustomizeNewGrid = Xprefs.getBoolean(QS_NEW_GRID_ENABLED, false);
        // Never let legacy QS hooks compete with the OOS16 separated grid.
        mCustomizeQSTiles = !mCustomizeNewGrid
                && Xprefs.getBoolean(QS_CUSTOMIZE_TILES, false);
        newGridRows = Xprefs.getInt(QS_NEW_GRID_ROWS, 3);
        newGridColumns = Xprefs.getInt(QS_NEW_GRID_COLUMNS, 4);
        newGridLandscapeColumns = Xprefs.getInt(QS_NEW_GRID_LANDSCAPE_COLUMNS, 5);
        QQSTileQty = Xprefs.getInt(QS_QUICK_TILES, QQS_NOT_SET);
        QSRowQty = Xprefs.getInt(QS_ROWS, QS_ROW_NOT_SET);
        QSColQty = Xprefs.getInt(QS_COLUMNS, QS_COL_NOT_SET);
        QSColQtyL = Xprefs.getInt(QS_COLUMNS_LANDSCAPE, QS_COL_NOT_SET);

        if (Key.length > 0) {
            for (String qsTilePref : QS_TILES_PREFS) {
                if (Key[0].equals(qsTilePref)) {
                    SystemUtils.doubleToggleDarkMode();
                }
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!listenPackage.equals(lpparam.packageName)) return;

        XposedBridge.log("[OC-QSGrid] init: legacyEnabled=" + mCustomizeQSTiles + " modernEnabled=" + mCustomizeNewGrid
                + " rows=" + QSRowQty + " portraitColumns=" + QSColQty
                + " landscapeColumns=" + QSColQtyL + " sdk=" + Build.VERSION.SDK_INT);
        ReflectedClass QuickQSPanel = ReflectedClass.ofIfPossible("com.android.systemui.qs.QuickQSPanel");
        QuickQSPanel
                .before("getNumQuickTiles")
                .run(param -> {
                    if (mCustomizeQSTiles) {
                        param.setResult(QQSTileQty);
                    }
                });

        ReflectedClass TileLayout = ReflectedClass.ofIfPossible("com.android.systemui.qs.TileLayout");
        TileLayout
                .before("updateMaxRows")
                .run(param -> {
                    if (!mCustomizeQSTiles ||
                            mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        return;

                    if (aospGridSeen.compareAndSet(false, true)) XposedBridge.log("[OC-QSGrid] AOSP TileLayout active");
                    int mRows = getIntField(param.thisObject, "mRows");
                    setIntField(param.thisObject, "mRows", QSRowQty);
                    param.setResult(mRows != QSRowQty);
                });


        TileLayout
                .before("updateColumns")
                .run(param -> {
                    if (!mCustomizeQSTiles) return;

                    int mColumns = getIntField(param.thisObject, "mColumns");
                    int orientation = mContext.getResources().getConfiguration().orientation;
                    int newColumns = orientation == Configuration.ORIENTATION_PORTRAIT ? QSColQty : QSColQtyL;
                    setIntField(param.thisObject, "mColumns", newColumns);
                    param.setResult(mColumns != newColumns);

                });

        if (Build.VERSION.SDK_INT >= 35) {
            // New separated QS grid has its own preferences, independent of the legacy grid.
            // These methods are present in the supplied OOS16 SystemUI APK. They may be
            // loaded by a plugin ClassLoader; the compatibility diagnostic reports misses.
            installNewGridHookWhenLoaded(lpparam.classLoader);
            installNewLayoutHooks();

            // OPlus uses its own container when Quick Settings is separated from notifications.
            // The AOSP TileLayout hooks above do not affect this layout.
            ReflectedClass oplusContainer = ReflectedClass.ofIfPossible(
                    "com.oplus.systemui.plugins.qs.tile.OplusTileContainerView");
            oplusContainer.before("updateCell").run(param -> {
                if (!mCustomizeQSTiles) return;
                if (oplusGridSeen.compareAndSet(false, true)) XposedBridge.log("[OC-QSGrid] OPlus updateCell active: " + param.thisObject.getClass().getName());
                applyOplusGridSize(param.thisObject);
            });
            oplusContainer.after("updateCell").run(param -> {
                if (!mCustomizeQSTiles) return;
                applyOplusGridSize(param.thisObject);
            });

            ReflectedClass calculator = ReflectedClass.ofIfPossible(
                    "com.oplus.systemui.plugins.qs.DeviceProfile$DefaultCellCalculator");
            calculator.before("getMaxItemNum").run(param -> {
                if (!mCustomizeQSTiles) return;
                if (calculatorSeen.compareAndSet(false, true)) XposedBridge.log("[OC-QSGrid] OPlus getMaxItemNum active");
                int columns = mContext.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE ? QSColQtyL : QSColQty;
                param.setResult(Math.max(1, QSRowQty) * Math.max(1, columns));
            });
        }

    }

    /**
     * In OOS16 the separated QS implementation may be loaded by a plugin
     * ClassLoader instead of the SystemUI application ClassLoader. Resolve the
     * actual class at load time instead of assuming it is available at startup.
     */
    private void installNewGridHookWhenLoaded(ClassLoader systemUiLoader) {
        tryInstallNewGridClass(findClassIfExists(NEW_GRID_CLASS, systemUiLoader));
        tryInstallNewGridClass(findClassIfExists(NEW_GRID_CLASS, mContext.getClassLoader()));
        if (newGridInstalled.get()) return;
        XposedBridge.log("[OC-QSGrid] OOS16 grid not in startup loader, awaiting plugin ClassLoader");
        XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (newGridInstalled.get() || param.hasThrowable()
                        || param.args.length == 0
                        || !(param.args[0] instanceof String)
                        || !NEW_GRID_CLASS.equals(param.args[0])) return;
                Object loaded = param.getResult();
                if (loaded instanceof Class<?>) {
                    tryInstallNewGridClass((Class<?>) loaded);
                }
            }
        });
    }

    private void tryInstallNewGridClass(Class<?> target) {
        if (target == null || !newGridInstalled.compareAndSet(false, true)) return;
        try {
            XposedBridge.log("[OC-QSGrid] OOS16 grid found: " + target.getName()
                    + " loader=" + target.getClassLoader());
            ReflectedClass grid = ReflectedClass.of(target);
            grid.before("getGridCol").run(param -> {
                if (!mCustomizeNewGrid) return;
                int columns = mContext.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE
                        ? newGridLandscapeColumns : newGridColumns;
                param.setResult(Math.max(1, columns));
                if (newGridUsed.compareAndSet(false, true)) {
                    XposedBridge.log("[OC-QSGrid] OOS16 grid getters active");
                }
            });
            grid.before("getGridRow").run(param -> {
                if (!mCustomizeNewGrid) return;
                if (mContext.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_PORTRAIT) {
                    param.setResult(Math.max(1, newGridRows));
                }
            });
            XposedBridge.log("[OC-QSGrid] OOS16 grid getter hooks installed");
        } catch (Throwable failure) {
            newGridInstalled.set(false);
            XposedBridge.log("[OC-QSGrid] OOS16 grid hook install failed: " + failure);
        }
    }

    /**
     * OOS16 separates persisted GridInfo from the RecyclerView grid layout.
     * Hook the live span calculation as the legacy implementation hooks
     * TileLayout.updateColumns. Keep pager getters diagnostic-only: they are
     * also used by customization/editor pages with their own page capacity.
     */
    private void installNewLayoutHooks() {
        ReflectedClass spanManager = ReflectedClass.ofIfPossible(
                "com.oplus.systemui.qs.base.widget.SpannedGridLayoutManager");
        spanManager.after("getSpanCount").run(param -> {
            if (!mCustomizeNewGrid || param.hasThrowable()) return;
            Object original = param.getResult();
            int columns = mContext.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE
                    ? newGridLandscapeColumns : newGridColumns;
            if (spanManagerSeen.compareAndSet(false, true)) {
                XposedBridge.log("[OC-QSGrid] SpannedGridLayoutManager#getSpanCount "
                        + "original=" + original + " requested=" + columns
                        + " class=" + param.thisObject.getClass().getName());
            }
            if (original instanceof Integer) {
                param.setResult(Math.max(1, Math.min(8, columns)));
            }
        });

        ReflectedClass pagerManager = ReflectedClass.ofIfPossible(
                "com.oplus.systemui.qs.base.customizer.OplusPagerGridLayoutManager");
        pagerManager.after("getColumns").run(param -> {
            if (!mCustomizeNewGrid || param.hasThrowable()) return;
            if (pagerManagerSeen.compareAndSet(false, true)) {
                XposedBridge.log("[OC-QSGrid] PagerGridLayoutManager active "
                        + "columns=" + param.getResult()
                        + " (editor/pager dimensions left unchanged)");
            }
        });
        pagerManager.after("getRows").run(param -> {
            if (!mCustomizeNewGrid || param.hasThrowable()) return;
            if (pagerManagerSeen.compareAndSet(false, true)) {
                XposedBridge.log("[OC-QSGrid] PagerGridLayoutManager active "
                        + "rows=" + param.getResult()
                        + " (editor/pager dimensions left unchanged)");
            }
        });
    }

    private void applyOplusGridSize(Object container) {
        int orientation = mContext.getResources().getConfiguration().orientation;
        int columns = orientation == Configuration.ORIENTATION_LANDSCAPE ? QSColQtyL : QSColQty;
        setGridFieldIfPresent(container, "mColumns", Math.max(1, columns));
        if (orientation != Configuration.ORIENTATION_LANDSCAPE) {
            setGridFieldIfPresent(container, "mRows", Math.max(1, QSRowQty));
        }
    }

    private static void setGridFieldIfPresent(Object target, String fieldName, int value) {
        if (findFieldIfExists(target.getClass(), fieldName) != null) {
            setIntField(target, fieldName, value);
        } else if (BuildConfig.DEBUG) {
            XposedBridge.log("[OC-QSGrid] missing field " + target.getClass().getName() + "." + fieldName);
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
