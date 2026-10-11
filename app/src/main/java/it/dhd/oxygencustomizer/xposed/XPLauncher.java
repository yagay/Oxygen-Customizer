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
import android.os.SystemClock;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static volatile IRootProviderProxy rootProxyIPC;
    public static final ConcurrentHashMap<String, String> modLoadStates = new ConcurrentHashMap<>();
    private static final int MAX_PENDING_PROXY_COMMANDS = 64;
    private static final int ROOT_CONNECT_RETRIES = 24;
    private static final long ROOT_BIND_TIMEOUT_MS = 15_000;
    private static volatile long lastRootBindAttempt = 0;
    private static final ExecutorService ROOT_COMMAND_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "OC-RootCommand-Worker");
        worker.setDaemon(true);
        return worker;
    });
    private static final Queue<ProxyRunnable> proxyQueue = new LinkedList<>();
    private static final AtomicBoolean rootConnectWorkerRunning = new AtomicBoolean(false);
    private static final AtomicBoolean bindInProgress = new AtomicBoolean(false);
    // Context hooks can fire more than once; installing a pack twice corrupts UI state.
    private final AtomicBoolean modsLoaded = new AtomicBoolean(false);
    private final AtomicBoolean loadingMods = new AtomicBoolean(false);
    private final AtomicBoolean recoveryScheduled = new AtomicBoolean(false);
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

                            loadPrefsWithoutBlockingHost(lpparam);
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
        if (modsLoaded.get() || !loadingMods.compareAndSet(false, true)) return;
        try {
            if (isBootLooped(lpparam.packageName)) {
                log("Oxygen Customizer: startup protection active in " + lpparam.packageName);
                modLoadStates.put("STARTUP", "BOOT_LOOP_PROTECTED");
                modsLoaded.set(true);
                return;
            }
            new SystemUtils(mContext);
            loadModpacks(lpparam);
            modsLoaded.set(true);
            long unsuccessful = modLoadStates.values().stream()
                    .filter(state -> !"ACTIVE".equals(state)).count();
            if (unsuccessful > 0) {
                log("[ Oxygen Customizer ] " + unsuccessful + " Hook packs not fully active in "
                        + lpparam.processName);
            }
        } catch (Throwable failure) {
            log("[ Oxygen Customizer ] Hook startup failed in " + lpparam.processName);
            log(failure);
            // A transient failure before pack installation can be retried.
        } finally {
            loadingMods.set(false);
        }
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
                    modLoadStates.put(mod.getName(), "PREFERENCES_FAILED");
                    // Never install a hook with uninitialised preference state.
                    continue;
                }
                instance.initResources();
                // Register before invoking installers: partially installed Hooks still
                // need preference updates and must be visible in diagnostics.
                runningMods.add(instance);
                try {
                    instance.handleLoadPackageInternal(lpparam);
                    modLoadStates.put(mod.getName(), "ACTIVE");
                } catch (Throwable partialFailure) {
                    modLoadStates.put(mod.getName(), "PARTIAL");
                    log("[ Oxygen Customizer ] Partial Hook: " + mod.getName());
                    log(partialFailure);
                    continue;
                }
                if (BuildConfig.DEBUG) {
                    log("[ Oxygen Customizer ] Registered " + mod.getSimpleName()
                            + " for " + lpparam.processName);
                }
            } catch (Throwable T) {
                modLoadStates.put(mod.getName(), "FAILED");
                log("Start Error Dump - Occurred in " + mod.getName());
                log(T);
            }
        }
    }

    private void loadPrefsWithoutBlockingHost(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (Xprefs != null) {
                Xprefs.getBoolean("LoadTestBooleanValue", false);
                onXPrefsReady(lpparam);
                return;
            }
        } catch (Throwable unavailable) {
            log("[ Oxygen Customizer ] Preferences not ready, deferring Hook load for "
                    + lpparam.processName);
        }
        // newApplication runs on the host's startup thread. Never perform
        // multi-second retry sleeps on that thread.
        CompletableFuture.runAsync(() -> waitForXprefsLoad(lpparam));
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
                + "; scheduling bounded asynchronous recovery");
        // Do not stall the host process during early boot. Recover only once,
        // on a background thread, if the preference provider becomes available.
        if (recoveryScheduled.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                for (int attempt = 0; attempt < 60 && !modsLoaded.get(); attempt++) {
                    try {
                        Thread.sleep(500);
                        if (Xprefs != null) {
                            Xprefs.getBoolean("LoadTestBooleanValue", false);
                            onXPrefsReady(lpparam);
                            return;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable ignored) {
                        // Preference provider can be unavailable until user unlock.
                    }
                }
                log("[ Oxygen Customizer ] Preference recovery exhausted for "
                        + lpparam.processName);
            });
        }
    }

    private void forceConnectRootService() {
        if (!rootConnectWorkerRunning.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                int attempts = 0;
                while (rootProxyIPC == null && attempts < ROOT_CONNECT_RETRIES) {
                    if (SystemUtils.UserManager() == null
                            || !SystemUtils.UserManager().isUserUnlocked()) {
                        // Waiting for the first unlock must not consume bind attempts.
                        sleep(5000);
                        continue;
                    }

                    attempts++;
                    connectRootService();
                    sleep(5000);
                }
                if (rootProxyIPC == null) {
                    log("[ Oxygen Customizer ] Root proxy unavailable after bounded retries");
                }
            } catch (Throwable failure) {
                log("[ Oxygen Customizer ] Root connector error: " + failure);
            } finally {
                rootConnectWorkerRunning.set(false);
            }
        }, "OC-RootProxy-Connector").start();
    }

    private void connectRootService() {
        if (mContext == null || rootProxyIPC != null) return;
        long now = SystemClock.elapsedRealtime();
        if (bindInProgress.get()) {
            if (now - lastRootBindAttempt < ROOT_BIND_TIMEOUT_MS) return;
            // bindService can return true without ever delivering a connection.
            // Release that binding before another attempt; otherwise all retries are no-ops.
            log("[ Oxygen Customizer ] Root binding timed out; retrying");
            try {
                mContext.unbindService(instance);
            } catch (Throwable ignored) {
            }
            bindInProgress.set(false);
        }
        if (!bindInProgress.compareAndSet(false, true)) return;
        lastRootBindAttempt = now;

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
        ArrayList<ProxyRunnable> pending = new ArrayList<>();
        synchronized (proxyQueue) {
            while (!proxyQueue.isEmpty()) pending.add(Objects.requireNonNull(proxyQueue.poll()));
        }
        for (ProxyRunnable command : pending) {
            dispatchProxyCommand(command);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        rootProxyIPC = null;
        bindInProgress.set(false);
        forceConnectRootService();
    }

    @Override
    public void onBindingDied(ComponentName name) {
        releaseDeadRootBinding();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        releaseDeadRootBinding();
    }

    private void releaseDeadRootBinding() {
        rootProxyIPC = null;
        try {
            if (mContext != null) mContext.unbindService(this);
        } catch (Throwable ignored) {
        }
        bindInProgress.set(false);
        forceConnectRootService();
    }

    private static void dispatchProxyCommand(ProxyRunnable command) {
        ROOT_COMMAND_EXECUTOR.execute(() -> {
            IRootProviderProxy proxy = rootProxyIPC;
            if (proxy == null) {
                enqueueProxyCommand(command);
                return;
            }
            try {
                command.run(proxy);
            } catch (RemoteException failure) {
                log("[ Oxygen Customizer ] Root IPC failed: " + failure);
                // Do not blindly replay root operations: some may have executed
                // before the binder died, so duplicate execution is unsafe.
                rootProxyIPC = null;
                if (instance != null) instance.forceConnectRootService();
            } catch (Throwable failure) {
                log("[ Oxygen Customizer ] Root IPC callback failed: " + failure);
            }
        });
    }

    public static void enqueueProxyCommand(ProxyRunnable runnable) {
        if (runnable == null) return;
        if (rootProxyIPC != null) {
            dispatchProxyCommand(runnable);
        } else {
            synchronized (proxyQueue) {
                if (proxyQueue.size() >= MAX_PENDING_PROXY_COMMANDS) {
                    proxyQueue.poll();
                    log("[ Oxygen Customizer ] Root IPC queue full, dropped oldest command");
                }
                proxyQueue.add(runnable);
            }
            if (instance != null) instance.forceConnectRootService();
        }
    }

    public interface ProxyRunnable {
        void run(IRootProviderProxy proxy) throws RemoteException;
    }

}
