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

import androidx.annotation.NonNull;
import androidx.collection.CircularIntArray;
import androidx.recyclerview.widget.RecyclerView;

import java.io.PrintWriter;

/**
 * A Grid with restriction to single row.
 */
class SingleRow extends Grid {

    private final Location mTmpLocation = new Location(0);

    SingleRow() {
        setNumRows(1);
    }

    @Override
    public final Location getLocation(int index) {
        // all items are on row 0, share the same Location object.
        return mTmpLocation;
    }

    @Override
    public final void debugPrint(PrintWriter pw) {
        pw.print("SingleRow<");
        pw.print(mFirstVisibleIndex);
        pw.print(",");
        pw.print(mLastVisibleIndex);
        pw.print(">");
        pw.println();
    }

    int getStartIndexForAppend() {
        if (mLastVisibleIndex >= 0) {
            return mLastVisibleIndex + 1;
        } else if (mStartIndex != START_DEFAULT) {
            return Math.min(mStartIndex, mProvider.getCount() - 1);
        } else {
            return 0;
        }
    }

    int getStartIndexForPrepend() {
        if (mFirstVisibleIndex >= 0) {
            return mFirstVisibleIndex - 1;
        } else if (mStartIndex != START_DEFAULT) {
            return Math.min(mStartIndex, mProvider.getCount() - 1);
        } else {
            return mProvider.getCount() - 1;
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
        boolean filledOne = false;
        int minIndex = mProvider.getMinIndex();
        for (int index = getStartIndexForPrepend(); index >= minIndex; index--) {
            int size = mProvider.createItem(index, false, mTmpItem, false);
            int edge;
            if (mFirstVisibleIndex < 0 || mLastVisibleIndex < 0) {
                edge = mReversedFlow ? Integer.MIN_VALUE : Integer.MAX_VALUE;
                mLastVisibleIndex = mFirstVisibleIndex = index;
            } else {
                if (mReversedFlow) {
                    edge = mProvider.getEdge(index + 1) + mSpacing + size;
                } else {
                    edge = mProvider.getEdge(index + 1) - mSpacing - size;
                }
                mFirstVisibleIndex = index;
            }
            mProvider.addItem(mTmpItem[0], index, size, 0, edge);
            filledOne = true;
            if (oneColumnMode || checkPrependOverLimit(toLimit)) {
                break;
            }
        }
        return filledOne;
    }

    @Override
    protected final boolean appendVisibleItems(int toLimit, boolean oneColumnMode) {
        if (mProvider.getCount() == 0) {
            return false;
        }
        if (!oneColumnMode && checkAppendOverLimit(toLimit)) {
            // not in one column mode, return immediately if over limit
            return false;
        }
        boolean filledOne = false;
        for (int index = getStartIndexForAppend(); index < mProvider.getCount(); index++) {
            int size = mProvider.createItem(index, true, mTmpItem, false);
            int edge;
            if (mFirstVisibleIndex < 0 || mLastVisibleIndex< 0) {
                edge = mReversedFlow ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                mLastVisibleIndex = mFirstVisibleIndex = index;
            } else {
                if (mReversedFlow) {
                    edge = mProvider.getEdge(index - 1) - mProvider.getSize(index - 1) - mSpacing;
                } else {
                    edge = mProvider.getEdge(index - 1) + mProvider.getSize(index - 1) + mSpacing;
                }
                mLastVisibleIndex = index;
            }
            mProvider.addItem(mTmpItem[0], index, size, 0, edge);
            filledOne = true;
            if (oneColumnMode || checkAppendOverLimit(toLimit)) {
                break;
            }
        }
        return filledOne;
    }

    @Override
    public void collectAdjacentPrefetchPositions(int fromLimit, int da,
        @NonNull RecyclerView.LayoutManager.LayoutPrefetchRegistry layoutPrefetchRegistry) {
        int indexToPrefetch;
        int nearestEdge;
        if (mReversedFlow ? da > 0 : da < 0) {
            // prefetch next prepend, lower index number
            if (getFirstVisibleIndex() == 0) {
                return; // no remaining items to prefetch
            }

            indexToPrefetch = getStartIndexForPrepend();
            nearestEdge = mProvider.getEdge(mFirstVisibleIndex)
                    + (mReversedFlow ? mSpacing : -mSpacing);
        } else {
            // prefetch next append, higher index number
            if (getLastVisibleIndex() == mProvider.getCount() - 1) {
                return; // no remaining items to prefetch
            }

            indexToPrefetch = getStartIndexForAppend();
            int itemSizeWithSpace = mProvider.getSize(mLastVisibleIndex) + mSpacing;
            nearestEdge = mProvider.getEdge(mLastVisibleIndex)
                    + (mReversedFlow ? -itemSizeWithSpace : itemSizeWithSpace);
        }

        int distance = Math.abs(nearestEdge - fromLimit);
        layoutPrefetchRegistry.addPosition(indexToPrefetch, distance);
    }

    @Override
    public final CircularIntArray[] getItemPositionsInRows(int startPos, int endPos) {
        // all items are on the same row:
        mTmpItemPositionsInRows[0].clear();
        mTmpItemPositionsInRows[0].addLast(startPos);
        mTmpItemPositionsInRows[0].addLast(endPos);
        return mTmpItemPositionsInRows;
    }

    @Override
    protected final int findRowMin(boolean findLarge, int indexLimit, int[] indices) {
        if (indices != null) {
            indices[0] = 0;
            indices[1] = indexLimit;
        }
        return mReversedFlow ? mProvider.getEdge(indexLimit) - mProvider.getSize(indexLimit)
                : mProvider.getEdge(indexLimit);
    }

    @Override
    protected final int findRowMax(boolean findLarge, int indexLimit, int[] indices) {
        if (indices != null) {
            indices[0] = 0;
            indices[1] = indexLimit;
        }
        return mReversedFlow ? mProvider.getEdge(indexLimit)
                : mProvider.getEdge(indexLimit) + mProvider.getSize(indexLimit);
    }

}
