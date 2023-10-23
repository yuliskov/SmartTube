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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * An overview {@link Row} for a details fragment. This row consists of an image, a
 * description view, and optionally a series of {@link Action}s that can be taken for
 * the item.
 *
 * <h3>Actions</h3>
 * Application uses {@link #setActionsAdapter(ObjectAdapter)} to set actions on the overview
 * row.  {@link SparseArrayObjectAdapter} is recommended for easily updating actions while
 * maintaining the order.  The application can add or remove actions on the UI thread after the
 * row is bound to a view.
 *
 * <h3>Updating main item</h3>
 * After the row is bound to a view, the application may call {@link #setItem(Object)}
 * on UI thread and the view will be updated.
 *
 * <h3>Updating image</h3>
 * After the row is bound to view, the application may change the image by calling {@link
 * #setImageBitmap(Context, Bitmap)} or {@link #setImageDrawable(Drawable)} on the UI thread,
 * and the view will be updated.
 */
public class DetailsOverviewRow extends Row {

    /**
     * Listener for changes of DetailsOverviewRow.
     */
    public static class Listener {

        /**
         * Called when DetailsOverviewRow has changed image drawable.
         */
        public void onImageDrawableChanged(DetailsOverviewRow row) {
        }

        /**
         * Called when DetailsOverviewRow has changed main item.
         */
        public void onItemChanged(DetailsOverviewRow row) {
        }

        /**
         * Called when DetailsOverviewRow has changed actions adapter.
         */
        public void onActionsAdapterChanged(DetailsOverviewRow row) {
        }
    }

    private Object mItem;
    private Drawable mImageDrawable;
    private boolean mImageScaleUpAllowed = true;
    private ArrayList<WeakReference<Listener>> mListeners;
    private PresenterSelector mDefaultActionPresenter = new ActionPresenterSelector();
    private ObjectAdapter mActionsAdapter = new ArrayObjectAdapter(mDefaultActionPresenter);

    /**
     * Constructor for a DetailsOverviewRow.
     *
     * @param item The main item for the details page.
     */
    public DetailsOverviewRow(Object item) {
        super(null);
        mItem = item;
        verify();
    }

    /**
     * Adds listener for the details page.
     */
    final void addListener(Listener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<WeakReference<Listener>>();
        } else {
            for (int i = 0; i < mListeners.size();) {
                Listener l = mListeners.get(i).get();
                if (l == null) {
                    mListeners.remove(i);
                } else {
                    if (l == listener) {
                        return;
                    }
                    i++;
                }
            }
        }
        mListeners.add(new WeakReference<Listener>(listener));
    }

    /**
     * Removes listener of the details page.
     */
    final void removeListener(Listener listener) {
        if (mListeners != null) {
            for (int i = 0; i < mListeners.size();) {
                Listener l = mListeners.get(i).get();
                if (l == null) {
                    mListeners.remove(i);
                } else {
                    if (l == listener) {
                        mListeners.remove(i);
                        return;
                    }
                    i++;
                }
            }
        }
    }

    /**
     * Notifies listeners for main item change on UI thread.
     */
    final void notifyItemChanged() {
        if (mListeners != null) {
            for (int i = 0; i < mListeners.size();) {
                Listener l = mListeners.get(i).get();
                if (l == null) {
                    mListeners.remove(i);
                } else {
                    l.onItemChanged(this);
                    i++;
                }
            }
        }
    }

    /**
     * Notifies listeners for image related change on UI thread.
     */
    final void notifyImageDrawableChanged() {
        if (mListeners != null) {
            for (int i = 0; i < mListeners.size();) {
                Listener l = mListeners.get(i).get();
                if (l == null) {
                    mListeners.remove(i);
                } else {
                    l.onImageDrawableChanged(this);
                    i++;
                }
            }
        }
    }

    /**
     * Notifies listeners for actions adapter changed on UI thread.
     */
    final void notifyActionsAdapterChanged() {
        if (mListeners != null) {
            for (int i = 0; i < mListeners.size();) {
                Listener l = mListeners.get(i).get();
                if (l == null) {
                    mListeners.remove(i);
                } else {
                    l.onActionsAdapterChanged(this);
                    i++;
                }
            }
        }
    }

    /**
     * Returns the main item for the details page.
     */
    public final Object getItem() {
        return mItem;
    }

    /**
     * Sets the main item for the details page.  Must be called on UI thread after
     * row is bound to view.
     */
    public final void setItem(Object item) {
        if (item != mItem) {
            mItem = item;
            notifyItemChanged();
        }
    }

    /**
     * Sets a drawable as the image of this details overview.  Must be called on UI thread
     * after row is bound to view.
     *
     * @param drawable The drawable to set.
     */
    public final void setImageDrawable(Drawable drawable) {
        if (mImageDrawable != drawable) {
            mImageDrawable = drawable;
            notifyImageDrawableChanged();
        }
    }

    /**
     * Sets a Bitmap as the image of this details overview.  Must be called on UI thread
     * after row is bound to view.
     *
     * @param context The context to retrieve display metrics from.
     * @param bm The bitmap to set.
     */
    public final void setImageBitmap(Context context, Bitmap bm) {
        mImageDrawable = new BitmapDrawable(context.getResources(), bm);
        notifyImageDrawableChanged();
    }

    /**
     * Returns the image drawable of this details overview.
     *
     * @return The overview's image drawable, or null if no drawable has been
     *         assigned.
     */
    public final Drawable getImageDrawable() {
        return mImageDrawable;
    }

    /**
     * Allows or disallows scaling up of images.
     * Images will always be scaled down if necessary.  Must be called on UI thread
     * after row is bound to view.
     */
    public void setImageScaleUpAllowed(boolean allowed) {
        if (allowed != mImageScaleUpAllowed) {
            mImageScaleUpAllowed = allowed;
            notifyImageDrawableChanged();
        }
    }

    /**
     * Returns true if the image may be scaled up; false otherwise.
     */
    public boolean isImageScaleUpAllowed() {
        return mImageScaleUpAllowed;
    }

    /**
     * Returns the actions adapter.  Throws ClassCastException if the current
     * actions adapter is not an instance of {@link ArrayObjectAdapter}.
     */
    private ArrayObjectAdapter getArrayObjectAdapter() {
        return (ArrayObjectAdapter) mActionsAdapter;
    }

    /**
     * Adds an Action to the overview. It will throw ClassCastException if the current actions
     * adapter is not an instance of {@link ArrayObjectAdapter}. Must be called on the UI thread.
     *
     * @param action The Action to add.
     * @deprecated Use {@link #setActionsAdapter(ObjectAdapter)} and {@link #getActionsAdapter()}
     */
    @Deprecated
    public final void addAction(Action action) {
        getArrayObjectAdapter().add(action);
    }

    /**
     * Adds an Action to the overview at the specified position. It will throw ClassCastException if
     * current actions adapter is not an instance of f{@link ArrayObjectAdapter}. Must be called
     * on the UI thread.
     *
     * @param pos The position to insert the Action.
     * @param action The Action to add.
     * @deprecated Use {@link #setActionsAdapter(ObjectAdapter)} and {@link #getActionsAdapter()}
     */
    @Deprecated
    public final void addAction(int pos, Action action) {
        getArrayObjectAdapter().add(pos, action);
    }

    /**
     * Removes the given Action from the overview. It will throw ClassCastException if current
     * actions adapter is not {@link ArrayObjectAdapter}. Must be called on UI thread.
     *
     * @param action The Action to remove.
     * @return true if the overview contained the specified Action.
     * @deprecated Use {@link #setActionsAdapter(ObjectAdapter)} and {@link #getActionsAdapter()}
     */
    @Deprecated
    public final boolean removeAction(Action action) {
        return getArrayObjectAdapter().remove(action);
    }

    /**
     * Returns a read-only view of the list of Actions of this details overview. It will throw
     * ClassCastException if current actions adapter is not {@link ArrayObjectAdapter}. Must be
     * called on UI thread.
     *
     * @return An unmodifiable view of the list of Actions.
     * @deprecated Use {@link #setActionsAdapter(ObjectAdapter)} and {@link #getActionsAdapter()}
     */
    @Deprecated
    public final List<Action> getActions() {
        return getArrayObjectAdapter().unmodifiableList();
    }

    /**
     * Returns the {@link ObjectAdapter} for actions.
     */
    public final ObjectAdapter getActionsAdapter() {
        return mActionsAdapter;
    }

    /**
     * Sets the {@link ObjectAdapter} for actions.  A default {@link PresenterSelector} will be
     * attached to the adapter if it doesn't have one.
     *
     * @param adapter  Adapter for actions.
     */
    public final void setActionsAdapter(ObjectAdapter adapter) {
        if (adapter != mActionsAdapter) {
            mActionsAdapter = adapter;
            if (mActionsAdapter.getPresenterSelector() == null) {
                mActionsAdapter.setPresenterSelector(mDefaultActionPresenter);
            }
            notifyActionsAdapterChanged();
        }
    }

    /**
     * Returns the Action associated with the given keycode, or null if no associated action exists.
     */
    public Action getActionForKeyCode(int keyCode) {
        ObjectAdapter adapter = getActionsAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.size(); i++) {
                Action action = (Action) adapter.get(i);
                if (action.respondsToKeyCode(keyCode)) {
                    return action;
                }
            }
        }
        return null;
    }

    private void verify() {
        if (mItem == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
    }
}
