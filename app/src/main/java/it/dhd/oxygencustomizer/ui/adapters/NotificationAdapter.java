package it.dhd.oxygencustomizer.ui.adapters;

import static it.dhd.oxygencustomizer.utils.ViewBindingHelpers.setDrawable;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import it.dhd.oxygencustomizer.ui.models.NotificationModel;
import it.dhd.oxygencustomizer.utils.Prefs;
import it.dhd.oxygencustomizer.utils.overlay.manager.NotificationManager;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    Context context;
    ArrayList<NotificationModel> itemList;
    ArrayList<String> NOTIFICATION_KEY = new ArrayList<>();
    LinearLayoutManager linearLayoutManager;
    LoadingDialog loadingDialog;
    int selectedItem = -1;

    public NotificationAdapter(Context context, ArrayList<NotificationModel> itemList, LoadingDialog loadingDialog) {
        this.context = context;
        this.itemList = itemList;
        this.loadingDialog = loadingDialog;

        // Preference key
        for (int i = 1; i <= itemList.size(); i++)
            NOTIFICATION_KEY.add("OxygenCustomizerComponentNFN" + i + ".overlay");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_list_option_notification, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (itemList.get(position).getBackground() != 0)
            setDrawable(holder.container, ContextCompat.getDrawable(context, itemList.get(position).getBackground()));
        else
            setDrawable(holder.container, itemList.get(position).getDrawableBackground());
        holder.style_name.setText(itemList.get(position).getName());
        holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));

        if (Prefs.getBoolean(NOTIFICATION_KEY.get(position))) {
            holder.style_name.setTextColor(context.getResources().getColor(R.color.colorAccent, context.getTheme()));
            holder.container.findViewById(R.id.icon_selected).setVisibility(View.VISIBLE);
        } else {
            holder.style_name.setTextColor(context.getResources().getColor(R.color.textColorPrimary, context.getTheme()));
            holder.container.findViewById(R.id.icon_selected).setVisibility(View.INVISIBLE);
        }

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

        if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return;
        if (Prefs.getBoolean(NOTIFICATION_KEY.get(holder.getBindingAdapterPosition()))) {
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
        // Set onClick operation for each item
        holder.container.setOnClickListener(v -> {
            if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return;
            selectedItem = selectedItem == holder.getBindingAdapterPosition() ? -1 : holder.getBindingAdapterPosition();
            refreshLayout(holder);

            if (!Prefs.getBoolean(NOTIFICATION_KEY.get(holder.getBindingAdapterPosition()))) {
                holder.btn_disable.setVisibility(View.GONE);
                if (holder.btn_enable.getVisibility() == View.VISIBLE) {
                    holder.btn_enable.setVisibility(View.GONE);
                    holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
                } else {
                    holder.btn_enable.setVisibility(View.VISIBLE);
                    holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_collapse_arrow));
                }
            } else {
                holder.btn_enable.setVisibility(View.GONE);
                if (holder.btn_disable.getVisibility() == View.VISIBLE) {
                    holder.btn_disable.setVisibility(View.GONE);
                    holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
                } else {
                    holder.btn_disable.setVisibility(View.VISIBLE);
                    holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_collapse_arrow));
                }
            }
        });

        // Work is performed synchronously on a worker and only then reflected in UI.
        holder.btn_enable.setOnClickListener(v -> changeNotificationStyle(holder, true));
        holder.btn_disable.setOnClickListener(v -> changeNotificationStyle(holder, false));

    }

    private void changeNotificationStyle(ViewHolder holder, boolean enable) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= itemList.size()) return;

        loadingDialog.show(context.getString(R.string.loading_dialog_wait));
        new Thread(() -> {
            boolean success;
            try {
                success = enable ? NotificationManager.enableOverlay(position + 1)
                        : NotificationManager.disableOverlay(position + 1);
            } catch (Throwable failure) {
                android.util.Log.e("OC-NotificationAdapter", "Failed to change style", failure);
                success = false;
            }
            boolean finished = success;
            ((Activity) context).runOnUiThread(() -> {
                loadingDialog.hide();
                Activity activity = (Activity) context;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (finished) {
                    // RecyclerView holders can be rebound while the operation runs.
                    notifyDataSetChanged();
                }
                int message = !finished ? R.string.toast_error
                        : enable ? R.string.toast_applied : R.string.toast_disabled;
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            });
        }, enable ? "OC-EnableNotificationStyle" : "OC-DisableNotificationStyle").start();
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

                    if (i == holder.getAbsoluteAdapterPosition() && Prefs.getBoolean(NOTIFICATION_KEY.get(i - (holder.getAbsoluteAdapterPosition() - holder.getBindingAdapterPosition())))) {
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

    private void refreshButton(ViewHolder holder) {
        if (holder.getBindingAdapterPosition() != selectedItem) {
            holder.btn_enable.setVisibility(View.GONE);
            holder.btn_disable.setVisibility(View.GONE);
            holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
        } else {
            if (Prefs.getBoolean(NOTIFICATION_KEY.get(selectedItem))) {
                holder.btn_enable.setVisibility(View.GONE);
                holder.btn_disable.setVisibility(View.VISIBLE);
                holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_expand_arrow));
            } else {
                holder.btn_enable.setVisibility(View.VISIBLE);
                holder.btn_disable.setVisibility(View.GONE);
                holder.ic_collapse_expand.setForeground(ContextCompat.getDrawable(context, R.drawable.ic_collapse_arrow));
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout container;
        TextView style_name;
        ImageView ic_collapse_expand;
        Button btn_enable, btn_disable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            container = itemView.findViewById(R.id.notification_child);
            style_name = itemView.findViewById(R.id.notif_title);
            ic_collapse_expand = itemView.findViewById(R.id.notif_arrow);
            btn_enable = itemView.findViewById(R.id.enable_notif);
            btn_disable = itemView.findViewById(R.id.disable_notif);
        }
    }
}
