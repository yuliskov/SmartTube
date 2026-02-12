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

package androidx.leanback.app;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.R;
import androidx.leanback.widget.PagingIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * An OnboardingSupportFragment provides a common and simple way to build onboarding screen for
 * applications.
 * <p>
 * <h3>Building the screen</h3>
 * The view structure of onboarding screen is composed of the common parts and custom parts. The
 * common parts are composed of icon, title, description and page navigator and the custom parts
 * are composed of background, contents and foreground.
 * <p>
 * To build the screen views, the inherited class should override:
 * <ul>
 * <li>{@link #onCreateBackgroundView} to provide the background view. Background view has the same
 * size as the screen and the lowest z-order.</li>
 * <li>{@link #onCreateContentView} to provide the contents view. The content view is located in
 * the content area at the center of the screen.</li>
 * <li>{@link #onCreateForegroundView} to provide the foreground view. Foreground view has the same
 * size as the screen and the highest z-order</li>
 * </ul>
 * <p>
 * Each of these methods can return {@code null} if the application doesn't want to provide it.
 * <p>
 * <h3>Page information</h3>
 * The onboarding screen may have several pages which explain the functionality of the application.
 * The inherited class should provide the page information by overriding the methods:
 * <p>
 * <ul>
 * <li>{@link #getPageCount} to provide the number of pages.</li>
 * <li>{@link #getPageTitle} to provide the title of the page.</li>
 * <li>{@link #getPageDescription} to provide the description of the page.</li>
 * </ul>
 * <p>
 * Note that the information is used in {@link #onCreateView}, so should be initialized before
 * calling {@code super.onCreateView}.
 * <p>
 * <h3>Animation</h3>
 * Onboarding screen has three kinds of animations:
 * <p>
 * <h4>Logo Splash Animation</a></h4>
 * When onboarding screen appears, the logo splash animation is played by default. The animation
 * fades in the logo image, pauses in a few seconds and fades it out.
 * <p>
 * In most cases, the logo animation needs to be customized because the logo images of applications
 * are different from each other, or some applications may want to show their own animations.
 * <p>
 * The logo animation can be customized in two ways:
 * <ul>
 * <li>The simplest way is to provide the logo image by calling {@link #setLogoResourceId} to show
 * the default logo animation. This method should be called in {@link Fragment#onCreateView}.</li>
 * <li>If the logo animation is complex, then override {@link #onCreateLogoAnimation} and return the
 * {@link Animator} object to run.</li>
 * </ul>
 * <p>
 * If the inherited class provides neither the logo image nor the animation, the logo animation will
 * be omitted.
 * <h4>Page enter animation</h4>
 * After logo animation finishes, page enter animation starts, which causes the header section -
 * title and description views to fade and slide in. Users can override the default
 * fade + slide animation by overriding {@link #onCreateTitleAnimator()} &
 * {@link #onCreateDescriptionAnimator()}. By default we don't animate the custom views but users
 * can provide animation by overriding {@link #onCreateEnterAnimation}.
 *
 * <h4>Page change animation</h4>
 * When the page changes, the default animations of the title and description are played. The
 * inherited class can override {@link #onPageChanged} to start the custom animations.
 * <p>
 * <h3>Finishing the screen</h3>
 * <p>
 * If the user finishes the onboarding screen after navigating all the pages,
 * {@link #onFinishFragment} is called. The inherited class can override this method to show another
 * fragment or activity, or just remove this fragment.
 * <p>
 * <h3>Theming</h3>
 * <p>
 * OnboardingSupportFragment must have access to an appropriate theme. Specifically, the fragment must
 * receive  {@link R.style#Theme_Leanback_Onboarding}, or a theme whose parent is set to that theme.
 * Themes can be provided in one of three ways:
 * <ul>
 * <li>The simplest way is to set the theme for the host Activity to the Onboarding theme or a theme
 * that derives from it.</li>
 * <li>If the Activity already has a theme and setting its parent theme is inconvenient, the
 * existing Activity theme can have an entry added for the attribute
 * {@link R.styleable#LeanbackOnboardingTheme_onboardingTheme}. If present, this theme will be used
 * by OnboardingSupportFragment as an overlay to the Activity's theme.</li>
 * <li>Finally, custom subclasses of OnboardingSupportFragment may provide a theme through the
 * {@link #onProvideTheme} method. This can be useful if a subclass is used across multiple
 * Activities.</li>
 * </ul>
 * <p>
 * If the theme is provided in multiple ways, the onProvideTheme override has priority, followed by
 * the Activity's theme. (Themes whose parent theme is already set to the onboarding theme do not
 * need to set the onboardingTheme attribute; if set, it will be ignored.)
 *
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingTheme
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingHeaderStyle
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingTitleStyle
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingDescriptionStyle
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingNavigatorContainerStyle
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingPageIndicatorStyle
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingStartButtonStyle
 * @attr ref R.styleable#LeanbackOnboardingTheme_onboardingLogoStyle
 */
abstract public class OnboardingSupportFragment extends Fragment {
    private static final String TAG = "OnboardingF";
    private static final boolean DEBUG = false;

    private static final long LOGO_SPLASH_PAUSE_DURATION_MS = 1333;

    private static final long HEADER_ANIMATION_DURATION_MS = 417;
    private static final long DESCRIPTION_START_DELAY_MS = 33;
    private static final long HEADER_APPEAR_DELAY_MS = 500;
    private static final int SLIDE_DISTANCE = 60;

    private static int sSlideDistance;

    private static final TimeInterpolator HEADER_APPEAR_INTERPOLATOR = new DecelerateInterpolator();
    private static final TimeInterpolator HEADER_DISAPPEAR_INTERPOLATOR =
            new AccelerateInterpolator();

    // Keys used to save and restore the states.
    private static final String KEY_CURRENT_PAGE_INDEX = "leanback.onboarding.current_page_index";
    private static final String KEY_LOGO_ANIMATION_FINISHED =
            "leanback.onboarding.logo_animation_finished";
    private static final String KEY_ENTER_ANIMATION_FINISHED =
            "leanback.onboarding.enter_animation_finished";

    private ContextThemeWrapper mThemeWrapper;

    PagingIndicator mPageIndicator;
    View mStartButton;
    private ImageView mLogoView;
    // Optional icon that can be displayed on top of the header section.
    private ImageView mMainIconView;
    private int mIconResourceId;

    TextView mTitleView;
    TextView mDescriptionView;

    boolean mIsLtr;

    // No need to save/restore the logo resource ID, because the logo animation will not appear when
    // the fragment is restored.
    private int mLogoResourceId;
    boolean mLogoAnimationFinished;
    boolean mEnterAnimationFinished;
    int mCurrentPageIndex;

    @ColorInt
    private int mTitleViewTextColor = Color.TRANSPARENT;
    private boolean mTitleViewTextColorSet;

    @ColorInt
    private int mDescriptionViewTextColor = Color.TRANSPARENT;
    private boolean mDescriptionViewTextColorSet;

    @ColorInt
    private int mDotBackgroundColor = Color.TRANSPARENT;
    private boolean mDotBackgroundColorSet;

    @ColorInt
    private int mArrowColor = Color.TRANSPARENT;
    private boolean mArrowColorSet;

    @ColorInt
    private int mArrowBackgroundColor = Color.TRANSPARENT;
    private boolean mArrowBackgroundColorSet;

    private CharSequence mStartButtonText;
    private boolean mStartButtonTextSet;


    private AnimatorSet mAnimator;

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (!mLogoAnimationFinished) {
                // Do not change page until the enter transition finishes.
                return;
            }
            if (mCurrentPageIndex == getPageCount() - 1) {
                onFinishFragment();
            } else {
                moveToNextPage();
            }
        }
    };

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (!mLogoAnimationFinished) {
                // Ignore key event until the enter transition finishes.
                return keyCode != KeyEvent.KEYCODE_BACK;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return false;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (mCurrentPageIndex == 0) {
                        return false;
                    }
                    moveToPreviousPage();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (mIsLtr) {
                        moveToPreviousPage();
                    } else {
                        moveToNextPage();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (mIsLtr) {
                        moveToNextPage();
                    } else {
                        moveToPreviousPage();
                    }
                    return true;
            }
            return false;
        }
    };

    /**
     * Navigates to the previous page.
     */
    protected void moveToPreviousPage() {
        if (!mLogoAnimationFinished) {
            // Ignore if the logo enter transition is in progress.
            return;
        }
        if (mCurrentPageIndex > 0) {
            --mCurrentPageIndex;
            onPageChangedInternal(mCurrentPageIndex + 1);
        }
    }

    /**
     * Navigates to the next page.
     */
    protected void moveToNextPage() {
        if (!mLogoAnimationFinished) {
            // Ignore if the logo enter transition is in progress.
            return;
        }
        if (mCurrentPageIndex < getPageCount() - 1) {
            ++mCurrentPageIndex;
            onPageChangedInternal(mCurrentPageIndex - 1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        resolveTheme();
        LayoutInflater localInflater = getThemeInflater(inflater);
        final ViewGroup view = (ViewGroup) localInflater.inflate(R.layout.lb_onboarding_fragment,
                container, false);
        mIsLtr = getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_LTR;
        mPageIndicator = (PagingIndicator) view.findViewById(R.id.page_indicator);
        mPageIndicator.setOnClickListener(mOnClickListener);
        mPageIndicator.setOnKeyListener(mOnKeyListener);
        mStartButton = view.findViewById(R.id.button_start);
        mStartButton.setOnClickListener(mOnClickListener);
        mStartButton.setOnKeyListener(mOnKeyListener);
        mMainIconView = (ImageView) view.findViewById(R.id.main_icon);
        mLogoView = (ImageView) view.findViewById(R.id.logo);
        mTitleView = (TextView) view.findViewById(R.id.title);
        mDescriptionView = (TextView) view.findViewById(R.id.description);

        if (mTitleViewTextColorSet) {
            mTitleView.setTextColor(mTitleViewTextColor);
        }
        if (mDescriptionViewTextColorSet) {
            mDescriptionView.setTextColor(mDescriptionViewTextColor);
        }
        if (mDotBackgroundColorSet) {
            mPageIndicator.setDotBackgroundColor(mDotBackgroundColor);
        }
        if (mArrowColorSet) {
            mPageIndicator.setArrowColor(mArrowColor);
        }
        if (mArrowBackgroundColorSet) {
            mPageIndicator.setDotBackgroundColor(mArrowBackgroundColor);
        }
        if (mStartButtonTextSet) {
            ((Button) mStartButton).setText(mStartButtonText);
        }
        final Context context = getContext();
        if (sSlideDistance == 0) {
            sSlideDistance = (int) (SLIDE_DISTANCE * context.getResources()
                    .getDisplayMetrics().scaledDensity);
        }
        view.requestFocus();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            mCurrentPageIndex = 0;
            mLogoAnimationFinished = false;
            mEnterAnimationFinished = false;
            mPageIndicator.onPageSelected(0, false);
            view.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getView().getViewTreeObserver().removeOnPreDrawListener(this);
                    if (!startLogoAnimation()) {
                        mLogoAnimationFinished = true;
                        onLogoAnimationFinished();
                    }
                    return true;
                }
            });
        } else {
            mCurrentPageIndex = savedInstanceState.getInt(KEY_CURRENT_PAGE_INDEX);
            mLogoAnimationFinished = savedInstanceState.getBoolean(KEY_LOGO_ANIMATION_FINISHED);
            mEnterAnimationFinished = savedInstanceState.getBoolean(KEY_ENTER_ANIMATION_FINISHED);
            if (!mLogoAnimationFinished) {
                // logo animation wasn't started or was interrupted when the activity was destroyed;
                // restart it againl
                if (!startLogoAnimation()) {
                    mLogoAnimationFinished = true;
                    onLogoAnimationFinished();
                }
            } else {
                onLogoAnimationFinished();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_PAGE_INDEX, mCurrentPageIndex);
        outState.putBoolean(KEY_LOGO_ANIMATION_FINISHED, mLogoAnimationFinished);
        outState.putBoolean(KEY_ENTER_ANIMATION_FINISHED, mEnterAnimationFinished);
    }

    /**
     * Sets the text color for TitleView. If not set, the default textColor set in style
     * referenced by attr {@link R.attr#onboardingTitleStyle} will be used.
     * @param color the color to use as the text color for TitleView
     */
    public void setTitleViewTextColor(@ColorInt int color) {
        mTitleViewTextColor = color;
        mTitleViewTextColorSet = true;
        if (mTitleView != null) {
            mTitleView.setTextColor(color);
        }
    }

    /**
     * Returns the text color of TitleView if it's set through
     * {@link #setTitleViewTextColor(int)}. If no color was set, transparent is returned.
     */
    @ColorInt
    public final int getTitleViewTextColor() {
        return mTitleViewTextColor;
    }

    /**
     * Sets the text color for DescriptionView. If not set, the default textColor set in style
     * referenced by attr {@link R.attr#onboardingDescriptionStyle} will be used.
     * @param color the color to use as the text color for DescriptionView
     */
    public void setDescriptionViewTextColor(@ColorInt int color) {
        mDescriptionViewTextColor = color;
        mDescriptionViewTextColorSet = true;
        if (mDescriptionView != null) {
            mDescriptionView.setTextColor(color);
        }
    }

    /**
     * Returns the text color of DescriptionView if it's set through
     * {@link #setDescriptionViewTextColor(int)}. If no color was set, transparent is returned.
     */
    @ColorInt
    public final int getDescriptionViewTextColor() {
        return mDescriptionViewTextColor;
    }
    /**
     * Sets the background color of the dots. If not set, the default color from attr
     * {@link R.styleable#PagingIndicator_dotBgColor} in the theme will be used.
     * @param color the color to use for dot backgrounds
     */
    public void setDotBackgroundColor(@ColorInt int color) {
        mDotBackgroundColor = color;
        mDotBackgroundColorSet = true;
        if (mPageIndicator != null) {
            mPageIndicator.setDotBackgroundColor(color);
        }
    }

    /**
     * Returns the background color of the dot if it's set through
     * {@link #setDotBackgroundColor(int)}. If no color was set, transparent is returned.
     */
    @ColorInt
    public final int getDotBackgroundColor() {
        return mDotBackgroundColor;
    }

    /**
     * Sets the color of the arrow. This color will supersede the color set in the theme attribute
     * {@link R.styleable#PagingIndicator_arrowColor} if provided. If none of these two are set, the
     * arrow will have its original bitmap color.
     *
     * @param color the color to use for arrow background
     */
    public void setArrowColor(@ColorInt int color) {
        mArrowColor = color;
        mArrowColorSet = true;
        if (mPageIndicator != null) {
            mPageIndicator.setArrowColor(color);
        }
    }

    /**
     * Returns the color of the arrow if it's set through
     * {@link #setArrowColor(int)}. If no color was set, transparent is returned.
     */
    @ColorInt
    public final int getArrowColor() {
        return mArrowColor;
    }

    /**
     * Sets the background color of the arrow. If not set, the default color from attr
     * {@link R.styleable#PagingIndicator_arrowBgColor} in the theme will be used.
     * @param color the color to use for arrow background
     */
    public void setArrowBackgroundColor(@ColorInt int color) {
        mArrowBackgroundColor = color;
        mArrowBackgroundColorSet = true;
        if (mPageIndicator != null) {
            mPageIndicator.setArrowBackgroundColor(color);
        }
    }

    /**
     * Returns the background color of the arrow if it's set through
     * {@link #setArrowBackgroundColor(int)}. If no color was set, transparent is returned.
     */
    @ColorInt
    public final int getArrowBackgroundColor() {
        return mArrowBackgroundColor;
    }

    /**
     * Returns the start button text if it's set through
     * {@link #setStartButtonText(CharSequence)}}. If no string was set, null is returned.
     */
    public final CharSequence getStartButtonText() {
        return mStartButtonText;
    }

    /**
     * Sets the text on the start button text. If not set, the default text set in
     * {@link R.styleable#LeanbackOnboardingTheme_onboardingStartButtonStyle} will be used.
     *
     * @param text the start button text
     */
    public void setStartButtonText(CharSequence text) {
        mStartButtonText = text;
        mStartButtonTextSet = true;
        if (mStartButton != null) {
            ((Button) mStartButton).setText(mStartButtonText);
        }
    }

    /**
     * Returns the theme used for styling the fragment. The default returns -1, indicating that the
     * host Activity's theme should be used.
     *
     * @return The theme resource ID of the theme to use in this fragment, or -1 to use the host
     *         Activity's theme.
     */
    public int onProvideTheme() {
        return -1;
    }

    private void resolveTheme() {
        final Context context = getContext();
        int theme = onProvideTheme();
        if (theme == -1) {
            // Look up the onboardingTheme in the activity's currently specified theme. If it
            // exists, wrap the theme with its value.
            int resId = R.attr.onboardingTheme;
            TypedValue typedValue = new TypedValue();
            boolean found = context.getTheme().resolveAttribute(resId, typedValue, true);
            if (DEBUG) Log.v(TAG, "Found onboarding theme reference? " + found);
            if (found) {
                mThemeWrapper = new ContextThemeWrapper(context, typedValue.resourceId);
            }
        } else {
            mThemeWrapper = new ContextThemeWrapper(context, theme);
        }
    }

    private LayoutInflater getThemeInflater(LayoutInflater inflater) {
        return mThemeWrapper == null ? inflater : inflater.cloneInContext(mThemeWrapper);
    }

    /**
     * Sets the resource ID of the splash logo image. If the logo resource id set, the default logo
     * splash animation will be played.
     *
     * @param id The resource ID of the logo image.
     */
    public final void setLogoResourceId(int id) {
        mLogoResourceId = id;
    }

    /**
     * Returns the resource ID of the splash logo image.
     *
     * @return The resource ID of the splash logo image.
     */
    public final int getLogoResourceId() {
        return mLogoResourceId;
    }

    /**
     * Called to have the inherited class create its own logo animation.
     * <p>
     * This is called only if the logo image resource ID is not set by {@link #setLogoResourceId}.
     * If this returns {@code null}, the logo animation is skipped.
     *
     * @return The {@link Animator} object which runs the logo animation.
     */
    @Nullable
    protected Animator onCreateLogoAnimation() {
        return null;
    }

    boolean startLogoAnimation() {
        final Context context = getContext();
        if (context == null) {
            return false;
        }
        Animator animator = null;
        if (mLogoResourceId != 0) {
            mLogoView.setVisibility(View.VISIBLE);
            mLogoView.setImageResource(mLogoResourceId);
            Animator inAnimator = AnimatorInflater.loadAnimator(context,
                    R.animator.lb_onboarding_logo_enter);
            Animator outAnimator = AnimatorInflater.loadAnimator(context,
                    R.animator.lb_onboarding_logo_exit);
            outAnimator.setStartDelay(LOGO_SPLASH_PAUSE_DURATION_MS);
            AnimatorSet logoAnimator = new AnimatorSet();
            logoAnimator.playSequentially(inAnimator, outAnimator);
            logoAnimator.setTarget(mLogoView);
            animator = logoAnimator;
        } else {
            animator = onCreateLogoAnimation();
        }
        if (animator != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (context != null) {
                        mLogoAnimationFinished = true;
                        onLogoAnimationFinished();
                    }
                }
            });
            animator.start();
            return true;
        }
        return false;
    }

    /**
     * Called to have the inherited class create its enter animation. The start animation runs after
     * logo animation ends.
     *
     * @return The {@link Animator} object which runs the page enter animation.
     */
    @Nullable
    protected Animator onCreateEnterAnimation() {
        return null;
    }


    /**
     * Hides the logo view and makes other fragment views visible. Also initializes the texts for
     * Title and Description views.
     */
    void hideLogoView() {
        mLogoView.setVisibility(View.GONE);

        if (mIconResourceId != 0) {
            mMainIconView.setImageResource(mIconResourceId);
            mMainIconView.setVisibility(View.VISIBLE);
        }

        View container = getView();
        // Create custom views.
        LayoutInflater inflater = getThemeInflater(LayoutInflater.from(
                getContext()));
        ViewGroup backgroundContainer = (ViewGroup) container.findViewById(
                R.id.background_container);
        View background = onCreateBackgroundView(inflater, backgroundContainer);
        if (background != null) {
            backgroundContainer.setVisibility(View.VISIBLE);
            backgroundContainer.addView(background);
        }
        ViewGroup contentContainer = (ViewGroup) container.findViewById(R.id.content_container);
        View content = onCreateContentView(inflater, contentContainer);
        if (content != null) {
            contentContainer.setVisibility(View.VISIBLE);
            contentContainer.addView(content);
        }
        ViewGroup foregroundContainer = (ViewGroup) container.findViewById(
                R.id.foreground_container);
        View foreground = onCreateForegroundView(inflater, foregroundContainer);
        if (foreground != null) {
            foregroundContainer.setVisibility(View.VISIBLE);
            foregroundContainer.addView(foreground);
        }
        // Make views visible which were invisible while logo animation is running.
        container.findViewById(R.id.page_container).setVisibility(View.VISIBLE);
        container.findViewById(R.id.content_container).setVisibility(View.VISIBLE);
        if (getPageCount() > 1) {
            mPageIndicator.setPageCount(getPageCount());
            mPageIndicator.onPageSelected(mCurrentPageIndex, false);
        }
        if (mCurrentPageIndex == getPageCount() - 1) {
            mStartButton.setVisibility(View.VISIBLE);
        } else {
            mPageIndicator.setVisibility(View.VISIBLE);
        }
        // Header views.
        mTitleView.setText(getPageTitle(mCurrentPageIndex));
        mDescriptionView.setText(getPageDescription(mCurrentPageIndex));
    }

    /**
     * Called immediately after the logo animation is complete or no logo animation is specified.
     * This method can also be called when the activity is recreated, i.e. when no logo animation
     * are performed.
     * By default, this method will hide the logo view and start the entrance animation for this
     * fragment.
     * Overriding subclasses can provide their own data loading logic as to when the entrance
     * animation should be executed.
     */
    protected void onLogoAnimationFinished() {
        startEnterAnimation(false);
    }

    /**
     * Called to start entrance transition. This can be called by subclasses when the logo animation
     * and data loading is complete. If force flag is set to false, it will only start the animation
     * if it's not already done yet. Otherwise, it will always start the enter animation. In both
     * cases, the logo view will hide and the rest of fragment views become visible after this call.
     *
     * @param force {@code true} if enter animation has to be performed regardless of whether it's
     *                          been done in the past, {@code false} otherwise
     */
    protected final void startEnterAnimation(boolean force) {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        hideLogoView();
        if (mEnterAnimationFinished && !force) {
            return;
        }
        List<Animator> animators = new ArrayList<>();
        Animator animator = AnimatorInflater.loadAnimator(context,
                R.animator.lb_onboarding_page_indicator_enter);
        animator.setTarget(getPageCount() <= 1 ? mStartButton : mPageIndicator);
        animators.add(animator);

        animator = onCreateTitleAnimator();
        if (animator != null) {
            // Header title.
            animator.setTarget(mTitleView);
            animators.add(animator);
        }

        animator = onCreateDescriptionAnimator();
        if (animator != null) {
            // Header description.
            animator.setTarget(mDescriptionView);
            animators.add(animator);
        }

        // Customized animation by the inherited class.
        Animator customAnimator = onCreateEnterAnimation();
        if (customAnimator != null) {
            animators.add(customAnimator);
        }

        // Return if we don't have any animations.
        if (animators.isEmpty()) {
            return;
        }
        mAnimator = new AnimatorSet();
        mAnimator.playTogether(animators);
        mAnimator.start();
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mEnterAnimationFinished = true;
            }
        });
        // Search focus and give the focus to the appropriate child which has become visible.
        getView().requestFocus();
    }

    /**
     * Provides the entry animation for description view. This allows users to override the
     * default fade and slide animation. Returning null will disable the animation.
     */
    protected Animator onCreateDescriptionAnimator() {
        return AnimatorInflater.loadAnimator(getContext(),
                R.animator.lb_onboarding_description_enter);
    }

    /**
     * Provides the entry animation for title view. This allows users to override the
     * default fade and slide animation. Returning null will disable the animation.
     */
    protected Animator onCreateTitleAnimator() {
        return AnimatorInflater.loadAnimator(getContext(),
                R.animator.lb_onboarding_title_enter);
    }

    /**
     * Returns whether the logo enter animation is finished.
     *
     * @return {@code true} if the logo enter transition is finished, {@code false} otherwise
     */
    protected final boolean isLogoAnimationFinished() {
        return mLogoAnimationFinished;
    }

    /**
     * Returns the page count.
     *
     * @return The page count.
     */
    abstract protected int getPageCount();

    /**
     * Returns the title of the given page.
     *
     * @param pageIndex The page index.
     *
     * @return The title of the page.
     */
    abstract protected CharSequence getPageTitle(int pageIndex);

    /**
     * Returns the description of the given page.
     *
     * @param pageIndex The page index.
     *
     * @return The description of the page.
     */
    abstract protected CharSequence getPageDescription(int pageIndex);

    /**
     * Returns the index of the current page.
     *
     * @return The index of the current page.
     */
    protected final int getCurrentPageIndex() {
        return mCurrentPageIndex;
    }

    /**
     * Called to have the inherited class create background view. This is optional and the fragment
     * which doesn't have the background view can return {@code null}. This is called inside
     * {@link #onCreateView}.
     *
     * @param inflater The LayoutInflater object that can be used to inflate the views,
     * @param container The parent view that the additional views are attached to.The fragment
     *        should not add the view by itself.
     *
     * @return The background view for the onboarding screen, or {@code null}.
     */
    @Nullable
    abstract protected View onCreateBackgroundView(LayoutInflater inflater, ViewGroup container);

    /**
     * Called to have the inherited class create content view. This is optional and the fragment
     * which doesn't have the content view can return {@code null}. This is called inside
     * {@link #onCreateView}.
     *
     * <p>The content view would be located at the center of the screen.
     *
     * @param inflater The LayoutInflater object that can be used to inflate the views,
     * @param container The parent view that the additional views are attached to.The fragment
     *        should not add the view by itself.
     *
     * @return The content view for the onboarding screen, or {@code null}.
     */
    @Nullable
    abstract protected View onCreateContentView(LayoutInflater inflater, ViewGroup container);

    /**
     * Called to have the inherited class create foreground view. This is optional and the fragment
     * which doesn't need the foreground view can return {@code null}. This is called inside
     * {@link #onCreateView}.
     *
     * <p>This foreground view would have the highest z-order.
     *
     * @param inflater The LayoutInflater object that can be used to inflate the views,
     * @param container The parent view that the additional views are attached to.The fragment
     *        should not add the view by itself.
     *
     * @return The foreground view for the onboarding screen, or {@code null}.
     */
    @Nullable
    abstract protected View onCreateForegroundView(LayoutInflater inflater, ViewGroup container);

    /**
     * Called when the onboarding flow finishes.
     */
    protected void onFinishFragment() { }

    /**
     * Called when the page changes.
     */
    private void onPageChangedInternal(int previousPage) {
        if (mAnimator != null) {
            mAnimator.end();
        }
        mPageIndicator.onPageSelected(mCurrentPageIndex, true);

        List<Animator> animators = new ArrayList<>();
        // Header animation
        Animator fadeAnimator = null;
        if (previousPage < getCurrentPageIndex()) {
            // sliding to left
            animators.add(createAnimator(mTitleView, false, Gravity.START, 0));
            animators.add(fadeAnimator = createAnimator(mDescriptionView, false, Gravity.START,
                    DESCRIPTION_START_DELAY_MS));
            animators.add(createAnimator(mTitleView, true, Gravity.END,
                    HEADER_APPEAR_DELAY_MS));
            animators.add(createAnimator(mDescriptionView, true, Gravity.END,
                    HEADER_APPEAR_DELAY_MS + DESCRIPTION_START_DELAY_MS));
        } else {
            // sliding to right
            animators.add(createAnimator(mTitleView, false, Gravity.END, 0));
            animators.add(fadeAnimator = createAnimator(mDescriptionView, false, Gravity.END,
                    DESCRIPTION_START_DELAY_MS));
            animators.add(createAnimator(mTitleView, true, Gravity.START,
                    HEADER_APPEAR_DELAY_MS));
            animators.add(createAnimator(mDescriptionView, true, Gravity.START,
                    HEADER_APPEAR_DELAY_MS + DESCRIPTION_START_DELAY_MS));
        }
        final int currentPageIndex = getCurrentPageIndex();
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTitleView.setText(getPageTitle(currentPageIndex));
                mDescriptionView.setText(getPageDescription(currentPageIndex));
            }
        });

        final Context context = getContext();
        // Animator for switching between page indicator and button.
        if (getCurrentPageIndex() == getPageCount() - 1) {
            mStartButton.setVisibility(View.VISIBLE);
            Animator navigatorFadeOutAnimator = AnimatorInflater.loadAnimator(context,
                    R.animator.lb_onboarding_page_indicator_fade_out);
            navigatorFadeOutAnimator.setTarget(mPageIndicator);
            navigatorFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mPageIndicator.setVisibility(View.GONE);
                }
            });
            animators.add(navigatorFadeOutAnimator);
            Animator buttonFadeInAnimator = AnimatorInflater.loadAnimator(context,
                    R.animator.lb_onboarding_start_button_fade_in);
            buttonFadeInAnimator.setTarget(mStartButton);
            animators.add(buttonFadeInAnimator);
        } else if (previousPage == getPageCount() - 1) {
            mPageIndicator.setVisibility(View.VISIBLE);
            Animator navigatorFadeInAnimator = AnimatorInflater.loadAnimator(context,
                    R.animator.lb_onboarding_page_indicator_fade_in);
            navigatorFadeInAnimator.setTarget(mPageIndicator);
            animators.add(navigatorFadeInAnimator);
            Animator buttonFadeOutAnimator = AnimatorInflater.loadAnimator(context,
                    R.animator.lb_onboarding_start_button_fade_out);
            buttonFadeOutAnimator.setTarget(mStartButton);
            buttonFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mStartButton.setVisibility(View.GONE);
                }
            });
            animators.add(buttonFadeOutAnimator);
        }
        mAnimator = new AnimatorSet();
        mAnimator.playTogether(animators);
        mAnimator.start();
        onPageChanged(mCurrentPageIndex, previousPage);
    }

    /**
     * Called when the page has been changed.
     *
     * @param newPage The new page.
     * @param previousPage The previous page.
     */
    protected void onPageChanged(int newPage, int previousPage) { }

    private Animator createAnimator(View view, boolean fadeIn, int slideDirection,
            long startDelay) {
        boolean isLtr = getView().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
        boolean slideRight = (isLtr && slideDirection == Gravity.END)
                || (!isLtr && slideDirection == Gravity.START)
                || slideDirection == Gravity.RIGHT;
        Animator fadeAnimator;
        Animator slideAnimator;
        if (fadeIn) {
            fadeAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f);
            slideAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                    slideRight ? sSlideDistance : -sSlideDistance, 0);
            fadeAnimator.setInterpolator(HEADER_APPEAR_INTERPOLATOR);
            slideAnimator.setInterpolator(HEADER_APPEAR_INTERPOLATOR);
        } else {
            fadeAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f);
            slideAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0,
                    slideRight ? sSlideDistance : -sSlideDistance);
            fadeAnimator.setInterpolator(HEADER_DISAPPEAR_INTERPOLATOR);
            slideAnimator.setInterpolator(HEADER_DISAPPEAR_INTERPOLATOR);
        }
        fadeAnimator.setDuration(HEADER_ANIMATION_DURATION_MS);
        fadeAnimator.setTarget(view);
        slideAnimator.setDuration(HEADER_ANIMATION_DURATION_MS);
        slideAnimator.setTarget(view);
        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(fadeAnimator, slideAnimator);
        if (startDelay > 0) {
            animator.setStartDelay(startDelay);
        }
        return animator;
    }

    /**
     * Sets the resource id for the main icon.
     */
    public final void setIconResouceId(int resourceId) {
        this.mIconResourceId = resourceId;
        if (mMainIconView != null) {
            mMainIconView.setImageResource(resourceId);
            mMainIconView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Returns the resource id of the main icon.
     */
    public final int getIconResourceId() {
        return mIconResourceId;
    }
}
