/*
 * Copyright (c) 2014 The Android Open Source Project
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

package com.example.android.tvleanback.ui;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import androidx.leanback.app.ErrorSupportFragment;
import androidx.fragment.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.example.android.tvleanback.R;

/*
 * This class demonstrates how to extend ErrorFragment to create an error dialog.
 */
public class BrowseErrorFragment extends ErrorSupportFragment {
    private static final boolean TRANSLUCENT = true;
    private static final int TIMER_DELAY = 1000;

    private final Handler mHandler = new Handler();
    private SpinnerFragment mSpinnerFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getResources().getString(R.string.app_name));

        mSpinnerFragment = new SpinnerFragment();
        getFragmentManager().beginTransaction().add(R.id.main_frame, mSpinnerFragment).commit();
    }

    @Override
    public void onStart() {
        super.onStart();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getFragmentManager().beginTransaction().remove(mSpinnerFragment).commit();
                setErrorContent();
            }
        }, TIMER_DELAY);
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacksAndMessages(null);
        getFragmentManager().beginTransaction().remove(mSpinnerFragment).commit();
    }

    private void setErrorContent() {
        setImageDrawable(getResources().getDrawable(R.drawable.lb_ic_sad_cloud, null));
        setMessage(getResources().getString(R.string.error_fragment_message));
        setDefaultBackground(TRANSLUCENT);

        setButtonText(getResources().getString(R.string.dismiss_error));
        setButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                getFragmentManager().beginTransaction().remove(BrowseErrorFragment.this).commit();
                getFragmentManager().popBackStack();
            }
        });
    }

    public static class SpinnerFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            ProgressBar progressBar = new ProgressBar(container.getContext());
            if (container instanceof FrameLayout) {
                Resources res = getResources();
                int width = res.getDimensionPixelSize(R.dimen.spinner_width);
                int height = res.getDimensionPixelSize(R.dimen.spinner_height);
                FrameLayout.LayoutParams layoutParams =
                        new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
                progressBar.setLayoutParams(layoutParams);
            }
            return progressBar;
        }
    }
}
