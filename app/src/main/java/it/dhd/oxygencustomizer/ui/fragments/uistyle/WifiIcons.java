package it.dhd.oxygencustomizer.ui.fragments.uistyle;

import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.getDrawableFromOverlay;
import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.getOverlayForComponent;
import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.getStringFromOverlay;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.databinding.FragmentRecyclerBinding;
import it.dhd.oxygencustomizer.ui.adapters.IconsAdapter;
import it.dhd.oxygencustomizer.ui.base.BaseFragment;
import it.dhd.oxygencustomizer.ui.dialogs.LoadingDialog;
import it.dhd.oxygencustomizer.ui.models.IconModel;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

public class WifiIcons extends BaseFragment {

    private FragmentRecyclerBinding binding;
    private LoadingDialog loadingDialog;
    private ArrayList<IconModel> wifiIcons;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecyclerBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Loading dialog while enabling or disabling pack
        loadingDialog = new LoadingDialog(requireContext());

        // RecyclerView
        binding.recyclerViewFragment.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewFragment.setAdapter(initWifiIconsItems());
        binding.recyclerViewFragment.setHasFixedSize(true);

        return view;
    }

    private IconsAdapter initWifiIconsItems() {
        wifiIcons = new ArrayList<>();
        List<String> packs = getOverlayForComponent("WIFI");
        for (int i = 0; i< packs.size(); i++) {
            String pkgName = packs.get(i).split("]")[1].replaceAll(" ", "");
            wifiIcons.add(
                    new IconModel(
                            getStringFromOverlay(getContext(), pkgName, "theme_name"),
                            pkgName,
                            getDrawableFromOverlay(getContext(), pkgName, "stat_signal_wifi_signal_1"),
                            getDrawableFromOverlay(getContext(), pkgName, "stat_signal_wifi_signal_2"),
                            getDrawableFromOverlay(getContext(), pkgName, "stat_signal_wifi_signal_3"),
                            getDrawableFromOverlay(getContext(), pkgName, "stat_signal_wifi_signal_4"),
                            packs.get(i).contains("[x]")));
        }
        wifiIcons.sort(Comparator.comparing(IconModel::getName));
        return new IconsAdapter(requireContext(), wifiIcons, loadingDialog, "WIFI", onButtonClick, true);
    }

    private final IconsAdapter.OnButtonClick onButtonClick = new IconsAdapter.OnButtonClick() {
        @Override
        public void onEnableClick(int position, IconModel item) {
            final String selected = item.getPackageName();
            OverlayUtil.executeOverlayBatch(() -> {
                boolean success = true;
                for (IconModel choice : wifiIcons) {
                    success &= OverlayUtil.applyOverlayStateNow(choice.getPackageName(), false, false);
                }
                success &= OverlayUtil.applyOverlayStateNow(
                        "OxygenCustomizerComponentCOMMONWIFI.overlay", true, false);
                success &= OverlayUtil.applyOverlayStateNow(selected, true, false);
                refreshVisibleIcons(success);
            });
        }

        @Override
        public void onDisableClick(int position, IconModel item) {
            OverlayUtil.executeOverlayBatch(() -> {
                boolean success = OverlayUtil.applyOverlayStateNow(item.getPackageName(), false, false);
                boolean anyEnabled = false;
                for (IconModel choice : wifiIcons) {
                    anyEnabled |= OverlayUtil.isOverlayEnabled(choice.getPackageName());
                }
                if (!anyEnabled) success &= OverlayUtil.applyOverlayStateNow(
                        "OxygenCustomizerComponentCOMMONWIFI.overlay", false, false);
                refreshVisibleIcons(success);
            });
        }
    };

    private void refreshVisibleIcons(boolean succeeded) {
        boolean[] states = new boolean[wifiIcons.size()];
        for (int i = 0; i < wifiIcons.size(); i++) {
            states[i] = OverlayUtil.isOverlayEnabled(wifiIcons.get(i).getPackageName());
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isAdded() || binding == null) return;
            for (int i = 0; i < states.length; i++) wifiIcons.get(i).setEnabled(states[i]);
            if (binding.recyclerViewFragment.getAdapter() != null)
                binding.recyclerViewFragment.getAdapter().notifyDataSetChanged();
            if (!succeeded) Toast.makeText(requireContext(), R.string.toast_error,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroy() {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public String getTitle() {
        return getString(R.string.theme_customization_wifi_icon_title);
    }

    @Override
    public boolean backButtonEnabled() {
        return true;
    }
}