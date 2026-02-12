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
package androidx.leanback.transition;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

@RequiresApi(19)
class Scale extends Transition {
    private static final String PROPNAME_SCALE = "android:leanback:scale";

    public Scale() {
    }

    private void captureValues(TransitionValues values) {
        View view = values.view;
        values.values.put(PROPNAME_SCALE, view.getScaleX());
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public Animator createAnimator(final ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return null;
        }

        final float startScale = (Float) startValues.values.get(PROPNAME_SCALE);
        final float endScale = (Float) endValues.values.get(PROPNAME_SCALE);

        final View view = startValues.view;
        view.setScaleX(startScale);
        view.setScaleY(startScale);

        ValueAnimator animator = ValueAnimator.ofFloat(startScale, endScale);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float scale = (Float) animation.getAnimatedValue();
                view.setScaleX(scale);
                view.setScaleY(scale);
            }
        });
        return animator;
    }
}
