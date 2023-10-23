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
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

class ControlBar extends LinearLayout {

    public interface OnChildFocusedListener {

        public void onChildFocusedListener(View child, View focused);
    }

    private int mChildMarginFromCenter;
    private OnChildFocusedListener mOnChildFocusedListener;
    // Can't set to static. Because we have two control bars.
    private int mLastFocusIndex = -1;
    // MOD: Maintain global focus index to seamless navigation between control rows.
    private static int mGlobalFocusIndex = -1;
    private boolean mDefaultFocusToMiddle = true;
    private boolean mFocusRecovery = true;
    private boolean mGlobalFocus = true;

    public ControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        resetFocus(); // MOD: reset global focus index
    }

    public ControlBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        resetFocus(); // MOD: reset global focus index
    }

    void setDefaultFocusToMiddle(boolean defaultFocusToMiddle) {
        mDefaultFocusToMiddle = defaultFocusToMiddle;
    }

    /**
     * MOD: enable/disable focus restoration
     */
    void setFocusRecovery(boolean focusRecovery) {
        mFocusRecovery = focusRecovery;
    }

    /**
     * MOD: global navigation
     */
    void setGlobalFocus(boolean globalFocus) {
        mGlobalFocus = globalFocus;
    }

    void resetFocus() {
        setFocusIndex(-1);
    }

    int getDefaultFocusIndex() {
        return mDefaultFocusToMiddle ? getChildCount() / 2 : 0;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (getChildCount() > 0) {
            // MOD: fix situation when rows have different length
            if (getFocusIndex() >= getChildCount()) {
                setFocusIndex(getChildCount() - 1);
            }

            int index = getFocusIndex() >= 0 && getFocusIndex() < getChildCount()
                    ? getFocusIndex() : getDefaultFocusIndex();

            if (getChildAt(index).requestFocus(direction, previouslyFocusedRect)) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if ((direction == ViewGroup.FOCUS_UP || direction == ViewGroup.FOCUS_DOWN)) {
            if (getFocusIndex() >= 0 && getFocusIndex() < getChildCount()) {
                views.add(getChildAt(getFocusIndex()));
            } else if (getChildCount() > 0) {
                views.add(getChildAt(getDefaultFocusIndex()));
            }
        } else {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    public void setOnChildFocusedListener(OnChildFocusedListener listener) {
        mOnChildFocusedListener = listener;
    }

    public void setChildMarginFromCenter(int marginFromCenter) {
        mChildMarginFromCenter = marginFromCenter;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (mFocusRecovery) {
            setFocusIndex(indexOfChild(child));
        }
        if (mOnChildFocusedListener != null) {
            mOnChildFocusedListener.onChildFocusedListener(child, focused);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mChildMarginFromCenter <= 0) {
            return;
        }

        int totalExtraMargin = 0;
        for (int i = 0; i < getChildCount() - 1; i++) {
            View first = getChildAt(i);
            View second = getChildAt(i+1);
            int measuredWidth = first.getMeasuredWidth() + second.getMeasuredWidth();
            int marginStart = mChildMarginFromCenter - measuredWidth / 2;
            LayoutParams lp = (LayoutParams) second.getLayoutParams();
            int extraMargin = marginStart - lp.getMarginStart();
            lp.setMarginStart(marginStart);
            second.setLayoutParams(lp);
            totalExtraMargin += extraMargin;
        }
        setMeasuredDimension(getMeasuredWidth() + totalExtraMargin, getMeasuredHeight());
    }

    private void setFocusIndex(int index) {
        mLastFocusIndex = index;
        mGlobalFocusIndex = index;
    }

    private int getFocusIndex() {
        return mGlobalFocus ? mGlobalFocusIndex : mLastFocusIndex;
    }
}
