package it.dhd.oxygencustomizer.xposed;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.dhd.oxygencustomizer.BuildConfig.APPLICATION_ID;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;
import static it.dhd.oxygencustomizer.xposed.utils.BootLoopProtector.isBootLooped;
import static it.dhd.oxygencustomizer.xposed.utils.SystemUtils.sleep;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.IRootProviderProxy;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.utils.SystemUtils;

public class XPLauncher implements ServiceConnection {

    public static boolean isChildProcess = false;
    public static String processName = "";

    public static final CopyOnWriteArrayList<XposedMods> runningMods =
            new CopyOnWriteArrayList<>();
    public Context mContext = null;

    private static IRootProviderProxy rootProxyIPC;
    private static final Queue<ProxyRunnable> proxyQueue = new LinkedList<>();
    private static final AtomicBoolean rootConnectWorkerRunning = new AtomicBoolean(false);
    private static final AtomicBoolean bindInProgress = new AtomicBoolean(false);
    private static final int XPREFS_LOAD_RETRIES = 20;
    private static final long XPREFS_LOAD_RETRY_DELAY_MS = 100;
    @SuppressLint("StaticFieldLeak")
    static XPLauncher instance;

    /**
     * @noinspection FieldCanBeLocal
     */
    public XPLauncher() {
        instance = this;
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            isChildProcess = lpparam.processName.contains(":");
            processName = lpparam.processName;
        } catch (Throwable ignored) {
            isChildProcess = false;
        }


        if (lpparam.packageName.equals(Constants.Packages.FRAMEWORK)) {
            log("[ Oxygen Customizer - XPLauncher ] packageName Framework: " + lpparam.packageName);
            Class<?> PhoneWindowManager = findClass("com.android.server.policy.PhoneWindowManager", lpparam.classLoader);
            hookAllMethods(PhoneWindowManager, "init", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    log("[ Oxygen Customizer - XPLauncher ] packageName Framework: PhoneWindowManager init ");
                    try {
                        log("[ Oxygen Customizer - XPLauncher ] mContext null? " + (mContext == null));
                        if (param.args[0] instanceof Context && mContext == null) {
                            log("[ Oxygen Customizer - XPLauncher ] PhoneWindowManager param.args[0] instanceof Context");
                            mContext = (Context) param.args[0];
                            log("[ Oxygen Customizer - XPLauncher ] PhoneWindowManager Context null? " + (mContext == null));

                            ResourceManager.modRes = mContext.createPackageContext(APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
                                    .getResources();

                            XPrefs.init(mContext);
                        }
                        CompletableFuture.runAsync(() -> waitForXprefsLoad(lpparam));
                    } catch (Throwable t) {
                        log("[ Oxygen Customizer - XPLauncher ] fault in PhoneWindowManager: " + t);
                    }
                }
            });
        } else {
            findAndHookMethod(Instrumentation.class, "newApplication", ClassLoader.class, String.class, Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (mContext == null || lpparam.packageName.equals(Constants.Packages.TELECOM_SERVER_PACKAGE)) { //telecom service launches as a secondary process in framework, but has its own package name. context is not null when it loads
                            if (Build.VERSION.SDK_INT >= 35 && lpparam.packageName.equals(Constants.Packages.TELECOM_SERVER_PACKAGE)) return;
                            if (param.args[2] == null) return;
                            if (!(param.args[2] instanceof Context)) return;
                            mContext = (Context) param.args[2];

                            ResourceManager.modRes = mContext.createPackageContext(APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
                                    .getResources();

                            XPrefs.init(mContext);

                            waitForXprefsLoad(lpparam);
                        }
                    } catch (Throwable t) {
                        // Context is null
                        log("[ Oxygen Customizer - XPLauncher ] Instrumentation error in newApplication: " + t);
                    }
                }
            });
        }
    }

    private void onXPrefsReady(XC_LoadPackage.LoadPackageParam lpparam) {
        if (isBootLooped(lpparam.packageName)) {
            log(String.format("Oxygen Customizer: Possible bootloop in %s. Will not load for now", lpparam.packageName));
            return;
        }

        new SystemUtils(mContext);

        loadModpacks(lpparam);
    }

    private void loadModpacks(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Arrays.asList(ResourceManager.modRes.getStringArray(R.array.root_requirement)).contains(lpparam.packageName)) {
            log("Root required package: " + lpparam.packageName);
            forceConnectRootService();
        }
        for (Class<? extends XposedMods> mod : ModPacks.getMods(lpparam.packageName)) {
            try {
                XposedMods instance = mod.getConstructor(Context.class).newInstance(mContext);
                if (!instance.listensTo(lpparam.packageName)) continue;
                try {
                    instance.updatePrefs();
                } catch (Throwable throwable) {
                    instance.log(throwable);
                }
                instance.initResources();
                instance.handleLoadPackageInternal(lpparam);
                runningMods.add(instance);
            } catch (Throwable T) {
                log("Start Error Dump - Occurred in " + mod.getName());
                log(T);
            }
        }
    }

    private void waitForXprefsLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        for (int attempt = 0; attempt < XPREFS_LOAD_RETRIES; attempt++) {
            try {
                if (Xprefs != null) {
                    Xprefs.getBoolean("LoadTestBooleanValue", false);
                    log("Oxygen Customizer Version: " + BuildConfig.VERSION_NAME
                            + " package: " + lpparam.packageName + " loaded");
                    onXPrefsReady(lpparam);
                    return;
                }
            } catch (Throwable ignored) {
                // Provider may not be ready during early boot. Retry briefly, but
                // never stall a host process indefinitely.
            }

            try {
                //noinspection BusyWait
                Thread.sleep(XPREFS_LOAD_RETRY_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log("[ Oxygen Customizer ] Preferences unavailable for " + lpparam.packageName
                + "; skipping hooks for this process instead of blocking startup");
    }

    private void forceConnectRootService() {
        if (!rootConnectWorkerRunning.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                while (rootProxyIPC == null) {
                    if (SystemUtils.UserManager() == null
                            || !SystemUtils.UserManager().isUserUnlocked()) {
                        sleep(2000);
                        continue;
                    }

                    connectRootService();
                    sleep(5000);
                }
            } finally {
                rootConnectWorkerRunning.set(false);
            }
        }, "OC-RootProxy-Connector").start();
    }

    private void connectRootService() {
        if (mContext == null || rootProxyIPC != null
                || !bindInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(APPLICATION_ID, APPLICATION_ID + ".services.RootProviderProxy"));
            boolean bound = mContext.bindService(
                    intent,
                    instance,
                    Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY
            );
            if (!bound) {
                bindInProgress.set(false);
            }
        } catch (Throwable t) {
            bindInProgress.set(false);
            log(t);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        bindInProgress.set(false);
        rootProxyIPC = IRootProviderProxy.Stub.asInterface(service);
        synchronized (proxyQueue) {
            while (!proxyQueue.isEmpty()) {
                try {
                    Objects.requireNonNull(proxyQueue.poll()).run(rootProxyIPC);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        rootProxyIPC = null;
        bindInProgress.set(false);
        forceConnectRootService();
    }

    public static void enqueueProxyCommand(ProxyRunnable runnable) {
        if (rootProxyIPC != null) {
            try {
                runnable.run(rootProxyIPC);
            } catch (RemoteException ignored) {}
        } else {
            synchronized (proxyQueue) {
                proxyQueue.add(runnable);
            }
            instance.forceConnectRootService();
        }
    }

    public interface ProxyRunnable {
        void run(IRootProviderProxy proxy) throws RemoteException;
    }

}
