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
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/**
 * An abstract base class for vertically and horizontally scrolling lists. The items come
 * from the {@link RecyclerView.Adapter} associated with this view.
 * Do not directly use this class, use {@link VerticalGridView} and {@link HorizontalGridView}.
 * The class is not intended to be subclassed other than {@link VerticalGridView} and
 * {@link HorizontalGridView}.
 */
public abstract class BaseGridView extends RecyclerView {

    /**
     * Always keep focused item at a aligned position.  Developer can use
     * WINDOW_ALIGN_XXX and ITEM_ALIGN_XXX to define how focused item is aligned.
     * In this mode, the last focused position will be remembered and restored when focus
     * is back to the view.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public final static int FOCUS_SCROLL_ALIGNED = 0;

    /**
     * Scroll to make the focused item inside client area.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public final static int FOCUS_SCROLL_ITEM = 1;

    /**
     * Scroll a page of items when focusing to item outside the client area.
     * The page size matches the client area size of RecyclerView.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public final static int FOCUS_SCROLL_PAGE = 2;

    /**
     * The first item is aligned with the low edge of the viewport. When
     * navigating away from the first item, the focus item is aligned to a key line location.
     * <p>
     * For HorizontalGridView, low edge refers to getPaddingLeft() when RTL is false or
     * getWidth() - getPaddingRight() when RTL is true.
     * For VerticalGridView, low edge refers to getPaddingTop().
     * <p>
     * The key line location is calculated by "windowAlignOffset" and
     * "windowAlignOffsetPercent"; if neither of these two is defined, the
     * default value is 1/2 of the size.
     * <p>
     * Note if there are very few items between low edge and key line, use
     * {@link #setWindowAlignmentPreferKeyLineOverLowEdge(boolean)} to control whether you prefer
     * to align the items to key line or low edge. Default is preferring low edge.
     */
    public final static int WINDOW_ALIGN_LOW_EDGE = 1;

    /**
     * The last item is aligned with the high edge of the viewport when
     * navigating to the end of list. When navigating away from the end, the
     * focus item is aligned to a key line location.
     * <p>
     * For HorizontalGridView, high edge refers to getWidth() - getPaddingRight() when RTL is false
     * or getPaddingLeft() when RTL is true.
     * For VerticalGridView, high edge refers to getHeight() - getPaddingBottom().
     * <p>
     * The key line location is calculated by "windowAlignOffset" and
     * "windowAlignOffsetPercent"; if neither of these two is defined, the
     * default value is 1/2 of the size.
     * <p>
     * Note if there are very few items between high edge and key line, use
     * {@link #setWindowAlignmentPreferKeyLineOverHighEdge(boolean)} to control whether you prefer
     * to align the items to key line or high edge. Default is preferring key line.
     */
    public final static int WINDOW_ALIGN_HIGH_EDGE = 1 << 1;

    /**
     * The first item and last item are aligned with the two edges of the
     * viewport. When navigating in the middle of list, the focus maintains a
     * key line location.
     * <p>
     * The key line location is calculated by "windowAlignOffset" and
     * "windowAlignOffsetPercent"; if neither of these two is defined, the
     * default value is 1/2 of the size.
     */
    public final static int WINDOW_ALIGN_BOTH_EDGE =
            WINDOW_ALIGN_LOW_EDGE | WINDOW_ALIGN_HIGH_EDGE;

    /**
     * The focused item always stays in a key line location.
     * <p>
     * The key line location is calculated by "windowAlignOffset" and
     * "windowAlignOffsetPercent"; if neither of these two is defined, the
     * default value is 1/2 of the size.
     */
    public final static int WINDOW_ALIGN_NO_EDGE = 0;

    /**
     * Value indicates that percent is not used.
     */
    public final static float WINDOW_ALIGN_OFFSET_PERCENT_DISABLED = -1;

    /**
     * Value indicates that percent is not used.
     */
    public final static float ITEM_ALIGN_OFFSET_PERCENT_DISABLED =
            ItemAlignmentFacet.ITEM_ALIGN_OFFSET_PERCENT_DISABLED;

    /**
     * Dont save states of any child views.
     */
    public static final int SAVE_NO_CHILD = 0;

    /**
     * Only save on screen child views, the states are lost when they become off screen.
     */
    public static final int SAVE_ON_SCREEN_CHILD = 1;

    /**
     * Save on screen views plus save off screen child views states up to
     * {@link #getSaveChildrenLimitNumber()}.
     */
    public static final int SAVE_LIMITED_CHILD = 2;

    /**
     * Save on screen views plus save off screen child views without any limitation.
     * This might cause out of memory, only use it when you are dealing with limited data.
     */
    public static final int SAVE_ALL_CHILD = 3;

    /**
     * Listener for intercepting touch dispatch events.
     */
    public interface OnTouchInterceptListener {
        /**
         * Returns true if the touch dispatch event should be consumed.
         */
        public boolean onInterceptTouchEvent(MotionEvent event);
    }

    /**
     * Listener for intercepting generic motion dispatch events.
     */
    public interface OnMotionInterceptListener {
        /**
         * Returns true if the touch dispatch event should be consumed.
         */
        public boolean onInterceptMotionEvent(MotionEvent event);
    }

