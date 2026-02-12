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
package androidx.leanback.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.RestrictTo;

/**
 * Customized FrameLayout excludes margin of child from calculating the child size.
 * So we can change left margin of rows while keep the width of rows unchanged without
 * using hardcoded DIPS.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class BrowseRowsFrameLayout extends FrameLayout {

    public BrowseRowsFrameLayout(Context context) {
        this(context ,null);
    }

    public BrowseRowsFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BrowseRowsFrameLayout(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void measureChildWithMargins(View child,
            int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + widthUsed, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + heightUsed, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

}
