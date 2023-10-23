/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.view.View;
import android.view.ViewGroup;

/**
 * Interface for receiving notification when a child of this
 * ViewGroup has been laid out.
 */
public interface OnChildLaidOutListener {
    /**
     * Callback method to be invoked when a child of this ViewGroup has been
     * added to the view hierarchy and has been laid out.
     *
     * @param parent The ViewGroup where the layout happened.
     * @param view The view within the ViewGroup that was laid out.
     * @param position The position of the view in the adapter.
     * @param id The id of the child.
     */
    void onChildLaidOut(ViewGroup parent, View view, int position, long id);
}