    /**
     * Listener for intercepting key dispatch events.
     */
    public interface OnKeyInterceptListener {
        /**
         * Returns true if the key dispatch event should be consumed.
         */
        public boolean onInterceptKeyEvent(KeyEvent event);
    }

    public interface OnUnhandledKeyListener {
        /**
         * Returns true if the key event should be consumed.
         */
        public boolean onUnhandledKey(KeyEvent event);
    }

    final GridLayoutManager mLayoutManager;

    /**
     * Animate layout changes from a child resizing or adding/removing a child.
     */
    private boolean mAnimateChildLayout = true;

    private boolean mHasOverlappingRendering = true;

    private RecyclerView.ItemAnimator mSavedItemAnimator;

    private OnTouchInterceptListener mOnTouchInterceptListener;
    private OnMotionInterceptListener mOnMotionInterceptListener;
    private OnKeyInterceptListener mOnKeyInterceptListener;
    RecyclerView.RecyclerListener mChainedRecyclerListener;
    private OnUnhandledKeyListener mOnUnhandledKeyListener;

    /**
     * Number of items to prefetch when first coming on screen with new data.
     */
    int mInitialPrefetchItemCount = 4;

    BaseGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLayoutManager = new GridLayoutManager(this);
        setLayoutManager(mLayoutManager);
        // leanback LayoutManager already restores focus inside onLayoutChildren().
        setPreserveFocusAfterLayout(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setHasFixedSize(true);
        setChildrenDrawingOrderEnabled(true);
        setWillNotDraw(true);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Disable change animation by default on leanback.
        // Change animation will create a new view and cause undesired
        // focus animation between the old view and new view.
        ((SimpleItemAnimator)getItemAnimator()).setSupportsChangeAnimations(false);
        super.setRecyclerListener(new RecyclerView.RecyclerListener() {
            @Override
            public void onViewRecycled(RecyclerView.ViewHolder holder) {
                mLayoutManager.onChildRecycled(holder);
                if (mChainedRecyclerListener != null) {
                    mChainedRecyclerListener.onViewRecycled(holder);
                }
            }
        });
    }

