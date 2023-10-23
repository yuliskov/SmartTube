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

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Optional facet provided by {@link RecyclerView.Adapter} or {@link RecyclerView.ViewHolder} for
 * use in {@link HorizontalGridView} and {@link VerticalGridView}. Apps using {@link Presenter} may
 * set facet using {@link Presenter#setFacet(Class, Object)} or
 * {@link Presenter.ViewHolder#setFacet(Class, Object)}. Facet on ViewHolder has a higher priority
 * than Presenter or Adapter.
 * <p>
 * ItemAlignmentFacet contains single or multiple {@link ItemAlignmentDef}s. First
 * {@link ItemAlignmentDef} describes the default alignment position for ViewHolder, it also
 * overrides the default item alignment settings on {@link VerticalGridView} and
 * {@link HorizontalGridView} (see {@link BaseGridView#setItemAlignmentOffset(int)} etc). One
 * ItemAlignmentFacet can have multiple {@link ItemAlignmentDef}s, e.g. having two aligned positions
 * when child1 gets focus or child2 gets focus. Grid view will visit focused view and its
 * ancestors till the root of ViewHolder to match {@link ItemAlignmentDef}s'
 * {@link ItemAlignmentDef#getItemAlignmentFocusViewId()}. Once a match found, the
 * {@link ItemAlignmentDef} is used to calculate alignment position.
 */
public final class ItemAlignmentFacet {

    /**
     * Value indicates that percent is not used. Equivalent to 0.
     */
    public final static float ITEM_ALIGN_OFFSET_PERCENT_DISABLED = -1;

    /**
     * Definition of an alignment position under a view.
     */
    public static class ItemAlignmentDef {
        int mViewId = View.NO_ID;
        int mFocusViewId = View.NO_ID;
        int mOffset = 0;
        float mOffsetPercent = 50f;
        boolean mOffsetWithPadding = false;
        private boolean mAlignToBaseline;

        /**
         * Sets number of pixels to the end of low edge. Supports right to left layout direction.
         * @param offset In left to right or vertical case, it's the offset added to left/top edge.
         *               In right to left case, it's the offset subtracted from right edge.
         */
        public final void setItemAlignmentOffset(int offset) {
            mOffset = offset;
        }

        /**
         * Returns number of pixels to the end of low edge. Supports right to left layout direction.
         * In left to right or vertical case, it's the offset added to left/top edge. In right to
         * left case, it's the offset subtracted from right edge.
         * @return Number of pixels to the end of low edge.
         */
        public final int getItemAlignmentOffset() {
            return mOffset;
        }

        /**
         * Sets whether applies padding to item alignment when
         * {@link #getItemAlignmentOffsetPercent()} is 0 or 100.
         * <p>When true:
         * Applies start/top padding if {@link #getItemAlignmentOffsetPercent()} is 0.
         * Applies end/bottom padding if {@link #getItemAlignmentOffsetPercent()} is 100.
         * Does not apply padding if {@link #getItemAlignmentOffsetPercent()} is neither 0 nor 100.
         * </p>
         * <p>When false: does not apply padding</p>
         */
        public final void setItemAlignmentOffsetWithPadding(boolean withPadding) {
            mOffsetWithPadding = withPadding;
        }

        /**
         * Returns true if applies padding to item alignment when
         * {@link #getItemAlignmentOffsetPercent()} is 0 or 100; returns false otherwise.
         * <p>When true:
         * Applies start/top padding when {@link #getItemAlignmentOffsetPercent()} is 0.
         * Applies end/bottom padding when {@link #getItemAlignmentOffsetPercent()} is 100.
         * Does not apply padding if {@link #getItemAlignmentOffsetPercent()} is neither 0 nor 100.
         * </p>
         * <p>When false: does not apply padding</p>
         */
        public final boolean isItemAlignmentOffsetWithPadding() {
            return mOffsetWithPadding;
        }

        /**
         * Sets the offset percent for item alignment in addition to offset.  E.g., 40
         * means 40% of width/height from the low edge. In the right to left case, it's the 40%
         * width from right edge. Use {@link #ITEM_ALIGN_OFFSET_PERCENT_DISABLED} to disable.
         */
        public final void setItemAlignmentOffsetPercent(float percent) {
            if ((percent < 0 || percent > 100)
                    && percent != ITEM_ALIGN_OFFSET_PERCENT_DISABLED) {
                throw new IllegalArgumentException();
            }
            mOffsetPercent = percent;
        }

        /**
         * Gets the offset percent for item alignment in addition to offset. E.g., 40
         * means 40% of the width from the low edge. In the right to left case, it's the 40% from
         * right edge. Use {@link #ITEM_ALIGN_OFFSET_PERCENT_DISABLED} to disable.
         */
        public final float getItemAlignmentOffsetPercent() {
            return mOffsetPercent;
        }

        /**
         * Sets Id of which child view to be aligned.  View.NO_ID refers to root view and should
         * be only used in first one.  Different view ids of {@link ItemAlignmentFacet
         * #getAlignmentDefs()} define multiple alignment steps within one itemView, e.g. there are
         * two child views R.id.child1 and R.id.child2. App may allocated two
         * {@link ItemAlignmentDef}s, one with view id R.id.child1, the other with view id
         * R.id.child2. Note this id may or may not be same as the child view that takes focus.
         *
         * @param viewId The id of child view that will be aligned to.
         * @see #setItemAlignmentFocusViewId(int)
         */
        public final void setItemAlignmentViewId(int viewId) {
            mViewId = viewId;
        }

        /**
         * Returns Id of which child view to be aligned.  View.NO_ID refers to root view and should
         * be only used in first one.  Different view ids of {@link ItemAlignmentFacet
         * #getAlignmentDefs()} define multiple alignment steps within one itemView, e.g. there are
         * two child views R.id.child1 and R.id.child2. App may allocated two
         * {@link ItemAlignmentDef}s, one with view id R.id.child1, the other with view id
         * R.id.child2. Note this id may or may not be same as the child view that takes focus.
         *
         * @see #setItemAlignmentFocusViewId(int)
         */
        public final int getItemAlignmentViewId() {
            return mViewId;
        }

        /**
         * Sets Id of which child view take focus for alignment.  When not set, it will use
         * use same id of {@link #getItemAlignmentViewId()}.
         * @param viewId The id of child view that will be focused to.
         */
        public final void setItemAlignmentFocusViewId(int viewId) {
            mFocusViewId = viewId;
        }

        /**
         * Returns Id of which child view take focus for alignment.  When not set, it will use
         * use same id of {@link #getItemAlignmentViewId()}
         */
        public final int getItemAlignmentFocusViewId() {
            return mFocusViewId != View.NO_ID ? mFocusViewId : mViewId;
        }

        /**
         * When true, align to {@link View#getBaseline()} for the view of with id equals
         * {@link #getItemAlignmentViewId()}; false otherwise.
         * @param alignToBaseline Boolean indicating whether to align to view baseline.
         */
        public final void setAlignedToTextViewBaseline(boolean alignToBaseline) {
            this.mAlignToBaseline = alignToBaseline;
        }

        /**
         * Returns true when View should be aligned to {@link View#getBaseline()}
         */
        public boolean isAlignedToTextViewBaseLine() {
            return mAlignToBaseline;
        }
    }

    private ItemAlignmentDef[] mAlignmentDefs = new ItemAlignmentDef[]{new ItemAlignmentDef()};

    public boolean isMultiAlignment() {
        return mAlignmentDefs.length > 1;
    }

    /**
     * Sets definitions of alignment positions.
     */
    public void setAlignmentDefs(ItemAlignmentDef[] defs) {
        if (defs == null || defs.length < 1) {
            throw new IllegalArgumentException();
        }
        mAlignmentDefs = defs;
    }

    /**
     * Returns read only definitions of alignment positions.
     */
    public ItemAlignmentDef[] getAlignmentDefs() {
        return mAlignmentDefs;
    }

}
