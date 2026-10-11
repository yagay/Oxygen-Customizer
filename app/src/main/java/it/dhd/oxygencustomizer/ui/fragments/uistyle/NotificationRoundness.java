package it.dhd.oxygencustomizer.ui.fragments.uistyle;

import static it.dhd.oxygencustomizer.utils.Constants.Preferences.NOTIFICATION_CORNER_RADIUS;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;

import java.io.IOException;

import it.dhd.oxygencustomizer.OxygenCustomizer;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.databinding.FragmentNotifRoundnessBinding;
import it.dhd.oxygencustomizer.ui.base.BaseFragment;
import it.dhd.oxygencustomizer.ui.dialogs.LoadingDialog;
import it.dhd.oxygencustomizer.utils.AppUtils;
import it.dhd.oxygencustomizer.utils.Prefs;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;
import it.dhd.oxygencustomizer.utils.overlay.manager.RoundnessManager;

public class NotificationRoundness extends BaseFragment {

    private FragmentNotifRoundnessBinding binding;
    private LoadingDialog loadingDialog;

    @Override
    public String getTitle() {
        return getString(R.string.notification_corner_radius_title);
    }

    @Override
    public boolean backButtonEnabled() {
        return true;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotifRoundnessBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Show loading dialog
        loadingDialog = new LoadingDialog(requireContext());

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setCornerRadius(Prefs.getInt(NOTIFICATION_CORNER_RADIUS, 28));
        gradientDrawable.setColor(ContextCompat.getColor(OxygenCustomizer.getAppContext(), R.color.offStateColor));
        binding.notifRoundPreview.notificationChild.setBackground(gradientDrawable);
        binding.notifRoundPreview.notifDesc.setText(getString(R.string.notif_roundness_preview_desc));
        binding.disableRadius.setVisibility(OverlayUtil.isOverlayEnabled("OxygenCustomizerComponentCRN1.overlay") || OverlayUtil.isOverlayEnabled("OxygenCustomizerComponentCR1.overlay") ? View.VISIBLE : View.GONE);
        binding.disableRadius.setOnClickListener(v -> {
            loadingDialog.show(getString(R.string.loading_dialog_wait));
            new Thread(() -> {
                boolean success = true;
                for (String pkg : new String[]{
                        "OxygenCustomizerComponentCRN1.overlay",
                        "OxygenCustomizerComponentCR1.overlay"}) {
                    if (OverlayUtil.isOverlayEnabled(pkg)
                            && !OverlayUtil.applyOverlayStateNow(pkg, false, false)) {
                        success = false;
                    }
                }
                final boolean disabled = success;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (loadingDialog != null) loadingDialog.hide();
                    if (!isAdded() || binding == null) return;
                    if (disabled) binding.disableRadius.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            disabled ? R.string.toast_disabled : R.string.toast_error,
                            Toast.LENGTH_SHORT).show();
                });
            }, "OC-DisableNotificationRadius").start();
        });

        final int[] finalUiCornerRadius = {Prefs.getInt(NOTIFICATION_CORNER_RADIUS, 28)};

        if (finalUiCornerRadius[0] == 28) {
            binding.cornerRadiusOutput.setText(getResources().getString(R.string.default_value));
        } else {
            binding.cornerRadiusOutput.setText(finalUiCornerRadius[0] + " dp");
        }
        gradientDrawable.setCornerRadius(finalUiCornerRadius[0] * OxygenCustomizer.getAppContext().getResources().getDisplayMetrics().density);
        binding.cornerRadiusSeekbar.setValue(finalUiCornerRadius[0]);

        binding.cornerRadiusSeekbar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                finalUiCornerRadius[0] = (int) slider.getValue();
                if (finalUiCornerRadius[0] == 28)
                    binding.cornerRadiusOutput.setText(getResources().getString(R.string.default_value));
                else
                    binding.cornerRadiusOutput.setText(finalUiCornerRadius[0] + " dp");
            }
        });

        binding.cornerRadiusSeekbar.addOnChangeListener((slider, value, fromUser) -> {
            gradientDrawable.setCornerRadius(value * OxygenCustomizer.getAppContext().getResources().getDisplayMetrics().density);
            gradientDrawable.invalidateSelf();
        });

        binding.applyRadius.setOnClickListener(v -> {
            if (!AppUtils.hasStoragePermission()) {
                AppUtils.requestStoragePermission(requireContext());
            } else {
                // Show loading dialog
                loadingDialog.show(getResources().getString(R.string.loading_dialog_wait));
                final int requestedRadius = finalUiCornerRadius[0];
                new Thread(() -> {
                    boolean failed;
                    try {
                        failed = RoundnessManager.buildOverlay(requestedRadius, true);
                    } catch (Exception error) {
                        failed = true;
                        Log.e("NotificationRoundness",
                                "Failed to compile or install roundness overlay", error);
                    }
                    final boolean success = !failed;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (loadingDialog != null) loadingDialog.hide();
                        if (!isAdded() || binding == null) return;
                        if (success) {
                            Prefs.putInt(NOTIFICATION_CORNER_RADIUS, requestedRadius);
                            binding.disableRadius.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(requireContext(),
                                success ? R.string.toast_applied : R.string.toast_error,
                                Toast.LENGTH_SHORT).show();
                    });
                }, "OC-ApplyNotificationRadius").start();
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }
        super.onDestroy();
    }
}
