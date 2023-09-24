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

import androidx.collection.CircularArray;
import androidx.collection.CircularIntArray;

import java.io.PrintWriter;

/**
 * A dynamic data structure that caches staggered grid position information
 * for each individual child. The algorithm ensures that each row will be kept
 * as balanced as possible when prepending and appending a child.
 *
 * <p>
 * You may keep view {@link StaggeredGrid.Location} inside StaggeredGrid as much
 * as possible since prepending and appending views is not symmetric: layout
 * going from 0 to N will likely produce a different result than layout going
 * from N to 0 for the staggered cases. If a user scrolls from 0 to N then
 * scrolls back to 0 and we don't keep history location information, edges of
 * the very beginning of rows will not be aligned. It is recommended to keep a
 * list of tens of thousands of {@link StaggeredGrid.Location}s which will be
 * big enough to remember a typical user's scroll history.
 *
 * <p>
 * This class is abstract and can be replaced with different implementations.
 */
abstract class StaggeredGrid extends Grid {

    /**
     * Cached representation of Staggered item.
     */
    public static class Location extends Grid.Location {
        /**
         * Offset to previous item location.
         * min_edge(index) - min_edge(index - 1) for non reversed case
         * max_edge(index) - max_edge(index - 1) for reversed case
         */
        public int offset;

        /**
         * size of the item.
         */
        public int size;

        public Location(int row, int offset, int size) {
            super(row);
            this.offset = offset;
            this.size = size;
        }
    }

    protected CircularArray<Location> mLocations = new CircularArray<Location>(64);

    // mFirstIndex <= mFirstVisibleIndex <= mLastVisibleIndex
    //    <= mFirstIndex + mLocations.size() - 1
    protected int mFirstIndex = -1;

    protected Object mPendingItem;
    protected int mPendingItemSize;

    /**
     * Returns index of first item (cached or visible) in the staggered grid.
     * Returns negative value if no item.
     */
    public final int getFirstIndex() {
        return mFirstIndex;
    }

    /**
     * Returns index of last item (cached or visible) in the staggered grid.
     * Returns negative value if no item.
     */
    public final int getLastIndex() {
        return mFirstIndex + mLocations.size() - 1;
    }

    /**
     * Returns the size of the saved {@link Location}s.
     */
    public final int getSize() {
        return mLocations.size();
    }

    @Override
    public final Location getLocation(int index) {
        final int indexInArray = index - mFirstIndex;
        if (indexInArray < 0 || indexInArray >= mLocations.size()) {
            return null;
        }
        return mLocations.get(indexInArray);
    }

    @Override
    public final void debugPrint(PrintWriter pw) {
        for (int i = 0, size = mLocations.size(); i < size; i++) {
            Location loc = mLocations.get(i);
            pw.print("<" + (mFirstIndex + i) + "," + loc.row + ">");
            pw.print(" ");
            pw.println();
        }
    }

    @Override
    protected final boolean prependVisibleItems(int toLimit, boolean oneColumnMode) {
        if (mProvider.getCount() == 0) {
            return false;
        }
        if (!oneColumnMode && checkPrependOverLimit(toLimit)) {
            return false;
        }
        try {
            if (prependVisbleItemsWithCache(toLimit, oneColumnMode)) {
                return true;
            }
            return prependVisibleItemsWithoutCache(toLimit, oneColumnMode);
        } finally {
            mTmpItem[0] = null;
            mPendingItem = null;
        }
    }

