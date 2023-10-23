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

import static androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_BOTH_EDGE;
import static androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_HIGH_EDGE;
import static androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_LOW_EDGE;
import static androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED;
import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;

/**
 * Maintains Window Alignment information of two axis.
 */
class WindowAlignment {

    /**
     * Maintains alignment information in one direction.
     */
    public static class Axis {
        /**
         * Right or bottom edge of last child.
         */
        private int mMaxEdge;
        /**
         * Left or top edge of first child
         */
        private int mMinEdge;
        /**
         * Scroll distance to align last child, it defines limit of scroll.
         */
        private int mMaxScroll;
        /**
         * Scroll distance to align first child, it defines limit of scroll.
         */
        private int mMinScroll;

        static final int PF_KEYLINE_OVER_LOW_EDGE = 1;
        static final int PF_KEYLINE_OVER_HIGH_EDGE = 1 << 1;

        /**
         * By default we prefer low edge over keyline, prefer keyline over high edge.
         */
        private int mPreferredKeyLine = PF_KEYLINE_OVER_HIGH_EDGE;

        private int mWindowAlignment = WINDOW_ALIGN_BOTH_EDGE;

        private int mWindowAlignmentOffset = 0;

        private float mWindowAlignmentOffsetPercent = 50f;

        private int mSize;

        /**
         * Padding at the min edge, it is the left or top padding.
         */
        private int mPaddingMin;

        /**
         * Padding at the max edge, it is the right or bottom padding.
         */
        private int mPaddingMax;

        private boolean mReversedFlow;

        private String mName; // for debugging

        public Axis(String name) {
            reset();
            mName = name;
        }

        public final int getWindowAlignment() {
            return mWindowAlignment;
        }

        public final void setWindowAlignment(int windowAlignment) {
            mWindowAlignment = windowAlignment;
        }

        final void setPreferKeylineOverLowEdge(boolean keylineOverLowEdge) {
            mPreferredKeyLine = keylineOverLowEdge
                    ? mPreferredKeyLine | PF_KEYLINE_OVER_LOW_EDGE
                    : mPreferredKeyLine & ~PF_KEYLINE_OVER_LOW_EDGE;
        }

        final void setPreferKeylineOverHighEdge(boolean keylineOverHighEdge) {
            mPreferredKeyLine = keylineOverHighEdge
                    ? mPreferredKeyLine | PF_KEYLINE_OVER_HIGH_EDGE
                    : mPreferredKeyLine & ~PF_KEYLINE_OVER_HIGH_EDGE;
        }

        final boolean isPreferKeylineOverHighEdge() {
            return (mPreferredKeyLine & PF_KEYLINE_OVER_HIGH_EDGE) != 0;
        }

        final boolean isPreferKeylineOverLowEdge() {
            return (mPreferredKeyLine & PF_KEYLINE_OVER_LOW_EDGE) != 0;
        }

        public final int getWindowAlignmentOffset() {
            return mWindowAlignmentOffset;
        }

        public final void setWindowAlignmentOffset(int offset) {
            mWindowAlignmentOffset = offset;
        }

        public final void setWindowAlignmentOffsetPercent(float percent) {
            if ((percent < 0 || percent > 100)
                    && percent != WINDOW_ALIGN_OFFSET_PERCENT_DISABLED) {
                throw new IllegalArgumentException();
            }
            mWindowAlignmentOffsetPercent = percent;
        }

        public final float getWindowAlignmentOffsetPercent() {
            return mWindowAlignmentOffsetPercent;
        }

        /**
         * Returns scroll distance to align min child.
         */
        public final int getMinScroll() {
            return mMinScroll;
        }

        public final void invalidateScrollMin() {
            mMinEdge = Integer.MIN_VALUE;
            mMinScroll = Integer.MIN_VALUE;
        }

        /**
         * Returns scroll distance to align max child.
         */
        public final int getMaxScroll() {
            return mMaxScroll;
        }

        public final void invalidateScrollMax() {
            mMaxEdge = Integer.MAX_VALUE;
            mMaxScroll = Integer.MAX_VALUE;
        }

        void reset() {
            mMinEdge = Integer.MIN_VALUE;
            mMaxEdge = Integer.MAX_VALUE;
        }

        public final boolean isMinUnknown() {
            return mMinEdge == Integer.MIN_VALUE;
        }

        public final boolean isMaxUnknown() {
            return mMaxEdge == Integer.MAX_VALUE;
        }

