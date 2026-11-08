package id.rk.aserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        boolean autoStart = AppPrefs.get(context)
                .getBoolean(AppPrefs.KEY_AUTO_START, true);
        boolean wasActive = AppPrefs.get(context)
                .getBoolean(AppPrefs.KEY_ACTIVE, false);
        if (!autoStart || !wasActive) return;

        try {
            Intent serviceIntent = new Intent(context, GuardService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
            // Android/vendor power policies may block automatic restart.
            // The user can reopen ASERVER and start protection manually.
        }
    }
}
