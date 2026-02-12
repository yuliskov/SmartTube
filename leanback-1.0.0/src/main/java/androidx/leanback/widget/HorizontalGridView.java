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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.leanback.R;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A {@link android.view.ViewGroup} that shows items in a horizontal scrolling list. The items come from
 * the {@link RecyclerView.Adapter} associated with this view.
 * <p>
 * {@link RecyclerView.Adapter} can optionally implement {@link FacetProviderAdapter} which
 * provides {@link FacetProvider} for a given view type;  {@link RecyclerView.ViewHolder}
 * can also implement {@link FacetProvider}.  Facet from ViewHolder
 * has a higher priority than the one from FacetProviderAdapter associated with viewType.
 * Supported optional facets are:
 * <ol>
 * <li> {@link ItemAlignmentFacet}
 * When this facet is provided by ViewHolder or FacetProviderAdapter,  it will
 * override the item alignment settings set on HorizontalGridView.  This facet also allows multiple
 * alignment positions within one ViewHolder.
 * </li>
 * </ol>
 */
public class HorizontalGridView extends BaseGridView {

    private boolean mFadingLowEdge;
    private boolean mFadingHighEdge;

    private Paint mTempPaint = new Paint();
    private Bitmap mTempBitmapLow;
    private LinearGradient mLowFadeShader;
    private int mLowFadeShaderLength;
    private int mLowFadeShaderOffset;
    private Bitmap mTempBitmapHigh;
    private LinearGradient mHighFadeShader;
    private int mHighFadeShaderLength;
    private int mHighFadeShaderOffset;
    private Rect mTempRect = new Rect();

    public HorizontalGridView(Context context) {
        this(context, null);
    }

