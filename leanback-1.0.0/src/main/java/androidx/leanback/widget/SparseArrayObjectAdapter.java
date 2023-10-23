package androidx.leanback.widget;

import android.util.SparseArray;

/**
 * An {@link ObjectAdapter} implemented with a {@link android.util.SparseArray}.
 * This class maintains an array of objects where each object is associated
 * with an integer key which determines its order relative to other objects.
 */
public class SparseArrayObjectAdapter extends ObjectAdapter {
    private SparseArray<Object> mItems = new SparseArray<Object>();

    /**
     * Constructs an adapter with the given {@link PresenterSelector}.
     */
    public SparseArrayObjectAdapter(PresenterSelector presenterSelector) {
        super(presenterSelector);
    }

    /**
     * Constructs an adapter with the given {@link Presenter}.
     */
    public SparseArrayObjectAdapter(Presenter presenter) {
        super(presenter);
    }

    /**
     * Constructs an adapter.
     */
    public SparseArrayObjectAdapter() {
        super();
    }

    @Override
    public int size() {
        return mItems.size();
    }

    @Override
    public Object get(int position) {
        return mItems.valueAt(position);
    }

    /**
     * Returns the index for the given item in the adapter.
     *
     * @param item  The item to find in the array.
     * @return Index of the item, or a negative value if not found.
     */
    public int indexOf(Object item) {
        return mItems.indexOfValue(item);
    }

    /**
     * Returns the index for the given key in the adapter.
     *
     * @param key The key to find in the array.
     * @return Index of the item, or a negative value if not found.
     */
    public int indexOf(int key) {
        return mItems.indexOfKey(key);
    }

    /**
     * Notify that the content of a range of items changed. Note that this is
     * not same as items being added or removed.
     *
     * @param positionStart The position of first item that has changed.
     * @param itemCount The count of how many items have changed.
     */
    public void notifyArrayItemRangeChanged(int positionStart, int itemCount) {
        notifyItemRangeChanged(positionStart, itemCount);
    }

    /**
     * Sets the item for the given key.
     *
     * @param key The key associated with the item.
     * @param item The item associated with the key.
     */
    public void set(int key, Object item) {
        int index = mItems.indexOfKey(key);
        if (index >= 0) {
            if (mItems.valueAt(index) != item) {
                mItems.setValueAt(index, item);
                notifyItemRangeChanged(index, 1);
            }
        } else {
            mItems.append(key, item);
            index = mItems.indexOfKey(key);
            notifyItemRangeInserted(index, 1);
        }
    }

    /**
     * Clears the given key and associated item from the adapter.
     *
     * @param key The key to be cleared.
     */
    public void clear(int key) {
        int index = mItems.indexOfKey(key);
        if (index >= 0) {
            mItems.removeAt(index);
            notifyItemRangeRemoved(index, 1);
        }
    }

    /**
     * Removes all items from this adapter, leaving it empty.
     */
    public void clear() {
        final int itemCount = mItems.size();
        if (itemCount == 0) {
            return;
        }
        mItems.clear();
        notifyItemRangeRemoved(0, itemCount);
    }

    /**
     * Returns the object for the given key, or null if no mapping for that key exists.
     */
    public Object lookup(int key) {
        return mItems.get(key);
    }

    @Override
    public boolean isImmediateNotifySupported() {
        return true;
    }
}