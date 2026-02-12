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
package androidx.leanback.transition;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.animation.AnimationUtils;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * Helper class to load Leanback specific transition.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class LeanbackTransitionHelper {

    public static Object loadTitleInTransition(Context context) {
        if (Build.VERSION.SDK_INT < 19 || Build.VERSION.SDK_INT >= 21) {
            return TransitionHelper.loadTransition(context, R.transition.lb_title_in);
        }

        SlideKitkat slide = new SlideKitkat();
        slide.setSlideEdge(Gravity.TOP);
        slide.setInterpolator(AnimationUtils.loadInterpolator(context,
                android.R.anim.decelerate_interpolator));
        slide.addTarget(R.id.browse_title_group);
        return slide;
    }

    public static Object loadTitleOutTransition(Context context) {
        if (Build.VERSION.SDK_INT < 19 || Build.VERSION.SDK_INT >= 21) {
            return TransitionHelper.loadTransition(context, R.transition.lb_title_out);
        }

        SlideKitkat slide = new SlideKitkat();
        slide.setSlideEdge(Gravity.TOP);
        slide.setInterpolator(AnimationUtils.loadInterpolator(context,
                R.anim.lb_decelerator_4));
        slide.addTarget(R.id.browse_title_group);
        return slide;
    }

    private LeanbackTransitionHelper() {
    }
}
