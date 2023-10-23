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
package androidx.leanback.graphics;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.view.View;

import androidx.leanback.R;

/**
 * Helper class for applying a dim level to a View.  The ColorFilterDimmer
 * uses a ColorFilter in a Paint object to dim the view according to the
 * currently active level.
 */
public final class ColorFilterDimmer {

    private final ColorFilterCache mColorDimmer;

    private final float mActiveLevel;
    private final float mDimmedLevel;

    private final Paint mPaint;
    private ColorFilter mFilter;

    /**
     * Creates a default ColorFilterDimmer. Uses the default color and level for
     * the dimmer.
     *
     * @param context A Context used to retrieve Resources.
     * @return A ColorFilterDimmer with the default dim color and levels.
     */
    public static ColorFilterDimmer createDefault(Context context) {
        TypedArray a = context.obtainStyledAttributes(R.styleable.LeanbackTheme);

        int dimColor = a.getColor(R.styleable.LeanbackTheme_overlayDimMaskColor,
                context.getResources().getColor(R.color.lb_view_dim_mask_color));
        float activeLevel = a.getFraction(R.styleable.LeanbackTheme_overlayDimActiveLevel, 1, 1,
                context.getResources().getFraction(R.fraction.lb_view_active_level, 1, 0));
        float dimmedLevel = a.getFraction(R.styleable.LeanbackTheme_overlayDimDimmedLevel, 1, 1,
                context.getResources().getFraction(R.fraction.lb_view_dimmed_level, 1, 1));
        a.recycle();
        return new ColorFilterDimmer(ColorFilterCache.getColorFilterCache(
                    dimColor), activeLevel, dimmedLevel);
    }

    /**
     * Creates a ColorFilterDimmer for the given color and levels..
     *
     * @param dimmer      The ColorFilterCache for dim color.
     * @param activeLevel The level of dimming when the View is in its active
     *                    state. Must be a float value between 0.0 and 1.0.
     * @param dimmedLevel The level of dimming when the View is in its dimmed
     *                    state. Must be a float value between 0.0 and 1.0.
     */
    public static ColorFilterDimmer create(ColorFilterCache dimmer,
            float activeLevel, float dimmedLevel) {
        return new ColorFilterDimmer(dimmer, activeLevel, dimmedLevel);
    }

    private ColorFilterDimmer(ColorFilterCache dimmer, float activeLevel, float dimmedLevel) {
        mColorDimmer = dimmer;
        if (activeLevel > 1.0f) activeLevel = 1.0f;
        if (activeLevel < 0.0f) activeLevel = 0.0f;
        if (dimmedLevel > 1.0f) dimmedLevel = 1.0f;
        if (dimmedLevel < 0.0f) dimmedLevel = 0.0f;
        mActiveLevel = activeLevel;
        mDimmedLevel = dimmedLevel;
        mPaint = new Paint();
    }

    /**
     * Apply current the ColorFilter to a View. This method will set the
     * hardware layer of the view when applying a filter, and remove it when not
     * applying a filter.
     *
     * @param view The View to apply the ColorFilter to.
     */
    public void applyFilterToView(View view) {
        if (mFilter != null) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, mPaint);
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        // FIXME: Current framework has bug that not triggering invalidate when change layer
        // paint.  Will add conditional sdk version check once bug is fixed in released
        // framework.
        view.invalidate();
    }

    /**
     * Sets the active level of the dimmer. Updates the ColorFilter based on the
     * level.
     *
     * @param level A float between 0 (fully dim) and 1 (fully active).
     */
    public void setActiveLevel(float level) {
        if (level < 0.0f) level = 0.0f;
        if (level > 1.0f) level = 1.0f;
        mFilter = mColorDimmer.getFilterForLevel(
                mDimmedLevel + level * (mActiveLevel - mDimmedLevel));
        mPaint.setColorFilter(mFilter);
    }

    /**
     * Gets the ColorFilter set to the current dim level.
     *
     * @return The current ColorFilter.
     */
    public ColorFilter getColorFilter() {
        return mFilter;
    }

    /**
     * Gets the Paint object set to the current dim level.
     *
     * @return The current Paint object.
     */
    public Paint getPaint() {
        return mPaint;
    }
}
