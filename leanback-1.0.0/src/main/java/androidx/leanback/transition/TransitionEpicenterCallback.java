/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.graphics.Rect;

import androidx.annotation.RestrictTo;

/**
 * Class to get the epicenter of Transition.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class TransitionEpicenterCallback {

    /**
     * Implementers must override to return the epicenter of the Transition in screen
     * coordinates.
     *
     * @param transition The transition for which the epicenter applies.
     * @return The Rect region of the epicenter of <code>transition</code> or null if
     * there is no epicenter.
     */
    public abstract Rect onGetEpicenter(Object transition);
}
