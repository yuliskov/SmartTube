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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.leanback.R;

/**
 * Helper class for assigning a dim color to Paint. It holds the alpha value for
 * the current active level.
 */
public final class ColorOverlayDimmer {

    private final float mActiveLevel;
    private final float mDimmedLevel;

    private final Paint mPaint;

    private int mAlpha;
    private float mAlphaFloat;

    /**
     * Creates a default ColorOverlayDimmer.
     */
    public static ColorOverlayDimmer createDefault(Context context) {
        TypedArray a = context.obtainStyledAttributes(R.styleable.LeanbackTheme);

        int dimColor = a.getColor(R.styleable.LeanbackTheme_overlayDimMaskColor,
                context.getResources().getColor(R.color.lb_view_dim_mask_color));
        float activeLevel = a.getFraction(R.styleable.LeanbackTheme_overlayDimActiveLevel, 1, 1,
                context.getResources().getFraction(R.fraction.lb_view_active_level, 1, 0));
        float dimmedLevel = a.getFraction(R.styleable.LeanbackTheme_overlayDimDimmedLevel, 1, 1,
                context.getResources().getFraction(R.fraction.lb_view_dimmed_level, 1, 1));
        a.recycle();
        return new ColorOverlayDimmer(dimColor, activeLevel, dimmedLevel);
    }

    /**
     * Creates a ColorOverlayDimmer for the given color and levels.
     *
     * @param dimColor    The color for fully dimmed. Only the RGB values are
     *                    used; the alpha channel is ignored.
     * @param activeLevel The level of dimming when the View is in its active
     *                    state. Must be a float value between 0.0 and 1.0.
     * @param dimmedLevel The level of dimming when the View is in its dimmed
     *                    state. Must be a float value between 0.0 and 1.0.
     */
    public static ColorOverlayDimmer createColorOverlayDimmer(int dimColor, float activeLevel,
            float dimmedLevel) {
        return new ColorOverlayDimmer(dimColor, activeLevel, dimmedLevel);
    }

    private ColorOverlayDimmer(int dimColor, float activeLevel, float dimmedLevel) {
        if (activeLevel > 1.0f) activeLevel = 1.0f;
        if (activeLevel < 0.0f) activeLevel = 0.0f;
        if (dimmedLevel > 1.0f) dimmedLevel = 1.0f;
        if (dimmedLevel < 0.0f) dimmedLevel = 0.0f;
        mPaint = new Paint();
        dimColor = Color.rgb(Color.red(dimColor), Color.green(dimColor), Color.blue(dimColor));
        mPaint.setColor(dimColor);
        mActiveLevel = activeLevel;
        mDimmedLevel = dimmedLevel;
        setActiveLevel(1);
    }

    /**
     * Sets the active level of the dimmer. Updates the alpha value based on the
     * level.
     *
     * @param level A float between 0 (fully dim) and 1 (fully active).
     */
    public void setActiveLevel(float level) {
        mAlphaFloat = (mDimmedLevel + level * (mActiveLevel - mDimmedLevel));
        mAlpha = (int) (255 * mAlphaFloat);
        mPaint.setAlpha(mAlpha);
    }

    /**
     * Returns whether the dimmer needs to draw.
     */
    public boolean needsDraw() {
        return mAlpha != 0;
    }

    /**
     * Returns the alpha value for the dimmer.
     */
    public int getAlpha() {
        return mAlpha;
    }

    /**
     * Returns the float value between 0 and 1 corresponding to alpha between
     * 0 and 255.
     */
    public float getAlphaFloat() {
        return mAlphaFloat;
    }

    /**
     * Returns the Paint object set to the current alpha value.
     */
    public Paint getPaint() {
        return mPaint;
    }

    /**
     * Change the RGB of the color according to current dim level. Maintains the
     * alpha value of the color.
     *
     * @param color The color to apply the dim level to.
     * @return A color with the RGB values adjusted by the alpha of the current
     *         dim level.
     */
    public int applyToColor(int color) {
        float f = 1 - mAlphaFloat;
        return Color.argb(Color.alpha(color),
                (int)(Color.red(color) * f),
                (int)(Color.green(color) * f),
                (int)(Color.blue(color) * f));
    }

    /**
     * Draw a dim color overlay on top of a child View inside the canvas of
     * the parent View.
     *
     * @param c Canvas of the parent View.
     * @param v A child of the parent View.
     * @param includePadding Set to true to draw overlay on padding area of the
     *        View.
     */
    public void drawColorOverlay(Canvas c, View v, boolean includePadding) {
        c.save();
        float dx = v.getLeft() + v.getTranslationX();
        float dy = v.getTop() + v.getTranslationY();
        c.translate(dx, dy);
        c.concat(v.getMatrix());
        c.translate(-dx, -dy);
        if (includePadding) {
            c.drawRect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom(), mPaint);
        } else {
            c.drawRect(v.getLeft() + v.getPaddingLeft(),
                    v.getTop() + v.getPaddingTop(),
                    v.getRight() - v.getPaddingRight(),
                    v.getBottom() - v.getPaddingBottom(), mPaint);
        }
        c.restore();
    }
}