        public final void setSize(int size) {
            mSize = size;
        }

        public final int getSize() {
            return mSize;
        }

        public final void setPadding(int paddingMin, int paddingMax) {
            mPaddingMin = paddingMin;
            mPaddingMax = paddingMax;
        }

        public final int getPaddingMin() {
            return mPaddingMin;
        }

        public final int getPaddingMax() {
            return mPaddingMax;
        }

        public final int getClientSize() {
            return mSize - mPaddingMin - mPaddingMax;
        }

        final int calculateKeyline() {
            int keyLine;
            if (!mReversedFlow) {
                if (mWindowAlignmentOffset >= 0) {
                    keyLine = mWindowAlignmentOffset;
                } else {
                    keyLine = mSize + mWindowAlignmentOffset;
                }
                if (mWindowAlignmentOffsetPercent != WINDOW_ALIGN_OFFSET_PERCENT_DISABLED) {
                    keyLine += (int) (mSize * mWindowAlignmentOffsetPercent / 100);
                }
            } else {
                if (mWindowAlignmentOffset >= 0) {
                    keyLine = mSize - mWindowAlignmentOffset;
                } else {
                    keyLine = -mWindowAlignmentOffset;
                }
                if (mWindowAlignmentOffsetPercent != WINDOW_ALIGN_OFFSET_PERCENT_DISABLED) {
                    keyLine -= (int) (mSize * mWindowAlignmentOffsetPercent / 100);
                }
            }
            return keyLine;
        }

        /**
         * Returns scroll distance to move viewCenterPosition to keyLine.
         */
        final int calculateScrollToKeyLine(int viewCenterPosition, int keyLine) {
            return viewCenterPosition - keyLine;
        }

        /**
         * Update {@link #getMinScroll()} and {@link #getMaxScroll()}
         */
        public final void updateMinMax(int minEdge, int maxEdge,
                int minChildViewCenter, int maxChildViewCenter) {
            mMinEdge = minEdge;
            mMaxEdge = maxEdge;
            final int clientSize = getClientSize();
            final int keyLine = calculateKeyline();
            final boolean isMinUnknown = isMinUnknown();
            final boolean isMaxUnknown = isMaxUnknown();
            if (!isMinUnknown) {
                if (!mReversedFlow ? (mWindowAlignment & WINDOW_ALIGN_LOW_EDGE) != 0
                        : (mWindowAlignment & WINDOW_ALIGN_HIGH_EDGE) != 0) {
                    // calculate scroll distance to move current mMinEdge to padding at min edge
                    mMinScroll = mMinEdge - mPaddingMin;
                } else  {
                    // calculate scroll distance to move min child center to key line
                    mMinScroll = calculateScrollToKeyLine(minChildViewCenter, keyLine);
                }
            }
            if (!isMaxUnknown) {
                if (!mReversedFlow ? (mWindowAlignment & WINDOW_ALIGN_HIGH_EDGE) != 0
                        : (mWindowAlignment & WINDOW_ALIGN_LOW_EDGE) != 0) {
                    // calculate scroll distance to move current mMaxEdge to padding at max edge
                    mMaxScroll = mMaxEdge - mPaddingMin - clientSize;
                } else  {
                    // calculate scroll distance to move max child center to key line
                    mMaxScroll = calculateScrollToKeyLine(maxChildViewCenter, keyLine);
                }
            }
            if (!isMaxUnknown && !isMinUnknown) {
                if (!mReversedFlow) {
                    if ((mWindowAlignment & WINDOW_ALIGN_LOW_EDGE) != 0) {
                        if (isPreferKeylineOverLowEdge()) {
                            // if we prefer key line, might align max child to key line for
                            // minScroll
                            mMinScroll = Math.min(mMinScroll,
                                    calculateScrollToKeyLine(maxChildViewCenter, keyLine));
                        }
                        // don't over scroll max
                        mMaxScroll = Math.max(mMinScroll, mMaxScroll);
                    } else if ((mWindowAlignment & WINDOW_ALIGN_HIGH_EDGE) != 0) {
                        if (isPreferKeylineOverHighEdge()) {
                            // if we prefer key line, might align min child to key line for
                            // maxScroll
                            mMaxScroll = Math.max(mMaxScroll,
                                    calculateScrollToKeyLine(minChildViewCenter, keyLine));
                        }
                        // don't over scroll min
                        mMinScroll = Math.min(mMinScroll, mMaxScroll);
                    }
                } else {
                    if ((mWindowAlignment & WINDOW_ALIGN_LOW_EDGE) != 0) {
                        if (isPreferKeylineOverLowEdge()) {
                            // if we prefer key line, might align min child to key line for
                            // maxScroll
                            mMaxScroll = Math.max(mMaxScroll,
                                    calculateScrollToKeyLine(minChildViewCenter, keyLine));
                        }
                        // don't over scroll min
                        mMinScroll = Math.min(mMinScroll, mMaxScroll);
                    } else if ((mWindowAlignment & WINDOW_ALIGN_HIGH_EDGE) != 0) {
                        if (isPreferKeylineOverHighEdge()) {
                            // if we prefer key line, might align max child to key line for
                            // minScroll
                            mMinScroll = Math.min(mMinScroll,
                                    calculateScrollToKeyLine(maxChildViewCenter, keyLine));
                        }
                        // don't over scroll max
                        mMaxScroll = Math.max(mMinScroll, mMaxScroll);
                    }
                }
            }
        }

