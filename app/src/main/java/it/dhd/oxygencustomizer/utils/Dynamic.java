package it.dhd.oxygencustomizer.utils;


import static it.dhd.oxygencustomizer.utils.ModuleConstants.BIN_DIR;

import android.os.Build;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.List;

import it.dhd.oxygencustomizer.OxygenCustomizer;

public class Dynamic {

    // Kept as fields for legacy callers; updated from one current overlay-manager
    // snapshot instead of permanently caching the first class-load result.
    public static volatile int TOTAL_ANDROID_THEMES;
    public static volatile List<String> LIST_ANDROID_THEMES = List.of();
    public static volatile int TOTAL_ICON_PACKS;
    public static volatile List<String> LIST_ICON_PACKS = List.of();
    public static volatile int TOTAL_NOTIFICATIONS;
    public static volatile int TOTAL_NAVBAR;
    public static volatile int TOTAL_SIGNAL_ICONS;
    public static volatile int TOTAL_WIFI_ICONS;

    static {
        refreshOverlayInventory();
    }

    public static synchronized void refreshOverlayInventory() {
        Shell.Result result = Shell.cmd("cmd overlay list --user current").exec();
        if (!result.isSuccess()) {
            android.util.Log.w("OC-OverlayInventory", "Overlay list unavailable: " + result.getErr());
            return;
        }
        java.util.ArrayList<String> themes = new java.util.ArrayList<>();
        java.util.ArrayList<String> icons = new java.util.ArrayList<>();
        int notifications = 0, navbar = 0, signal = 0, wifi = 0;
        for (String line : result.getOut()) {
            int start = line.indexOf("OxygenCustomizerComponent");
            if (start < 0) continue;
            String name = line.substring(start);
            if (name.startsWith("OxygenCustomizerComponentTH")) themes.add(line);
            if (name.startsWith("OxygenCustomizerComponentIPSUI")) icons.add(line);
            if (name.startsWith("OxygenCustomizerComponentNFN")) notifications++;
            if (name.startsWith("OxygenCustomizerComponentNB")) navbar++;
            if (name.startsWith("OxygenCustomizerComponentSGIC")) signal++;
            if (name.startsWith("OxygenCustomizerComponentWIFI")) wifi++;
        }
        LIST_ANDROID_THEMES = List.copyOf(themes);
        LIST_ICON_PACKS = List.copyOf(icons);
        TOTAL_ANDROID_THEMES = themes.size();
        TOTAL_ICON_PACKS = icons.size();
        TOTAL_NOTIFICATIONS = notifications;
        TOTAL_NAVBAR = navbar;
        TOTAL_SIGNAL_ICONS = signal;
        TOTAL_WIFI_ICONS = wifi;
    }

    // Overlay compiler tools
    public static final String NATIVE_LIBRARY_DIR = OxygenCustomizer.getAppContext().getApplicationInfo().nativeLibraryDir;
    public static final File AAPT2LIB = new File(NATIVE_LIBRARY_DIR, "libaapt2.so");
    public static final File AAPT2 = new File(BIN_DIR, "aapt2");
    public static final File ZIPALIGNLIB = new File(NATIVE_LIBRARY_DIR, "libzipalign.so");
    public static final File ZIPALIGN = new File(BIN_DIR, "zipalign");

    // Onboarding overlay installation
    public static boolean skippedInstallation = false;

    // Device information
    public static final boolean isAtleastA14 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
}
