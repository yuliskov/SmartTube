/*
 * Copyright 2018 The Android Open Source Project
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


package androidx.fragment.app;

import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelStore;

import java.util.Collection;
import java.util.Map;

/**
 * FragmentManagerNonConfig stores the retained instance fragments across
 * activity recreation events.
 *
 * <p>Apps should treat objects of this type as opaque, returned by
 * and passed to the state save and restore process for fragments in
 * {@link FragmentController#retainNestedNonConfig()} and
 * {@link FragmentController#restoreAllState(Parcelable, FragmentManagerNonConfig)}.</p>
 *
 * @deprecated Have your {@link FragmentHostCallback} implement
 * {@link androidx.lifecycle.ViewModelStoreOwner} to automatically retain the Fragment's
 * non configuration state.
 */
@Deprecated
public class FragmentManagerNonConfig {
    private final @Nullable Collection<Fragment> mFragments;
    private final @Nullable Map<String, FragmentManagerNonConfig> mChildNonConfigs;
    private final @Nullable Map<String, ViewModelStore> mViewModelStores;

    FragmentManagerNonConfig(@Nullable Collection<Fragment> fragments,
            @Nullable Map<String, FragmentManagerNonConfig> childNonConfigs,
            @Nullable Map<String, ViewModelStore> viewModelStores) {
        mFragments = fragments;
        mChildNonConfigs = childNonConfigs;
        mViewModelStores = viewModelStores;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isRetaining(Fragment f) {
        if (mFragments == null) {
            return false;
        }
        return mFragments.contains(f);
    }

    /**
     * @return the retained instance fragments returned by a FragmentManager
     */
    @Nullable
    Collection<Fragment> getFragments() {
        return mFragments;
    }

    /**
     * @return the FragmentManagerNonConfigs from any applicable fragment's child FragmentManager
     */
    @Nullable
    Map<String, FragmentManagerNonConfig> getChildNonConfigs() {
        return mChildNonConfigs;
    }

    /**
     * @return the ViewModelStores for all fragments associated with the FragmentManager
     */
    @Nullable
    Map<String, ViewModelStore> getViewModelStores() {
        return mViewModelStores;
    }
}
