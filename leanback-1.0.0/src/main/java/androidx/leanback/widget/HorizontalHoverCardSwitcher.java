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

import android.graphics.Rect;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import androidx.core.view.ViewCompat;

/**
 * A helper class for showing a hover card view below a {@link HorizontalGridView}.  The hover card
 * is aligned to the starting edge of the selected child view.  If there is no space when scrolling
 * to the end, the ending edge of the hover card will be aligned to the ending edge of the parent
 * view, excluding padding.
 */
public final class HorizontalHoverCardSwitcher extends PresenterSwitcher {
    // left and right of selected card view
    int mCardLeft, mCardRight;

    private int[] mTmpOffsets = new int[2];
    private Rect mTmpRect = new Rect();

    @Override
    protected void insertView(View view) {
        // append hovercard to the end of container
        getParentViewGroup().addView(view);
    }

    @Override
    protected void onViewSelected(View view) {
        int rightLimit = getParentViewGroup().getWidth() - getParentViewGroup().getPaddingRight();
        int leftLimit = getParentViewGroup().getPaddingLeft();
        // measure the hover card width; if it's too large, align hover card
        // end edge with row view's end edge, otherwise align start edges.
        view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        MarginLayoutParams params = (MarginLayoutParams) view.getLayoutParams();
        boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
        if (!isRtl && mCardLeft + view.getMeasuredWidth() > rightLimit) {
            params.leftMargin = rightLimit  - view.getMeasuredWidth();
        } else if (isRtl && mCardLeft < leftLimit) {
            params.leftMargin = leftLimit;
        } else if (isRtl) {
            params.leftMargin = mCardRight - view.getMeasuredWidth();
        } else {
            params.leftMargin = mCardLeft;
        }
        view.requestLayout();
    }

    /**
     * Select a childView inside a grid view and create/bind a corresponding hover card view
     * for the object.
     */
    public void select(HorizontalGridView gridView, View childView, Object object) {
        ViewGroup parent = getParentViewGroup();
        gridView.getViewSelectedOffsets(childView, mTmpOffsets);
        mTmpRect.set(0, 0, childView.getWidth(), childView.getHeight());
        parent.offsetDescendantRectToMyCoords(childView, mTmpRect);
        mCardLeft = mTmpRect.left - mTmpOffsets[0];
        mCardRight = mTmpRect.right - mTmpOffsets[0];
        select(object);
    }

}
