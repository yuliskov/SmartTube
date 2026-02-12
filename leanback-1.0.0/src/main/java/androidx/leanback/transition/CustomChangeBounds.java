/*
 * Copyright 2018 The Android Open Source Project
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
import android.transition.ChangeBounds;
import android.transition.TransitionValues;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import java.util.HashMap;

/**
 * change bounds that support customized start delay.
 */
@RequiresApi(19)
class CustomChangeBounds extends ChangeBounds {

    int mDefaultStartDelay;
    // View -> delay
    final HashMap<View, Integer> mViewStartDelays = new HashMap<View, Integer>();
    // id -> delay
    final SparseIntArray mIdStartDelays = new SparseIntArray();
    // Class.getName() -> delay
    final HashMap<String, Integer> mClassStartDelays = new HashMap<String, Integer>();

    private int getDelay(View view) {
        Integer delay = mViewStartDelays.get(view);
        if (delay != null) {
            return delay;
        }
        int idStartDelay = mIdStartDelays.get(view.getId(), -1);
        if (idStartDelay != -1) {
            return idStartDelay;
        }
        delay = mClassStartDelays.get(view.getClass().getName());
        if (delay != null) {
            return delay;
        }
        return mDefaultStartDelay;
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        Animator animator = super.createAnimator(sceneRoot, startValues, endValues);
        if (animator != null && endValues != null && endValues.view != null) {
            animator.setStartDelay(getDelay(endValues.view));
        }
        return animator;
    }

    public void setStartDelay(View view, int startDelay) {
        mViewStartDelays.put(view, startDelay);
    }

    public void setStartDelay(int viewId, int startDelay) {
        mIdStartDelays.put(viewId, startDelay);
    }

    public void setStartDelay(String className, int startDelay) {
        mClassStartDelays.put(className, startDelay);
    }

    public void setDefaultStartDelay(int startDelay) {
        mDefaultStartDelay = startDelay;
    }
}
