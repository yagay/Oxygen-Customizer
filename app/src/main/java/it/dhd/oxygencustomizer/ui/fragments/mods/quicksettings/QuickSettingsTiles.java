package it.dhd.oxygencustomizer.ui.fragments.mods.quicksettings;

import androidx.preference.TwoStatePreference;

import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.base.ControlledPreferenceFragmentCompat;
import it.dhd.oxygencustomizer.utils.Constants;

public class QuickSettingsTiles extends ControlledPreferenceFragmentCompat {

    private boolean updatingGridMode;

    @Override
    public void updateScreen(String changedKey) {
        super.updateScreen(changedKey);
        TwoStatePreference legacy = findPreference(Constants.Preferences.QsTiles.QS_CUSTOMIZE_TILES);
        TwoStatePreference modern = findPreference(Constants.Preferences.QsTiles.QS_NEW_GRID_ENABLED);
        if (legacy == null || modern == null || updatingGridMode) return;

        // PreferenceHelper.setupAllPreferences() may replace change listeners.
        // Reinstall the exclusive switch handling after the common setup.
        legacy.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) selectGridMode(modern);
            return true;
        });
        modern.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) selectGridMode(legacy);
            return true;
        });

        if (legacy.isChecked() && modern.isChecked()) {
            updatingGridMode = true;
            try {
                // Honor the switch the user just enabled, not always the newer one.
                if (Constants.Preferences.QsTiles.QS_CUSTOMIZE_TILES.equals(changedKey)) {
                    modern.setChecked(false);
                } else {
                    legacy.setChecked(false);
                }
            } finally {
                updatingGridMode = false;
            }
        }
    }

    private void selectGridMode(TwoStatePreference other) {
        if (updatingGridMode) return;
        updatingGridMode = true;
        try {
            if (other.isChecked()) other.setChecked(false);
        } finally {
            updatingGridMode = false;
        }
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
