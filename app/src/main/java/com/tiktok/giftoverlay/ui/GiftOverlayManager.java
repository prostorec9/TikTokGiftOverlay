package com.tiktok.giftoverlay.ui;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
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

    private final List<GiftCardView> activeCards = new ArrayList<>();

    private final int cardWidthPx;
    private final int cardHeightPx;
    private final int gapPx;

    public GiftOverlayManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        this.cardWidthPx = dpToPx(CARD_WIDTH_DP);
        this.cardHeightPx = dpToPx(CARD_HEIGHT_DP);
        this.gapPx = dpToPx(GAP_DP);
    }

    public void showGift(GiftEvent gift) {
        mainHandler.post(() -> {
            if (activeCards.size() >= MAX_VISIBLE) {
                removeTopCard();
            }
            addNewCard(gift);
        });
    }

    private void removeTopCard() {
        if (activeCards.isEmpty()) return;
        GiftCardView oldest = activeCards.remove(0);
        try {
            windowManager.removeView(oldest);
        } catch (Exception ignored) {}
        
        repositionAllCards(); // Сдвигаем остальные карточки вверх
    }

    private void addNewCard(GiftEvent gift) {
        GiftCardView card = new GiftCardView(context);
        activeCards.add(card);

        int slot = activeCards.size() - 1;
        WindowManager.LayoutParams params = createLayoutParams(slot);

        // Нажатие для копирования текста
        card.setOnClickListener(v -> {
            String username = gift.username;
            if (username != null && !username.isEmpty()) {
                copyText(card, params, username);
            }
        });

        try {
            windowManager.addView(card, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add card", e);
            activeCards.remove(card);
            return;
        }

        card.show(gift, () -> mainHandler.post(() -> {
            int idx = activeCards.indexOf(card);
            if (idx >= 0) {
                activeCards.remove(idx);
                try { windowManager.removeView(card); } catch (Exception ignored) {}
                repositionAllCards(); // Сдвигаем карточки, если одна удалилась по таймеру
            }
        }));
    }

    // НОВЫЙ МЕТОД: Плавная анимация окон (без мертвых зон на экране)
    private void repositionAllCards() {
        int marginTopPx = dpToPx(MARGIN_TOP_DP);
        int totalCardHeight = cardHeightPx + gapPx;

        for (int i = 0; i < activeCards.size(); i++) {
            GiftCardView card = activeCards.get(i);
            int targetY = marginTopPx + i * totalCardHeight;

            WindowManager.LayoutParams params = (WindowManager.LayoutParams) card.getLayoutParams();
            if (params == null) continue;

            int startY = params.y;
            if (startY == targetY) continue; // Уже на нужном месте

            // Отменяем старую анимацию, если карточка уже ехала
            ValueAnimator oldAnimator = (ValueAnimator) card.getTag();
            if (oldAnimator != null) {
                oldAnimator.cancel();
            }

            // Создаем новую плавную анимацию сдвига вверх
            ValueAnimator animator = ValueAnimator.ofInt(startY, targetY);
            animator.setDuration(250); // Скорость скольжения (250 мс)
            animator.addUpdateListener(animation -> {
                params.y = (int) animation.getAnimatedValue();
                try {
                    windowManager.updateViewLayout(card, params);
                } catch (Exception ignored) {}
            });
            
            card.setTag(animator);
            animator.start();
        }
    }

    private void copyText(GiftCardView card, WindowManager.LayoutParams params, String text) {
        // Микро-перехват фокуса только для одной карточки
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { windowManager.updateViewLayout(card, params); } catch (Exception ignored) {}
        
        card.postDelayed(() -> {
            try {
                ClipData clip = ClipData.newPlainText("TikTok Username", text);
                clipboardManager.setPrimaryClip(clip);
                Toast.makeText(context, "Скопировано: " + text, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка буфера обмена", e);
            } finally {
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                try { windowManager.updateViewLayout(card, params); } catch (Exception ignored) {}
            }
        }, 100);
    }

    private WindowManager.LayoutParams createLayoutParams(int slot) {
        int marginTopPx = dpToPx(MARGIN_TOP_DP);
        int yOffset = marginTopPx + slot * (cardHeightPx + gapPx);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                cardWidthPx,
                cardHeightPx,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = yOffset;
        // Устанавливаем анимацию исчезновения/появления по умолчанию
        params.windowAnimations = android.R.style.Animation_Toast; 
        
        return params;
    }

    public void hideAll() {
        mainHandler.post(() -> {
            for (GiftCardView card : activeCards) {
                try {
                    card.cancelAndHide();
                    windowManager.removeView(card);
                } catch (Exception ignored) {}
            }
            activeCards.clear();
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
