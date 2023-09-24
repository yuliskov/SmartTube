/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.widget;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

/**
 * Callback that informs {@link ArrayObjectAdapter} how to compute list updates when using
 * {@link DiffUtil} in {@link ArrayObjectAdapter#setItems(List,
 * DiffCallback)} method.
 * <p>
 * The {@link ArrayObjectAdapter#setItems(List,
 * DiffCallback)} method will pass items from different
 * lists to this callback in order to implement
 * the {@link DiffUtil.Callback} it uses to compute differences between
 * lists.
 *
 * @param <Value> Type of items to compare.
 */
public abstract class DiffCallback<Value> {
    /**
     * Called to decide whether two objects represent the same item.
     *
     * @param oldItem The item in the old list.
     * @param newItem The item in the new list.
     * @return True if the two items represent the same object or false if they are different.
     * @see DiffUtil.Callback#areItemsTheSame(int, int)
     */
    public abstract boolean areItemsTheSame(@NonNull Value oldItem, @NonNull Value newItem);

    /**
     * Called to decide whether two items have the same data. This information is used to detect if
     * the contents of an item have changed.
     *
     * @param oldItem The item in the old list.
     * @param newItem The item in the new list.
     * @return True if the contents of the items are the same or false if they are different.
     * @see DiffUtil.Callback#areContentsTheSame(int, int)
     */
    public abstract boolean areContentsTheSame(@NonNull Value oldItem, @NonNull Value newItem);

    /**
     * Called to get a change payload between an old and new version of an item.
     *
     * @see DiffUtil.Callback#getChangePayload(int, int)
     */
    @SuppressWarnings("WeakerAccess")
    public Object getChangePayload(@NonNull Value oldItem, @NonNull Value newItem) {
        return null;
    }
}
