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
package androidx.leanback.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * Root view of GuidedAction item, it supports a foreground drawable and can disable focus out
 * of view.
 */
class GuidedActionItemContainer extends NonOverlappingLinearLayoutWithForeground {

    private boolean mFocusOutAllowed = true;

    public GuidedActionItemContainer(Context context) {
        this(context, null);
    }

    public GuidedActionItemContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuidedActionItemContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (mFocusOutAllowed || !Util.isDescendant(this, focused)) {
            return super.focusSearch(focused, direction);
        }
        View view = super.focusSearch(focused, direction);
        if (Util.isDescendant(this, view)) {
            return view;
        }
        return null;
    }

    public void setFocusOutAllowed(boolean focusOutAllowed) {
        mFocusOutAllowed = focusOutAllowed;
    }
}
