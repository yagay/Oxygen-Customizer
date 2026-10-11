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
    (JAVA / "utils/UpdateWorker.java", [
        "hasTransport(NetworkCapabilities.TRANSPORT_WIFI)",
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
]

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

# Reject regressions whose syntax can compile but whose behavior is wrong.
for filename, forbidden in [
    (JAVA / "xposed/hooks/launcher/Launcher.java",
     "param.getClass().getSimpleName().contains"),
    (JAVA / "xposed/hooks/systemui/statusbar/QsTileCustomization.java",
     'getObjectField(param.thisObject, "getBgOutlineProvider")'),
    (JAVA / "utils/overlay/compiler/OnDemandCompiler.java",
     "Shell.cmd(\"rm -rf \" + TEMP_DIR).exec()"),
]:
    if forbidden in (BASE / filename).read_text(encoding="utf-8"):
        failures.append(f"{filename}: stale unsafe code {forbidden!r}")

if failures:
    raise SystemExit("\n".join(failures))
print(f"Runtime hardening invariant checks passed ({len(checks)} source files)")
