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

package androidx.leanback.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.leanback.R;

/**
 * A page indicator with dots.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class PagingIndicator extends View {
    private static final long DURATION_ALPHA = 167;
    private static final long DURATION_DIAMETER = 417;
    private static final long DURATION_TRANSLATION_X = DURATION_DIAMETER;
    private static final TimeInterpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

    private static final Property<Dot, Float> DOT_ALPHA =
            new Property<Dot, Float>(Float.class, "alpha") {
        @Override
        public Float get(Dot dot) {
            return dot.getAlpha();
        }

        @Override
        public void set(Dot dot, Float value) {
            dot.setAlpha(value);
        }
    };

    private static final Property<Dot, Float> DOT_DIAMETER =
            new Property<Dot, Float>(Float.class, "diameter") {
        @Override
        public Float get(Dot dot) {
            return dot.getDiameter();
        }

        @Override
        public void set(Dot dot, Float value) {
            dot.setDiameter(value);
        }
    };

    private static final Property<Dot, Float> DOT_TRANSLATION_X =
            new Property<Dot, Float>(Float.class, "translation_x") {
        @Override
        public Float get(Dot dot) {
            return dot.getTranslationX();
        }

        @Override
        public void set(Dot dot, Float value) {
            dot.setTranslationX(value);
        }
    };

    // attribute
    boolean mIsLtr;
    final int mDotDiameter;
    final int mDotRadius;
    private final int mDotGap;
    final int mArrowDiameter;
    final int mArrowRadius;
    private final int mArrowGap;
    private final int mShadowRadius;
    private Dot[] mDots;
    // X position when the dot is selected.
    private int[] mDotSelectedX;
    // X position when the dot is located to the left of the selected dot.
    private int[] mDotSelectedPrevX;
    // X position when the dot is located to the right of the selected dot.
    private int[] mDotSelectedNextX;
    int mDotCenterY;

    // state
    private int mPageCount;
    private int mCurrentPage;
    private int mPreviousPage;

    // drawing
    @ColorInt
    int mDotFgSelectColor;
    final Paint mBgPaint;
    final Paint mFgPaint;
    private final AnimatorSet mShowAnimator;
    private final AnimatorSet mHideAnimator;
    private final AnimatorSet mAnimator = new AnimatorSet();
    Bitmap mArrow;
    Paint mArrowPaint;
    final Rect mArrowRect;
    final float mArrowToBgRatio;

    public PagingIndicator(Context context) {
        this(context, null, 0);
    }

    public PagingIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagingIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Resources res = getResources();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.PagingIndicator,
                defStyle, 0);
        mDotRadius = getDimensionFromTypedArray(typedArray, R.styleable.PagingIndicator_lbDotRadius,
                R.dimen.lb_page_indicator_dot_radius);
        mDotDiameter = mDotRadius * 2;
        mArrowRadius = getDimensionFromTypedArray(typedArray,
                R.styleable.PagingIndicator_arrowRadius, R.dimen.lb_page_indicator_arrow_radius);
        mArrowDiameter = mArrowRadius * 2;
        mDotGap = getDimensionFromTypedArray(typedArray, R.styleable.PagingIndicator_dotToDotGap,
                R.dimen.lb_page_indicator_dot_gap);
        mArrowGap = getDimensionFromTypedArray(typedArray,
                R.styleable.PagingIndicator_dotToArrowGap, R.dimen.lb_page_indicator_arrow_gap);

        int dotBgColor = getColorFromTypedArray(typedArray, R.styleable.PagingIndicator_dotBgColor,
                R.color.lb_page_indicator_dot);
        mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgPaint.setColor(dotBgColor);
        mDotFgSelectColor = getColorFromTypedArray(typedArray,
                R.styleable.PagingIndicator_arrowBgColor,
                R.color.lb_page_indicator_arrow_background);
        if (mArrowPaint == null && typedArray.hasValue(R.styleable.PagingIndicator_arrowColor)) {
            setArrowColor(typedArray.getColor(R.styleable.PagingIndicator_arrowColor, 0));
        }
        typedArray.recycle();

        mIsLtr = res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
        int shadowColor = res.getColor(R.color.lb_page_indicator_arrow_shadow);
        mShadowRadius = res.getDimensionPixelSize(R.dimen.lb_page_indicator_arrow_shadow_radius);
        mFgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int shadowOffset = res.getDimensionPixelSize(R.dimen.lb_page_indicator_arrow_shadow_offset);
        mFgPaint.setShadowLayer(mShadowRadius, shadowOffset, shadowOffset, shadowColor);
        mArrow = loadArrow();
        mArrowRect = new Rect(0, 0, mArrow.getWidth(), mArrow.getHeight());
        mArrowToBgRatio = (float) mArrow.getWidth() / (float) mArrowDiameter;
        // Initialize animations.
        mShowAnimator = new AnimatorSet();
        mShowAnimator.playTogether(createDotAlphaAnimator(0.0f, 1.0f),
                createDotDiameterAnimator(mDotRadius * 2, mArrowRadius * 2),
                createDotTranslationXAnimator());
        mHideAnimator = new AnimatorSet();
        mHideAnimator.playTogether(createDotAlphaAnimator(1.0f, 0.0f),
                createDotDiameterAnimator(mArrowRadius * 2, mDotRadius * 2),
                createDotTranslationXAnimator());
        mAnimator.playTogether(mShowAnimator, mHideAnimator);
        // Use software layer to show shadows.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    private int getDimensionFromTypedArray(TypedArray typedArray, int attr, int defaultId) {
        return typedArray.getDimensionPixelOffset(attr,
                getResources().getDimensionPixelOffset(defaultId));
    }

    private int getColorFromTypedArray(TypedArray typedArray, int attr, int defaultId) {
        return typedArray.getColor(attr, getResources().getColor(defaultId));
    }

    private Bitmap loadArrow() {
        Bitmap arrow = BitmapFactory.decodeResource(getResources(), R.drawable.lb_ic_nav_arrow);
        if (mIsLtr) {
            return arrow;
        } else {
            Matrix matrix = new Matrix();
            matrix.preScale(-1, 1);
            return Bitmap.createBitmap(arrow, 0, 0, arrow.getWidth(), arrow.getHeight(), matrix,
                    false);
        }
    }

    /**
     * Sets the color of the arrow. This color will take over the value set through the
     * theme attribute {@link R.styleable#PagingIndicator_arrowColor} if provided.
     *
     * @param color the color of the arrow
     */
    public void setArrowColor(@ColorInt int color) {
        if (mArrowPaint == null) {
            mArrowPaint = new Paint();
        }
        mArrowPaint.setColorFilter(new PorterDuffColorFilter(color,
                PorterDuff.Mode.SRC_IN));
    }

    /**
     * Set the background color of the dot. This color will take over the value set through the
     * theme attribute.
     *
     * @param color the background color of the dot
     */
    public void setDotBackgroundColor(@ColorInt int color) {
        mBgPaint.setColor(color);
    }

    /**
     * Sets the background color of the arrow. This color will take over the value set through the
     * theme attribute.
     *
     * @param color the background color of the arrow
     */
    public void setArrowBackgroundColor(@ColorInt int color) {
        mDotFgSelectColor = color;
    }

    private Animator createDotAlphaAnimator(float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(null, DOT_ALPHA, from, to);
        animator.setDuration(DURATION_ALPHA);
        animator.setInterpolator(DECELERATE_INTERPOLATOR);
        return animator;
    }

    private Animator createDotDiameterAnimator(float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(null, DOT_DIAMETER, from, to);
        animator.setDuration(DURATION_DIAMETER);
        animator.setInterpolator(DECELERATE_INTERPOLATOR);
        return animator;
    }

    private Animator createDotTranslationXAnimator() {
        // The direction is determined in the Dot.
        ObjectAnimator animator = ObjectAnimator.ofFloat(null, DOT_TRANSLATION_X,
                -mArrowGap + mDotGap, 0.0f);
        animator.setDuration(DURATION_TRANSLATION_X);
        animator.setInterpolator(DECELERATE_INTERPOLATOR);
        return animator;
    }

    /**
     * Sets the page count.
     */
    public void setPageCount(int pages) {
        if (pages <= 0) {
            throw new IllegalArgumentException("The page count should be a positive integer");
        }
        mPageCount = pages;
        mDots = new Dot[mPageCount];
        for (int i = 0; i < mPageCount; ++i) {
            mDots[i] = new Dot();
        }
        calculateDotPositions();
        setSelectedPage(0);
    }

    /**
     * Called when the page has been selected.
     */
    public void onPageSelected(int pageIndex, boolean withAnimation) {
        if (mCurrentPage == pageIndex) {
            return;
        }
        if (mAnimator.isStarted()) {
            mAnimator.end();
        }
        mPreviousPage = mCurrentPage;
        if (withAnimation) {
            mHideAnimator.setTarget(mDots[mPreviousPage]);
            mShowAnimator.setTarget(mDots[pageIndex]);
            mAnimator.start();
        }
        setSelectedPage(pageIndex);
    }

    private void calculateDotPositions() {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getWidth() - getPaddingRight();
        int requiredWidth = getRequiredWidth();
        int mid = (left + right) / 2;
        mDotSelectedX = new int[mPageCount];
        mDotSelectedPrevX = new int[mPageCount];
        mDotSelectedNextX = new int[mPageCount];
        if (mIsLtr) {
            int startLeft = mid - requiredWidth / 2;
            // mDotSelectedX[0] should be mDotSelectedPrevX[-1] + mArrowGap
            mDotSelectedX[0] = startLeft + mDotRadius - mDotGap + mArrowGap;
            mDotSelectedPrevX[0] = startLeft + mDotRadius;
            mDotSelectedNextX[0] = startLeft + mDotRadius - 2 * mDotGap + 2 * mArrowGap;
            for (int i = 1; i < mPageCount; i++) {
                mDotSelectedX[i] = mDotSelectedPrevX[i - 1] + mArrowGap;
                mDotSelectedPrevX[i] = mDotSelectedPrevX[i - 1] + mDotGap;
                mDotSelectedNextX[i] = mDotSelectedX[i - 1] + mArrowGap;
            }
        } else {
            int startRight = mid + requiredWidth / 2;
            // mDotSelectedX[0] should be mDotSelectedPrevX[-1] - mArrowGap
            mDotSelectedX[0] = startRight - mDotRadius + mDotGap - mArrowGap;
            mDotSelectedPrevX[0] = startRight - mDotRadius;
            mDotSelectedNextX[0] = startRight - mDotRadius + 2 * mDotGap - 2 * mArrowGap;
            for (int i = 1; i < mPageCount; i++) {
                mDotSelectedX[i] = mDotSelectedPrevX[i - 1] - mArrowGap;
                mDotSelectedPrevX[i] = mDotSelectedPrevX[i - 1] - mDotGap;
                mDotSelectedNextX[i] = mDotSelectedX[i - 1] - mArrowGap;
            }
        }
        mDotCenterY = top + mArrowRadius;
        adjustDotPosition();
    }

    @VisibleForTesting
    int getPageCount() {
        return mPageCount;
    }

    @VisibleForTesting
    int[] getDotSelectedX() {
        return mDotSelectedX;
    }

    @VisibleForTesting
    int[] getDotSelectedLeftX() {
        return mDotSelectedPrevX;
    }

    @VisibleForTesting
    int[] getDotSelectedRightX() {
        return mDotSelectedNextX;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = getDesiredHeight();
        int height;
        switch (MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.EXACTLY:
                height = MeasureSpec.getSize(heightMeasureSpec);
                break;
            case MeasureSpec.AT_MOST:
                height = Math.min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec));
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                height = desiredHeight;
                break;
        }
        int desiredWidth = getDesiredWidth();
        int width;
        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.EXACTLY:
                width = MeasureSpec.getSize(widthMeasureSpec);
                break;
            case MeasureSpec.AT_MOST:
                width = Math.min(desiredWidth, MeasureSpec.getSize(widthMeasureSpec));
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                width = desiredWidth;
                break;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        setMeasuredDimension(width, height);
        calculateDotPositions();
    }

    private int getDesiredHeight() {
        return getPaddingTop() + mArrowDiameter + getPaddingBottom() + mShadowRadius;
    }

    private int getRequiredWidth() {
        return 2 * mDotRadius + 2 * mArrowGap + (mPageCount - 3) * mDotGap;
    }

    private int getDesiredWidth() {
        return getPaddingLeft() + getRequiredWidth() + getPaddingRight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < mPageCount; ++i) {
            mDots[i].draw(canvas);
        }
    }

    private void setSelectedPage(int now) {
        if (now == mCurrentPage) {
            return;
        }

        mCurrentPage = now;
        adjustDotPosition();
    }

    private void adjustDotPosition() {
        for (int i = 0; i < mCurrentPage; ++i) {
            mDots[i].deselect();
            mDots[i].mDirection = i == mPreviousPage ? Dot.LEFT : Dot.RIGHT;
            mDots[i].mCenterX = mDotSelectedPrevX[i];
        }
        mDots[mCurrentPage].select();
        mDots[mCurrentPage].mDirection = mPreviousPage < mCurrentPage ? Dot.LEFT : Dot.RIGHT;
        mDots[mCurrentPage].mCenterX = mDotSelectedX[mCurrentPage];
        for (int i = mCurrentPage + 1; i < mPageCount; ++i) {
            mDots[i].deselect();
            mDots[i].mDirection = Dot.RIGHT;
            mDots[i].mCenterX = mDotSelectedNextX[i];
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        boolean isLtr = layoutDirection == View.LAYOUT_DIRECTION_LTR;
        if (mIsLtr != isLtr) {
            mIsLtr = isLtr;
            mArrow = loadArrow();
            if (mDots != null) {
                for (Dot dot : mDots) {
                    dot.onRtlPropertiesChanged();
                }
            }
            calculateDotPositions();
            invalidate();
        }
    }

    public class Dot {
        static final float LEFT = -1;
        static final float RIGHT = 1;
        static final float LTR = 1;
        static final float RTL = -1;

        float mAlpha;
        @ColorInt
        int mFgColor;
        float mTranslationX;
        float mCenterX;
        float mDiameter;
        float mRadius;
        float mArrowImageRadius;
        float mDirection = RIGHT;
        float mLayoutDirection = mIsLtr ? LTR : RTL;

        void select() {
            mTranslationX = 0.0f;
            mCenterX = 0.0f;
            mDiameter = mArrowDiameter;
            mRadius = mArrowRadius;
            mArrowImageRadius = mRadius * mArrowToBgRatio;
            mAlpha = 1.0f;
            adjustAlpha();
        }

        void deselect() {
            mTranslationX = 0.0f;
            mCenterX = 0.0f;
            mDiameter = mDotDiameter;
            mRadius = mDotRadius;
            mArrowImageRadius = mRadius * mArrowToBgRatio;
            mAlpha = 0.0f;
            adjustAlpha();
        }

        public void adjustAlpha() {
            int alpha = Math.round(0xFF * mAlpha);
            int red = Color.red(mDotFgSelectColor);
            int green = Color.green(mDotFgSelectColor);
            int blue = Color.blue(mDotFgSelectColor);
            mFgColor = Color.argb(alpha, red, green, blue);
        }

        public float getAlpha() {
            return mAlpha;
        }

        public void setAlpha(float alpha) {
            this.mAlpha = alpha;
            adjustAlpha();
            invalidate();
        }

        public float getTranslationX() {
            return mTranslationX;
        }

        public void setTranslationX(float translationX) {
            this.mTranslationX = translationX * mDirection * mLayoutDirection;
            invalidate();
        }

        public float getDiameter() {
            return mDiameter;
        }

        public void setDiameter(float diameter) {
            this.mDiameter = diameter;
            this.mRadius = diameter / 2;
            this.mArrowImageRadius = diameter / 2 * mArrowToBgRatio;
            invalidate();
        }

        void draw(Canvas canvas) {
            float centerX = mCenterX + mTranslationX;
            canvas.drawCircle(centerX, mDotCenterY, mRadius, mBgPaint);
            if (mAlpha > 0) {
                mFgPaint.setColor(mFgColor);
                canvas.drawCircle(centerX, mDotCenterY, mRadius, mFgPaint);
                canvas.drawBitmap(mArrow, mArrowRect, new Rect((int) (centerX - mArrowImageRadius),
                        (int) (mDotCenterY - mArrowImageRadius),
                        (int) (centerX + mArrowImageRadius),
                        (int) (mDotCenterY + mArrowImageRadius)), mArrowPaint);
            }
        }

        void onRtlPropertiesChanged() {
            mLayoutDirection = mIsLtr ? LTR : RTL;
        }
    }
}
