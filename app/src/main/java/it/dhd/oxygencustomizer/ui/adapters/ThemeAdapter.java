package it.dhd.oxygencustomizer.ui.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import it.dhd.oxygencustomizer.OxygenCustomizer;
import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.dialogs.LoadingDialog;
import it.dhd.oxygencustomizer.ui.models.ThemeModel;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ViewHolder> {

    Context context;
    ArrayList<ThemeModel> itemList;
    LinearLayoutManager linearLayoutManager;
    LoadingDialog loadingDialog;
    int selectedItem = -1;
    int enabledItem = -1;

    public ThemeAdapter(Context context, ArrayList<ThemeModel> itemList, LoadingDialog loadingDialog) {
        this.context = context;
        this.itemList = itemList;
        this.loadingDialog = loadingDialog;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_list_option_iconpack, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.style_name.setText(itemList.get(position).getThemeName());
        holder.desc.setVisibility(View.GONE);
        holder.icon_preview.setVisibility(View.GONE);
        itemSelected(holder.container, itemList.get(position).isEnabled());

        refreshButton(holder);

        enableOnClickListener(holder);
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;
        itemSelected(holder.container, itemList.get(position).isEnabled());
        refreshButton(holder);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
    }

    // Function for onClick events
    private void enableOnClickListener(ViewHolder holder) {
        holder.container.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            selectedItem = selectedItem == position ? -1 : position;
            notifyDataSetChanged();
        });
        holder.btn_enable.setOnClickListener(v -> changeTheme(holder, true));
        holder.btn_disable.setOnClickListener(v -> changeTheme(holder, false));
    }

    private void changeTheme(ViewHolder holder, boolean enable) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;
        if (!(context instanceof Activity activity) || activity.isFinishing() || activity.isDestroyed()) return;
        String selectedPackage = itemList.get(position).getPkgName();
        ArrayList<String> packages = new ArrayList<>();
        for (ThemeModel theme : itemList) packages.add(theme.getPkgName());
        String commonPackage = "OxygenCustomizerComponentCOMMONSUITH.overlay";
        loadingDialog.show(context.getString(R.string.loading_dialog_wait));
        new Thread(() -> {
            boolean success = true;
            ArrayList<String> previouslyEnabled = new ArrayList<>();
            OverlayUtil.OPERATION_LOCK.lock();
            try {
                for (String pkg : packages) {
                    if (OverlayUtil.isOverlayEnabled(pkg)) previouslyEnabled.add(pkg);
                }
                boolean commonEnabled = OverlayUtil.isOverlayEnabled(commonPackage);
                if (enable) {
                    success = OverlayUtil.applyOverlayStateNow(selectedPackage, true, false);
                    for (String pkg : packages) {
                        if (!success) break;
                        if (!pkg.equals(selectedPackage) && OverlayUtil.isOverlayEnabled(pkg)) {
                            success = OverlayUtil.applyOverlayStateNow(pkg, false, false);
                        }
                    }
                    if (success && OverlayUtil.overlayExist("COMMONSUITH")) {
                        success = OverlayUtil.applyOverlayStateNow(commonPackage, true, false);
                    }
                } else {
                    success = OverlayUtil.applyOverlayStateNow(selectedPackage, false, false);
                    if (success && commonEnabled) {
                        success = OverlayUtil.applyOverlayStateNow(commonPackage, false, false);
                    }
                }
                if (!success) {
                    for (String pkg : packages) {
                        boolean shouldEnable = previouslyEnabled.contains(pkg);
                        if (OverlayUtil.isOverlayEnabled(pkg) != shouldEnable) {
                            OverlayUtil.applyOverlayStateNow(pkg, shouldEnable, false);
                        }
                    }
                    if (OverlayUtil.isOverlayEnabled(commonPackage) != commonEnabled) {
                        OverlayUtil.applyOverlayStateNow(commonPackage, commonEnabled, false);
                    }
                }
            } catch (Throwable failure) {
                android.util.Log.e("OC-ThemeAdapter", "Theme change failed", failure);
                success = false;
            } finally {
                OverlayUtil.OPERATION_LOCK.unlock();
            }
            boolean completed = success;
            activity.runOnUiThread(() -> {
                loadingDialog.hide();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (completed) {
                    for (int i = 0; i < itemList.size(); i++) {
                        if (enable) itemList.get(i).setEnabled(i == position);
                        else if (i == position) itemList.get(i).setEnabled(false);
                    }
                    notifyDataSetChanged();
                }
                Toast.makeText(context, completed
                        ? (enable ? R.string.toast_applied : R.string.toast_disabled)
                        : R.string.toast_error, Toast.LENGTH_SHORT).show();
            });
        }, "OC-ThemeSelection").start();
    }

    // Function to check for layout changes
    private void refreshLayout(ViewHolder holder) {
        int firstVisible = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();

        for (int i = firstVisible; i <= lastVisible; i++) {
            View view = linearLayoutManager.findViewByPosition(i);

            if (view != null) {
                LinearLayout child = view.findViewById(R.id.icon_pack_child);

                if (!(view == holder.container) && child != null) {
                    child.findViewById(R.id.enable_iconpack).setVisibility(View.GONE);
                    child.findViewById(R.id.disable_iconpack).setVisibility(View.GONE);
                }
            }
        }
    }

    // Function to check for applied options
    @SuppressLint("SetTextI18n")
    private void refreshBackground(ViewHolder holder) {
        int firstVisible = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();

        for (int i = firstVisible; i <= lastVisible; i++) {
            View view = linearLayoutManager.findViewByPosition(i);

            if (view != null) {
                LinearLayout child = view.findViewById(R.id.icon_pack_child);

                if (child != null) {
                    itemSelected(child, i == holder.getAbsoluteAdapterPosition() && (itemList.get(i - (holder.getAbsoluteAdapterPosition() - holder.getBindingAdapterPosition())).isEnabled()));
                }
            }
        }
    }

    private void refreshButton(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;
        if (position != selectedItem) {
            holder.btn_enable.setVisibility(View.GONE);
            holder.btn_disable.setVisibility(View.GONE);
        } else {
            if (itemList.get(position).isEnabled()) {
                holder.btn_enable.setVisibility(View.GONE);
                holder.btn_disable.setVisibility(View.VISIBLE);
            } else {
                holder.btn_enable.setVisibility(View.VISIBLE);
                holder.btn_disable.setVisibility(View.GONE);
            }
        }
    }

    private void itemSelected(View parent, boolean state) {
        if (state) {
            ((TextView) parent.findViewById(R.id.iconpack_title)).setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
            ((TextView) parent.findViewById(R.id.iconpack_desc)).setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
            parent.findViewById(R.id.icon_selected).setVisibility(View.VISIBLE);
            parent.findViewById(R.id.iconpack_desc).setAlpha(0.8f);
        } else {
            ((TextView) parent.findViewById(R.id.iconpack_title)).setTextColor(ContextCompat.getColor(context, R.color.text_color_primary));
            ((TextView) parent.findViewById(R.id.iconpack_desc)).setTextColor(ContextCompat.getColor(context, R.color.text_color_secondary));
            parent.findViewById(R.id.icon_selected).setVisibility(View.INVISIBLE);
            parent.findViewById(R.id.iconpack_desc).setAlpha(1f);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout container;
        TextView style_name, desc;
        LinearLayout icon_preview;
        Button btn_enable, btn_disable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            container = itemView.findViewById(R.id.icon_pack_child);
            desc = itemView.findViewById(R.id.iconpack_desc);
            style_name = itemView.findViewById(R.id.iconpack_title);
            icon_preview = itemView.findViewById(R.id.icon_preview);
            btn_enable = itemView.findViewById(R.id.enable_iconpack);
            btn_disable = itemView.findViewById(R.id.disable_iconpack);
        }
    }
}
