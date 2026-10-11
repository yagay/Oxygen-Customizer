package it.dhd.oxygencustomizer.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Defensive decoder for the package|mode entries stored in shared preferences.
 *  This class is shared by UI, Settings hooks and SystemUI hooks.
 */
public final class PackageModeParser {
    private PackageModeParser() {}

    public static Map<String, Integer> parse(Set<String> values, int maxMode) {
        Map<String, Integer> parsed = new HashMap<>();
        if (values == null) return parsed;

        for (String value : values) {
            if (value == null) continue;
            int separator = value.indexOf('|');
            String packageName = (separator < 0 ? value : value.substring(0, separator)).trim();
            if (packageName.isEmpty()) continue;

            int mode = 0;
            if (separator >= 0) {
                String rawMode = value.substring(separator + 1).trim();
                try {
                    mode = Integer.parseInt(rawMode);
                    if (mode < 0 || mode > maxMode) mode = 0;
                } catch (NumberFormatException ignored) {
                    // Corrupted legacy entries must never crash a hooked system process.
                    mode = 0;
                }
            }
            parsed.put(packageName, mode);
        }
        return parsed;
    }
}
