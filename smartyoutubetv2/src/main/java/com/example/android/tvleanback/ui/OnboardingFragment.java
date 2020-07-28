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

package com.example.android.tvleanback.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.leanback.app.OnboardingSupportFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.example.android.tvleanback.R;

import java.util.ArrayList;

public class OnboardingFragment extends OnboardingSupportFragment {
    public static final String COMPLETED_ONBOARDING = "completed_onboarding";

    private static final int[] pageTitles = {
            R.string.onboarding_title_welcome,
            R.string.onboarding_title_design,
            R.string.onboarding_title_simple,
            R.string.onboarding_title_project
    };
    private static final int[] pageDescriptions = {
            R.string.onboarding_description_welcome,
            R.string.onboarding_description_design,
            R.string.onboarding_description_simple,
            R.string.onboarding_description_project
    };
    private final int[] pageImages = {
            R.drawable.tv_animation_a,
            R.drawable.tv_animation_b,
            R.drawable.tv_animation_c,
            R.drawable.tv_animation_d
    };
    private static final long ANIMATION_DURATION = 500;
    private Animator mContentAnimator;
    private ImageView mContentView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Set the logo to display a splash animation
        setLogoResourceId(R.drawable.videos_by_google_banner);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected void onFinishFragment() {
        super.onFinishFragment();
        // Our onboarding is done
        // Update the shared preferences
        SharedPreferences.Editor sharedPreferencesEditor =
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
        sharedPreferencesEditor.putBoolean(COMPLETED_ONBOARDING, true);
        sharedPreferencesEditor.apply();
        // Let's go back to the MainActivity
        getActivity().finish();
    }

    @Override
    protected int getPageCount() {
        return pageTitles.length;
    }

    @Override
    protected String getPageTitle(int pageIndex) {
        return getString(pageTitles[pageIndex]);
    }

    @Override
    protected String getPageDescription(int pageIndex) {
        return getString(pageDescriptions[pageIndex]);
    }

    @Nullable
    @Override
    protected View onCreateBackgroundView(LayoutInflater inflater, ViewGroup container) {
        View bgView = new View(getActivity());
        bgView.setBackgroundColor(getResources().getColor(R.color.fastlane_background));
        return bgView;
    }

    @Nullable
    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup container) {
        mContentView = new ImageView(getActivity());
        mContentView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mContentView.setPadding(0, 32, 0, 32);
        return mContentView;
    }

    @Nullable
    @Override
    protected View onCreateForegroundView(LayoutInflater inflater, ViewGroup container) {
        return null;
    }

    @Override
    protected void onPageChanged(final int newPage, int previousPage) {
        if (mContentAnimator != null) {
            mContentAnimator.end();
        }
        ArrayList<Animator> animators = new ArrayList<>();
        Animator fadeOut = createFadeOutAnimator(mContentView);

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContentView.setImageDrawable(getResources().getDrawable(pageImages[newPage]));
                ((AnimationDrawable) mContentView.getDrawable()).start();
            }
        });
        animators.add(fadeOut);
        animators.add(createFadeInAnimator(mContentView));
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(animators);
        set.start();
        mContentAnimator = set;
    }
    @Override
    protected Animator onCreateEnterAnimation() {
        mContentView.setImageDrawable(getResources().getDrawable(pageImages[0]));
        ((AnimationDrawable) mContentView.getDrawable()).start();
        mContentAnimator = createFadeInAnimator(mContentView);
        return mContentAnimator;
    }
    private Animator createFadeInAnimator(View view) {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f).setDuration(ANIMATION_DURATION);
    }

    private Animator createFadeOutAnimator(View view) {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f).setDuration(ANIMATION_DURATION);
    }
}