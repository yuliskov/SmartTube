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

package androidx.leanback.transition;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.leanback.R;
import androidx.leanback.widget.Parallax;

/**
 * A slide transition changes TRANSLATION attribute of view which is not exposed by any event.
 * In order to run a parallax effect with Slide transition, ParallaxTransition is running on the
 * side, calling ParallaxSource.updateValues() on every frame. User should make sure slide
 * and ParallaxTransition are using same duration and startDelay.
 *
 * @hide
 */
@RequiresApi(21)
@RestrictTo(LIBRARY_GROUP)
public class ParallaxTransition extends Visibility {

    static Interpolator sInterpolator = new LinearInterpolator();

    public ParallaxTransition() {
    }

    public ParallaxTransition(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    Animator createAnimator(View view) {
        final Parallax source = (Parallax) view.getTag(R.id.lb_parallax_source);
        if (source == null) {
            return null;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setInterpolator(sInterpolator);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                source.updateValues();
            }
        });
        return animator;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view,
                             TransitionValues startValues, TransitionValues endValues) {
        if (endValues == null) {
            return null;
        }
        return createAnimator(view);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view,
                                TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null) {
            return null;
        }
        return createAnimator(view);
    }
}
