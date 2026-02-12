/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.core.view.ViewCompat;
import androidx.leanback.R;

/**
 * <p>A widget that draws a search affordance, represented by a round background and an icon.</p>
 *
 * The background color and icon can be customized.
 */
public class SearchOrbView extends FrameLayout implements View.OnClickListener {
    private OnClickListener mListener;
    private View mRootView;
    private View mSearchOrbView;
    private ImageView mIcon;
    private Drawable mIconDrawable;
    private Colors mColors;
    private final float mFocusedZoom;
    private final int mPulseDurationMs;
    private final int mScaleDurationMs;
    private final float mUnfocusedZ;
    private final float mFocusedZ;
    private ValueAnimator mColorAnimator;
    private boolean mColorAnimationEnabled;
    private boolean mAttachedToWindow;

    /**
     * A set of colors used to display the search orb.
     */
    public static class Colors {
        private static final float BRIGHTNESS_ALPHA = 0.15f;

        /**
         * Constructs a color set using the given color for the search orb.
         * Other colors are provided by the framework.
         *
         * @param color The main search orb color.
         */
        public Colors(@ColorInt int color) {
            this(color, color);
        }

        /**
         * Constructs a color set using the given colors for the search orb.
         * Other colors are provided by the framework.
         *
         * @param color The main search orb color.
         * @param brightColor A brighter version of the search orb used for animation.
         */
        public Colors(@ColorInt int color, @ColorInt int brightColor) {
            this(color, brightColor, Color.TRANSPARENT);
        }

        /**
         * Constructs a color set using the given colors.
         *
         * @param color The main search orb color.
         * @param brightColor A brighter version of the search orb used for animation.
         * @param iconColor A color used to tint the search orb icon.
         */
        public Colors(@ColorInt int color, @ColorInt int brightColor, @ColorInt int iconColor) {
            this.color = color;
            this.brightColor = brightColor == color ? getBrightColor(color) : brightColor;
            this.iconColor = iconColor;
        }

        /**
         * The main color of the search orb.
         */
        @ColorInt
        public int color;

        /**
         * A brighter version of the search orb used for animation.
         */
        @ColorInt
        public int brightColor;

        /**
         * A color used to tint the search orb icon.
         */
        @ColorInt
        public int iconColor;

        /**
         * Computes a default brighter version of the given color.
         */
        public static int getBrightColor(int color) {
            final float brightnessValue = 0xff * BRIGHTNESS_ALPHA;
            int red = (int)(Color.red(color) * (1 - BRIGHTNESS_ALPHA) + brightnessValue);
            int green = (int)(Color.green(color) * (1 - BRIGHTNESS_ALPHA) + brightnessValue);
            int blue = (int)(Color.blue(color) * (1 - BRIGHTNESS_ALPHA) + brightnessValue);
            int alpha = (int)(Color.alpha(color) * (1 - BRIGHTNESS_ALPHA) + brightnessValue);
            return Color.argb(alpha, red, green, blue);
        }
    }

    private final ArgbEvaluator mColorEvaluator = new ArgbEvaluator();

