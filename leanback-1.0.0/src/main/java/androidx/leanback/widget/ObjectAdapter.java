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

import android.database.Observable;

import androidx.annotation.RestrictTo;

/**
 * Base class adapter to be used in leanback activities.  Provides access to a data model and is
 * decoupled from the presentation of the items via {@link PresenterSelector}.
 */
public abstract class ObjectAdapter {

    /** Indicates that an id has not been set. */
    public static final int NO_ID = -1;

    /**
     * A DataObserver can be notified when an ObjectAdapter's underlying data
     * changes. Separate methods provide notifications about different types of
     * changes.
     */
    public static abstract class DataObserver {
        /**
         * Called whenever the ObjectAdapter's data has changed in some manner
         * outside of the set of changes covered by the other range-based change
         * notification methods.
         */
        public void onChanged() {
        }

        /**
         * Called when a range of items in the ObjectAdapter has changed. The
         * basic ordering and structure of the ObjectAdapter has not changed.
         *
         * @param positionStart The position of the first item that changed.
         * @param itemCount     The number of items changed.
         */
        public void onItemRangeChanged(int positionStart, int itemCount) {
            onChanged();
        }

        /**
         * Called when a range of items in the ObjectAdapter has changed. The
         * basic ordering and structure of the ObjectAdapter has not changed.
         *
         * @param positionStart The position of the first item that changed.
         * @param itemCount     The number of items changed.
         * @param payload       Optional parameter, use null to identify a "full" update.
         */
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            onChanged();
        }

        /**
         * Called when a range of items is inserted into the ObjectAdapter.
         *
         * @param positionStart The position of the first inserted item.
         * @param itemCount     The number of items inserted.
         */
        public void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        /**
         * Called when an item is moved from one position to another position
         *
         * @param fromPosition Previous position of the item.
         * @param toPosition   New position of the item.
         */
        public void onItemMoved(int fromPosition, int toPosition) {
            onChanged();
        }

