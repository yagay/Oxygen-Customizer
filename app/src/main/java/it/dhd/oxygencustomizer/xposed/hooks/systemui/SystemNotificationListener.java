package it.dhd.oxygencustomizer.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;

public class SystemNotificationListener extends XposedMods {

    private final static String listenPackage = SYSTEM_UI;

    @SuppressLint("StaticFieldLeak")
    private static SystemNotificationListener instance = null;

    private final CopyOnWriteArrayList<NotificationCallback> mNotificationCallbacks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DeviceUnlockListener> mDeviceUnlockListeners = new CopyOnWriteArrayList<>();
    public Object mNotificationListener = null;

    public SystemNotificationListener(Context context) {
        super(context);
        instance = this;
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        ReflectedClass NotificationListener = ReflectedClass.of("com.android.systemui.statusbar.NotificationListener");

        NotificationListener
                .afterConstruction()
                .run(param -> mNotificationListener = param.thisObject);

        NotificationListener
                .before("onNotificationPosted")
                .run(param -> {
                    StatusBarNotification notification = (StatusBarNotification) param.args[0];
                    NotificationListenerService.RankingMap rankingMap = (NotificationListenerService.RankingMap) param.args[1];
                    onNotificationPosted(notification, rankingMap);
                });

        NotificationListener
                .before("onNotificationRemoved")
                .run(param -> {
                    StatusBarNotification notification = (StatusBarNotification) param.args[0];
                    NotificationListenerService.RankingMap rankingMap = (NotificationListenerService.RankingMap) param.args[1];
                    if (param.args.length == 3) {
                        int reason = (int) param.args[2];
                        onNotificationRemoved(notification, rankingMap, reason);
                    } else {
                        onNotificationRemoved(notification, rankingMap);
                    }
                });

        NotificationListener
                .before("onNotificationRankingUpdate")
                .run(param -> {
                    NotificationListenerService.RankingMap rankingMap = (NotificationListenerService.RankingMap) param.args[0];
                    onNotificationRankingUpdate(rankingMap);
                });


        Class<?> KeyguardUpdateMonitor = findClass("com.android.keyguard.KeyguardUpdateMonitor", lpparam.classLoader);
        hookAllMethods(KeyguardUpdateMonitor, "getUserCanSkipBouncer", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                boolean canSkipBouncer = (boolean) param.getResult();
                onDeviceUnlock(canSkipBouncer);
            }
        });

    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }

    public interface NotificationCallback {
        void onNotificationPosted(StatusBarNotification notification, NotificationListenerService.RankingMap rankingMap);
        void onNotificationRemoved(StatusBarNotification notification, NotificationListenerService.RankingMap rankingMap, int reason);
        void onNotificationRemoved(StatusBarNotification notification, NotificationListenerService.RankingMap rankingMap);
        void onNotificationRankingUpdate(NotificationListenerService.RankingMap rankingMap);
    }

    public interface DeviceUnlockListener {
        void onDeviceUnlock(boolean unlocked);
    }

    public static void addNotificationCallback(NotificationCallback callback) {
        instance.mNotificationCallbacks.addIfAbsent(callback);
    }

    public static void removeNotificationCallback(NotificationCallback callback) {
        instance.mNotificationCallbacks.remove(callback);
    }

    public static void addDeviceUnlockListener(DeviceUnlockListener listener) {
        instance.mDeviceUnlockListeners.addIfAbsent(listener);
    }

    public static void removeDeviceUnlockListener(DeviceUnlockListener listener) {
        instance.mDeviceUnlockListeners.remove(listener);
    }

    public static Object getNotificationListenerExternal() {
        return instance.mNotificationListener;
    }

    private void onNotificationPosted(StatusBarNotification notification, NotificationListenerService.RankingMap rankingMap) {
        for (NotificationCallback callback : mNotificationCallbacks) {
            try {
                callback.onNotificationPosted(notification, rankingMap);
            } catch (Throwable ignored) {
            }
        }
    }

    private void onNotificationRemoved(StatusBarNotification notification, NotificationListenerService.RankingMap rankingMap, int reason) {
        for (NotificationCallback callback : mNotificationCallbacks) {
            try {
                callback.onNotificationRemoved(notification, rankingMap, reason);
            } catch (Throwable ignored) {
            }
        }
    }

    private void onNotificationRemoved(StatusBarNotification notification, NotificationListenerService.RankingMap rankingMap) {
        for (NotificationCallback callback : mNotificationCallbacks) {
            try {
                callback.onNotificationRemoved(notification, rankingMap);
            } catch (Throwable ignored) {
            }
        }
    }

    private void onNotificationRankingUpdate(NotificationListenerService.RankingMap rankingMap) {
        for (NotificationCallback callback : mNotificationCallbacks) {
            try {
                callback.onNotificationRankingUpdate(rankingMap);
            } catch (Throwable ignored) {
            }
        }
    }

    private void onDeviceUnlock(boolean unlocked) {
        for (DeviceUnlockListener listener : mDeviceUnlockListeners) {
            try {
                listener.onDeviceUnlock(unlocked);
            } catch (Throwable ignored) {
            }
        }
    }

}
