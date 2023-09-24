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
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.R;
import androidx.leanback.system.Settings;


/**
 * ShadowOverlayHelper is a helper class for shadow, overlay color and rounded corner.
 * There are many choices to implement Shadow, overlay color.
 * Initialize it with ShadowOverlayHelper.Builder and it decides the best strategy based
 * on options user choose and current platform version.
 *
 * <li> For shadow:  it may use 9-patch with opticalBounds or Z-value based shadow for
 *                   API >= 21.  When 9-patch is used, it requires a ShadowOverlayContainer
 *                   to include 9-patch views.
 * <li> For overlay: it may use ShadowOverlayContainer which overrides draw() or it may
 *                   use setForeground(new ColorDrawable()) for API>=23.  The foreground support
 *                   might be disabled if rounded corner is applied due to performance reason.
 * <li> For rounded-corner:  it uses a ViewOutlineProvider for API>=21.
 *
 * There are two different strategies: use Wrapper with a ShadowOverlayContainer;
 * or apply rounded corner, overlay and rounded-corner to the view itself.  Below is an example
 * of how helper is used.
 *
 * <code>
 * ShadowOverlayHelper mHelper = new ShadowOverlayHelper.Builder().
 *         .needsOverlay(true).needsRoundedCorner(true).needsShadow(true)
 *         .build();
 * mHelper.prepareParentForShadow(parentView); // apply optical-bounds for 9-patch shadow.
 * mHelper.setOverlayColor(view, Color.argb(0x80, 0x80, 0x80, 0x80));
 * mHelper.setShadowFocusLevel(view, 1.0f);
 * ...
 * View initializeView(View view) {
 *     if (mHelper.needsWrapper()) {
 *         ShadowOverlayContainer wrapper = mHelper.createShadowOverlayContainer(context);
 *         wrapper.wrap(view);
 *         return wrapper;
 *     } else {
 *         mHelper.onViewCreated(view);
 *         return view;
 *     }
 * }
 * ...
 *
 * </code>
 */
public final class ShadowOverlayHelper {

    /**
     * Builder for creating ShadowOverlayHelper.
     */
    public static final class Builder {

        private boolean needsOverlay;
        private boolean needsRoundedCorner;
        private boolean needsShadow;
        private boolean preferZOrder = true;
        private boolean keepForegroundDrawable;
        private Options options = Options.DEFAULT;

        /**
         * Set if needs overlay color.
         * @param needsOverlay   True if needs overlay.
         * @return  The Builder object itself.
         */
        public Builder needsOverlay(boolean needsOverlay) {
            this.needsOverlay = needsOverlay;
            return this;
        }

        /**
         * Set if needs shadow.
         * @param needsShadow   True if needs shadow.
         * @return  The Builder object itself.
         */
        public Builder needsShadow(boolean needsShadow) {
            this.needsShadow = needsShadow;
            return this;
        }

        /**
         * Set if needs rounded corner.
         * @param needsRoundedCorner   True if needs rounded corner.
         * @return  The Builder object itself.
         */
        public Builder needsRoundedCorner(boolean needsRoundedCorner) {
            this.needsRoundedCorner = needsRoundedCorner;
            return this;
        }

        /**
         * Set if prefer z-order shadow.  On old devices,  z-order shadow might be slow,
         * set to false to fall back to static 9-patch shadow.  Recommend to read
         * from system wide Setting value: see {@link Settings}.
         *
         * @param preferZOrder   True if prefer Z shadow.  Default is true.
         * @return The Builder object itself.
         */
        public Builder preferZOrder(boolean preferZOrder) {
            this.preferZOrder = preferZOrder;
            return this;
        }

        /**
         * Set if not using foreground drawable for overlay color.  For example if
         * the view has already assigned a foreground drawable for other purposes.
         * When it's true, helper will use a ShadowOverlayContainer for overlay color.
         *
         * @param keepForegroundDrawable   True to keep the original foreground drawable.
         * @return The Builder object itself.
         */
        public Builder keepForegroundDrawable(boolean keepForegroundDrawable) {
            this.keepForegroundDrawable = keepForegroundDrawable;
            return this;
        }

        /**
         * Set option values e.g. Shadow Z value, rounded corner radius.
         *
         * @param options   The Options object to create ShadowOverlayHelper.
         */
        public Builder options(Options options) {
            this.options = options;
            return this;
        }

