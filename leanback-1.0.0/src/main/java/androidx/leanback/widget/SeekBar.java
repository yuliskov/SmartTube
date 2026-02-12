/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.leanback.R;

/**
 * Replacement of SeekBar, has two bar heights and two thumb size when focused/not_focused.
 * The widget does not deal with KeyEvent, it's client's responsibility to set a key listener.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public final class SeekBar extends View {

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public abstract static class AccessibilitySeekListener {
        /**
         * Called to perform AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
         */
        public abstract boolean onAccessibilitySeekForward();
        /**
         * Called to perform AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
         */
        public abstract boolean onAccessibilitySeekBackward();
    }

    private final RectF mProgressRect = new RectF();
    private final RectF mSecondProgressRect = new RectF();
    private final RectF mBackgroundRect = new RectF();
    private final Paint mSecondProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int mProgress;
    private int mSecondProgress;
    private int mMax;
    private int mKnobx;

    private int mActiveRadius;
    private int mBarHeight;
    private int mActiveBarHeight;

    private AccessibilitySeekListener mAccessibilitySeekListener;

    public SeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mBackgroundPaint.setColor(Color.GRAY);
        mSecondProgressPaint.setColor(Color.LTGRAY);
        mProgressPaint.setColor(Color.RED);
        mKnobPaint.setColor(Color.WHITE);
        mBarHeight = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_progressbar_bar_height);
        mActiveBarHeight = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_progressbar_active_bar_height);
        mActiveRadius = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_progressbar_active_radius);
    }

    /**
     * Set radius in pixels for thumb when SeekBar is focused.
     */
    public void setActiveRadius(int radius) {
        mActiveRadius = radius;
        calculate();
    }

    /**
     * Set horizontal bar height in pixels when SeekBar is not focused.
     */
    public void setBarHeight(int barHeight) {
        mBarHeight = barHeight;
        calculate();
    }

    /**
     * Set horizontal bar height in pixels when SeekBar is focused.
     */
    public void setActiveBarHeight(int activeBarHeight) {
        mActiveBarHeight = activeBarHeight;
        calculate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus,
            int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        calculate();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int radius = isFocused() ? mActiveRadius : mBarHeight / 2;
        canvas.drawRoundRect(mBackgroundRect, radius, radius, mBackgroundPaint);
        if (mSecondProgressRect.right > mSecondProgressRect.left) {
            canvas.drawRoundRect(mSecondProgressRect, radius, radius, mSecondProgressPaint);
        }
        canvas.drawRoundRect(mProgressRect, radius, radius, mProgressPaint);
        canvas.drawCircle(mKnobx, getHeight() / 2, radius, mKnobPaint);
    }

    /**
     * Set progress within 0 and {@link #getMax()}
     */
    public void setProgress(int progress) {
        if (progress > mMax) {
            progress = mMax;
        } else if (progress < 0) {
            progress = 0;
        }
        mProgress = progress;
        calculate();
    }

    /**
     * Set secondary progress within 0 and {@link #getMax()}
     */
    public void setSecondaryProgress(int progress) {
        if (progress > mMax) {
            progress = mMax;
        } else if (progress < 0) {
            progress = 0;
        }
        mSecondProgress = progress;
        calculate();
    }

    /**
     * Get progress within 0 and {@link #getMax()}
     */
    public int getProgress() {
        return mProgress;
    }

    /**
     * Get secondary progress within 0 and {@link #getMax()}
     */
    public int getSecondProgress() {
        return mSecondProgress;
    }

    /**
     * Get max value.
     */
    public int getMax() {
        return mMax;
    }

    /**
     * Set max value.
     */
    public void setMax(int max) {
        this.mMax = max;
        calculate();
    }

    /**
     * Set color for progress.
     */
    public void setProgressColor(int color) {
        mProgressPaint.setColor(color);
    }

    /**
     * Set color for second progress which is usually for buffering indication.
     */
    public void setSecondaryProgressColor(int color) {
        mSecondProgressPaint.setColor(color);
    }

    /**
     * Set color for second progress which is usually for buffering indication.
     */
    public int getSecondaryProgressColor() {
        return mSecondProgressPaint.getColor();
    }

    private void calculate() {
        final int barHeight = isFocused() ? mActiveBarHeight : mBarHeight;

        final int width = getWidth();
        final int height = getHeight();
        final int verticalPadding = (height - barHeight) / 2;

        mBackgroundRect.set(mBarHeight / 2, verticalPadding,
                width - mBarHeight / 2, height - verticalPadding);

        final int radius = isFocused() ? mActiveRadius : mBarHeight / 2;
        final int progressWidth = width - radius * 2;
        final float progressPixels = mProgress / (float) mMax * progressWidth;
        mProgressRect.set(mBarHeight / 2, verticalPadding, mBarHeight / 2 + progressPixels,
                height - verticalPadding);

        final float secondProgressPixels = mSecondProgress / (float) mMax * progressWidth;
        mSecondProgressRect.set(mProgressRect.right, verticalPadding,
                mBarHeight / 2 + secondProgressPixels, height - verticalPadding);

        mKnobx = radius + (int) progressPixels;
        invalidate();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.SeekBar.class.getName();
    }

    public void setAccessibilitySeekListener(AccessibilitySeekListener listener) {
        mAccessibilitySeekListener = listener;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (mAccessibilitySeekListener != null) {
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                    return mAccessibilitySeekListener.onAccessibilitySeekForward();
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                    return mAccessibilitySeekListener.onAccessibilitySeekBackward();
            }
        }
        return super.performAccessibilityAction(action, arguments);
    }

}
