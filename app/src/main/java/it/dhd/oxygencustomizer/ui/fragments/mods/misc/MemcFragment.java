package it.dhd.oxygencustomizer.ui.fragments.mods.misc;

import static it.dhd.oxygencustomizer.OxygenCustomizer.getAppContextLocale;
import static it.dhd.oxygencustomizer.utils.Constants.ACTIONS_MEMC_FEATURE_GET;
import static it.dhd.oxygencustomizer.utils.Constants.ACTIONS_MEMC_FEATURE_RECEIVED;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.FRAMEWORK;
import static it.dhd.oxygencustomizer.utils.Constants.SETTINGS_SECURE_OSIE_MOTION_FLUENCY_SWITCH;
import static it.dhd.oxygencustomizer.utils.Constants.SETTINGS_SECURE_OSIE_MOTION_VALUE;
import static it.dhd.oxygencustomizer.utils.Constants.SETTINGS_SECURE_OSIE_VIDEO_SWITCH;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.topjohnwu.superuser.Shell;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import it.dhd.oneplusui.preference.OplusPreference;
import it.dhd.oxygencustomizer.OxygenCustomizer;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.base.ControlledPreferenceFragmentCompat;
import it.dhd.oxygencustomizer.ui.dialogs.LoadingDialog;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.utils.OCPreferences;
import it.dhd.oxygencustomizer.utils.ModuleUtil;
import it.dhd.oxygencustomizer.utils.ThemeUtils;
import it.dhd.oxygencustomizer.xposed.hooks.framework.MemcEnhancer;

public class MemcFragment extends ControlledPreferenceFragmentCompat {

