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

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * Callbacks to a {@link Fragment}'s container.
 */
public abstract class FragmentContainer {
    /**
     * Return the view with the given resource ID. May return {@code null} if the
     * view is not a child of this container.
     */
    @Nullable
    public abstract View onFindViewById(@IdRes int id);

    /**
     * Return {@code true} if the container holds any view.
     */
    public abstract boolean onHasView();


    /**
     * Creates an instance of the specified fragment, can be overridden to construct fragments
     * with dependencies, or change the fragment being constructed. By default just calls
     * {@link Fragment#instantiate(Context, String, Bundle)}.
     * @deprecated Use {@link FragmentManager#setFragmentFactory} to control how Fragments are
     * instantiated.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    @NonNull
    public Fragment instantiate(@NonNull Context context, @NonNull String className,
            @Nullable Bundle arguments) {
        return Fragment.instantiate(context, className, arguments);
    }
}
