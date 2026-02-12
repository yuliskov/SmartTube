/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.AttributeSet;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.RequiresApi;
import androidx.leanback.R;

/**
 * Slide distance toward/from a edge.
 * This is a limited Slide implementation for KitKat without propagation support.
 */
@RequiresApi(19)
class SlideKitkat extends Visibility {
    private static final String TAG = "SlideKitkat";

    private static final TimeInterpolator sDecelerate = new DecelerateInterpolator();
    private static final TimeInterpolator sAccelerate = new AccelerateInterpolator();

    private int mSlideEdge;
    private CalculateSlide mSlideCalculator;

    private interface CalculateSlide {
        /** Returns the translation value for view when it out of the scene */
        float getGone(View view);

        /** Returns the translation value for view when it is in the scene */
        float getHere(View view);

        /** Returns the property to animate translation */
        Property<View, Float> getProperty();
    }

    private static abstract class CalculateSlideHorizontal implements CalculateSlide {
        CalculateSlideHorizontal() {
        }

        @Override
        public float getHere(View view) {
            return view.getTranslationX();
        }

        @Override
        public Property<View, Float> getProperty() {
            return View.TRANSLATION_X;
        }
    }

    private static abstract class CalculateSlideVertical implements CalculateSlide {
        CalculateSlideVertical() {
        }

        @Override
        public float getHere(View view) {
            return view.getTranslationY();
        }

        @Override
        public Property<View, Float> getProperty() {
            return View.TRANSLATION_Y;
        }
    }

    private static final CalculateSlide sCalculateLeft = new CalculateSlideHorizontal() {
        @Override
        public float getGone(View view) {
            return view.getTranslationX() - view.getWidth();
        }
    };

    private static final CalculateSlide sCalculateTop = new CalculateSlideVertical() {
        @Override
        public float getGone(View view) {
            return view.getTranslationY() - view.getHeight();
        }
    };

    private static final CalculateSlide sCalculateRight = new CalculateSlideHorizontal() {
        @Override
        public float getGone(View view) {
            return view.getTranslationX() + view.getWidth();
        }
    };

    private static final CalculateSlide sCalculateBottom = new CalculateSlideVertical() {
        @Override
        public float getGone(View view) {
            return view.getTranslationY() + view.getHeight();
        }
    };

