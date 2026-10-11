package it.dhd.oxygencustomizer.services.tiles;

import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_INACTIVE;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.utils.OCPreferences;

/** A tile reflecting the saved sleep-on-flat preference without leaking listeners. */
public class SleepOnSurfaceTileService extends TileService {
    private static final String KEY = "SleepOnFlatScreen";
    private boolean listening;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> {
                if (KEY.equals(key)) {
                    getMainExecutor().execute(() -> setTile(OCPreferences.getBoolean(KEY, false)));
                }
            };

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            OCPreferences.getPrefs().registerOnSharedPreferenceChangeListener(listener);
            listening = true;
        }
        setTile(OCPreferences.getBoolean(KEY, false));
    }

    @Override
    public void onStopListening() {
        unregisterListener();
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        unregisterListener();
        super.onDestroy();
    }

    private void unregisterListener() {
        if (listening) {
            OCPreferences.getPrefs().unregisterOnSharedPreferenceChangeListener(listener);
            listening = false;
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean enabled = !OCPreferences.getBoolean(KEY, false);
        OCPreferences.putBoolean(KEY, enabled);
        setTile(enabled);
    }

    private void setTile(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setIcon(Icon.createWithResource(getApplicationContext(),
                enabled ? R.drawable.ic_sleep_surface_on : R.drawable.ic_sleep_surface_off));
        tile.setState(enabled ? STATE_ACTIVE : STATE_INACTIVE);
        tile.setSubtitle(getString(enabled ? R.string.general_on : R.string.general_off));
        tile.setLabel(getString(R.string.sleep_on_flat_screen_tile_title));
        tile.updateTile();
    }
}
