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

import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;
import static androidx.recyclerview.widget.RecyclerView.NO_ID;
import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;
import static androidx.recyclerview.widget.RecyclerView.VERTICAL;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.VisibleForTesting;
import androidx.collection.CircularIntArray;
import androidx.core.os.TraceCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Recycler;
import androidx.recyclerview.widget.RecyclerView.State;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class GridLayoutManager extends RecyclerView.LayoutManager {

    /*
     * LayoutParams for {@link HorizontalGridView} and {@link VerticalGridView}.
     * The class currently does two internal jobs:
     * - Saves optical bounds insets.
     * - Caches focus align view center.
     */
    final static class LayoutParams extends RecyclerView.LayoutParams {

        // For placement
        int mLeftInset;
        int mTopInset;
        int mRightInset;
        int mBottomInset;

        // For alignment
        private int mAlignX;
        private int mAlignY;
        private int[] mAlignMultiple;
        private ItemAlignmentFacet mAlignmentFacet;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(RecyclerView.LayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
        }

        int getAlignX() {
            return mAlignX;
        }

        int getAlignY() {
            return mAlignY;
        }

        int getOpticalLeft(View view) {
            return view.getLeft() + mLeftInset;
        }

        int getOpticalTop(View view) {
            return view.getTop() + mTopInset;
        }

        int getOpticalRight(View view) {
            return view.getRight() - mRightInset;
        }

        int getOpticalBottom(View view) {
            return view.getBottom() - mBottomInset;
        }

        int getOpticalWidth(View view) {
            return view.getWidth() - mLeftInset - mRightInset;
        }

        int getOpticalHeight(View view) {
            return view.getHeight() - mTopInset - mBottomInset;
        }

        int getOpticalLeftInset() {
            return mLeftInset;
        }

        int getOpticalRightInset() {
            return mRightInset;
        }

        int getOpticalTopInset() {
            return mTopInset;
        }

        int getOpticalBottomInset() {
            return mBottomInset;
        }

        void setAlignX(int alignX) {
            mAlignX = alignX;
        }

        void setAlignY(int alignY) {
            mAlignY = alignY;
        }

        void setItemAlignmentFacet(ItemAlignmentFacet facet) {
            mAlignmentFacet = facet;
        }

        ItemAlignmentFacet getItemAlignmentFacet() {
            return mAlignmentFacet;
        }

        void calculateItemAlignments(int orientation, View view) {
            ItemAlignmentFacet.ItemAlignmentDef[] defs = mAlignmentFacet.getAlignmentDefs();
            if (mAlignMultiple == null || mAlignMultiple.length != defs.length) {
                mAlignMultiple = new int[defs.length];
            }
            for (int i = 0; i < defs.length; i++) {
                mAlignMultiple[i] = ItemAlignmentFacetHelper
                        .getAlignmentPosition(view, defs[i], orientation);
            }
            if (orientation == HORIZONTAL) {
                mAlignX = mAlignMultiple[0];
            } else {
                mAlignY = mAlignMultiple[0];
            }
        }

        int[] getAlignMultiple() {
            return mAlignMultiple;
        }

        void setOpticalInsets(int leftInset, int topInset, int rightInset, int bottomInset) {
            mLeftInset = leftInset;
            mTopInset = topInset;
            mRightInset = rightInset;
            mBottomInset = bottomInset;
        }

    }

    /**
     * Base class which scrolls to selected view in onStop().
     */
    abstract class GridLinearSmoothScroller extends LinearSmoothScroller {
        boolean mSkipOnStopInternal;

        GridLinearSmoothScroller() {
            super(mBaseGridView.getContext());
        }

        @Override
        protected void onStop() {
            super.onStop();
            if (!mSkipOnStopInternal) {
                onStopInternal();
            }
            if (mCurrentSmoothScroller == this) {
                mCurrentSmoothScroller = null;
            }
            if (mPendingMoveSmoothScroller == this) {
                mPendingMoveSmoothScroller = null;
            }
        }

        protected void onStopInternal() {
            // onTargetFound() may not be called if we hit the "wall" first or get cancelled.
            View targetView = findViewByPosition(getTargetPosition());
            if (targetView == null) {
                if (getTargetPosition() >= 0) {
                    // if smooth scroller is stopped without target, immediately jumps
                    // to the target position.
                    scrollToSelection(getTargetPosition(), 0, false, 0);
                }
                return;
            }
            if (mFocusPosition != getTargetPosition()) {
                // This should not happen since we cropped value in startPositionSmoothScroller()
                mFocusPosition = getTargetPosition();
            }
            if (hasFocus()) {
                mFlag |= PF_IN_SELECTION;
                targetView.requestFocus();
                mFlag &= ~PF_IN_SELECTION;
            }
            dispatchChildSelected();
            dispatchChildSelectedAndPositioned();
        }

        @Override
        protected int calculateTimeForScrolling(int dx) {
            int ms = super.calculateTimeForScrolling(dx);
            if (mWindowAlignment.mainAxis().getSize() > 0) {
                float minMs = (float) MIN_MS_SMOOTH_SCROLL_MAIN_SCREEN
                        / mWindowAlignment.mainAxis().getSize() * dx;
                if (ms < minMs) {
                    ms = (int) minMs;
                }
            }
            return ms;
        }

        @Override
        protected void onTargetFound(View targetView,
                RecyclerView.State state, Action action) {
            if (getScrollPosition(targetView, null, sTwoInts)) {
                int dx, dy;
                if (mOrientation == HORIZONTAL) {
                    dx = sTwoInts[0];
                    dy = sTwoInts[1];
                } else {
                    dx = sTwoInts[1];
                    dy = sTwoInts[0];
                }
                final int distance = (int) Math.sqrt(dx * dx + dy * dy);
                final int time = calculateTimeForDeceleration(distance);
                action.update(dx, dy, time, mDecelerateInterpolator);
            }
        }
    }

    /**
     * The SmoothScroller that remembers pending DPAD keys and consume pending keys
     * during scroll.
     */
    final class PendingMoveSmoothScroller extends GridLinearSmoothScroller {
        // -2 is a target position that LinearSmoothScroller can never find until
        // consumePendingMovesXXX() sets real targetPosition.
        final static int TARGET_UNDEFINED = -2;
        // whether the grid is staggered.
        private final boolean mStaggeredGrid;
        // Number of pending movements on primary direction, negative if PREV_ITEM.
        private int mPendingMoves;

        PendingMoveSmoothScroller(int initialPendingMoves, boolean staggeredGrid) {
            mPendingMoves = initialPendingMoves;
            mStaggeredGrid = staggeredGrid;
            setTargetPosition(TARGET_UNDEFINED);
        }

        void increasePendingMoves() {
            if (mPendingMoves < mMaxPendingMoves) {
                mPendingMoves++;
            }
        }

        void decreasePendingMoves() {
            if (mPendingMoves > -mMaxPendingMoves) {
                mPendingMoves--;
            }
        }

        /**
         * Called before laid out an item when non-staggered grid can handle pending movements
         * by skipping "mNumRows" per movement;  staggered grid will have to wait the item
         * has been laid out in consumePendingMovesAfterLayout().
         */
        void consumePendingMovesBeforeLayout() {
            if (mStaggeredGrid || mPendingMoves == 0) {
                return;
            }
            View newSelected = null;
            int startPos = mPendingMoves > 0 ? mFocusPosition + mNumRows :
                    mFocusPosition - mNumRows;
            for (int pos = startPos; mPendingMoves != 0;
                    pos = mPendingMoves > 0 ? pos + mNumRows: pos - mNumRows) {
                View v = findViewByPosition(pos);
                if (v == null) {
                    break;
                }
                if (!canScrollTo(v)) {
                    continue;
                }
                newSelected = v;
                mFocusPosition = pos;
                mSubFocusPosition = 0;
                if (mPendingMoves > 0) {
                    mPendingMoves--;
                } else {
                    mPendingMoves++;
                }
            }
            if (newSelected != null && hasFocus()) {
                mFlag |= PF_IN_SELECTION;
                newSelected.requestFocus();
                mFlag &= ~PF_IN_SELECTION;
            }
        }

        /**
         * Called after laid out an item.  Staggered grid should find view on same
         * Row and consume pending movements.
         */
        void consumePendingMovesAfterLayout() {
            if (mStaggeredGrid && mPendingMoves != 0) {
                // consume pending moves, focus to item on the same row.
                mPendingMoves = processSelectionMoves(true, mPendingMoves);
            }
            if (mPendingMoves == 0 || (mPendingMoves > 0 && hasCreatedLastItem())
                    || (mPendingMoves < 0 && hasCreatedFirstItem())) {
                setTargetPosition(mFocusPosition);
                stop();
            }
        }

        @Override
        protected void updateActionForInterimTarget(Action action) {
            if (mPendingMoves == 0) {
                return;
            }
            super.updateActionForInterimTarget(action);
        }

        @Override
        public PointF computeScrollVectorForPosition(int targetPosition) {
            if (mPendingMoves == 0) {
                return null;
            }
            int direction = ((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0
                    ? mPendingMoves > 0 : mPendingMoves < 0)
                    ? -1 : 1;
            if (mOrientation == HORIZONTAL) {
                return new PointF(direction, 0);
            } else {
                return new PointF(0, direction);
            }
        }

        @Override
        protected void onStopInternal() {
            super.onStopInternal();
            // if we hit wall,  need clear the remaining pending moves.
            mPendingMoves = 0;
            View v = findViewByPosition(getTargetPosition());
            if (v != null) scrollToView(v, true);
        }
    };

    private static final String TAG = "GridLayoutManager";
    static final boolean DEBUG = false;
    static final boolean TRACE = false;

    // maximum pending movement in one direction.
    static final int DEFAULT_MAX_PENDING_MOVES = 10;
    int mMaxPendingMoves = DEFAULT_MAX_PENDING_MOVES;
    // minimal milliseconds to scroll window size in major direction,  we put a cap to prevent the
    // effect smooth scrolling too over to bind an item view then drag the item view back.
    final static int MIN_MS_SMOOTH_SCROLL_MAIN_SCREEN = 30;

    String getTag() {
        return TAG + ":" + mBaseGridView.getId();
    }

    final BaseGridView mBaseGridView;

    /**
     * Note on conventions in the presence of RTL layout directions:
     * Many properties and method names reference entities related to the
     * beginnings and ends of things.  In the presence of RTL flows,
     * it may not be clear whether this is intended to reference a
     * quantity that changes direction in RTL cases, or a quantity that
     * does not.  Here are the conventions in use:
     *
     * start/end: coordinate quantities - do reverse
     * (optical) left/right: coordinate quantities - do not reverse
     * low/high: coordinate quantities - do not reverse
     * min/max: coordinate quantities - do not reverse
     * scroll offset - coordinate quantities - do not reverse
     * first/last: positional indices - do not reverse
     * front/end: positional indices - do not reverse
     * prepend/append: related to positional indices - do not reverse
     *
     * Note that although quantities do not reverse in RTL flows, their
     * relationship does.  In LTR flows, the first positional index is
     * leftmost; in RTL flows, it is rightmost.  Thus, anywhere that
     * positional quantities are mapped onto coordinate quantities,
     * the flow must be checked and the logic reversed.
     */

    /**
     * The orientation of a "row".
     */
    @RecyclerView.Orientation
    int mOrientation = HORIZONTAL;
    private OrientationHelper mOrientationHelper = OrientationHelper.createHorizontalHelper(this);

    RecyclerView.State mState;
    // Suppose currently showing 4, 5, 6, 7; removing 2,3,4 will make the layoutPosition to be
    // 2(deleted), 3, 4, 5 in prelayout pass. So when we add item in prelayout, we must subtract 2
    // from index of Grid.createItem.
    int mPositionDeltaInPreLayout;
    // Extra layout space needs to fill in prelayout pass. Note we apply the extra space to both
    // appends and prepends due to the fact leanback is doing mario scrolling: removing items to
    // the left of focused item might need extra layout on the right.
    int mExtraLayoutSpaceInPreLayout;
    // mPositionToRowInPostLayout and mDisappearingPositions are temp variables in post layout.
    final SparseIntArray mPositionToRowInPostLayout = new SparseIntArray();
    int[] mDisappearingPositions;

    RecyclerView.Recycler mRecycler;

    private static final Rect sTempRect = new Rect();

    // 2 bits mask is for 3 STAGEs: 0, PF_STAGE_LAYOUT or PF_STAGE_SCROLL.
    static final int PF_STAGE_MASK = 0x3;
    static final int PF_STAGE_LAYOUT = 0x1;
    static final int PF_STAGE_SCROLL = 0x2;

    // Flag for "in fast relayout", determined by layoutInit() result.
    static final int PF_FAST_RELAYOUT = 1 << 2;

    // Flag for the selected item being updated in fast relayout.
    static final int PF_FAST_RELAYOUT_UPDATED_SELECTED_POSITION = 1 << 3;
    /**
     * During full layout pass, when GridView had focus: onLayoutChildren will
     * skip non-focusable child and adjust mFocusPosition.
     */
    static final int PF_IN_LAYOUT_SEARCH_FOCUS = 1 << 4;

    // flag to prevent reentry if it's already processing selection request.
    static final int PF_IN_SELECTION = 1 << 5;

    // Represents whether child views are temporarily sliding out
    static final int PF_SLIDING = 1 << 6;
    static final int PF_LAYOUT_EATEN_IN_SLIDING = 1 << 7;

    /**
     * Force a full layout under certain situations.  E.g. Rows change, jump to invisible child.
     */
    static final int PF_FORCE_FULL_LAYOUT = 1 << 8;

    /**
     * True if layout is enabled.
     */
    static final int PF_LAYOUT_ENABLED = 1 << 9;

    /**
     * Flag controlling whether the current/next layout should
     * be updating the secondary size of rows.
     */
    static final int PF_ROW_SECONDARY_SIZE_REFRESH = 1 << 10;

    /**
     *  Allow DPAD key to navigate out at the front of the View (where position = 0),
     *  default is false.
     */
    static final int PF_FOCUS_OUT_FRONT = 1 << 11;

    /**
     * Allow DPAD key to navigate out at the end of the view, default is false.
     */
    static final int PF_FOCUS_OUT_END = 1 << 12;

    static final int PF_FOCUS_OUT_MASKS = PF_FOCUS_OUT_FRONT | PF_FOCUS_OUT_END;

    /**
     *  Allow DPAD key to navigate out of second axis.
     *  default is true.
     */
    static final int PF_FOCUS_OUT_SIDE_START = 1 << 13;

    /**
     * Allow DPAD key to navigate out of second axis.
     */
    static final int PF_FOCUS_OUT_SIDE_END = 1 << 14;

    static final int PF_FOCUS_OUT_SIDE_MASKS = PF_FOCUS_OUT_SIDE_START | PF_FOCUS_OUT_SIDE_END;

    /**
     * True if focus search is disabled.
     */
    static final int PF_FOCUS_SEARCH_DISABLED = 1 << 15;

    /**
     * True if prune child,  might be disabled during transition.
     */
    static final int PF_PRUNE_CHILD = 1 << 16;

    /**
     * True if scroll content,  might be disabled during transition.
     */
    static final int PF_SCROLL_ENABLED = 1 << 17;

    /**
     * Set to true for RTL layout in horizontal orientation
     */
    static final int PF_REVERSE_FLOW_PRIMARY = 1 << 18;

    /**
     * Set to true for RTL layout in vertical orientation
     */
    static final int PF_REVERSE_FLOW_SECONDARY = 1 << 19;

    static final int PF_REVERSE_FLOW_MASK = PF_REVERSE_FLOW_PRIMARY | PF_REVERSE_FLOW_SECONDARY;

    int mFlag = PF_LAYOUT_ENABLED
            | PF_FOCUS_OUT_SIDE_START | PF_FOCUS_OUT_SIDE_END
            | PF_PRUNE_CHILD | PF_SCROLL_ENABLED;

    private OnChildSelectedListener mChildSelectedListener = null;

    private ArrayList<OnChildViewHolderSelectedListener> mChildViewHolderSelectedListeners = null;

    OnChildLaidOutListener mChildLaidOutListener = null;

    /**
     * The focused position, it's not the currently visually aligned position
     * but it is the final position that we intend to focus on. If there are
     * multiple setSelection() called, mFocusPosition saves last value.
     */
    int mFocusPosition = NO_POSITION;

    /**
     * A view can have multiple alignment position,  this is the index of which
     * alignment is used,  by default is 0.
     */
    int mSubFocusPosition = 0;

    /**
     * Current running SmoothScroller.
     */
    GridLinearSmoothScroller mCurrentSmoothScroller;

    /**
     * LinearSmoothScroller that consume pending DPAD movements. Can be same object as
     * mCurrentSmoothScroller when mCurrentSmoothScroller is PendingMoveSmoothScroller.
     */
    PendingMoveSmoothScroller mPendingMoveSmoothScroller;

    /**
     * The offset to be applied to mFocusPosition, due to adapter change, on the next
     * layout.  Set to Integer.MIN_VALUE means we should stop adding delta to mFocusPosition
     * until next layout cycler.
     * TODO:  This is somewhat duplication of RecyclerView getOldPosition() which is
     * unfortunately cleared after prelayout.
     */
    private int mFocusPositionOffset = 0;

    /**
     * Extra pixels applied on primary direction.
     */
    private int mPrimaryScrollExtra;

    /**
     * override child visibility
     */
    @Visibility
    int mChildVisibility;

    /**
     * Pixels that scrolled in secondary forward direction. Negative value means backward.
     * Note that we treat secondary differently than main. For the main axis, update scroll min/max
     * based on first/last item's view location. For second axis, we don't use item's view location.
     * We are using the {@link #getRowSizeSecondary(int)} plus mScrollOffsetSecondary. see
     * details in {@link #updateSecondaryScrollLimits()}.
     */
    int mScrollOffsetSecondary;

    /**
     * User-specified row height/column width.  Can be WRAP_CONTENT.
     */
    private int mRowSizeSecondaryRequested;

    /**
     * The fixed size of each grid item in the secondary direction. This corresponds to
     * the row height, equal for all rows. Grid items may have variable length
     * in the primary direction.
     */
    private int mFixedRowSizeSecondary;

    /**
     * Tracks the secondary size of each row.
     */
    private int[] mRowSizeSecondary;

    /**
     * The maximum measured size of the view.
     */
    private int mMaxSizeSecondary;

    /**
     * Margin between items.
     */
    private int mHorizontalSpacing;
    /**
     * Margin between items vertically.
     */
    private int mVerticalSpacing;
    /**
     * Margin in main direction.
     */
    private int mSpacingPrimary;
    /**
     * Margin in second direction.
     */
    private int mSpacingSecondary;
    /**
     * How to position child in secondary direction.
     */
    private int mGravity = Gravity.START | Gravity.TOP;
    /**
     * The number of rows in the grid.
     */
    int mNumRows;
    /**
     * Number of rows requested, can be 0 to be determined by parent size and
     * rowHeight.
     */
    private int mNumRowsRequested = 1;

    /**
     * Saves grid information of each view.
     */
    Grid mGrid;

    /**
     * Focus Scroll strategy.
     */
    private int mFocusScrollStrategy = BaseGridView.FOCUS_SCROLL_ALIGNED;
    /**
     * Defines how item view is aligned in the window.
     */
    final WindowAlignment mWindowAlignment = new WindowAlignment();

    /**
     * Defines how item view is aligned.
     */
    private final ItemAlignment mItemAlignment = new ItemAlignment();

    /**
     * Dimensions of the view, width or height depending on orientation.
     */
    private int mSizePrimary;

    /**
     * Pixels of extra space for layout item (outside the widget)
     */
    private int mExtraLayoutSpace;

    /**
     * Temporary variable: an int array of length=2.
     */
    static int[] sTwoInts = new int[2];

    /**
     * Temporaries used for measuring.
     */
    private int[] mMeasuredDimension = new int[2];

    final ViewsStateBundle mChildrenStates = new ViewsStateBundle();

    /**
     * Optional interface implemented by Adapter.
     */
    private FacetProviderAdapter mFacetProviderAdapter;

    private boolean mIsPortrait;

    public GridLayoutManager(BaseGridView baseGridView) {
        mBaseGridView = baseGridView;
        mChildVisibility = -1;
        // disable prefetch by default, prefetch causes regression on low power chipset
        setItemPrefetchEnabled(false);
    }

    public void setOrientation(@RecyclerView.Orientation int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            if (DEBUG) Log.v(getTag(), "invalid orientation: " + orientation);
            return;
        }

        mOrientation = orientation;
        mOrientationHelper = OrientationHelper.createOrientationHelper(this, mOrientation);
        mWindowAlignment.setOrientation(orientation);
        mItemAlignment.setOrientation(orientation);
        mFlag |= PF_FORCE_FULL_LAYOUT;
    }

    public void onRtlPropertiesChanged(int layoutDirection) {
        final int flags;
        if (mOrientation == HORIZONTAL) {
            flags = layoutDirection == View.LAYOUT_DIRECTION_RTL ? PF_REVERSE_FLOW_PRIMARY : 0;
        } else {
            flags = layoutDirection == View.LAYOUT_DIRECTION_RTL ? PF_REVERSE_FLOW_SECONDARY : 0;
        }
        if ((mFlag & PF_REVERSE_FLOW_MASK) == flags) {
            return;
        }
        mFlag = (mFlag & ~PF_REVERSE_FLOW_MASK) | flags;
        mFlag |= PF_FORCE_FULL_LAYOUT;
        mWindowAlignment.horizontal.setReversedFlow(layoutDirection == View.LAYOUT_DIRECTION_RTL);
    }

    public int getFocusScrollStrategy() {
        return mFocusScrollStrategy;
    }

    public void setFocusScrollStrategy(int focusScrollStrategy) {
        mFocusScrollStrategy = focusScrollStrategy;
    }

    public void setWindowAlignment(int windowAlignment) {
        mWindowAlignment.mainAxis().setWindowAlignment(windowAlignment);
    }

    public int getWindowAlignment() {
        return mWindowAlignment.mainAxis().getWindowAlignment();
    }

    public void setWindowAlignmentOffset(int alignmentOffset) {
        mWindowAlignment.mainAxis().setWindowAlignmentOffset(alignmentOffset);
    }

    public int getWindowAlignmentOffset() {
        return mWindowAlignment.mainAxis().getWindowAlignmentOffset();
    }

    public void setWindowAlignmentOffsetPercent(float offsetPercent) {
        mWindowAlignment.mainAxis().setWindowAlignmentOffsetPercent(offsetPercent);
    }

    public float getWindowAlignmentOffsetPercent() {
        return mWindowAlignment.mainAxis().getWindowAlignmentOffsetPercent();
    }

    public void setItemAlignmentOffset(int alignmentOffset) {
        mItemAlignment.mainAxis().setItemAlignmentOffset(alignmentOffset);
        updateChildAlignments();
    }

    public int getItemAlignmentOffset() {
        return mItemAlignment.mainAxis().getItemAlignmentOffset();
    }

    public void setItemAlignmentOffsetWithPadding(boolean withPadding) {
        mItemAlignment.mainAxis().setItemAlignmentOffsetWithPadding(withPadding);
        updateChildAlignments();
    }

    public boolean isItemAlignmentOffsetWithPadding() {
        return mItemAlignment.mainAxis().isItemAlignmentOffsetWithPadding();
    }

    public void setItemAlignmentOffsetPercent(float offsetPercent) {
        mItemAlignment.mainAxis().setItemAlignmentOffsetPercent(offsetPercent);
        updateChildAlignments();
    }

    public float getItemAlignmentOffsetPercent() {
        return mItemAlignment.mainAxis().getItemAlignmentOffsetPercent();
    }

    public void setItemAlignmentViewId(int viewId) {
        mItemAlignment.mainAxis().setItemAlignmentViewId(viewId);
        updateChildAlignments();
    }

    public int getItemAlignmentViewId() {
        return mItemAlignment.mainAxis().getItemAlignmentViewId();
    }

    public void setFocusOutAllowed(boolean throughFront, boolean throughEnd) {
        mFlag = (mFlag & ~PF_FOCUS_OUT_MASKS)
                | (throughFront ? PF_FOCUS_OUT_FRONT : 0)
                | (throughEnd ? PF_FOCUS_OUT_END : 0);
    }

    public void setFocusOutSideAllowed(boolean throughStart, boolean throughEnd) {
        mFlag = (mFlag & ~PF_FOCUS_OUT_SIDE_MASKS)
                | (throughStart ? PF_FOCUS_OUT_SIDE_START : 0)
                | (throughEnd ? PF_FOCUS_OUT_SIDE_END : 0);
    }

    public void setNumRows(int numRows) {
        if (numRows < 0) throw new IllegalArgumentException();
        mNumRowsRequested = numRows;
    }

    /**
     * Set the row height. May be WRAP_CONTENT, or a size in pixels.
     */
    public void setRowHeight(int height) {
        if (height >= 0 || height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            mRowSizeSecondaryRequested = height;
        } else {
            throw new IllegalArgumentException("Invalid row height: " + height);
        }
    }

    public void setItemSpacing(int space) {
        mVerticalSpacing = mHorizontalSpacing = space;
        mSpacingPrimary = mSpacingSecondary = space;
    }

    public void setVerticalSpacing(int space) {
        if (mOrientation == VERTICAL) {
            mSpacingPrimary = mVerticalSpacing = space;
        } else {
            mSpacingSecondary = mVerticalSpacing = space;
        }
    }

    public void setHorizontalSpacing(int space) {
        if (mOrientation == HORIZONTAL) {
            mSpacingPrimary = mHorizontalSpacing = space;
        } else {
            mSpacingSecondary = mHorizontalSpacing = space;
        }
    }

    public int getVerticalSpacing() {
        return mVerticalSpacing;
    }

    public int getHorizontalSpacing() {
        return mHorizontalSpacing;
    }

    public void setGravity(int gravity) {
        mGravity = gravity;
    }

    protected boolean hasDoneFirstLayout() {
        return mGrid != null;
    }

    public void setOnChildSelectedListener(OnChildSelectedListener listener) {
        mChildSelectedListener = listener;
    }

    public void setOnChildViewHolderSelectedListener(OnChildViewHolderSelectedListener listener) {
        if (listener == null) {
            mChildViewHolderSelectedListeners = null;
            return;
        }
        if (mChildViewHolderSelectedListeners == null) {
            mChildViewHolderSelectedListeners = new ArrayList<OnChildViewHolderSelectedListener>();
        } else {
            mChildViewHolderSelectedListeners.clear();
        }
        mChildViewHolderSelectedListeners.add(listener);
    }

    public void addOnChildViewHolderSelectedListener(OnChildViewHolderSelectedListener listener) {
        if (mChildViewHolderSelectedListeners == null) {
            mChildViewHolderSelectedListeners = new ArrayList<OnChildViewHolderSelectedListener>();
        }
        mChildViewHolderSelectedListeners.add(listener);
    }

    public void removeOnChildViewHolderSelectedListener(OnChildViewHolderSelectedListener
            listener) {
        if (mChildViewHolderSelectedListeners != null) {
            mChildViewHolderSelectedListeners.remove(listener);
        }
    }

    boolean hasOnChildViewHolderSelectedListener() {
        return mChildViewHolderSelectedListeners != null
                && mChildViewHolderSelectedListeners.size() > 0;
    }

    void fireOnChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child,
            int position, int subposition) {
        if (mChildViewHolderSelectedListeners == null) {
            return;
        }
        for (int i = mChildViewHolderSelectedListeners.size() - 1; i >= 0 ; i--) {
            mChildViewHolderSelectedListeners.get(i).onChildViewHolderSelected(parent, child,
                    position, subposition);
        }
    }

    void fireOnChildViewHolderSelectedAndPositioned(RecyclerView parent, RecyclerView.ViewHolder
            child, int position, int subposition) {
        if (mChildViewHolderSelectedListeners == null) {
            return;
        }
        for (int i = mChildViewHolderSelectedListeners.size() - 1; i >= 0 ; i--) {
            mChildViewHolderSelectedListeners.get(i).onChildViewHolderSelectedAndPositioned(parent,
                    child, position, subposition);
        }
    }

    void setOnChildLaidOutListener(OnChildLaidOutListener listener) {
        mChildLaidOutListener = listener;
    }

    private int getAdapterPositionByView(View view) {
        if (view == null) {
            return NO_POSITION;
        }
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (params == null || params.isItemRemoved()) {
            // when item is removed, the position value can be any value.
            return NO_POSITION;
        }
        return params.getViewAdapterPosition();
    }

    int getSubPositionByView(View view, View childView) {
        if (view == null || childView == null) {
            return 0;
        }
        final LayoutParams lp = (LayoutParams) view.getLayoutParams();
        final ItemAlignmentFacet facet = lp.getItemAlignmentFacet();
        if (facet != null) {
            final ItemAlignmentFacet.ItemAlignmentDef[] defs = facet.getAlignmentDefs();
            if (defs.length > 1) {
                while (childView != view) {
                    int id = childView.getId();
                    if (id != View.NO_ID) {
                        for (int i = 1; i < defs.length; i++) {
                            if (defs[i].getItemAlignmentFocusViewId() == id) {
                                return i;
                            }
                        }
                    }
                    childView = (View) childView.getParent();
                }
            }
        }
        return 0;
    }

    private int getAdapterPositionByIndex(int index) {
        return getAdapterPositionByView(getChildAt(index));
    }

    void dispatchChildSelected() {
        if (mChildSelectedListener == null && !hasOnChildViewHolderSelectedListener()) {
            return;
        }

        if (TRACE) TraceCompat.beginSection("onChildSelected");
        View view = mFocusPosition == NO_POSITION ? null : findViewByPosition(mFocusPosition);
        if (view != null) {
            RecyclerView.ViewHolder vh = mBaseGridView.getChildViewHolder(view);
            if (mChildSelectedListener != null) {
                mChildSelectedListener.onChildSelected(mBaseGridView, view, mFocusPosition,
                        vh == null? NO_ID: vh.getItemId());
            }
            fireOnChildViewHolderSelected(mBaseGridView, vh, mFocusPosition, mSubFocusPosition);
        } else {
            if (mChildSelectedListener != null) {
                mChildSelectedListener.onChildSelected(mBaseGridView, null, NO_POSITION, NO_ID);
            }
            fireOnChildViewHolderSelected(mBaseGridView, null, NO_POSITION, 0);
        }
        if (TRACE) TraceCompat.endSection();

        // Children may request layout when a child selection event occurs (such as a change of
        // padding on the current and previously selected rows).
        // If in layout, a child requesting layout may have been laid out before the selection
        // callback.
        // If it was not, the child will be laid out after the selection callback.
        // If so, the layout request will be honoured though the view system will emit a double-
        // layout warning.
        // If not in layout, we may be scrolling in which case the child layout request will be
        // eaten by recyclerview.  Post a requestLayout.
        if ((mFlag & PF_STAGE_MASK) != PF_STAGE_LAYOUT && !mBaseGridView.isLayoutRequested()) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                if (getChildAt(i).isLayoutRequested()) {
                    forceRequestLayout();
                    break;
                }
            }
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void dispatchChildSelectedAndPositioned() {
        if (!hasOnChildViewHolderSelectedListener()) {
            return;
        }

        if (TRACE) TraceCompat.beginSection("onChildSelectedAndPositioned");
        View view = mFocusPosition == NO_POSITION ? null : findViewByPosition(mFocusPosition);
        if (view != null) {
            RecyclerView.ViewHolder vh = mBaseGridView.getChildViewHolder(view);
            fireOnChildViewHolderSelectedAndPositioned(mBaseGridView, vh, mFocusPosition,
                    mSubFocusPosition);
        } else {
            if (mChildSelectedListener != null) {
                mChildSelectedListener.onChildSelected(mBaseGridView, null, NO_POSITION, NO_ID);
            }
            fireOnChildViewHolderSelectedAndPositioned(mBaseGridView, null, NO_POSITION, 0);
        }
        if (TRACE) TraceCompat.endSection();

    }

    @Override
    public boolean canScrollHorizontally() {
        // We can scroll horizontally if we have horizontal orientation, or if
        // we are vertical and have more than one column.
        // MOD: fix VerticalGridView overscroll in touch mode
        // MOD: use regular behavior in portrait mode (content clipped)
        //return mOrientation == HORIZONTAL || mNumRows > 1;
        return mOrientation == HORIZONTAL || (mNumRows > 1 && mIsPortrait);
    }

    @Override
    public boolean canScrollVertically() {
        // We can scroll vertically if we have vertical orientation, or if we
        // are horizontal and have more than one row.
        return mOrientation == VERTICAL || mNumRows > 1;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context context, AttributeSet attrs) {
        return new LayoutParams(context, attrs);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        } else if (lp instanceof RecyclerView.LayoutParams) {
            return new LayoutParams((RecyclerView.LayoutParams) lp);
        } else if (lp instanceof MarginLayoutParams) {
            return new LayoutParams((MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    protected View getViewForPosition(int position) {
        return mRecycler.getViewForPosition(position);
    }

    final int getOpticalLeft(View v) {
        return ((LayoutParams) v.getLayoutParams()).getOpticalLeft(v);
    }

    final int getOpticalRight(View v) {
        return ((LayoutParams) v.getLayoutParams()).getOpticalRight(v);
    }

    final int getOpticalTop(View v) {
        return ((LayoutParams) v.getLayoutParams()).getOpticalTop(v);
    }

    final int getOpticalBottom(View v) {
        return ((LayoutParams) v.getLayoutParams()).getOpticalBottom(v);
    }

    @Override
    public int getDecoratedLeft(View child) {
        return super.getDecoratedLeft(child) + ((LayoutParams) child.getLayoutParams()).mLeftInset;
    }

    @Override
    public int getDecoratedTop(View child) {
        return super.getDecoratedTop(child) + ((LayoutParams) child.getLayoutParams()).mTopInset;
    }

    @Override
    public int getDecoratedRight(View child) {
        return super.getDecoratedRight(child)
                - ((LayoutParams) child.getLayoutParams()).mRightInset;
    }

    @Override
    public int getDecoratedBottom(View child) {
        return super.getDecoratedBottom(child)
                - ((LayoutParams) child.getLayoutParams()).mBottomInset;
    }

    @Override
    public void getDecoratedBoundsWithMargins(View view, Rect outBounds) {
        super.getDecoratedBoundsWithMargins(view, outBounds);
        LayoutParams params = ((LayoutParams) view.getLayoutParams());
        outBounds.left += params.mLeftInset;
        outBounds.top += params.mTopInset;
        outBounds.right -= params.mRightInset;
        outBounds.bottom -= params.mBottomInset;
    }

    int getViewMin(View v) {
        return mOrientationHelper.getDecoratedStart(v);
    }

    int getViewMax(View v) {
        return mOrientationHelper.getDecoratedEnd(v);
    }

    int getViewPrimarySize(View view) {
        getDecoratedBoundsWithMargins(view, sTempRect);
        return mOrientation == HORIZONTAL ? sTempRect.width() : sTempRect.height();
    }

    private int getViewCenter(View view) {
        return (mOrientation == HORIZONTAL) ? getViewCenterX(view) : getViewCenterY(view);
    }

    private int getAdjustedViewCenter(View view) {
        if (view.hasFocus()) {
            View child = view.findFocus();
            if (child != null && child != view) {
                return getAdjustedPrimaryAlignedScrollDistance(getViewCenter(view), view, child);
            }
        }
        return getViewCenter(view);
    }

    private int getViewCenterSecondary(View view) {
        return (mOrientation == HORIZONTAL) ? getViewCenterY(view) : getViewCenterX(view);
    }

    private int getViewCenterX(View v) {
        LayoutParams p = (LayoutParams) v.getLayoutParams();
        return p.getOpticalLeft(v) + p.getAlignX();
    }

    private int getViewCenterY(View v) {
        LayoutParams p = (LayoutParams) v.getLayoutParams();
        return p.getOpticalTop(v) + p.getAlignY();
    }

    /**
     * Save Recycler and State for convenience.  Must be paired with leaveContext().
     */
    private void saveContext(Recycler recycler, State state) {
        if (mRecycler != null || mState != null) {
            Log.e(TAG, "Recycler information was not released, bug!");
        }
        mRecycler = recycler;
        mState = state;
        mPositionDeltaInPreLayout = 0;
        mExtraLayoutSpaceInPreLayout = 0;
    }

    /**
     * Discard saved Recycler and State.
     */
    private void leaveContext() {
        mRecycler = null;
        mState = null;
        mPositionDeltaInPreLayout = 0;
        mExtraLayoutSpaceInPreLayout = 0;
    }

    /**
     * Re-initialize data structures for a data change or handling invisible
     * selection. The method tries its best to preserve position information so
     * that staggered grid looks same before and after re-initialize.
     * @return true if can fastRelayout()
     */
    private boolean layoutInit() {
        final int newItemCount = mState.getItemCount();
        if (newItemCount == 0) {
            mFocusPosition = NO_POSITION;
            mSubFocusPosition = 0;
        } else if (mFocusPosition >= newItemCount) {
            mFocusPosition = newItemCount - 1;
            mSubFocusPosition = 0;
        } else if (mFocusPosition == NO_POSITION && newItemCount > 0) {
            // if focus position is never set before,  initialize it to 0
            mFocusPosition = 0;
            mSubFocusPosition = 0;
        }
        if (!mState.didStructureChange() && mGrid != null && mGrid.getFirstVisibleIndex() >= 0
                && (mFlag & PF_FORCE_FULL_LAYOUT) == 0 && mGrid.getNumRows() == mNumRows) {
            updateScrollController();
            updateSecondaryScrollLimits();
            mGrid.setSpacing(mSpacingPrimary);
            return true;
        } else {
            mFlag &= ~PF_FORCE_FULL_LAYOUT;

            if (mGrid == null || mNumRows != mGrid.getNumRows()
                    || ((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0) != mGrid.isReversedFlow()) {
                mGrid = Grid.createGrid(mNumRows);
                mGrid.setProvider(mGridProvider);
                mGrid.setReversedFlow((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0);
            }
            initScrollController();
            updateSecondaryScrollLimits();
            mGrid.setSpacing(mSpacingPrimary);
            detachAndScrapAttachedViews(mRecycler);
            mGrid.resetVisibleIndex();
            mWindowAlignment.mainAxis().invalidateScrollMin();
            mWindowAlignment.mainAxis().invalidateScrollMax();
            return false;
        }
    }

    private int getRowSizeSecondary(int rowIndex) {
        if (mFixedRowSizeSecondary != 0) {
            return mFixedRowSizeSecondary;
        }
        if (mRowSizeSecondary == null) {
            return 0;
        }
        return mRowSizeSecondary[rowIndex];
    }

    int getRowStartSecondary(int rowIndex) {
        int start = 0;
        // Iterate from left to right, which is a different index traversal
        // in RTL flow
        if ((mFlag & PF_REVERSE_FLOW_SECONDARY) != 0) {
            for (int i = mNumRows-1; i > rowIndex; i--) {
                start += getRowSizeSecondary(i) + mSpacingSecondary;
            }
        } else {
            for (int i = 0; i < rowIndex; i++) {
                start += getRowSizeSecondary(i) + mSpacingSecondary;
            }
        }
        return start;
    }

    private int getSizeSecondary() {
        int rightmostIndex = (mFlag & PF_REVERSE_FLOW_SECONDARY) != 0 ? 0 : mNumRows - 1;
        return getRowStartSecondary(rightmostIndex) + getRowSizeSecondary(rightmostIndex);
    }

    int getDecoratedMeasuredWidthWithMargin(View v) {
        final LayoutParams lp = (LayoutParams) v.getLayoutParams();
        return getDecoratedMeasuredWidth(v) + lp.leftMargin + lp.rightMargin;
    }

    int getDecoratedMeasuredHeightWithMargin(View v) {
        final LayoutParams lp = (LayoutParams) v.getLayoutParams();
        return getDecoratedMeasuredHeight(v) + lp.topMargin + lp.bottomMargin;
    }

    private void measureScrapChild(int position, int widthSpec, int heightSpec,
            int[] measuredDimension) {
        View view = mRecycler.getViewForPosition(position);
        if (view != null) {
            final LayoutParams p = (LayoutParams) view.getLayoutParams();
            calculateItemDecorationsForChild(view, sTempRect);
            int widthUsed = p.leftMargin + p.rightMargin + sTempRect.left + sTempRect.right;
            int heightUsed = p.topMargin + p.bottomMargin + sTempRect.top + sTempRect.bottom;

            int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec,
                    getPaddingLeft() + getPaddingRight() + widthUsed, p.width);
            int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec,
                    getPaddingTop() + getPaddingBottom() + heightUsed, p.height);
            view.measure(childWidthSpec, childHeightSpec);

            measuredDimension[0] = getDecoratedMeasuredWidthWithMargin(view);
            measuredDimension[1] = getDecoratedMeasuredHeightWithMargin(view);
            mRecycler.recycleView(view);
        }
    }

    private boolean processRowSizeSecondary(boolean measure) {
        if (mFixedRowSizeSecondary != 0 || mRowSizeSecondary == null) {
            return false;
        }

        if (TRACE) TraceCompat.beginSection("processRowSizeSecondary");
        CircularIntArray[] rows = mGrid == null ? null : mGrid.getItemPositionsInRows();
        boolean changed = false;
        int scrapeChildSize = -1;

        for (int rowIndex = 0; rowIndex < mNumRows; rowIndex++) {
            CircularIntArray row = rows == null ? null : rows[rowIndex];
            final int rowItemsPairCount = row == null ? 0 : row.size();
            int rowSize = -1;
            for (int rowItemPairIndex = 0; rowItemPairIndex < rowItemsPairCount;
                    rowItemPairIndex += 2) {
                final int rowIndexStart = row.get(rowItemPairIndex);
                final int rowIndexEnd = row.get(rowItemPairIndex + 1);
                for (int i = rowIndexStart; i <= rowIndexEnd; i++) {
                    final View view = findViewByPosition(i - mPositionDeltaInPreLayout);
                    if (view == null) {
                        continue;
                    }
                    if (measure) {
                        measureChild(view);
                    }
                    final int secondarySize = mOrientation == HORIZONTAL
                            ? getDecoratedMeasuredHeightWithMargin(view)
                            : getDecoratedMeasuredWidthWithMargin(view);
                    if (secondarySize > rowSize) {
                        rowSize = secondarySize;
                    }
                }
            }

            final int itemCount = mState.getItemCount();
            if (!mBaseGridView.hasFixedSize() && measure && rowSize < 0 && itemCount > 0) {
                if (scrapeChildSize < 0) {
                    // measure a child that is close to mFocusPosition but not currently visible
                    int position = mFocusPosition;
                    if (position < 0) {
                        position = 0;
                    } else if (position >= itemCount) {
                        position = itemCount - 1;
                    }
                    if (getChildCount() > 0) {
                        int firstPos = mBaseGridView.getChildViewHolder(
                                getChildAt(0)).getLayoutPosition();
                        int lastPos = mBaseGridView.getChildViewHolder(
                                getChildAt(getChildCount() - 1)).getLayoutPosition();
                        // if mFocusPosition is between first and last, choose either
                        // first - 1 or last + 1
                        if (position >= firstPos && position <= lastPos) {
                            position = (position - firstPos <= lastPos - position)
                                    ? (firstPos - 1) : (lastPos + 1);
                            // try the other value if the position is invalid. if both values are
                            // invalid, skip measureScrapChild below.
                            if (position < 0 && lastPos < itemCount - 1) {
                                position = lastPos + 1;
                            } else if (position >= itemCount && firstPos > 0) {
                                position = firstPos - 1;
                            }
                        }
                    }
                    if (position >= 0 && position < itemCount) {
                        measureScrapChild(position,
                                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                mMeasuredDimension);
                        scrapeChildSize = mOrientation == HORIZONTAL ? mMeasuredDimension[1] :
                                mMeasuredDimension[0];
                        if (DEBUG) {
                            Log.v(TAG, "measured scrap child: " + mMeasuredDimension[0] + " "
                                    + mMeasuredDimension[1]);
                        }
                    }
                }
                if (scrapeChildSize >= 0) {
                    rowSize = scrapeChildSize;
                }
            }
            if (rowSize < 0) {
                rowSize = 0;
            }
            if (mRowSizeSecondary[rowIndex] != rowSize) {
                if (DEBUG) {
                    Log.v(getTag(), "row size secondary changed: " + mRowSizeSecondary[rowIndex]
                            + ", " + rowSize);
                }
                mRowSizeSecondary[rowIndex] = rowSize;
                changed = true;
            }
        }

        if (TRACE) TraceCompat.endSection();
        return changed;
    }

    /**
     * Checks if we need to update row secondary sizes.
     */
    private void updateRowSecondarySizeRefresh() {
        mFlag = (mFlag & ~PF_ROW_SECONDARY_SIZE_REFRESH)
                | (processRowSizeSecondary(false) ? PF_ROW_SECONDARY_SIZE_REFRESH : 0);
        if ((mFlag & PF_ROW_SECONDARY_SIZE_REFRESH) != 0) {
            if (DEBUG) Log.v(getTag(), "mRowSecondarySizeRefresh now set");
            forceRequestLayout();
        }
    }

    private void forceRequestLayout() {
        if (DEBUG) Log.v(getTag(), "forceRequestLayout");
        // RecyclerView prevents us from requesting layout in many cases
        // (during layout, during scroll, etc.)
        // For secondary row size wrap_content support we currently need a
        // second layout pass to update the measured size after having measured
        // and added child views in layoutChildren.
        // Force the second layout by posting a delayed runnable.
        // TODO: investigate allowing a second layout pass,
        // or move child add/measure logic to the measure phase.
        ViewCompat.postOnAnimation(mBaseGridView, mRequestLayoutRunnable);
    }

    private final Runnable mRequestLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.v(getTag(), "request Layout from runnable");
            requestLayout();
        }
    };

    @Override
    public void onMeasure(Recycler recycler, State state, int widthSpec, int heightSpec) {
        saveContext(recycler, state);

        // MOD: disable overscroll fix for portrait mode
        mIsPortrait = mBaseGridView.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        int sizePrimary, sizeSecondary, modeSecondary, paddingSecondary;
        int measuredSizeSecondary;
        if (mOrientation == HORIZONTAL) {
            sizePrimary = MeasureSpec.getSize(widthSpec);
            sizeSecondary = MeasureSpec.getSize(heightSpec);
            modeSecondary = MeasureSpec.getMode(heightSpec);
            paddingSecondary = getPaddingTop() + getPaddingBottom();
        } else {
            sizeSecondary = MeasureSpec.getSize(widthSpec);
            sizePrimary = MeasureSpec.getSize(heightSpec);
            modeSecondary = MeasureSpec.getMode(widthSpec);
            paddingSecondary = getPaddingLeft() + getPaddingRight();
        }
        if (DEBUG) {
            Log.v(getTag(), "onMeasure widthSpec " + Integer.toHexString(widthSpec)
                    + " heightSpec " + Integer.toHexString(heightSpec)
                    + " modeSecondary " + Integer.toHexString(modeSecondary)
                    + " sizeSecondary " + sizeSecondary + " " + this);
        }

        mMaxSizeSecondary = sizeSecondary;

        if (mRowSizeSecondaryRequested == ViewGroup.LayoutParams.WRAP_CONTENT) {
            mNumRows = mNumRowsRequested == 0 ? 1 : mNumRowsRequested;
            mFixedRowSizeSecondary = 0;

            if (mRowSizeSecondary == null || mRowSizeSecondary.length != mNumRows) {
                mRowSizeSecondary = new int[mNumRows];
            }

            if (mState.isPreLayout()) {
                updatePositionDeltaInPreLayout();
            }
            // Measure all current children and update cached row height or column width
            processRowSizeSecondary(true);

            switch (modeSecondary) {
                case MeasureSpec.UNSPECIFIED:
                    measuredSizeSecondary = getSizeSecondary() + paddingSecondary;
                    break;
                case MeasureSpec.AT_MOST:
                    measuredSizeSecondary = Math.min(getSizeSecondary() + paddingSecondary,
                            mMaxSizeSecondary);
                    break;
                case MeasureSpec.EXACTLY:
                    measuredSizeSecondary = mMaxSizeSecondary;
                    break;
                default:
                    throw new IllegalStateException("wrong spec");
            }

        } else {
            switch (modeSecondary) {
                case MeasureSpec.UNSPECIFIED:
                    mFixedRowSizeSecondary = mRowSizeSecondaryRequested == 0
                            ? sizeSecondary - paddingSecondary : mRowSizeSecondaryRequested;
                    mNumRows = mNumRowsRequested == 0 ? 1 : mNumRowsRequested;
                    measuredSizeSecondary = mFixedRowSizeSecondary * mNumRows + mSpacingSecondary
                            * (mNumRows - 1) + paddingSecondary;
                    break;
                case MeasureSpec.AT_MOST:
                case MeasureSpec.EXACTLY:
                    if (mNumRowsRequested == 0 && mRowSizeSecondaryRequested == 0) {
                        mNumRows = 1;
                        mFixedRowSizeSecondary = sizeSecondary - paddingSecondary;
                    } else if (mNumRowsRequested == 0) {
                        mFixedRowSizeSecondary = mRowSizeSecondaryRequested;
                        mNumRows = (sizeSecondary + mSpacingSecondary)
                                / (mRowSizeSecondaryRequested + mSpacingSecondary);
                    } else if (mRowSizeSecondaryRequested == 0) {
                        mNumRows = mNumRowsRequested;
                        mFixedRowSizeSecondary = (sizeSecondary - paddingSecondary
                                - mSpacingSecondary * (mNumRows - 1)) / mNumRows;
                    } else {
                        mNumRows = mNumRowsRequested;
                        mFixedRowSizeSecondary = mRowSizeSecondaryRequested;
                    }
                    measuredSizeSecondary = sizeSecondary;
                    if (modeSecondary == MeasureSpec.AT_MOST) {
                        int childrenSize = mFixedRowSizeSecondary * mNumRows + mSpacingSecondary
                                * (mNumRows - 1) + paddingSecondary;
                        if (childrenSize < measuredSizeSecondary) {
                            measuredSizeSecondary = childrenSize;
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException("wrong spec");
            }
        }
        if (mOrientation == HORIZONTAL) {
            setMeasuredDimension(sizePrimary, measuredSizeSecondary);
        } else {
            setMeasuredDimension(measuredSizeSecondary, sizePrimary);
        }
        if (DEBUG) {
            Log.v(getTag(), "onMeasure sizePrimary " + sizePrimary
                    + " measuredSizeSecondary " + measuredSizeSecondary
                    + " mFixedRowSizeSecondary " + mFixedRowSizeSecondary
                    + " mNumRows " + mNumRows);
        }
        leaveContext();
    }

    void measureChild(View child) {
        if (TRACE) TraceCompat.beginSection("measureChild");
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        calculateItemDecorationsForChild(child, sTempRect);
        int widthUsed = lp.leftMargin + lp.rightMargin + sTempRect.left + sTempRect.right;
        int heightUsed = lp.topMargin + lp.bottomMargin + sTempRect.top + sTempRect.bottom;

        final int secondarySpec =
                (mRowSizeSecondaryRequested == ViewGroup.LayoutParams.WRAP_CONTENT)
                        ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                        : MeasureSpec.makeMeasureSpec(mFixedRowSizeSecondary, MeasureSpec.EXACTLY);
        int widthSpec, heightSpec;

        if (mOrientation == HORIZONTAL) {
            widthSpec = ViewGroup.getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), widthUsed, lp.width);
            heightSpec = ViewGroup.getChildMeasureSpec(secondarySpec, heightUsed, lp.height);
        } else {
            heightSpec = ViewGroup.getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed, lp.height);
            widthSpec = ViewGroup.getChildMeasureSpec(secondarySpec, widthUsed, lp.width);
        }
        child.measure(widthSpec, heightSpec);
        if (DEBUG) {
            Log.v(getTag(), "measureChild secondarySpec " + Integer.toHexString(secondarySpec)
                    + " widthSpec " + Integer.toHexString(widthSpec)
                    + " heightSpec " + Integer.toHexString(heightSpec)
                    + " measuredWidth " + child.getMeasuredWidth()
                    + " measuredHeight " + child.getMeasuredHeight());
        }
        if (DEBUG) Log.v(getTag(), "child lp width " + lp.width + " height " + lp.height);
        if (TRACE) TraceCompat.endSection();
    }

    /**
     * Get facet from the ViewHolder or the viewType.
     */
    <E> E getFacet(RecyclerView.ViewHolder vh, Class<? extends E> facetClass) {
        E facet = null;
        if (vh instanceof FacetProvider) {
            facet = (E) ((FacetProvider) vh).getFacet(facetClass);
        }
        if (facet == null && mFacetProviderAdapter != null) {
            FacetProvider p = mFacetProviderAdapter.getFacetProvider(vh.getItemViewType());
            if (p != null) {
                facet = (E) p.getFacet(facetClass);
            }
        }
        return facet;
    }

    private Grid.Provider mGridProvider = new Grid.Provider() {

        @Override
        public int getMinIndex() {
            return mPositionDeltaInPreLayout;
        }

        @Override
        public int getCount() {
            // MOD: Fix npe in touch mode
            if (mState == null) {
                return mPositionDeltaInPreLayout;
            }

            return mState.getItemCount() + mPositionDeltaInPreLayout;
        }

        @Override
        public int createItem(int index, boolean append, Object[] item, boolean disappearingItem) {
            if (TRACE) TraceCompat.beginSection("createItem");
            if (TRACE) TraceCompat.beginSection("getview");
            View v = getViewForPosition(index - mPositionDeltaInPreLayout);
            if (TRACE) TraceCompat.endSection();
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            RecyclerView.ViewHolder vh = mBaseGridView.getChildViewHolder(v);
            lp.setItemAlignmentFacet((ItemAlignmentFacet)getFacet(vh, ItemAlignmentFacet.class));
            // See recyclerView docs:  we don't need re-add scraped view if it was removed.
            if (!lp.isItemRemoved()) {
                if (TRACE) TraceCompat.beginSection("addView");
                if (disappearingItem) {
                    if (append) {
                        addDisappearingView(v);
                    } else {
                        addDisappearingView(v, 0);
                    }
                } else {
                    if (append) {
                        addView(v);
                    } else {
                        addView(v, 0);
                    }
                }
                if (TRACE) TraceCompat.endSection();
                if (mChildVisibility != -1) {
                    v.setVisibility(mChildVisibility);
                }

                if (mPendingMoveSmoothScroller != null) {
                    mPendingMoveSmoothScroller.consumePendingMovesBeforeLayout();
                }
                int subindex = getSubPositionByView(v, v.findFocus());
                if ((mFlag & PF_STAGE_MASK) != PF_STAGE_LAYOUT) {
                    // when we are appending item during scroll pass and the item's position
                    // matches the mFocusPosition,  we should signal a childSelected event.
                    // However if we are still running PendingMoveSmoothScroller,  we defer and
                    // signal the event in PendingMoveSmoothScroller.onStop().  This can
                    // avoid lots of childSelected events during a long smooth scrolling and
                    // increase performance.
                    if (index == mFocusPosition && subindex == mSubFocusPosition
                            && mPendingMoveSmoothScroller == null) {
                        dispatchChildSelected();
                    }
                } else if ((mFlag & PF_FAST_RELAYOUT) == 0) {
                    // fastRelayout will dispatch event at end of onLayoutChildren().
                    // For full layout, two situations here:
                    // 1. mInLayoutSearchFocus is false, dispatchChildSelected() at mFocusPosition.
                    // 2. mInLayoutSearchFocus is true:  dispatchChildSelected() on first child
                    //    equal to or after mFocusPosition that can take focus.
                    if ((mFlag & PF_IN_LAYOUT_SEARCH_FOCUS) == 0 && index == mFocusPosition
                            && subindex == mSubFocusPosition) {
                        dispatchChildSelected();
                    } else if ((mFlag & PF_IN_LAYOUT_SEARCH_FOCUS) != 0 && index >= mFocusPosition
                            && v.hasFocusable()) {
                        mFocusPosition = index;
                        mSubFocusPosition = subindex;
                        mFlag &= ~PF_IN_LAYOUT_SEARCH_FOCUS;
                        dispatchChildSelected();
                    }
                }
                measureChild(v);
            }
            item[0] = v;
            return mOrientation == HORIZONTAL ? getDecoratedMeasuredWidthWithMargin(v)
                    : getDecoratedMeasuredHeightWithMargin(v);
        }

        @Override
        public void addItem(Object item, int index, int length, int rowIndex, int edge) {
            View v = (View) item;
            int start, end;
            if (edge == Integer.MIN_VALUE || edge == Integer.MAX_VALUE) {
                edge = !mGrid.isReversedFlow() ? mWindowAlignment.mainAxis().getPaddingMin()
                        : mWindowAlignment.mainAxis().getSize()
                                - mWindowAlignment.mainAxis().getPaddingMax();
            }
            boolean edgeIsMin = !mGrid.isReversedFlow();
            if (edgeIsMin) {
                start = edge;
                end = edge + length;
            } else {
                start = edge - length;
                end = edge;
            }
            int startSecondary = getRowStartSecondary(rowIndex)
                    + mWindowAlignment.secondAxis().getPaddingMin() - mScrollOffsetSecondary;
            mChildrenStates.loadView(v, index);
            layoutChild(rowIndex, v, start, end, startSecondary);
            if (DEBUG) {
                Log.d(getTag(), "addView " + index + " " + v);
            }
            if (TRACE) TraceCompat.endSection();

            if (!mState.isPreLayout()) {
                updateScrollLimits();
            }
            if ((mFlag & PF_STAGE_MASK) != PF_STAGE_LAYOUT && mPendingMoveSmoothScroller != null) {
                mPendingMoveSmoothScroller.consumePendingMovesAfterLayout();
            }
            if (mChildLaidOutListener != null) {
                RecyclerView.ViewHolder vh = mBaseGridView.getChildViewHolder(v);
                mChildLaidOutListener.onChildLaidOut(mBaseGridView, v, index,
                        vh == null ? NO_ID : vh.getItemId());
            }
        }

        @Override
        public void removeItem(int index) {
            if (TRACE) TraceCompat.beginSection("removeItem");
            View v = findViewByPosition(index - mPositionDeltaInPreLayout);
            if ((mFlag & PF_STAGE_MASK) == PF_STAGE_LAYOUT) {
                detachAndScrapView(v, mRecycler);
            } else {
                removeAndRecycleView(v, mRecycler);
            }
            if (TRACE) TraceCompat.endSection();
        }

        @Override
        public int getEdge(int index) {
            View v = findViewByPosition(index - mPositionDeltaInPreLayout);
            return (mFlag & PF_REVERSE_FLOW_PRIMARY) != 0 ? getViewMax(v) : getViewMin(v);
        }

        @Override
        public int getSize(int index) {
            return getViewPrimarySize(findViewByPosition(index - mPositionDeltaInPreLayout));
        }
    };

    void layoutChild(int rowIndex, View v, int start, int end, int startSecondary) {
        if (TRACE) TraceCompat.beginSection("layoutChild");
        int sizeSecondary = mOrientation == HORIZONTAL ? getDecoratedMeasuredHeightWithMargin(v)
                : getDecoratedMeasuredWidthWithMargin(v);
        if (mFixedRowSizeSecondary > 0) {
            sizeSecondary = Math.min(sizeSecondary, mFixedRowSizeSecondary);
        }
        final int verticalGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int horizontalGravity = (mFlag & PF_REVERSE_FLOW_MASK) != 0
                ? Gravity.getAbsoluteGravity(mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK,
                View.LAYOUT_DIRECTION_RTL)
                : mGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        if ((mOrientation == HORIZONTAL && verticalGravity == Gravity.TOP)
                || (mOrientation == VERTICAL && horizontalGravity == Gravity.LEFT)) {
            // do nothing
        } else if ((mOrientation == HORIZONTAL && verticalGravity == Gravity.BOTTOM)
                || (mOrientation == VERTICAL && horizontalGravity == Gravity.RIGHT)) {
            startSecondary += getRowSizeSecondary(rowIndex) - sizeSecondary;
        } else if ((mOrientation == HORIZONTAL && verticalGravity == Gravity.CENTER_VERTICAL)
                || (mOrientation == VERTICAL && horizontalGravity == Gravity.CENTER_HORIZONTAL)) {
            startSecondary += (getRowSizeSecondary(rowIndex) - sizeSecondary) / 2;
        }
        int left, top, right, bottom;
        if (mOrientation == HORIZONTAL) {
            left = start;
            top = startSecondary;
            right = end;
            bottom = startSecondary + sizeSecondary;
        } else {
            top = start;
            left = startSecondary;
            bottom = end;
            right = startSecondary + sizeSecondary;
        }
        LayoutParams params = (LayoutParams) v.getLayoutParams();
        layoutDecoratedWithMargins(v, left, top, right, bottom);
        // Now super.getDecoratedBoundsWithMargins() includes the extra space for optical bounds,
        // subtracting it from value passed in layoutDecoratedWithMargins(), we can get the optical
        // bounds insets.
        super.getDecoratedBoundsWithMargins(v, sTempRect);
        params.setOpticalInsets(left - sTempRect.left, top - sTempRect.top,
                sTempRect.right - right, sTempRect.bottom - bottom);
        updateChildAlignments(v);
        if (TRACE) TraceCompat.endSection();
    }

    private void updateChildAlignments(View v) {
        final LayoutParams p = (LayoutParams) v.getLayoutParams();
        if (p.getItemAlignmentFacet() == null) {
            // Fallback to global settings on grid view
            p.setAlignX(mItemAlignment.horizontal.getAlignmentPosition(v));
            p.setAlignY(mItemAlignment.vertical.getAlignmentPosition(v));
        } else {
            // Use ItemAlignmentFacet defined on specific ViewHolder
            p.calculateItemAlignments(mOrientation, v);
            if (mOrientation == HORIZONTAL) {
                p.setAlignY(mItemAlignment.vertical.getAlignmentPosition(v));
            } else {
                p.setAlignX(mItemAlignment.horizontal.getAlignmentPosition(v));
            }
        }
    }

    private void updateChildAlignments() {
        for (int i = 0, c = getChildCount(); i < c; i++) {
            updateChildAlignments(getChildAt(i));
        }
    }

    void setExtraLayoutSpace(int extraLayoutSpace) {
        if (mExtraLayoutSpace == extraLayoutSpace) {
            return;
        } else if (mExtraLayoutSpace < 0) {
            throw new IllegalArgumentException("ExtraLayoutSpace must >= 0");
        }
        mExtraLayoutSpace = extraLayoutSpace;
        requestLayout();
    }

    int getExtraLayoutSpace() {
        return mExtraLayoutSpace;
    }

    private void removeInvisibleViewsAtEnd() {
        if ((mFlag & (PF_PRUNE_CHILD | PF_SLIDING)) == PF_PRUNE_CHILD) {
            mGrid.removeInvisibleItemsAtEnd(mFocusPosition, (mFlag & PF_REVERSE_FLOW_PRIMARY) != 0
                    ? -mExtraLayoutSpace : mSizePrimary + mExtraLayoutSpace);
        }
    }

    private void removeInvisibleViewsAtFront() {
        if ((mFlag & (PF_PRUNE_CHILD | PF_SLIDING)) == PF_PRUNE_CHILD) {
            mGrid.removeInvisibleItemsAtFront(mFocusPosition, (mFlag & PF_REVERSE_FLOW_PRIMARY) != 0
                    ? mSizePrimary + mExtraLayoutSpace : -mExtraLayoutSpace);
        }
    }

    private boolean appendOneColumnVisibleItems() {
        return mGrid.appendOneColumnVisibleItems();
    }

    void slideIn() {
        if ((mFlag & PF_SLIDING) != 0) {
            mFlag &= ~PF_SLIDING;
            if (mFocusPosition >= 0) {
                scrollToSelection(mFocusPosition, mSubFocusPosition, true, mPrimaryScrollExtra);
            } else {
                mFlag &= ~PF_LAYOUT_EATEN_IN_SLIDING;
                requestLayout();
            }
            if ((mFlag & PF_LAYOUT_EATEN_IN_SLIDING) != 0) {
                mFlag &= ~PF_LAYOUT_EATEN_IN_SLIDING;
                if (mBaseGridView.getScrollState() != SCROLL_STATE_IDLE || isSmoothScrolling()) {
                    mBaseGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                            if (newState == SCROLL_STATE_IDLE) {
                                mBaseGridView.removeOnScrollListener(this);
                                requestLayout();
                            }
                        }
                    });
                } else {
                    requestLayout();
                }
            }
        }
    }

    int getSlideOutDistance() {
        int distance;
        if (mOrientation == VERTICAL) {
            distance = -getHeight();
            if (getChildCount() > 0) {
                int top = getChildAt(0).getTop();
                if (top < 0) {
                    // scroll more if first child is above top edge
                    distance = distance + top;
                }
            }
        } else {
            if ((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0) {
                distance = getWidth();
                if (getChildCount() > 0) {
                    int start = getChildAt(0).getRight();
                    if (start > distance) {
                        // scroll more if first child is outside right edge
                        distance = start;
                    }
                }
            } else {
                distance = -getWidth();
                if (getChildCount() > 0) {
                    int start = getChildAt(0).getLeft();
                    if (start < 0) {
                        // scroll more if first child is out side left edge
                        distance = distance + start;
                    }
                }
            }
        }
        return distance;
    }

    boolean isSlidingChildViews() {
        return (mFlag & PF_SLIDING) != 0;
    }

    /**
     * Temporarily slide out child and block layout and scroll requests.
     */
    void slideOut() {
        if ((mFlag & PF_SLIDING) != 0) {
            return;
        }
        mFlag |= PF_SLIDING;
        if (getChildCount() == 0) {
            return;
        }
        if (mOrientation == VERTICAL) {
            mBaseGridView.smoothScrollBy(0, getSlideOutDistance(),
                    new AccelerateDecelerateInterpolator());
        } else {
            mBaseGridView.smoothScrollBy(getSlideOutDistance(), 0,
                    new AccelerateDecelerateInterpolator());
        }
    }

    private boolean prependOneColumnVisibleItems() {
        return mGrid.prependOneColumnVisibleItems();
    }

    private void appendVisibleItems() {
        mGrid.appendVisibleItems((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0
                ? -mExtraLayoutSpace - mExtraLayoutSpaceInPreLayout
                : mSizePrimary + mExtraLayoutSpace + mExtraLayoutSpaceInPreLayout);
    }

    private void prependVisibleItems() {
        mGrid.prependVisibleItems((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0
                ? mSizePrimary + mExtraLayoutSpace + mExtraLayoutSpaceInPreLayout
                : -mExtraLayoutSpace - mExtraLayoutSpaceInPreLayout);
    }

    /**
     * Fast layout when there is no structure change, adapter change, etc.
     * It will layout all views was layout requested or updated, until hit a view
     * with different size,  then it break and detachAndScrap all views after that.
     */
    private void fastRelayout() {
        boolean invalidateAfter = false;
        final int childCount = getChildCount();
        int position = mGrid.getFirstVisibleIndex();
        int index = 0;
        mFlag &= ~PF_FAST_RELAYOUT_UPDATED_SELECTED_POSITION;
        for (; index < childCount; index++, position++) {
            View view = getChildAt(index);
            // We don't hit fastRelayout() if State.didStructure() is true, but prelayout may add
            // extra views and invalidate existing Grid position. Also the prelayout calling
            // getViewForPosotion() may retrieve item from cache with FLAG_INVALID. The adapter
            // postion will be -1 for this case. Either case, we should invalidate after this item
            // and call getViewForPosition() again to rebind.
            if (position != getAdapterPositionByView(view)) {
                invalidateAfter = true;
                break;
            }
            Grid.Location location = mGrid.getLocation(position);
            if (location == null) {
                invalidateAfter = true;
                break;
            }

            int startSecondary = getRowStartSecondary(location.row)
                    + mWindowAlignment.secondAxis().getPaddingMin() - mScrollOffsetSecondary;
            int primarySize, end;
            int start = getViewMin(view);
            int oldPrimarySize = getViewPrimarySize(view);

            LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (lp.viewNeedsUpdate()) {
                mFlag |= PF_FAST_RELAYOUT_UPDATED_SELECTED_POSITION;
                detachAndScrapView(view, mRecycler);
                view = getViewForPosition(position);
                addView(view, index);
            }

            measureChild(view);
            if (mOrientation == HORIZONTAL) {
                primarySize = getDecoratedMeasuredWidthWithMargin(view);
                end = start + primarySize;
            } else {
                primarySize = getDecoratedMeasuredHeightWithMargin(view);
                end = start + primarySize;
            }
            layoutChild(location.row, view, start, end, startSecondary);
            if (oldPrimarySize != primarySize) {
                // size changed invalidate remaining Locations
                if (DEBUG) Log.d(getTag(), "fastRelayout: view size changed at " + position);
                invalidateAfter = true;
                break;
            }
        }
        if (invalidateAfter) {
            final int savedLastPos = mGrid.getLastVisibleIndex();
            for (int i = childCount - 1; i >= index; i--) {
                View v = getChildAt(i);
                detachAndScrapView(v, mRecycler);
            }
            mGrid.invalidateItemsAfter(position);
            if ((mFlag & PF_PRUNE_CHILD) != 0) {
                // in regular prune child mode, we just append items up to edge limit
                appendVisibleItems();
                if (mFocusPosition >= 0 && mFocusPosition <= savedLastPos) {
                    // make sure add focus view back:  the view might be outside edge limit
                    // when there is delta in onLayoutChildren().
                    while (mGrid.getLastVisibleIndex() < mFocusPosition) {
                        mGrid.appendOneColumnVisibleItems();
                    }
                }
            } else {
                // prune disabled(e.g. in RowsFragment transition): append all removed items
                while (mGrid.appendOneColumnVisibleItems()
                        && mGrid.getLastVisibleIndex() < savedLastPos);
            }
        }
        updateScrollLimits();
        updateSecondaryScrollLimits();
    }

    @Override
    public void removeAndRecycleAllViews(RecyclerView.Recycler recycler) {
        if (TRACE) TraceCompat.beginSection("removeAndRecycleAllViews");
        if (DEBUG) Log.v(TAG, "removeAndRecycleAllViews " + getChildCount());
        for (int i = getChildCount() - 1; i >= 0; i--) {
            removeAndRecycleViewAt(i, recycler);
        }
        if (TRACE) TraceCompat.endSection();
    }

    // called by onLayoutChildren, either focus to FocusPosition or declare focusViewAvailable
    // and scroll to the view if framework focus on it.
    private void focusToViewInLayout(boolean hadFocus, boolean alignToView, int extraDelta,
            int extraDeltaSecondary) {
        View focusView = findViewByPosition(mFocusPosition);
        if (focusView != null && alignToView) {
            scrollToView(focusView, false, extraDelta, extraDeltaSecondary);
        }
        if (focusView != null && hadFocus && !focusView.hasFocus()) {
            focusView.requestFocus();
        } else if (!hadFocus && !mBaseGridView.hasFocus()) {
            if (focusView != null && focusView.hasFocusable()) {
                mBaseGridView.focusableViewAvailable(focusView);
            } else {
                for (int i = 0, count = getChildCount(); i < count; i++) {
                    focusView = getChildAt(i);
                    if (focusView != null && focusView.hasFocusable()) {
                        mBaseGridView.focusableViewAvailable(focusView);
                        break;
                    }
                }
            }
            // focusViewAvailable() might focus to the view, scroll to it if that is the case.
            if (alignToView && focusView != null && focusView.hasFocus()) {
                scrollToView(focusView, false, extraDelta, extraDeltaSecondary);
            }
        }
    }

    @VisibleForTesting
    public static class OnLayoutCompleteListener {
        public void onLayoutCompleted(RecyclerView.State state) {
        }
    }

    @VisibleForTesting
    OnLayoutCompleteListener mLayoutCompleteListener;

    @Override
    public void onLayoutCompleted(State state) {
        if (mLayoutCompleteListener != null) {
            mLayoutCompleteListener.onLayoutCompleted(state);
        }
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return true;
    }

    void updatePositionToRowMapInPostLayout() {
        mPositionToRowInPostLayout.clear();
        final int childCount = getChildCount();
        for (int i = 0;  i < childCount; i++) {
            // Grid still maps to old positions at this point, use old position to get row infor
            int position = mBaseGridView.getChildViewHolder(getChildAt(i)).getOldPosition();
            if (position >= 0) {
                Grid.Location loc = mGrid.getLocation(position);
                if (loc != null) {
                    mPositionToRowInPostLayout.put(position, loc.row);
                }
            }
        }
    }

    void fillScrapViewsInPostLayout() {
        List<RecyclerView.ViewHolder> scrapList = mRecycler.getScrapList();
        final int scrapSize = scrapList.size();
        if (scrapSize == 0) {
            return;
        }
        // initialize the int array or re-allocate the array.
        if (mDisappearingPositions == null  || scrapSize > mDisappearingPositions.length) {
            int length = mDisappearingPositions == null ? 16 : mDisappearingPositions.length;
            while (length < scrapSize) {
                length = length << 1;
            }
            mDisappearingPositions = new int[length];
        }
        int totalItems = 0;
        for (int i = 0; i < scrapSize; i++) {
            int pos = scrapList.get(i).getAdapterPosition();
            if (pos >= 0) {
                mDisappearingPositions[totalItems++] = pos;
            }
        }
        // totalItems now has the length of disappearing items
        if (totalItems > 0) {
            Arrays.sort(mDisappearingPositions, 0, totalItems);
            mGrid.fillDisappearingItems(mDisappearingPositions, totalItems,
                    mPositionToRowInPostLayout);
        }
        mPositionToRowInPostLayout.clear();
    }

    // in prelayout, first child's getViewPosition can be smaller than old adapter position
    // if there were items removed before first visible index. For example:
    // visible items are 3, 4, 5, 6, deleting 1, 2, 3 from adapter; the view position in
    // prelayout are not 3(deleted), 4, 5, 6. Instead it's 1(deleted), 2, 3, 4.
    // So there is a delta (2 in this case) between last cached position and prelayout position.
    void updatePositionDeltaInPreLayout() {
        if (getChildCount() > 0) {
            View view = getChildAt(0);
            LayoutParams lp = (LayoutParams) view.getLayoutParams();
            mPositionDeltaInPreLayout = mGrid.getFirstVisibleIndex()
                    - lp.getViewLayoutPosition();
        } else {
            mPositionDeltaInPreLayout = 0;
        }
    }

    // Lays out items based on the current scroll position
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (DEBUG) {
            Log.v(getTag(), "layoutChildren start numRows " + mNumRows
                    + " inPreLayout " + state.isPreLayout()
                    + " didStructureChange " + state.didStructureChange()
                    + " mForceFullLayout " + ((mFlag & PF_FORCE_FULL_LAYOUT) != 0));
            Log.v(getTag(), "width " + getWidth() + " height " + getHeight());
        }

        if (mNumRows == 0) {
            // haven't done measure yet
            return;
        }
        final int itemCount = state.getItemCount();
        if (itemCount < 0) {
            return;
        }

        if ((mFlag & PF_SLIDING) != 0) {
            // if there is already children, delay the layout process until slideIn(), if it's
            // first time layout children: scroll them offscreen at end of onLayoutChildren()
            if (getChildCount() > 0) {
                mFlag |= PF_LAYOUT_EATEN_IN_SLIDING;
                return;
            }
        }
        if ((mFlag & PF_LAYOUT_ENABLED) == 0) {
            discardLayoutInfo();
            removeAndRecycleAllViews(recycler);
            return;
        }
        mFlag = (mFlag & ~PF_STAGE_MASK) | PF_STAGE_LAYOUT;

        saveContext(recycler, state);
        if (state.isPreLayout()) {
            updatePositionDeltaInPreLayout();
            int childCount = getChildCount();
            if (mGrid != null && childCount > 0) {
                int minChangedEdge = Integer.MAX_VALUE;
                int maxChangeEdge = Integer.MIN_VALUE;
                int minOldAdapterPosition = mBaseGridView.getChildViewHolder(
                        getChildAt(0)).getOldPosition();
                int maxOldAdapterPosition = mBaseGridView.getChildViewHolder(
                        getChildAt(childCount - 1)).getOldPosition();
                for (int i = 0; i < childCount; i++) {
                    View view = getChildAt(i);
                    LayoutParams lp = (LayoutParams) view.getLayoutParams();
                    int newAdapterPosition = mBaseGridView.getChildAdapterPosition(view);
                    // if either of following happening
                    // 1. item itself has changed or layout parameter changed
                    // 2. item is losing focus
                    // 3. item is gaining focus
                    // 4. item is moved out of old adapter position range.
                    if (lp.isItemChanged() || lp.isItemRemoved() || view.isLayoutRequested()
                            || (!view.hasFocus() && mFocusPosition == lp.getViewAdapterPosition())
                            || (view.hasFocus() && mFocusPosition != lp.getViewAdapterPosition())
                            || newAdapterPosition < minOldAdapterPosition
                            || newAdapterPosition > maxOldAdapterPosition) {
                        minChangedEdge = Math.min(minChangedEdge, getViewMin(view));
                        maxChangeEdge = Math.max(maxChangeEdge, getViewMax(view));
                    }
                }
                if (maxChangeEdge > minChangedEdge) {
                    mExtraLayoutSpaceInPreLayout = maxChangeEdge - minChangedEdge;
                }
                // MOD: fix RecycleView crash on Amazon
                try {
                    // append items for mExtraLayoutSpaceInPreLayout
                    appendVisibleItems();
                    prependVisibleItems();
                } catch (IndexOutOfBoundsException | NullPointerException | IllegalArgumentException e) {
                    // IndexOutOfBoundsException: Invalid item position -1(-1). Item count:12 androidx.leanback.widget.VerticalGridView
                    // NullPointerException: Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()'
                    // IllegalArgumentException: VideoCardPresenter$1 is not a direct child of HorizontalGridView
                    e.printStackTrace();
                }
            }
            mFlag &= ~PF_STAGE_MASK;
            leaveContext();
            if (DEBUG) Log.v(getTag(), "layoutChildren end");
            return;
        }

        // save all view's row information before detach all views
        if (state.willRunPredictiveAnimations()) {
            updatePositionToRowMapInPostLayout();
        }
        // check if we need align to mFocusPosition, this is usually true unless in smoothScrolling
        final boolean scrollToFocus = !isSmoothScrolling()
                && mFocusScrollStrategy == BaseGridView.FOCUS_SCROLL_ALIGNED;
        if (mFocusPosition != NO_POSITION && mFocusPositionOffset != Integer.MIN_VALUE) {
            mFocusPosition = mFocusPosition + mFocusPositionOffset;
            mSubFocusPosition = 0;
        }
        mFocusPositionOffset = 0;

        View savedFocusView = findViewByPosition(mFocusPosition);
        int savedFocusPos = mFocusPosition;
        int savedSubFocusPos = mSubFocusPosition;
        boolean hadFocus = mBaseGridView.hasFocus();
        final int firstVisibleIndex = mGrid != null ? mGrid.getFirstVisibleIndex() : NO_POSITION;
        final int lastVisibleIndex = mGrid != null ? mGrid.getLastVisibleIndex() : NO_POSITION;
        final int deltaPrimary;
        final int deltaSecondary;
        if (mOrientation == HORIZONTAL) {
            deltaPrimary = state.getRemainingScrollHorizontal();
            deltaSecondary = state.getRemainingScrollVertical();
        } else {
            deltaSecondary = state.getRemainingScrollHorizontal();
            deltaPrimary = state.getRemainingScrollVertical();
        }
        if (layoutInit()) {
            mFlag |= PF_FAST_RELAYOUT;
            // If grid view is empty, we will start from mFocusPosition
            mGrid.setStart(mFocusPosition);
            // MOD: fix RecycleView crash on Ugoos
            try {
                fastRelayout();
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
                // IllegalArgumentException: Called attach on a child which is not detached: ViewHolder{434061b0 position=10 id=-1, oldPos=-1, pLpos:-1 no parent}
                // IllegalStateException: Layout state should be one of 100 but it is 10
                // NullPointerException: Attempt to read from field 'int androidx.recyclerview.widget.ViewInfoStore$InfoRecord.flags' on a null object reference
                e.printStackTrace();
            }
        } else {
            mFlag &= ~PF_FAST_RELAYOUT;
            // layoutInit() has detached all views, so start from scratch
            mFlag = (mFlag & ~PF_IN_LAYOUT_SEARCH_FOCUS)
                    | (hadFocus ? PF_IN_LAYOUT_SEARCH_FOCUS : 0);
            int startFromPosition, endPos;
            if (scrollToFocus && (firstVisibleIndex < 0 || mFocusPosition > lastVisibleIndex
                    || mFocusPosition < firstVisibleIndex)) {
                startFromPosition = endPos = mFocusPosition;
            } else {
                startFromPosition = firstVisibleIndex;
                endPos = lastVisibleIndex;
            }
            mGrid.setStart(startFromPosition);
            if (endPos != NO_POSITION) {
                // MOD: fix RecycleView crash on Asus tablet
                try {
                    while (appendOneColumnVisibleItems() && findViewByPosition(endPos) == null) {
                        // continuously append items until endPos
                    }
                } catch (IndexOutOfBoundsException e) {
                    // IndexOutOfBoundsException: Invalid item position 20(20). Item count:6 androidx.leanback.widget.VerticalGridView
                    e.printStackTrace();
                }
            }
        }
        // multiple rounds: scrollToView of first round may drag first/last child into
        // "visible window" and we update scrollMin/scrollMax then run second scrollToView
        // we must do this for fastRelayout() for the append item case
        int oldFirstVisible;
        int oldLastVisible;
        do {
            updateScrollLimits();
            oldFirstVisible = mGrid.getFirstVisibleIndex();
            oldLastVisible = mGrid.getLastVisibleIndex();
            focusToViewInLayout(hadFocus, scrollToFocus, -deltaPrimary, -deltaSecondary);
            // MOD: fix RecycleView crash on Droidlogic
            try {
                appendVisibleItems();
                prependVisibleItems();
            } catch (IndexOutOfBoundsException | NullPointerException | IllegalArgumentException e) {
                // IndexOutOfBoundsException: Invalid item position -1(-1). Item count:12 androidx.leanback.widget.VerticalGridView
                // NullPointerException: Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()'
                // IllegalArgumentException: VideoCardPresenter$1 is not a direct child of HorizontalGridView
                e.printStackTrace();
            }
            // b/67370222: do not removeInvisibleViewsAtFront/End() in the loop, otherwise
            // loop may bounce between scroll forward and scroll backward forever. Example:
            // Assuming there are 19 items, child#18 and child#19 are both in RV, we are
            // trying to focus to child#18 and there are 200px remaining scroll distance.
            //   1  focusToViewInLayout() tries scroll forward 50 px to align focused child#18 on
            //      right edge, but there to compensate remaining scroll 200px, also scroll
            //      backward 200px, 150px pushes last child#19 out side of right edge.
            //   2  removeInvisibleViewsAtEnd() remove last child#19, updateScrollLimits()
            //      invalidates scroll max
            //   3  In next iteration, when scroll max/min is unknown, focusToViewInLayout() will
            //      align focused child#18 at center of screen.
            //   4  Because #18 is aligned at center, appendVisibleItems() will fill child#19 to
            //      the right.
            //   5  (back to 1 and loop forever)
        } while (mGrid.getFirstVisibleIndex() != oldFirstVisible
                || mGrid.getLastVisibleIndex() != oldLastVisible);
        try {
            removeInvisibleViewsAtFront();
            removeInvisibleViewsAtEnd();
        } catch (NullPointerException e) {
            // NullPointerException: Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()' on a null object reference
            e.printStackTrace();
        }

        if (state.willRunPredictiveAnimations()) {
            fillScrapViewsInPostLayout();
        }

        if (DEBUG) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            mGrid.debugPrint(pw);
            Log.d(getTag(), sw.toString());
        }

        if ((mFlag & PF_ROW_SECONDARY_SIZE_REFRESH) != 0) {
            mFlag &= ~PF_ROW_SECONDARY_SIZE_REFRESH;
        } else {
            updateRowSecondarySizeRefresh();
        }

        // For fastRelayout, only dispatch event when focus position changes or selected item
        // being updated.
        if ((mFlag & PF_FAST_RELAYOUT) != 0 && (mFocusPosition != savedFocusPos || mSubFocusPosition
                != savedSubFocusPos || findViewByPosition(mFocusPosition) != savedFocusView
                || (mFlag & PF_FAST_RELAYOUT_UPDATED_SELECTED_POSITION) != 0)) {
            dispatchChildSelected();
        } else if ((mFlag & (PF_FAST_RELAYOUT | PF_IN_LAYOUT_SEARCH_FOCUS))
                == PF_IN_LAYOUT_SEARCH_FOCUS) {
            // For full layout we dispatchChildSelected() in createItem() unless searched all
            // children and found none is focusable then dispatchChildSelected() here.
            dispatchChildSelected();
        }
        dispatchChildSelectedAndPositioned();
        if ((mFlag & PF_SLIDING) != 0) {
            scrollDirectionPrimary(getSlideOutDistance());
        }

        mFlag &= ~PF_STAGE_MASK;
        leaveContext();
        if (DEBUG) Log.v(getTag(), "layoutChildren end");
    }

    private void offsetChildrenSecondary(int increment) {
        final int childCount = getChildCount();
        if (mOrientation == HORIZONTAL) {
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).offsetTopAndBottom(increment);
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).offsetLeftAndRight(increment);
            }
        }
    }

    private void offsetChildrenPrimary(int increment) {
        final int childCount = getChildCount();
        if (mOrientation == VERTICAL) {
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).offsetTopAndBottom(increment);
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).offsetLeftAndRight(increment);
            }
        }
    }

    @Override
    public int scrollHorizontallyBy(int dx, Recycler recycler, RecyclerView.State state) {
        if (DEBUG) Log.v(getTag(), "scrollHorizontallyBy " + dx);
        if ((mFlag & PF_LAYOUT_ENABLED) == 0 || !hasDoneFirstLayout()) {
            return 0;
        }
        saveContext(recycler, state);
        mFlag = (mFlag & ~PF_STAGE_MASK) | PF_STAGE_SCROLL;
        int result;
        if (mOrientation == HORIZONTAL) {
            // MOD: fix RecycleView crash on Ugoos
            try {
                result = scrollDirectionPrimary(dx);
            } catch (NullPointerException | IllegalArgumentException e) {
                // Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()' on a null object reference
                // IllegalArgumentException: VideoCardPresenter$1 is not a direct child of androidx.leanback.widget.HorizontalGridView
                e.printStackTrace();
                result = 0;
            }
        } else {
            result = scrollDirectionSecondary(dx);
        }
        leaveContext();
        mFlag &= ~PF_STAGE_MASK;
        return result;
    }

    @Override
    public int scrollVerticallyBy(int dy, Recycler recycler, RecyclerView.State state) {
        if (DEBUG) Log.v(getTag(), "scrollVerticallyBy " + dy);
        if ((mFlag & PF_LAYOUT_ENABLED) == 0 || !hasDoneFirstLayout()) {
            return 0;
        }
        mFlag = (mFlag & ~PF_STAGE_MASK) | PF_STAGE_SCROLL;
        saveContext(recycler, state);
        int result;
        if (mOrientation == VERTICAL) {
            // MOD: fix RecycleView crash on Eltex (Android 9)
            try {
                result = scrollDirectionPrimary(dy);
            } catch (NullPointerException | IllegalArgumentException e) {
                // Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()' on a null object reference
                // IllegalArgumentException: VideoCardPresenter$1 is not a direct child of androidx.leanback.widget.HorizontalGridView
                e.printStackTrace();
                result = 0;
            }
        } else {
            result = scrollDirectionSecondary(dy);
        }
        leaveContext();
        mFlag &= ~PF_STAGE_MASK;
        return result;
    }

    // scroll in main direction may add/prune views
    private int scrollDirectionPrimary(int da) {
        if (TRACE) TraceCompat.beginSection("scrollPrimary");
        // We apply the cap of maxScroll/minScroll to the delta, except for two cases:
        // 1. when children are in sliding out mode
        // 2. During onLayoutChildren(), it may compensate the remaining scroll delta,
        //    we should honor the request regardless if it goes over minScroll / maxScroll.
        //    (see b/64931938 testScrollAndRemove and testScrollAndRemoveSample1)
        if ((mFlag & PF_SLIDING) == 0 && (mFlag & PF_STAGE_MASK) != PF_STAGE_LAYOUT) {
            if (da > 0) {
                if (!mWindowAlignment.mainAxis().isMaxUnknown()) {
                    int maxScroll = mWindowAlignment.mainAxis().getMaxScroll();
                    if (da > maxScroll) {
                        da = maxScroll;
                    }
                }
            } else if (da < 0) {
                if (!mWindowAlignment.mainAxis().isMinUnknown()) {
                    int minScroll = mWindowAlignment.mainAxis().getMinScroll();
                    if (da < minScroll) {
                        da = minScroll;
                    }
                }
            }
        }
        if (da == 0) {
            if (TRACE) TraceCompat.endSection();
            return 0;
        }
        offsetChildrenPrimary(-da);
        if ((mFlag & PF_STAGE_MASK) == PF_STAGE_LAYOUT) {
            updateScrollLimits();
            if (TRACE) TraceCompat.endSection();
            return da;
        }

        int childCount = getChildCount();
        boolean updated;

        if ((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0 ? da > 0 : da < 0) {
            prependVisibleItems();
        } else {
            appendVisibleItems();
        }
        updated = getChildCount() > childCount;
        childCount = getChildCount();

        if (TRACE) TraceCompat.beginSection("remove");
        if ((mFlag & PF_REVERSE_FLOW_PRIMARY) != 0 ? da > 0 : da < 0) {
            removeInvisibleViewsAtEnd();
        } else {
            removeInvisibleViewsAtFront();
        }
        if (TRACE) TraceCompat.endSection();
        updated |= getChildCount() < childCount;
        if (updated) {
            updateRowSecondarySizeRefresh();
        }

        mBaseGridView.invalidate();
        updateScrollLimits();
        if (TRACE) TraceCompat.endSection();
        return da;
    }

    // scroll in second direction will not add/prune views
    private int scrollDirectionSecondary(int dy) {
        if (dy == 0) {
            return 0;
        }
        offsetChildrenSecondary(-dy);
        mScrollOffsetSecondary += dy;
        updateSecondaryScrollLimits();
        mBaseGridView.invalidate();
        return dy;
    }

    @Override
    public void collectAdjacentPrefetchPositions(int dx, int dy, State state,
            LayoutPrefetchRegistry layoutPrefetchRegistry) {
        try {
            saveContext(null, state);
            int da = (mOrientation == HORIZONTAL) ? dx : dy;
            if (getChildCount() == 0 || da == 0) {
                // can't support this scroll, so don't bother prefetching
                return;
            }

            int fromLimit = da < 0
                    ? -mExtraLayoutSpace
                    : mSizePrimary + mExtraLayoutSpace;
            mGrid.collectAdjacentPrefetchPositions(fromLimit, da, layoutPrefetchRegistry);
        } finally {
            leaveContext();
        }
    }

    @Override
    public void collectInitialPrefetchPositions(int adapterItemCount,
            LayoutPrefetchRegistry layoutPrefetchRegistry) {
        int numToPrefetch = mBaseGridView.mInitialPrefetchItemCount;
        if (adapterItemCount != 0 && numToPrefetch != 0) {
            // prefetch items centered around mFocusPosition
            int initialPos = Math.max(0, Math.min(mFocusPosition - (numToPrefetch - 1)/ 2,
                    adapterItemCount - numToPrefetch));
            for (int i = initialPos; i < adapterItemCount && i < initialPos + numToPrefetch; i++) {
                layoutPrefetchRegistry.addPosition(i, 0);
            }
        }
    }

    void updateScrollLimits() {
        if (mState.getItemCount() == 0) {
            return;
        }
        int highVisiblePos, lowVisiblePos;
        int highMaxPos, lowMinPos;
        if ((mFlag & PF_REVERSE_FLOW_PRIMARY) == 0) {
            highVisiblePos = mGrid.getLastVisibleIndex();
            highMaxPos = mState.getItemCount() - 1;
            lowVisiblePos = mGrid.getFirstVisibleIndex();
            lowMinPos = 0;
        } else {
            highVisiblePos = mGrid.getFirstVisibleIndex();
            highMaxPos = 0;
            lowVisiblePos = mGrid.getLastVisibleIndex();
            lowMinPos = mState.getItemCount() - 1;
        }
        if (highVisiblePos < 0 || lowVisiblePos < 0) {
            return;
        }
        final boolean highAvailable = highVisiblePos == highMaxPos;
        final boolean lowAvailable = lowVisiblePos == lowMinPos;
        if (!highAvailable && mWindowAlignment.mainAxis().isMaxUnknown()
                && !lowAvailable && mWindowAlignment.mainAxis().isMinUnknown()) {
            return;
        }
        int maxEdge, maxViewCenter;
        if (highAvailable) {
            maxEdge = mGrid.findRowMax(true, sTwoInts);
            View maxChild = findViewByPosition(sTwoInts[1]);
            maxViewCenter = getViewCenter(maxChild);
            final LayoutParams lp = (LayoutParams) maxChild.getLayoutParams();
            int[] multipleAligns = lp.getAlignMultiple();
            if (multipleAligns != null && multipleAligns.length > 0) {
                maxViewCenter += multipleAligns[multipleAligns.length - 1] - multipleAligns[0];
            }
        } else {
            maxEdge = Integer.MAX_VALUE;
            maxViewCenter = Integer.MAX_VALUE;
        }
        int minEdge, minViewCenter;
        if (lowAvailable) {
            minEdge = mGrid.findRowMin(false, sTwoInts);
            View minChild = findViewByPosition(sTwoInts[1]);
            minViewCenter = getViewCenter(minChild);
        } else {
            minEdge = Integer.MIN_VALUE;
            minViewCenter = Integer.MIN_VALUE;
        }
        mWindowAlignment.mainAxis().updateMinMax(minEdge, maxEdge, minViewCenter, maxViewCenter);
    }

    /**
     * Update secondary axis's scroll min/max, should be updated in
     * {@link #scrollDirectionSecondary(int)}.
     */
    private void updateSecondaryScrollLimits() {
        WindowAlignment.Axis secondAxis = mWindowAlignment.secondAxis();
        int minEdge = secondAxis.getPaddingMin() - mScrollOffsetSecondary;
        int maxEdge = minEdge + getSizeSecondary();
        secondAxis.updateMinMax(minEdge, maxEdge, minEdge, maxEdge);
    }

    private void initScrollController() {
        mWindowAlignment.reset();
        mWindowAlignment.horizontal.setSize(getWidth());
        mWindowAlignment.vertical.setSize(getHeight());
        mWindowAlignment.horizontal.setPadding(getPaddingLeft(), getPaddingRight());
        mWindowAlignment.vertical.setPadding(getPaddingTop(), getPaddingBottom());
        mSizePrimary = mWindowAlignment.mainAxis().getSize();
        mScrollOffsetSecondary = 0;

        if (DEBUG) {
            Log.v(getTag(), "initScrollController mSizePrimary " + mSizePrimary
                    + " mWindowAlignment " + mWindowAlignment);
        }
    }

    private void updateScrollController() {
        mWindowAlignment.horizontal.setSize(getWidth());
        mWindowAlignment.vertical.setSize(getHeight());
        mWindowAlignment.horizontal.setPadding(getPaddingLeft(), getPaddingRight());
        mWindowAlignment.vertical.setPadding(getPaddingTop(), getPaddingBottom());
        mSizePrimary = mWindowAlignment.mainAxis().getSize();

        if (DEBUG) {
            Log.v(getTag(), "updateScrollController mSizePrimary " + mSizePrimary
                    + " mWindowAlignment " + mWindowAlignment);
        }
    }

    @Override
    public void scrollToPosition(int position) {
        setSelection(position, 0, false, 0);
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, State state,
            int position) {
        setSelection(position, 0, true, 0);
    }

    public void setSelection(int position,
            int primaryScrollExtra) {
        setSelection(position, 0, false, primaryScrollExtra);
    }

    public void setSelectionSmooth(int position) {
        setSelection(position, 0, true, 0);
    }

    public void setSelectionWithSub(int position, int subposition,
            int primaryScrollExtra) {
        setSelection(position, subposition, false, primaryScrollExtra);
    }

    public void setSelectionSmoothWithSub(int position, int subposition) {
        setSelection(position, subposition, true, 0);
    }

    public int getSelection() {
        return mFocusPosition;
    }

    public int getSubSelection() {
        return mSubFocusPosition;
    }

    public void setSelection(int position, int subposition, boolean smooth,
            int primaryScrollExtra) {
        if ((mFocusPosition != position && position != NO_POSITION)
                || subposition != mSubFocusPosition || primaryScrollExtra != mPrimaryScrollExtra) {
            scrollToSelection(position, subposition, smooth, primaryScrollExtra);
        }
    }

    void scrollToSelection(int position, int subposition,
            boolean smooth, int primaryScrollExtra) {
        if (TRACE) TraceCompat.beginSection("scrollToSelection");
        mPrimaryScrollExtra = primaryScrollExtra;

        View view = findViewByPosition(position);
        // scrollToView() is based on Adapter position. Only call scrollToView() when item
        // is still valid and no layout is requested, otherwise defer to next layout pass.
        // If it is still in smoothScrolling, we should either update smoothScroller or initiate
        // a layout.
        final boolean notSmoothScrolling = !isSmoothScrolling();
        if (notSmoothScrolling && !mBaseGridView.isLayoutRequested()
                && view != null && getAdapterPositionByView(view) == position) {
            mFlag |= PF_IN_SELECTION;
            scrollToView(view, smooth);
            mFlag &= ~PF_IN_SELECTION;
        } else {
            if ((mFlag & PF_LAYOUT_ENABLED) == 0 || (mFlag & PF_SLIDING) != 0) {
                mFocusPosition = position;
                mSubFocusPosition = subposition;
                mFocusPositionOffset = Integer.MIN_VALUE;
                return;
            }
            if (smooth && !mBaseGridView.isLayoutRequested()) {
                mFocusPosition = position;
                mSubFocusPosition = subposition;
                mFocusPositionOffset = Integer.MIN_VALUE;
                if (!hasDoneFirstLayout()) {
                    Log.w(getTag(), "setSelectionSmooth should "
                            + "not be called before first layout pass");
                    return;
                }
                position = startPositionSmoothScroller(position);
                if (position != mFocusPosition) {
                    // gets cropped by adapter size
                    mFocusPosition = position;
                    mSubFocusPosition = 0;
                }
            } else {
                // stopScroll might change mFocusPosition, so call it before assign value to
                // mFocusPosition
                if (!notSmoothScrolling) {
                    skipSmoothScrollerOnStopInternal();
                    mBaseGridView.stopScroll();
                }
                if (!mBaseGridView.isLayoutRequested()
                        && view != null && getAdapterPositionByView(view) == position) {
                    mFlag |= PF_IN_SELECTION;
                    scrollToView(view, smooth);
                    mFlag &= ~PF_IN_SELECTION;
                } else {
                    mFocusPosition = position;
                    mSubFocusPosition = subposition;
                    mFocusPositionOffset = Integer.MIN_VALUE;
                    mFlag |= PF_FORCE_FULL_LAYOUT;
                    requestLayout();
                }
            }
        }
        if (TRACE) TraceCompat.endSection();
    }

    int startPositionSmoothScroller(int position) {
        LinearSmoothScroller linearSmoothScroller = new GridLinearSmoothScroller() {
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                if (getChildCount() == 0) {
                    return null;
                }
                final int firstChildPos = getPosition(getChildAt(0));
                // TODO We should be able to deduce direction from bounds of current and target
                // focus, rather than making assumptions about positions and directionality
                final boolean isStart = (mFlag & PF_REVERSE_FLOW_PRIMARY) != 0
                        ? targetPosition > firstChildPos
                        : targetPosition < firstChildPos;
                final int direction = isStart ? -1 : 1;
                if (mOrientation == HORIZONTAL) {
                    return new PointF(direction, 0);
                } else {
                    return new PointF(0, direction);
                }
            }

        };
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
        return linearSmoothScroller.getTargetPosition();
    }

    /**
     * when start a new SmoothScroller or scroll to a different location, dont need
     * current SmoothScroller.onStopInternal() doing the scroll work.
     */
    void skipSmoothScrollerOnStopInternal() {
        if (mCurrentSmoothScroller != null) {
            mCurrentSmoothScroller.mSkipOnStopInternal = true;
        }
    }

    @Override
    public void startSmoothScroll(RecyclerView.SmoothScroller smoothScroller) {
        skipSmoothScrollerOnStopInternal();
        super.startSmoothScroll(smoothScroller);
        if (smoothScroller.isRunning() && smoothScroller instanceof GridLinearSmoothScroller) {
            mCurrentSmoothScroller = (GridLinearSmoothScroller) smoothScroller;
            if (mCurrentSmoothScroller instanceof PendingMoveSmoothScroller) {
                mPendingMoveSmoothScroller = (PendingMoveSmoothScroller) mCurrentSmoothScroller;
            } else {
                mPendingMoveSmoothScroller = null;
            }
        } else {
            mCurrentSmoothScroller = null;
            mPendingMoveSmoothScroller = null;
        }
    }

    private void processPendingMovement(boolean forward) {
        if (forward ? hasCreatedLastItem() : hasCreatedFirstItem()) {
            return;
        }
        if (mPendingMoveSmoothScroller == null) {
            // Stop existing scroller and create a new PendingMoveSmoothScroller.
            mBaseGridView.stopScroll();
            PendingMoveSmoothScroller linearSmoothScroller = new PendingMoveSmoothScroller(
                    forward ? 1 : -1, mNumRows > 1);
            mFocusPositionOffset = 0;
            startSmoothScroll(linearSmoothScroller);
        } else {
            if (forward) {
                mPendingMoveSmoothScroller.increasePendingMoves();
            } else {
                mPendingMoveSmoothScroller.decreasePendingMoves();
            }
        }
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        if (DEBUG) Log.v(getTag(), "onItemsAdded positionStart "
                + positionStart + " itemCount " + itemCount);
        if (mFocusPosition != NO_POSITION && mGrid != null && mGrid.getFirstVisibleIndex() >= 0
                && mFocusPositionOffset != Integer.MIN_VALUE) {
            int pos = mFocusPosition + mFocusPositionOffset;
            if (positionStart <= pos) {
                mFocusPositionOffset += itemCount;
            }
        }
        mChildrenStates.clear();
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        if (DEBUG) Log.v(getTag(), "onItemsChanged");
        mFocusPositionOffset = 0;
        mChildrenStates.clear();
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        if (DEBUG) Log.v(getTag(), "onItemsRemoved positionStart "
                + positionStart + " itemCount " + itemCount);
        if (mFocusPosition != NO_POSITION  && mGrid != null && mGrid.getFirstVisibleIndex() >= 0
                && mFocusPositionOffset != Integer.MIN_VALUE) {
            int pos = mFocusPosition + mFocusPositionOffset;
            if (positionStart <= pos) {
                if (positionStart + itemCount > pos) {
                    // stop updating offset after the focus item was removed
                    mFocusPositionOffset += positionStart - pos;
                    mFocusPosition += mFocusPositionOffset;
                    mFocusPositionOffset = Integer.MIN_VALUE;
                } else {
                    mFocusPositionOffset -= itemCount;
                }
            }
        }
        mChildrenStates.clear();
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int fromPosition, int toPosition,
            int itemCount) {
        if (DEBUG) Log.v(getTag(), "onItemsMoved fromPosition "
                + fromPosition + " toPosition " + toPosition);
        if (mFocusPosition != NO_POSITION && mFocusPositionOffset != Integer.MIN_VALUE) {
            int pos = mFocusPosition + mFocusPositionOffset;
            if (fromPosition <= pos && pos < fromPosition + itemCount) {
                // moved items include focused position
                mFocusPositionOffset += toPosition - fromPosition;
            } else if (fromPosition < pos && toPosition > pos - itemCount) {
                // move items before focus position to after focused position
                mFocusPositionOffset -= itemCount;
            } else if (fromPosition > pos && toPosition < pos) {
                // move items after focus position to before focused position
                mFocusPositionOffset += itemCount;
            }
        }
        mChildrenStates.clear();
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount) {
        if (DEBUG) Log.v(getTag(), "onItemsUpdated positionStart "
                + positionStart + " itemCount " + itemCount);
        for (int i = positionStart, end = positionStart + itemCount; i < end; i++) {
            mChildrenStates.remove(i);
        }
    }

    @Override
    public boolean onRequestChildFocus(RecyclerView parent, View child, View focused) {
        if ((mFlag & PF_FOCUS_SEARCH_DISABLED) != 0) {
            return true;
        }
        if (getAdapterPositionByView(child) == NO_POSITION) {
            // This is could be the last view in DISAPPEARING animation.
            return true;
        }
        if ((mFlag & (PF_STAGE_MASK | PF_IN_SELECTION)) == 0) {
            scrollToView(child, focused, true);
        }
        return true;
    }

    @Override
    public boolean requestChildRectangleOnScreen(RecyclerView parent, View view, Rect rect,
            boolean immediate) {
        if (DEBUG) Log.v(getTag(), "requestChildRectangleOnScreen " + view + " " + rect);
        return false;
    }

    public void getViewSelectedOffsets(View view, int[] offsets) {
        if (mOrientation == HORIZONTAL) {
            offsets[0] = getPrimaryAlignedScrollDistance(view);
            offsets[1] = getSecondaryScrollDistance(view);
        } else {
            offsets[1] = getPrimaryAlignedScrollDistance(view);
            offsets[0] = getSecondaryScrollDistance(view);
        }
    }

    /**
     * Return the scroll delta on primary direction to make the view selected. If the return value
     * is 0, there is no need to scroll.
     */
    private int getPrimaryAlignedScrollDistance(View view) {
        return mWindowAlignment.mainAxis().getScroll(getViewCenter(view));
    }

    /**
     * Get adjusted primary position for a given childView (if there is multiple ItemAlignment
     * defined on the view).
     */
    private int getAdjustedPrimaryAlignedScrollDistance(int scrollPrimary, View view,
            View childView) {
        int subindex = getSubPositionByView(view, childView);
        if (subindex != 0) {
            final LayoutParams lp = (LayoutParams) view.getLayoutParams();
            scrollPrimary += lp.getAlignMultiple()[subindex] - lp.getAlignMultiple()[0];
        }
        return scrollPrimary;
    }

    private int getSecondaryScrollDistance(View view) {
        int viewCenterSecondary = getViewCenterSecondary(view);
        return mWindowAlignment.secondAxis().getScroll(viewCenterSecondary);
    }

    /**
     * Scroll to a given child view and change mFocusPosition. Ignored when in slideOut() state.
     */
    void scrollToView(View view, boolean smooth) {
        scrollToView(view, view == null ? null : view.findFocus(), smooth);
    }

    void scrollToView(View view, boolean smooth, int extraDelta, int extraDeltaSecondary) {
        scrollToView(view, view == null ? null : view.findFocus(), smooth, extraDelta,
                extraDeltaSecondary);
    }

    private void scrollToView(View view, View childView, boolean smooth) {
        scrollToView(view, childView, smooth, 0, 0);
    }
    /**
     * Scroll to a given child view and change mFocusPosition. Ignored when in slideOut() state.
     */
    private void scrollToView(View view, View childView, boolean smooth, int extraDelta,
            int extraDeltaSecondary) {
        if ((mFlag & PF_SLIDING) != 0) {
            return;
        }
        int newFocusPosition = getAdapterPositionByView(view);
        int newSubFocusPosition = getSubPositionByView(view, childView);
        if (newFocusPosition != mFocusPosition || newSubFocusPosition != mSubFocusPosition) {
            mFocusPosition = newFocusPosition;
            mSubFocusPosition = newSubFocusPosition;
            mFocusPositionOffset = 0;
            if ((mFlag & PF_STAGE_MASK) != PF_STAGE_LAYOUT) {
                dispatchChildSelected();
            }
            if (mBaseGridView.isChildrenDrawingOrderEnabledInternal()) {
                mBaseGridView.invalidate();
            }
        }
        if (view == null) {
            return;
        }
        if (!view.hasFocus() && mBaseGridView.hasFocus()) {
            // transfer focus to the child if it does not have focus yet (e.g. triggered
            // by setSelection())
            view.requestFocus();
        }
        if ((mFlag & PF_SCROLL_ENABLED) == 0 && smooth) {
            return;
        }
        if (getScrollPosition(view, childView, sTwoInts)
                || extraDelta != 0 || extraDeltaSecondary != 0) {
            scrollGrid(sTwoInts[0] + extraDelta, sTwoInts[1] + extraDeltaSecondary, smooth);
        }
    }

    boolean getScrollPosition(View view, View childView, int[] deltas) {
        switch (mFocusScrollStrategy) {
            case BaseGridView.FOCUS_SCROLL_ALIGNED:
            default:
                return getAlignedPosition(view, childView, deltas);
            case BaseGridView.FOCUS_SCROLL_ITEM:
            case BaseGridView.FOCUS_SCROLL_PAGE:
                return getNoneAlignedPosition(view, deltas);
        }
    }

    private boolean getNoneAlignedPosition(View view, int[] deltas) {
        int pos = getAdapterPositionByView(view);
        int viewMin = getViewMin(view);
        int viewMax = getViewMax(view);
        // we either align "firstView" to left/top padding edge
        // or align "lastView" to right/bottom padding edge
        View firstView = null;
        View lastView = null;
        int paddingMin = mWindowAlignment.mainAxis().getPaddingMin();
        int clientSize = mWindowAlignment.mainAxis().getClientSize();
        final int row = mGrid.getRowIndex(pos);
        if (viewMin < paddingMin) {
            // view enters low padding area:
            firstView = view;
            if (mFocusScrollStrategy == BaseGridView.FOCUS_SCROLL_PAGE) {
                // scroll one "page" left/top,
                // align first visible item of the "page" at the low padding edge.
                while (prependOneColumnVisibleItems()) {
                    CircularIntArray positions =
                            mGrid.getItemPositionsInRows(mGrid.getFirstVisibleIndex(), pos)[row];
                    firstView = findViewByPosition(positions.get(0));
                    if (viewMax - getViewMin(firstView) > clientSize) {
                        if (positions.size() > 2) {
                            firstView = findViewByPosition(positions.get(2));
                        }
                        break;
                    }
                }
            }
        } else if (viewMax > clientSize + paddingMin) {
            // view enters high padding area:
            if (mFocusScrollStrategy == BaseGridView.FOCUS_SCROLL_PAGE) {
                // scroll whole one page right/bottom, align view at the low padding edge.
                firstView = view;
                do {
                    CircularIntArray positions =
                            mGrid.getItemPositionsInRows(pos, mGrid.getLastVisibleIndex())[row];
                    lastView = findViewByPosition(positions.get(positions.size() - 1));
                    if (getViewMax(lastView) - viewMin > clientSize) {
                        lastView = null;
                        break;
                    }
                } while (appendOneColumnVisibleItems());
                if (lastView != null) {
                    // however if we reached end,  we should align last view.
                    firstView = null;
                }
            } else {
                lastView = view;
            }
        }
        int scrollPrimary = 0;
        int scrollSecondary = 0;
        if (firstView != null) {
            scrollPrimary = getViewMin(firstView) - paddingMin;
        } else if (lastView != null) {
            scrollPrimary = getViewMax(lastView) - (paddingMin + clientSize);
        }
        View secondaryAlignedView;
        if (firstView != null) {
            secondaryAlignedView = firstView;
        } else if (lastView != null) {
            secondaryAlignedView = lastView;
        } else {
            secondaryAlignedView = view;
        }
        scrollSecondary = getSecondaryScrollDistance(secondaryAlignedView);
        if (scrollPrimary != 0 || scrollSecondary != 0) {
            deltas[0] = scrollPrimary;
            deltas[1] = scrollSecondary;
            return true;
        }
        return false;
    }

    private boolean getAlignedPosition(View view, View childView, int[] deltas) {
        int scrollPrimary = getPrimaryAlignedScrollDistance(view);
        if (childView != null) {
            scrollPrimary = getAdjustedPrimaryAlignedScrollDistance(scrollPrimary, view, childView);
        }
        int scrollSecondary = getSecondaryScrollDistance(view);
        if (DEBUG) {
            Log.v(getTag(), "getAlignedPosition " + scrollPrimary + " " + scrollSecondary
                    + " " + mPrimaryScrollExtra + " " + mWindowAlignment);
        }
        scrollPrimary += mPrimaryScrollExtra;
        if (scrollPrimary != 0 || scrollSecondary != 0) {
            deltas[0] = scrollPrimary;
            deltas[1] = scrollSecondary;
            return true;
        } else {
            deltas[0] = 0;
            deltas[1] = 0;
        }
        return false;
    }

    private void scrollGrid(int scrollPrimary, int scrollSecondary, boolean smooth) {
        if ((mFlag & PF_STAGE_MASK) == PF_STAGE_LAYOUT) {
            scrollDirectionPrimary(scrollPrimary);
            scrollDirectionSecondary(scrollSecondary);
        } else {
            int scrollX;
            int scrollY;
            if (mOrientation == HORIZONTAL) {
                scrollX = scrollPrimary;
                scrollY = scrollSecondary;
            } else {
                scrollX = scrollSecondary;
                scrollY = scrollPrimary;
            }
            if (smooth) {
                mBaseGridView.smoothScrollBy(scrollX, scrollY);
            } else {
                mBaseGridView.scrollBy(scrollX, scrollY);
                dispatchChildSelectedAndPositioned();
            }
        }
    }

    public void setPruneChild(boolean pruneChild) {
        if (((mFlag & PF_PRUNE_CHILD) != 0) != pruneChild) {
            mFlag = (mFlag & ~PF_PRUNE_CHILD) | (pruneChild ? PF_PRUNE_CHILD : 0);
            if (pruneChild) {
                requestLayout();
            }
        }
    }

    public boolean getPruneChild() {
        return (mFlag & PF_PRUNE_CHILD) != 0;
    }

    public void setScrollEnabled(boolean scrollEnabled) {
        if (((mFlag & PF_SCROLL_ENABLED) != 0) != scrollEnabled) {
            mFlag = (mFlag & ~PF_SCROLL_ENABLED) | (scrollEnabled ? PF_SCROLL_ENABLED : 0);
            if (((mFlag & PF_SCROLL_ENABLED) != 0)
                    && mFocusScrollStrategy == BaseGridView.FOCUS_SCROLL_ALIGNED
                    && mFocusPosition != NO_POSITION) {
                scrollToSelection(mFocusPosition, mSubFocusPosition,
                        true, mPrimaryScrollExtra);
            }
        }
    }

    public boolean isScrollEnabled() {
        return (mFlag & PF_SCROLL_ENABLED) != 0;
    }

    private int findImmediateChildIndex(View view) {
        if (mBaseGridView != null && view != mBaseGridView) {
            view = findContainingItemView(view);
            if (view != null) {
                for (int i = 0, count = getChildCount(); i < count; i++) {
                    if (getChildAt(i) == view) {
                        return i;
                    }
                }
            }
        }
        return NO_POSITION;
    }

    void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        if (gainFocus) {
            // if gridview.requestFocus() is called, select first focusable child.
            for (int i = mFocusPosition; ;i++) {
                View view = findViewByPosition(i);
                if (view == null) {
                    break;
                }
                if (view.getVisibility() == View.VISIBLE && view.hasFocusable()) {
                    view.requestFocus();
                    break;
                }
            }
        }
    }

    void setFocusSearchDisabled(boolean disabled) {
        mFlag = (mFlag & ~PF_FOCUS_SEARCH_DISABLED) | (disabled ? PF_FOCUS_SEARCH_DISABLED : 0);
    }

    boolean isFocusSearchDisabled() {
        return (mFlag & PF_FOCUS_SEARCH_DISABLED) != 0;
    }

    @Override
    public View onInterceptFocusSearch(View focused, int direction) {
        if ((mFlag & PF_FOCUS_SEARCH_DISABLED) != 0) {
            return focused;
        }

        final FocusFinder ff = FocusFinder.getInstance();
        View result = null;
        if (direction == View.FOCUS_FORWARD || direction == View.FOCUS_BACKWARD) {
            // convert direction to absolute direction and see if we have a view there and if not
            // tell LayoutManager to add if it can.
            if (canScrollVertically()) {
                final int absDir =
                        direction == View.FOCUS_FORWARD ? View.FOCUS_DOWN : View.FOCUS_UP;
                result = ff.findNextFocus(mBaseGridView, focused, absDir);
            }
            if (canScrollHorizontally()) {
                boolean rtl = getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL;
                final int absDir = (direction == View.FOCUS_FORWARD) ^ rtl
                        ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
                result = ff.findNextFocus(mBaseGridView, focused, absDir);
            }
        } else {
            result = ff.findNextFocus(mBaseGridView, focused, direction);
        }
        if (result != null) {
            return result;
        }

        if (mBaseGridView.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
            return mBaseGridView.getParent().focusSearch(focused, direction);
        }

        if (DEBUG) Log.v(getTag(), "regular focusSearch failed direction " + direction);
        int movement = getMovement(direction);
        final boolean isScroll = mBaseGridView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
        if (movement == NEXT_ITEM) {
            if (isScroll || (mFlag & PF_FOCUS_OUT_END) == 0) {
                // MOD: dialog loop navigation (not good, too fast scrolling)
                //result = mBaseGridView.getChildAt(0);
                result = focused;
            }
            if ((mFlag & PF_SCROLL_ENABLED) != 0 && !hasCreatedLastItem()) {
                processPendingMovement(true);
                result = focused;
            }
        } else if (movement == PREV_ITEM) {
            if (isScroll || (mFlag & PF_FOCUS_OUT_FRONT) == 0) {
                // MOD: dialog loop navigation (not good, too fast scrolling)
                //result = mBaseGridView.getChildAt(mBaseGridView.getChildCount() - 1);
                result = focused;
            }
            if ((mFlag & PF_SCROLL_ENABLED) != 0 && !hasCreatedFirstItem()) {
                processPendingMovement(false);
                result = focused;
            }
        } else if (movement == NEXT_ROW) {
            if (isScroll || (mFlag & PF_FOCUS_OUT_SIDE_END) == 0) {
                result = focused;
            }
        } else if (movement == PREV_ROW) {
            if (isScroll || (mFlag & PF_FOCUS_OUT_SIDE_START) == 0) {
                result = focused;
            }
        }
        if (result != null) {
            return result;
        }

        if (DEBUG) Log.v(getTag(), "now focusSearch in parent");
        result = mBaseGridView.getParent().focusSearch(focused, direction);
        if (result != null) {
            return result;
        }
        return focused != null ? focused : mBaseGridView;
    }

    boolean hasPreviousViewInSameRow(int pos) {
        if (mGrid == null || pos == NO_POSITION || mGrid.getFirstVisibleIndex() < 0) {
            return false;
        }
        if (mGrid.getFirstVisibleIndex() > 0) {
            return true;
        }
        final int focusedRow = mGrid.getLocation(pos).row;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            int position = getAdapterPositionByIndex(i);
            Grid.Location loc = mGrid.getLocation(position);
            if (loc != null && loc.row == focusedRow) {
                if (position < pos) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onAddFocusables(RecyclerView recyclerView,
            ArrayList<View> views, int direction, int focusableMode) {
        if ((mFlag & PF_FOCUS_SEARCH_DISABLED) != 0) {
            return true;
        }
        // If this viewgroup or one of its children currently has focus then we
        // consider our children for focus searching in main direction on the same row.
        // If this viewgroup has no focus and using focus align, we want the system
        // to ignore our children and pass focus to the viewgroup, which will pass
        // focus on to its children appropriately.
        // If this viewgroup has no focus and not using focus align, we want to
        // consider the child that does not overlap with padding area.
        if (recyclerView.hasFocus()) {
            if (mPendingMoveSmoothScroller != null) {
                // don't find next focusable if has pending movement.
                return true;
            }
            final int movement = getMovement(direction);
            final View focused = recyclerView.findFocus();
            final int focusedIndex = findImmediateChildIndex(focused);
            final int focusedPos = getAdapterPositionByIndex(focusedIndex);
            // Even if focusedPos != NO_POSITION, findViewByPosition could return null if the view
            // is ignored or getLayoutPosition does not match the adapter position of focused view.
            final View immediateFocusedChild = (focusedPos == NO_POSITION) ? null
                    : findViewByPosition(focusedPos);
            // Add focusables of focused item.
            if (immediateFocusedChild != null) {
                immediateFocusedChild.addFocusables(views,  direction, focusableMode);
            }
            if (mGrid == null || getChildCount() == 0) {
                // no grid information, or no child, bail out.
                return true;
            }
            if ((movement == NEXT_ROW || movement == PREV_ROW) && mGrid.getNumRows() <= 1) {
                // For single row, cannot navigate to previous/next row.
                return true;
            }
            // Add focusables of neighbor depending on the focus search direction.
            final int focusedRow = mGrid != null && immediateFocusedChild != null
                    ? mGrid.getLocation(focusedPos).row : NO_POSITION;
            final int focusableCount = views.size();
            int inc = movement == NEXT_ITEM || movement == NEXT_ROW ? 1 : -1;
            int loop_end = inc > 0 ? getChildCount() - 1 : 0;
            int loop_start;
            if (focusedIndex == NO_POSITION) {
                loop_start = inc > 0 ? 0 : getChildCount() - 1;
            } else {
                loop_start = focusedIndex + inc;
            }
            for (int i = loop_start; inc > 0 ? i <= loop_end : i >= loop_end; i += inc) {
                final View child = getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || !child.hasFocusable()) {
                    continue;
                }
                // if there wasn't any focused item, add the very first focusable
                // items and stop.
                if (immediateFocusedChild == null) {
                    child.addFocusables(views,  direction, focusableMode);
                    if (views.size() > focusableCount) {
                        break;
                    }
                    continue;
                }
                int position = getAdapterPositionByIndex(i);
                Grid.Location loc = mGrid.getLocation(position);
                if (loc == null) {
                    continue;
                }
                if (movement == NEXT_ITEM) {
                    // Add first focusable item on the same row
                    if (loc.row == focusedRow && position > focusedPos) {
                        child.addFocusables(views,  direction, focusableMode);
                        if (views.size() > focusableCount) {
                            break;
                        }
                    }
                } else if (movement == PREV_ITEM) {
                    // Add first focusable item on the same row
                    if (loc.row == focusedRow && position < focusedPos) {
                        child.addFocusables(views,  direction, focusableMode);
                        if (views.size() > focusableCount) {
                            break;
                        }
                    }
                } else if (movement == NEXT_ROW) {
                    // Add all focusable items after this item whose row index is bigger
                    if (loc.row == focusedRow) {
                        continue;
                    } else if (loc.row < focusedRow) {
                        break;
                    }
                    child.addFocusables(views,  direction, focusableMode);
                } else if (movement == PREV_ROW) {
                    // Add all focusable items before this item whose row index is smaller
                    if (loc.row == focusedRow) {
                        continue;
                    } else if (loc.row > focusedRow) {
                        break;
                    }
                    child.addFocusables(views,  direction, focusableMode);
                }
            }
        } else {
            int focusableCount = views.size();
            if (mFocusScrollStrategy != BaseGridView.FOCUS_SCROLL_ALIGNED) {
                // adding views not overlapping padding area to avoid scrolling in gaining focus
                int left = mWindowAlignment.mainAxis().getPaddingMin();
                int right = mWindowAlignment.mainAxis().getClientSize() + left;
                for (int i = 0, count = getChildCount(); i < count; i++) {
                    View child = getChildAt(i);
                    if (child.getVisibility() == View.VISIBLE) {
                        if (getViewMin(child) >= left && getViewMax(child) <= right) {
                            child.addFocusables(views, direction, focusableMode);
                        }
                    }
                }
                // if we cannot find any, then just add all children.
                if (views.size() == focusableCount) {
                    for (int i = 0, count = getChildCount(); i < count; i++) {
                        View child = getChildAt(i);
                        if (child.getVisibility() == View.VISIBLE) {
                            child.addFocusables(views, direction, focusableMode);
                        }
                    }
                }
            } else {
                View view = findViewByPosition(mFocusPosition);
                if (view != null) {
                    view.addFocusables(views, direction, focusableMode);
                }
            }
            // if still cannot find any, fall through and add itself
            if (views.size() != focusableCount) {
                return true;
            }
            if (recyclerView.isFocusable()) {
                views.add(recyclerView);
            }
        }
        return true;
    }

    boolean hasCreatedLastItem() {
        int count = getItemCount();
        return count == 0 || mBaseGridView.findViewHolderForAdapterPosition(count - 1) != null;
    }

    boolean hasCreatedFirstItem() {
        int count = getItemCount();
        return count == 0 || mBaseGridView.findViewHolderForAdapterPosition(0) != null;
    }

    boolean isItemFullyVisible(int pos) {
        RecyclerView.ViewHolder vh = mBaseGridView.findViewHolderForAdapterPosition(pos);
        if (vh == null) {
            return false;
        }
        return vh.itemView.getLeft() >= 0 && vh.itemView.getRight() <= mBaseGridView.getWidth()
                && vh.itemView.getTop() >= 0 && vh.itemView.getBottom()
                <= mBaseGridView.getHeight();
    }

    boolean canScrollTo(View view) {
        return view.getVisibility() == View.VISIBLE && (!hasFocus() || view.hasFocusable());
    }

    boolean gridOnRequestFocusInDescendants(RecyclerView recyclerView, int direction,
            Rect previouslyFocusedRect) {
        switch (mFocusScrollStrategy) {
            case BaseGridView.FOCUS_SCROLL_ALIGNED:
            default:
                return gridOnRequestFocusInDescendantsAligned(recyclerView,
                        direction, previouslyFocusedRect);
            case BaseGridView.FOCUS_SCROLL_PAGE:
            case BaseGridView.FOCUS_SCROLL_ITEM:
                return gridOnRequestFocusInDescendantsUnaligned(recyclerView,
                        direction, previouslyFocusedRect);
        }
    }

    private boolean gridOnRequestFocusInDescendantsAligned(RecyclerView recyclerView,
            int direction, Rect previouslyFocusedRect) {
        View view = findViewByPosition(mFocusPosition);
        if (view != null) {
            boolean result = view.requestFocus(direction, previouslyFocusedRect);
            if (!result && DEBUG) {
                Log.w(getTag(), "failed to request focus on " + view);
            }
            return result;
        }
        return false;
    }

    private boolean gridOnRequestFocusInDescendantsUnaligned(RecyclerView recyclerView,
            int direction, Rect previouslyFocusedRect) {
        // focus to view not overlapping padding area to avoid scrolling in gaining focus
        int index;
        int increment;
        int end;
        int count = getChildCount();
        if ((direction & View.FOCUS_FORWARD) != 0) {
            index = 0;
            increment = 1;
            end = count;
        } else {
            index = count - 1;
            increment = -1;
            end = -1;
        }
        int left = mWindowAlignment.mainAxis().getPaddingMin();
        int right = mWindowAlignment.mainAxis().getClientSize() + left;
        for (int i = index; i != end; i += increment) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                if (getViewMin(child) >= left && getViewMax(child) <= right) {
                    if (child.requestFocus(direction, previouslyFocusedRect)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private final static int PREV_ITEM = 0;
    private final static int NEXT_ITEM = 1;
    private final static int PREV_ROW = 2;
    private final static int NEXT_ROW = 3;

    private int getMovement(int direction) {
        int movement = View.FOCUS_LEFT;

        if (mOrientation == HORIZONTAL) {
            switch(direction) {
                case View.FOCUS_LEFT:
                    movement = (mFlag & PF_REVERSE_FLOW_PRIMARY) == 0 ? PREV_ITEM : NEXT_ITEM;
                    break;
                case View.FOCUS_RIGHT:
                    movement = (mFlag & PF_REVERSE_FLOW_PRIMARY) == 0 ? NEXT_ITEM : PREV_ITEM;
                    break;
                case View.FOCUS_UP:
                    movement = PREV_ROW;
                    break;
                case View.FOCUS_DOWN:
                    movement = NEXT_ROW;
                    break;
            }
        } else if (mOrientation == VERTICAL) {
            switch(direction) {
                case View.FOCUS_LEFT:
                    movement = (mFlag & PF_REVERSE_FLOW_SECONDARY) == 0 ? PREV_ROW : NEXT_ROW;
                    break;
                case View.FOCUS_RIGHT:
                    movement = (mFlag & PF_REVERSE_FLOW_SECONDARY) == 0 ? NEXT_ROW : PREV_ROW;
                    break;
                case View.FOCUS_UP:
                    movement = PREV_ITEM;
                    break;
                case View.FOCUS_DOWN:
                    movement = NEXT_ITEM;
                    break;
            }
        }

        return movement;
    }

    int getChildDrawingOrder(RecyclerView recyclerView, int childCount, int i) {
        View view = findViewByPosition(mFocusPosition);
        if (view == null) {
            return i;
        }
        int focusIndex = recyclerView.indexOfChild(view);
        // supposely 0 1 2 3 4 5 6 7 8 9, 4 is the center item
        // drawing order is 0 1 2 3 9 8 7 6 5 4
        if (i < focusIndex) {
            return i;
        } else if (i < childCount - 1) {
            return focusIndex + childCount - 1 - i;
        } else {
            return focusIndex;
        }
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter,
            RecyclerView.Adapter newAdapter) {
        if (DEBUG) Log.v(getTag(), "onAdapterChanged to " + newAdapter);
        if (oldAdapter != null) {
            discardLayoutInfo();
            mFocusPosition = NO_POSITION;
            mFocusPositionOffset = 0;
            mChildrenStates.clear();
        }
        if (newAdapter instanceof FacetProviderAdapter) {
            mFacetProviderAdapter = (FacetProviderAdapter) newAdapter;
        } else {
            mFacetProviderAdapter = null;
        }
        super.onAdapterChanged(oldAdapter, newAdapter);
    }

    private void discardLayoutInfo() {
        mGrid = null;
        mRowSizeSecondary = null;
        mFlag &= ~PF_ROW_SECONDARY_SIZE_REFRESH;
    }

    public void setLayoutEnabled(boolean layoutEnabled) {
        if (((mFlag & PF_LAYOUT_ENABLED) != 0) != layoutEnabled) {
            mFlag = (mFlag & ~PF_LAYOUT_ENABLED) | (layoutEnabled ? PF_LAYOUT_ENABLED : 0);
            requestLayout();
        }
    }

    void setChildrenVisibility(int visibility) {
        mChildVisibility = visibility;
        if (mChildVisibility != -1) {
            int count = getChildCount();
            for (int i= 0; i < count; i++) {
                getChildAt(i).setVisibility(mChildVisibility);
            }
        }
    }

    final static class SavedState implements Parcelable {

        int index; // index inside adapter of the current view
        Bundle childStates = Bundle.EMPTY;

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(index);
            out.writeBundle(childStates);
        }

        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        @Override
        public int describeContents() {
            return 0;
        }

        SavedState(Parcel in) {
            index = in.readInt();
            childStates = in.readBundle(GridLayoutManager.class.getClassLoader());
        }

        SavedState() {
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (DEBUG) Log.v(getTag(), "onSaveInstanceState getSelection() " + getSelection());
        SavedState ss = new SavedState();
        // save selected index
        ss.index = getSelection();
        // save offscreen child (state when they are recycled)
        Bundle bundle = mChildrenStates.saveAsBundle();
        // save views currently is on screen (TODO save cached views)
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View view = getChildAt(i);
            int position = getAdapterPositionByView(view);
            if (position != NO_POSITION) {
                bundle = mChildrenStates.saveOnScreenView(bundle, view, position);
            }
        }
        ss.childStates = bundle;
        return ss;
    }

    void onChildRecycled(RecyclerView.ViewHolder holder) {
        final int position = holder.getAdapterPosition();
        if (position != NO_POSITION) {
            mChildrenStates.saveOffscreenView(holder.itemView, position);
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            return;
        }
        SavedState loadingState = (SavedState)state;
        mFocusPosition = loadingState.index;
        mFocusPositionOffset = 0;
        mChildrenStates.loadFromBundle(loadingState.childStates);
        mFlag |= PF_FORCE_FULL_LAYOUT;
        requestLayout();
        if (DEBUG) Log.v(getTag(), "onRestoreInstanceState mFocusPosition " + mFocusPosition);
    }

    @Override
    public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        if (mOrientation == HORIZONTAL && mGrid != null) {
            return mGrid.getNumRows();
        }
        return super.getRowCountForAccessibility(recycler, state);
    }

    @Override
    public int getColumnCountForAccessibility(RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        if (mOrientation == VERTICAL && mGrid != null) {
            return mGrid.getNumRows();
        }
        return super.getColumnCountForAccessibility(recycler, state);
    }

    @Override
    public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler,
            RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
        ViewGroup.LayoutParams lp = host.getLayoutParams();
        if (mGrid == null || !(lp instanceof LayoutParams)) {
            return;
        }
        LayoutParams glp = (LayoutParams) lp;
        int position = glp.getViewAdapterPosition();
        int rowIndex = position >= 0 ? mGrid.getRowIndex(position) : -1;
        if (rowIndex < 0) {
            return;
        }
        int guessSpanIndex = position / mGrid.getNumRows();
        if (mOrientation == HORIZONTAL) {
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    rowIndex, 1, guessSpanIndex, 1, false, false));
        } else {
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    guessSpanIndex, 1, rowIndex, 1, false, false));
        }
    }

    /*
     * Leanback widget is different than the default implementation because the "scroll" is driven
     * by selection change.
     */
    @Override
    public boolean performAccessibilityAction(Recycler recycler, State state, int action,
            Bundle args) {
        if (!isScrollEnabled()) {
            // eat action request so that talkback wont focus out of RV
            return true;
        }
        saveContext(recycler, state);
        int translatedAction = action;
        boolean reverseFlowPrimary = (mFlag & PF_REVERSE_FLOW_PRIMARY) != 0;
        if (Build.VERSION.SDK_INT >= 23) {
            if (mOrientation == HORIZONTAL) {
                if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat
                        .ACTION_SCROLL_LEFT.getId()) {
                    translatedAction = reverseFlowPrimary
                            ? AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD :
                            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
                } else if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat
                        .ACTION_SCROLL_RIGHT.getId()) {
                    translatedAction = reverseFlowPrimary
                            ? AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD :
                            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
                }
            } else { // VERTICAL layout
                if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP
                        .getId()) {
                    translatedAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
                } else if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat
                        .ACTION_SCROLL_DOWN.getId()) {
                    translatedAction = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
                }
            }
        }
        switch (translatedAction) {
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                processPendingMovement(false);
                processSelectionMoves(false, -1);
                break;
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                processPendingMovement(true);
                processSelectionMoves(false, 1);
                break;
        }
        leaveContext();
        return true;
    }

    /*
     * Move mFocusPosition multiple steps on the same row in main direction.
     * Stops when moves are all consumed or reach first/last visible item.
     * Returning remaining moves.
     */
    int processSelectionMoves(boolean preventScroll, int moves) {
        if (mGrid == null) {
            return moves;
        }
        int focusPosition = mFocusPosition;
        int focusedRow = focusPosition != NO_POSITION
                ? mGrid.getRowIndex(focusPosition) : NO_POSITION;
        View newSelected = null;
        for (int i = 0, count = getChildCount(); i < count && moves != 0; i++) {
            int index = moves > 0 ? i : count - 1 - i;
            final View child = getChildAt(index);
            if (!canScrollTo(child)) {
                continue;
            }
            int position = getAdapterPositionByIndex(index);
            int rowIndex = mGrid.getRowIndex(position);
            if (focusedRow == NO_POSITION) {
                focusPosition = position;
                newSelected = child;
                focusedRow = rowIndex;
            } else if (rowIndex == focusedRow) {
                if ((moves > 0 && position > focusPosition)
                        || (moves < 0 && position < focusPosition)) {
                    focusPosition = position;
                    newSelected = child;
                    if (moves > 0) {
                        moves--;
                    } else {
                        moves++;
                    }
                }
            }
        }
        if (newSelected != null) {
            if (preventScroll) {
                if (hasFocus()) {
                    mFlag |= PF_IN_SELECTION;
                    newSelected.requestFocus();
                    mFlag &= ~PF_IN_SELECTION;
                }
                mFocusPosition = focusPosition;
                mSubFocusPosition = 0;
            } else {
                scrollToView(newSelected, true);
            }
        }
        return moves;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(Recycler recycler, State state,
            AccessibilityNodeInfoCompat info) {
        saveContext(recycler, state);
        int count = state.getItemCount();
        boolean reverseFlowPrimary = (mFlag & PF_REVERSE_FLOW_PRIMARY) != 0;
        if (count > 1 && !isItemFullyVisible(0)) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (mOrientation == HORIZONTAL) {
                    info.addAction(reverseFlowPrimary
                            ? AccessibilityNodeInfoCompat.AccessibilityActionCompat
                                    .ACTION_SCROLL_RIGHT :
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat
                                    .ACTION_SCROLL_LEFT);
                } else {
                    info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP);
                }
            } else {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
            }
            info.setScrollable(true);
        }
        if (count > 1 && !isItemFullyVisible(count - 1)) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (mOrientation == HORIZONTAL) {
                    info.addAction(reverseFlowPrimary
                            ? AccessibilityNodeInfoCompat.AccessibilityActionCompat
                                    .ACTION_SCROLL_LEFT :
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat
                                    .ACTION_SCROLL_RIGHT);
                } else {
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat
                                    .ACTION_SCROLL_DOWN);
                }
            } else {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
            }
            info.setScrollable(true);
        }
        final AccessibilityNodeInfoCompat.CollectionInfoCompat collectionInfo =
                AccessibilityNodeInfoCompat.CollectionInfoCompat
                        .obtain(getRowCountForAccessibility(recycler, state),
                                getColumnCountForAccessibility(recycler, state),
                                isLayoutHierarchical(recycler, state),
                                getSelectionModeForAccessibility(recycler, state));
        info.setCollectionInfo(collectionInfo);
        leaveContext();
    }
}
