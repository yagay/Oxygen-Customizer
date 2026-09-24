package it.dhd.oxygencustomizer.ui.activity;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.topjohnwu.superuser.Shell;

import it.dhd.oxygencustomizer.utils.Prefs;

public class ShortcutActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        handleShortcutIntent(getIntent());
    }

    @Override
    public void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleShortcutIntent(intent);
    }

    private void handleShortcutIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            finishAndRemoveTask();
            return;
        }

        String shortcutId = intent.getStringExtra("shortcutId");
        String shortcutToken = intent.getStringExtra("shortcutToken");
        String expectedToken = Prefs.getString("launcher_shortcut_token", "");

        if (!"shortcut_1".equals(shortcutId)
                || expectedToken.isEmpty()
                || !expectedToken.equals(shortcutToken)) {
            Log.w("ShortcutActivity", "Rejected unauthenticated shortcut launch");
            finishAndRemoveTask();
            return;
        }

        Log.d("ShortcutActivity", "Launching authenticated shortcut: " + shortcutId);
        Shell.cmd("am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android").exec();
        finishAndRemoveTask();
    }

}
