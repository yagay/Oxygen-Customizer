#!/usr/bin/env python3
"""Fast source-level checks for regressions seen in upstream/fork merges.

The Android compiler remains the authoritative compile check; real device
LSPosed and overlay behaviour still needs on-device diagnostics.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/it/dhd/oxygencustomizer"


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"Required file missing: {relative}")
    return path.read_text(encoding="utf-8")


def assert_contains(relative: str, needle: str) -> None:
    content = read(relative)
    if needle not in content:
        raise AssertionError(f"{relative} does not contain required invariant: {needle}")


def check() -> None:
    prefs = "app/src/main/java/it/dhd/oxygencustomizer/utils/PreferenceHelper.java"
    update = "app/src/main/java/it/dhd/oxygencustomizer/utils/UpdateWorker.java"
    controllers = "app/src/main/java/it/dhd/oxygencustomizer/xposed/hooks/systemui/ControllersProvider.java"
    launcher = "app/src/main/java/it/dhd/oxygencustomizer/xposed/XPLauncher.java"
    overlays = "app/src/main/java/it/dhd/oxygencustomizer/utils/overlay/OverlayUtil.java"
    ci = ".github/workflows/auto_build.yml"

    assert_contains(prefs, "mOplusVersion = ")
    assert_contains(prefs, "getprop ro.build.version.oplusrom.display")
    assert_contains(prefs, "return (is16010() || Build.VERSION.SDK_INT >= 37) &&")

    assert_contains(update, "private void showUpdateNotification(String version)")
    assert_contains(update, "showUpdateNotification(versionType")
    assert_contains(controllers, "CopyOnWriteArrayList<UiStateChangedListener> mUiStateChangedListeners")
    assert_contains(controllers, "instance.mUiStateChangedListeners.add(callback)")
    assert_contains(launcher, "recoveryScheduled.compareAndSet(false, true)")
    assert_contains(launcher, "modsLoaded.compareAndSet(false, true)")
    assert_contains(overlays, "boolean actualState = isOverlayEnabled(pkgName);")
    assert_contains(overlays, "Prefs.putBoolean(pkgName, actualState);")

    assert_contains("app/src/main/assets/xposed_init", "it.dhd.oxygencustomizer.xposed.MainHook")
    assert_contains("app/src/main/AndroidManifest.xml", 'android:name="xposedmodule"')

    workflow = read(ci)
    if "assembleRelease" in workflow or "release:" in workflow:
        raise AssertionError("Auto build must not compile Release")
    if "-PciArm64Only=true assembleDebug" not in workflow:
        raise AssertionError("Auto build must only compile arm64 Debug")

    print("OxygenOS upstream merge invariants: PASS")


if __name__ == "__main__":
    try:
        check()
    except AssertionError as error:
        print(f"INTEGRATION CHECK FAILED: {error}", file=sys.stderr)
        sys.exit(1)
