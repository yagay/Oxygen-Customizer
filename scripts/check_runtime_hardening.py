#!/usr/bin/env python3
"""Lightweight regression guard for high-risk startup/overlay fixes.

This runs before the only permitted arm64 Debug APK build. The Gradle build
remains the authoritative Java/Kotlin compilation check; hardware Hook behavior
must additionally be confirmed on a real supported device.
"""
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
JAVA = Path("app/src/main/java/it/dhd/oxygencustomizer")
checks = [
    (JAVA / "utils/overlay/OverlayUtil.java", [
        "queryOverlayPackages(Boolean enabled)",
        "return queryOverlayPackages(true);",
        "return queryOverlayPackages(false);",
        "Absence is unknown",
        "return Boolean.FALSE.equals(queryOverlayState(pkgName));",
    ]),
    (JAVA / "xposed/XPLauncher.java", [
        "MAX_PENDING_PROXY_COMMANDS",
        "ROOT_CONNECT_RETRIES",
        "modLoadStates.put(mod.getName(), \"PARTIAL\")",
        "runningMods.add(instance);",
    ]),
    (JAVA / "utils/ModuleUtil.java", [
        "requireModuleCommand(",
        "throw new IllegalStateException(\"Failed to write module XML:",
    ]),
    (JAVA / "utils/overlay/compiler/OnDemandCompiler.java", [
        "wasEnabled = force && OverlayUtil.isOverlayEnabled(targetOverlay);",
        "OverlayCompileCoordinator.runChecked(",
    ]),
    (JAVA / "xposed/hooks/settings/CustomShortcut.java", [
        "if (mCategory == null)",
        "intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);",
    ]),
    (JAVA / "ui/activity/LocationBrowseActivity.java", [
        "latch.await(15, TimeUnit.SECONDS)",
        "mExecutorService.shutdownNow();",
        "!input.equals(mQueryString)",
        "mLocationBrowseList.addAll(results)",
    ]),
    (JAVA / "ui/fragments/mods/misc/memc/MemcApplicationsFragment.java", [
        "new java.util.HashMap<>(enabledApps)",
        "executor.shutdown();",
        "new HashSet<>(mAlreadyAddedApps)",
    ]),
    (JAVA / "ui/fragments/mods/misc/memc/MemcActivitiesFragment.java", [
        "delimiter <= 0 || delimiter >= pkg.length() - 1",
        "executor.shutdown();",
    ]),
    (JAVA / "ui/adapters/MemcAppAdapter.java", [
        "filteredApps.get(position)",
        "if (matchesText)",
    ]),
    (JAVA / "xposed/hooks/framework/MemcEnhancer.java", [
        "XPrefs.isTrustedInternalBroadcast(intent)",
        "columns.length != 2",
        "values.length != 2",
        "slash <= 0",
    ]),
    (JAVA / "ui/fragments/mods/misc/MemcFragment.java", [
        "OCPreferences.addInternalBroadcastToken(broadcast)",
    ]),
    (JAVA / "utils/PackageModeParser.java", [
        "public static Map<String, Integer> parse(Set<String> values, int maxMode)",
        "catch (NumberFormatException ignored)",
        "mode < 0 || mode > maxMode",
    ]),
    (JAVA / "ui/fragments/UpdateFragment.java", [
        'Shell.cmd("reboot").submit(result ->',
        "public static int getNightlyBuildNumber(String versionName)",
        "Character.isDigit(versionName.charAt(end))",
        'if (!result.isSuccess())',
    ]),
    (JAVA / "xposed/hooks/framework/DarkMode.java", [
        "PackageModeParser.parse(darkModeApps, mapping.length - 1)",
    ]),
    (JAVA / "xposed/hooks/settings/DarkModeSettings.java", [
        "AppEntity != null",
        "PackageModeParser.parse(darkModeApps, 4)",
    ]),
    (JAVA / "xposed/hooks/systemui/statusbar/StatusbarNotification.java", [
        "PackageModeParser.parse(notificationApps, 2)",
        "if (!(rawEntries instanceof Collection<?> entries)) return;",
        "Math.max(0, clockContainer.getChildCount() - 1)",
        "resIdWidth != 0",
        "tintedCopy(",
    ]),
    (JAVA / "ui/fragments/mods/misc/DarkMode.java", [
        "PackageModeParser.parse(enabledApps, 4)",
        "if (isChecked) {",
        "mEnabledApps.remove(model.getPackageName());",
    ]),
    (JAVA / "ui/base/AppFragmentBase.java", [
        "executor.shutdown();",
    ]),
    (JAVA / "ui/adapters/PackageListAdapter.java", [
        "mLoadGeneration",
        "if (msg.arg1 != mLoadGeneration) return;",
        "new HashSet<>(mExcludedPackages)",
        "new HashSet<>(packages)",
    ]),
    (JAVA / "ui/adapters/AppAdapter.java", [
        "holder.itemView.post(() -> filter(filterText));",
        "private final OnSwitchChange switchChangeListener;",
        "private final OnSliderChange sliderChangeListener;",
        "private final OnMenuChange menuChangeListener;",
        "filteredApps.get(position)",
        "matchesText && (matchesSystem || app.isEnabled())",
    ]),
    (JAVA / "ui/fragments/mods/statusbar/StatusbarNotificationChoice.java", [
        "PackageModeParser.parse(enabledApps, 2)",
    ]),
    (JAVA / "utils/UpdateWorker.java", [
        "hasTransport(NetworkCapabilities.TRANSPORT_WIFI)",
        "UpdateFragment.getNightlyBuildNumber(versionName)",
    ]),
    (JAVA / "xposed/utils/toolkit/ReflectedClass.java", [
        "findClassIfExists(name, loader)",
        "reportUnavailable(name)",
    ]),
    (JAVA / "utils/overlay/compiler/CompilerUtil.java", [
        r"\\.apk$",
    ]),
    (JAVA / "utils/overlay/compiler/OnboardingCompiler.java", [
        'name.contains("SGIC")',
        ").exec();",
    ]),
    (JAVA / "utils/overlay/compiler/RoundnessCompiler.java", [
        "try {",
        'OverlayCompileCoordinator.runChecked(',
    ]),
    (JAVA / "utils/overlay/compiler/SettingsIconsCompiler.java", [
        "try {",
        'OverlayCompileCoordinator.runChecked(',
    ]),
    (JAVA / "xposed/hooks/launcher/Launcher.java", [
        "param.thisObject.getClass().getSimpleName()",
    ]),
    (JAVA / "xposed/hooks/systemui/VolumePanel.java", [
        'getRecommendedTimeoutMillis',
        'param.setResult(mTimeOut);',
    ]),
    (JAVA / "xposed/hooks/systemui/statusbar/QsTileCustomization.java", [
        "if (outline != null) param.setResult(outline);",
        "if (param.thisObject instanceof View tile) tile.invalidateOutline();",
        "Drawable.ConstantState state =",
    ]),
    (JAVA / "xposed/hooks/systemui/lockscreen/DepthWallpaper.java", [
        "AI plugin IPC is a broadcast",
        "XPLauncher.enqueueProxyCommand(proxy ->",
    ]),
    (JAVA / "services/RootProviderProxy.java", [
        "Root command failed with exit code",
        "Overlay activation was not verified",
        "Subject image copy failed",
    ]),
    (JAVA / "services/RootProvider.java", [
        "shellQuote(SQLITE_BIN)",
        "LSPosed database query failed",
    ]),
    (JAVA / "ui/fragments/Mods.java", [
        "SearchPreferenceResult.highlight(this, result.getKey())",
        "executePendingTransactions()",
    ]),
    (JAVA / "ui/activity/MainActivity.java", [
        "modsFragment.onSearchResultClicked(result)",
        "executePendingTransactions()",
    ]),
    (JAVA / "ui/fragments/mods/quicksettings/QuickSettings.java", [
        "pullDownIndicator != null && pullDownIndicator.getParent() instanceof ViewGroup",
    ]),
    (JAVA / "ui/fragments/uistyle/SettingsIcons.java", [
        "boolean first = !OverlayUtil.isOverlayEnabled(firstPkg)",
    ]),
    (JAVA / "utils/overlay/manager/NotificationManager.java", [
        "public static boolean enableOverlay(int style)",
        "public static boolean disableOverlay(int style)",
        "OverlayUtil.OPERATION_LOCK.lock()",
    ]),
    (JAVA / "ui/adapters/NotificationAdapter.java", [
        "boolean finished = success",
        "notifyDataSetChanged()",
    ]),
    (JAVA / "utils/BitmapSubjectSegmenter.java", [
        "MASK_WORKER",
        "Do not recycle inputBitmap",
        "mask.remaining()",
    ]),
    (JAVA / "utils/overlay/compiler/OverlayCompiler.java", [
        'OverlayCompiler.listContains(result.getErr(), "colorSurfaceHeader")',
    ]),
    (JAVA / "ui/fragments/mods/misc/MemcFragment.java", [
        '"force_memc_enabled".equals(key)',
        '"OC-UpdateMemcFeatures"',
        "unregisterReceiver(mFeatureReceiver)",
    ]),
    (JAVA / "utils/FileUtil.java", [
        'target.toPath().startsWith(appFiles.toPath())',
        'Shell.cmd("rm -rf -- " + shellQuote(target.getAbsolutePath()))',
        'File.createTempFile("oc-import-", suffix, getAppContext().getFilesDir())',
        '&& mv -f --',
        'shellQuote(source)',
        'shellQuote(destination)',
        'String[] descendants = assets.list(inputPath);',
        'try (InputStream input = assets.open(inputPath);',
    ]),
    (JAVA / "ui/fragments/mods/lockscreen/DepthWallpaperFragment.java", [
        'data == null || data.getData() == null || !isAdded()',
        'if (mAiStatus == null || getContext() == null) return;',
        'if (isAdded() && mAiStatus != null)',
    ]),
    (JAVA / "ui/fragments/uistyle/NotificationRoundness.java", [
        'OverlayUtil.applyOverlayStateNow(pkg, false, false)',
        'final boolean disabled = success;',
        'requestedRadius',
        'void onDestroyView()',
    ]),
]

