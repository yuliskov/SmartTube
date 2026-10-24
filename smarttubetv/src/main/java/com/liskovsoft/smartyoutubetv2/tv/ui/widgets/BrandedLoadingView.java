package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Startup artwork whose loading bar fills even when system animations are disabled. */
public final class BrandedLoadingView extends FrameLayout {
    private static final long FILL_DURATION_MS = 3000;
    private static final long FULL_BAR_HOLD_MS = 200;
    private static final long FADE_DURATION_MS = 500;
    private final ProgressBar mProgress;
    private long mShownAt;
    private long mFadeStartedAt;
    private Runnable mOnComplete;
    private boolean mCompletionPosted;

    private final Runnable mFade = new Runnable() {
        @Override
        public void run() {
            float fraction = Math.min(1f,
                    (SystemClock.uptimeMillis() - mFadeStartedAt) / (float) FADE_DURATION_MS);
            // Smoothly reveal the already loaded main screen beneath this overlay.
            float eased = fraction * fraction * (3f - 2f * fraction);
            setAlpha(1f - eased);
            if (fraction < 1f) {
                postOnAnimation(this);
            } else if (mOnComplete != null) {
                Runnable onComplete = mOnComplete;
                mOnComplete = null;
                onComplete.run();
            }
        }
    };

    private final Runnable mFinish = () -> {
        mFadeStartedAt = SystemClock.uptimeMillis();
        setLayerType(LAYER_TYPE_HARDWARE, null);
        postOnAnimation(mFade);
    };

    private final Runnable mFill = new Runnable() {
        @Override
        public void run() {
            long elapsed = SystemClock.uptimeMillis() - mShownAt;
            int progress = (int) Math.min(1000, elapsed * 1000 / FILL_DURATION_MS);
            // Update directly: ObjectAnimator skips to the end when the TV's animation scale is zero.
            mProgress.setProgress(progress);
            if (progress < 1000) {
                postOnAnimation(this);
            } else {
                finishWhenReady();
            }
        }
    };

    public BrandedLoadingView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.branded_loading_screen, this, true);
        mProgress = findViewById(R.id.loading_progress);
        // Blend the artwork's near-black backdrop into the exact app background color.
        ImageView artwork = findViewById(R.id.loading_brand_image);
        artwork.setColorFilter(ContextCompat.getColor(context, R.color.scheme_youtube_background),
                PorterDuff.Mode.LIGHTEN);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mShownAt = SystemClock.uptimeMillis();
        mCompletionPosted = false;
        setAlpha(1f);
        mProgress.setProgress(0);
        postOnAnimation(mFill);
    }

    public void complete(Runnable onComplete) {
        if (mOnComplete == null) {
            mOnComplete = onComplete;
            finishWhenReady();
        }
    }

    private void finishWhenReady() {
        if (mOnComplete != null && mProgress.getProgress() == 1000 && !mCompletionPosted) {
            mCompletionPosted = true;
            postDelayed(mFinish, FULL_BAR_HOLD_MS);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mFill);
        removeCallbacks(mFinish);
        removeCallbacks(mFade);
        setLayerType(LAYER_TYPE_NONE, null);
        mOnComplete = null;
        super.onDetachedFromWindow();
    }
}
