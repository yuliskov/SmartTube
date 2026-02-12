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

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge from {@link Presenter} to {@link RecyclerView.Adapter}. Public to allow use by third
 * party Presenters.
 */
public class ItemBridgeAdapter extends RecyclerView.Adapter implements FacetProviderAdapter {
    static final String TAG = "ItemBridgeAdapter";
    static final boolean DEBUG = false;

    /**
     * Interface for listening to ViewHolder operations.
     */
    public static class AdapterListener {
        public void onAddPresenter(Presenter presenter, int type) {
        }

        public void onCreate(ViewHolder viewHolder) {
        }

        public void onBind(ViewHolder viewHolder) {
        }

        public void onBind(ViewHolder viewHolder, List payloads) {
            onBind(viewHolder);
        }

        public void onUnbind(ViewHolder viewHolder) {
        }

        public void onAttachedToWindow(ViewHolder viewHolder) {
        }

        public void onDetachedFromWindow(ViewHolder viewHolder) {
        }
    }

    /**
     * Interface for wrapping a view created by a Presenter into another view.
     * The wrapper must be the immediate parent of the wrapped view.
     */
    public static abstract class Wrapper {
        public abstract View createWrapper(View root);

        public abstract void wrap(View wrapper, View wrapped);
    }

    private ObjectAdapter mAdapter;
    Wrapper mWrapper;
    private PresenterSelector mPresenterSelector;
    FocusHighlightHandler mFocusHighlight;
    private AdapterListener mAdapterListener;
    private ArrayList<Presenter> mPresenters = new ArrayList<Presenter>();

