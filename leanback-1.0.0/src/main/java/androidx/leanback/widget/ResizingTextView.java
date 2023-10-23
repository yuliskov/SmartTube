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

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;
import androidx.leanback.R;

/**
 * <p>A {@link android.widget.TextView} that adjusts text size automatically in response
 * to certain trigger conditions, such as text that wraps over multiple lines.</p>
 */
class ResizingTextView extends TextView {

    /**
     * Trigger text resize when text flows into the last line of a multi-line text view.
     */
    public static final int TRIGGER_MAX_LINES = 0x01;

    private int mTriggerConditions; // Union of trigger conditions
    private int mResizedTextSize;
    // Note: Maintaining line spacing turned out not to be useful, and will be removed in
    // the next round of design for this class (b/18736630). For now it simply defaults to false.
    private boolean mMaintainLineSpacing;
    private int mResizedPaddingAdjustmentTop;
    private int mResizedPaddingAdjustmentBottom;

    private boolean mIsResized = false;
    // Remember default properties in case we need to restore them
    private boolean mDefaultsInitialized = false;
    private int mDefaultTextSize;
    private float mDefaultLineSpacingExtra;
    private int mDefaultPaddingTop;
    private int mDefaultPaddingBottom;

    public ResizingTextView(Context ctx, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(ctx, attrs, defStyleAttr);
        TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.lbResizingTextView,
                defStyleAttr, defStyleRes);

