package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class MarqueeTextViewCompat extends MarqueeTextViewCompatBase {
    private boolean mAttached;
    private boolean mWindowFocused = true;
    private boolean mLaidOut;

    public MarqueeTextViewCompat(Context context) {
        super(context);
    }

    public MarqueeTextViewCompat(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean shouldMarquee() {
        if (!mAttached || !mWindowFocused || !mLaidOut || !isShown()) return false;
        if (!(isFocused() || isSelected())) return false;
        return !isTextFullyVisible();
    }

    private void updateMarquee() {
        if (!shouldMarquee()) {
            stopScroll();
        }

        // Force call onMeasure after focus change
        requestLayout();
    }
    
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
}