# Regression coverage for bugs found during the additional whole-project audit.
checks.extend([
    (JAVA / "ui/adapters/PackageListAdapter.java", [
        "List<PackageItem> result = new java.util.ArrayList<>();",
        "mHandler.obtainMessage(0, generation, 0, result).sendToTarget();",
    ]),
    (JAVA / "ui/adapters/ActivitiesListAdapter.java", [
        "if (msg.arg1 != mLoadGeneration) return;",
        "if (list == null) list = new ActivityInfo[0];",
    ]),
    (JAVA / "ui/adapters/ClockPreviewAdapter.java", [
        "private final String prefSwitch;",
        "private Bitmap wallpaperBitmap;",
        "getInt(prefStyle, 0) == position",
    ]),
    (JAVA / "utils/overlay/FabricatedUtil.java", [
        "if (expected.equals(line.trim())) return true;",
        "queryFabricatedOverlayNames(Boolean enabled)",
    ]),
    (JAVA / "xposed/XPLauncher.java", [
        "ROOT_BIND_TIMEOUT_MS",
        "onBindingDied(ComponentName name)",
        "onNullBinding(ComponentName name)",
    ]),
    (JAVA / "ui/fragments/UpdateFragment.java", [
        "mUpdateRequestGeneration",
        "OC-NightlyUpdateExtractor",
    ]),
    (JAVA / "utils/overlay/compiler/OverlayCompileCoordinator.java", [
        "cleanupStaging(String... paths)",
        "active == enabled[i]",
    ]),
    (JAVA / "ui/adapters/IconsAdapter.java", [
        'OverlayUtil.applyOverlayStateNow(selectedPackage, true, false)',
        'if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;',
    ]),
    (JAVA / "ui/adapters/ThemeAdapter.java", [
        'OverlayUtil.applyOverlayStateNow(selectedPackage, true, false)',
        'if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;',
    ]),
    (JAVA / "ui/adapters/NavbarAdapter.java", [
        'OverlayUtil.applyOverlayStateNow(selectedPackage, true, false)',
        'if (position == RecyclerView.NO_POSITION || position >= NAVBAR_KEY.size()) return;',
    ]),
    (JAVA / "ui/base/AppFragmentBase.java", [
        "new HashMap<>(enabledApps)",
        "new HashSet<>(enabledApps)",
    ]),
])