    void initBaseGridViewAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbBaseGridView);
        boolean throughFront = a.getBoolean(R.styleable.lbBaseGridView_focusOutFront, false);
        boolean throughEnd = a.getBoolean(R.styleable.lbBaseGridView_focusOutEnd, false);
        mLayoutManager.setFocusOutAllowed(throughFront, throughEnd);
        boolean throughSideStart = a.getBoolean(R.styleable.lbBaseGridView_focusOutSideStart, true);
        boolean throughSideEnd = a.getBoolean(R.styleable.lbBaseGridView_focusOutSideEnd, true);
        mLayoutManager.setFocusOutSideAllowed(throughSideStart, throughSideEnd);
        mLayoutManager.setVerticalSpacing(
                a.getDimensionPixelSize(R.styleable.lbBaseGridView_android_verticalSpacing,
                        a.getDimensionPixelSize(R.styleable.lbBaseGridView_verticalMargin, 0)));
        mLayoutManager.setHorizontalSpacing(
                a.getDimensionPixelSize(R.styleable.lbBaseGridView_android_horizontalSpacing,
                        a.getDimensionPixelSize(R.styleable.lbBaseGridView_horizontalMargin, 0)));
        if (a.hasValue(R.styleable.lbBaseGridView_android_gravity)) {
            setGravity(a.getInt(R.styleable.lbBaseGridView_android_gravity, Gravity.NO_GRAVITY));
        }
        a.recycle();
    }

    /**
     * Sets the strategy used to scroll in response to item focus changing:
     * <ul>
     * <li>{@link #FOCUS_SCROLL_ALIGNED} (default) </li>
     * <li>{@link #FOCUS_SCROLL_ITEM}</li>
     * <li>{@link #FOCUS_SCROLL_PAGE}</li>
     * </ul>
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setFocusScrollStrategy(int scrollStrategy) {
        if (scrollStrategy != FOCUS_SCROLL_ALIGNED && scrollStrategy != FOCUS_SCROLL_ITEM
            && scrollStrategy != FOCUS_SCROLL_PAGE) {
            throw new IllegalArgumentException("Invalid scrollStrategy");
        }
        mLayoutManager.setFocusScrollStrategy(scrollStrategy);
        requestLayout();
    }

    /**
     * Returns the strategy used to scroll in response to item focus changing.
     * <ul>
     * <li>{@link #FOCUS_SCROLL_ALIGNED} (default) </li>
     * <li>{@link #FOCUS_SCROLL_ITEM}</li>
     * <li>{@link #FOCUS_SCROLL_PAGE}</li>
     * </ul>
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public int getFocusScrollStrategy() {
        return mLayoutManager.getFocusScrollStrategy();
    }

    /**
     * Sets the method for focused item alignment in the view.
     *
     * @param windowAlignment {@link #WINDOW_ALIGN_BOTH_EDGE},
     *        {@link #WINDOW_ALIGN_LOW_EDGE}, {@link #WINDOW_ALIGN_HIGH_EDGE} or
     *        {@link #WINDOW_ALIGN_NO_EDGE}.
     */
    public void setWindowAlignment(int windowAlignment) {
        mLayoutManager.setWindowAlignment(windowAlignment);
        requestLayout();
    }

    /**
     * Returns the method for focused item alignment in the view.
     *
     * @return {@link #WINDOW_ALIGN_BOTH_EDGE}, {@link #WINDOW_ALIGN_LOW_EDGE},
     *         {@link #WINDOW_ALIGN_HIGH_EDGE} or {@link #WINDOW_ALIGN_NO_EDGE}.
     */
    public int getWindowAlignment() {
        return mLayoutManager.getWindowAlignment();
    }

    /**
     * Sets whether prefer key line over low edge when {@link #WINDOW_ALIGN_LOW_EDGE} is used.
     * When true, if there are very few items between low edge and key line, align items to key
     * line instead of align items to low edge.
     * Default value is false (aka prefer align to low edge).
     *
     * @param preferKeyLineOverLowEdge True to prefer key line over low edge, false otherwise.
     */
    public void setWindowAlignmentPreferKeyLineOverLowEdge(boolean preferKeyLineOverLowEdge) {
        mLayoutManager.mWindowAlignment.mainAxis()
                .setPreferKeylineOverLowEdge(preferKeyLineOverLowEdge);
        requestLayout();
    }


    /**
     * Returns whether prefer key line over high edge when {@link #WINDOW_ALIGN_HIGH_EDGE} is used.
     * When true, if there are very few items between high edge and key line, align items to key
     * line instead of align items to high edge.
     * Default value is true (aka prefer align to key line).
     *
     * @param preferKeyLineOverHighEdge True to prefer key line over high edge, false otherwise.
     */
    public void setWindowAlignmentPreferKeyLineOverHighEdge(boolean preferKeyLineOverHighEdge) {
        mLayoutManager.mWindowAlignment.mainAxis()
                .setPreferKeylineOverHighEdge(preferKeyLineOverHighEdge);
        requestLayout();
    }

    /**
     * Returns whether prefer key line over low edge when {@link #WINDOW_ALIGN_LOW_EDGE} is used.
     * When true, if there are very few items between low edge and key line, align items to key
     * line instead of align items to low edge.
     * Default value is false (aka prefer align to low edge).
     *
     * @return True to prefer key line over low edge, false otherwise.
     */
    public boolean isWindowAlignmentPreferKeyLineOverLowEdge() {
        return mLayoutManager.mWindowAlignment.mainAxis().isPreferKeylineOverLowEdge();
    }


    /**
     * Returns whether prefer key line over high edge when {@link #WINDOW_ALIGN_HIGH_EDGE} is used.
     * When true, if there are very few items between high edge and key line, align items to key
     * line instead of align items to high edge.
     * Default value is true (aka prefer align to key line).
     *
     * @return True to prefer key line over high edge, false otherwise.
     */
    public boolean isWindowAlignmentPreferKeyLineOverHighEdge() {
        return mLayoutManager.mWindowAlignment.mainAxis().isPreferKeylineOverHighEdge();
    }


    /**
     * Sets the offset in pixels for window alignment key line.
     *
     * @param offset The number of pixels to offset.  If the offset is positive,
     *        it is distance from low edge (see {@link #WINDOW_ALIGN_LOW_EDGE});
     *        if the offset is negative, the absolute value is distance from high
     *        edge (see {@link #WINDOW_ALIGN_HIGH_EDGE}).
     *        Default value is 0.
     */
    public void setWindowAlignmentOffset(int offset) {
        mLayoutManager.setWindowAlignmentOffset(offset);
        requestLayout();
    }

    /**
     * Returns the offset in pixels for window alignment key line.
     *
     * @return The number of pixels to offset.  If the offset is positive,
     *        it is distance from low edge (see {@link #WINDOW_ALIGN_LOW_EDGE});
     *        if the offset is negative, the absolute value is distance from high
     *        edge (see {@link #WINDOW_ALIGN_HIGH_EDGE}).
     *        Default value is 0.
     */
    public int getWindowAlignmentOffset() {
        return mLayoutManager.getWindowAlignmentOffset();
    }

    /**
     * Sets the offset percent for window alignment key line in addition to {@link
     * #getWindowAlignmentOffset()}.
     *
     * @param offsetPercent Percentage to offset. E.g., 40 means 40% of the
     *        width from low edge. Use
     *        {@link #WINDOW_ALIGN_OFFSET_PERCENT_DISABLED} to disable.
     *         Default value is 50.
     */
    public void setWindowAlignmentOffsetPercent(float offsetPercent) {
        mLayoutManager.setWindowAlignmentOffsetPercent(offsetPercent);
        requestLayout();
    }

    /**
     * Returns the offset percent for window alignment key line in addition to
     * {@link #getWindowAlignmentOffset()}.
     *
     * @return Percentage to offset. E.g., 40 means 40% of the width from the
     *         low edge, or {@link #WINDOW_ALIGN_OFFSET_PERCENT_DISABLED} if
     *         disabled. Default value is 50.
     */
    public float getWindowAlignmentOffsetPercent() {
        return mLayoutManager.getWindowAlignmentOffsetPercent();
    }

    /**
     * Sets number of pixels to the end of low edge. Supports right to left layout direction.
     * Item alignment settings are ignored for the child if {@link ItemAlignmentFacet}
     * is provided by {@link RecyclerView.ViewHolder} or {@link FacetProviderAdapter}.
     *
     * @param offset In left to right or vertical case, it's the offset added to left/top edge.
     *               In right to left case, it's the offset subtracted from right edge.
     */
    public void setItemAlignmentOffset(int offset) {
        mLayoutManager.setItemAlignmentOffset(offset);
        requestLayout();
    }

    /**
     * Returns number of pixels to the end of low edge. Supports right to left layout direction. In
     * left to right or vertical case, it's the offset added to left/top edge. In right to left
     * case, it's the offset subtracted from right edge.
     * Item alignment settings are ignored for the child if {@link ItemAlignmentFacet}
     * is provided by {@link RecyclerView.ViewHolder} or {@link FacetProviderAdapter}.
     *
     * @return The number of pixels to the end of low edge.
     */
    public int getItemAlignmentOffset() {
        return mLayoutManager.getItemAlignmentOffset();
    }

    /**
     * Sets whether applies padding to item alignment when {@link #getItemAlignmentOffsetPercent()}
     * is 0 or 100.
     * <p>When true:
     * Applies start/top padding if {@link #getItemAlignmentOffsetPercent()} is 0.
     * Applies end/bottom padding if {@link #getItemAlignmentOffsetPercent()} is 100.
     * Does not apply padding if {@link #getItemAlignmentOffsetPercent()} is neither 0 nor 100.
     * </p>
     * <p>When false: does not apply padding</p>
     */
    public void setItemAlignmentOffsetWithPadding(boolean withPadding) {
        mLayoutManager.setItemAlignmentOffsetWithPadding(withPadding);
        requestLayout();
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
    public boolean isItemAlignmentOffsetWithPadding() {
        return mLayoutManager.isItemAlignmentOffsetWithPadding();
    }

    /**
     * Sets the offset percent for item alignment in addition to {@link
     * #getItemAlignmentOffset()}.
     * Item alignment settings are ignored for the child if {@link ItemAlignmentFacet}
     * is provided by {@link RecyclerView.ViewHolder} or {@link FacetProviderAdapter}.
     *
     * @param offsetPercent Percentage to offset. E.g., 40 means 40% of the
     *        width from the low edge. Use
     *        {@link #ITEM_ALIGN_OFFSET_PERCENT_DISABLED} to disable.
     */
    public void setItemAlignmentOffsetPercent(float offsetPercent) {
        mLayoutManager.setItemAlignmentOffsetPercent(offsetPercent);
        requestLayout();
    }

    /**
     * Returns the offset percent for item alignment in addition to {@link
     * #getItemAlignmentOffset()}.
     *
     * @return Percentage to offset. E.g., 40 means 40% of the width from the
     *         low edge, or {@link #ITEM_ALIGN_OFFSET_PERCENT_DISABLED} if
     *         disabled. Default value is 50.
     */
    public float getItemAlignmentOffsetPercent() {
        return mLayoutManager.getItemAlignmentOffsetPercent();
    }

    /**
     * Sets the id of the view to align with. Use {@link android.view.View#NO_ID} (default)
     * for the root {@link RecyclerView.ViewHolder#itemView}.
     * Item alignment settings on BaseGridView are if {@link ItemAlignmentFacet}
     * is provided by {@link RecyclerView.ViewHolder} or {@link FacetProviderAdapter}.
     */
    public void setItemAlignmentViewId(int viewId) {
        mLayoutManager.setItemAlignmentViewId(viewId);
    }

    /**
     * Returns the id of the view to align with, or {@link android.view.View#NO_ID} for the root
     * {@link RecyclerView.ViewHolder#itemView}.
     * @return The id of the view to align with, or {@link android.view.View#NO_ID} for the root
     * {@link RecyclerView.ViewHolder#itemView}.
     */
    public int getItemAlignmentViewId() {
        return mLayoutManager.getItemAlignmentViewId();
    }

    /**
     * Sets the spacing in pixels between two child items.
     * @deprecated use {@link #setItemSpacing(int)}
     */
    @Deprecated
    public void setItemMargin(int margin) {
        setItemSpacing(margin);
    }

    /**
     * Sets the vertical and horizontal spacing in pixels between two child items.
     * @param spacing Vertical and horizontal spacing in pixels between two child items.
     */
    public void setItemSpacing(int spacing) {
        mLayoutManager.setItemSpacing(spacing);
        requestLayout();
    }

    /**
     * Sets the spacing in pixels between two child items vertically.
     * @deprecated Use {@link #setVerticalSpacing(int)}
     */
    @Deprecated
    public void setVerticalMargin(int margin) {
        setVerticalSpacing(margin);
    }

    /**
     * Returns the spacing in pixels between two child items vertically.
     * @deprecated Use {@link #getVerticalSpacing()}
     */
    @Deprecated
    public int getVerticalMargin() {
        return mLayoutManager.getVerticalSpacing();
    }

    /**
     * Sets the spacing in pixels between two child items horizontally.
     * @deprecated Use {@link #setHorizontalSpacing(int)}
     */
    @Deprecated
    public void setHorizontalMargin(int margin) {
        setHorizontalSpacing(margin);
    }

    /**
     * Returns the spacing in pixels between two child items horizontally.
     * @deprecated Use {@link #getHorizontalSpacing()}
     */
    @Deprecated
    public int getHorizontalMargin() {
        return mLayoutManager.getHorizontalSpacing();
    }

    /**
     * Sets the vertical spacing in pixels between two child items.
     * @param spacing Vertical spacing between two child items.
     */
    public void setVerticalSpacing(int spacing) {
        mLayoutManager.setVerticalSpacing(spacing);
        requestLayout();
    }

    /**
     * Returns the vertical spacing in pixels between two child items.
     * @return The vertical spacing in pixels between two child items.
     */
    public int getVerticalSpacing() {
        return mLayoutManager.getVerticalSpacing();
    }

    /**
     * Sets the horizontal spacing in pixels between two child items.
     * @param spacing Horizontal spacing in pixels between two child items.
     */
    public void setHorizontalSpacing(int spacing) {
        mLayoutManager.setHorizontalSpacing(spacing);
        requestLayout();
    }

    /**
     * Returns the horizontal spacing in pixels between two child items.
     * @return The Horizontal spacing in pixels between two child items.
     */
    public int getHorizontalSpacing() {
        return mLayoutManager.getHorizontalSpacing();
    }

    /**
     * Registers a callback to be invoked when an item in BaseGridView has
     * been laid out.
     *
     * @param listener The listener to be invoked.
     */
    public void setOnChildLaidOutListener(OnChildLaidOutListener listener) {
        mLayoutManager.setOnChildLaidOutListener(listener);
    }

    /**
     * Registers a callback to be invoked when an item in BaseGridView has
     * been selected.  Note that the listener may be invoked when there is a
     * layout pending on the view, affording the listener an opportunity to
     * adjust the upcoming layout based on the selection state.
     *
     * @param listener The listener to be invoked.
     */
    public void setOnChildSelectedListener(OnChildSelectedListener listener) {
        mLayoutManager.setOnChildSelectedListener(listener);
    }

    /**
     * Registers a callback to be invoked when an item in BaseGridView has
     * been selected.  Note that the listener may be invoked when there is a
     * layout pending on the view, affording the listener an opportunity to
     * adjust the upcoming layout based on the selection state.
     * This method will clear all existing listeners added by
     * {@link #addOnChildViewHolderSelectedListener}.
     *
     * @param listener The listener to be invoked.
     */
    public void setOnChildViewHolderSelectedListener(OnChildViewHolderSelectedListener listener) {
        mLayoutManager.setOnChildViewHolderSelectedListener(listener);
    }

    /**
     * Registers a callback to be invoked when an item in BaseGridView has
     * been selected.  Note that the listener may be invoked when there is a
     * layout pending on the view, affording the listener an opportunity to
     * adjust the upcoming layout based on the selection state.
     *
     * @param listener The listener to be invoked.
     */
    public void addOnChildViewHolderSelectedListener(OnChildViewHolderSelectedListener listener) {
        mLayoutManager.addOnChildViewHolderSelectedListener(listener);
    }

    /**
     * Remove the callback invoked when an item in BaseGridView has been selected.
     *
     * @param listener The listener to be removed.
     */
    public void removeOnChildViewHolderSelectedListener(OnChildViewHolderSelectedListener listener)
            {
        mLayoutManager.removeOnChildViewHolderSelectedListener(listener);
    }

    /**
     * Changes the selected item immediately without animation.
     */
    public void setSelectedPosition(int position) {
        mLayoutManager.setSelection(position, 0);
    }

    /**
     * Changes the selected item and/or subposition immediately without animation.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setSelectedPositionWithSub(int position, int subposition) {
        mLayoutManager.setSelectionWithSub(position, subposition, 0);
    }

    /**
     * Changes the selected item immediately without animation, scrollExtra is
     * applied in primary scroll direction.  The scrollExtra will be kept until
     * another {@link #setSelectedPosition} or {@link #setSelectedPositionSmooth} call.
     */
    public void setSelectedPosition(int position, int scrollExtra) {
        mLayoutManager.setSelection(position, scrollExtra);
    }

    /**
     * Changes the selected item and/or subposition immediately without animation, scrollExtra is
     * applied in primary scroll direction.  The scrollExtra will be kept until
     * another {@link #setSelectedPosition} or {@link #setSelectedPositionSmooth} call.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setSelectedPositionWithSub(int position, int subposition, int scrollExtra) {
        mLayoutManager.setSelectionWithSub(position, subposition, scrollExtra);
    }

    /**
     * Changes the selected item and run an animation to scroll to the target
     * position.
     * @param position Adapter position of the item to select.
     */
    public void setSelectedPositionSmooth(int position) {
        mLayoutManager.setSelectionSmooth(position);
    }

    /**
     * Changes the selected item and/or subposition, runs an animation to scroll to the target
     * position.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setSelectedPositionSmoothWithSub(int position, int subposition) {
        mLayoutManager.setSelectionSmoothWithSub(position, subposition);
    }

    /**
     * Perform a task on ViewHolder at given position after smooth scrolling to it.
     * @param position Position of item in adapter.
     * @param task Task to executed on the ViewHolder at a given position.
     */
    public void setSelectedPositionSmooth(final int position, final ViewHolderTask task) {
        if (task != null) {
            RecyclerView.ViewHolder vh = findViewHolderForPosition(position);
            if (vh == null || hasPendingAdapterUpdates()) {
                addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
                    @Override
                    public void onChildViewHolderSelected(RecyclerView parent,
                            RecyclerView.ViewHolder child, int selectedPosition, int subposition) {
                        if (selectedPosition == position) {
                            removeOnChildViewHolderSelectedListener(this);
                            task.run(child);
                        }
                    }
                });
            } else {
                task.run(vh);
            }
        }
        setSelectedPositionSmooth(position);
    }

    /**
     * Perform a task on ViewHolder at given position after scroll to it.
     * @param position Position of item in adapter.
     * @param task Task to executed on the ViewHolder at a given position.
     */
    public void setSelectedPosition(final int position, final ViewHolderTask task) {
        if (task != null) {
            RecyclerView.ViewHolder vh = findViewHolderForPosition(position);
            if (vh == null || hasPendingAdapterUpdates()) {
                addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
                    @Override
                    public void onChildViewHolderSelectedAndPositioned(RecyclerView parent,
                            RecyclerView.ViewHolder child, int selectedPosition, int subposition) {
                        if (selectedPosition == position) {
                            removeOnChildViewHolderSelectedListener(this);
                            task.run(child);
                        }
                    }
                });
            } else {
                task.run(vh);
            }
        }
        setSelectedPosition(position);
    }

    /**
     * Returns the adapter position of selected item.
     * @return The adapter position of selected item.
     */
    public int getSelectedPosition() {
        return mLayoutManager.getSelection();
    }

    /**
     * Returns the sub selected item position started from zero.  An item can have
     * multiple {@link ItemAlignmentFacet}s provided by {@link RecyclerView.ViewHolder}
     * or {@link FacetProviderAdapter}.  Zero is returned when no {@link ItemAlignmentFacet}
     * is defined.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public int getSelectedSubPosition() {
        return mLayoutManager.getSubSelection();
    }

    /**
     * Sets whether ItemAnimator should run when a child changes size or when adding
     * or removing a child.
     * @param animateChildLayout True to enable ItemAnimator, false to disable.
     */
    public void setAnimateChildLayout(boolean animateChildLayout) {
        if (mAnimateChildLayout != animateChildLayout) {
            mAnimateChildLayout = animateChildLayout;
            if (!mAnimateChildLayout) {
                mSavedItemAnimator = getItemAnimator();
                super.setItemAnimator(null);
            } else {
                super.setItemAnimator(mSavedItemAnimator);
            }
        }
    }

    /**
     * Returns true if an animation will run when a child changes size or when
     * adding or removing a child.
     * @return True if ItemAnimator is enabled, false otherwise.
     */
    public boolean isChildLayoutAnimated() {
        return mAnimateChildLayout;
    }

    /**
     * Sets the gravity used for child view positioning. Defaults to
     * GRAVITY_TOP|GRAVITY_START.
     *
     * @param gravity See {@link android.view.Gravity}
     */
    public void setGravity(int gravity) {
        mLayoutManager.setGravity(gravity);
        requestLayout();
    }

    @Override
    public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        return mLayoutManager.gridOnRequestFocusInDescendants(this, direction,
                previouslyFocusedRect);
    }

    /**
     * Returns the x/y offsets to final position from current position if the view
     * is selected.
     *
     * @param view The view to get offsets.
     * @param offsets offsets[0] holds offset of X, offsets[1] holds offset of Y.
     */
    public void getViewSelectedOffsets(View view, int[] offsets) {
        mLayoutManager.getViewSelectedOffsets(view, offsets);
    }

    @Override
    public int getChildDrawingOrder(int childCount, int i) {
        return mLayoutManager.getChildDrawingOrder(this, childCount, i);
    }

    final boolean isChildrenDrawingOrderEnabledInternal() {
        return isChildrenDrawingOrderEnabled();
    }

    @Override
    public View focusSearch(int direction) {
        if (isFocused()) {
            // focusSearch(int) is called when GridView itself is focused.
            // Calling focusSearch(view, int) to get next sibling of current selected child.
            View view = mLayoutManager.findViewByPosition(mLayoutManager.getSelection());
            if (view != null) {
                return focusSearch(view, direction);
            }
        }
        // otherwise, go to mParent to perform focusSearch
        return super.focusSearch(direction);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        mLayoutManager.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    /**
     * Disables or enables focus search.
     * @param disabled True to disable focus search, false to enable.
     */
    public final void setFocusSearchDisabled(boolean disabled) {
        // LayoutManager may detachView and attachView in fastRelayout, it causes RowsFragment
        // re-gain focus after a BACK key pressed, so block children focus during transition.
        setDescendantFocusability(disabled ? FOCUS_BLOCK_DESCENDANTS: FOCUS_AFTER_DESCENDANTS);
        mLayoutManager.setFocusSearchDisabled(disabled);
    }

    /**
     * Returns true if focus search is disabled.
     * @return True if focus search is disabled.
     */
    public final boolean isFocusSearchDisabled() {
        return mLayoutManager.isFocusSearchDisabled();
    }

    /**
     * Enables or disables layout.  All children will be removed when layout is
     * disabled.
     * @param layoutEnabled True to enable layout, false otherwise.
     */
    public void setLayoutEnabled(boolean layoutEnabled) {
        mLayoutManager.setLayoutEnabled(layoutEnabled);
    }

    /**
     * Changes and overrides children's visibility.
     * @param visibility See {@link View#getVisibility()}.
     */
    public void setChildrenVisibility(int visibility) {
        mLayoutManager.setChildrenVisibility(visibility);
    }

    /**
     * Enables or disables pruning of children.  Disable is useful during transition.
     * @param pruneChild True to prune children out side visible area, false to enable.
     */
    public void setPruneChild(boolean pruneChild) {
        mLayoutManager.setPruneChild(pruneChild);
    }

    /**
     * Enables or disables scrolling.  Disable is useful during transition.
     * @param scrollEnabled True to enable scroll, false to disable.
     */
    public void setScrollEnabled(boolean scrollEnabled) {
        mLayoutManager.setScrollEnabled(scrollEnabled);
    }

    /**
     * Returns true if scrolling is enabled, false otherwise.
     * @return True if scrolling is enabled, false otherwise.
     */
    public boolean isScrollEnabled() {
        return mLayoutManager.isScrollEnabled();
    }

    /**
     * Returns true if the view at the given position has a same row sibling
     * in front of it.  This will return true if first item view is not created.
     *
     * @param position Position in adapter.
     * @return True if the view at the given position has a same row sibling in front of it.
     */
    public boolean hasPreviousViewInSameRow(int position) {
        return mLayoutManager.hasPreviousViewInSameRow(position);
    }

    /**
     * Enables or disables the default "focus draw at last" order rule. Default is enabled.
     * @param enabled True to draw the selected child at last, false otherwise.
     */
    public void setFocusDrawingOrderEnabled(boolean enabled) {
        super.setChildrenDrawingOrderEnabled(enabled);
    }

    /**
     * Returns true if draws selected child at last, false otherwise. Default is enabled.
     * @return True if draws selected child at last, false otherwise.
     */
    public boolean isFocusDrawingOrderEnabled() {
        return super.isChildrenDrawingOrderEnabled();
    }

    /**
     * Sets the touch intercept listener.
     * @param listener The touch intercept listener.
     */
    public void setOnTouchInterceptListener(OnTouchInterceptListener listener) {
        mOnTouchInterceptListener = listener;
    }

    /**
     * Sets the generic motion intercept listener.
     * @param listener The motion intercept listener.
     */
    public void setOnMotionInterceptListener(OnMotionInterceptListener listener) {
        mOnMotionInterceptListener = listener;
    }

    /**
     * Sets the key intercept listener.
     * @param listener The key intercept listener.
     */
    public void setOnKeyInterceptListener(OnKeyInterceptListener listener) {
        mOnKeyInterceptListener = listener;
    }

    /**
     * Sets the unhandled key listener.
     * @param listener The unhandled key intercept listener.
     */
    public void setOnUnhandledKeyListener(OnUnhandledKeyListener listener) {
        mOnUnhandledKeyListener = listener;
    }

    /**
     * Returns the unhandled key listener.
     * @return The unhandled key listener.
     */
    public OnUnhandledKeyListener getOnUnhandledKeyListener() {
        return mOnUnhandledKeyListener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mOnKeyInterceptListener != null && mOnKeyInterceptListener.onInterceptKeyEvent(event)) {
            return true;
        }
        if (super.dispatchKeyEvent(event)) {
            return true;
        }
        return mOnUnhandledKeyListener != null && mOnUnhandledKeyListener.onUnhandledKey(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mOnTouchInterceptListener != null) {
            if (mOnTouchInterceptListener.onInterceptTouchEvent(event)) {
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected boolean dispatchGenericFocusedEvent(MotionEvent event) {
        if (mOnMotionInterceptListener != null) {
            if (mOnMotionInterceptListener.onInterceptMotionEvent(event)) {
                return true;
            }
        }
        return super.dispatchGenericFocusedEvent(event);
    }

    /**
     * Returns the policy for saving children.
     *
     * @return policy, one of {@link #SAVE_NO_CHILD}
     * {@link #SAVE_ON_SCREEN_CHILD} {@link #SAVE_LIMITED_CHILD} {@link #SAVE_ALL_CHILD}.
     */
    public final int getSaveChildrenPolicy() {
        return mLayoutManager.mChildrenStates.getSavePolicy();
    }

    /**
     * Returns the limit used when when {@link #getSaveChildrenPolicy()} is
     *         {@link #SAVE_LIMITED_CHILD}
     */
    public final int getSaveChildrenLimitNumber() {
        return mLayoutManager.mChildrenStates.getLimitNumber();
    }

    /**
     * Sets the policy for saving children.
     * @param savePolicy One of {@link #SAVE_NO_CHILD} {@link #SAVE_ON_SCREEN_CHILD}
     * {@link #SAVE_LIMITED_CHILD} {@link #SAVE_ALL_CHILD}.
     */
    public final void setSaveChildrenPolicy(int savePolicy) {
        mLayoutManager.mChildrenStates.setSavePolicy(savePolicy);
    }

    /**
     * Sets the limit number when {@link #getSaveChildrenPolicy()} is {@link #SAVE_LIMITED_CHILD}.
     */
    public final void setSaveChildrenLimitNumber(int limitNumber) {
        mLayoutManager.mChildrenStates.setLimitNumber(limitNumber);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mHasOverlappingRendering;
    }

    public void setHasOverlappingRendering(boolean hasOverlapping) {
        mHasOverlappingRendering = hasOverlapping;
    }

    /**
     * Notify layout manager that layout directionality has been updated
     */
    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        mLayoutManager.onRtlPropertiesChanged(layoutDirection);
    }

    @Override
    public void setRecyclerListener(RecyclerView.RecyclerListener listener) {
        mChainedRecyclerListener = listener;
    }

    /**
     * Sets pixels of extra space for layout child in invisible area.
     *
     * @param extraLayoutSpace  Pixels of extra space for layout invisible child.
     *                          Must be bigger or equals to 0.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setExtraLayoutSpace(int extraLayoutSpace) {
        mLayoutManager.setExtraLayoutSpace(extraLayoutSpace);
    }

    /**
     * Returns pixels of extra space for layout child in invisible area.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public int getExtraLayoutSpace() {
        return mLayoutManager.getExtraLayoutSpace();
    }

    /**
     * Temporarily slide out child views to bottom (for VerticalGridView) or end
     * (for HorizontalGridView). Layout and scrolling will be suppressed until
     * {@link #animateIn()} is called.
     */
    public void animateOut() {
        mLayoutManager.slideOut();
    }

    /**
     * Undo animateOut() and slide in child views.
     */
    public void animateIn() {
        mLayoutManager.slideIn();
    }

    @Override
    public void scrollToPosition(int position) {
        // dont abort the animateOut() animation, just record the position
        if (mLayoutManager.isSlidingChildViews()) {
            mLayoutManager.setSelectionWithSub(position, 0, 0);
            return;
        }
        super.scrollToPosition(position);
    }

    @Override
    public void smoothScrollToPosition(int position) {
        // dont abort the animateOut() animation, just record the position
        if (mLayoutManager.isSlidingChildViews()) {
            mLayoutManager.setSelectionWithSub(position, 0, 0);
            return;
        }
        super.smoothScrollToPosition(position);
    }

    /**
     * Sets the number of items to prefetch in
     * {@link RecyclerView.LayoutManager#collectInitialPrefetchPositions(int, RecyclerView.LayoutManager.LayoutPrefetchRegistry)},
     * which defines how many inner items should be prefetched when this GridView is nested inside
     * another RecyclerView.
     *
     * <p>Set this value to the number of items this inner GridView will display when it is
     * first scrolled into the viewport. RecyclerView will attempt to prefetch that number of items
     * so they are ready, avoiding jank as the inner GridView is scrolled into the viewport.</p>
     *
     * <p>For example, take a VerticalGridView of scrolling HorizontalGridViews. The rows always
     * have 6 items visible in them (or 7 if not aligned). Passing <code>6</code> to this method
     * for each inner GridView will enable RecyclerView's prefetching feature to do create/bind work
     * for 6 views within a row early, before it is scrolled on screen, instead of just the default
     * 4.</p>
     *
     * <p>Calling this method does nothing unless the LayoutManager is in a RecyclerView
     * nested in another RecyclerView.</p>
     *
     * <p class="note"><strong>Note:</strong> Setting this value to be larger than the number of
     * views that will be visible in this view can incur unnecessary bind work, and an increase to
     * the number of Views created and in active use.</p>
     *
     * @param itemCount Number of items to prefetch
     *
     * @see #getInitialPrefetchItemCount()
     * @see RecyclerView.LayoutManager#isItemPrefetchEnabled()
     * @see RecyclerView.LayoutManager#collectInitialPrefetchPositions(int, RecyclerView.LayoutManager.LayoutPrefetchRegistry)
     */
    public void setInitialPrefetchItemCount(int itemCount) {
        mInitialPrefetchItemCount = itemCount;
    }

    /**
     * Gets the number of items to prefetch in
     * {@link RecyclerView.LayoutManager#collectInitialPrefetchPositions(int, RecyclerView.LayoutManager.LayoutPrefetchRegistry)},
     * which defines how many inner items should be prefetched when this GridView is nested inside
     * another RecyclerView.
     *
     * @see RecyclerView.LayoutManager#isItemPrefetchEnabled()
     * @see #setInitialPrefetchItemCount(int)
     * @see RecyclerView.LayoutManager#collectInitialPrefetchPositions(int, RecyclerView.LayoutManager.LayoutPrefetchRegistry)
     *
     * @return number of items to prefetch.
     */
    public int getInitialPrefetchItemCount() {
        return mInitialPrefetchItemCount;
    }
}
