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

import androidx.leanback.widget.ItemAlignmentFacet.ItemAlignmentDef;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Interface for receiving notification when a child of this ViewGroup has been selected.
 * There are two methods:
 * <li>
 *     {link {@link #onChildViewHolderSelected(RecyclerView, RecyclerView.ViewHolder, int, int)}}
 *     is called when the view holder is about to be selected.  The listener could change size
 *     of the view holder in this callback.
 * </li>
 * <li>
 *     {link {@link #onChildViewHolderSelectedAndPositioned(RecyclerView, RecyclerView.ViewHolder,
 *     int, int)} is called when view holder has been selected and laid out in RecyclerView.
 *
 * </li>
 */
public abstract class OnChildViewHolderSelectedListener {
    /**
     * Callback method to be invoked when a child of this ViewGroup has been selected. Listener
     * might change the size of the child and the position of the child is not finalized. To get
     * the final layout position of child, overide {@link #onChildViewHolderSelectedAndPositioned(
     * RecyclerView, RecyclerView.ViewHolder, int, int)}.
     *
     * @param parent The RecyclerView where the selection happened.
     * @param child The ViewHolder within the RecyclerView that is selected, or null if no
     *        view is selected.
     * @param position The position of the view in the adapter, or NO_POSITION
     *        if no view is selected.
     * @param subposition The index of which {@link ItemAlignmentDef} being used,
     *                    0 if there is no ItemAlignmentDef defined for the item.
     */
    public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child,
            int position, int subposition) {
    }

    /**
     * Callback method to be invoked when a child of this ViewGroup has been selected and
     * positioned.
     *
     * @param parent The RecyclerView where the selection happened.
     * @param child The ViewHolder within the RecyclerView that is selected, or null if no
     *        view is selected.
     * @param position The position of the view in the adapter, or NO_POSITION
     *        if no view is selected.
     * @param subposition The index of which {@link ItemAlignmentDef} being used,
     *                    0 if there is no ItemAlignmentDef defined for the item.
     */
    public void onChildViewHolderSelectedAndPositioned(RecyclerView parent,
            RecyclerView.ViewHolder child, int position, int subposition) {
    }

}
