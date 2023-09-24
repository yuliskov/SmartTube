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

/**
 * A default implementation of {@link StaggeredGrid}.
 *
 * This implementation tries to fill items in consecutive row order. The next
 * item is always in same row or in the next row.
 */
final class StaggeredGridDefault extends StaggeredGrid {

    /**
     * Returns the max edge value of item (visible or cached) in a row.  This
     * will be the place to append or prepend item not in cache.
     */
    int getRowMax(int rowIndex) {
        if (mFirstVisibleIndex < 0) {
            return Integer.MIN_VALUE;
        }
        if (mReversedFlow) {
            int edge = mProvider.getEdge(mFirstVisibleIndex);
            if (getLocation(mFirstVisibleIndex).row == rowIndex) {
                return edge;
            }
            for (int i = mFirstVisibleIndex + 1; i <= getLastIndex(); i++) {
                Location loc = getLocation(i);
                edge += loc.offset;
                if (loc.row == rowIndex) {
                    return edge;
                }
            }
        } else {
            int edge = mProvider.getEdge(mLastVisibleIndex);
            Location loc = getLocation(mLastVisibleIndex);
            if (loc.row == rowIndex) {
                return edge + loc.size;
            }
            for (int i = mLastVisibleIndex - 1; i >= getFirstIndex(); i--) {
                edge -= loc.offset;
                loc = getLocation(i);
                if (loc.row == rowIndex) {
                    return edge + loc.size;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Returns the min edge value of item (visible or cached) in a row.  This
     * will be the place to prepend or append item not in cache.
     */
    int getRowMin(int rowIndex) {
        if (mFirstVisibleIndex < 0) {
            return Integer.MAX_VALUE;
        }
        if (mReversedFlow) {
            int edge = mProvider.getEdge(mLastVisibleIndex);
            Location loc = getLocation(mLastVisibleIndex);
            if (loc.row == rowIndex) {
                return edge - loc.size;
            }
            for (int i = mLastVisibleIndex - 1; i >= getFirstIndex(); i--) {
                edge -= loc.offset;
                loc = getLocation(i);
                if (loc.row == rowIndex) {
                    return edge - loc.size;
                }
            }
        } else {
            int edge = mProvider.getEdge(mFirstVisibleIndex);
            if (getLocation(mFirstVisibleIndex).row == rowIndex) {
                return edge;
            }
            for (int i = mFirstVisibleIndex + 1; i <= getLastIndex() ; i++) {
                Location loc = getLocation(i);
                edge += loc.offset;
                if (loc.row == rowIndex) {
                    return edge;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Note this method has assumption that item is filled either in the same row
     * next row of last item.  Search until row index wrapped.
     */
    @Override
    public int findRowMax(boolean findLarge, int indexLimit, int[] indices) {
        int value;
        int edge = mProvider.getEdge(indexLimit);
        Location loc = getLocation(indexLimit);
        int row = loc.row;
        int index = indexLimit;
        int visitedRows = 1;
        int visitRow = row;
        if (mReversedFlow) {
            value = edge;
            for (int i = indexLimit + 1; visitedRows < mNumRows && i <= mLastVisibleIndex; i++) {
                loc = getLocation(i);
                edge += loc.offset;
                if (loc.row != visitRow) {
                    visitRow = loc.row;
                    visitedRows++;
                    if (findLarge ? edge > value : edge < value) {
                        row = visitRow;
                        value = edge;
                        index = i;
                    }
                }
            }
        } else {
            value = edge + mProvider.getSize(indexLimit);
            for (int i = indexLimit - 1; visitedRows < mNumRows && i >= mFirstVisibleIndex; i--) {
                edge -= loc.offset;
                loc = getLocation(i);
                if (loc.row != visitRow) {
                    visitRow = loc.row;
                    visitedRows++;
                    int newValue = edge + mProvider.getSize(i);
                    if (findLarge ? newValue > value : newValue < value) {
                        row = visitRow;
                        value = newValue;
                        index = i;
                    }
                }
            }
        }
        if (indices != null) {
            indices[0] = row;
            indices[1] = index;
        }
        return value;
    }

    /**
     * Note this method has assumption that item is filled either in the same row
     * next row of last item.  Search until row index wrapped.
     */
    @Override
    public int findRowMin(boolean findLarge, int indexLimit, int[] indices) {
        int value;
        int edge = mProvider.getEdge(indexLimit);
        Location loc = getLocation(indexLimit);
        int row = loc.row;
        int index = indexLimit;
        int visitedRows = 1;
        int visitRow = row;
        if (mReversedFlow) {
            value = edge - mProvider.getSize(indexLimit);
            for (int i = indexLimit - 1; visitedRows < mNumRows && i >= mFirstVisibleIndex; i--) {
                edge -= loc.offset;
                loc = getLocation(i);
                if (loc.row != visitRow) {
                    visitRow = loc.row;
                    visitedRows++;
                    int newValue = edge - mProvider.getSize(i);
                    if (findLarge ? newValue > value : newValue < value) {
                        value = newValue;
                        row = visitRow;
                        index = i;
                    }
                }
            }
        } else {
            value = edge;
            for (int i = indexLimit + 1; visitedRows < mNumRows && i <= mLastVisibleIndex; i++) {
                loc = getLocation(i);
                edge += loc.offset;
                if (loc.row != visitRow) {
                    visitRow = loc.row;
                    visitedRows++;
                    if (findLarge ? edge > value : edge < value) {
                        value = edge;
                        row = visitRow;
                        index = i;
                    }
                }
            }
        }
        if (indices != null) {
            indices[0] = row;
            indices[1] = index;
        }
        return value;
    }

    private int findRowEdgeLimitSearchIndex(boolean append) {
        boolean wrapped = false;
        if (append) {
            for (int index = mLastVisibleIndex; index >= mFirstVisibleIndex; index--) {
                int row = getLocation(index).row;
                if (row == 0) {
                    wrapped = true;
                } else if (wrapped && row == mNumRows - 1) {
                    return index;
                }
            }
        } else {
            for (int index = mFirstVisibleIndex; index <= mLastVisibleIndex; index++) {
                int row = getLocation(index).row;
                if (row == mNumRows - 1) {
                    wrapped = true;
                } else if (wrapped && row == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    @Override
    protected boolean appendVisibleItemsWithoutCache(int toLimit, boolean oneColumnMode) {
        final int count = mProvider.getCount();
        int itemIndex;
        int rowIndex;
        int edgeLimit;
        boolean edgeLimitIsValid;
        if (mLastVisibleIndex >= 0) {
            if (mLastVisibleIndex < getLastIndex()) {
                // should fill using cache instead
                return false;
            }
            itemIndex = mLastVisibleIndex + 1;
            rowIndex = getLocation(mLastVisibleIndex).row;
            // find start item index of "previous column"
            int edgeLimitSearchIndex = findRowEdgeLimitSearchIndex(true);
            if (edgeLimitSearchIndex < 0) {
                // if "previous column" is not found, using edgeLimit of
                // first row currently in grid
                edgeLimit = Integer.MIN_VALUE;
                for (int i = 0; i < mNumRows; i++) {
                    edgeLimit = mReversedFlow ? getRowMin(i) : getRowMax(i);
                    if (edgeLimit != Integer.MIN_VALUE) {
                        break;
                    }
                }
            } else {
                edgeLimit = mReversedFlow ? findRowMin(false, edgeLimitSearchIndex, null) :
                        findRowMax(true, edgeLimitSearchIndex, null);
            }
            if (mReversedFlow ? getRowMin(rowIndex) <= edgeLimit
                    : getRowMax(rowIndex) >= edgeLimit) {
                // if current row exceeds previous column, fill from next row
                rowIndex = rowIndex + 1;
                if (rowIndex == mNumRows) {
                    // start a new column and using edge limit of current column
                    rowIndex = 0;
                    edgeLimit = mReversedFlow ? findRowMin(false, null) : findRowMax(true, null);
                }
            }
            edgeLimitIsValid = true;
        } else {
            itemIndex = mStartIndex != START_DEFAULT ? mStartIndex : 0;
            // if there are cached items,  put on next row of last cached item.
            rowIndex = (mLocations.size() > 0 ? getLocation(getLastIndex()).row + 1 : itemIndex)
                    % mNumRows;
            edgeLimit = 0;
            edgeLimitIsValid = false;
        }

        boolean filledOne = false;
        while (true) {
            // find end-most row edge (.high is biggest, or .low is smallest in reversed flow)
            // fill from current row till last row so that each row will grow longer than
            // the previous highest row.
            for (; rowIndex < mNumRows; rowIndex++) {
                // fill one item to a row
                if (itemIndex == count || (!oneColumnMode && checkAppendOverLimit(toLimit))) {
                    return filledOne;
                }
                int location = mReversedFlow ? getRowMin(rowIndex) : getRowMax(rowIndex);
                if (location == Integer.MAX_VALUE || location == Integer.MIN_VALUE) {
                    // nothing on the row
                    if (rowIndex == 0) {
                        location = mReversedFlow ? getRowMin(mNumRows - 1) : getRowMax(mNumRows - 1);
                        if (location != Integer.MAX_VALUE && location != Integer.MIN_VALUE) {
                            location = location + (mReversedFlow ? -mSpacing : mSpacing);
                        }
                    } else {
                        location = mReversedFlow ? getRowMax(rowIndex - 1) : getRowMin(rowIndex - 1);
                    }
                } else {
                    location = location + (mReversedFlow ? -mSpacing : mSpacing);
                }
                int size = appendVisibleItemToRow(itemIndex++, rowIndex, location);
                filledOne = true;
                // fill more item to the row to make sure this row is longer than
                // the previous highest row.
                if (edgeLimitIsValid) {
                    while (mReversedFlow ? location - size > edgeLimit :
                            location + size < edgeLimit) {
                        if (itemIndex == count || (!oneColumnMode && checkAppendOverLimit(toLimit))) {
                            return filledOne;
                        }
                        location = location + (mReversedFlow ? - size - mSpacing : size + mSpacing);
                        size = appendVisibleItemToRow(itemIndex++, rowIndex, location);
                    }
                } else {
                    edgeLimitIsValid = true;
                    edgeLimit = mReversedFlow ? getRowMin(rowIndex) : getRowMax(rowIndex);
                }
            }
            if (oneColumnMode) {
                return filledOne;
            }
            edgeLimit = mReversedFlow ? findRowMin(false, null) : findRowMax(true, null);
            // start fill from row 0 again
            rowIndex = 0;
        }
    }

    @Override
    protected boolean prependVisibleItemsWithoutCache(int toLimit, boolean oneColumnMode) {
        int itemIndex;
        int rowIndex;
        int edgeLimit;
        boolean edgeLimitIsValid;
        if (mFirstVisibleIndex >= 0) {
            if (mFirstVisibleIndex > getFirstIndex()) {
                // should fill using cache instead
                return false;
            }
            itemIndex = mFirstVisibleIndex - 1;
            rowIndex = getLocation(mFirstVisibleIndex).row;
            // find start item index of "previous column"
            int edgeLimitSearchIndex = findRowEdgeLimitSearchIndex(false);
            if (edgeLimitSearchIndex < 0) {
                // if "previous column" is not found, using edgeLimit of
                // last row currently in grid and fill from upper row
                rowIndex = rowIndex - 1;
                edgeLimit = Integer.MAX_VALUE;
                for (int i = mNumRows - 1; i >= 0; i--) {
                    edgeLimit = mReversedFlow ? getRowMax(i) : getRowMin(i);
                    if (edgeLimit != Integer.MAX_VALUE) {
                        break;
                    }
                }
            } else {
                edgeLimit = mReversedFlow ? findRowMax(true, edgeLimitSearchIndex, null) :
                        findRowMin(false, edgeLimitSearchIndex, null);
            }
            if (mReversedFlow ? getRowMax(rowIndex) >= edgeLimit
                    : getRowMin(rowIndex) <= edgeLimit) {
                // if current row exceeds previous column, fill from next row
                rowIndex = rowIndex - 1;
                if (rowIndex < 0) {
                    // start a new column and using edge limit of current column
                    rowIndex = mNumRows - 1;
                    edgeLimit = mReversedFlow ? findRowMax(true, null) :
                            findRowMin(false, null);
                }
            }
            edgeLimitIsValid = true;
        } else {
            itemIndex = mStartIndex != START_DEFAULT ? mStartIndex : 0;
            // if there are cached items,  put on previous row of first cached item.
            rowIndex = (mLocations.size() > 0 ? getLocation(getFirstIndex()).row + mNumRows - 1
                    : itemIndex) % mNumRows;
            edgeLimit = 0;
            edgeLimitIsValid = false;
        }
        boolean filledOne = false;
        while (true) {
            // find start-most row edge (.low is smallest, or .high is largest in reversed flow)
            // fill from current row till first row so that each row will grow longer than
            // the previous lowest row.
            for (; rowIndex >= 0; rowIndex--) {
                // fill one item to a row
                if (itemIndex < 0 || (!oneColumnMode && checkPrependOverLimit(toLimit))) {
                    return filledOne;
                }
                int location = mReversedFlow ? getRowMax(rowIndex) : getRowMin(rowIndex);
                if (location == Integer.MAX_VALUE || location == Integer.MIN_VALUE) {
                    // nothing on the row
                    if (rowIndex == mNumRows - 1) {
                        location = mReversedFlow ? getRowMax(0) : getRowMin(0);
                        if (location != Integer.MAX_VALUE && location != Integer.MIN_VALUE) {
                            location = location + (mReversedFlow ? mSpacing : -mSpacing);
                        }
                    } else {
                        location = mReversedFlow ? getRowMin(rowIndex + 1) : getRowMax(rowIndex + 1);
                    }
                } else {
                    location = location + (mReversedFlow ? mSpacing : -mSpacing);
                }
                int size = prependVisibleItemToRow(itemIndex--, rowIndex, location);
                filledOne = true;

                // fill more item to the row to make sure this row is longer than
                // the previous highest row.
                if (edgeLimitIsValid) {
                    while (mReversedFlow ? location + size < edgeLimit :
                            location - size > edgeLimit) {
                        if (itemIndex < 0 || (!oneColumnMode && checkPrependOverLimit(toLimit))) {
                            return filledOne;
                        }
                        location = location + (mReversedFlow ? size + mSpacing : -size - mSpacing);
                        size = prependVisibleItemToRow(itemIndex--, rowIndex, location);
                    }
                } else {
                    edgeLimitIsValid = true;
                    edgeLimit = mReversedFlow ? getRowMax(rowIndex) : getRowMin(rowIndex);
                }
            }
            if (oneColumnMode) {
                return filledOne;
            }
            edgeLimit = mReversedFlow ? findRowMax(true, null) : findRowMin(false, null);
            // start fill from last row again
            rowIndex = mNumRows - 1;
        }
    }


}
