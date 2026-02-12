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

package androidx.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;

import androidx.annotation.VisibleForTesting;
import androidx.leanback.R;

import java.util.ArrayList;

/**
 * A card style layout that responds to certain state changes. It arranges its
 * children in a vertical column, with different regions becoming visible at
 * different times.
 *
 * <p>
 * A BaseCardView will draw its children based on its type, the region
 * visibilities of the child types, and the state of the widget. A child may be
 * marked as belonging to one of three regions: main, info, or extra. The main
 * region is always visible, while the info and extra regions can be set to
 * display based on the activated or selected state of the View. The card states
 * are set by calling {@link #setActivated(boolean) setActivated} and
 * {@link #setSelected(boolean) setSelected}.
 * <p>
 * See {@link BaseCardView.LayoutParams} for layout attributes.
 * </p>
 */
public class BaseCardView extends FrameLayout {
    private static final String TAG = "BaseCardView";
    private static final boolean DEBUG = false;

    /**
     * A simple card type with a single layout area. This card type does not
     * change its layout or size as it transitions between
     * Activated/Not-Activated or Selected/Unselected states.
     *
     * @see #getCardType()
     */
    public static final int CARD_TYPE_MAIN_ONLY = 0;

    /**
     * A Card type with 2 layout areas: A main area which is always visible, and
     * an info area that fades in over the main area when it is visible.
     * The card height will not change.
     *
     * @see #getCardType()
     */
    public static final int CARD_TYPE_INFO_OVER = 1;

    /**
     * A Card type with 2 layout areas: A main area which is always visible, and
     * an info area that appears below the main area. When the info area is visible
     * the total card height will change.
     *
     * @see #getCardType()
     */
    public static final int CARD_TYPE_INFO_UNDER = 2;

    /**
     * A Card type with 3 layout areas: A main area which is always visible; an
     * info area which will appear below the main area, and an extra area that
     * only appears after a short delay. The info area appears below the main
     * area, causing the total card height to change. The extra area animates in
     * at the bottom of the card, shifting up the info view without affecting
     * the card height.
     *
     * @see #getCardType()
     */
    public static final int CARD_TYPE_INFO_UNDER_WITH_EXTRA = 3;

    /**
     * Indicates that a card region is always visible.
     */
    public static final int CARD_REGION_VISIBLE_ALWAYS = 0;

    /**
     * Indicates that a card region is visible when the card is activated.
     */
    public static final int CARD_REGION_VISIBLE_ACTIVATED = 1;

    /**
     * Indicates that a card region is visible when the card is selected.
     */
    public static final int CARD_REGION_VISIBLE_SELECTED = 2;

    private static final int CARD_TYPE_INVALID = 4;

    private int mCardType;
    private int mInfoVisibility;
    private int mExtraVisibility;

    private ArrayList<View> mMainViewList;
    ArrayList<View> mInfoViewList;
    ArrayList<View> mExtraViewList;

    private int mMeasuredWidth;
    private int mMeasuredHeight;
    private boolean mDelaySelectedAnim;
    private int mSelectedAnimationDelay;
    private final int mActivatedAnimDuration;
    private final int mSelectedAnimDuration;

    /**
     * Distance of top of info view to bottom of MainView, it will shift up when extra view appears.
     */
    float mInfoOffset;
    float mInfoVisFraction;
    float mInfoAlpha;
    private Animation mAnim;

    private final static int[] LB_PRESSED_STATE_SET = new int[]{
        android.R.attr.state_pressed};

    private final Runnable mAnimationTrigger = new Runnable() {
        @Override
        public void run() {
            animateInfoOffset(true);
        }
    };

    public BaseCardView(Context context) {
        this(context, null);
    }

