package com.tiktok.giftoverlay.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.tiktok.giftoverlay.R;
import com.tiktok.giftoverlay.model.GiftEvent;

public class GiftCardView extends FrameLayout {

    private ImageView avatarImage;
    private ImageView giftImage;
    private TextView nicknameText;
    private TextView actionText;

    private static final long DISPLAY_DURATION_MS = 4000;
    private static final long ANIM_IN_MS = 400;
    private static final long ANIM_OUT_MS = 300;

    private Runnable hideRunnable;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

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
        setTranslationX(-2000f);
        setAlpha(0f);
    }

    public void show(GiftEvent gift, Runnable onHidden) {
        nicknameText.setText(gift.nickname);

        // Аватарка
        if (gift.avatarUrl != null && !gift.avatarUrl.isEmpty()) {
            Glide.with(getContext())
                .load(gift.avatarUrl)
                .apply(new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder))
                .into(avatarImage);
        } else {
            avatarImage.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        // Загружаем картинку подарка через прокси-сервер
if (gift.giftImageUrl != null && !gift.giftImageUrl.isEmpty()) {
    Glide.with(getContext())
        .load(gift.giftImageUrl)
        .apply(new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_gift_placeholder)
            .error(R.drawable.ic_gift_placeholder)
            .override(120, 120)
            .fitCenter())
        .into(giftImage);
} else {
    giftImage.setImageResource(R.drawable.ic_gift_placeholder);
}

        animateIn();

        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        hideRunnable = () -> animateOut(onHidden);
        handler.postDelayed(hideRunnable, DISPLAY_DURATION_MS);
    }

    private void animateIn() {
        setAlpha(1f);
        ObjectAnimator slideIn = ObjectAnimator.ofFloat(this, "translationX", -800f, 0f);
        slideIn.setDuration(ANIM_IN_MS);
        slideIn.setInterpolator(new OvershootInterpolator(0.8f));
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f);
        fadeIn.setDuration(200);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(slideIn, fadeIn);
        set.start();
    }

    private void animateOut(Runnable onDone) {
        ObjectAnimator slideOut = ObjectAnimator.ofFloat(this, "translationX", 0f, -900f);
        slideOut.setDuration(ANIM_OUT_MS);
        slideOut.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
        fadeOut.setDuration(ANIM_OUT_MS);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(slideOut, fadeOut);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onDone != null) onDone.run();
            }
        });
        set.start();
    }

    public void cancelAndHide() {
        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        setVisibility(GONE);
    }
}