        try {
            mTriggerConditions = a.getInt(
                    R.styleable.lbResizingTextView_resizeTrigger, TRIGGER_MAX_LINES);
            mResizedTextSize = a.getDimensionPixelSize(
                    R.styleable.lbResizingTextView_resizedTextSize, -1);
            mMaintainLineSpacing = a.getBoolean(
                    R.styleable.lbResizingTextView_maintainLineSpacing, false);
            mResizedPaddingAdjustmentTop = a.getDimensionPixelOffset(
                    R.styleable.lbResizingTextView_resizedPaddingAdjustmentTop, 0);
            mResizedPaddingAdjustmentBottom = a.getDimensionPixelOffset(
                    R.styleable.lbResizingTextView_resizedPaddingAdjustmentBottom, 0);
        } finally {
            a.recycle();
        }
    }

    public ResizingTextView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        this(ctx, attrs, defStyleAttr, 0);
    }

    public ResizingTextView(Context ctx, AttributeSet attrs) {
        // TODO We should define our own style that inherits from TextViewStyle, to set defaults
        // for new styleables,  We then pass the appropriate R.attr up the constructor chain here.
        this(ctx, attrs, android.R.attr.textViewStyle);
    }

    public ResizingTextView(Context ctx) {
        this(ctx, null);
    }

    /**
     * @return the trigger conditions used to determine whether resize occurs
     */
    public int getTriggerConditions() {
        return mTriggerConditions;
    }

    /**
     * Set the trigger conditions used to determine whether resize occurs. Pass
     * a union of trigger condition constants, such as {@link ResizingTextView#TRIGGER_MAX_LINES}.
     *
     * @param conditions A union of trigger condition constants
     */
    public void setTriggerConditions(int conditions) {
        if (mTriggerConditions != conditions) {
            mTriggerConditions = conditions;
            // Always request a layout when trigger conditions change
            requestLayout();
        }
    }

    /**
     * @return the resized text size
     */
    public int getResizedTextSize() {
        return mResizedTextSize;
    }

    /**
     * Set the text size for resized text.
     *
     * @param size The text size for resized text
     */
    public void setResizedTextSize(int size) {
        if (mResizedTextSize != size) {
            mResizedTextSize = size;
            resizeParamsChanged();
        }
    }

    /**
     * @return whether or not to maintain line spacing when resizing text.
     * The default is true.
     */
    public boolean getMaintainLineSpacing() {
        return mMaintainLineSpacing;
    }

    /**
     * Set whether or not to maintain line spacing when resizing text.
     * The default is true.
     *
     * @param maintain Whether or not to maintain line spacing
     */
    public void setMaintainLineSpacing(boolean maintain) {
        if (mMaintainLineSpacing != maintain) {
            mMaintainLineSpacing = maintain;
            resizeParamsChanged();
        }
    }

    /**
     * @return desired adjustment to top padding for resized text
     */
    public int getResizedPaddingAdjustmentTop() {
        return mResizedPaddingAdjustmentTop;
    }

    /**
     * Set the desired adjustment to top padding for resized text.
     *
     * @param adjustment The adjustment to top padding, in pixels
     */
    public void setResizedPaddingAdjustmentTop(int adjustment) {
        if (mResizedPaddingAdjustmentTop != adjustment) {
            mResizedPaddingAdjustmentTop = adjustment;
            resizeParamsChanged();
        }
    }

    /**
     * @return desired adjustment to bottom padding for resized text
     */
    public int getResizedPaddingAdjustmentBottom() {
        return mResizedPaddingAdjustmentBottom;
    }

    /**
     * Set the desired adjustment to bottom padding for resized text.
     *
     * @param adjustment The adjustment to bottom padding, in pixels
     */
    public void setResizedPaddingAdjustmentBottom(int adjustment) {
        if (mResizedPaddingAdjustmentBottom != adjustment) {
            mResizedPaddingAdjustmentBottom = adjustment;
            resizeParamsChanged();
        }
    }

    private void resizeParamsChanged() {
        // If we're not resized, then changing resize parameters doesn't
        // affect layout, so don't bother requesting.
        if (mIsResized) {
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mDefaultsInitialized) {
            mDefaultTextSize = (int) getTextSize();
            mDefaultLineSpacingExtra = getLineSpacingExtra();
            mDefaultPaddingTop = getPaddingTop();
            mDefaultPaddingBottom = getPaddingBottom();
            mDefaultsInitialized = true;
        }

        // Always try first to measure with defaults. Otherwise, we may think we can get away
        // with larger text sizes later when we actually can't.
        setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize);
        setLineSpacing(mDefaultLineSpacingExtra, getLineSpacingMultiplier());
        setPaddingTopAndBottom(mDefaultPaddingTop, mDefaultPaddingBottom);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        boolean resizeText = false;

        final Layout layout = getLayout();
        if (layout != null) {
            if ((mTriggerConditions & TRIGGER_MAX_LINES) > 0) {
                final int lineCount = layout.getLineCount();
                final int maxLines = getMaxLines();
                if (maxLines > 1) {
                    resizeText = lineCount == maxLines;
                }
            }
        }

        final int currentSizePx = (int) getTextSize();
        boolean remeasure = false;
        if (resizeText) {
            if (mResizedTextSize != -1 && currentSizePx != mResizedTextSize) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, mResizedTextSize);
                remeasure = true;
            }
            // Check for other desired adjustments in addition to the text size
            final float targetLineSpacingExtra = mDefaultLineSpacingExtra
                    + mDefaultTextSize - mResizedTextSize;
            if (mMaintainLineSpacing && getLineSpacingExtra() != targetLineSpacingExtra) {
                setLineSpacing(targetLineSpacingExtra, getLineSpacingMultiplier());
                remeasure = true;
            }
            final int paddingTop = mDefaultPaddingTop + mResizedPaddingAdjustmentTop;
            final int paddingBottom = mDefaultPaddingBottom + mResizedPaddingAdjustmentBottom;
            if (getPaddingTop() != paddingTop || getPaddingBottom() != paddingBottom) {
                setPaddingTopAndBottom(paddingTop, paddingBottom);
                remeasure = true;
            }
        } else {
            // Use default size, line spacing, and padding
            if (mResizedTextSize != -1 && currentSizePx != mDefaultTextSize) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize);
                remeasure = true;
            }
            if (mMaintainLineSpacing && getLineSpacingExtra() != mDefaultLineSpacingExtra) {
                setLineSpacing(mDefaultLineSpacingExtra, getLineSpacingMultiplier());
                remeasure = true;
            }
            if (getPaddingTop() != mDefaultPaddingTop
                    || getPaddingBottom() != mDefaultPaddingBottom) {
                setPaddingTopAndBottom(mDefaultPaddingTop, mDefaultPaddingBottom);
                remeasure = true;
            }
        }
        mIsResized = resizeText;
        if (remeasure) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void setPaddingTopAndBottom(int paddingTop, int paddingBottom) {
        if (isPaddingRelative()) {
            setPaddingRelative(getPaddingStart(), paddingTop, getPaddingEnd(), paddingBottom);
        } else {
            setPadding(getPaddingLeft(), paddingTop, getPaddingRight(), paddingBottom);
        }
    }

    /**
     * See
     * {@link TextViewCompat#setCustomSelectionActionModeCallback(TextView, ActionMode.Callback)}
     */
    @Override
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        super.setCustomSelectionActionModeCallback(TextViewCompat
                .wrapCustomSelectionActionModeCallback(this, actionModeCallback));
    }
}