    public BaseCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.baseCardViewStyle);
    }

    public BaseCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbBaseCardView,
                defStyleAttr, 0);

        try {
            mCardType = a.getInteger(R.styleable.lbBaseCardView_cardType, CARD_TYPE_MAIN_ONLY);
            Drawable cardForeground = a.getDrawable(R.styleable.lbBaseCardView_cardForeground);
            if (cardForeground != null) {
                setForeground(cardForeground);
            }
            Drawable cardBackground = a.getDrawable(R.styleable.lbBaseCardView_cardBackground);
            if (cardBackground != null) {
                setBackground(cardBackground);
            }
            mInfoVisibility = a.getInteger(R.styleable.lbBaseCardView_infoVisibility,
                    CARD_REGION_VISIBLE_ACTIVATED);
            mExtraVisibility = a.getInteger(R.styleable.lbBaseCardView_extraVisibility,
                    CARD_REGION_VISIBLE_SELECTED);
            // Extra region should never show before info region.
            if (mExtraVisibility < mInfoVisibility) {
                mExtraVisibility = mInfoVisibility;
            }

            mSelectedAnimationDelay = a.getInteger(
                    R.styleable.lbBaseCardView_selectedAnimationDelay,
                    getResources().getInteger(R.integer.lb_card_selected_animation_delay));

            mSelectedAnimDuration = a.getInteger(
                    R.styleable.lbBaseCardView_selectedAnimationDuration,
                    getResources().getInteger(R.integer.lb_card_selected_animation_duration));

            mActivatedAnimDuration =
                    a.getInteger(R.styleable.lbBaseCardView_activatedAnimationDuration,
                    getResources().getInteger(R.integer.lb_card_activated_animation_duration));
        } finally {
            a.recycle();
        }

        mDelaySelectedAnim = true;

        mMainViewList = new ArrayList<View>();
        mInfoViewList = new ArrayList<View>();
        mExtraViewList = new ArrayList<View>();

        mInfoOffset = 0.0f;
        mInfoVisFraction = getFinalInfoVisFraction();
        mInfoAlpha = getFinalInfoAlpha();
    }

    /**
     * Sets a flag indicating if the Selected animation (if the selected card
     * type implements one) should run immediately after the card is selected,
     * or if it should be delayed. The default behavior is to delay this
     * animation. This is a one-shot override. If set to false, after the card
     * is selected and the selected animation is triggered, this flag is
     * automatically reset to true. This is useful when you want to change the
     * default behavior, and have the selected animation run immediately. One
     * such case could be when focus moves from one row to the other, when
     * instead of delaying the selected animation until the user pauses on a
     * card, it may be desirable to trigger the animation for that card
     * immediately.
     *
     * @param delay True (default) if the selected animation should be delayed
     *            after the card is selected, or false if the animation should
     *            run immediately the next time the card is Selected.
     */
    public void setSelectedAnimationDelayed(boolean delay) {
        mDelaySelectedAnim = delay;
    }

    /**
     * Returns a boolean indicating if the selected animation will run
     * immediately or be delayed the next time the card is Selected.
     *
     * @return true if this card is set to delay the selected animation the next
     *         time it is selected, or false if the selected animation will run
     *         immediately the next time the card is selected.
     */
    public boolean isSelectedAnimationDelayed() {
        return mDelaySelectedAnim;
    }

    /**
     * Sets the type of this Card.
     *
     * @param type The desired card type.
     */
    public void setCardType(int type) {
        if (mCardType != type) {
            if (type >= CARD_TYPE_MAIN_ONLY && type < CARD_TYPE_INVALID) {
                // Valid card type
                mCardType = type;
            } else {
                Log.e(TAG, "Invalid card type specified: " + type
                        + ". Defaulting to type CARD_TYPE_MAIN_ONLY.");
                mCardType = CARD_TYPE_MAIN_ONLY;
            }
            requestLayout();
        }
    }

    /**
     * Returns the type of this Card.
     *
     * @return The type of this card.
     */
    public int getCardType() {
        return mCardType;
    }

    /**
     * Sets the visibility of the info region of the card.
     *
     * @param visibility The region visibility to use for the info region. Must
     *     be one of {@link #CARD_REGION_VISIBLE_ALWAYS},
     *     {@link #CARD_REGION_VISIBLE_SELECTED}, or
     *     {@link #CARD_REGION_VISIBLE_ACTIVATED}.
     */
    public void setInfoVisibility(int visibility) {
        if (mInfoVisibility != visibility) {
            cancelAnimations();
            mInfoVisibility = visibility;
            mInfoVisFraction = getFinalInfoVisFraction();
            requestLayout();
            float newInfoAlpha = getFinalInfoAlpha();
            if (newInfoAlpha != mInfoAlpha) {
                mInfoAlpha = newInfoAlpha;
                for (int i = 0; i < mInfoViewList.size(); i++) {
                    mInfoViewList.get(i).setAlpha(mInfoAlpha);
                }
            }
        }
    }

    final float getFinalInfoVisFraction() {
        return mCardType == CARD_TYPE_INFO_UNDER && mInfoVisibility == CARD_REGION_VISIBLE_SELECTED
                && !isSelected() ? 0.0f : 1.0f;
    }

    final float getFinalInfoAlpha() {
        return mCardType == CARD_TYPE_INFO_OVER && mInfoVisibility == CARD_REGION_VISIBLE_SELECTED
                && !isSelected() ? 0.0f : 1.0f;
    }

    /**
     * Returns the visibility of the info region of the card.
     */
    public int getInfoVisibility() {
        return mInfoVisibility;
    }

    /**
     * Sets the visibility of the extra region of the card.
     *
     * @param visibility The region visibility to use for the extra region. Must
     *     be one of {@link #CARD_REGION_VISIBLE_ALWAYS},
     *     {@link #CARD_REGION_VISIBLE_SELECTED}, or
     *     {@link #CARD_REGION_VISIBLE_ACTIVATED}.
     * @deprecated Extra view's visibility is controlled by {@link #setInfoVisibility(int)}
     */
    @Deprecated
    public void setExtraVisibility(int visibility) {
        if (mExtraVisibility != visibility) {
            mExtraVisibility = visibility;
        }
    }

    /**
     * Returns the visibility of the extra region of the card.
     * @deprecated Extra view's visibility is controlled by {@link #getInfoVisibility()}
     */
    @Deprecated
    public int getExtraVisibility() {
        return mExtraVisibility;
    }

    /**
     * Sets the Activated state of this Card. This can trigger changes in the
     * card layout, resulting in views to become visible or hidden. A card is
     * normally set to Activated state when its parent container (like a Row)
     * receives focus, and then activates all of its children.
     *
     * @param activated True if the card is ACTIVE, or false if INACTIVE.
     * @see #isActivated()
     */
    @Override
    public void setActivated(boolean activated) {
        if (activated != isActivated()) {
            super.setActivated(activated);
            applyActiveState(isActivated());
        }
    }

    /**
     * Sets the Selected state of this Card. This can trigger changes in the
     * card layout, resulting in views to become visible or hidden. A card is
     * normally set to Selected state when it receives input focus.
     *
     * @param selected True if the card is Selected, or false otherwise.
     * @see #isSelected()
     */
    @Override
    public void setSelected(boolean selected) {
        if (selected != isSelected()) {
            super.setSelected(selected);
            applySelectedState(isSelected());
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mMeasuredWidth = 0;
        mMeasuredHeight = 0;
        int state = 0;
        int mainHeight = 0;
        int infoHeight = 0;
        int extraHeight = 0;

        findChildrenViews();

        final int unspecifiedSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        // MAIN is always present
        for (int i = 0; i < mMainViewList.size(); i++) {
            View mainView = mMainViewList.get(i);
            if (mainView.getVisibility() != View.GONE) {
                measureChild(mainView, unspecifiedSpec, unspecifiedSpec);
                mMeasuredWidth = Math.max(mMeasuredWidth, mainView.getMeasuredWidth());
                mainHeight += mainView.getMeasuredHeight();
                state = View.combineMeasuredStates(state, mainView.getMeasuredState());
            }
        }
        setPivotX(mMeasuredWidth / 2);
        setPivotY(mainHeight / 2);


        // The MAIN area determines the card width
        int cardWidthMeasureSpec = MeasureSpec.makeMeasureSpec(mMeasuredWidth, MeasureSpec.EXACTLY);

        if (hasInfoRegion()) {
            for (int i = 0; i < mInfoViewList.size(); i++) {
                View infoView = mInfoViewList.get(i);
                if (infoView.getVisibility() != View.GONE) {
                    measureChild(infoView, cardWidthMeasureSpec, unspecifiedSpec);
                    if (mCardType != CARD_TYPE_INFO_OVER) {
                        infoHeight += infoView.getMeasuredHeight();
                    }
                    state = View.combineMeasuredStates(state, infoView.getMeasuredState());
                }
            }

            if (hasExtraRegion()) {
                for (int i = 0; i < mExtraViewList.size(); i++) {
                    View extraView = mExtraViewList.get(i);
                    if (extraView.getVisibility() != View.GONE) {
                        measureChild(extraView, cardWidthMeasureSpec, unspecifiedSpec);
                        extraHeight += extraView.getMeasuredHeight();
                        state = View.combineMeasuredStates(state, extraView.getMeasuredState());
                    }
                }
            }
        }

        boolean infoAnimating = hasInfoRegion() && mInfoVisibility == CARD_REGION_VISIBLE_SELECTED;
        mMeasuredHeight = (int) (mainHeight
                + (infoAnimating ? (infoHeight * mInfoVisFraction) : infoHeight)
                + extraHeight - (infoAnimating ? 0 : mInfoOffset));

        // Report our final dimensions.
        setMeasuredDimension(View.resolveSizeAndState(mMeasuredWidth + getPaddingLeft()
                + getPaddingRight(), widthMeasureSpec, state),
                View.resolveSizeAndState(mMeasuredHeight + getPaddingTop() + getPaddingBottom(),
                        heightMeasureSpec, state << View.MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        float currBottom = getPaddingTop();

        // MAIN is always present
        for (int i = 0; i < mMainViewList.size(); i++) {
            View mainView = mMainViewList.get(i);
            if (mainView.getVisibility() != View.GONE) {
                mainView.layout(getPaddingLeft(),
                        (int) currBottom,
                                mMeasuredWidth + getPaddingLeft(),
                        (int) (currBottom + mainView.getMeasuredHeight()));
                currBottom += mainView.getMeasuredHeight();
            }
        }

        if (hasInfoRegion()) {
            float infoHeight = 0f;
            for (int i = 0; i < mInfoViewList.size(); i++) {
                infoHeight += mInfoViewList.get(i).getMeasuredHeight();
            }

            if (mCardType == CARD_TYPE_INFO_OVER) {
                // retract currBottom to overlap the info views on top of main
                currBottom -= infoHeight;
                if (currBottom < 0) {
                    currBottom = 0;
                }
            } else if (mCardType == CARD_TYPE_INFO_UNDER) {
                if (mInfoVisibility == CARD_REGION_VISIBLE_SELECTED) {
                    infoHeight = infoHeight * mInfoVisFraction;
                }
            } else {
                currBottom -= mInfoOffset;
            }

            for (int i = 0; i < mInfoViewList.size(); i++) {
                View infoView = mInfoViewList.get(i);
                if (infoView.getVisibility() != View.GONE) {
                    int viewHeight = infoView.getMeasuredHeight();
                    if (viewHeight > infoHeight) {
                        viewHeight = (int) infoHeight;
                    }
                    infoView.layout(getPaddingLeft(),
                            (int) currBottom,
                                    mMeasuredWidth + getPaddingLeft(),
                            (int) (currBottom + viewHeight));
                    currBottom += viewHeight;
                    infoHeight -= viewHeight;
                    if (infoHeight <= 0) {
                        break;
                    }
                }
            }

            if (hasExtraRegion()) {
                for (int i = 0; i < mExtraViewList.size(); i++) {
                    View extraView = mExtraViewList.get(i);
                    if (extraView.getVisibility() != View.GONE) {
                        extraView.layout(getPaddingLeft(),
                                (int) currBottom,
                                        mMeasuredWidth + getPaddingLeft(),
                                (int) (currBottom + extraView.getMeasuredHeight()));
                        currBottom += extraView.getMeasuredHeight();
                    }
                }
            }
        }
        // Force update drawable bounds.
        onSizeChanged(0, 0, right - left, bottom - top);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mAnimationTrigger);
        cancelAnimations();
    }

    private boolean hasInfoRegion() {
        return mCardType != CARD_TYPE_MAIN_ONLY;
    }

    private boolean hasExtraRegion() {
        return mCardType == CARD_TYPE_INFO_UNDER_WITH_EXTRA;
    }

    /**
     * Returns target visibility of info region.
     */
    private boolean isRegionVisible(int regionVisibility) {
        switch (regionVisibility) {
            case CARD_REGION_VISIBLE_ALWAYS:
                return true;
            case CARD_REGION_VISIBLE_ACTIVATED:
                return isActivated();
            case CARD_REGION_VISIBLE_SELECTED:
                return isSelected();
            default:
                if (DEBUG) Log.e(TAG, "invalid region visibility state: " + regionVisibility);
                return false;
        }
    }

    /**
     * Unlike isRegionVisible(), this method returns true when it is fading out when unselected.
     */
    private boolean isCurrentRegionVisible(int regionVisibility) {
        switch (regionVisibility) {
            case CARD_REGION_VISIBLE_ALWAYS:
                return true;
            case CARD_REGION_VISIBLE_ACTIVATED:
                return isActivated();
            case CARD_REGION_VISIBLE_SELECTED:
                if (mCardType == CARD_TYPE_INFO_UNDER) {
                    return mInfoVisFraction > 0f;
                } else {
                    return isSelected();
                }
            default:
                if (DEBUG) Log.e(TAG, "invalid region visibility state: " + regionVisibility);
                return false;
        }
    }

    private void findChildrenViews() {
        mMainViewList.clear();
        mInfoViewList.clear();
        mExtraViewList.clear();

        final int count = getChildCount();

        boolean infoVisible = hasInfoRegion() && isCurrentRegionVisible(mInfoVisibility);
        boolean extraVisible = hasExtraRegion() && mInfoOffset > 0f;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);

            if (child == null) {
                continue;
            }

            BaseCardView.LayoutParams lp = (BaseCardView.LayoutParams) child
                    .getLayoutParams();
            if (lp.viewType == LayoutParams.VIEW_TYPE_INFO) {
                child.setAlpha(mInfoAlpha);
                mInfoViewList.add(child);
                child.setVisibility(infoVisible ? View.VISIBLE : View.GONE);
            } else if (lp.viewType == LayoutParams.VIEW_TYPE_EXTRA) {
                mExtraViewList.add(child);
                child.setVisibility(extraVisible ? View.VISIBLE : View.GONE);
            } else {
                // Default to MAIN
                mMainViewList.add(child);
                child.setVisibility(View.VISIBLE);
            }
        }

    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        // filter out focus states,  since leanback does not fade foreground on focus.
        final int[] s = super.onCreateDrawableState(extraSpace);
        final int N = s.length;
        boolean pressed = false;
        boolean enabled = false;
        for (int i = 0; i < N; i++) {
            if (s[i] == android.R.attr.state_pressed) {
                pressed = true;
            }
            if (s[i] == android.R.attr.state_enabled) {
                enabled = true;
            }
        }
        if (pressed && enabled) {
            return View.PRESSED_ENABLED_STATE_SET;
        } else if (pressed) {
            return LB_PRESSED_STATE_SET;
        } else if (enabled) {
            return View.ENABLED_STATE_SET;
        } else {
            return View.EMPTY_STATE_SET;
        }
    }

    private void applyActiveState(boolean active) {
        if (hasInfoRegion() && mInfoVisibility == CARD_REGION_VISIBLE_ACTIVATED) {
            setInfoViewVisibility(isRegionVisible(mInfoVisibility));
        }
    }

    private void setInfoViewVisibility(boolean visible) {
        if (mCardType == CARD_TYPE_INFO_UNDER_WITH_EXTRA) {
            // Active state changes for card type
            // CARD_TYPE_INFO_UNDER_WITH_EXTRA
            if (visible) {
                for (int i = 0; i < mInfoViewList.size(); i++) {
                    mInfoViewList.get(i).setVisibility(View.VISIBLE);
                }
            } else {
                for (int i = 0; i < mInfoViewList.size(); i++) {
                    mInfoViewList.get(i).setVisibility(View.GONE);
                }
                for (int i = 0; i < mExtraViewList.size(); i++) {
                    mExtraViewList.get(i).setVisibility(View.GONE);
                }
                mInfoOffset = 0.0f;
            }
        } else if (mCardType == CARD_TYPE_INFO_UNDER) {
            // Active state changes for card type CARD_TYPE_INFO_UNDER
            if (mInfoVisibility == CARD_REGION_VISIBLE_SELECTED) {
                animateInfoHeight(visible);
            } else {
                for (int i = 0; i < mInfoViewList.size(); i++) {
                    mInfoViewList.get(i).setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        } else if (mCardType == CARD_TYPE_INFO_OVER) {
            // Active state changes for card type CARD_TYPE_INFO_OVER
            animateInfoAlpha(visible);
        }
    }

    private void applySelectedState(boolean focused) {
        removeCallbacks(mAnimationTrigger);

        if (mCardType == CARD_TYPE_INFO_UNDER_WITH_EXTRA) {
            // Focus changes for card type CARD_TYPE_INFO_UNDER_WITH_EXTRA
            if (focused) {
                if (!mDelaySelectedAnim) {
                    post(mAnimationTrigger);
                    mDelaySelectedAnim = true;
                } else {
                    postDelayed(mAnimationTrigger, mSelectedAnimationDelay);
                }
            } else {
                animateInfoOffset(false);
            }
        } else if (mInfoVisibility == CARD_REGION_VISIBLE_SELECTED) {
            setInfoViewVisibility(focused);
        }
    }

    void cancelAnimations() {
        if (mAnim != null) {
            mAnim.cancel();
            mAnim = null;
            // force-clear the animation, as Animation#cancel() doesn't work prior to N,
            // and will instead cause the animation to infinitely loop
            clearAnimation();
        }
    }

    // This animation changes the Y offset of the info and extra views,
    // so that they animate UP to make the extra info area visible when a
    // card is selected.
    void animateInfoOffset(boolean shown) {
        cancelAnimations();

        int extraHeight = 0;
        if (shown) {
            int widthSpec = MeasureSpec.makeMeasureSpec(mMeasuredWidth, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

            for (int i = 0; i < mExtraViewList.size(); i++) {
                View extraView = mExtraViewList.get(i);
                extraView.setVisibility(View.VISIBLE);
                extraView.measure(widthSpec, heightSpec);
                extraHeight = Math.max(extraHeight, extraView.getMeasuredHeight());
            }
        }

        mAnim = new InfoOffsetAnimation(mInfoOffset, shown ? extraHeight : 0);
        mAnim.setDuration(mSelectedAnimDuration);
        mAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (mInfoOffset == 0f) {
                    for (int i = 0; i < mExtraViewList.size(); i++) {
                        mExtraViewList.get(i).setVisibility(View.GONE);
                    }
                }
            }

                @Override
            public void onAnimationRepeat(Animation animation) {
            }

        });
        startAnimation(mAnim);
    }

    // This animation changes the visible height of the info views,
    // so that they animate in and out of view.
    private void animateInfoHeight(boolean shown) {
        cancelAnimations();

        if (shown) {
            for (int i = 0; i < mInfoViewList.size(); i++) {
                View extraView = mInfoViewList.get(i);
                extraView.setVisibility(View.VISIBLE);
            }
        }

        float targetFraction = shown ? 1.0f : 0f;
        if (mInfoVisFraction == targetFraction) {
            return;
        }
        mAnim = new InfoHeightAnimation(mInfoVisFraction, targetFraction);
        mAnim.setDuration(mSelectedAnimDuration);
        mAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (mInfoVisFraction == 0f) {
                    for (int i = 0; i < mInfoViewList.size(); i++) {
                        mInfoViewList.get(i).setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

        });
        startAnimation(mAnim);
    }

    // This animation changes the alpha of the info views, so they animate in
    // and out. It's meant to be used when the info views are overlaid on top of
    // the main view area. It gets triggered by a change in the Active state of
    // the card.
    private void animateInfoAlpha(boolean shown) {
        cancelAnimations();

        if (shown) {
            for (int i = 0; i < mInfoViewList.size(); i++) {
                mInfoViewList.get(i).setVisibility(View.VISIBLE);
            }
        }
        float targetAlpha = shown ? 1.0f : 0.0f;
        if (targetAlpha == mInfoAlpha) {
            return;
        }

        mAnim = new InfoAlphaAnimation(mInfoAlpha, shown ? 1.0f : 0.0f);
        mAnim.setDuration(mActivatedAnimDuration);
        mAnim.setInterpolator(new DecelerateInterpolator());
        mAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (mInfoAlpha == 0.0) {
                    for (int i = 0; i < mInfoViewList.size(); i++) {
                        mInfoViewList.get(i).setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

        });
        startAnimation(mAnim);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new BaseCardView.LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new BaseCardView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof BaseCardView.LayoutParams;
    }

    /**
     * Per-child layout information associated with BaseCardView.
     */
    public static class LayoutParams extends FrameLayout.LayoutParams {
        public static final int VIEW_TYPE_MAIN = 0;
        public static final int VIEW_TYPE_INFO = 1;
        public static final int VIEW_TYPE_EXTRA = 2;

        /**
         * Card component type for the view associated with these LayoutParams.
         */
        @ViewDebug.ExportedProperty(category = "layout", mapping = {
                @ViewDebug.IntToString(from = VIEW_TYPE_MAIN, to = "MAIN"),
                @ViewDebug.IntToString(from = VIEW_TYPE_INFO, to = "INFO"),
                @ViewDebug.IntToString(from = VIEW_TYPE_EXTRA, to = "EXTRA")
        })
        public int viewType = VIEW_TYPE_MAIN;

        /**
         * {@inheritDoc}
         */
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.lbBaseCardView_Layout);

            viewType = a.getInt(
                    R.styleable.lbBaseCardView_Layout_layout_viewType, VIEW_TYPE_MAIN);

            a.recycle();
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        /**
         * Copy constructor. Clones the width, height, and View Type of the
         * source.
         *
         * @param source The layout params to copy from.
         */
        public LayoutParams(LayoutParams source) {
            super((ViewGroup.MarginLayoutParams) source);

            this.viewType = source.viewType;
        }
    }

    class AnimationBase extends Animation {

        @VisibleForTesting
        final void mockStart() {
            getTransformation(0, null);
        }

        @VisibleForTesting
        final void mockEnd() {
            applyTransformation(1f, null);
            cancelAnimations();
        }
    }

    // Helper animation class used in the animation of the info and extra
    // fields vertically within the card
    final class InfoOffsetAnimation extends AnimationBase {
        private float mStartValue;
        private float mDelta;

        public InfoOffsetAnimation(float start, float end) {
            mStartValue = start;
            mDelta = end - start;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mInfoOffset = mStartValue + (interpolatedTime * mDelta);
            requestLayout();
        }
    }

    // Helper animation class used in the animation of the visible height
    // for the info fields.
    final class InfoHeightAnimation extends AnimationBase {
        private float mStartValue;
        private float mDelta;

        public InfoHeightAnimation(float start, float end) {
            mStartValue = start;
            mDelta = end - start;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mInfoVisFraction = mStartValue + (interpolatedTime * mDelta);
            requestLayout();
        }
    }

    // Helper animation class used to animate the alpha for the info views
    // when they are fading in or out of view.
    final class InfoAlphaAnimation extends AnimationBase {
        private float mStartValue;
        private float mDelta;

        public InfoAlphaAnimation(float start, float end) {
            mStartValue = start;
            mDelta = end - start;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mInfoAlpha = mStartValue + (interpolatedTime * mDelta);
            for (int i = 0; i < mInfoViewList.size(); i++) {
                mInfoViewList.get(i).setAlpha(mInfoAlpha);
            }
        }
    }

    @Override
    public String toString() {
        if (DEBUG) {
            StringBuilder sb = new StringBuilder();
            sb.append(this.getClass().getSimpleName()).append(" : ");
            sb.append("cardType=");
            switch(mCardType) {
                case CARD_TYPE_MAIN_ONLY:
                    sb.append("MAIN_ONLY");
                    break;
                case CARD_TYPE_INFO_OVER:
                    sb.append("INFO_OVER");
                    break;
                case CARD_TYPE_INFO_UNDER:
                    sb.append("INFO_UNDER");
                    break;
                case CARD_TYPE_INFO_UNDER_WITH_EXTRA:
                    sb.append("INFO_UNDER_WITH_EXTRA");
                    break;
                default:
                    sb.append("INVALID");
                    break;
            }
            sb.append(" : ");
            sb.append(mMainViewList.size()).append(" main views, ");
            sb.append(mInfoViewList.size()).append(" info views, ");
            sb.append(mExtraViewList.size()).append(" extra views : ");
            sb.append("infoVisibility=").append(mInfoVisibility).append(" ");
            sb.append("extraVisibility=").append(mExtraVisibility).append(" ");
            sb.append("isActivated=").append(isActivated());
            sb.append(" : ");
            sb.append("isSelected=").append(isSelected());
            return sb.toString();
        } else {
            return super.toString();
        }
    }
}
