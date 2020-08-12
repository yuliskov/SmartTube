/*
 * Copyright (c) 2016 The Android Open Source Project
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

package com.liskovsoft.android.smartyoutubetv2.ui;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import com.liskovsoft.android.smartyoutubetv2.R;
import com.liskovsoft.smartyoutubetv2.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.presenters.OnboardingPresenter;

/*
 * OnboardingActivity for OnboardingFragment
 */
public class OnboardingActivity extends FragmentActivity {
    private OnboardingPresenter mPresenter;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.onboarding);

        mPresenter = OnboardingPresenter.instance(getApplicationContext());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        mPresenter.onBackPressed();
    }
}