    private static final ExecutorService SETTINGS_WORKER =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "OC-MemcSettings"));
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger settingsGeneration = new AtomicInteger();
    private boolean cachedMemcEnabled;
    private boolean cachedSdr2hdrEnabled;
    private String cachedMemcMode = "";
    private volatile boolean secureSettingsLoaded;
    private LoadingDialog mLoadingDialog;

    private boolean mIsFeatureOn = false;
    private boolean mIsPwX7Enable = false;
    private boolean mMemcEnable = false;
    private boolean mSdr2hdrEnable = false;
    private boolean mVideoOsieSupport = false;

    private boolean mReceiverRegistered = false;
    private final AtomicBoolean featureUpdateInProgress = new AtomicBoolean(false);

    private OplusPreference mFeatureOn, mPwX7Enable, mMemcEnabled, mSdr2hdrEnabled, mVideoOsieSupported, mMemcMode;

    @Override
    public String getTitle() {
        return getString(R.string.memc_page_title);
    }

    @Override
    public boolean backButtonEnabled() {
        return true;
    }

    @Override
    public int getLayoutResource() {
        return R.xml.memc_overview;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public String[] getScopes() {
        return new String[0];
    }

    final BroadcastReceiver mFeatureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTIONS_MEMC_FEATURE_RECEIVED.equals(intent.getAction())
                    || !OCPreferences.isTrustedInternalBroadcast(intent)) return;
            {
                mIsFeatureOn = intent.hasExtra("feature_on") ? intent.getBooleanExtra("feature_on", false) : false;
                mIsPwX7Enable = intent.hasExtra("pw_x7_enable") ? intent.getBooleanExtra("pw_x7_enable", false) : false;
                mMemcEnable = intent.hasExtra("memc_enable") ? intent.getBooleanExtra("memc_enable", false) : getMemcEnableFromSettings();
                mSdr2hdrEnable = intent.hasExtra("sdr2hdr_enable") ? intent.getBooleanExtra("sdr2hdr_enable", false) : getSdr2hdrEnableFromSettings();
                mVideoOsieSupport = intent.hasExtra("video_osie_support") ? intent.getBooleanExtra("video_osie_support", false) : false;
                setPrefs();
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        mLoadingDialog = new LoadingDialog(requireContext());

        if (!mReceiverRegistered) {
            mReceiverRegistered = true;
            requireContext().registerReceiver(mFeatureReceiver, new IntentFilter(ACTIONS_MEMC_FEATURE_RECEIVED), Context.RECEIVER_EXPORTED);
        }

        mFeatureOn = findPreference("memc_feature_on");
        mPwX7Enable = findPreference("pwx7_enabled");
        mMemcEnabled = findPreference("memc_enabled");
        mSdr2hdrEnabled = findPreference("sdr2hdr_enabled");
        mVideoOsieSupported = findPreference("video_osie_support");
        mMemcMode = findPreference("memc_mode");

        Intent intent = new Intent(ACTIONS_MEMC_FEATURE_GET).setPackage(FRAMEWORK);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        OCPreferences.addInternalBroadcastToken(intent);
        requireContext().sendBroadcast(intent);

    }

    private void setPrefs() {
        if (getContext() == null) {
            return;
        }

        if (mFeatureOn != null) {
            mFeatureOn.setSummary(getSpannedSummary(mIsFeatureOn));
        }

        if (mPwX7Enable != null) {
            mPwX7Enable.setSummary(getSpannedSummary(mIsPwX7Enable));
        }

        if (mMemcEnabled != null) {
            mMemcEnabled.setSummary(secureSettingsLoaded
                    ? getSpannedSummary(getMemcEnableFromSettings()) : getString(R.string.toast_error));
        }

        if (mSdr2hdrEnabled != null) {
            mSdr2hdrEnabled.setSummary(secureSettingsLoaded
                    ? getSpannedSummary(getSdr2hdrEnableFromSettings()) : getString(R.string.toast_error));
        }

        if (mVideoOsieSupported != null) {
            mVideoOsieSupported.setSummary(getSpannedSummary(mVideoOsieSupport));
        }

        if (mMemcMode != null) {
            mMemcMode.setSummary(secureSettingsLoaded
                    ? getMemcModeFromSettings() : getString(R.string.toast_error));
        }

    }

    private SpannableStringBuilder getSpannedSummary(boolean enabled) {
        String text = enabled ? getString(R.string.general_enabled) : getString(R.string.general_disabled);
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        int color = enabled ?
                ContextCompat.getColor(requireContext(), android.R.color.system_accent1_400) :
                ThemeUtils.getAttrColor(requireContext(), R.attr.colorError);
        spannableStringBuilder.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableStringBuilder;
    }

    private String getSecureSetting(String key, String defaultValue) {
        Shell.Result response = Shell.cmd("settings get secure " + key).exec();
        if (!response.isSuccess() || response.getOut().isEmpty()) return null;
        String value = response.getOut().get(0).trim();
        return "null".equals(value) || value.isEmpty() ? null : value;
    }

    private void refreshSecureSettingsAsync() {
        final int request = settingsGeneration.incrementAndGet();
        SETTINGS_WORKER.execute(() -> {
            String memc = null, hdr = null, mode = null;
            try {
                memc = getSecureSetting(SETTINGS_SECURE_OSIE_MOTION_FLUENCY_SWITCH, "0");
                hdr = getSecureSetting(SETTINGS_SECURE_OSIE_VIDEO_SWITCH, "0");
                mode = getSecureSetting(SETTINGS_SECURE_OSIE_MOTION_VALUE, "");
            } catch (Throwable failure) {
                Log.e("MemcFragment", "Unable to read MEMC secure settings", failure);
            }
            final boolean available = memc != null && hdr != null && mode != null;
            final boolean enabled = "1".equals(memc);
            final boolean hdrEnabled = "1".equals(hdr);
            final String reportedMode = mode;
            mainHandler.post(() -> {
                if (request != settingsGeneration.get() || !isAdded() || getView() == null) return;
                secureSettingsLoaded = available;
                if (available) {
                    cachedMemcEnabled = enabled;
                    cachedSdr2hdrEnabled = hdrEnabled;
                    cachedMemcMode = reportedMode;
                }
                setPrefs();
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSecureSettingsAsync();
    }

    public boolean getMemcEnableFromSettings() {
        return cachedMemcEnabled;
    }

    public String getMemcModeFromSettings() {
        return cachedMemcMode;
    }

    public boolean getSdr2hdrEnableFromSettings() {
        return !mVideoOsieSupport && cachedSdr2hdrEnabled;
    }

    @Override
    public void updateScreen(String key) {
        super.updateScreen(key);

        if (key == null) return;

        if ("force_memc_enabled".equals(key)) {
            if (featureUpdateInProgress.get()) return;
            enableMemc();
            return;
        }

        Intent broadcast = new Intent(Constants.ACTION_SETTINGS_CHANGED).setPackage(FRAMEWORK);
        broadcast.putExtra("packageName", FRAMEWORK);
        broadcast.putExtra("class", MemcEnhancer.class.getSimpleName());
        OCPreferences.addInternalBroadcastToken(broadcast);
        if (getContext() != null)
            getContext().sendBroadcast(broadcast);
    }

    private void enableMemc() {
        if (!ModuleUtil.moduleExists() || mLoadingDialog == null) return;
        if (!featureUpdateInProgress.compareAndSet(false, true)) return;
        final boolean enabled = mPreferences.getBoolean("force_memc_enabled", false);
        mLoadingDialog.show(getString(R.string.loading_dialog_wait));
        new Thread(() -> {
            boolean applied = false;
            try {
                ModuleUtil.enableMemcFeature(enabled);
                applied = true;
            } catch (Throwable failure) {
                Log.e("MemcFragment", "Unable to update MEMC module features", failure);
            }
            final boolean success = applied;
            mainHandler.post(() -> {
                if (!success) {
                    // The preference switch is optimistic. Restore its previous value
                    // while the in-progress guard prevents a second firmware write.
                    mPreferences.edit().putBoolean("force_memc_enabled", !enabled).apply();
                }
                featureUpdateInProgress.set(false);
                if (!isAdded() || getView() == null || mLoadingDialog == null) return;
                mLoadingDialog.hide();
                if (success) {
                    Intent broadcast = new Intent(Constants.ACTION_SETTINGS_CHANGED).setPackage(FRAMEWORK);
                    broadcast.putExtra("packageName", FRAMEWORK);
                    broadcast.putExtra("class", MemcEnhancer.class.getSimpleName());
                    OCPreferences.addInternalBroadcastToken(broadcast);
                    requireContext().sendBroadcast(broadcast);
                    refreshSecureSettingsAsync();
                }
                Toast.makeText(requireContext(), success ? R.string.toast_applied
                        : R.string.toast_error, Toast.LENGTH_SHORT).show();
            });
        }, "OC-UpdateMemcFeatures").start();
    }


    @Override
    public void onDestroy() {
        settingsGeneration.incrementAndGet();
        if (mReceiverRegistered && getContext() != null) {
            try {
                requireContext().unregisterReceiver(mFeatureReceiver);
            } catch (IllegalArgumentException ignored) {
                Log.w("MemcFragment", "MEMC receiver was already unregistered");
            }
            mReceiverRegistered = false;
        }
        if (mLoadingDialog != null) mLoadingDialog.dismiss();
        mLoadingDialog = null;
        super.onDestroy();
    }

}
