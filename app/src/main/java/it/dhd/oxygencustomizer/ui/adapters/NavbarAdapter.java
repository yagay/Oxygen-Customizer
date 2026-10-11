package it.dhd.oxygencustomizer.ui.adapters;

import static it.dhd.oxygencustomizer.utils.Dynamic.TOTAL_NAVBAR;
import static it.dhd.oxygencustomizer.utils.overlay.OverlayUtil.getDrawableFromOverlay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import it.dhd.oxygencustomizer.utils.Prefs;
import it.dhd.oxygencustomizer.utils.overlay.OverlayUtil;

public class NavbarAdapter extends RecyclerView.Adapter<NavbarAdapter.ViewHolder> {

    Context context;
    ArrayList<String> itemList;
    ArrayList<String> NAVBAR_KEY = new ArrayList<>();
    LinearLayoutManager linearLayoutManager;
    LoadingDialog loadingDialog;
    int selectedItem = -1;
    int expandedItem = -1;

    public NavbarAdapter(Context context, ArrayList<String> itemList, LoadingDialog loadingDialog) {
        this.context = context;
        this.itemList = itemList;
        this.loadingDialog = loadingDialog;

        // Preference key
        for (int i = 1; i <= itemList.size(); i++)
            NAVBAR_KEY.add("OxygenCustomizerComponentNB" + i + ".overlay");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_list_option_navbar, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.style_name.setText(itemList.get(position));

        if (Prefs.getBoolean(NAVBAR_KEY.get(position))) {
            holder.style_name.setTextColor(context.getResources().getColor(R.color.colorAccent, context.getTheme()));
            holder.container.findViewById(R.id.icon_selected).setVisibility(View.VISIBLE);
        } else {
            holder.style_name.setTextColor(context.getResources().getColor(R.color.textColorPrimary, context.getTheme()));
            holder.container.findViewById(R.id.icon_selected).setVisibility(View.INVISIBLE);
        }

        setIcons(holder, position+1);

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
        if (position == RecyclerView.NO_POSITION || position >= NAVBAR_KEY.size()) return;
        if (Prefs.getBoolean(NAVBAR_KEY.get(position))) {
            holder.style_name.setTextColor(context.getResources().getColor(R.color.colorAccent, context.getTheme()));
            holder.container.findViewById(R.id.icon_selected).setVisibility(View.VISIBLE);
        } else {
            holder.style_name.setTextColor(context.getResources().getColor(R.color.textColorPrimary, context.getTheme()));
            holder.container.findViewById(R.id.icon_selected).setVisibility(View.INVISIBLE);
        }

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
            expandedItem = selectedItem;
            notifyDataSetChanged();
        });
        holder.btn_enable.setOnClickListener(v -> changeNavbar(holder, true));
        holder.btn_disable.setOnClickListener(v -> changeNavbar(holder, false));
    }

    private void changeNavbar(ViewHolder holder, boolean enable) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= NAVBAR_KEY.size()) return;
        if (!(context instanceof Activity activity) || activity.isFinishing() || activity.isDestroyed()) return;
        String selectedPackage = NAVBAR_KEY.get(position);
        ArrayList<String> packages = new ArrayList<>(NAVBAR_KEY);
        loadingDialog.show(context.getString(R.string.loading_dialog_wait));
        new Thread(() -> {
            boolean success = true;
            ArrayList<String> previouslyEnabled = new ArrayList<>();
            OverlayUtil.OPERATION_LOCK.lock();
            try {
                for (String pkg : packages) {
                    if (OverlayUtil.isOverlayEnabled(pkg)) previouslyEnabled.add(pkg);
                }
                if (enable) {
                    success = OverlayUtil.applyOverlayStateNow(selectedPackage, true, false);
                    for (String pkg : packages) {
                        if (!success) break;
                        if (!pkg.equals(selectedPackage) && OverlayUtil.isOverlayEnabled(pkg)) {
                            success = OverlayUtil.applyOverlayStateNow(pkg, false, false);
                        }
                    }
                } else {
                    success = OverlayUtil.applyOverlayStateNow(selectedPackage, false, false);
                }
                if (!success) {
                    for (String pkg : packages) {
                        boolean shouldEnable = previouslyEnabled.contains(pkg);
                        if (OverlayUtil.isOverlayEnabled(pkg) != shouldEnable) {
                            OverlayUtil.applyOverlayStateNow(pkg, shouldEnable, false);
                        }
                    }
                }
            } catch (Throwable failure) {
                Log.e("OC-NavbarAdapter", "Navbar change failed", failure);
                success = false;
            } finally {
                OverlayUtil.OPERATION_LOCK.unlock();
            }
            boolean completed = success;
            activity.runOnUiThread(() -> {
                loadingDialog.hide();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                notifyDataSetChanged();
                Toast.makeText(context, completed
                        ? (enable ? R.string.toast_applied : R.string.toast_disabled)
                        : R.string.toast_error, Toast.LENGTH_SHORT).show();
            });
        }, "OC-NavbarSelection").start();
    }

    // Function to check for layout changes
    private void refreshLayout(ViewHolder holder) {
        int firstVisible = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();

        for (int i = firstVisible; i <= lastVisible; i++) {
            View view = linearLayoutManager.findViewByPosition(i);

            if (view != null) {
                LinearLayout child = view.findViewById(R.id.notification_child);

                if (!(view == holder.container) && child != null) {
                    child.findViewById(R.id.enable_notif).setVisibility(View.GONE);
                    child.findViewById(R.id.disable_notif).setVisibility(View.GONE);
                    child.findViewById(R.id.notif_arrow).setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
                }
            }
        }
    }

    // Function to check for applied options
    @SuppressLint("SetTextI18n")
    private void refreshName(ViewHolder holder) {
        int firstVisible = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();

        for (int i = firstVisible; i <= lastVisible; i++) {
            View view = linearLayoutManager.findViewByPosition(i);

            if (view != null) {
                LinearLayout child = view.findViewById(R.id.notification_child);

                if (child != null) {
                    TextView title = child.findViewById(R.id.notif_title);
                    ImageView selected = child.findViewById(R.id.icon_selected);

                    if (i == holder.getAbsoluteAdapterPosition() && Prefs.getBoolean(NAVBAR_KEY.get(i - (holder.getAbsoluteAdapterPosition() - holder.getBindingAdapterPosition())))) {
                        title.setTextColor(context.getResources().getColor(R.color.colorAccent, context.getTheme()));
                        selected.setVisibility(View.VISIBLE);
                    } else {
                        title.setTextColor(context.getResources().getColor(R.color.textColorPrimary, context.getTheme()));
                        selected.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }
    }

    private void setIcons(ViewHolder holder, int position) {
        Log.d("NavbarAdapter", "OxygenCustomizerComponentNB" + position + ".overlay");
        holder.backButton.setImageDrawable(getDrawableFromOverlay(context, "OxygenCustomizerComponentNB" + position + ".overlay", "ic_sysbar_back"));
        holder.homeButton.setImageDrawable(getDrawableFromOverlay(context, "OxygenCustomizerComponentNB" + position + ".overlay", "ic_sysbar_home"));
        holder.recentsButton.setImageDrawable(getDrawableFromOverlay(context, "OxygenCustomizerComponentNB" + position + ".overlay", "ic_sysbar_recent"));
    }

    private void refreshButton(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= NAVBAR_KEY.size()) return;
        if (position != selectedItem) {
            holder.btn_enable.setVisibility(View.GONE);
            holder.btn_disable.setVisibility(View.GONE);
            //holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
        } else {
            if (Prefs.getBoolean(NAVBAR_KEY.get(position))) {
                holder.btn_enable.setVisibility(View.GONE);
                holder.btn_disable.setVisibility(View.VISIBLE);
                ///holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
            } else {
                holder.btn_enable.setVisibility(View.VISIBLE);
                holder.btn_disable.setVisibility(View.GONE);
                //holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_collapse_arrow));
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout container;
        TextView style_name;
        ImageView backButton, homeButton, recentsButton;
        Button btn_enable, btn_disable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            container = itemView.findViewById(R.id.navbar_child);
            style_name = itemView.findViewById(R.id.navbar_style_name);
            backButton = itemView.findViewById(R.id.backButton);
            homeButton = itemView.findViewById(R.id.image2);
            recentsButton = itemView.findViewById(R.id.image3);
            btn_enable = itemView.findViewById(R.id.enable_notif);
            btn_disable = itemView.findViewById(R.id.disable_notif);
        }
    }
}
