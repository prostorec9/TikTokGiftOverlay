package com.tiktok.giftoverlay.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.tiktok.giftoverlay.MainActivity;
import com.tiktok.giftoverlay.R;
import com.tiktok.giftoverlay.model.GiftEvent;
import com.tiktok.giftoverlay.network.TikTokWebSocketClient;
import com.tiktok.giftoverlay.ui.GiftOverlayManager;

/**
 * Главный сервис — работает в фоне постоянно
 * Показывает overlay поверх любого приложения
 */
public class GiftOverlayService extends Service {

    private static final String TAG = "GiftOverlayService";
    public static final String CHANNEL_ID = "gift_overlay_channel";
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String EXTRA_USERNAME = "tiktok_username";

    private GiftOverlayManager overlayManager;
    private TikTokWebSocketClient wsClient;
    private PowerManager.WakeLock wakeLock;
    private String currentUsername;

    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // WakeLock — не даём системе убить сервис
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TikTokGiftOverlay::WakeLock");
        wakeLock.acquire();

        // Инициализируем overlay менеджер
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlayManager = new GiftOverlayManager(this, windowManager);

        isRunning = true;
        Log.i(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Восстановление после убийства системой
            restoreFromPrefs();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) action = ACTION_START;

        switch (action) {
            case ACTION_START:
                String username = intent.getStringExtra(EXTRA_USERNAME);
                if (username != null && !username.isEmpty()) {
                    saveToPrefs(username);
                    startOverlay(username);
                }
                break;

            case ACTION_STOP:
                stopSelf();
                break;
        }

        // START_STICKY = система перезапустит сервис если убьёт его
        return START_STICKY;
    }

    private void startOverlay(String username) {
        currentUsername = username;

        // Запускаем как foreground service с уведомлением
        startForeground(1, buildNotification("Подключение к @" + username + "..."));

        // Подключаемся к TikTok LIVE
        connectToTikTok(username);
    }

    private void connectToTikTok(String username) {
        // Отключаем старое подключение если было
        if (wsClient != null) wsClient.disconnect();

        wsClient = new TikTokWebSocketClient(new TikTokWebSocketClient.GiftCallback() {
            @Override
            public void onGiftReceived(GiftEvent gift) {
                Log.i(TAG, "Gift: " + gift.nickname + " sent " + gift.giftName);
                overlayManager.showGift(gift);
            }

            @Override
            public void onConnected(String uname) {
                Log.i(TAG, "Connected to @" + uname);
                updateNotification("🔴 LIVE @" + uname + " — подарки отображаются");
            }

            @Override
            public void onDisconnected() {
                Log.w(TAG, "Disconnected");
                updateNotification("Переподключение к @" + username + "...");
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error: " + message);
                updateNotification("⚠️ " + message);
            }
        });

        wsClient.connect(username);
    }

    private void restoreFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("gift_overlay", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        if (username != null) {
            startOverlay(username);
        }
    }

    private void saveToPrefs(String username) {
        getSharedPreferences("gift_overlay", MODE_PRIVATE)
                .edit()
                .putString("username", username)
                .apply();
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, GiftOverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TikTok Gift Overlay")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openPending)
                .addAction(R.drawable.ic_stop, "Стоп", stopPending)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(1, buildNotification(text));
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TikTok Gift Overlay",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Отображение подарков TikTok LIVE");
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

        if (wsClient != null) wsClient.disconnect();
        if (overlayManager != null) overlayManager.hideAll();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();

        Log.i(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
