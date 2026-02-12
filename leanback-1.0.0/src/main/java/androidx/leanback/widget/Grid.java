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

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.CircularIntArray;
import androidx.recyclerview.widget.RecyclerView;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * A grid is representation of single or multiple rows layout data structure and algorithm.
 * Grid is the base class for single row, non-staggered grid and staggered grid.
 * <p>
 * To use the Grid, user must implement a Provider to create or remove visible item.
 * Grid maintains a list of visible items.  Visible items are created when
 * user calls appendVisibleItems() or prependVisibleItems() with certain limitation
 * (e.g. a max edge that append up to).  Visible items are deleted when user calls
 * removeInvisibleItemsAtEnd() or removeInvisibleItemsAtFront().  Grid's algorithm
 * uses size of visible item returned from Provider.createItem() to decide which row
 * to add a new visible item and may cache the algorithm results.   User must call
 * invalidateItemsAfter() when it detects item size changed to ask Grid to remove cached
 * results.
 */
abstract class Grid {

    /**
     * A constant representing a default starting index, indicating that the
     * developer did not provide a start index.
     */
    public static final int START_DEFAULT = -1;

    Object[] mTmpItem = new Object[1];

    /**
     * When user uses Grid,  he should provide count of items and
     * the method to create item and remove item.
     */
    public static interface Provider {

        /**
         * Return how many items (are in the adapter).
         */
        int getCount();

        /**
         * @return Min index to prepend, usually it's 0; but in the preLayout case,
         * when grid was showing 5,6,7.  Removing 3,4,5 will make the layoutPosition to
         * be 3(deleted),4,5 in prelayout pass; Grid's index is still 5,6,7 in prelayout.
         * When we prepend in prelayout, we can call createItem(4), createItem(3), createItem(2),
         * the minimal index is 2, which is also the delta to mapping to layoutPosition in
         * prelayout pass.
         */
        int getMinIndex();

        /**
         * Create visible item and where the provider should measure it.
         * The call is always followed by addItem().
         * @param index     0-based index of the item in provider
         * @param append  True if new item is after last visible item, false if new item is
         *                before first visible item.
         * @param item    item[0] returns created item that will be passed in addItem() call.
         * @param disappearingItem The item is a disappearing item added by
         *                         {@link Grid#fillDisappearingItems(int[], int, SparseIntArray)}.
         *
         * @return length of the item.
         */
        int createItem(int index, boolean append, Object[] item, boolean disappearingItem);

        /**
         * add item to given row and given edge.  The call is always after createItem().
         * @param item      The object returned by createItem()
         * @param index     0-based index of the item in provider
         * @param length    The size of the object
         * @param rowIndex  Row index to put the item
         * @param edge      min_edge if not reversed or max_edge if reversed.
         */
        void addItem(Object item, int index, int length, int rowIndex, int edge);

        /**
         * Remove visible item at index.
         * @param index     0-based index of the item in provider
         */
        void removeItem(int index);

        /**
         * Get edge of an existing visible item. edge will be the min_edge
         * if not reversed or the max_edge if reversed.
         * @param index     0-based index of the item in provider
         */
        int getEdge(int index);

        /**
         * Get size of an existing visible item.
         * @param index     0-based index of the item in provider
         */
        int getSize(int index);
    }

    /**
     * Cached representation of an item in Grid.  May be subclassed.
     */
    public static class Location {
        /**
         * The index of the row for this Location.
         */
        public int row;

        public Location(int row) {
            this.row = row;
        }
    }

    protected Provider mProvider;
    protected boolean mReversedFlow;
    protected int mSpacing;
    protected int mNumRows;
    protected int mFirstVisibleIndex = -1;
    protected int mLastVisibleIndex = -1;

    protected CircularIntArray[] mTmpItemPositionsInRows;

    // the first index that grid will layout
    protected int mStartIndex = START_DEFAULT;

    /**
     * Creates a single or multiple rows (can be staggered or not staggered) grid
     */
    public static Grid createGrid(int rows) {
        Grid grid;
        if (rows == 1) {
            grid = new SingleRow();
        } else {
            // TODO support non staggered multiple rows grid
            grid = new StaggeredGridDefault();
            grid.setNumRows(rows);
        }
        return grid;
    }

    /**
     * Sets the space between items in a row
     */
    public final void setSpacing(int spacing) {
        mSpacing = spacing;
    }

