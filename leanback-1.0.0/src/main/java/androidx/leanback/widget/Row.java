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

import static androidx.leanback.widget.ObjectAdapter.NO_ID;

/**
 * The base class for all rows.  A commonly used subclass is the {@link ListRow}.  Custom
 * subclasses may define other types of rows.
 */
public class Row {

    private static final int FLAG_ID_USE_MASK = 1;
    private static final int FLAG_ID_USE_HEADER = 1;
    private static final int FLAG_ID_USE_ID = 0;

    private int mFlags = FLAG_ID_USE_HEADER;
    private HeaderItem mHeaderItem;
    private long mId = NO_ID;

    /**
     * Constructor for a Row.
     *
     * @param id The id of the row.
     * @param headerItem The {@link HeaderItem} for this Row, or null if there
     *        is no header.
     */
    public Row(long id, HeaderItem headerItem) {
        setId(id);
        setHeaderItem(headerItem);
    }

    /**
     * Constructor for a Row.
     *
     * @param headerItem The {@link HeaderItem} for this Row, or null if there
     *        is no header.
     */
    public Row(HeaderItem headerItem) {
        setHeaderItem(headerItem);
    }

    /**
     * Constructor for a Row.
     */
    public Row() {
    }

    /**
     * Returns the {@link HeaderItem} that represents metadata for the row.
     *
     * @return The HeaderItem for this row, or null if unset.
     */
    public final HeaderItem getHeaderItem() {
        return mHeaderItem;
    }

    /**
     * Sets the {@link HeaderItem} that represents metadata for the row.
     *
     * @param headerItem The HeaderItem for this Row, or null if there is no
     *        header.
     */
    public final void setHeaderItem(HeaderItem headerItem) {
        mHeaderItem = headerItem;
    }

    /**
     * Sets the id for this row.
     *
     * @param id The id of the row.
     */
    public final void setId(long id) {
        mId = id;
        setFlags(FLAG_ID_USE_ID, FLAG_ID_USE_MASK);
    }

    /**
     * Returns a unique identifier for this row. This id can come from one of
     * three places:
     * <ul>
     *   <li>If {@link #setId(long)} is ever called on this row, it will return
     *   this id.
     *   <li>If {@link #setId(long)} has not been called but the header item is
     *   not null, the result of {@link HeaderItem#getId()} is returned.
     *   <li>Otherwise {@link ObjectAdapter#NO_ID NO_ID} is returned.
     * </ul>
     */
    public final long getId() {
        if ( (mFlags & FLAG_ID_USE_MASK) == FLAG_ID_USE_HEADER) {
            HeaderItem header = getHeaderItem();
            if (header != null) {
                return header.getId();
            }
            return NO_ID;
        } else {
            return mId;
        }
    }

    final void setFlags(int flags, int mask) {
        mFlags = (mFlags & ~mask) | (flags & mask);
    }

    final int getFlags() {
        return mFlags;
    }

    /**
     * Returns true if this Row can be rendered in a visible row view, false otherwise.  For example
     * {@link ListRow} is rendered by {@link ListRowPresenter}.  {@link PageRow},
     * {@link SectionRow}, {@link DividerRow} are rendered as invisible row views.
     * @return True if this Row can be rendered in a visible row view, false otherwise.
     */
    public boolean isRenderedAsRowView() {
        return true;
    }
}
