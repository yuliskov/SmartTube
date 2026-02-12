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
import android.transition.Slide;
import android.util.AttributeSet;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RequiresApi(21)
@RestrictTo(LIBRARY_GROUP)
public class SlideNoPropagation extends Slide {

    public SlideNoPropagation() {
    }

    public SlideNoPropagation(int slideEdge) {
        super(slideEdge);
    }

    public SlideNoPropagation(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setSlideEdge(int slideEdge) {
        super.setSlideEdge(slideEdge);
        setPropagation(null);
    }
}
