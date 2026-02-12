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
package androidx.leanback.app;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.app.Fragment;

import androidx.annotation.RestrictTo;

/**
 * Fragment used by the background manager.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public final class BackgroundFragment extends Fragment {
    private BackgroundManager mBackgroundManager;

    void setBackgroundManager(BackgroundManager backgroundManager) {
        mBackgroundManager = backgroundManager;
    }

    BackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    @Override
    public void onStart() {
        super.onStart();
        // mBackgroundManager might be null:
        // if BackgroundFragment is just restored by FragmentManager,
        // and user does not call BackgroundManager.getInstance() yet.
        if (mBackgroundManager != null) {
            mBackgroundManager.onActivityStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // mBackgroundManager might be null:
        // if BackgroundFragment is just restored by FragmentManager,
        // and user does not call BackgroundManager.getInstance() yet.
        if (mBackgroundManager != null) {
            mBackgroundManager.onResume();
        }
    }

    @Override
    public void onStop() {
        if (mBackgroundManager != null) {
            mBackgroundManager.onStop();
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // mBackgroundManager might be null:
        // if BackgroundFragment is just restored by FragmentManager,
        // and user does not call BackgroundManager.getInstance() yet.
        if (mBackgroundManager != null) {
            mBackgroundManager.detach();
        }
    }
}
