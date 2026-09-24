package it.dhd.oxygencustomizer;

import android.app.Application;
import android.content.Context;

import com.google.android.material.color.DynamicColors;

import java.lang.ref.WeakReference;

import it.dhd.oxygencustomizer.utils.LocaleHelper;

public class OxygenCustomizer extends Application {

    private static OxygenCustomizer instance;
    private static WeakReference<Context> contextReference;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        instance = this;
        contextReference = new WeakReference<>(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        contextReference = new WeakReference<>(getApplicationContext());
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    public static Context getAppContext() {
        if (contextReference == null || contextReference.get() == null) {
            contextReference = new WeakReference<>(OxygenCustomizer.get().getApplicationContext());
        }
        return contextReference.get();
    }

    public static Context getAppContextLocale() {
        return LocaleHelper.setLocale(getAppContext());
    }

    public static OxygenCustomizer get() {
        if (instance == null) {
            throw new IllegalStateException("OxygenCustomizer Application is not attached yet");
        }
        return instance;
    }
}
