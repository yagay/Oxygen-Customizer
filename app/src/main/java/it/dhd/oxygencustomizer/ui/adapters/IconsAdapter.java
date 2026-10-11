package it.dhd.oxygencustomizer.ui.adapters;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
import it.dhd.oxygencustomizer.ui.models.IconModel;
import it.dhd.oxygencustomizer.utils.AppUtils;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

public class IconsAdapter extends RecyclerView.Adapter<IconsAdapter.ViewHolder> {

    Context context;
    ArrayList<IconModel> itemList;
    LinearLayoutManager linearLayoutManager;
    LoadingDialog loadingDialog;
    int selectedItem = -1;
    String mComponentName = "", mAdditionalComponent = "";
    private OnButtonClick mOnButtonClick = null;
    private boolean needSystemUIRestart = false;

    public interface OnButtonClick{
        boolean onEnableClick(int position, IconModel item);
        boolean onDisableClick(int position, IconModel item);
    }

    public IconsAdapter(Context context, ArrayList<IconModel> itemList, LoadingDialog loadingDialog, String compName, boolean needSysUiRestart) {
        this.context = context;
        this.itemList = itemList;
        this.loadingDialog = loadingDialog;
        this.mComponentName = compName;
        this.needSystemUIRestart = needSysUiRestart;
    }

    public IconsAdapter(Context context, ArrayList<IconModel> itemList, LoadingDialog loadingDialog, String compName, OnButtonClick onButtonClick, boolean needSysUiRestart) {
        this.context = context;
        this.itemList = itemList;
        this.loadingDialog = loadingDialog;
        this.mComponentName = compName;
        this.mOnButtonClick = onButtonClick;
        this.needSystemUIRestart = needSysUiRestart;
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
        holder.style_name.setText(itemList.get(position).getName());
        if (itemList.get(position).getDesc() != 0) {
            holder.desc.setVisibility(View.VISIBLE);
            holder.desc.setText(context.getResources().getString(itemList.get(position).getDesc()));
        } else
            holder.desc.setVisibility(View.GONE);

        if (itemList.get(position).getIcon1() != 0) {
            holder.icon1.setVisibility(View.VISIBLE);
            holder.icon1.setImageResource(itemList.get(position).getIcon1());
        }
        else if (itemList.get(position).getDrawableIcon1() != null) {
            holder.icon1.setVisibility(View.VISIBLE);
            holder.icon1.setImageDrawable(itemList.get(position).getDrawableIcon1());
        }
        else
            holder.icon1.setVisibility(View.GONE);
        if (itemList.get(position).getIcon2() != 0) {
            holder.icon2.setVisibility(View.VISIBLE);
            holder.icon2.setImageResource(itemList.get(position).getIcon2());
        }
        else if (itemList.get(position).getDrawableIcon2() != null) {
            holder.icon2.setVisibility(View.VISIBLE);
            holder.icon2.setImageDrawable(itemList.get(position).getDrawableIcon2());
        }
        else
            holder.icon2.setVisibility(View.GONE);
        if (itemList.get(position).getIcon3() != 0) {
            holder.icon3.setVisibility(View.VISIBLE);
            holder.icon3.setImageResource(itemList.get(position).getIcon3());
        }
        else if (itemList.get(position).getDrawableIcon3() != null) {
            holder.icon3.setVisibility(View.VISIBLE);
            holder.icon3.setImageDrawable(itemList.get(position).getDrawableIcon3());
        }
        else
            holder.icon3.setVisibility(View.GONE);
        if (itemList.get(position).getIcon4() != 0) {
            holder.icon4.setVisibility(View.VISIBLE);
            holder.icon4.setImageResource(itemList.get(position).getIcon4());
        }
        else if (itemList.get(position).getDrawableIcon4() != null) {
            holder.icon4.setVisibility(View.VISIBLE);
            holder.icon4.setImageDrawable(itemList.get(position).getDrawableIcon4());
        }
        else
            holder.icon4.setVisibility(View.GONE);

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
        holder.btn_enable.setOnClickListener(v -> changeIcons(holder, true));
        holder.btn_disable.setOnClickListener(v -> changeIcons(holder, false));
    }

