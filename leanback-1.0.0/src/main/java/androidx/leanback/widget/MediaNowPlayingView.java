/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * The view displaying 3 animated peak meters next to each other when a media item is playing.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class MediaNowPlayingView extends LinearLayout{

    private final ImageView mImage1;
    private final ImageView mImage2;
    private final ImageView mImage3;
    private final ObjectAnimator mObjectAnimator1;
    private final ObjectAnimator mObjectAnimator2;
    private final ObjectAnimator mObjectAnimator3;
    protected final LinearInterpolator mLinearInterpolator = new LinearInterpolator();

    public MediaNowPlayingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.lb_playback_now_playing_bars, this, true);
        mImage1 = findViewById(R.id.bar1);
        mImage2 = findViewById(R.id.bar2);
        mImage3 = findViewById(R.id.bar3);

        mImage1.setPivotY(mImage1.getDrawable().getIntrinsicHeight());
        mImage2.setPivotY(mImage2.getDrawable().getIntrinsicHeight());
        mImage3.setPivotY(mImage3.getDrawable().getIntrinsicHeight());

        setDropScale(mImage1);
        setDropScale(mImage2);
        setDropScale(mImage3);

        mObjectAnimator1 = ObjectAnimator.ofFloat(mImage1, "scaleY", 5f / 12f, 3f / 12f, 5f / 12f,
                7f / 12f, 9f / 12f, 10f / 12f, 11f / 12f, 12f / 12f, 11f / 12f, 12f / 12f,
                10f / 12f, 8f / 12f, 6f / 12f, 4f / 12f, 2f / 12f, 4f / 12f, 6f / 12f, 7f / 12f,
                9f / 12f, 11f / 12f, 9f / 12f, 7f / 12f, 5f / 12f, 3f / 12f, 5f / 12f, 8f / 12f,
                5f / 12f, 3f / 12f, 4f / 12f, 5f / 12f);
        mObjectAnimator1.setRepeatCount(ValueAnimator.INFINITE);
        mObjectAnimator1.setDuration(2320);
        mObjectAnimator1.setInterpolator(mLinearInterpolator);

        mObjectAnimator2 = ObjectAnimator.ofFloat(mImage2, "scaleY", 12f / 12f, 11f / 12f,
                10f / 12f, 11f / 12f, 12f / 12f, 11f / 12f, 9f / 12f, 7f / 12f, 9f / 12f, 11f / 12f,
                12f / 12f, 10f / 12f, 8f / 12f, 10f / 12f, 12f / 12f, 11f / 12f, 9f / 12f, 5f / 12f,
                3f / 12f, 5f / 12f, 8f / 12f, 10f / 12f, 12f / 12f, 10f / 12f, 9f / 12f, 8f / 12f,
                12f / 12f);
        mObjectAnimator2.setRepeatCount(ValueAnimator.INFINITE);
        mObjectAnimator2.setDuration(2080);
        mObjectAnimator2.setInterpolator(mLinearInterpolator);

        mObjectAnimator3 = ObjectAnimator.ofFloat(mImage3, "scaleY", 8f / 12f, 9f / 12f, 10f / 12f,
                12f / 12f, 11f / 12f, 9f / 12f, 7f / 12f, 5f / 12f, 7f / 12f, 8f / 12f, 9f / 12f,
                12f / 12f, 11f / 12f, 12f / 12f, 9f / 12f, 7f / 12f, 9f / 12f, 11f / 12f, 12f / 12f,
                10f / 12f, 8f / 12f, 9f / 12f, 7f / 12f, 5f / 12f, 3f / 12f, 8f / 12f);
        mObjectAnimator3.setRepeatCount(ValueAnimator.INFINITE);
        mObjectAnimator3.setDuration(2000);
        mObjectAnimator3.setInterpolator(mLinearInterpolator);
    }

    static void setDropScale(View view) {
        view.setScaleY(1f / 12f);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == View.GONE) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == View.VISIBLE)
            startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private void startAnimation() {
        startAnimation(mObjectAnimator1);
        startAnimation(mObjectAnimator2);
        startAnimation(mObjectAnimator3);
        mImage1.setVisibility(View.VISIBLE);
        mImage2.setVisibility(View.VISIBLE);
        mImage3.setVisibility(View.VISIBLE);
    }

    private void stopAnimation() {
        stopAnimation(mObjectAnimator1, mImage1);
        stopAnimation(mObjectAnimator2, mImage2);
        stopAnimation(mObjectAnimator3, mImage3);
        mImage1.setVisibility(View.GONE);
        mImage2.setVisibility(View.GONE);
        mImage3.setVisibility(View.GONE);
    }

    private void startAnimation(Animator animator) {
        if (!animator.isStarted()) {
            animator.start();
        }
    }

    private void stopAnimation(Animator animator, View view) {
        if (animator.isStarted()) {
            animator.cancel();
            setDropScale(view);
        }
    }
}
