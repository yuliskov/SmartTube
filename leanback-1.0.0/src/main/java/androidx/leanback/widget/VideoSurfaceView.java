/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

import androidx.annotation.RestrictTo;

/**
 * Activity transition will change transitionVisibility multiple times even the view is not
 * running transition, which causes visual flickering during activity return transition.
 * This class disables setTransitionVisibility() to avoid the problem.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class VideoSurfaceView extends SurfaceView {

    public VideoSurfaceView(Context context) {
        super(context);
    }

    public VideoSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Overrides hidden method View.setTransitionVisibility() to disable visibility changes
     * in activity transition.
     */
    public void setTransitionVisibility(int visibility) {
    }

}