    private void changeIcons(ViewHolder holder, boolean enable) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;
        if (!(context instanceof Activity activity) || activity.isFinishing() || activity.isDestroyed()) return;
        IconModel selectedModel = itemList.get(position);
        String selectedPackage = selectedModel.getPackageName();
        ArrayList<String> packages = new ArrayList<>();
        for (IconModel model : itemList) packages.add(model.getPackageName());
        String extraPackage = TextUtils.isEmpty(mAdditionalComponent) ? null
                : "OxygenCustomizerComponent" + mAdditionalComponent + ".overlay";
        loadingDialog.show(context.getString(R.string.loading_dialog_wait));
        new Thread(() -> {
            boolean success = true;
            OverlayUtil.OPERATION_LOCK.lock();
            try {
                if (mOnButtonClick != null) {
                    success = enable
                            ? mOnButtonClick.onEnableClick(position, selectedModel)
                            : mOnButtonClick.onDisableClick(position, selectedModel);
                } else {
                    ArrayList<String> previouslyEnabled = new ArrayList<>();
                    for (String pkg : packages) {
                        if (OverlayUtil.isOverlayEnabled(pkg)) previouslyEnabled.add(pkg);
                    }
                    boolean extraEnabled = extraPackage != null && OverlayUtil.isOverlayEnabled(extraPackage);
                    if (enable) {
                        success = OverlayUtil.applyOverlayStateNow(selectedPackage, true, false);
                        for (String pkg : packages) {
                            if (!success) break;
                            if (!pkg.equals(selectedPackage) && OverlayUtil.isOverlayEnabled(pkg)) {
                                success = OverlayUtil.applyOverlayStateNow(pkg, false, false);
                            }
                        }
                        if (success && extraPackage != null) {
                            success = OverlayUtil.applyOverlayStateNow(extraPackage, true, false);
                        }
                    } else {
                        success = OverlayUtil.applyOverlayStateNow(selectedPackage, false, false);
                        if (success && extraEnabled) {
                            success = OverlayUtil.applyOverlayStateNow(extraPackage, false, false);
                        }
                    }
                    if (!success) {
                        for (String pkg : packages) {
                            boolean shouldEnable = previouslyEnabled.contains(pkg);
                            if (OverlayUtil.isOverlayEnabled(pkg) != shouldEnable) {
                                OverlayUtil.applyOverlayStateNow(pkg, shouldEnable, false);
                            }
                        }
                        if (extraPackage != null && OverlayUtil.isOverlayEnabled(extraPackage) != extraEnabled) {
                            OverlayUtil.applyOverlayStateNow(extraPackage, extraEnabled, false);
                        }
                    }
                }
            } catch (Throwable failure) {
                Log.e("OC-IconsAdapter", "Icon selection failed", failure);
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
                    if (needSystemUIRestart) AppUtils.restartScope("systemui");
                }
                Toast.makeText(context, completed
                        ? (enable ? R.string.toast_applied : R.string.toast_disabled)
                        : R.string.toast_error, Toast.LENGTH_SHORT).show();
            });
        }, "OC-IconSelection").start();
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
                    Log.d("IconsAdapter", "refresh " + i + " " + holder.getAbsoluteAdapterPosition() + " " + holder.getBindingAdapterPosition() + " | " + (itemList.get(i - (holder.getAbsoluteAdapterPosition() - holder.getBindingAdapterPosition())).isEnabled()));
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
        ImageView icon1, icon2, icon3, icon4;
        Button btn_enable, btn_disable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            container = itemView.findViewById(R.id.icon_pack_child);
            style_name = itemView.findViewById(R.id.iconpack_title);
            desc = itemView.findViewById(R.id.iconpack_desc);
            icon1 = itemView.findViewById(R.id.iconpack_preview1);
            icon2 = itemView.findViewById(R.id.iconpack_preview2);
            icon3 = itemView.findViewById(R.id.iconpack_preview3);
            icon4 = itemView.findViewById(R.id.iconpack_preview4);
            btn_enable = itemView.findViewById(R.id.enable_iconpack);
            btn_disable = itemView.findViewById(R.id.disable_iconpack);
        }
    }
}