checks.extend([
    (JAVA / "receivers/UpdateDownloadReceiver.java", [
        "enqueueUniqueWork(",
        "DownloadManager.STATUS_SUCCESSFUL",
        "pending_update_token_",
        "verifyChecksum(",
    ]),
    (JAVA / "ui/fragments/UpdateFragment.java", [
        "MAX_NIGHTLY_APK_BYTES",
        "extracted > MAX_NIGHTLY_APK_BYTES",
        "UpdateDownloadReceiver.enqueueDownload(",
    ]),
    (JAVA / "utils/extension/TaskExecutor.java", [
        "preExecuteLatch.countDown();",
        "mExecutor.shutdown();",
        "mTask.cancel(true)",
    ]),
    (JAVA / "utils/RootUtil.java", [
        "private static void setPermissionsChecked(",
        "if (!result.isSuccess())",
    ]),
    (JAVA / "utils/overlay/compiler/OverlayCompileCoordinator.java", [
        "notePackageInstallAttempt(",
        "if (!installAttempted[i]) continue;",
        "installedBackups[i] = fallback;",
    ]),
    (JAVA / "xposed/hooks/systemui/lockscreen/DepthWallpaper.java", [
        "catch (NumberFormatException invalidPreference)",
        "if (stateValue == null) return;",
        "if (DWallpaperEnabled && (DWMode == 0 || DWMode == 2))",
    ]),
])
# Additional end-to-end runtime guards on the newest audit branch.
checks.extend([
    (JAVA / "utils/helper/BackupRestore.java", [
        "replaceAtomically(source",
        "cp -p -- ",
        "mv -f -- ",
        "if (!result.isSuccess())",
    ]),
    (JAVA / "utils/overlay/OverlayUtil.java", [
        'matchesComponentOverlay(packageName, componentName)',
        'suffix.matches("[0-9]+")',
    ]),
    (JAVA / "utils/ModuleUtil.java", [
        "previous = new java.util.ArrayList<>(OverlayUtil.getEnabledOverlayList())",
        "Uninstall rollback could not enable",
    ]),
    (JAVA / "weather/WeatherWork.java", [
        "LOCATION_TIMEOUT_MS",
        "whenCompleteAsync((location, failure)",
        "if (isStopped())",
    ]),
    (JAVA / "receivers/UpdateDownloadReceiver.java", [
        "recoverPendingDownloads(Context context)",
        "scheduleCheck(context, id)",
        "hasMatchingAppSignature(Context context, File apk)",
    ]),
    (JAVA / "ui/fragments/UpdateFragment.java", [
        ".hasMatchingAppSignature(requireContext(), new File(downloadPath))",
    ]),
    (JAVA / "utils/overlay/compiler/OverlayCompileCoordinator.java", [
        "if (installed) {",
        "installedBackups[i] != null",
    ]),
    (JAVA / "utils/extension/TaskExecutor.java", [
        "mStatus = Status.FINISHED;",
        "if (!mHandler.post(() -> {",
    ]),
])

