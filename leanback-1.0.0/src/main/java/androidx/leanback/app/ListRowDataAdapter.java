package androidx.leanback.app;

import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Row;

/**
 * Wrapper class for {@link ObjectAdapter} used by {@link BrowseFragment} to initialize
 * {@link RowsFragment}. We use invisible rows to represent
 * {@link androidx.leanback.widget.DividerRow},
 * {@link androidx.leanback.widget.SectionRow} and
 * {@link androidx.leanback.widget.PageRow} in RowsFragment. In case we have an
 * invisible row at the end of a RowsFragment, it creates a jumping effect as the layout manager
 * thinks there are items even though they're invisible. This class takes care of filtering out
 * the invisible rows at the end. In case the data inside the adapter changes, it adjusts the
 * bounds to reflect the latest data.
 * {@link #detach()} must be called to release DataObserver from Adapter.
 */
class ListRowDataAdapter extends ObjectAdapter {
    public static final int ON_ITEM_RANGE_CHANGED = 2;
    public static final int ON_ITEM_RANGE_INSERTED = 4;
    public static final int ON_ITEM_RANGE_REMOVED = 8;
    public static final int ON_CHANGED = 16;

    private final ObjectAdapter mAdapter;
    int mLastVisibleRowIndex;
    final DataObserver mDataObserver;

    public ListRowDataAdapter(ObjectAdapter adapter) {
        super(adapter.getPresenterSelector());
        this.mAdapter = adapter;
        initialize();

        // If an user implements its own ObjectAdapter, notification corresponding to data
        // updates can be batched e.g. remove, add might be followed by notifyRemove, notifyAdd.
        // But underlying data would have changed during the notifyRemove call by the previous add
        // operation. To handle this case, we use QueueBasedDataObserver which forces
        // recyclerview to do a full data refresh after each update operation.
        if (adapter.isImmediateNotifySupported()) {
            mDataObserver = new SimpleDataObserver();
        } else {
            mDataObserver = new QueueBasedDataObserver();
        }
        attach();
    }

    void detach() {
        mAdapter.unregisterObserver(mDataObserver);
    }

    void attach() {
        initialize();
        mAdapter.registerObserver(mDataObserver);
    }

    void initialize() {
        mLastVisibleRowIndex = -1;
        int i = mAdapter.size() - 1;
        while (i >= 0) {
            Row item = (Row) mAdapter.get(i);
            if (item.isRenderedAsRowView()) {
                mLastVisibleRowIndex = i;
                break;
            }
            i--;
        }
    }

    @Override
    public int size() {
        return mLastVisibleRowIndex + 1;
    }

    @Override
    public Object get(int index) {
        return mAdapter.get(index);
    }

    void doNotify(int eventType, int positionStart, int itemCount) {
        switch (eventType) {
            case ON_ITEM_RANGE_CHANGED:
                notifyItemRangeChanged(positionStart, itemCount);
                break;
            case ON_ITEM_RANGE_INSERTED:
                notifyItemRangeInserted(positionStart, itemCount);
                break;
            case ON_ITEM_RANGE_REMOVED:
                notifyItemRangeRemoved(positionStart, itemCount);
                break;
            case ON_CHANGED:
                notifyChanged();
                break;
            default:
                throw new IllegalArgumentException("Invalid event type " + eventType);
        }
    }

    private class SimpleDataObserver extends DataObserver {

        SimpleDataObserver() {
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            if (positionStart <= mLastVisibleRowIndex) {
                onEventFired(ON_ITEM_RANGE_CHANGED, positionStart,
                        Math.min(itemCount, mLastVisibleRowIndex - positionStart + 1));
            }
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            if (positionStart <= mLastVisibleRowIndex) {
                mLastVisibleRowIndex += itemCount;
                onEventFired(ON_ITEM_RANGE_INSERTED, positionStart, itemCount);
                return;
            }

            int lastVisibleRowIndex = mLastVisibleRowIndex;
            initialize();
            if (mLastVisibleRowIndex > lastVisibleRowIndex) {
                int totalItems = mLastVisibleRowIndex - lastVisibleRowIndex;
                onEventFired(ON_ITEM_RANGE_INSERTED, lastVisibleRowIndex + 1, totalItems);
            }
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            if (positionStart + itemCount - 1 < mLastVisibleRowIndex) {
                mLastVisibleRowIndex -= itemCount;
                onEventFired(ON_ITEM_RANGE_REMOVED, positionStart, itemCount);
                return;
            }

            int lastVisibleRowIndex = mLastVisibleRowIndex;
            initialize();
            int totalItems = lastVisibleRowIndex - mLastVisibleRowIndex;
            if (totalItems > 0) {
                onEventFired(ON_ITEM_RANGE_REMOVED,
                        Math.min(mLastVisibleRowIndex + 1, positionStart),
                        totalItems);
            }
        }

        @Override
        public void onChanged() {
            initialize();
            onEventFired(ON_CHANGED, -1, -1);
        }

        protected void onEventFired(int eventType, int positionStart, int itemCount) {
            doNotify(eventType, positionStart, itemCount);
        }
    }


    /**
     * When using custom {@link ObjectAdapter}, it's possible that the user may make multiple
     * changes to the underlying data at once. The notifications about those updates may be
     * batched and the underlying data would have changed to reflect latest updates as opposed
     * to intermediate changes. In order to force RecyclerView to refresh the view with access
     * only to the final data, we call notifyChange().
     */
    private class QueueBasedDataObserver extends DataObserver {

        QueueBasedDataObserver() {
        }

        @Override
        public void onChanged() {
            initialize();
            notifyChanged();
        }
    }
}