    public HorizontalGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        initAttributes(context, attrs);
    }

    protected void initAttributes(Context context, AttributeSet attrs) {
        initBaseGridViewAttributes(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbHorizontalGridView);
        setRowHeight(a);
        setNumRows(a.getInt(R.styleable.lbHorizontalGridView_numberOfRows, 1));
        a.recycle();
        updateLayerType();
        mTempPaint = new Paint();
        mTempPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void setRowHeight(TypedArray array) {
        TypedValue typedValue = array.peekValue(R.styleable.lbHorizontalGridView_rowHeight);
        if (typedValue != null) {
            int size = array.getLayoutDimension(R.styleable.lbHorizontalGridView_rowHeight, 0);
            setRowHeight(size);
        }
    }

    /**
     * Sets the number of rows.  Defaults to one.
     */
    public void setNumRows(int numRows) {
        mLayoutManager.setNumRows(numRows);
        requestLayout();
    }

    /**
     * Sets the row height.
     *
     * @param height May be {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT WRAP_CONTENT},
     *               or a size in pixels. If zero, row height will be fixed based on number of
     *               rows and view height.
     */
    public void setRowHeight(int height) {
        mLayoutManager.setRowHeight(height);
        requestLayout();
    }

    /**
     * Sets the fade out left edge to transparent.   Note turn on fading edge is very expensive
     * that you should turn off when HorizontalGridView is scrolling.
     */
    public final void setFadingLeftEdge(boolean fading) {
        if (mFadingLowEdge != fading) {
            mFadingLowEdge = fading;
            if (!mFadingLowEdge) {
                mTempBitmapLow = null;
            }
            invalidate();
            updateLayerType();
        }
    }

    /**
     * Returns true if left edge fading is enabled.
     */
    public final boolean getFadingLeftEdge() {
        return mFadingLowEdge;
    }

    /**
     * Sets the left edge fading length in pixels.
     */
    public final void setFadingLeftEdgeLength(int fadeLength) {
        if (mLowFadeShaderLength != fadeLength) {
            mLowFadeShaderLength = fadeLength;
            if (mLowFadeShaderLength != 0) {
                mLowFadeShader = new LinearGradient(0, 0, mLowFadeShaderLength, 0,
                        Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
            } else {
                mLowFadeShader = null;
            }
            invalidate();
        }
    }

    /**
     * Returns the left edge fading length in pixels.
     */
    public final int getFadingLeftEdgeLength() {
        return mLowFadeShaderLength;
    }

    /**
     * Sets the distance in pixels between fading start position and left padding edge.
     * The fading start position is positive when start position is inside left padding
     * area.  Default value is 0, means that the fading starts from left padding edge.
     */
    public final void setFadingLeftEdgeOffset(int fadeOffset) {
        if (mLowFadeShaderOffset != fadeOffset) {
            mLowFadeShaderOffset = fadeOffset;
            invalidate();
        }
    }

    /**
     * Returns the distance in pixels between fading start position and left padding edge.
     * The fading start position is positive when start position is inside left padding
     * area.  Default value is 0, means that the fading starts from left padding edge.
     */
    public final int getFadingLeftEdgeOffset() {
        return mLowFadeShaderOffset;
    }

    /**
     * Sets the fade out right edge to transparent.   Note turn on fading edge is very expensive
     * that you should turn off when HorizontalGridView is scrolling.
     */
    public final void setFadingRightEdge(boolean fading) {
        if (mFadingHighEdge != fading) {
            mFadingHighEdge = fading;
            if (!mFadingHighEdge) {
                mTempBitmapHigh = null;
            }
            invalidate();
            updateLayerType();
        }
    }

    /**
     * Returns true if fading right edge is enabled.
     */
    public final boolean getFadingRightEdge() {
        return mFadingHighEdge;
    }

    /**
     * Sets the right edge fading length in pixels.
     */
    public final void setFadingRightEdgeLength(int fadeLength) {
        if (mHighFadeShaderLength != fadeLength) {
            mHighFadeShaderLength = fadeLength;
            if (mHighFadeShaderLength != 0) {
                mHighFadeShader = new LinearGradient(0, 0, mHighFadeShaderLength, 0,
                        Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            } else {
                mHighFadeShader = null;
            }
            invalidate();
        }
    }

    /**
     * Returns the right edge fading length in pixels.
     */
    public final int getFadingRightEdgeLength() {
        return mHighFadeShaderLength;
    }

    /**
     * Returns the distance in pixels between fading start position and right padding edge.
     * The fading start position is positive when start position is inside right padding
     * area.  Default value is 0, means that the fading starts from right padding edge.
     */
    public final void setFadingRightEdgeOffset(int fadeOffset) {
        if (mHighFadeShaderOffset != fadeOffset) {
            mHighFadeShaderOffset = fadeOffset;
            invalidate();
        }
    }

    /**
     * Sets the distance in pixels between fading start position and right padding edge.
     * The fading start position is positive when start position is inside right padding
     * area.  Default value is 0, means that the fading starts from right padding edge.
     */
    public final int getFadingRightEdgeOffset() {
        return mHighFadeShaderOffset;
    }

    private boolean needsFadingLowEdge() {
        if (!mFadingLowEdge) {
            return false;
        }
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            View view = getChildAt(i);
            if (mLayoutManager.getOpticalLeft(view) < getPaddingLeft() - mLowFadeShaderOffset) {
                return true;
            }
        }
        return false;
    }

    private boolean needsFadingHighEdge() {
        if (!mFadingHighEdge) {
            return false;
        }
        final int c = getChildCount();
        for (int i = c - 1; i >= 0; i--) {
            View view = getChildAt(i);
            if (mLayoutManager.getOpticalRight(view) > getWidth()
                    - getPaddingRight() + mHighFadeShaderOffset) {
                return true;
            }
        }
        return false;
    }

    private Bitmap getTempBitmapLow() {
        if (mTempBitmapLow == null
                || mTempBitmapLow.getWidth() != mLowFadeShaderLength
                || mTempBitmapLow.getHeight() != getHeight()) {
            mTempBitmapLow = Bitmap.createBitmap(mLowFadeShaderLength, getHeight(),
                    Bitmap.Config.ARGB_8888);
        }
        return mTempBitmapLow;
    }

    private Bitmap getTempBitmapHigh() {
        if (mTempBitmapHigh == null
                || mTempBitmapHigh.getWidth() != mHighFadeShaderLength
                || mTempBitmapHigh.getHeight() != getHeight()) {
            // TODO: fix logic for sharing mTempBitmapLow
            if (false && mTempBitmapLow != null
                    && mTempBitmapLow.getWidth() == mHighFadeShaderLength
                    && mTempBitmapLow.getHeight() == getHeight()) {
                // share same bitmap for low edge fading and high edge fading.
                mTempBitmapHigh = mTempBitmapLow;
            } else {
                mTempBitmapHigh = Bitmap.createBitmap(mHighFadeShaderLength, getHeight(),
                        Bitmap.Config.ARGB_8888);
            }
        }
        return mTempBitmapHigh;
    }

    @Override
    public void draw(Canvas canvas) {
        final boolean needsFadingLow = needsFadingLowEdge();
        final boolean needsFadingHigh = needsFadingHighEdge();
        if (!needsFadingLow) {
            mTempBitmapLow = null;
        }
        if (!needsFadingHigh) {
            mTempBitmapHigh = null;
        }
        if (!needsFadingLow && !needsFadingHigh) {
            super.draw(canvas);
            return;
        }

        int lowEdge = mFadingLowEdge? getPaddingLeft() - mLowFadeShaderOffset - mLowFadeShaderLength : 0;
        int highEdge = mFadingHighEdge ? getWidth() - getPaddingRight()
                + mHighFadeShaderOffset + mHighFadeShaderLength : getWidth();

        // draw not-fade content
        int save = canvas.save();
        canvas.clipRect(lowEdge + (mFadingLowEdge ? mLowFadeShaderLength : 0), 0,
                highEdge - (mFadingHighEdge ? mHighFadeShaderLength : 0), getHeight());
        super.draw(canvas);
        canvas.restoreToCount(save);

        Canvas tmpCanvas = new Canvas();
        mTempRect.top = 0;
        mTempRect.bottom = getHeight();
        if (needsFadingLow && mLowFadeShaderLength > 0) {
            Bitmap tempBitmap = getTempBitmapLow();
            tempBitmap.eraseColor(Color.TRANSPARENT);
            tmpCanvas.setBitmap(tempBitmap);
            // draw original content
            int tmpSave = tmpCanvas.save();
            tmpCanvas.clipRect(0, 0, mLowFadeShaderLength, getHeight());
            tmpCanvas.translate(-lowEdge, 0);
            super.draw(tmpCanvas);
            tmpCanvas.restoreToCount(tmpSave);
            // draw fading out
            mTempPaint.setShader(mLowFadeShader);
            tmpCanvas.drawRect(0, 0, mLowFadeShaderLength, getHeight(), mTempPaint);
            // copy back to canvas
            mTempRect.left = 0;
            mTempRect.right = mLowFadeShaderLength;
            canvas.translate(lowEdge, 0);
            canvas.drawBitmap(tempBitmap, mTempRect, mTempRect, null);
            canvas.translate(-lowEdge, 0);
        }
        if (needsFadingHigh && mHighFadeShaderLength > 0) {
            Bitmap tempBitmap = getTempBitmapHigh();
            tempBitmap.eraseColor(Color.TRANSPARENT);
            tmpCanvas.setBitmap(tempBitmap);
            // draw original content
            int tmpSave = tmpCanvas.save();
            tmpCanvas.clipRect(0, 0, mHighFadeShaderLength, getHeight());
            tmpCanvas.translate(-(highEdge - mHighFadeShaderLength), 0);
            super.draw(tmpCanvas);
            tmpCanvas.restoreToCount(tmpSave);
            // draw fading out
            mTempPaint.setShader(mHighFadeShader);
            tmpCanvas.drawRect(0, 0, mHighFadeShaderLength, getHeight(), mTempPaint);
            // copy back to canvas
            mTempRect.left = 0;
            mTempRect.right = mHighFadeShaderLength;
            canvas.translate(highEdge - mHighFadeShaderLength, 0);
            canvas.drawBitmap(tempBitmap, mTempRect, mTempRect, null);
            canvas.translate(-(highEdge - mHighFadeShaderLength), 0);
        }
    }

    /**
     * Updates the layer type for this view.
     * If fading edges are needed, use a hardware layer.  This works around the problem
     * that when a child invalidates itself (for example has an animated background),
     * the parent view must also be invalidated to refresh the display list which
     * updates the the caching bitmaps used to draw the fading edges.
     */
    private void updateLayerType() {
        if (mFadingLowEdge || mFadingHighEdge) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setWillNotDraw(false);
        } else {
            setLayerType(View.LAYER_TYPE_NONE, null);
            setWillNotDraw(true);
        }
    }
}
