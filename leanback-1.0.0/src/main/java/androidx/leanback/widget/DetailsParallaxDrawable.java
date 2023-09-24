/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.RestrictTo;
import androidx.leanback.R;
import androidx.leanback.graphics.CompositeDrawable;
import androidx.leanback.graphics.FitWidthBitmapDrawable;

/**
 * Helper class responsible for wiring in parallax effect in
 * {@link androidx.leanback.app.DetailsFragment}. The default effect will render
 * a drawable like the following two parts, cover drawable above DetailsOverviewRow and solid
 * color below DetailsOverviewRow.
 * <pre>
 *        ***************************
 *        *        Cover Drawable   *
 *        ***************************
 *        *    DetailsOverviewRow   *
 *        *                         *
 *        ***************************
 *        *     Bottom Drawable     *
 *        *      (Solid Color)      *
 *        *         Related         *
 *        *         Content         *
 *        ***************************
 * </pre>
 * <ul>
 * <li>
 * Call {@link #DetailsParallaxDrawable(Context, DetailsParallax)} to create DetailsParallaxDrawable
 * using {@link FitWidthBitmapDrawable} for cover drawable.
 * </li>
 * </ul>
 * <li>
 * In case the solid color is not set, it will use defaultBrandColorDark from LeanbackTheme.
 * </li>
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DetailsParallaxDrawable extends CompositeDrawable {
    private Drawable mBottomDrawable;

    /**
     * Creates a DetailsParallaxDrawable using a cover drawable.
     * @param context Context to get resource values.
     * @param parallax DetailsParallax to add background parallax effect.
     * @param coverDrawable Cover drawable at top
     * @param coverDrawableParallaxTarget Define a ParallaxTarget that would be performed on cover
     *                                    Drawable. e.g. To change "verticalOffset" of cover
     *                                    Drawable from 0 to 120 pixels above screen, uses:
     *                                    new ParallaxTarget.PropertyValuesHolderTarget(
     *                                        coverDrawable,
     *                                        PropertyValuesHolder.ofInt("verticalOffset", 0, -120))
     */
    public DetailsParallaxDrawable(Context context, DetailsParallax parallax,
                                   Drawable coverDrawable,
                                   ParallaxTarget coverDrawableParallaxTarget) {
        init(context, parallax, coverDrawable, new ColorDrawable(), coverDrawableParallaxTarget);
    }

    /**
     * Creates a DetailsParallaxDrawable using a cover drawable and bottom drawable.
     * @param context Context to get resource values.
     * @param parallax DetailsParallax to add background parallax effect.
     * @param coverDrawable Cover drawable at top
     * @param bottomDrawable Bottom drawable, when null it will create a default ColorDrawable.
     * @param coverDrawableParallaxTarget Define a ParallaxTarget that would be performed on cover
     *                                    Drawable. e.g. To change "verticalOffset" of cover
     *                                    Drawable from 0 to 120 pixels above screen, uses:
     *                                    new ParallaxTarget.PropertyValuesHolderTarget(
     *                                        coverDrawable,
     *                                        PropertyValuesHolder.ofInt("verticalOffset", 0, -120))
     */
    public DetailsParallaxDrawable(Context context, DetailsParallax parallax,
                                   Drawable coverDrawable, Drawable bottomDrawable,
                                   ParallaxTarget coverDrawableParallaxTarget) {

        init(context, parallax, coverDrawable, bottomDrawable, coverDrawableParallaxTarget);
    }

    /**
     * Creates DetailsParallaxDrawable using {@link FitWidthBitmapDrawable} for cover drawable.
     * @param context Context to get resource values.
     * @param parallax DetailsParallax to add background parallax effect.
     */
    public DetailsParallaxDrawable(Context context, DetailsParallax parallax) {
        int verticalMovementMax = -context.getResources().getDimensionPixelSize(
                R.dimen.lb_details_cover_drawable_parallax_movement);
        Drawable coverDrawable = new FitWidthBitmapDrawable();
        ParallaxTarget coverDrawableParallaxTarget = new ParallaxTarget.PropertyValuesHolderTarget(
                coverDrawable, PropertyValuesHolder.ofInt("verticalOffset", 0,
                verticalMovementMax));
        init(context, parallax, coverDrawable, new ColorDrawable(), coverDrawableParallaxTarget);
    }

    void init(Context context, DetailsParallax parallax,
              Drawable coverDrawable, Drawable bottomDrawable,
              ParallaxTarget coverDrawableParallaxTarget) {
        if (bottomDrawable instanceof ColorDrawable) {
            ColorDrawable colorDrawable = ((ColorDrawable) bottomDrawable);
            if (colorDrawable.getColor() == Color.TRANSPARENT) {
                colorDrawable.setColor(getDefaultBackgroundColor(context));
            }
        }
        addChildDrawable(coverDrawable);
        addChildDrawable(mBottomDrawable = bottomDrawable);
        connect(context, parallax, coverDrawableParallaxTarget);
    }

    private static int getDefaultBackgroundColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.defaultBrandColorDark, outValue, true)) {
            return context.getResources().getColor(outValue.resourceId);
        }
        return context.getResources().getColor(R.color.lb_default_brand_color_dark);
    }

    /**
     * @return First child which is cover drawable appearing at top.
     */
    public Drawable getCoverDrawable() {
        return getChildAt(0).getDrawable();
    }

    /**
     * @return Second child which is ColorDrawable by default.
     */
    public Drawable getBottomDrawable() {
        return mBottomDrawable;
    }

    /**
     * Changes the solid background color of the related content section.
     */
    public void setSolidColor(@ColorInt int color) {
        ((ColorDrawable) mBottomDrawable).setColor(color);
    }

    /**
     * @return Returns the solid background color of the related content section.
     */
    public @ColorInt int getSolidColor() {
        return ((ColorDrawable) mBottomDrawable).getColor();
    }

    /**
     * Connects DetailsParallaxDrawable to DetailsParallax object.
     * @param parallax The DetailsParallax object to add ParallaxEffects for the drawable.
     */
    void connect(Context context, DetailsParallax parallax,
                        ParallaxTarget coverDrawableParallaxTarget) {

        Parallax.IntProperty frameTop = parallax.getOverviewRowTop();
        Parallax.IntProperty frameBottom = parallax.getOverviewRowBottom();

        final int fromValue = context.getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_actions);
        final int toValue = context.getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_description);
        parallax.addEffect(frameTop.atAbsolute(fromValue), frameTop.atAbsolute(toValue))
                .target(coverDrawableParallaxTarget);

        // Add solid color parallax effect:
        // When frameBottom moves from bottom of the screen to top of the screen,
        // change solid ColorDrawable's top from bottom of screen to top of the screen.
        parallax.addEffect(frameBottom.atMax(), frameBottom.atMin())
                .target(getChildAt(1), ChildDrawable.TOP_ABSOLUTE);
        // Also when frameTop moves from bottom of screen to top of the screen,
        // we are changing bottom of the bitmap from bottom of screen to top of screen.
        parallax.addEffect(frameTop.atMax(), frameTop.atMin())
                .target(getChildAt(0), ChildDrawable.BOTTOM_ABSOLUTE);
    }

}
