/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.preference;

import android.app.Fragment;
import android.os.Build;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;

public class LeanbackPreferenceDialogFragment extends Fragment {
    private Preference mPref;

    public static final String ARG_KEY = "key";

    private DialogPreference mPreference;

    public LeanbackPreferenceDialogFragment() {
        if (Build.VERSION.SDK_INT >= 21) {
            LeanbackPreferenceFragmentTransitionHelperApi21.addTransitions(this);
        }
    }

    // MODIFIED: Fix Android 9 error by allowing null fragments
    // Target fragment doesn't belongs to this fragment manager
    //@Override
    //public void onCreate(Bundle savedInstanceState) {
    //    super.onCreate(savedInstanceState);
    //
    //    final Fragment rawFragment = getTargetFragment();
    //    if (!(rawFragment instanceof DialogPreference.TargetFragment)) {
    //        throw new IllegalStateException("Target fragment " + rawFragment
    //                + " must implement TargetFragment interface");
    //    }
    //}

    // MODIFIED: Android 9 error fix: Target fragment doesn't belongs to this fragment manager
    //public DialogPreference getPreference() {
    //    if (mPreference == null) {
    //        final String key = getArguments().getString(ARG_KEY);
    //        final DialogPreference.TargetFragment fragment =
    //                (DialogPreference.TargetFragment) getTargetFragment();
    //        mPreference = (DialogPreference) fragment.findPreference(key);
    //    }
    //    return mPreference;
    //}

    // MODIFIED: Fix Android 9 error by allowing null fragments
    // Target fragment doesn't belongs to this fragment manager
    @Override
    public void setTargetFragment(Fragment fragment, int requestCode) {
        // NOP
    }

    public void setPreference(Preference pref) {
        mPref = pref;
    }

    // MODIFIED: allow use this fragment without target fragment
    public DialogPreference getPreference() {
        return mPref != null ? (DialogPreference) mPref : null;
    }
}
