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

import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.R;

/**
 * A presenter that assumes a LinearLayout container for a series
 * of control buttons backed by objects of type {@link Action}.
 *
 * Different layouts may be passed to the presenter constructor.
 * The layout must contain a view with id control_bar.
 */
class ControlBarPresenter extends Presenter {

    static final int MAX_CONTROLS = 7;

    /**
     * The data type expected by this presenter.
     */
    static class BoundData {
        /**
         * Adapter containing objects of type {@link Action}.
         */
        ObjectAdapter adapter;

        /**
         * The presenter to be used for the adapter objects.
         */
        Presenter presenter;
    }

    /**
     * Listener for control selected events.
     */
    interface OnControlSelectedListener {
        void onControlSelected(Presenter.ViewHolder controlViewHolder, Object item,
                BoundData data);
    }

    /**
     * Listener for control clicked events.
     */
    interface OnControlClickedListener {
        void onControlClicked(Presenter.ViewHolder controlViewHolder, Object item,
                BoundData data);
    }

    class ViewHolder extends Presenter.ViewHolder {
        ObjectAdapter mAdapter;
        BoundData mData;
        Presenter mPresenter;
        ControlBar mControlBar;
        View mControlsContainer;
        SparseArray<Presenter.ViewHolder> mViewHolders =
                new SparseArray<Presenter.ViewHolder>();
        ObjectAdapter.DataObserver mDataObserver;

        /**
         * Constructor for the ViewHolder.
         */
        ViewHolder(View rootView) {
            super(rootView);
            mControlsContainer = rootView.findViewById(R.id.controls_container);
            mControlBar = (ControlBar) rootView.findViewById(R.id.control_bar);
            if (mControlBar == null) {
                throw new IllegalStateException("Couldn't find control_bar");
            }
            mControlBar.setDefaultFocusToMiddle(mDefaultFocusToMiddle);
            mControlBar.setOnChildFocusedListener(new ControlBar.OnChildFocusedListener() {
                @Override
                public void onChildFocusedListener(View child, View focused) {
                    if (mOnControlSelectedListener == null) {
                        return;
                    }
                    for (int position = 0; position < mViewHolders.size(); position++) {
                        if (mViewHolders.get(position).view == child) {
                            mOnControlSelectedListener.onControlSelected(
                                    mViewHolders.get(position),
                                    getDisplayedAdapter().get(position), mData);
                            break;
                        }
                    }
                }
            });
            mDataObserver = new ObjectAdapter.DataObserver() {
                @Override
                public void onChanged() {
                    if (mAdapter == getDisplayedAdapter()) {
                        showControls(mPresenter);
                    }
                }
                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    if (mAdapter == getDisplayedAdapter()) {
                        for (int i = 0; i < itemCount; i++) {
                            bindControlToAction(positionStart + i, mPresenter);
                        }
                    }
                }
            };
        }

        int getChildMarginFromCenter(Context context, int numControls) {
            // Includes margin between icons plus two times half the icon width.
            return getChildMarginDefault(context) + getControlIconWidth(context);
        }

        void showControls(Presenter presenter) {
            ObjectAdapter adapter = getDisplayedAdapter();
            int adapterSize = adapter == null ? 0 : adapter.size();
            // Shrink the number of attached views
            View focusedView = mControlBar.getFocusedChild();
            if (focusedView != null && adapterSize > 0
                    && mControlBar.indexOfChild(focusedView) >= adapterSize) {
                mControlBar.getChildAt(adapter.size() - 1).requestFocus();
            }
            for (int i = mControlBar.getChildCount() - 1; i >= adapterSize; i--) {
                mControlBar.removeViewAt(i);
            }
            for (int position = 0; position < adapterSize && position < MAX_CONTROLS;
                    position++) {
                bindControlToAction(position, adapter, presenter);
            }
            mControlBar.setChildMarginFromCenter(
                    getChildMarginFromCenter(mControlBar.getContext(), adapterSize));
        }

