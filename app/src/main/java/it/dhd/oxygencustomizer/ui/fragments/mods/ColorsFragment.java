package it.dhd.oxygencustomizer.ui.fragments.mods;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import com.topjohnwu.superuser.Shell;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import it.dhd.oneplusui.preference.OplusPreference;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.base.ControlledPreferenceFragmentCompat;
import it.dhd.oxygencustomizer.ui.dialogs.LoadingDialog;
import it.dhd.oxygencustomizer.ui.preferences.OplusColorPreference;
import it.dhd.oxygencustomizer.utils.ColorUtils;

public class ColorsFragment extends ControlledPreferenceFragmentCompat {

    private OplusColorPreference mPrimaryColor;
    private OplusPreference mApplyColors;
    private String mSystemPalette, mAccentColor;
    private int mColorToApply = 0;
    private LoadingDialog mLoadingDialog;
    private static final ExecutorService COLOR_WORKER = Executors.newSingleThreadExecutor(
            work -> new Thread(work, "OC-ColorWorker"));
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();

    @Override
    public String getTitle() {
        return getString(R.string.colors);
    }

    @Override
    public boolean backButtonEnabled() {
        return true;
    }

    @Override
    public int getLayoutResource() {
        return R.xml.colors_prefs;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public String[] getScopes() {
        return new String[0];
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLoadingDialog = new LoadingDialog(requireContext());

        MenuHost menuHost = requireActivity();
        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                // Add menu items here
                menu.add(0, 1, 0, R.string.menu_launch_app)
                        .setIcon(R.drawable.ic_launch)
                        .setIconTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.textColorPrimary)))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                // Handle the menu selection
                if (menuItem.getItemId() == 1) {
                    Shell.cmd("am start -n com.oplus.uxdesign/com.oplus.uxdesign.personal.PersonalActivity").exec();
                    return true;
                }
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        mPrimaryColor = findPreference("primary_color");
        mApplyColors = findPreference("apply_colors");

        mApplyColors.setOnPreferenceClickListener(preference -> {
            applyColors();
            return true;
        });

    }

    @Override
    public void updateScreen(String key) {
        super.updateScreen(key);
        getDefaultColor();
        if (key == null) return;

        if (key.equals("primary_color")) {
            mColorToApply = mPreferences.getInt("primary_color", 0);
        }
    }

    private void getDefaultColor() {
        int request = generation.incrementAndGet();
        COLOR_WORKER.execute(() -> {
            String accent = null;
            try {
                Shell.Result result = Shell.cmd("settings get secure theme_customization_overlay_packages").exec();
                if (result.isSuccess() && !result.getOut().isEmpty()) {
                    JSONObject settings = new JSONObject(result.getOut().get(0));
                    accent = settings.optString("android.theme.customization.accent_color", null);
                }
            } catch (Throwable failure) {
                Log.e("ColorsFragment", "Could not load current accent colour", failure);
            }
            final String currentAccent = accent;
            mainThread.post(() -> {
                if (request != generation.get() || !isAdded() || mPrimaryColor == null) return;
                if (TextUtils.isEmpty(currentAccent)) return;
                try {
                    int value = Color.parseColor(currentAccent.startsWith("#")
                            ? currentAccent : "#" + currentAccent);
                    int chosen = mPreferences.getInt("primary_color", 0);
                    mPrimaryColor.setPreviewColor(chosen == 0 ? value : chosen, true);
                } catch (IllegalArgumentException failure) {
                    Log.w("ColorsFragment", "Invalid accent colour", failure);
                }
            });
        });
    }

    private void applyColors() {
        if (mColorToApply == 0) return;

        mLoadingDialog.show(getString(R.string.loading_dialog_wait));
        final int selectedColor = mColorToApply;
        COLOR_WORKER.execute(() -> {
            Throwable problem = null;
            try {
                generateColorFiles(String.format("#%08X", selectedColor));
                saveSecureSettings(selectedColor);
            } catch (Throwable failure) {
                Log.e("ColorsFragment", "Unable to apply colours", failure);
                problem = failure;
            }
            final Throwable outcome = problem;
            mainThread.post(() -> {
                if (mLoadingDialog != null) mLoadingDialog.hide();
                if (isAdded()) {
                    Toast.makeText(requireContext(), outcome == null
                            ? getString(R.string.toast_applied)
                            : "Unable to apply colour: " + outcome.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        });

    }

    private void generateColorFiles(String colorHex) {
        int baseColor = Color.parseColor(colorHex);

        int pressedColor = ColorUtils.adjustColorForPressed(baseColor, 0.3f);
        int lightNormalColor = ColorUtils.adjustAlpha(baseColor, 0.3f);
        int lightPressedColor = ColorUtils.adjustAlpha(pressedColor, 0.3f);
        int textHighLightColor = ColorUtils.adjustAlpha(baseColor, 0.15f);
        int barDisabledColor = textHighLightColor;

        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n" +
                "<resources>\n" +
                "<color name=\"couiSingleFirstNormal\">" + colorHex + "</color>\n" +
                "<color name=\"couiSingleFirstPressed\">" + String.format("#%08X", (0xFFFFFFFF & pressedColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstLightNormal\">" + String.format("#%08X", (0xFFFFFFFF & lightNormalColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstLightPressed\">" + String.format("#%08X", (0xFFFFFFFF & lightPressedColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstTextHighLight\">" + String.format("#%08X", (0xFFFFFFFF & textHighLightColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstBarDisabledColor\">" + String.format("#%08X", (0xFFFFFFFF & barDisabledColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstNormal\">" + colorHex + "</color>\n" +
                "<color name=\"NXcolorSingleFirstPressed\">" + String.format("#%08X", (0xFFFFFFFF & pressedColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstLightNormal\">" + String.format("#%08X", (0xFFFFFFFF & lightNormalColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstLightPressed\">" + String.format("#%08X", (0xFFFFFFFF & lightPressedColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstTextHighLight\">" + String.format("#%08X", (0xFFFFFFFF & textHighLightColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstBarDisabledColor\">" + String.format("#%08X", (0xFFFFFFFF & barDisabledColor)) + "</color>\n" +
                "</resources>";

        writeToFile(content, "ux_custom_color.xml");

        int nightPressedColor = ColorUtils.adjustColorForPressed(baseColor, 0.7f);
        int nightLightNormalColor = ColorUtils.adjustAlpha(baseColor, 0.4f);
        int nightLightPressedColor = ColorUtils.adjustAlpha(nightPressedColor, 0.3f);

        String nightContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n" +
                "<resources>\n" +
                "<color name=\"couiSingleFirstNormal\">" + colorHex + "</color>\n" +
                "<color name=\"couiSingleFirstPressed\">" + String.format("#%08X", (0xFFFFFFFF & nightPressedColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstLightNormal\">" + String.format("#%08X", (0xFFFFFFFF & nightLightNormalColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstLightPressed\">" + String.format("#%08X", (0xFFFFFFFF & nightLightPressedColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstTextHighLight\">" + String.format("#%08X", (0xFFFFFFFF & textHighLightColor)) + "</color>\n" +
                "<color name=\"couiSingleFirstBarDisabledColor\">" + String.format("#%08X", (0xFFFFFFFF & textHighLightColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstNormal\">" + colorHex + "</color>\n" +
                "<color name=\"NXcolorSingleFirstPressed\">" + String.format("#%08X", (0xFFFFFFFF & nightPressedColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstLightNormal\">" + String.format("#%08X", (0xFFFFFFFF & nightLightNormalColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstLightPressed\">" + String.format("#%08X", (0xFFFFFFFF & nightLightPressedColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstTextHighLight\">" + String.format("#%08X", (0xFFFFFFFF & textHighLightColor)) + "</color>\n" +
                "<color name=\"NXcolorSingleFirstBarDisabledColor\">" + String.format("#%08X", (0xFFFFFFFF & textHighLightColor)) + "</color>\n" +
                "</resources>";

        writeToFile(nightContent, "ux_custom_color_night.xml");
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void writeToFile(String content, String fileName) {
        if (!"ux_custom_color.xml".equals(fileName)
                && !"ux_custom_color_night.xml".equals(fileName)) {
            throw new IllegalArgumentException("Invalid colour filename");
        }
        String destination = "/data/oplus/uxres/uxcolor/" + fileName;
        String staging = destination + ".oc-write";
        Shell.Result result = Shell.cmd("printf %s " + shellQuote(content)
                + " > " + shellQuote(staging)
                + " && mv -f -- " + shellQuote(staging) + " " + shellQuote(destination)).exec();
        if (!result.isSuccess()) {
            Shell.cmd("rm -f -- " + shellQuote(staging)).exec();
            throw new IllegalStateException("Could not write " + fileName
                    + ": " + result.getErr());
        }
    }

    private void saveSecureSettings(int selectedColor) {
        long timestamp = System.currentTimeMillis();

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("_applied_timestamp", timestamp);
            jsonObject.put("android.theme.customization.theme_style", "TONAL_SPOT");
            jsonObject.put("android.theme.customization.color_source", "home_wallpaper");
            jsonObject.put("material_you_overlay_enable", 1);
            jsonObject.put("android.theme.customization.color_index", 0);
            jsonObject.put("android.theme.customization.system_palette", String.format("%08X", (0xFFFFFFFF & selectedColor)));
            jsonObject.put("android.theme.customization.accent_color", String.format("%08X", (0xFFFFFFFF & selectedColor)));

            /*
            {
                "_applied_timestamp": 1727720712371,
                "android.theme.customization.theme_style": "VIBRANT",
                "android.theme.customization.color_source": "home_wallpaper",
                "material_you_overlay_enable": 1,
                "android.theme.customization.color_index": 0,
                "android.theme.customization.system_palette": "FF38D6D4",
                "android.theme.customization.accent_color": "FF38D6D4"
            }
             */


            String secureString = jsonObject.toString();
            Log.d("ColorsFragment", "Applying secure settings: " + secureString);
            Shell.Result result = Shell.cmd("settings put secure theme_customization_overlay_packages "
                    + shellQuote(secureString)).exec();
            if (!result.isSuccess()) {
                throw new IllegalStateException("Unable to save theme settings: " + result.getErr());
            }
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize theme settings", failure);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getDefaultColor();
    }

    @Override
    public void onDestroyView() {
        generation.incrementAndGet();
        if (mLoadingDialog != null) mLoadingDialog.dismiss();
        super.onDestroyView();
    }

}
