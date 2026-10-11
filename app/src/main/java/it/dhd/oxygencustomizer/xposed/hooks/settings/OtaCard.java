package it.dhd.oxygencustomizer.xposed.hooks.settings;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SETTINGS;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;
import static it.dhd.oxygencustomizer.xposed.utils.ViewHelper.dp2px;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.utils.toolkit.ReflectedClass;

public class OtaCard extends XposedMods {

    private static final String listenPackage = SETTINGS;

    private boolean mCustomStyle = false;
    private static final ScheduledExecutorService IMAGE_WORKER =
            Executors.newSingleThreadScheduledExecutor(work -> {
                Thread thread = new Thread(work, "OC-OtaCardImage");
                thread.setDaemon(true);
                return thread;
            });
    private ScheduledFuture<?> currentImageTask;

    public OtaCard(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        mCustomStyle = Xprefs.getBoolean("custom_ota_card", true);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {


        ReflectedClass AboutDeviceOtaUpdatePreference = ReflectedClass.of("com.oplus.settings.widget.preference.AboutDeviceOtaUpdatePreference");
        AboutDeviceOtaUpdatePreference
                .after("onBindViewHolder")
                .run(param -> {
                    if (!mCustomStyle) return;
                    Object preferenceHolder = param.args[0];
                    View iView = (View) getObjectField(preferenceHolder, "itemView");

                    if (iView == null) return;
                    try {
                        ImageView iv = iView.findViewById(mContext.getResources().getIdentifier("about_device_top_bg", "id", SETTINGS));
                        if (iv != null) iv.setVisibility(View.GONE);
                    } catch (Throwable t) {
                        log(t);
                    }
                    setCustomImage(iView);
                });
    }

    private synchronized void setCustomImage(View target) {
        if (currentImageTask != null) currentImageTask.cancel(false);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> ownTask = new AtomicReference<>();
        Runnable load = () -> {
            int attempt = attempts.incrementAndGet();
            boolean finished = attempt >= 12;
            try {
                File androidDir = new File(Environment.getExternalStorageDirectory(), "Android");
                File imageFile = new File(Environment.getExternalStorageDirectory(),
                        ".oxygen_customizer/settings_ota_card.png");
                if (androidDir.isDirectory() && imageFile.isFile()) {
                    ImageDecoder.Source source = ImageDecoder.createSource(imageFile);
                    RoundedBitmapDrawable image = RoundedBitmapDrawableFactory.create(
                            mContext.getResources(), ImageDecoder.decodeBitmap(source));
                    image.setCornerRadius(dp2px(mContext, 12));
                    target.post(() -> {
                        if (target.isAttachedToWindow()) target.setBackground(image);
                    });
                    finished = true;
                }
            } catch (Throwable failure) {
                log(failure);
            } finally {
                if (finished) {
                    ScheduledFuture<?> future = ownTask.get();
                    if (future != null) future.cancel(false);
                }
            }
        };
        ownTask.set(IMAGE_WORKER.scheduleWithFixedDelay(load, 0, 5, TimeUnit.SECONDS));
        currentImageTask = ownTask.get();
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
