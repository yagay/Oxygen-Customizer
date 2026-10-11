package it.dhd.oxygencustomizer.ui.adapters;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import it.dhd.oxygencustomizer.R;

public class ActivitiesListAdapter extends BaseAdapter {

    private final Context mContext;
    private final PackageManager mPm;
    private final LayoutInflater mInflater;
    private final List<ActivityItem> mInstalledPackages = new LinkedList<>();
    private Set<String> mExcludedActivities = new HashSet<>();
    private String mPackageName;
    private volatile int mLoadGeneration = 0;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 != mLoadGeneration) return;
            @SuppressWarnings("unchecked")
            List<ActivityItem> result = (List<ActivityItem>) msg.obj;
            mInstalledPackages.clear();
            mInstalledPackages.addAll(result);
            notifyDataSetChanged();
        }
    };

    public static class ActivityItem implements Comparable<ActivityItem> {
        public final String activityName;
        public final CharSequence title;
        public final Drawable icon;

        ActivityItem(String packageName, CharSequence title, Drawable icon) {
            this.activityName = packageName;
            this.title = title;
            this.icon = icon;
        }

        @Override
        public int compareTo(ActivityItem another) {
            int result = title.toString().compareToIgnoreCase(another.title.toString());
            return result != 0 ? result : activityName.compareTo(another.activityName);
        }
    }

    public ActivitiesListAdapter(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
        mInflater = LayoutInflater.from(context);
        reloadList();
    }

    public void setPackageName(String pkgName) {
        mExcludedActivities.clear();
        mPackageName = pkgName;
        reloadList();
    }

    @Override
    public int getCount() {
        synchronized (mInstalledPackages) {
            return mInstalledPackages.size();
        }
    }

    @Override
    public ActivityItem getItem(int position) {
        synchronized (mInstalledPackages) {
            return mInstalledPackages.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        synchronized (mInstalledPackages) {
            // packageName is guaranteed to be unique in mInstalledPackages
            return mInstalledPackages.get(position).activityName.hashCode();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView != null) {
            holder = (ViewHolder) convertView.getTag();
        } else {
            convertView = mInflater.inflate(R.layout.applist_preference_icon, null, false);
            holder = new ViewHolder();
            convertView.setTag(holder);
            holder.title = convertView.findViewById(R.id.title);
            holder.summary = convertView.findViewById(R.id.summary);
            holder.icon = convertView.findViewById(R.id.icon);
            holder.divider = convertView.findViewById(R.id.divider);
        }

        ActivityItem applicationInfo = getItem(position);
        holder.title.setText(applicationInfo.title);
        holder.icon.setImageDrawable(applicationInfo.icon);
        holder.summary.setText(String.join("\n", mPackageName, applicationInfo.activityName));
        holder.summary.setVisibility(View.VISIBLE);
        holder.divider.setVisibility(position == getCount() - 1 ? View.GONE : View.VISIBLE);

        return convertView;
    }

    private void reloadList() {
        int generation = ++mLoadGeneration;
        mHandler.removeMessages(0);
        mInstalledPackages.clear();
        notifyDataSetChanged();
        String packageName = mPackageName;
        Set<String> excluded = new HashSet<>(mExcludedActivities);
        new Thread(() -> loadList(generation, packageName, excluded), "OC-ActivityList").start();
    }

    private void loadList(int generation, String packageName, Set<String> excluded) {
        List<ActivityItem> result = new java.util.ArrayList<>();
        if (TextUtils.isEmpty(packageName)) {
            mHandler.obtainMessage(0, generation, 0, result).sendToTarget();
            return;
        }

        try {
            ApplicationInfo appInfo = mPm.getApplicationInfo(packageName, 0);
            Drawable appIcon = mPm.getApplicationIcon(appInfo);
            PackageInfo info = mPm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            ActivityInfo[] list = info.activities;
            if (list == null) list = new ActivityInfo[0];
            for (ActivityInfo activityInfo : list) {
                if (excluded.contains(activityInfo.name) || excluded.contains(activityInfo.targetActivity)) {
                    continue;
                }
                String label = activityInfo.loadLabel(mPm).toString();
                if (label.isEmpty()) {
                    label = activityInfo.name;
                }
                final ActivityItem item = new ActivityItem(activityInfo.name,
                        label,
                        appIcon);
                int index = Collections.binarySearch(result, item);
                if (index < 0) result.add(-index - 1, item);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("ActivitiesListAdapter", "Package not found: " + packageName, e);
        }
        mHandler.obtainMessage(0, generation, 0, result).sendToTarget();
    }

    public void setExcludedActivities(HashSet<String> packages) {
        mExcludedActivities = new HashSet<>(packages);
        reloadList();
    }

    private static class ViewHolder {
        TextView title;
        TextView summary;
        ImageView icon;
        ImageView divider;
    }
}