        /**
         * Called when a range of items is removed from the ObjectAdapter.
         *
         * @param positionStart The position of the first removed item.
         * @param itemCount     The number of items removed.
         */
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }
    }

    private static final class DataObservable extends Observable<DataObserver> {

        DataObservable() {
        }

        public void notifyChanged() {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onChanged();
            }
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeChanged(positionStart, itemCount);
            }
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount, Object payload) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeChanged(positionStart, itemCount, payload);
            }
        }

        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeInserted(positionStart, itemCount);
            }
        }

        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeRemoved(positionStart, itemCount);
            }
        }

        public void notifyItemMoved(int positionStart, int toPosition) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemMoved(positionStart, toPosition);
            }
        }

        boolean hasObserver() {
            return mObservers.size() > 0;
        }
    }

    private final DataObservable mObservable = new DataObservable();
    private boolean mHasStableIds;
    private PresenterSelector mPresenterSelector;

    /**
     * Constructs an adapter with the given {@link PresenterSelector}.
     */
    public ObjectAdapter(PresenterSelector presenterSelector) {
        setPresenterSelector(presenterSelector);
    }

    /**
     * Constructs an adapter that uses the given {@link Presenter} for all items.
     */
    public ObjectAdapter(Presenter presenter) {
        setPresenterSelector(new SinglePresenterSelector(presenter));
    }

    /**
     * Constructs an adapter.
     */
    public ObjectAdapter() {
    }

    /**
     * Sets the presenter selector.  May not be null.
     */
    public final void setPresenterSelector(PresenterSelector presenterSelector) {
        if (presenterSelector == null) {
            throw new IllegalArgumentException("Presenter selector must not be null");
        }
        final boolean update = (mPresenterSelector != null);
        final boolean selectorChanged = update && mPresenterSelector != presenterSelector;

        mPresenterSelector = presenterSelector;

        if (selectorChanged) {
            onPresenterSelectorChanged();
        }
        if (update) {
            notifyChanged();
        }
    }

    /**
     * Called when {@link #setPresenterSelector(PresenterSelector)} is called
     * and the PresenterSelector differs from the previous one.
     */
    protected void onPresenterSelectorChanged() {
    }

    /**
     * Returns the presenter selector for this ObjectAdapter.
     */
    public final PresenterSelector getPresenterSelector() {
        return mPresenterSelector;
    }

    /**
     * Registers a DataObserver for data change notifications.
     */
    public final void registerObserver(DataObserver observer) {
        mObservable.registerObserver(observer);
    }

    /**
     * Unregisters a DataObserver for data change notifications.
     */
    public final void unregisterObserver(DataObserver observer) {
        mObservable.unregisterObserver(observer);
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public final boolean hasObserver() {
        return mObservable.hasObserver();
    }

    /**
     * Unregisters all DataObservers for this ObjectAdapter.
     */
    public final void unregisterAllObservers() {
        mObservable.unregisterAll();
    }

    /**
     * Notifies UI that some items has changed.
     *
     * @param positionStart Starting position of the changed items.
     * @param itemCount     Total number of items that changed.
     */
    public final void notifyItemRangeChanged(int positionStart, int itemCount) {
        mObservable.notifyItemRangeChanged(positionStart, itemCount);
    }

    /**
     * Notifies UI that some items has changed.
     *
     * @param positionStart Starting position of the changed items.
     * @param itemCount     Total number of items that changed.
     * @param payload       Optional parameter, use null to identify a "full" update.
     */
    public final void notifyItemRangeChanged(int positionStart, int itemCount, Object payload) {
        mObservable.notifyItemRangeChanged(positionStart, itemCount, payload);
    }

    /**
     * Notifies UI that new items has been inserted.
     *
     * @param positionStart Position where new items has been inserted.
     * @param itemCount     Count of the new items has been inserted.
     */
    final protected void notifyItemRangeInserted(int positionStart, int itemCount) {
        mObservable.notifyItemRangeInserted(positionStart, itemCount);
    }

    /**
     * Notifies UI that some items that has been removed.
     *
     * @param positionStart Starting position of the removed items.
     * @param itemCount     Total number of items that has been removed.
     */
    final protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
        mObservable.notifyItemRangeRemoved(positionStart, itemCount);
    }

    /**
     * Notifies UI that item at fromPosition has been moved to toPosition.
     *
     * @param fromPosition Previous position of the item.
     * @param toPosition   New position of the item.
     */
    protected final void notifyItemMoved(int fromPosition, int toPosition) {
        mObservable.notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * Notifies UI that the underlying data has changed.
     */
    final protected void notifyChanged() {
        mObservable.notifyChanged();
    }

    /**
     * Returns true if the item ids are stable across changes to the
     * underlying data.  When this is true, clients of the ObjectAdapter can use
     * {@link #getId(int)} to correlate Objects across changes.
     */
    public final boolean hasStableIds() {
        return mHasStableIds;
    }

    /**
     * Sets whether the item ids are stable across changes to the underlying
     * data.
     */
    public final void setHasStableIds(boolean hasStableIds) {
        boolean changed = mHasStableIds != hasStableIds;
        mHasStableIds = hasStableIds;

        if (changed) {
            onHasStableIdsChanged();
        }
    }

    /**
     * Called when {@link #setHasStableIds(boolean)} is called and the status
     * of stable ids has changed.
     */
    protected void onHasStableIdsChanged() {
    }

    /**
     * Returns the {@link Presenter} for the given item from the adapter.
     */
    public final Presenter getPresenter(Object item) {
        if (mPresenterSelector == null) {
            throw new IllegalStateException("Presenter selector must not be null");
        }
        return mPresenterSelector.getPresenter(item);
    }

    /**
     * Returns the number of items in the adapter.
     */
    public abstract int size();

    /**
     * Returns the item for the given position.
     */
    public abstract Object get(int position);

    /**
     * Returns the id for the given position.
     */
    public long getId(int position) {
        return NO_ID;
    }

    /**
     * Returns true if the adapter pairs each underlying data change with a call to notify and
     * false otherwise.
     */
    public boolean isImmediateNotifySupported() {
        return false;
    }
}
