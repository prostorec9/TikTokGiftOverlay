package com.tiktok.giftoverlay.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Автозапуск после перезагрузки телефона
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Проверяем, был ли сервис активен до перезагрузки
            SharedPreferences prefs = context.getSharedPreferences("gift_overlay", Context.MODE_PRIVATE);
            String username = prefs.getString("username", null);
            boolean wasActive = prefs.getBoolean("was_active", false);

            if (username != null && wasActive) {
                Intent serviceIntent = new Intent(context, GiftOverlayService.class);
                serviceIntent.setAction(GiftOverlayService.ACTION_START);
                serviceIntent.putExtra(GiftOverlayService.EXTRA_USERNAME, username);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