failures = []
for filename, needles in checks:
    content = (BASE / filename).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in content:
            failures.append(f"{filename}: missing required guard {needle!r}")

launcher = (BASE / JAVA / "xposed/XPLauncher.java").read_text(encoding="utf-8")
if launcher.find("runningMods.add(instance);") > launcher.find(
    "instance.handleLoadPackageInternal(lpparam);"
):
    failures.append("XPLauncher: partial Hooks must be registered for preference updates")

compiler = (BASE / JAVA / "utils/overlay/compiler/OnDemandCompiler.java").read_text(
    encoding="utf-8"
)
if compiler.find("try {") > compiler.find(
    "wasEnabled = force && OverlayUtil.isOverlayEnabled(targetOverlay);"
):
    failures.append("OnDemandCompiler: snapshot must be inside the lock-protected try")

# The file-import temp name must not originate from an untrusted document provider.
file_util = (BASE / JAVA / "utils/FileUtil.java").read_text(encoding="utf-8")
if "new File(getAppContext().getFilesDir(), name)" in file_util:
    failures.append("FileUtil: unsafe document provider file name")
if 'rm -f \\"' + ' + destination' in file_util:
    failures.append("FileUtil: destination removed before a verified move")

# Reject regressions whose syntax can compile but whose behavior is wrong.
for filename, forbidden in [
    (JAVA / "xposed/hooks/launcher/Launcher.java",
     "param.getClass().getSimpleName().contains"),
    (JAVA / "xposed/hooks/systemui/statusbar/QsTileCustomization.java",
     'getObjectField(param.thisObject, "getBgOutlineProvider")'),
]:
    if forbidden in (BASE / filename).read_text(encoding="utf-8"):
        failures.append(f"{filename}: stale unsafe code {forbidden!r}")

if failures:
    raise SystemExit("\n".join(failures))
print(f"Runtime hardening invariant checks passed ({len(checks)} source files)")