    final class OnFocusChangeListener implements View.OnFocusChangeListener {
        View.OnFocusChangeListener mChainedListener;

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (DEBUG) {
                Log.v(TAG, "onFocusChange " + hasFocus + " " + view
                        + " mFocusHighlight" + mFocusHighlight);
            }
            if (mWrapper != null) {
                view = (View) view.getParent();
            }
            if (mFocusHighlight != null) {
                mFocusHighlight.onItemFocused(view, hasFocus);
            }
            if (mChainedListener != null) {
                mChainedListener.onFocusChange(view, hasFocus);
            }
        }
    }

    /**
     * ViewHolder for the ItemBridgeAdapter.
     */
    public class ViewHolder extends RecyclerView.ViewHolder implements FacetProvider {
        final Presenter mPresenter;
        final Presenter.ViewHolder mHolder;
        final OnFocusChangeListener mFocusChangeListener = new OnFocusChangeListener();
        Object mItem;
        Object mExtraObject;

        /**
         * Get {@link Presenter}.
         */
        public final Presenter getPresenter() {
            return mPresenter;
        }

        /**
         * Get {@link Presenter.ViewHolder}.
         */
        public final Presenter.ViewHolder getViewHolder() {
            return mHolder;
        }

        /**
         * Get currently bound object.
         */
        public final Object getItem() {
            return mItem;
        }

        /**
         * Get extra object associated with the view.  Developer can attach
         * any customized UI object in addition to {@link Presenter.ViewHolder}.
         * A typical use case is attaching an animator object.
         */
        public final Object getExtraObject() {
            return mExtraObject;
        }

        /**
         * Set extra object associated with the view.  Developer can attach
         * any customized UI object in addition to {@link Presenter.ViewHolder}.
         * A typical use case is attaching an animator object.
         */
        public void setExtraObject(Object object) {
            mExtraObject = object;
        }

        @Override
        public Object getFacet(Class<?> facetClass) {
            return mHolder.getFacet(facetClass);
        }

        ViewHolder(Presenter presenter, View view, Presenter.ViewHolder holder) {
            super(view);
            mPresenter = presenter;
            mHolder = holder;
        }
    }

    private ObjectAdapter.DataObserver mDataObserver = new ObjectAdapter.DataObserver() {
        @Override
        public void onChanged() {
            ItemBridgeAdapter.this.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            ItemBridgeAdapter.this.notifyItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            ItemBridgeAdapter.this.notifyItemRangeChanged(positionStart, itemCount, payload);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            ItemBridgeAdapter.this.notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            ItemBridgeAdapter.this.notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        public void onItemMoved(int fromPosition, int toPosition) {
            ItemBridgeAdapter.this.notifyItemMoved(fromPosition, toPosition);
        }
    };

    public ItemBridgeAdapter(ObjectAdapter adapter, PresenterSelector presenterSelector) {
        setAdapter(adapter);
        mPresenterSelector = presenterSelector;
    }

    public ItemBridgeAdapter(ObjectAdapter adapter) {
        this(adapter, null);
    }

    public ItemBridgeAdapter() {
    }

    /**
     * Sets the {@link ObjectAdapter}.
     */
    public void setAdapter(ObjectAdapter adapter) {
        if (adapter == mAdapter) {
            return;
        }
        if (mAdapter != null) {
            mAdapter.unregisterObserver(mDataObserver);
        }
        mAdapter = adapter;
        if (mAdapter == null) {
            notifyDataSetChanged();
            return;
        }

        mAdapter.registerObserver(mDataObserver);
        if (hasStableIds() != mAdapter.hasStableIds()) {
            setHasStableIds(mAdapter.hasStableIds());
        }
        notifyDataSetChanged();
    }

    /**
     * Changes Presenter that creates and binds the view.
     *
     * @param presenterSelector Presenter that creates and binds the view.
     */
    public void setPresenter(PresenterSelector presenterSelector) {
        mPresenterSelector = presenterSelector;
        notifyDataSetChanged();
    }

    /**
     * Sets the {@link Wrapper}.
     */
    public void setWrapper(Wrapper wrapper) {
        mWrapper = wrapper;
    }

    /**
     * Returns the {@link Wrapper}.
     */
    public Wrapper getWrapper() {
        return mWrapper;
    }

    void setFocusHighlight(FocusHighlightHandler listener) {
        mFocusHighlight = listener;
        if (DEBUG) Log.v(TAG, "setFocusHighlight " + mFocusHighlight);
    }

    /**
     * Clears the adapter.
     */
    public void clear() {
        setAdapter(null);
    }

    /**
     * Sets the presenter mapper array.
     */
    public void setPresenterMapper(ArrayList<Presenter> presenters) {
        mPresenters = presenters;
    }

    /**
     * Returns the presenter mapper array.
     */
    public ArrayList<Presenter> getPresenterMapper() {
        return mPresenters;
    }

    @Override
    public int getItemCount() {
        return mAdapter != null ? mAdapter.size() : 0;
    }

    @Override
    public int getItemViewType(int position) {
        PresenterSelector presenterSelector = mPresenterSelector != null
                ? mPresenterSelector : mAdapter.getPresenterSelector();
        Object item = mAdapter.get(position);
        Presenter presenter = presenterSelector.getPresenter(item);
        int type = mPresenters.indexOf(presenter);
        if (type < 0) {
            mPresenters.add(presenter);
            type = mPresenters.indexOf(presenter);
            if (DEBUG) Log.v(TAG, "getItemViewType added presenter " + presenter + " type " + type);
            onAddPresenter(presenter, type);
            if (mAdapterListener != null) {
                mAdapterListener.onAddPresenter(presenter, type);
            }
        }
        return type;
    }

    /**
     * Called when presenter is added to Adapter.
     */
    protected void onAddPresenter(Presenter presenter, int type) {
    }

    /**
     * Called when ViewHolder is created.
     */
    protected void onCreate(ViewHolder viewHolder) {
    }

    /**
     * Called when ViewHolder has been bound to data.
     */
    protected void onBind(ViewHolder viewHolder) {
    }

    /**
     * Called when ViewHolder has been unbound from data.
     */
    protected void onUnbind(ViewHolder viewHolder) {
    }

    /**
     * Called when ViewHolder has been attached to window.
     */
    protected void onAttachedToWindow(ViewHolder viewHolder) {
    }

    /**
     * Called when ViewHolder has been detached from window.
     */
    protected void onDetachedFromWindow(ViewHolder viewHolder) {
    }

    /**
     * {@link View.OnFocusChangeListener} that assigned in
     * {@link Presenter#onCreateViewHolder(ViewGroup)} may be chained, user should never change
     * {@link View.OnFocusChangeListener} after that.
     */
    @Override
    public final RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (DEBUG) Log.v(TAG, "onCreateViewHolder viewType " + viewType);
        Presenter presenter = mPresenters.get(viewType);
        Presenter.ViewHolder presenterVh;
        View view;
        if (mWrapper != null) {
            view = mWrapper.createWrapper(parent);
            presenterVh = presenter.onCreateViewHolder(parent);
            mWrapper.wrap(view, presenterVh.view);
        } else {
            presenterVh = presenter.onCreateViewHolder(parent);
            view = presenterVh.view;
        }
        ViewHolder viewHolder = new ViewHolder(presenter, view, presenterVh);
        onCreate(viewHolder);
        if (mAdapterListener != null) {
            mAdapterListener.onCreate(viewHolder);
        }
        View presenterView = viewHolder.mHolder.view;
        if (presenterView != null) {
            viewHolder.mFocusChangeListener.mChainedListener =
                    presenterView.getOnFocusChangeListener();
            presenterView.setOnFocusChangeListener(viewHolder.mFocusChangeListener);
        }
        if (mFocusHighlight != null) {
            mFocusHighlight.onInitializeView(view);
        }
        return viewHolder;
    }

    /**
     * Sets the AdapterListener.
     */
    public void setAdapterListener(AdapterListener listener) {
        mAdapterListener = listener;
    }

    @Override
    public final void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (DEBUG) Log.v(TAG, "onBindViewHolder position " + position);
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.mItem = mAdapter.get(position);

        viewHolder.mPresenter.onBindViewHolder(viewHolder.mHolder, viewHolder.mItem);

        onBind(viewHolder);
        if (mAdapterListener != null) {
            mAdapterListener.onBind(viewHolder);
        }
    }

    @Override
    public final  void onBindViewHolder(RecyclerView.ViewHolder holder, int position,
            List payloads) {
        if (DEBUG) Log.v(TAG, "onBindViewHolder position " + position);
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.mItem = mAdapter.get(position);

        viewHolder.mPresenter.onBindViewHolder(viewHolder.mHolder, viewHolder.mItem, payloads);

        onBind(viewHolder);
        if (mAdapterListener != null) {
            mAdapterListener.onBind(viewHolder, payloads);
        }
    }

    @Override
    public final void onViewRecycled(RecyclerView.ViewHolder holder) {
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.mPresenter.onUnbindViewHolder(viewHolder.mHolder);
        onUnbind(viewHolder);
        if (mAdapterListener != null) {
            mAdapterListener.onUnbind(viewHolder);
        }
        viewHolder.mItem = null;
    }

    @Override
    public final boolean onFailedToRecycleView(RecyclerView.ViewHolder holder) {
        onViewRecycled(holder);
        return false;
    }

    @Override
    public final void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        ViewHolder viewHolder = (ViewHolder) holder;
        onAttachedToWindow(viewHolder);
        if (mAdapterListener != null) {
            mAdapterListener.onAttachedToWindow(viewHolder);
        }
        viewHolder.mPresenter.onViewAttachedToWindow(viewHolder.mHolder);
    }

    @Override
    public final void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.mPresenter.onViewDetachedFromWindow(viewHolder.mHolder);
        onDetachedFromWindow(viewHolder);
        if (mAdapterListener != null) {
            mAdapterListener.onDetachedFromWindow(viewHolder);
        }
    }

    @Override
    public long getItemId(int position) {
        return mAdapter.getId(position);
    }

    @Override
    public FacetProvider getFacetProvider(int type) {
        return mPresenters.get(type);
    }
}
