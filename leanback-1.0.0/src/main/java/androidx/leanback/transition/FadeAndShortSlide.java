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
 * limitations under the License.
 */
package androidx.leanback.transition;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * Execute horizontal slide of 1/4 width and fade (to workaround bug 23718734)
 * @hide
 */
@RequiresApi(21)
@RestrictTo(LIBRARY_GROUP)
public class FadeAndShortSlide extends Visibility {

    private static final TimeInterpolator sDecelerate = new DecelerateInterpolator();
    // private static final TimeInterpolator sAccelerate = new AccelerateInterpolator();
    private static final String PROPNAME_SCREEN_POSITION =
            "android:fadeAndShortSlideTransition:screenPosition";

    private CalculateSlide mSlideCalculator;
    private Visibility mFade = new Fade();
    private float mDistance = -1;

    private static abstract class CalculateSlide {

        CalculateSlide() {
        }

        /** Returns the translation X value for view when it goes out of the scene */
        float getGoneX(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            return view.getTranslationX();
        }

        /** Returns the translation Y value for view when it goes out of the scene */
        float getGoneY(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            return view.getTranslationY();
        }
    }

    float getHorizontalDistance(ViewGroup sceneRoot) {
        return mDistance >= 0 ? mDistance : (sceneRoot.getWidth() / 4);
    }

    float getVerticalDistance(ViewGroup sceneRoot) {
        return mDistance >= 0 ? mDistance : (sceneRoot.getHeight() / 4);
    }

    final static CalculateSlide sCalculateStart = new CalculateSlide() {
        @Override
        public float getGoneX(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final float x;
            if (isRtl) {
                x = view.getTranslationX() + t.getHorizontalDistance(sceneRoot);
            } else {
                x = view.getTranslationX() - t.getHorizontalDistance(sceneRoot);
            }
            return x;
        }
    };

    final static CalculateSlide sCalculateEnd = new CalculateSlide() {
        @Override
        public float getGoneX(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final float x;
            if (isRtl) {
                x = view.getTranslationX() - t.getHorizontalDistance(sceneRoot);
            } else {
                x = view.getTranslationX() + t.getHorizontalDistance(sceneRoot);
            }
            return x;
        }
    };

    final static CalculateSlide sCalculateStartEnd = new CalculateSlide() {
        @Override
        public float getGoneX(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            final int viewCenter = position[0] + view.getWidth() / 2;
            sceneRoot.getLocationOnScreen(position);
            Rect center = t.getEpicenter();
            final int sceneRootCenter = center == null ? (position[0] + sceneRoot.getWidth() / 2)
                    : center.centerX();
            if (viewCenter < sceneRootCenter) {
                return view.getTranslationX() - t.getHorizontalDistance(sceneRoot);
            } else {
                return view.getTranslationX() + t.getHorizontalDistance(sceneRoot);
            }
        }
    };

    final static CalculateSlide sCalculateBottom = new CalculateSlide() {
        @Override
        public float getGoneY(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            return view.getTranslationY() + t.getVerticalDistance(sceneRoot);
        }
    };

    final static CalculateSlide sCalculateTop = new CalculateSlide() {
        @Override
        public float getGoneY(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            return view.getTranslationY() - t.getVerticalDistance(sceneRoot);
        }
    };

    final CalculateSlide sCalculateTopBottom = new CalculateSlide() {
        @Override
        public float getGoneY(FadeAndShortSlide t, ViewGroup sceneRoot, View view, int[] position) {
            final int viewCenter = position[1] + view.getHeight() / 2;
            sceneRoot.getLocationOnScreen(position);
            Rect center = getEpicenter();
            final int sceneRootCenter = center == null ? (position[1] + sceneRoot.getHeight() / 2)
                    : center.centerY();
            if (viewCenter < sceneRootCenter) {
                return view.getTranslationY() - t.getVerticalDistance(sceneRoot);
            } else {
                return view.getTranslationY() + t.getVerticalDistance(sceneRoot);
            }
        }
    };

    public FadeAndShortSlide() {
        this(Gravity.START);
    }

    public FadeAndShortSlide(int slideEdge) {
        setSlideEdge(slideEdge);
    }