    private static final CalculateSlide sCalculateStart = new CalculateSlideHorizontal() {
        @Override
        public float getGone(View view) {
            if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                return view.getTranslationX() + view.getWidth();
            } else {
                return view.getTranslationX() - view.getWidth();
            }
        }
    };

    private static final CalculateSlide sCalculateEnd = new CalculateSlideHorizontal() {
        @Override
        public float getGone(View view) {
            if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                return view.getTranslationX() - view.getWidth();
            } else {
                return view.getTranslationX() + view.getWidth();
            }
        }
    };

    public SlideKitkat() {
        setSlideEdge(Gravity.BOTTOM);
    }

    public SlideKitkat(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbSlide);
        int edge = a.getInt(R.styleable.lbSlide_lb_slideEdge, Gravity.BOTTOM);
        setSlideEdge(edge);
        long duration = a.getInt(R.styleable.lbSlide_android_duration, -1);
        if (duration >= 0) {
            setDuration(duration);
        }
        long startDelay = a.getInt(R.styleable.lbSlide_android_startDelay, -1);
        if (startDelay > 0) {
            setStartDelay(startDelay);
        }
        final int resID = a.getResourceId(R.styleable.lbSlide_android_interpolator, 0);
        if (resID > 0) {
            setInterpolator(AnimationUtils.loadInterpolator(context, resID));
        }
        a.recycle();
    }

    /**
     * Change the edge that Views appear and disappear from.
     *
     * @param slideEdge The edge of the scene to use for Views appearing and disappearing. One of
     *                  {@link android.view.Gravity#LEFT}, {@link android.view.Gravity#TOP},
     *                  {@link android.view.Gravity#RIGHT}, {@link android.view.Gravity#BOTTOM},
     *                  {@link android.view.Gravity#START}, {@link android.view.Gravity#END}.
     */
    public void setSlideEdge(int slideEdge) {
        switch (slideEdge) {
            case Gravity.LEFT:
                mSlideCalculator = sCalculateLeft;
                break;
            case Gravity.TOP:
                mSlideCalculator = sCalculateTop;
                break;
            case Gravity.RIGHT:
                mSlideCalculator = sCalculateRight;
                break;
            case Gravity.BOTTOM:
                mSlideCalculator = sCalculateBottom;
                break;
            case Gravity.START:
                mSlideCalculator = sCalculateStart;
                break;
            case Gravity.END:
                mSlideCalculator = sCalculateEnd;
                break;
            default:
                throw new IllegalArgumentException("Invalid slide direction");
        }
        mSlideEdge = slideEdge;
    }

    /**
     * Returns the edge that Views appear and disappear from.
     * @return the edge of the scene to use for Views appearing and disappearing. One of
     *         {@link android.view.Gravity#LEFT}, {@link android.view.Gravity#TOP},
     *         {@link android.view.Gravity#RIGHT}, {@link android.view.Gravity#BOTTOM},
     *         {@link android.view.Gravity#START}, {@link android.view.Gravity#END}.
     */
    public int getSlideEdge() {
        return mSlideEdge;
    }

    private Animator createAnimation(final View view, Property<View, Float> property,
            float start, float end, float terminalValue, TimeInterpolator interpolator,
            int finalVisibility) {
        float[] startPosition = (float[]) view.getTag(R.id.lb_slide_transition_value);
        if (startPosition != null) {
            start = View.TRANSLATION_Y == property ? startPosition[1] : startPosition[0];
            view.setTag(R.id.lb_slide_transition_value, null);
        }
        final ObjectAnimator anim = ObjectAnimator.ofFloat(view, property, start, end);

        SlideAnimatorListener listener = new SlideAnimatorListener(view, property, terminalValue, end,
                finalVisibility);
        anim.addListener(listener);
        anim.addPauseListener(listener);
        anim.setInterpolator(interpolator);
        return anim;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot,
            TransitionValues startValues, int startVisibility,
            TransitionValues endValues, int endVisibility) {
        View view = (endValues != null) ? endValues.view : null;
        if (view == null) {
            return null;
        }
        float end = mSlideCalculator.getHere(view);
        float start = mSlideCalculator.getGone(view);
        return createAnimation(view, mSlideCalculator.getProperty(), start, end, end, sDecelerate,
                View.VISIBLE);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot,
            TransitionValues startValues, int startVisibility,
            TransitionValues endValues, int endVisibility) {
        View view = (startValues != null) ? startValues.view : null;
        if (view == null) {
            return null;
        }
        float start = mSlideCalculator.getHere(view);
        float end = mSlideCalculator.getGone(view);

        return createAnimation(view, mSlideCalculator.getProperty(), start, end, start,
                sAccelerate, View.INVISIBLE);
    }

    private static class SlideAnimatorListener extends AnimatorListenerAdapter {
        private boolean mCanceled = false;
        private float mPausedValue;
        private final View mView;
        private final float mEndValue;
        private final float mTerminalValue;
        private final int mFinalVisibility;
        private final Property<View, Float> mProp;

        public SlideAnimatorListener(View view, Property<View, Float> prop,
                float terminalValue, float endValue, int finalVisibility) {
            mProp = prop;
            mView = view;
            mTerminalValue = terminalValue;
            mEndValue = endValue;
            mFinalVisibility = finalVisibility;
            view.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAnimationCancel(Animator animator) {
            float[] transitionPosition = new float[2];
            transitionPosition[0] = mView.getTranslationX();
            transitionPosition[1] = mView.getTranslationY();
            mView.setTag(R.id.lb_slide_transition_value, transitionPosition);
            mProp.set(mView, mTerminalValue);
            mCanceled = true;
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            if (!mCanceled) {
                mProp.set(mView, mTerminalValue);
            }
            mView.setVisibility(mFinalVisibility);
        }

        @Override
        public void onAnimationPause(Animator animator) {
            mPausedValue = mProp.get(mView);
            mProp.set(mView, mEndValue);
            mView.setVisibility(mFinalVisibility);
        }

        @Override
        public void onAnimationResume(Animator animator) {
            mProp.set(mView, mPausedValue);
            mView.setVisibility(View.VISIBLE);
        }
    }
}