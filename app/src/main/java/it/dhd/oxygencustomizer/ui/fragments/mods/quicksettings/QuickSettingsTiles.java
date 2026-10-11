package it.dhd.oxygencustomizer.ui.fragments.mods.quicksettings;

import android.os.Bundle;
import androidx.preference.TwoStatePreference;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.base.ControlledPreferenceFragmentCompat;
import it.dhd.oxygencustomizer.utils.Constants;

public class QuickSettingsTiles extends ControlledPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        String oldKey = Constants.Preferences.QsTiles.QS_CUSTOMIZE_TILES;
        String newKey = Constants.Preferences.QsTiles.QS_NEW_GRID_ENABLED;
        TwoStatePreference oldSwitch = findPreference(oldKey);
        TwoStatePreference newSwitch = findPreference(newKey);
        if (oldSwitch == null || newSwitch == null) return;

        // Migration: prefer the new grid when older preferences have both flags enabled.
        if (oldSwitch.isChecked() && newSwitch.isChecked()) {
            oldSwitch.setChecked(false);
        }

        oldSwitch.setOnPreferenceChangeListener((preference, next) -> {
            if (Boolean.TRUE.equals(next)) {
                newSwitch.setChecked(false);
            }
            return true;
        });
        newSwitch.setOnPreferenceChangeListener((preference, next) -> {
            if (Boolean.TRUE.equals(next)) {
                oldSwitch.setChecked(false);
            }
            return true;
        });
    }


    @Override
    public String getTitle() {
        return getString(R.string.quick_settings_tiles_title);
    }

    @Override
    public boolean backButtonEnabled() {
        return true;
    }

    @Override
    public int getLayoutResource() {
        return R.xml.quick_settings_tiles_prefs;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public String[] getScopes() {
        return new String[]{Constants.Packages.SYSTEM_UI};
    }
}
