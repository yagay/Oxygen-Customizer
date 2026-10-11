package it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar;

import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.findFieldIfExists;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_COLUMNS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_COLUMNS_LANDSCAPE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_CUSTOMIZE_TILES;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_QUICK_TILES;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_ROWS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsTiles.QS_TILES_PREFS;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import it.dhd.oxygencustomizer.BuildConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XposedBridge;

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
    private final AtomicBoolean aospGridSeen = new AtomicBoolean(false);
    private final AtomicBoolean oplusGridSeen = new AtomicBoolean(false);
    private final AtomicBoolean calculatorSeen = new AtomicBoolean(false);

    public QSTiles(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mCustomizeQSTiles = Xprefs.getBoolean(QS_CUSTOMIZE_TILES, false);
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

        XposedBridge.log("[OC-QSGrid] init: enabled=" + mCustomizeQSTiles
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
