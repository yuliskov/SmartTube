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

import android.database.Cursor;
import android.util.LruCache;

import androidx.leanback.database.CursorMapper;

/**
 * An {@link ObjectAdapter} implemented with a {@link Cursor}.
 */
public class CursorObjectAdapter extends ObjectAdapter {
    private static final int CACHE_SIZE = 100;
    private Cursor mCursor;
    private CursorMapper mMapper;
    private final LruCache<Integer, Object> mItemCache = new LruCache<Integer, Object>(CACHE_SIZE);

    /**
     * Constructs an adapter with the given {@link PresenterSelector}.
     */
    public CursorObjectAdapter(PresenterSelector presenterSelector) {
        super(presenterSelector);
    }

    /**
     * Constructs an adapter that uses the given {@link Presenter} for all items.
     */
    public CursorObjectAdapter(Presenter presenter) {
        super(presenter);
    }

    /**
     * Constructs an adapter.
     */
    public CursorObjectAdapter() {
        super();
    }

    /**
     * Changes the underlying cursor to a new cursor. If there is
     * an existing cursor it will be closed if it is different than the new
     * cursor.
     *
     * @param cursor The new cursor to be used.
     */
    public void changeCursor(Cursor cursor) {
        if (cursor == mCursor) {
            return;
        }
        if (mCursor != null) {
            mCursor.close();
        }
        mCursor = cursor;
        mItemCache.trimToSize(0);
        onCursorChanged();
    }

    /**
     * Swap in a new Cursor, returning the old Cursor. Unlike changeCursor(Cursor),
     * the returned old Cursor is not closed.
     *
     * @param cursor The new cursor to be used.
     */
    public Cursor swapCursor(Cursor cursor) {
        if (cursor == mCursor) {
            return mCursor;
        }
        Cursor oldCursor = mCursor;
        mCursor = cursor;
        mItemCache.trimToSize(0);
        onCursorChanged();
        return oldCursor;
    }

    /**
     * Called whenever the cursor changes.
     */
    protected void onCursorChanged() {
        notifyChanged();
    }

    /**
     * Returns the {@link Cursor} backing the adapter.
     */
     public final Cursor getCursor() {
        return mCursor;
    }

    /**
     * Sets the {@link CursorMapper} used to convert {@link Cursor} rows into
     * Objects.
     */
    public final void setMapper(CursorMapper mapper) {
        boolean changed = mMapper != mapper;
        mMapper = mapper;

        if (changed) {
            onMapperChanged();
        }
    }

    /**
     * Called when {@link #setMapper(CursorMapper)} is called and a different
     * mapper is provided.
     */
    protected void onMapperChanged() {
    }

    /**
     * Returns the {@link CursorMapper} used to convert {@link Cursor} rows into
     * Objects.
     */
    public final CursorMapper getMapper() {
        return mMapper;
    }

    @Override
    public int size() {
        if (mCursor == null) {
            return 0;
        }
        return mCursor.getCount();
    }

    @Override
    public Object get(int index) {
        if (mCursor == null) {
            return null;
        }
        if (!mCursor.moveToPosition(index)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        Object item = mItemCache.get(index);
        if (item != null) {
            return item;
        }
        item = mMapper.convert(mCursor);
        mItemCache.put(index, item);
        return item;
    }

    /**
     * Closes this adapter, closing the backing {@link Cursor} as well.
     */
    public void close() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    /**
     * Returns true if the adapter, and hence the backing {@link Cursor}, is closed; false
     * otherwise.
     */
    public boolean isClosed() {
        return mCursor == null || mCursor.isClosed();
    }

    /**
     * Removes an item from the cache. This will force the item to be re-read
     * from the data source the next time {@link #get(int)} is called.
     */
    protected final void invalidateCache(int index) {
        mItemCache.remove(index);
    }

    /**
     * Removes {@code count} items starting at {@code index}.
     */
    protected final void invalidateCache(int index, int count) {
        for (int limit = count + index; index < limit; index++) {
            invalidateCache(index);
        }
    }

    @Override
    public boolean isImmediateNotifySupported() {
        return true;
    }
}
