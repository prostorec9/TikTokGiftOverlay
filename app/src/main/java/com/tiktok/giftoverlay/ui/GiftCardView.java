package com.tiktok.giftoverlay.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.tiktok.giftoverlay.R;
import com.tiktok.giftoverlay.model.GiftEvent;

public class GiftCardView extends FrameLayout {

    private ImageView avatarImage;
    private ImageView giftImage;
    private TextView nicknameText;
    private TextView actionText;
    private String currentUsername = "";
    private boolean isTouching = false;

    private static final long DISPLAY_DURATION_MS = 5000;
    private static final long ANIM_IN_MS = 350;
    private static final long ANIM_OUT_MS = 300;

    private Runnable hideRunnable;
    private Handler handler = new Handler(Looper.getMainLooper());

    public GiftCardView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_gift_card, this, true);
        avatarImage  = findViewById(R.id.avatar_image);
        giftImage    = findViewById(R.id.gift_image);
        nicknameText = findViewById(R.id.nickname_text);
        actionText   = findViewById(R.id.action_text);

        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Визуальный фидбек при нажатии
                    setAlpha(0.7f);
                    break;

                case MotionEvent.ACTION_UP:
                    setAlpha(1.0f);
                    // Копируем username в буфер обмена
                    if (!currentUsername.isEmpty()) {
                        try {
                            ClipboardManager clipboard = (ClipboardManager)
                                context.getApplicationContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                ClipData clip = ClipData.newPlainText(
                                    "tiktok_username", "@" + currentUsername);
                                clipboard.setPrimaryClip(clip);
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                        // Мигание фоном как фидбек (Toast не работает из Service)
                        setBackgroundColor(Color.parseColor("#44FFFFFF"));
                        handler.postDelayed(() ->
                            setBackground(getContext().getResources()
                                .getDrawable(R.drawable.bg_gift_card, null)), 300);
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    setAlpha(1.0f);
                    break;
            }
            return true;
        });

        setTranslationX(-2000f);
        setAlpha(0f);
    }

    public void show(GiftEvent gift, Runnable onHidden) {
        currentUsername = gift.username != null ? gift.username : "";
        nicknameText.setText(gift.nickname);
        actionText.setText("sent " + gift.giftName);

        loadImage(gift.avatarUrl, avatarImage, true);
        loadImage(gift.giftImageUrl, giftImage, false);

        animateIn();

        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        hideRunnable = () -> animateOut(onHidden);
        handler.postDelayed(hideRunnable, DISPLAY_DURATION_MS);
    }

    private void loadImage(String url, ImageView imageView, boolean isAvatar) {
        if (url == null || url.isEmpty()) {
            imageView.setImageResource(isAvatar
                ? R.drawable.ic_avatar_placeholder
                : R.drawable.ic_gift_placeholder);
            return;
        }

        GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
            .addHeader("Referer", "https://www.tiktok.com/")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build());

        RequestOptions opts = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(isAvatar ? R.drawable.ic_avatar_placeholder : R.drawable.ic_gift_placeholder)
            .error(isAvatar ? R.drawable.ic_avatar_placeholder : R.drawable.ic_gift_placeholder);

        if (isAvatar) opts = opts.circleCrop();
        else opts = opts.fitCenter().override(80, 80);

        Glide.with(getContext()).load(glideUrl).apply(opts).into(imageView);
    }

    private void animateIn() {
        setAlpha(1f);
        ObjectAnimator slideIn = ObjectAnimator.ofFloat(this, "translationX", -600f, 0f);
        slideIn.setDuration(ANIM_IN_MS);
        slideIn.setInterpolator(new OvershootInterpolator(0.6f));
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f);
        fadeIn.setDuration(200);
        AnimatorSet setIn = new AnimatorSet();
        setIn.playTogether(slideIn, fadeIn);
        setIn.start();
    }

    private void animateOut(Runnable onDone) {
        ObjectAnimator slideOut = ObjectAnimator.ofFloat(this, "translationX", 0f, -700f);
        slideOut.setDuration(ANIM_OUT_MS);
        slideOut.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
        fadeOut.setDuration(ANIM_OUT_MS);
        AnimatorSet setOut = new AnimatorSet();
        setOut.playTogether(slideOut, fadeOut);
        setOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onDone != null) onDone.run();
            }
        });
        setOut.start();
    }

    public void cancelAndHide() {
        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        setVisibility(GONE);
    }
}