    /**
     * Prepends items using cached locations,  returning true if toLimit is reached.
     * This method should only be called by prependVisibleItems().
     */
    protected final boolean prependVisbleItemsWithCache(int toLimit, boolean oneColumnMode) {
        if (mLocations.size() == 0) {
            return false;
        }
        int itemIndex;
        int edge;
        int offset;
        if (mFirstVisibleIndex >= 0) {
            // prepend visible items from first visible index
            edge = mProvider.getEdge(mFirstVisibleIndex);
            offset = getLocation(mFirstVisibleIndex).offset;
            itemIndex = mFirstVisibleIndex - 1;
        } else {
            // prepend first visible item
            edge = Integer.MAX_VALUE;
            offset = 0;
            itemIndex = mStartIndex != START_DEFAULT ? mStartIndex : 0;
            if (itemIndex > getLastIndex() || itemIndex < getFirstIndex() - 1) {
                // if the item is not within or adjacent to cached items, clear cache.
                mLocations.clear();
                return false;
            } else if (itemIndex < getFirstIndex()) {
                // if the item is adjacent to first index, should prepend without cache.
                return false;
            }
        }
        int firstIndex = Math.max(mProvider.getMinIndex(), mFirstIndex);
        for (; itemIndex >= firstIndex; itemIndex--) {
            Location loc = getLocation(itemIndex);
            int rowIndex = loc.row;
            int size = mProvider.createItem(itemIndex, false, mTmpItem, false);
            if (size != loc.size) {
                mLocations.removeFromStart(itemIndex + 1 - mFirstIndex);
                mFirstIndex = mFirstVisibleIndex;
                // pending item will be added in prependVisibleItemsWithoutCache
                mPendingItem = mTmpItem[0];
                mPendingItemSize = size;
                return false;
            }
            mFirstVisibleIndex = itemIndex;
            if (mLastVisibleIndex < 0) {
                mLastVisibleIndex = itemIndex;
            }
            mProvider.addItem(mTmpItem[0], itemIndex, size, rowIndex, edge - offset);
            if (!oneColumnMode && checkPrependOverLimit(toLimit)) {
                return true;
            }
            edge = mProvider.getEdge(itemIndex);
            offset = loc.offset;
            // Check limit after filled a full column
            if (rowIndex == 0) {
                if (oneColumnMode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calculate offset of item after last cached item.
     */
    private int calculateOffsetAfterLastItem(int row) {
        // Find a cached item in same row, if not found, just use last item.
        int cachedIndex = getLastIndex();
        boolean foundCachedItemInSameRow = false;
        while (cachedIndex >= mFirstIndex) {
            Location loc = getLocation(cachedIndex);
            if (loc.row == row) {
                foundCachedItemInSameRow = true;
                break;
            }
            cachedIndex--;
        }
        if (!foundCachedItemInSameRow) {
            cachedIndex = getLastIndex();
        }
        // Assuming the cachedIndex is next to item on the same row, so the
        // sum of offset of [cachedIndex + 1, itemIndex] should be size of the
        // cached item plus spacing.
        int offset = isReversedFlow() ?  -getLocation(cachedIndex).size - mSpacing:
                getLocation(cachedIndex).size + mSpacing;
        for (int i = cachedIndex + 1; i <= getLastIndex(); i++) {
            offset -= getLocation(i).offset;
        }
        return offset;
    }


    /**
     * This implements the algorithm of layout staggered grid, the method should only be called by
     * prependVisibleItems().
     */
    protected abstract boolean prependVisibleItemsWithoutCache(int toLimit, boolean oneColumnMode);

    /**
     * Prepends one visible item with new Location info.  Only called from
     * prependVisibleItemsWithoutCache().
     */
    protected final int prependVisibleItemToRow(int itemIndex, int rowIndex, int edge) {
        int offset;
        if (mFirstVisibleIndex >= 0) {
            if (mFirstVisibleIndex != getFirstIndex() || mFirstVisibleIndex != itemIndex + 1) {
                // should never hit this when we prepend a new item with a new Location object.
                throw new IllegalStateException();
            }
        }
        Location oldFirstLoc = mFirstIndex >= 0 ? getLocation(mFirstIndex) : null;
        int oldFirstEdge = mProvider.getEdge(mFirstIndex);
        Location loc = new Location(rowIndex, 0, 0);
        mLocations.addFirst(loc);
        Object item;
        if (mPendingItem != null) {
            loc.size = mPendingItemSize;
            item = mPendingItem;
            mPendingItem = null;
        } else {
            loc.size = mProvider.createItem(itemIndex, false, mTmpItem, false);
            item = mTmpItem[0];
        }
        mFirstIndex = mFirstVisibleIndex = itemIndex;
        if (mLastVisibleIndex < 0) {
            mLastVisibleIndex = itemIndex;
        }
        int thisEdge = !mReversedFlow ? edge - loc.size : edge + loc.size;
        if (oldFirstLoc != null) {
            oldFirstLoc.offset = oldFirstEdge - thisEdge;
        }
        mProvider.addItem(item, itemIndex, loc.size, rowIndex, thisEdge);
        return loc.size;
    }

    @Override
    protected final boolean appendVisibleItems(int toLimit, boolean oneColumnMode) {
        if (mProvider.getCount() == 0) {
            return false;
        }
        if (!oneColumnMode && checkAppendOverLimit(toLimit)) {
            return false;
        }
        try {
            if (appendVisbleItemsWithCache(toLimit, oneColumnMode)) {
                return true;
            }
            return appendVisibleItemsWithoutCache(toLimit, oneColumnMode);
        } finally {
            mTmpItem[0] = null;
            mPendingItem = null;
        }
    }

    /**
     * Appends items using cached locations,  returning true if at least one item is appended
     * and (oneColumnMode is true or reach limit and aboveIndex).
     * This method should only be called by appendVisibleItems()
     */
    protected final boolean appendVisbleItemsWithCache(int toLimit, boolean oneColumnMode) {
        if (mLocations.size() == 0) {
            return false;
        }
        final int count = mProvider.getCount();
        int itemIndex;
        int edge;
        if (mLastVisibleIndex >= 0) {
            // append visible items from last visible index
            itemIndex = mLastVisibleIndex + 1;
            edge = mProvider.getEdge(mLastVisibleIndex);
        } else {
            // append first visible item
            edge = Integer.MAX_VALUE;
            itemIndex = mStartIndex != START_DEFAULT ? mStartIndex : 0;
            if (itemIndex > getLastIndex() + 1 || itemIndex < getFirstIndex()) {
                // if the item is not within or adjacent to cached items, clear cache.
                mLocations.clear();
                return false;
            } else if (itemIndex > getLastIndex()) {
                // if the item is adjacent to first index, should prepend without cache.
                return false;
            }
        }
        int lastIndex = getLastIndex();
        for (; itemIndex < count && itemIndex <= lastIndex; itemIndex++) {
            Location loc = getLocation(itemIndex);
            if (edge != Integer.MAX_VALUE) {
                edge = edge + loc.offset;
            }
            int rowIndex = loc.row;
            int size = mProvider.createItem(itemIndex, true, mTmpItem, false);
            if (size != loc.size) {
                loc.size = size;
                mLocations.removeFromEnd(lastIndex - itemIndex);
                lastIndex = itemIndex;
            }
            mLastVisibleIndex = itemIndex;
            if (mFirstVisibleIndex < 0) {
                mFirstVisibleIndex = itemIndex;
            }
            mProvider.addItem(mTmpItem[0], itemIndex, size, rowIndex, edge);
            if (!oneColumnMode && checkAppendOverLimit(toLimit)) {
                return true;
            }
            if (edge == Integer.MAX_VALUE) {
                edge = mProvider.getEdge(itemIndex);
            }
            // Check limit after filled a full column
            if (rowIndex == mNumRows - 1) {
                if (oneColumnMode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * algorithm of layout staggered grid, this method should only be called by
     * appendVisibleItems().
     */
    protected abstract boolean appendVisibleItemsWithoutCache(int toLimit, boolean oneColumnMode);

    /**
     * Appends one visible item with new Location info.  Only called from
     * appendVisibleItemsWithoutCache().
     */
    protected final int appendVisibleItemToRow(int itemIndex, int rowIndex, int location) {
        int offset;
        if (mLastVisibleIndex >= 0) {
            if (mLastVisibleIndex != getLastIndex() || mLastVisibleIndex != itemIndex - 1) {
                // should never hit this when we append a new item with a new Location object.
                throw new IllegalStateException();
            }
        }
        if (mLastVisibleIndex < 0) {
            // if we append first visible item after existing cached items,  we need update
            // the offset later when prependVisbleItemsWithCache()
            if (mLocations.size() > 0 && itemIndex == getLastIndex() + 1) {
                offset = calculateOffsetAfterLastItem(rowIndex);
            } else {
                offset = 0;
            }
        } else {
            offset = location - mProvider.getEdge(mLastVisibleIndex);
        }
        Location loc = new Location(rowIndex, offset, 0);
        mLocations.addLast(loc);
        Object item;
        if (mPendingItem != null) {
            loc.size = mPendingItemSize;
            item = mPendingItem;
            mPendingItem = null;
        } else {
            loc.size = mProvider.createItem(itemIndex, true, mTmpItem, false);
            item = mTmpItem[0];
        }
        if (mLocations.size() == 1) {
            mFirstIndex = mFirstVisibleIndex = mLastVisibleIndex = itemIndex;
        } else {
            if (mLastVisibleIndex < 0) {
                mFirstVisibleIndex = mLastVisibleIndex = itemIndex;
            } else {
                mLastVisibleIndex++;
            }
        }
        mProvider.addItem(item, itemIndex, loc.size, rowIndex, location);
        return loc.size;
    }

    @Override
    public final CircularIntArray[] getItemPositionsInRows(int startPos, int endPos) {
        for (int i = 0; i < mNumRows; i++) {
            mTmpItemPositionsInRows[i].clear();
        }
        if (startPos >= 0) {
            for (int i = startPos; i <= endPos; i++) {
                CircularIntArray row = mTmpItemPositionsInRows[getLocation(i).row];
                if (row.size() > 0 && row.getLast() == i - 1) {
                    // update continuous range
                    row.popLast();
                    row.addLast(i);
                } else {
                    // add single position
                    row.addLast(i);
                    row.addLast(i);
                }
            }
        }
        return mTmpItemPositionsInRows;
    }

    @Override
    public void invalidateItemsAfter(int index) {
        super.invalidateItemsAfter(index);
        mLocations.removeFromEnd(getLastIndex() - index + 1);
        if (mLocations.size() == 0) {
            mFirstIndex = -1;
        }
    }

}