    public FadeAndShortSlide(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbSlide);
        int edge = a.getInt(R.styleable.lbSlide_lb_slideEdge, Gravity.START);
        setSlideEdge(edge);
        a.recycle();
    }

    @Override
    public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
        mFade.setEpicenterCallback(epicenterCallback);
        super.setEpicenterCallback(epicenterCallback);
    }

    private void captureValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        transitionValues.values.put(PROPNAME_SCREEN_POSITION, position);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        mFade.captureStartValues(transitionValues);
        super.captureStartValues(transitionValues);
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        mFade.captureEndValues(transitionValues);
        super.captureEndValues(transitionValues);
        captureValues(transitionValues);
    }

    public void setSlideEdge(int slideEdge) {
        switch (slideEdge) {
            case Gravity.START:
                mSlideCalculator = sCalculateStart;
                break;
            case Gravity.END:
                mSlideCalculator = sCalculateEnd;
                break;
            case Gravity.START | Gravity.END:
                mSlideCalculator = sCalculateStartEnd;
                break;
            case Gravity.TOP:
                mSlideCalculator = sCalculateTop;
                break;
            case Gravity.BOTTOM:
                mSlideCalculator = sCalculateBottom;
                break;
            case Gravity.TOP | Gravity.BOTTOM:
                mSlideCalculator = sCalculateTopBottom;
                break;
            default:
                throw new IllegalArgumentException("Invalid slide direction");
        }
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (endValues == null) {
            return null;
        }
        if (sceneRoot == view) {
            // workaround b/25375640, avoid run animation on sceneRoot
            return null;
        }
        int[] position = (int[]) endValues.values.get(PROPNAME_SCREEN_POSITION);
        int left = position[0];
        int top = position[1];
        float endX = view.getTranslationX();
        float startX = mSlideCalculator.getGoneX(this, sceneRoot, view, position);
        float endY = view.getTranslationY();
        float startY = mSlideCalculator.getGoneY(this, sceneRoot, view, position);
        final Animator slideAnimator = TranslationAnimationCreator.createAnimation(view, endValues,
                left, top, startX, startY, endX, endY, sDecelerate, this);
        final Animator fadeAnimator = mFade.onAppear(sceneRoot, view, startValues, endValues);
        if (slideAnimator == null) {
            return fadeAnimator;
        } else if (fadeAnimator == null) {
            return slideAnimator;
        }
        final AnimatorSet set = new AnimatorSet();
        set.play(slideAnimator).with(fadeAnimator);

        return set;
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null) {
            return null;
        }
        if (sceneRoot == view) {
            // workaround b/25375640, avoid run animation on sceneRoot
            return null;
        }
        int[] position = (int[]) startValues.values.get(PROPNAME_SCREEN_POSITION);
        int left = position[0];
        int top = position[1];
        float startX = view.getTranslationX();
        float endX = mSlideCalculator.getGoneX(this, sceneRoot, view, position);
        float startY = view.getTranslationY();
        float endY = mSlideCalculator.getGoneY(this, sceneRoot, view, position);
        final Animator slideAnimator = TranslationAnimationCreator.createAnimation(view,
                startValues, left, top, startX, startY, endX, endY, sDecelerate /* sAccelerate */,
                this);
        final Animator fadeAnimator = mFade.onDisappear(sceneRoot, view, startValues, endValues);
        if (slideAnimator == null) {
            return fadeAnimator;
        } else if (fadeAnimator == null) {
            return slideAnimator;
        }
        final AnimatorSet set = new AnimatorSet();
        set.play(slideAnimator).with(fadeAnimator);

        return set;
    }

    @Override
    public Transition addListener(TransitionListener listener) {
        mFade.addListener(listener);
        return super.addListener(listener);
    }

    @Override
    public Transition removeListener(TransitionListener listener) {
        mFade.removeListener(listener);
        return super.removeListener(listener);
    }

    /**
     * Returns distance to slide.  When negative value is returned, it will use 1/4 of
     * sceneRoot dimension.
     */
    public float getDistance() {
        return mDistance;
    }

    /**
     * Set distance to slide, default value is -1.  when negative value is set, it will use 1/4 of
     * sceneRoot dimension.
     * @param distance Pixels to slide.
     */
    public void setDistance(float distance) {
        mDistance = distance;
    }

    @Override
    public Transition clone() {
        FadeAndShortSlide clone = null;
        clone = (FadeAndShortSlide) super.clone();
        clone.mFade = (Visibility) mFade.clone();
        return clone;
    }
}