    private final ValueAnimator.AnimatorUpdateListener mUpdateListener =
            new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animator) {
            Integer color = (Integer) animator.getAnimatedValue();
            setOrbViewColor(color.intValue());
        }
    };

    private ValueAnimator mShadowFocusAnimator;

    private final ValueAnimator.AnimatorUpdateListener mFocusUpdateListener =
            new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            setSearchOrbZ(animation.getAnimatedFraction());
        }
    };

    void setSearchOrbZ(float fraction) {
        ViewCompat.setZ(mSearchOrbView, mUnfocusedZ + fraction * (mFocusedZ - mUnfocusedZ));
    }

    public SearchOrbView(Context context) {
        this(context, null);
    }

    public SearchOrbView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.searchOrbViewStyle);
    }

    public SearchOrbView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final Resources res = context.getResources();

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = inflater.inflate(getLayoutResourceId(), this, true);
        mSearchOrbView = mRootView.findViewById(R.id.search_orb);
        mIcon = (ImageView) mRootView.findViewById(R.id.icon);

        mFocusedZoom = context.getResources().getFraction(
                R.fraction.lb_search_orb_focused_zoom, 1, 1);
        mPulseDurationMs = context.getResources().getInteger(
                R.integer.lb_search_orb_pulse_duration_ms);
        mScaleDurationMs = context.getResources().getInteger(
                R.integer.lb_search_orb_scale_duration_ms);
        mFocusedZ = context.getResources().getDimensionPixelSize(
                R.dimen.lb_search_orb_focused_z);
        mUnfocusedZ = context.getResources().getDimensionPixelSize(
                R.dimen.lb_search_orb_unfocused_z);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbSearchOrbView,
                defStyleAttr, 0);

        Drawable img = a.getDrawable(R.styleable.lbSearchOrbView_searchOrbIcon);
        if (img == null) {
            img = res.getDrawable(R.drawable.lb_ic_in_app_search);
        }
        setOrbIcon(img);

        int defColor = res.getColor(R.color.lb_default_search_color);
        int color = a.getColor(R.styleable.lbSearchOrbView_searchOrbColor, defColor);
        int brightColor = a.getColor(
                R.styleable.lbSearchOrbView_searchOrbBrightColor, color);
        int iconColor = a.getColor(R.styleable.lbSearchOrbView_searchOrbIconColor, Color.TRANSPARENT);
        setOrbColors(new Colors(color, brightColor, iconColor));
        a.recycle();

        setFocusable(true);
        setClipChildren(false);
        setOnClickListener(this);
        setSoundEffectsEnabled(false);
        setSearchOrbZ(0);

        // Icon has no background, but must be on top of the search orb view
        ViewCompat.setZ(mIcon, mFocusedZ);
    }

    int getLayoutResourceId() {
        return R.layout.lb_search_orb;
    }

    void scaleOrbViewOnly(float scale) {
        mSearchOrbView.setScaleX(scale);
        mSearchOrbView.setScaleY(scale);
    }

    float getFocusedZoom() {
        return mFocusedZoom;
    }

    @Override
    public void onClick(View view) {
        if (null != mListener) {
            mListener.onClick(view);
        }
    }

    private void startShadowFocusAnimation(boolean gainFocus, int duration) {
        if (mShadowFocusAnimator == null) {
            mShadowFocusAnimator = ValueAnimator.ofFloat(0f, 1f);
            mShadowFocusAnimator.addUpdateListener(mFocusUpdateListener);
        }
        if (gainFocus) {
            mShadowFocusAnimator.start();
        } else {
            mShadowFocusAnimator.reverse();
        }
        mShadowFocusAnimator.setDuration(duration);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        animateOnFocus(gainFocus);
    }

    void animateOnFocus(boolean hasFocus) {
        final float zoom = hasFocus ? mFocusedZoom : 1f;
        mRootView.animate().scaleX(zoom).scaleY(zoom).setDuration(mScaleDurationMs).start();
        startShadowFocusAnimation(hasFocus, mScaleDurationMs);
        enableOrbColorAnimation(hasFocus);
    }

    /**
     * Sets the orb icon.
     * @param icon the drawable to be used as the icon
     */
    public void setOrbIcon(Drawable icon) {
        mIconDrawable = icon;
        mIcon.setImageDrawable(mIconDrawable);
    }

    /**
     * Returns the orb icon
     * @return the drawable used as the icon
     */
    public Drawable getOrbIcon() {
        return mIconDrawable;
    }

    /**
     * Sets the on click listener for the orb.
     * @param listener The listener.
     */
    public void setOnOrbClickedListener(OnClickListener listener) {
        mListener = listener;
    }

    /**
     * Sets the background color of the search orb.
     * Other colors will be provided by the framework.
     *
     * @param color the RGBA color
     */
    public void setOrbColor(int color) {
        setOrbColors(new Colors(color, color, Color.TRANSPARENT));
    }

    /**
     * Sets the search orb colors.
     * Other colors are provided by the framework.
     * @deprecated Use {@link #setOrbColors(Colors)} instead.
     */
    @Deprecated
    public void setOrbColor(@ColorInt int color, @ColorInt int brightColor) {
        setOrbColors(new Colors(color, brightColor, Color.TRANSPARENT));
    }

    /**
     * Returns the orb color
     * @return the RGBA color
     */
    @ColorInt
    public int getOrbColor() {
        return mColors.color;
    }

    /**
     * Sets the {@link Colors} used to display the search orb.
     */
    public void setOrbColors(Colors colors) {
        mColors = colors;
        mIcon.setColorFilter(mColors.iconColor);

        if (mColorAnimator == null) {
            setOrbViewColor(mColors.color);
        } else {
            enableOrbColorAnimation(true);
        }
    }

    /**
     * Returns the {@link Colors} used to display the search orb.
     */
    public Colors getOrbColors() {
        return mColors;
    }

    /**
     * Enables or disables the orb color animation.
     *
     * <p>
     * Orb color animation is handled automatically when the orb is focused/unfocused,
     * however, an app may choose to override the current animation state, for example
     * when an activity is paused.
     * </p>
     */
    public void enableOrbColorAnimation(boolean enable) {
        mColorAnimationEnabled = enable;
        updateColorAnimator();
    }

    private void updateColorAnimator() {
        if (mColorAnimator != null) {
            mColorAnimator.end();
            mColorAnimator = null;
        }
        if (mColorAnimationEnabled && mAttachedToWindow) {
            // TODO: set interpolator (material if available)
            mColorAnimator = ValueAnimator.ofObject(mColorEvaluator,
                    mColors.color, mColors.brightColor, mColors.color);
            mColorAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mColorAnimator.setDuration(mPulseDurationMs * 2);
            mColorAnimator.addUpdateListener(mUpdateListener);
            mColorAnimator.start();
        }
    }

    void setOrbViewColor(int color) {
        if (mSearchOrbView.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) mSearchOrbView.getBackground()).setColor(color);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        updateColorAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttachedToWindow = false;
        // Must stop infinite animation to prevent activity leak
        updateColorAnimator();
        super.onDetachedFromWindow();
    }
}