    /**
     * Sets if reversed flow (rtl)
     */
    public final void setReversedFlow(boolean reversedFlow) {
        mReversedFlow = reversedFlow;
    }

    /**
     * Returns true if reversed flow (rtl)
     */
    public boolean isReversedFlow() {
        return mReversedFlow;
    }

    /**
     * Sets the {@link Provider} for this grid.
     *
     * @param provider The provider for this grid.
     */
    public void setProvider(Provider provider) {
        mProvider = provider;
    }

    /**
     * Sets the first item index to create when there are no items.
     *
     * @param startIndex the index of the first item
     */
    public void setStart(int startIndex) {
        mStartIndex = startIndex;
    }

    /**
     * Returns the number of rows in the grid.
     */
    public int getNumRows() {
        return mNumRows;
    }

    /**
     * Sets number of rows to fill into. For views that represent a
     * horizontal list, this will be the rows of the view. For views that
     * represent a vertical list, this will be the columns.
     *
     * @param numRows numberOfRows
     */
    void setNumRows(int numRows) {
        if (numRows <= 0) {
            throw new IllegalArgumentException();
        }
        if (mNumRows == numRows) {
            return;
        }
        mNumRows = numRows;
        mTmpItemPositionsInRows = new CircularIntArray[mNumRows];
        for (int i = 0; i < mNumRows; i++) {
            mTmpItemPositionsInRows[i] = new CircularIntArray();
        }
    }

    /**
     * Returns index of first visible item in the staggered grid.  Returns negative value
     * if no visible item.
     */
    public final int getFirstVisibleIndex() {
        return mFirstVisibleIndex;
    }

    /**
     * Returns index of last visible item in the staggered grid.  Returns negative value
     * if no visible item.
     */
    public final int getLastVisibleIndex() {
        return mLastVisibleIndex;
    }

    /**
     * Reset visible indices and keep cache (if exists)
     */
    public void resetVisibleIndex() {
        mFirstVisibleIndex = mLastVisibleIndex = -1;
    }

    /**
     * Invalidate items after or equal to index. This will remove visible items
     * after that and invalidate cache of layout results after that. Note that it's client's
     * responsibility to perform removing child action, {@link Provider#removeItem(int)} will not
     * be called because the index might be invalidated.
     */
    public void invalidateItemsAfter(int index) {
        if (index < 0) {
            return;
        }
        if (mLastVisibleIndex < 0) {
            return;
        }
        if (mLastVisibleIndex >= index) {
            mLastVisibleIndex = index - 1;
        }
        resetVisibleIndexIfEmpty();
        if (getFirstVisibleIndex() < 0) {
            setStart(index);
        }
    }

    /**
     * Gets the row index of item at given index.
     */
    public final int getRowIndex(int index) {
        Location location = getLocation(index);
        if (location == null) {
            return -1;
        }
        return location.row;
    }

    /**
     * Gets {@link Location} of item.  The return object is read only and temporarily.
     */
    public abstract Location getLocation(int index);

    /**
     * Finds the largest or smallest row min edge of visible items,
     * the row index is returned in indices[0], the item index is returned in indices[1].
     */
    public final int findRowMin(boolean findLarge, @Nullable int[] indices) {
        return findRowMin(findLarge, mReversedFlow ? mLastVisibleIndex : mFirstVisibleIndex,
                indices);
    }

    /**
     * Finds the largest or smallest row min edge of visible items, starts searching from
     * indexLimit, the row index is returned in indices[0], the item index is returned in indices[1].
     */
    protected abstract int findRowMin(boolean findLarge, int indexLimit, int[] rowIndex);

    /**
     * Finds the largest or smallest row max edge of visible items, the row index is returned in
     * indices[0], the item index is returned in indices[1].
     */
    public final int findRowMax(boolean findLarge, @Nullable int[] indices) {
        return findRowMax(findLarge, mReversedFlow ? mFirstVisibleIndex : mLastVisibleIndex,
                indices);
    }

    /**
     * Find largest or smallest row max edge of visible items, starts searching from indexLimit,
     * the row index is returned in indices[0], the item index is returned in indices[1].
     */
    protected abstract int findRowMax(boolean findLarge, int indexLimit, int[] indices);

