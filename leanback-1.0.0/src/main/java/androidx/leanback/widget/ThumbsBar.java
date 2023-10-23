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
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class ThumbsBar extends LinearLayout {

    // initial value for Thumb's number before measuring the screen size
    int mNumOfThumbs = -1;
    int mThumbWidthInPixel;
    int mThumbHeightInPixel;
    int mHeroThumbWidthInPixel;
    int mHeroThumbHeightInPixel;
    int mMeasuredMarginInPixel;
    final SparseArray<Bitmap> mBitmaps = new SparseArray<>();

    // flag to determine if the number of thumbs in thumbs bar is set by user through
    // setNumberofThumbs API or auto-calculated according to android tv design spec.
    private boolean mIsUserSets = false;

    public ThumbsBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThumbsBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // According to the spec,
        // the width of non-hero thumb should be 80% of HeroThumb's Width, i.e. 0.8 * 192dp = 154dp
        mThumbWidthInPixel = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_thumbs_width);
        mThumbHeightInPixel = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_thumbs_height);
        // According to the spec, the width of HeroThumb should be 192dp
        mHeroThumbHeightInPixel = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_hero_thumbs_width);
        mHeroThumbWidthInPixel = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_hero_thumbs_height);
        // According to the spec, the margin between thumbs to be 4dp
        mMeasuredMarginInPixel = context.getResources().getDimensionPixelSize(
                R.dimen.lb_playback_transport_thumbs_margin);
    }

    /**
     * Get hero index which is the middle child.
     */
    public int getHeroIndex() {
        return getChildCount() / 2;
    }

    /**
     * Set size of thumb view in pixels
     */
    public void setThumbSize(int width, int height) {
        mThumbHeightInPixel = height;
        mThumbWidthInPixel = width;
        int heroIndex = getHeroIndex();
        for (int i = 0; i < getChildCount(); i++) {
            if (heroIndex != i) {
                View child = getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                boolean changed = false;
                if (lp.height != height) {
                    lp.height = height;
                    changed = true;
                }
                if (lp.width != width) {
                    lp.width = width;
                    changed = true;
                }
                if (changed) {
                    child.setLayoutParams(lp);
                }
            }
        }
    }

    /**
     * Set size of hero thumb view in pixels, it is usually larger than other thumbs.
     */
    public void setHeroThumbSize(int width, int height) {
        mHeroThumbHeightInPixel = height;
        mHeroThumbWidthInPixel = width;
        int heroIndex = getHeroIndex();
        for (int i = 0; i < getChildCount(); i++) {
            if (heroIndex == i) {
                View child = getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                boolean changed = false;
                if (lp.height != height) {
                    lp.height = height;
                    changed = true;
                }
                if (lp.width != width) {
                    lp.width = width;
                    changed = true;
                }
                if (changed) {
                    child.setLayoutParams(lp);
                }
            }
        }
    }

    /**
     * Set the space between thumbs in pixels
     */
    public void setThumbSpace(int spaceInPixel) {
        mMeasuredMarginInPixel = spaceInPixel;
        requestLayout();
    }

    /**
     * Set number of thumb views.
     */
    public void setNumberOfThumbs(int numOfThumbs) {
        mIsUserSets = true;
        mNumOfThumbs = numOfThumbs;
        setNumberOfThumbsInternal();
    }

    /**
     * Helper function for setNumberOfThumbs.
     * Will Update the layout settings in ThumbsBar based on mNumOfThumbs
     */
    private void setNumberOfThumbsInternal() {
        while (getChildCount() > mNumOfThumbs) {
            removeView(getChildAt(getChildCount() - 1));
        }
        while (getChildCount() < mNumOfThumbs) {
            View view = createThumbView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mThumbWidthInPixel,
                    mThumbHeightInPixel);
            addView(view, lp);
        }
        int heroIndex = getHeroIndex();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
            if (heroIndex == i) {
                lp.width = mHeroThumbWidthInPixel;
                lp.height = mHeroThumbHeightInPixel;
            } else {
                lp.width = mThumbWidthInPixel;
                lp.height = mThumbHeightInPixel;
            }
            child.setLayoutParams(lp);
        }
    }

    private static int roundUp(int num, int divisor) {
        return (num + divisor - 1) / divisor;
    }

    /**
     * Helper function to compute how many thumbs should be put in the screen
     * Assume we should put x's non-hero thumbs in the screen, the equation should be
     *   192dp (width of hero thumbs) +
     *   154dp (width of common thumbs) * x +
     *   4dp (width of the margin between thumbs) * x
     *     = width
     * So the calculated number of non-hero thumbs should be (width - 192dp) / 158dp.
     * If the calculated number of non-hero thumbs is less than 2, it will be updated to 2
     * or if the calculated number or non-hero thumbs is not an even number, it will be
     * decremented by one.
     * This processing is used to make sure the arrangement of non-hero thumbs
     * in ThumbsBar is symmetrical.
     * Also there should be a hero thumb in the middle of the ThumbsBar,
     * the final result should be non-hero thumbs (after processing) + 1.
     *
     * @param  widthInPixel measured width in pixel
     * @return The number of thumbs
     */
    private int calculateNumOfThumbs(int widthInPixel) {
        int nonHeroThumbNum = roundUp(widthInPixel - mHeroThumbWidthInPixel,
                mThumbWidthInPixel + mMeasuredMarginInPixel);
        if (nonHeroThumbNum < 2) {
            // If the calculated number of non-hero thumbs is less than 2,
            // it will be updated to 2
            nonHeroThumbNum = 2;
        } else if ((nonHeroThumbNum & 1) != 0) {
            // If the calculated number or non-hero thumbs is not an even number,
            // it will be increased by one.
            nonHeroThumbNum++;
        }
        // Count Hero Thumb to the final result
        return nonHeroThumbNum + 1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        // If the number of thumbs in ThumbsBar is not set by user explicitly, it will be
        // recalculated based on Android TV Design Spec
        if (!mIsUserSets) {
            int numOfThumbs = calculateNumOfThumbs(width);
            // Set new number of thumbs when calculation result is different with current number
            if (mNumOfThumbs != numOfThumbs) {
                mNumOfThumbs = numOfThumbs;
                setNumberOfThumbsInternal();
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int heroIndex = getHeroIndex();
        View heroView = getChildAt(heroIndex);
        int heroLeft = getWidth() / 2 - heroView.getMeasuredWidth() / 2;
        int heroRight = getWidth() / 2 + heroView.getMeasuredWidth() / 2;
        heroView.layout(heroLeft, getPaddingTop(), heroRight,
                getPaddingTop() + heroView.getMeasuredHeight());
        int heroCenter = getPaddingTop() + heroView.getMeasuredHeight() / 2;

        for (int i = heroIndex - 1; i >= 0; i--) {
            heroLeft -= mMeasuredMarginInPixel;
            View child = getChildAt(i);
            child.layout(heroLeft - child.getMeasuredWidth(),
                    heroCenter - child.getMeasuredHeight() / 2,
                    heroLeft,
                    heroCenter + child.getMeasuredHeight() / 2);
            heroLeft -= child.getMeasuredWidth();
        }
        for (int i = heroIndex + 1; i < mNumOfThumbs; i++) {
            heroRight += mMeasuredMarginInPixel;
            View child = getChildAt(i);
            child.layout(heroRight,
                    heroCenter - child.getMeasuredHeight() / 2,
                    heroRight + child.getMeasuredWidth(),
                    heroCenter + child.getMeasuredHeight() / 2);
            heroRight += child.getMeasuredWidth();
        }
    }

    /**
     * Create a thumb view, it's by default a ImageView.
     */
    protected View createThumbView(ViewGroup parent) {
        return new ImageView(parent.getContext());
    }

    /**
     * Clear all thumb bitmaps set on thumb views.
     */
    public void clearThumbBitmaps() {
        for (int i = 0; i < getChildCount(); i++) {
            setThumbBitmap(i, null);
        }
        mBitmaps.clear();
    }


    /**
     * Get bitmap of given child index.
     */
    public Bitmap getThumbBitmap(int index) {
        return mBitmaps.get(index);
    }

    /**
     * Set thumb bitmap for a given index of child.
     */
    public void setThumbBitmap(int index, Bitmap bitmap) {
        mBitmaps.put(index, bitmap);
        ((ImageView) getChildAt(index)).setImageBitmap(bitmap);
    }
}
