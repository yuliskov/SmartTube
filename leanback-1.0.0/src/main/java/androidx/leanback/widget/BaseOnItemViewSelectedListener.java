/*
 * Copyright (C) 2016 The Android Open Source Project
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

/**
 * Interface for receiving notification when a row or item becomes selected. The concept of
 * current selection is different than focus.  A row or item can be selected without having focus;
 * for example, when a row header view gains focus then the corresponding row view becomes selected.
 */
public interface BaseOnItemViewSelectedListener<T> {

    /**
     * Called when a row or a new item becomes selected.
     * <p>
     * For a non {@link ListRow} case, parameter item may be null.  Event is fired when
     * selection changes between rows, regardless if row view has focus or not.
     * <p>
     * For a {@link ListRow} case, parameter item is null if the list row is empty.
     * </p>
     * <p>
     * In the case of a grid, the row parameter is always null.
     * </p>
     * <li>
     * Row has focus: event is fired when focus changes between children of the row.
     * </li>
     * <li>
     * No row has focus: the event is fired with the currently selected row and last
     * focused item in the row.
     * </li>
     *
     * @param itemViewHolder The view holder of the item that is currently selected.
     * @param item The item that is currently selected.
     * @param rowViewHolder The view holder of the row that is currently selected.
     * @param row The row that is currently selected.
     */
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                               RowPresenter.ViewHolder rowViewHolder, T row);
}