        /**
         * Create ShadowOverlayHelper object
         * @param context    The context uses to read Resources settings.
         * @return           The ShadowOverlayHelper object.
         */
        public ShadowOverlayHelper build(Context context) {
            final ShadowOverlayHelper helper = new ShadowOverlayHelper();
            helper.mNeedsOverlay = needsOverlay;
            helper.mNeedsRoundedCorner = needsRoundedCorner && supportsRoundedCorner();
            helper.mNeedsShadow = needsShadow && supportsShadow();

            if (helper.mNeedsRoundedCorner) {
                helper.setupRoundedCornerRadius(options, context);
            }

            // figure out shadow type and if we need use wrapper:
            if (helper.mNeedsShadow) {
                // if static shadow is preferred or dynamic shadow is not supported,
                // use static shadow,  otherwise use dynamic shadow.
                if (!preferZOrder || !supportsDynamicShadow()) {
                    helper.mShadowType = SHADOW_STATIC;
                    // static shadow requires ShadowOverlayContainer to support crossfading
                    // of two shadow views.
                    helper.mNeedsWrapper = true;
                } else {
                    helper.mShadowType = SHADOW_DYNAMIC;
                    helper.setupDynamicShadowZ(options, context);
                    helper.mNeedsWrapper = ((!supportsForeground() || keepForegroundDrawable)
                            && helper.mNeedsOverlay);
                }
            } else {
                helper.mShadowType = SHADOW_NONE;
                helper.mNeedsWrapper = ((!supportsForeground() || keepForegroundDrawable)
                        && helper.mNeedsOverlay);
            }

            return helper;
        }

    }

    /**
     * Option values for ShadowOverlayContainer.
     */
    public static final class Options {

        /**
         * Default Options for values.
         */
        public static final Options DEFAULT = new Options();

        private int roundedCornerRadius = 0; // 0 for default value
        private float dynamicShadowUnfocusedZ = -1; // < 0 for default value
        private float dynamicShadowFocusedZ = -1;   // < 0 for default value
        /**
         * Set value of rounded corner radius.
         *
         * @param roundedCornerRadius   Number of pixels of rounded corner radius.
         *                              Set to 0 to use default settings.
         * @return  The Options object itself.
         */
        public Options roundedCornerRadius(int roundedCornerRadius){
            this.roundedCornerRadius = roundedCornerRadius;
            return this;
        }

        /**
         * Set value of focused and unfocused Z value for shadow.
         *
         * @param unfocusedZ   Number of pixels for unfocused Z value.
         * @param focusedZ     Number of pixels for focused Z value.
         * @return  The Options object itself.
         */
        public Options dynamicShadowZ(float unfocusedZ, float focusedZ){
            this.dynamicShadowUnfocusedZ = unfocusedZ;
            this.dynamicShadowFocusedZ = focusedZ;
            return this;
        }

        /**
         * Get radius of rounded corner in pixels.
         *
         * @return Radius of rounded corner in pixels.
         */
        public final int getRoundedCornerRadius() {
            return roundedCornerRadius;
        }

        /**
         * Get z value of shadow when a view is not focused.
         *
         * @return Z value of shadow when a view is not focused.
         */
        public final float getDynamicShadowUnfocusedZ() {
            return dynamicShadowUnfocusedZ;
        }

        /**
         * Get z value of shadow when a view is focused.
         *
         * @return Z value of shadow when a view is focused.
         */
        public final float getDynamicShadowFocusedZ() {
            return dynamicShadowFocusedZ;
        }
    }

    /**
     * No shadow.
     */
    public static final int SHADOW_NONE = 1;

    /**
     * Shadows are fixed.
     */
    public static final int SHADOW_STATIC = 2;

    /**
     * Shadows depend on the size, shape, and position of the view.
     */
    public static final int SHADOW_DYNAMIC = 3;

    int mShadowType = SHADOW_NONE;
    boolean mNeedsOverlay;
    boolean mNeedsRoundedCorner;
    boolean mNeedsShadow;
    boolean mNeedsWrapper;

    int mRoundedCornerRadius;
    float mUnfocusedZ;
    float mFocusedZ;

    /**
     * Return true if the platform sdk supports shadow.
     */
    public static boolean supportsShadow() {
        return StaticShadowHelper.supportsShadow();
    }

    /**
     * Returns true if the platform sdk supports dynamic shadows.
     */
    public static boolean supportsDynamicShadow() {
        return ShadowHelper.supportsDynamicShadow();
    }

    /**
     * Returns true if the platform sdk supports rounded corner through outline.
     */
    public static boolean supportsRoundedCorner() {
        return RoundedRectHelper.supportsRoundedCorner();
    }

    /**
     * Returns true if view.setForeground() is supported.
     */
    public static boolean supportsForeground() {
        return ForegroundHelper.supportsForeground();
    }

    /*
     * hide from external, should be only created by ShadowOverlayHelper.Options.
     */
    ShadowOverlayHelper() {
    }

    /**
     * {@link #prepareParentForShadow(ViewGroup)} must be called on parent of container
     * before using shadow.  Depending on Shadow type, optical bounds might be applied.
     */
    public void prepareParentForShadow(ViewGroup parent) {
        if (mShadowType == SHADOW_STATIC) {
            StaticShadowHelper.prepareParent(parent);
        }
    }

    public int getShadowType() {
        return mShadowType;
    }

    public boolean needsOverlay() {
        return mNeedsOverlay;
    }

    public boolean needsRoundedCorner() {
        return mNeedsRoundedCorner;
    }

