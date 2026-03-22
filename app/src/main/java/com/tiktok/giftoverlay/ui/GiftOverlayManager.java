package com.tiktok.giftoverlay.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import com.tiktok.giftoverlay.model.GiftEvent;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Управляет очередью подарков
 * Показывает их один под другим слева на экране
 * Максимум 3 одновременно
 */
public class GiftOverlayManager {

    private static final int MAX_VISIBLE = 3;
    private static final int CARD_MARGIN_TOP_DP = 20;
    private static final int CARD_HEIGHT_DP = 72;
    private static final int CARD_SPACING_DP = 8;

    private Context context;
    private WindowManager windowManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Текущие показанные карточки
    private GiftCardView[] activeCards = new GiftCardView[MAX_VISIBLE];
    private boolean[] slotOccupied = new boolean[MAX_VISIBLE];

    // Очередь ожидающих подарков
    private Queue<GiftEvent> pendingQueue = new LinkedList<>();

    public GiftOverlayManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    /**
     * Добавить подарок в очередь для показа
     */
    public void showGift(GiftEvent gift) {
        mainHandler.post(() -> {
            int freeSlot = getFreeSlot();
            if (freeSlot >= 0) {
                displayGiftInSlot(gift, freeSlot);
            } else {
                // Все слоты заняты — ставим в очередь
                pendingQueue.offer(gift);
            }
        });
    }

    private int getFreeSlot() {
        for (int i = 0; i < MAX_VISIBLE; i++) {
            if (!slotOccupied[i]) return i;
        }
        return -1;
    }

    private void displayGiftInSlot(GiftEvent gift, int slot) {
        slotOccupied[slot] = true;

        // Создаём карточку
        GiftCardView card = new GiftCardView(context);
        activeCards[slot] = card;

        // Параметры позиции на экране
        WindowManager.LayoutParams params = createLayoutParams(slot);

        try {
            windowManager.addView(card, params);
        } catch (Exception e) {
            slotOccupied[slot] = false;
            return;
        }

        // Показываем с анимацией
        card.show(gift, () -> {
            // Карточка исчезла — освобождаем слот
            mainHandler.post(() -> {
                try {
                    windowManager.removeView(card);
                } catch (Exception ignored) {}
                activeCards[slot] = null;
                slotOccupied[slot] = false;

                // Берём следующий из очереди
                if (!pendingQueue.isEmpty()) {
                    GiftEvent next = pendingQueue.poll();
                    if (next != null) displayGiftInSlot(next, slot);
                }
            });
        });
    }

    private WindowManager.LayoutParams createLayoutParams(int slot) {
    float density = context.getResources().getDisplayMetrics().density;

    int cardWidth  = (int)(380 * density);
    int cardHeight = (int)(90  * density);
    int spacing    = (int)(10  * density);
    int marginTop  = (int)(24  * density);

    int yOffset = marginTop + (slot * (cardHeight + spacing));

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            cardWidth,
            cardHeight,
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
    );

    params.gravity = Gravity.TOP | Gravity.START;
    params.x = 0;
    params.y = yOffset;
    return params;
}

    /**
     * Убрать все карточки с экрана
     */
    public void hideAll() {
        mainHandler.post(() -> {
            pendingQueue.clear();
            for (int i = 0; i < MAX_VISIBLE; i++) {
                if (activeCards[i] != null) {
                    try {
                        activeCards[i].cancelAndHide();
                        windowManager.removeView(activeCards[i]);
                    } catch (Exception ignored) {}
                    activeCards[i] = null;
                    slotOccupied[i] = false;
                }
            }
        });
    }
}
