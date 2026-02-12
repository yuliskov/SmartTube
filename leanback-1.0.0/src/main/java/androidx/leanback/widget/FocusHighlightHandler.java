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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.view.View;

import androidx.annotation.RestrictTo;

/**
 * Interface for highlighting the item that has focus.
 */
interface FocusHighlightHandler {
    /**
     * Called when an item gains or loses focus.
     * @hide
     *
     * @param view The view whose focus is changing.
     * @param hasFocus True if focus is gained; false otherwise.
     */
    @RestrictTo(LIBRARY_GROUP)
    void onItemFocused(View view, boolean hasFocus);

    /**
     * Called when the view is being created.
     */
    void onInitializeView(View view);
}
