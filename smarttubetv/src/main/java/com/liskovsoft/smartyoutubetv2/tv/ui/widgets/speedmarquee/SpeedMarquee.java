package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.speedmarquee;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

@SuppressLint("AppCompatCustomView")
public class SpeedMarquee extends TextView {
    private TypedArray typedArray;
    private final Scroller textScroller;
    private int mXPaused = 0;
    private boolean isPaused = true;
    private float mScrollSpeed = 2.0f;
    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;

    public SpeedMarquee(Context context) {
        this(context, null);
    }

    public SpeedMarquee(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public SpeedMarquee(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setSingleLine();
        setEllipsize(null);
        setVisibility(VISIBLE);

        if (attrs != null) {
            typedArray = context.obtainStyledAttributes(attrs, R.styleable.SpeedMarquee);
            mScrollSpeed = typedArray.getFloat(R.styleable.SpeedMarquee_marquee_speed, 2.0f);
            typedArray.recycle();
        }

        onGlobalLayoutListener = this::startScroll;
        getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
        textScroller = new Scroller(getContext(), new LinearInterpolator());
    }

    @Override
    protected void onDetachedFromWindow() {
        removeGlobalListener();
        super.onDetachedFromWindow();
    }

    public void startScroll() {
        boolean needsScrolling = checkIfNeedsScrolling();
        mXPaused = -1 * (getWidth() / 2);
        isPaused = true;
        if (needsScrolling) {
            resumeScroll();
        } else {
            pauseScroll();
        }
        removeGlobalListener();
    }

    private void startScrollAfterUpdate(int currX) {
        boolean needsScrolling = checkIfNeedsScrolling();
        mXPaused = currX;
        isPaused = true;
        if (needsScrolling) {
            resumeScroll();
        } else {
            pauseScroll();
        }
        removeGlobalListener();
    }

    private synchronized void removeGlobalListener() {
        try {
            if (onGlobalLayoutListener != null) {
                getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
                onGlobalLayoutListener = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkIfNeedsScrolling() {
        measure(0, 0);
        int textViewWidth = getWidth();
        if (textViewWidth == 0) return false;
        float textWidth = getTextLength();
        return textWidth > textViewWidth;
    }

    public void resumeScroll() {
        if (!isPaused) return;
        setHorizontallyScrolling(true);
        setScroller(textScroller);
        int scrollingLen = calculateScrollingLen();
        int distance = scrollingLen - (getWidth() + mXPaused);
        int duration = (int) (10f * distance / mScrollSpeed);
        setVisibility(VISIBLE);
        textScroller.startScroll(mXPaused, 0, distance, 0, duration);
        invalidate();
        isPaused = false;
    }

    private int calculateScrollingLen() {
        return getTextLength() + getWidth();
    }

    private int getTextLength() {
        Rect rect = new Rect();
        getPaint().getTextBounds(getText().toString(), 0, getText().length(), rect);
        return rect.width();
    }

    public void pauseScroll() {
        if (textScroller == null || isPaused) return;
        isPaused = true;
        mXPaused = textScroller.getCurrX();
        textScroller.abortAnimation();
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (textScroller != null && textScroller.isFinished() && !isPaused) {
            startScroll();
        }
    }

    public void setSpeed(float value) {
        mScrollSpeed = value;
        startScrollAfterUpdate(textScroller.getCurrX());
    }

    public float getSpeed() {
        return mScrollSpeed;
    }
}