    /**
     * Returns true if appending item has reached "toLimit"
     */
    protected final boolean checkAppendOverLimit(int toLimit) {
        if (mLastVisibleIndex < 0) {
            return false;
        }
        return mReversedFlow ? findRowMin(true, null) <= toLimit + mSpacing :
                    findRowMax(false, null) >= toLimit - mSpacing;
    }

    /**
     * Returns true if prepending item has reached "toLimit"
     */
    protected final boolean checkPrependOverLimit(int toLimit) {
        if (mLastVisibleIndex < 0) {
            return false;
        }
        return mReversedFlow ? findRowMax(false, null) >= toLimit - mSpacing :
                    findRowMin(true, null) <= toLimit + mSpacing;
    }

    /**
     * Return array of int array for all rows, each int array contains visible item positions
     * in pair on that row between startPos(included) and endPositions(included).
     * Returned value is read only, do not change it.
     * <p>
     * E.g. First row has 3,7,8, second row has 4,5,6.
     * getItemPositionsInRows(3, 8) returns { {3,3,7,8}, {4,6} }
     */
    public abstract CircularIntArray[] getItemPositionsInRows(int startPos, int endPos);

    /**
     * Return array of int array for all rows, each int array contains visible item positions
     * in pair on that row.
     * Returned value is read only, do not change it.
     * <p>
     * E.g. First row has 3,7,8, second row has 4,5,6  { {3,3,7,8}, {4,6} }
     */
    public final CircularIntArray[] getItemPositionsInRows() {
        return getItemPositionsInRows(getFirstVisibleIndex(), getLastVisibleIndex());
    }

    /**
     * Prepends items and stops after one column is filled.
     * (i.e. filled items from row 0 to row mNumRows - 1)
     * @return true if at least one item is filled.
     */
    public final boolean prependOneColumnVisibleItems() {
        return prependVisibleItems(mReversedFlow ? Integer.MIN_VALUE : Integer.MAX_VALUE, true);
    }

    /**
     * Prepends items until first item or reaches toLimit (min edge when not reversed or
     * max edge when reversed)
     */
    public final void prependVisibleItems(int toLimit) {
        prependVisibleItems(toLimit, false);
    }

    /**
     * Prepends items until first item or reaches toLimit (min edge when not reversed or
     * max edge when reversed).
     * @param oneColumnMode  true when fills one column and stops,  false
     * when checks if condition matches before filling first column.
     * @return true if at least one item is filled.
     */
    protected abstract boolean prependVisibleItems(int toLimit, boolean oneColumnMode);

    /**
     * Appends items and stops after one column is filled.
     * (i.e. filled items from row 0 to row mNumRows - 1)
     * @return true if at least one item is filled.
     */
    public boolean appendOneColumnVisibleItems() {
        return appendVisibleItems(mReversedFlow ? Integer.MAX_VALUE : Integer.MIN_VALUE, true);
    }

    /**
     * Append items until last item or reaches toLimit (max edge when not
     * reversed or min edge when reversed)
     */
    public final void appendVisibleItems(int toLimit) {
        appendVisibleItems(toLimit, false);
    }

    /**
     * Appends items until last or reaches toLimit (high edge when not
     * reversed or low edge when reversed).
     * @param oneColumnMode True when fills one column and stops,  false
     * when checks if condition matches before filling first column.
     * @return true if filled at least one item
     */
    protected abstract boolean appendVisibleItems(int toLimit, boolean oneColumnMode);

    /**
     * Removes invisible items from end until reaches item at aboveIndex or toLimit.
     * @param aboveIndex Don't remove items whose index is equals or smaller than aboveIndex
     * @param toLimit Don't remove items whose left edge is less than toLimit.
     */
    public void removeInvisibleItemsAtEnd(int aboveIndex, int toLimit) {
        while(mLastVisibleIndex >= mFirstVisibleIndex && mLastVisibleIndex > aboveIndex) {
            boolean offEnd = !mReversedFlow ? mProvider.getEdge(mLastVisibleIndex) >= toLimit
                    : mProvider.getEdge(mLastVisibleIndex) <= toLimit;
            if (offEnd) {
                mProvider.removeItem(mLastVisibleIndex);
                mLastVisibleIndex--;
            } else {
                break;
            }
        }
        resetVisibleIndexIfEmpty();
    }