    /**
     * Returns true if a "wrapper" ShadowOverlayContainer is needed.
     * When needsWrapper() is true,  call {@link #createShadowOverlayContainer(Context)}
     * to create the wrapper.
     */
    public boolean needsWrapper() {
        return mNeedsWrapper;
    }

    /**
     * Create ShadowOverlayContainer for this helper.
     * @param context   Context to create view.
     * @return          ShadowOverlayContainer.
     */
    public ShadowOverlayContainer createShadowOverlayContainer(Context context) {
        if (!needsWrapper()) {
            throw new IllegalArgumentException();
        }
        return new ShadowOverlayContainer(context, mShadowType, mNeedsOverlay,
                mUnfocusedZ, mFocusedZ, mRoundedCornerRadius);
    }

    /**
     * Set overlay color for view other than ShadowOverlayContainer.
     * See also {@link ShadowOverlayContainer#setOverlayColor(int)}.
     */
    public static void setNoneWrapperOverlayColor(View view, int color) {
        Drawable d = ForegroundHelper.getForeground(view);
        if (d instanceof ColorDrawable) {
            ((ColorDrawable) d).setColor(color);
        } else {
            ForegroundHelper.setForeground(view, new ColorDrawable(color));
        }
    }

    /**
     * Set overlay color for view, it can be a ShadowOverlayContainer if needsWrapper() is true,
     * or other view type.
     */
    public void setOverlayColor(View view, int color) {
        if (needsWrapper()) {
            ((ShadowOverlayContainer) view).setOverlayColor(color);
        } else {
            setNoneWrapperOverlayColor(view, color);
        }
    }

    /**
     * Must be called when view is created for cases {@link #needsWrapper()} is false.
     * @param view
     */
    public void onViewCreated(View view) {
        if (!needsWrapper()) {
            if (!mNeedsShadow) {
                if (mNeedsRoundedCorner) {
                    RoundedRectHelper.setClipToRoundedOutline(view, true, mRoundedCornerRadius);
                }
            } else {
                if (mShadowType == SHADOW_DYNAMIC) {
                    Object tag = ShadowHelper.addDynamicShadow(
                            view, mUnfocusedZ, mFocusedZ, mRoundedCornerRadius);
                    view.setTag(R.id.lb_shadow_impl, tag);
                } else if (mNeedsRoundedCorner) {
                    RoundedRectHelper.setClipToRoundedOutline(view, true, mRoundedCornerRadius);
                }
            }
        }
    }

    /**
     * Set shadow focus level (0 to 1). 0 for unfocused, 1 for fully focused.
     * This is for view other than ShadowOverlayContainer.
     * See also {@link ShadowOverlayContainer#setShadowFocusLevel(float)}.
     */
    public static void setNoneWrapperShadowFocusLevel(View view, float level) {
        setShadowFocusLevel(getNoneWrapperDynamicShadowImpl(view), SHADOW_DYNAMIC, level);
    }

    /**
     * Set shadow focus level (0 to 1). 0 for unfocused, 1 for fully focused.
     */
    public void setShadowFocusLevel(View view, float level) {
        if (needsWrapper()) {
            ((ShadowOverlayContainer) view).setShadowFocusLevel(level);
        } else {
            setShadowFocusLevel(getNoneWrapperDynamicShadowImpl(view), SHADOW_DYNAMIC, level);
        }
    }

    void setupDynamicShadowZ(Options options, Context context) {
        if (options.getDynamicShadowUnfocusedZ() < 0f) {
            Resources res = context.getResources();
            mFocusedZ = res.getDimension(R.dimen.lb_material_shadow_focused_z);
            mUnfocusedZ = res.getDimension(R.dimen.lb_material_shadow_normal_z);
        } else {
            mFocusedZ = options.getDynamicShadowFocusedZ();
            mUnfocusedZ = options.getDynamicShadowUnfocusedZ();
        }
    }

    void setupRoundedCornerRadius(Options options, Context context) {
        if (options.getRoundedCornerRadius() == 0) {
            Resources res = context.getResources();
            mRoundedCornerRadius = res.getDimensionPixelSize(
                        R.dimen.lb_rounded_rect_corner_radius);
        } else {
            mRoundedCornerRadius = options.getRoundedCornerRadius();
        }
    }

    static Object getNoneWrapperDynamicShadowImpl(View view) {
        return view.getTag(R.id.lb_shadow_impl);
    }

    static void setShadowFocusLevel(Object impl, int shadowType, float level) {
        if (impl != null) {
            if (level < 0f) {
                level = 0f;
            } else if (level > 1f) {
                level = 1f;
            }
            switch (shadowType) {
                case SHADOW_DYNAMIC:
                    ShadowHelper.setShadowFocusLevel(impl, level);
                    break;
                case SHADOW_STATIC:
                    StaticShadowHelper.setShadowFocusLevel(impl, level);
                    break;
            }
        }
    }
}
