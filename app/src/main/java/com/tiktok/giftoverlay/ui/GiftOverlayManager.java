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
    // Размеры в пикселях
    private final int cardWidthPx;
    private final int cardHeightPx;
    private final int gapPx;

    public GiftOverlayManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        // Предварительный расчет размеров
        this.cardWidthPx = dpToPx(CARD_WIDTH_DP);
        this.cardHeightPx = dpToPx(CARD_HEIGHT_DP);
        this.gapPx = dpToPx(GAP_DP);

        initContainer();
    }

    private void initContainer() {
        mainHandler.post(() -> {
            containerLayout = new LinearLayout(context);
            containerLayout.setOrientation(LinearLayout.VERTICAL);
            // Плавная анимация при добавлении/удалении
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

            // Если лимит — удаляем самую старую
            if (containerLayout.getChildCount() >= MAX_VISIBLE) {
                containerLayout.removeViewAt(0);
            }
            addNewCard(gift);
        });
    }

    private void addNewCard(GiftEvent gift) {
        GiftCardView card = new GiftCardView(context);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
        layoutParams.bottomMargin = gapPx;

        // Копирование username (теперь берем публичную переменную .username напрямую)
        card.setOnClickListener(v -> {
            String username = gift.username; 
            if (username != null && !username.isEmpty()) {
                ClipData clip = ClipData.newPlainText("TikTok Username", username);
                clipboardManager.setPrimaryClip(clip);
                Toast.makeText(context, "Username " + username + " скопирован", Toast.LENGTH_SHORT).show();
            }
        });

        containerLayout.addView(card, layoutParams);

        // Показ карточки и коллбек на самоудаление
        card.show(gift, () -> mainHandler.post(() -> {
            if (containerLayout != null) {
                containerLayout.removeView(card);
            }
        }));
    }

    public void hideAll() {
        mainHandler.post(() -> {
            if (containerLayout != null) {
                for (int i = 0; i < containerLayout.getChildCount(); i++) {
                    View v = containerLayout.getChildAt(i);
                    if (v instanceof GiftCardView) {
                        ((GiftCardView) v).cancelAndHide();
                    }
                }
                try {
                    windowManager.removeView(containerLayout);
                } catch (Exception e) {
                    Log.e(TAG, "Error removing container", e);
                }
                containerLayout = null;
            }
        });
    }

    private WindowManager.LayoutParams createContainerLayoutParams() {
        int marginTopPx = dpToPx(MARGIN_TOP_DP);
        int totalHeightPx = (cardHeightPx + gapPx) * MAX_VISIBLE + marginTopPx;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                cardWidthPx,
                totalHeightPx,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
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
