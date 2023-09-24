/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * Implements foreground drawable before M and falls back to M's foreground implementation.
 */
class NonOverlappingLinearLayoutWithForeground extends LinearLayout {

    private static final int VERSION_M = 23;

    private Drawable mForeground;
    private boolean mForegroundBoundsChanged;
    private final Rect mSelfBounds = new Rect();

    public NonOverlappingLinearLayoutWithForeground(Context context) {
        this(context, null);
    }

    public NonOverlappingLinearLayoutWithForeground(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NonOverlappingLinearLayoutWithForeground(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        if (context.getApplicationInfo().targetSdkVersion >= VERSION_M
                && VERSION.SDK_INT >= VERSION_M) {
            // don't need do anything, base View constructor >=M already reads the foreground if
            // targetSDK is >= M.
        } else {
            // in other cases, including M but targetSDK is less than M, we need setForeground in
            // code.
            TypedArray a = context.obtainStyledAttributes(attrs,
                    new int[] { android.R.attr.foreground });
            Drawable d = a.getDrawable(0);
            if (d != null) {
                setForegroundCompat(d);
            }
            a.recycle();
        }
    }

    public void setForegroundCompat(Drawable d) {
        if (VERSION.SDK_INT >= VERSION_M) {
            // From M,  foreground is naturally supported.
            ForegroundHelper.setForeground(this, d);
        } else {
            // before M, do our own customized foreground draw.
            if (mForeground != d) {
                mForeground = d;
                mForegroundBoundsChanged = true;
                setWillNotDraw(false);
                mForeground.setCallback(this);
                if (mForeground.isStateful()) {
                    mForeground.setState(getDrawableState());
                }
            }
        }
    }

    public Drawable getForegroundCompat() {
        if (VERSION.SDK_INT >= VERSION_M) {
            return ForegroundHelper.getForeground(this);
        } else {
            return mForeground;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mForeground != null) {
            final Drawable foreground = mForeground;
            if (mForegroundBoundsChanged) {
                mForegroundBoundsChanged = false;
                final Rect selfBounds = mSelfBounds;
                final int w = getRight() - getLeft();
                final int h = getBottom() - getTop();
                selfBounds.set(0, 0, w, h);
                foreground.setBounds(selfBounds);
            }
            foreground.draw(canvas);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mForegroundBoundsChanged |= changed;
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (who == mForeground);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mForeground != null) {
            mForeground.jumpToCurrentState();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mForeground != null && mForeground.isStateful()) {
            mForeground.setState(getDrawableState());
        }
    }

    /**
     * Avoids creating a hardware layer when animating alpha.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}