        /**
         * Get scroll distance of align an item (depends on ALIGN_LOW_EDGE, ALIGN_HIGH_EDGE or the
         * item should be aligned to key line). The scroll distance will be capped by
         * {@link #getMinScroll()} and {@link #getMaxScroll()}.
         */
        public final int getScroll(int viewCenter) {
            final int size = getSize();
            final int keyLine = calculateKeyline();
            final boolean isMinUnknown = isMinUnknown();
            final boolean isMaxUnknown = isMaxUnknown();
            if (!isMinUnknown) {
                final int keyLineToMinEdge = keyLine - mPaddingMin;
                if ((!mReversedFlow ? (mWindowAlignment & WINDOW_ALIGN_LOW_EDGE) != 0
                     : (mWindowAlignment & WINDOW_ALIGN_HIGH_EDGE) != 0)
                        && (viewCenter - mMinEdge <= keyLineToMinEdge)) {
                    // view center is before key line: align the min edge (first child) to padding.
                    int alignToMin = mMinEdge - mPaddingMin;
                    // Also we need make sure don't over scroll
                    if (!isMaxUnknown && alignToMin > mMaxScroll) {
                        alignToMin = mMaxScroll;
                    }
                    return alignToMin;
                }
            }
            if (!isMaxUnknown) {
                final int keyLineToMaxEdge = size - keyLine - mPaddingMax;
                if ((!mReversedFlow ? (mWindowAlignment & WINDOW_ALIGN_HIGH_EDGE) != 0
                        : (mWindowAlignment & WINDOW_ALIGN_LOW_EDGE) != 0)
                        && (mMaxEdge - viewCenter <= keyLineToMaxEdge)) {
                    // view center is after key line: align the max edge (last child) to padding.
                    int alignToMax = mMaxEdge - (size - mPaddingMax);
                    // Also we need make sure don't over scroll
                    if (!isMinUnknown && alignToMax < mMinScroll) {
                        alignToMax = mMinScroll;
                    }
                    return alignToMax;
                }
            }
            // else put view center at key line.
            return calculateScrollToKeyLine(viewCenter, keyLine);
        }

        public final void setReversedFlow(boolean reversedFlow) {
            mReversedFlow = reversedFlow;
        }

        @Override
        public String toString() {
            return " min:" + mMinEdge + " " + mMinScroll + " max:" + mMaxEdge + " " + mMaxScroll;
        }

    }

    private int mOrientation = HORIZONTAL;

    public final Axis vertical = new Axis("vertical");

    public final Axis horizontal = new Axis("horizontal");

    private Axis mMainAxis = horizontal;

    private Axis mSecondAxis = vertical;

    public final Axis mainAxis() {
        return mMainAxis;
    }

    public final Axis secondAxis() {
        return mSecondAxis;
    }

    public final void setOrientation(int orientation) {
        mOrientation = orientation;
        if (mOrientation == HORIZONTAL) {
            mMainAxis = horizontal;
            mSecondAxis = vertical;
        } else {
            mMainAxis = vertical;
            mSecondAxis = horizontal;
        }
    }

    public final int getOrientation() {
        return mOrientation;
    }

    public final void reset() {
        mainAxis().reset();
    }

    @Override
    public String toString() {
        return "horizontal=" + horizontal + "; vertical=" + vertical;
    }

}
