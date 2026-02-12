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

import android.animation.Animator;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * FragmentAnimationProvider supplies animations for use during a fragment's onCreateAnimator
 * callback. Animators added here will be added to an animation set and played together. This
 * allows presenters used by a fragment to control their own fragment lifecycle animations.
 */
public interface FragmentAnimationProvider {

    /**
     * Animates the fragment in response to the IME appearing.
     * @param animators A list of animations to which this provider's animations should be added.
     */
    public abstract void onImeAppearing(@NonNull List<Animator> animators);

    /**
     * Animates the fragment in response to the IME disappearing.
     * @param animators A list of animations to which this provider's animations should be added.
     */
    public abstract void onImeDisappearing(@NonNull List<Animator> animators);

}
