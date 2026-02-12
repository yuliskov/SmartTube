/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.widget;

import static androidx.recyclerview.widget.RecyclerView.LayoutManager;
import static androidx.recyclerview.widget.RecyclerView.OnScrollListener;
import static androidx.recyclerview.widget.RecyclerView.ViewHolder;

import android.graphics.Rect;
import android.util.Property;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Implementation of {@link Parallax} class for {@link RecyclerView}. This class
 * allows users to track position of specific views inside {@link RecyclerView} relative to
 * itself. See {@link ChildPositionProperty} for details.
 */
public class RecyclerViewParallax extends Parallax<RecyclerViewParallax.ChildPositionProperty> {
    RecyclerView mRecylerView;
    boolean mIsVertical;

    OnScrollListener mOnScrollListener = new OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            updateValues();
        }
    };

    View.OnLayoutChangeListener mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View view, int l, int t, int r, int b,
                int oldL, int oldT, int oldR, int oldB) {
            updateValues();
        }
    };

    /**
     * Subclass of {@link Parallax.IntProperty}. Using this Property, users can track a
     * RecylerView child's position inside recyclerview. i.e.
     *
     * tracking_pos = view.top + fraction * view.height() + offset
     *
     * This way we can track top using fraction 0 and bottom using fraction 1.
     */
    public static final class ChildPositionProperty extends Parallax.IntProperty {
        int mAdapterPosition;
        int mViewId;
        int mOffset;
        float mFraction;

        ChildPositionProperty(String name, int index) {
            super(name, index);
        }

        /**
         * Sets adapter position of the recyclerview child to track.
         *
         * @param adapterPosition Zero based position in adapter.
         * @return This ChildPositionProperty object.
         */
        public ChildPositionProperty adapterPosition(int adapterPosition) {
            mAdapterPosition = adapterPosition;
            return this;
        };

        /**
         * Sets view Id of a descendant of recyclerview child to track.
         *
         * @param viewId Id of a descendant of recyclerview child.
         * @return This ChildPositionProperty object.
         */
        public ChildPositionProperty viewId(int viewId) {
            mViewId = viewId;
            return this;
        }

        /**
         * Sets offset in pixels added to the view's start position.
         *
         * @param offset Offset in pixels added to the view's start position.
         * @return This ChildPositionProperty object.
         */
        public ChildPositionProperty offset(int offset) {
            mOffset = offset;
            return this;
        }

        /**
         * Sets fraction of size to be added to view's start position.  e.g. to track the
         * center position of the view, use fraction 0.5; to track the end position of the view
         * use fraction 1.
         *
         * @param fraction Fraction of size of the view.
         * @return This ChildPositionProperty object.
         */
        public ChildPositionProperty fraction(float fraction) {
            mFraction = fraction;
            return this;
        }

        /**
         * Returns adapter position of the recyclerview child to track.
         */
        public int getAdapterPosition() {
            return mAdapterPosition;
        }

        /**
         * Returns view Id of a descendant of recyclerview child to track.
         */
        public int getViewId() {
            return mViewId;
        }

        /**
         * Returns offset in pixels added to the view's start position.
         */
        public int getOffset() {
            return mOffset;
        }

        /**
         * Returns fraction of size to be added to view's start position.  e.g. to track the
         * center position of the view, use fraction 0.5; to track the end position of the view
         * use fraction 1.
         */
        public float getFraction() {
            return mFraction;
        }

        void updateValue(RecyclerViewParallax source) {
            RecyclerView recyclerView = source.mRecylerView;
            ViewHolder viewHolder = recyclerView == null ? null
                    : recyclerView.findViewHolderForAdapterPosition(mAdapterPosition);
            if (viewHolder == null) {
                if (recyclerView == null || recyclerView.getLayoutManager().getChildCount() == 0) {
                    source.setIntPropertyValue(getIndex(), IntProperty.UNKNOWN_AFTER);
                    return;
                }
                View firstChild = recyclerView.getLayoutManager().getChildAt(0);
                ViewHolder vh = recyclerView.findContainingViewHolder(firstChild);
                int firstPosition = vh.getAdapterPosition();
                if (firstPosition < mAdapterPosition) {
                    source.setIntPropertyValue(getIndex(), IntProperty.UNKNOWN_AFTER);
                } else {
                    source.setIntPropertyValue(getIndex(), IntProperty.UNKNOWN_BEFORE);
                }
            } else {
                View trackingView = viewHolder.itemView.findViewById(mViewId);
                if (trackingView == null) {
                    return;
                }

                Rect rect = new Rect(
                        0, 0, trackingView.getWidth(), trackingView.getHeight());
                recyclerView.offsetDescendantRectToMyCoords(trackingView, rect);
                // Slide transition may change the trackingView's translationX/translationY,
                // add up translation values in parent.
                float tx = 0, ty = 0;
                while (trackingView != recyclerView && trackingView != null) {
                    // In RecyclerView dispatchLayout() it may call onScrolled(0) with a move
                    // ItemAnimation just created. We don't have any way to track the ItemAnimation
                    // update listener, and in ideal use case, the tracking view should not be
                    // animated in RecyclerView. Do not apply translation value for this case.
                    if (!(trackingView.getParent() == recyclerView && recyclerView.isAnimating())) {
                        tx += trackingView.getTranslationX();
                        ty += trackingView.getTranslationY();
                    }
                    trackingView = (View) trackingView.getParent();
                }
                rect.offset((int) tx, (int) ty);
                if (source.mIsVertical) {
                    source.setIntPropertyValue(getIndex(), rect.top + mOffset
                            + (int) (mFraction * rect.height()));
                } else {
                    source.setIntPropertyValue(getIndex(), rect.left + mOffset
                            + (int) (mFraction * rect.width()));
                }
            }
        }
    }


    @Override
    public ChildPositionProperty createProperty(String name, int index) {
        return new ChildPositionProperty(name, index);
    }

    @Override
    public float getMaxValue() {
        if (mRecylerView == null) {
            return 0;
        }
        return mIsVertical ? mRecylerView.getHeight() : mRecylerView.getWidth();
    }

    /**
     * Set RecyclerView that this Parallax will register onScrollListener.
     * @param recyclerView RecyclerView to register onScrollListener.
     */
    public void setRecyclerView(RecyclerView recyclerView) {
        if (mRecylerView == recyclerView) {
            return;
        }
        if (mRecylerView != null) {
            mRecylerView.removeOnScrollListener(mOnScrollListener);
            mRecylerView.removeOnLayoutChangeListener(mOnLayoutChangeListener);
        }
        mRecylerView = recyclerView;
        if (mRecylerView != null) {
            LayoutManager.Properties properties = mRecylerView.getLayoutManager()
                    .getProperties(mRecylerView.getContext(), null, 0, 0);
            mIsVertical = properties.orientation == RecyclerView.VERTICAL;
            mRecylerView.addOnScrollListener(mOnScrollListener);
            mRecylerView.addOnLayoutChangeListener(mOnLayoutChangeListener);
        }
    }

    /**
     * Manually update values. This is used for changes not controlled by RecyclerView. E.g.
     * called by a Slide transition that changes translation of the view.
     */
    @Override
    public void updateValues() {
        for (Property prop: getProperties()) {
            ((ChildPositionProperty) prop).updateValue(RecyclerViewParallax.this);
        }
        super.updateValues();
    }

    /**
     * @return Currently RecylerView that the source has registered onScrollListener.
     */
    public RecyclerView getRecyclerView() {
        return mRecylerView;
    }
}
