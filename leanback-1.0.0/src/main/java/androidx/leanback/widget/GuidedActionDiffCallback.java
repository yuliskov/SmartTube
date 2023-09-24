/*
 * Copyright 2017 The Android Open Source Project
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

import android.text.TextUtils;

import androidx.annotation.NonNull;

/**
 * DiffCallback used for GuidedActions, see {@link
 * androidx.leanback.app.GuidedStepSupportFragment#setActionsDiffCallback(DiffCallback)}.
 */
public class GuidedActionDiffCallback extends DiffCallback<GuidedAction> {

    static final GuidedActionDiffCallback sInstance = new GuidedActionDiffCallback();

    /**
     * Returns the singleton GuidedActionDiffCallback.
     * @return The singleton GuidedActionDiffCallback.
     */
    public static GuidedActionDiffCallback getInstance() {
        return sInstance;
    }

    @Override
    public boolean areItemsTheSame(@NonNull GuidedAction oldItem, @NonNull GuidedAction newItem) {
        if (oldItem == null) {
            return newItem == null;
        } else if (newItem == null) {
            return false;
        }
        return oldItem.getId() == newItem.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull GuidedAction oldItem,
            @NonNull GuidedAction newItem) {
        if (oldItem == null) {
            return newItem == null;
        } else if (newItem == null) {
            return false;
        }
        return oldItem.getCheckSetId() == newItem.getCheckSetId()
                && oldItem.mActionFlags == newItem.mActionFlags
                && TextUtils.equals(oldItem.getTitle(), newItem.getTitle())
                && TextUtils.equals(oldItem.getDescription(), newItem.getDescription())
                && oldItem.getInputType() == newItem.getInputType()
                && TextUtils.equals(oldItem.getEditTitle(), newItem.getEditTitle())
                && TextUtils.equals(oldItem.getEditDescription(), newItem.getEditDescription())
                && oldItem.getEditInputType() == newItem.getEditInputType()
                && oldItem.getDescriptionEditInputType() == newItem.getDescriptionEditInputType();
    }
}
