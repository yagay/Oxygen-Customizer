package it.dhd.oxygencustomizer.ui.fragments.mods.misc.memc;

import static it.dhd.oxygencustomizer.OxygenCustomizer.getAppContext;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.FRAMEWORK;
import static it.dhd.oxygencustomizer.utils.PreferenceHelper.getModulePrefs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.OplusRecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.databinding.FragmentAppListBinding;
import it.dhd.oxygencustomizer.ui.adapters.MemcAppAdapter;
import it.dhd.oxygencustomizer.ui.adapters.PackageListAdapter;
import it.dhd.oxygencustomizer.ui.dialogs.MemcDialog;
import it.dhd.oxygencustomizer.ui.base.MemcListLoader;
import it.dhd.oxygencustomizer.ui.models.MemcAppModel;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.utils.OCPreferences;
import it.dhd.oxygencustomizer.xposed.hooks.framework.MemcEnhancer;

public class MemcApplicationsFragment extends Fragment {

    private Map<String, String> mEnabledApps;
    private Set<String> mAlreadyAddedApps = new HashSet<>();
    public SharedPreferences mPreferences;
    public FragmentAppListBinding binding;
    private PackageListAdapter mPackageAdapter;
    private PackageManager mPackageManager;
    private MemcDialog mMemcDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageManager = requireActivity().getPackageManager();
        mPackageAdapter = new PackageListAdapter(requireActivity());

        mMemcDialog = new MemcDialog(requireActivity());
        mMemcDialog.setTitle(R.string.custom_memc_config_title);

    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = FragmentAppListBinding.inflate(inflater, container, false);
        binding.appFunctionSwitch.setVisibility(View.GONE);

        mPreferences = getModulePrefs();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Set<String> enabledApps = mPreferences.getStringSet("custom_memc_applications", new ArraySet<>());
        mEnabledApps = new ArrayMap<>();
        for (String item : enabledApps) {
            if (item == null || item.isBlank()) continue;
            int separator = item.indexOf('|');
            String key = (separator < 0 ? item : item.substring(0, separator)).trim();
            if (key.isEmpty()) continue;
            String config = separator < 0 ? "" : item.substring(separator + 1).trim();
            if (config.isEmpty()) config = "120$267-3-1";
            mEnabledApps.put(key, config);
            mAlreadyAddedApps.add(key);
        }

        mPackageAdapter.setExcludedPackages(new HashSet<>(mAlreadyAddedApps));

        MemcListLoader.load(getViewLifecycleOwner(), mPackageManager,
                mEnabledApps, false, () -> {
            if (!isAdded() || binding == null) return;
            binding.searchView.setEnabled(false);
            binding.progress.setVisibility(View.VISIBLE);
        }, appList -> {
            if (!isAdded() || binding == null) return;
            binding.recyclerView.addItemDecoration(new OplusRecyclerView.OplusRecyclerViewItemDecoration(requireContext()));
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(getAppContext()));
            binding.recyclerView.setAdapter(new MemcAppAdapter(appList,
                    this::onItemClick));
            binding.recyclerView.setHasFixedSize(true);
            binding.searchView.setEnabled(true);
            binding.progress.setVisibility(View.GONE);
            binding.searchView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (binding == null || !(binding.recyclerView.getAdapter() instanceof MemcAppAdapter adapter)) return;
                    adapter.filter(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        });

    }

    @SuppressLint("NotifyDataSetChanged")
    public void addItem() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder.setAdapter(mPackageAdapter, (dialog, which) -> {
            PackageListAdapter.PackageItem info = mPackageAdapter.getItem(which);
            MemcAppModel newApp = new MemcAppModel(
                    (String) info.title,
                    info.packageName,
                    info.icon);
            mMemcDialog.show(
                    newApp,
                    (newModel) -> {
                        mEnabledApps.put(info.packageName, newModel.getRefreshRate() + "$" + newModel.getMemcConfig());
                        ((MemcAppAdapter) binding.recyclerView.getAdapter()).addItem(
                                newModel);
                        binding.recyclerView.getAdapter().notifyDataSetChanged();
                        mAlreadyAddedApps.add(info.packageName);
                        mPackageAdapter.setExcludedPackages(new HashSet<>(mAlreadyAddedApps));
                        savePrefs();
                    },
                    null
            );

        });
        builder.setTitle(getString(R.string.add_app));
        builder.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onItemClick(MemcAppModel model) {
        mMemcDialog.show(model,
                (newModel) -> {
                    mEnabledApps.remove(model.getPackageName());
                    mEnabledApps.put(model.getPackageName(), newModel.getRefreshRate() + "$" + newModel.getMemcConfig());
                    binding.recyclerView.getAdapter().notifyDataSetChanged();
                    savePrefs();
                },
                () -> {
                    mEnabledApps.remove(model.getPackageName());
                    mAlreadyAddedApps.remove(model.getPackageName());
                    ((MemcAppAdapter) binding.recyclerView.getAdapter()).removeItem(model);
                    binding.recyclerView.getAdapter().notifyDataSetChanged();
                    mPackageAdapter.setExcludedPackages(new HashSet<>(mAlreadyAddedApps));
                    savePrefs();
                }
        );
    }

    private void savePrefs() {
        Set<String> enabledApps = new HashSet<>();
        for (Map.Entry<String, String> entry : mEnabledApps.entrySet()) {
            enabledApps.add(entry.getKey() + "|" + entry.getValue());
        }
        mPreferences.edit().putStringSet("custom_memc_applications", enabledApps).apply();

        Intent broadcast = new Intent(Constants.ACTION_SETTINGS_CHANGED);

        broadcast.putExtra("packageName", FRAMEWORK);
        broadcast.putExtra("class", MemcEnhancer.class.getSimpleName());

        OCPreferences.addInternalBroadcastToken(broadcast);
        if (getContext() != null)
            getContext().sendBroadcast(broadcast);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
