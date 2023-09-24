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
 * Interface for receiving notification when an item view holder is clicked.
 */
public interface BaseOnItemViewClickedListener<T> {

    /**
     * Called when an item inside a row gets clicked.
     * @param itemViewHolder The view holder of the item that is clicked.
     * @param item The item that is currently selected.
     * @param rowViewHolder The view holder of the row which the clicked item belongs to.
     * @param row The row which the clicked item belongs to.
     */
    void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                              RowPresenter.ViewHolder rowViewHolder, T row);
}
