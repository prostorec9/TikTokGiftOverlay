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

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<GiftCardView> activeCards = new ArrayList<>();

    // Всё в пикселях — никаких dp чтобы избежать расхождений
    private final int cardWidthPx;
    private final int cardHeightPx;
    private static final int GAP_PX = 2;      // 2 пикселя между блоками
    private static final int MARGIN_TOP_PX = 2; // 2 пикселя от верха

    public GiftOverlayManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
        float d = context.getResources().getDisplayMetrics().density;
        cardWidthPx  = (int)(240 * d);
        cardHeightPx = (int)(54  * d);
    }

    public void showGift(GiftEvent gift) {
        mainHandler.post(() -> {
            // Карусель: удаляем старую если уже 10
            if (activeCards.size() >= MAX_VISIBLE) {
                GiftCardView oldest = activeCards.remove(0);
                try { windowManager.removeView(oldest); } catch (Exception ignored) {}
                repositionAll();
            }
            addCard(gift);
        });
    }

    private void addCard(GiftEvent gift) {
        GiftCardView card = new GiftCardView(context);
        int slot = activeCards.size();
        activeCards.add(card);

        try {
            windowManager.addView(card, buildParams(slot));
        } catch (Exception e) {
            activeCards.remove(card);
            return;
        }

        card.show(gift, () -> mainHandler.post(() -> {
            int idx = activeCards.indexOf(card);
            if (idx < 0) return;
            activeCards.remove(idx);
            try { windowManager.removeView(card); } catch (Exception ignored) {}
            repositionAll();
        }));
    }

    private void repositionAll() {
        for (int i = 0; i < activeCards.size(); i++) {
            try {
                windowManager.updateViewLayout(activeCards.get(i), buildParams(i));
            } catch (Exception ignored) {}
        }
    }

    private WindowManager.LayoutParams buildParams(int slot) {
        // Точная позиция в пикселях
        int yPx = MARGIN_TOP_PX + slot * (cardHeightPx + GAP_PX);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                cardWidthPx,
                cardHeightPx,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                // Убираем FLAG_NOT_FOCUSABLE — он блокирует touch на Android 12/13!
                // FLAG_NOT_TOUCH_MODAL — тач вне окна идёт в другие приложения
                // FLAG_ALT_FOCUSABLE_IM — не показываем клавиатуру
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 0;
        p.y = yPx;
        return p;
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
