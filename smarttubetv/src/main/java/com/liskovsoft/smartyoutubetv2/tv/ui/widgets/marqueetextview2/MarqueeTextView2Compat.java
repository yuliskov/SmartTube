package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview2;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class MarqueeTextView2Compat extends MarqueeTextView2 {

    private static final int MARQUEE_START_DELAY_MS = 1000;
    private static final int MARQUEE_REPEAT_DELAY_MS = 1200;

    private boolean mScrolling;
    private boolean mAttached;
    private boolean mWindowFocused = true;
    private boolean mLaidOut;
    private boolean mPausedBetweenLoops;

    private final Runnable mStartMarqueeRunnable = () -> {
        if (!shouldMarquee() || mScrolling) return;
        startScroll(); // call parent method
        mScrolling = true;
    };

    private final Runnable mRepeatRunnable = () -> {
        mPausedBetweenLoops = false;
        if (shouldMarquee()) {
            startScroll();
            mScrolling = true;
        }
    };

    public MarqueeTextView2Compat(Context context) {
        super(context);
    }

    public MarqueeTextView2Compat(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean shouldMarquee() {
        if (!mAttached || !mWindowFocused || !mLaidOut || !isShown()) return false;
        if (!(isFocused() || isSelected())) return false;
        //return getTextViewWidth() > getWidth();
        return !isTextFullyVisible();
    }

    private void updateMarquee() {
        removeCallbacks(mStartMarqueeRunnable);
        removeCallbacks(mRepeatRunnable);

        if (shouldMarquee()) {
            postDelayed(mStartMarqueeRunnable, MARQUEE_START_DELAY_MS);
        } else {
            stopScroll(); // call parent's stopScroll
            mPausedBetweenLoops = false;
            mScrolling = false;
        }

        // Force call onMeasure between state change
        requestLayout();
        invalidate();
    }

    // --- Lifecycle hooks ---
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        updateMarquee();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;
        stopScroll();
        mPausedBetweenLoops = false;
        mScrolling = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        mWindowFocused = hasWindowFocus;
        updateMarquee();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        updateMarquee();
    }

    @Override
    public void setSelected(boolean selected) {
        boolean old = isSelected();
        super.setSelected(selected);
        if (old != selected) {
            updateMarquee();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mLaidOut = true;
        updateMarquee();
    }

    // Handle repeat delay for LTR/RTL
    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);

        int textWidth = getTextViewWidth();
        int loopWidth = textWidth + mSpace;

        // LTR
        if (!mPausedBetweenLoops && textWidth > getWidth() && getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            if (mLeftX < -loopWidth) {
                mPausedBetweenLoops = true;
                stopScroll(); // stop parent scroll
                postDelayed(mRepeatRunnable, MARQUEE_REPEAT_DELAY_MS);
            }
        }

        // RTL
        if (!mPausedBetweenLoops && textWidth > getWidth() && getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            if (mLeftX > loopWidth) {
                mPausedBetweenLoops = true;
                stopScroll(); // stop parent scroll
                postDelayed(mRepeatRunnable, MARQUEE_REPEAT_DELAY_MS);
            }
        }
    }
}