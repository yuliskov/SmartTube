/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.RestrictTo;

/**
 * Subclass of FrameLayout that support scale layout area size for children.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class ScaleFrameLayout extends FrameLayout {

    private static final int DEFAULT_CHILD_GRAVITY = Gravity.TOP | Gravity.START;

    private float mLayoutScaleX = 1f;
    private float mLayoutScaleY = 1f;

    private float mChildScale = 1f;

    public ScaleFrameLayout(Context context) {
        this(context ,null);
    }

    public ScaleFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScaleFrameLayout(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setLayoutScaleX(float scaleX) {
        if (scaleX != mLayoutScaleX) {
            mLayoutScaleX = scaleX;
            requestLayout();
        }
    }

    public void setLayoutScaleY(float scaleY) {
        if (scaleY != mLayoutScaleY) {
            mLayoutScaleY = scaleY;
            requestLayout();
        }
    }

    public void setChildScale(float scale) {
        if (mChildScale != scale) {
            mChildScale = scale;
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).setScaleX(scale);
                getChildAt(i).setScaleY(scale);
            }
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        child.setScaleX(mChildScale);
        child.setScaleY(mChildScale);
    }

    @Override
    protected boolean addViewInLayout (View child, int index, ViewGroup.LayoutParams params,
            boolean preventRequestLayout) {
        boolean ret = super.addViewInLayout(child, index, params, preventRequestLayout);
        if (ret) {
            child.setScaleX(mChildScale);
            child.setScaleY(mChildScale);
        }
        return ret;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();

        final int parentLeft, parentRight;
        final int layoutDirection = getLayoutDirection();
        final float pivotX = (layoutDirection == View.LAYOUT_DIRECTION_RTL)
                ? getWidth() - getPivotX()
                : getPivotX();
        if (mLayoutScaleX != 1f) {
            parentLeft = getPaddingLeft() + (int)(pivotX - pivotX / mLayoutScaleX + 0.5f);
            parentRight = (int)(pivotX + (right - left - pivotX) / mLayoutScaleX + 0.5f)
                    - getPaddingRight();
        } else {
            parentLeft = getPaddingLeft();
            parentRight = right - left - getPaddingRight();
        }

        final int parentTop, parentBottom;
        final float pivotY = getPivotY();
        if (mLayoutScaleY != 1f) {
            parentTop = getPaddingTop() + (int)(pivotY - pivotY / mLayoutScaleY + 0.5f);
            parentBottom = (int)(pivotY + (bottom - top - pivotY) / mLayoutScaleY + 0.5f)
                    - getPaddingBottom();
        } else {
            parentTop = getPaddingTop();
            parentBottom = bottom - top - getPaddingBottom();
        }

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = parentLeft + (parentRight - parentLeft - width) / 2
                                + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = parentRight - width - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = parentLeft + lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = parentTop + lp.topMargin;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = parentTop + (parentBottom - parentTop - height) / 2
                                + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = parentBottom - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = parentTop + lp.topMargin;
                }

                child.layout(childLeft, childTop, childLeft + width, childTop + height);
                // synchronize child pivot to be same as ScaleFrameLayout's pivot
                child.setPivotX(pivotX - childLeft);
                child.setPivotY(pivotY - childTop);
            }
        }
    }

    private static int getScaledMeasureSpec(int measureSpec, float scale) {
        return scale == 1f ? measureSpec : MeasureSpec.makeMeasureSpec(
                (int) (MeasureSpec.getSize(measureSpec) / scale + 0.5f),
                MeasureSpec.getMode(measureSpec));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mLayoutScaleX != 1f || mLayoutScaleY != 1f) {
            final int scaledWidthMeasureSpec =
                    getScaledMeasureSpec(widthMeasureSpec, mLayoutScaleX);
            final int scaledHeightMeasureSpec =
                    getScaledMeasureSpec(heightMeasureSpec, mLayoutScaleY);
            super.onMeasure(scaledWidthMeasureSpec, scaledHeightMeasureSpec);
            setMeasuredDimension((int)(getMeasuredWidth() * mLayoutScaleX + 0.5f),
                    (int)(getMeasuredHeight() * mLayoutScaleY + 0.5f));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * setForeground() is not supported,  throws UnsupportedOperationException() when called.
     */
    @Override
    public void setForeground(Drawable d) {
        throw new UnsupportedOperationException();
    }

}