    /**
     * Removes invisible items from front until reaches item at belowIndex or toLimit.
     * @param belowIndex Don't remove items whose index is equals or larger than belowIndex
     * @param toLimit Don't remove items whose right edge is equals or greater than toLimit.
     */
    public void removeInvisibleItemsAtFront(int belowIndex, int toLimit) {
        while(mLastVisibleIndex >= mFirstVisibleIndex && mFirstVisibleIndex < belowIndex) {
            final int size = mProvider.getSize(mFirstVisibleIndex);
            boolean offFront = !mReversedFlow
                    ? mProvider.getEdge(mFirstVisibleIndex) + size <= toLimit
                    : mProvider.getEdge(mFirstVisibleIndex) - size >= toLimit;
            if (offFront) {
                mProvider.removeItem(mFirstVisibleIndex);
                mFirstVisibleIndex++;
            } else {
                break;
            }
        }
        resetVisibleIndexIfEmpty();
    }

    private void resetVisibleIndexIfEmpty() {
        if (mLastVisibleIndex < mFirstVisibleIndex) {
            resetVisibleIndex();
        }
    }

    /**
     * Fill disappearing items, i.e. the items are moved out of window, we need give them final
     * location so recyclerview will run a slide out animation. The positions that was greater than
     * last visible index will be appended to end, the positions that was smaller than first visible
     * index will be prepend to beginning.
     * @param positions Sorted list of positions of disappearing items.
     * @param positionToRow Which row we want to put the disappearing item.
     */
    public void fillDisappearingItems(int[] positions, int positionsLength,
            SparseIntArray positionToRow) {
        final int lastPos = getLastVisibleIndex();
        final int resultSearchLast = lastPos >= 0
                ? Arrays.binarySearch(positions, 0, positionsLength, lastPos) : 0;
        if (resultSearchLast < 0) {
            // we shouldn't find lastPos in disappearing position list.
            int firstDisappearingIndex = -resultSearchLast - 1;
            int edge;
            if (mReversedFlow) {
                edge = mProvider.getEdge(lastPos) - mProvider.getSize(lastPos) - mSpacing;
            } else {
                edge = mProvider.getEdge(lastPos) + mProvider.getSize(lastPos) + mSpacing;
            }
            for (int i = firstDisappearingIndex; i < positionsLength; i++) {
                int disappearingIndex = positions[i];
                int disappearingRow = positionToRow.get(disappearingIndex);
                if (disappearingRow < 0) {
                    disappearingRow = 0; // if not found put in row 0
                }
                int size = mProvider.createItem(disappearingIndex, true, mTmpItem, true);
                mProvider.addItem(mTmpItem[0], disappearingIndex, size, disappearingRow, edge);
                if (mReversedFlow) {
                    edge = edge - size - mSpacing;
                } else {
                    edge = edge + size + mSpacing;
                }
            }
        }

        final int firstPos = getFirstVisibleIndex();
        final int resultSearchFirst = firstPos >= 0
                ? Arrays.binarySearch(positions, 0, positionsLength, firstPos) : 0;
        if (resultSearchFirst < 0) {
            // we shouldn't find firstPos in disappearing position list.
            int firstDisappearingIndex = -resultSearchFirst - 2;
            int edge;
            if (mReversedFlow) {
                edge = mProvider.getEdge(firstPos);
            } else {
                edge = mProvider.getEdge(firstPos);
            }
            for (int i = firstDisappearingIndex; i >= 0; i--) {
                int disappearingIndex = positions[i];
                int disappearingRow = positionToRow.get(disappearingIndex);
                if (disappearingRow < 0) {
                    disappearingRow = 0; // if not found put in row 0
                }
                int size = mProvider.createItem(disappearingIndex, false, mTmpItem, true);
                if (mReversedFlow) {
                    edge = edge + mSpacing + size;
                } else {
                    edge = edge - mSpacing - size;
                }
                mProvider.addItem(mTmpItem[0], disappearingIndex, size, disappearingRow, edge);
            }
        }
    }

    /**
     * Queries items adjacent to the viewport (in the direction of da) into the prefetch registry.
     */
    public void collectAdjacentPrefetchPositions(int fromLimit, int da,
            @NonNull RecyclerView.LayoutManager.LayoutPrefetchRegistry layoutPrefetchRegistry) {
    }

    public abstract void debugPrint(PrintWriter pw);
}