        void bindControlToAction(int position, Presenter presenter) {
            bindControlToAction(position, getDisplayedAdapter(), presenter);
        }

        private void bindControlToAction(final int position,
                ObjectAdapter adapter, Presenter presenter) {
            Presenter.ViewHolder vh = mViewHolders.get(position);
            Object item = adapter.get(position);
            if (vh == null) {
                vh = presenter.onCreateViewHolder(mControlBar);
                mViewHolders.put(position, vh);

                final Presenter.ViewHolder itemViewHolder = vh;
                presenter.setOnClickListener(vh, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Object item = getDisplayedAdapter().get(position);
                        if (mOnControlClickedListener != null) {
                            mOnControlClickedListener.onControlClicked(itemViewHolder, item,
                                    mData);
                        }
                    }
                });
            }
            if (vh.view.getParent() == null) {
                mControlBar.addView(vh.view);
            }
            presenter.onBindViewHolder(vh, item);
        }

        /**
         * Returns the adapter currently bound to the displayed controls.
         * May be overridden in a subclass.
         */
        ObjectAdapter getDisplayedAdapter() {
            return mAdapter;
        }
    }

    OnControlClickedListener mOnControlClickedListener;
    OnControlSelectedListener mOnControlSelectedListener;
    private int mLayoutResourceId;
    private static int sChildMarginDefault;
    private static int sControlIconWidth;
    boolean mDefaultFocusToMiddle = true;

    /**
     * Constructor for a ControlBarPresenter.
     *
     * @param layoutResourceId The resource id of the layout for this presenter.
     */
    public ControlBarPresenter(int layoutResourceId) {
        mLayoutResourceId = layoutResourceId;
    }

    /**
     * Returns the layout resource id.
     */
    public int getLayoutResourceId() {
        return mLayoutResourceId;
    }

    /**
     * Sets the listener for control clicked events.
     */
    public void setOnControlClickedListener(OnControlClickedListener listener) {
        mOnControlClickedListener = listener;
    }

    /**
     * Returns the listener for control clicked events.
     */
    public OnControlClickedListener getOnItemViewClickedListener() {
        return mOnControlClickedListener;
    }

    /**
     * Sets the listener for control selection.
     */
    public void setOnControlSelectedListener(OnControlSelectedListener listener) {
        mOnControlSelectedListener = listener;
    }

    /**
     * Returns the listener for control selection.
     */
    public OnControlSelectedListener getOnItemControlListener() {
        return mOnControlSelectedListener;
    }

    public void setBackgroundColor(ViewHolder vh, int color) {
        vh.mControlsContainer.setBackgroundColor(color);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(getLayoutResourceId(), parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder holder, Object item) {
        ViewHolder vh = (ViewHolder) holder;
        BoundData data = (BoundData) item;
        if (vh.mAdapter != data.adapter) {
            vh.mAdapter = data.adapter;
            if (vh.mAdapter != null) {
                vh.mAdapter.registerObserver(vh.mDataObserver);
            }
        }
        vh.mPresenter = data.presenter;
        vh.mData = data;
        vh.showControls(vh.mPresenter);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        if (vh.mAdapter != null) {
            vh.mAdapter.unregisterObserver(vh.mDataObserver);
            vh.mAdapter = null;
        }
        vh.mData = null;
    }

    int getChildMarginDefault(Context context) {
        if (sChildMarginDefault == 0) {
            sChildMarginDefault = context.getResources().getDimensionPixelSize(
                    R.dimen.lb_playback_controls_child_margin_default);
        }
        return sChildMarginDefault;
    }

    int getControlIconWidth(Context context) {
        if (sControlIconWidth == 0) {
            sControlIconWidth = context.getResources().getDimensionPixelSize(
                    R.dimen.lb_control_icon_width);
        }
        return sControlIconWidth;
    }

    /**
     * @param defaultFocusToMiddle True for middle item, false for 0.
     */
    void setDefaultFocusToMiddle(boolean defaultFocusToMiddle) {
        mDefaultFocusToMiddle = defaultFocusToMiddle;
    }

}
