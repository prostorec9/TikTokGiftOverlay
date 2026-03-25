package com.tiktok.giftoverlay.ui;

import android.animation.LayoutTransition;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.tiktok.giftoverlay.model.GiftEvent;

import java.util.ArrayList;
import java.util.List;

public class GiftOverlayManager {

    private static final String TAG = "GiftOverlayManager";
    private static final int MAX_VISIBLE = 10;
    private static final int CARD_HEIGHT_DP = 54;
    private static final int CARD_WIDTH_DP = 240;
    private static final int GAP_DP = 2;
    private static final int MARGIN_TOP_DP = 2;

    private final Context context;
    private final WindowManager windowManager;
    private final ClipboardManager clipboardManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Контейнер, который держит все карточки
    private LinearLayout containerLayout;
    // Размеры в пикселях (рассчитаны заранее)
    private final int cardWidthPx;
    private final int cardHeightPx;
    private final int gapPx;

    public GiftOverlayManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        // 1. Предварительный расчет размеров в пикселях
        this.cardWidthPx = dpToPx(CARD_WIDTH_DP);
        this.cardHeightPx = dpToPx(CARD_HEIGHT_DP);
        this.gapPx = dpToPx(GAP_DP);

        // 2. Инициализация главного контейнера оверлея
        initContainer();
    }

    private void initContainer() {
        mainHandler.post(() -> {
            containerLayout = new LinearLayout(context);
            containerLayout.setOrientation(LinearLayout.VERTICAL);
            // ВКЛЮЧАЕМ ПЛАВНУЮ АНИМАЦИЮ при добавлении/удалении детей
            containerLayout.setLayoutTransition(new LayoutTransition());

            WindowManager.LayoutParams params = createContainerLayoutParams();
            try {
                windowManager.addView(containerLayout, params);
            } catch (Exception e) {
                Log.e(TAG, "Failed to add main container to WindowManager", e);
            }
        });
    }

    public void showGift(GiftEvent gift) {
        mainHandler.post(() -> {
            if (containerLayout == null) return;

            // Если достигнут лимит — удаляем самую старую (первую в списке)
            if (containerLayout.getChildCount() >= MAX_VISIBLE) {
                // Анимация LayoutTransition сработает автоматически
                containerLayout.removeViewAt(0);
            }
            addNewCard(gift);
        });
    }

    private void addNewCard(GiftEvent gift) {
        // Предполагаем, что GiftCardView наследует View (напр. FrameLayout)
        GiftCardView card = new GiftCardView(context);

        // Параметры для размещения внутри LinearLayout контейнера
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
        layoutParams.bottomMargin = gapPx; // Отступ между карточками

        // --- РЕАЛИЗАЦИЯ КОПИРОВАНИЯ ---
        card.setOnClickListener(v -> {
            String username = gift.getUsername(); // Убедитесь, что в GiftEvent есть этот метод
            if (username != null && !username.isEmpty()) {
                ClipData clip = ClipData.newPlainText("TikTok Username", username);
                clipboardManager.setPrimaryClip(clip);
                // Показываем Toast для UX
                Toast.makeText(context, "Username " + username + " скопирован", Toast.LENGTH_SHORT).show();
            }
        });

        containerLayout.addView(card, layoutParams);

        // Запускаем логику самой карточки (загрузка аватара, имя подарка)
        // Также передаем callback для самоудаления по таймеру
        card.show(gift, () -> mainHandler.post(() -> {
            if (containerLayout != null) {
                // Плавное удаление, остальные карточки сами сдвинутся вверх
                containerLayout.removeView(card);
            }
        }));
    }

    public void hideAll() {
        mainHandler.post(() -> {
            if (containerLayout != null) {
                // Сначала отменяем анимации на самих карточках, если они есть
                for (int i = 0; i < containerLayout.getChildCount(); i++) {
                    View v = containerLayout.getChildAt(i);
                    if (v instanceof GiftCardView) {
                        ((GiftCardView) v).cancelAndHide();
                    }
                }
                // Удаляем контейнер целиком
                try {
                    windowManager.removeView(containerLayout);
                } catch (Exception e) {
                    Log.e(TAG, "Error removing container", e);
                }
                containerLayout = null;
            }
        });
    }

    // Создает параметры для одного большого контейнера
    private WindowManager.LayoutParams createContainerLayoutParams() {
        int marginTopPx = dpToPx(MARGIN_TOP_DP);

        // Рассчитываем общую максимальную высоту контейнера
        int totalHeightPx = (cardHeightPx + gapPx) * MAX_VISIBLE + marginTopPx;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                cardWidthPx,
                totalHeightPx,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                // ИСПРАВЛЕНО: Добавлен FLAG_NOT_FOCUSABLE
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = marginTopPx;
        return params;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
