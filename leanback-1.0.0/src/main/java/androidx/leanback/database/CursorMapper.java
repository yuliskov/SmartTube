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
package androidx.leanback.database;

import android.database.Cursor;

/**
 * Abstract class used to convert the current {@link Cursor} row to a single
 * object.
 */
public abstract class CursorMapper {

    private Cursor mCursor;

    /**
     * Called once when the associated {@link Cursor} is changed. A subclass
     * should bind column indexes to column names in this method. This method is
     * not intended to be called outside of CursorMapper.
     */
    protected abstract void bindColumns(Cursor cursor);

    /**
     * A subclass should implement this method to create a single object using
     * binding information. This method is not intended to be called
     * outside of CursorMapper.
     */
    protected abstract Object bind(Cursor cursor);

    /**
     * Convert a {@link Cursor} at its current position to an Object.
     */
    public Object convert(Cursor cursor) {
        if (cursor != mCursor) {
            mCursor = cursor;
            bindColumns(mCursor);
        }
        return bind(mCursor);
    }
}
