package com.tiktok.giftoverlay.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import com.tiktok.giftoverlay.model.GiftEvent;
import java.util.ArrayList;
import java.util.List;

public class GiftOverlayManager {

    private static final int MAX_VISIBLE = 10;
    private static final int CARD_HEIGHT_DP = 54;
    private static final int GAP_DP = 2;
    private static final int MARGIN_TOP_DP = 2;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Список активных карточек — карусель
    private final List<GiftCardView> activeCards = new ArrayList<>();

    public GiftOverlayManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    public void showGift(GiftEvent gift) {
        mainHandler.post(() -> {
            // Если уже 10 карточек — убираем самую верхнюю (старую)
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
        // Сдвигаем оставшиеся карточки вверх
        repositionAllCards();
    }

    private void addNewCard(GiftEvent gift) {
        GiftCardView card = new GiftCardView(context);
        activeCards.add(card);

        int slot = activeCards.size() - 1;
        WindowManager.LayoutParams params = createLayoutParams(slot);

        try {
            windowManager.addView(card, params);
        } catch (Exception e) {
            activeCards.remove(card);
            return;
        }

        card.show(gift, () -> mainHandler.post(() -> {
            int idx = activeCards.indexOf(card);
            if (idx >= 0) {
                activeCards.remove(idx);
                try { windowManager.removeView(card); } catch (Exception ignored) {}
                // Сдвигаем карточки выше занятого места вниз
                repositionAllCards();
            }
        }));
    }

    private void repositionAllCards() {
        float density = context.getResources().getDisplayMetrics().density;
        int cardHeight = (int)(CARD_HEIGHT_DP * density);
        int gap        = (int)(GAP_DP * density);
        int marginTop  = (int)(MARGIN_TOP_DP * density);

        for (int i = 0; i < activeCards.size(); i++) {
            GiftCardView card = activeCards.get(i);
            int yOffset = marginTop + i * (cardHeight + gap);
            try {
                WindowManager.LayoutParams params = createLayoutParams(i);
                windowManager.updateViewLayout(card, params);
            } catch (Exception ignored) {}
        }
    }

    private WindowManager.LayoutParams createLayoutParams(int slot) {
        float density = context.getResources().getDisplayMetrics().density;

        int cardWidth  = (int)(240 * density);
        int cardHeight = (int)(CARD_HEIGHT_DP * density);
        int gap        = (int)(GAP_DP * density);
        int marginTop  = (int)(MARGIN_TOP_DP * density);

        int yOffset = marginTop + slot * (cardHeight + gap);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                cardWidth,
                cardHeight,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = yOffset;
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
}
