/*
 * Copyright (C) 2014 The Android Open Source Project
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
package androidx.leanback.app;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.leanback.R;
import androidx.leanback.widget.BackgroundHelper;

import java.lang.ref.WeakReference;

/**
 * Supports background image continuity between multiple Activities.
 *
 * <p>An Activity should instantiate a BackgroundManager and {@link #attach}
 * to the Activity's window.  When the Activity is started, the background is
 * initialized to the current background values stored in a continuity service.
 * The background continuity service is updated as the background is updated.
 *
 * <p>At some point, for example when it is stopped, the Activity may release
 * its background state.
 *
 * <p>When an Activity is resumed, if the BackgroundManager has not been
 * released, the continuity service is updated from the BackgroundManager state.
 * If the BackgroundManager was released, the BackgroundManager inherits the
 * current state from the continuity service.
 *
 * <p>When the last Activity is destroyed, the background state is reset.
 *
 * <p>Backgrounds consist of several layers, from back to front:
 * <ul>
 *   <li>the background Drawable of the theme</li>
 *   <li>a solid color (set via {@link #setColor})</li>
 *   <li>two Drawables, previous and current (set via {@link #setBitmap} or
 *   {@link #setDrawable}), which may be in transition</li>
 * </ul>
 *
 * <p>BackgroundManager holds references to potentially large bitmap Drawables.
 * Call {@link #release} to release these references when the Activity is not
 * visible.
 */
// TODO: support for multiple app processes requires a proper android service
// instead of the shared memory "service" implemented here. Such a service could
// support continuity between fragments of different applications if desired.
public final class BackgroundManager {

    static final String TAG = "BackgroundManager";
    static final boolean DEBUG = false;

    static final int FULL_ALPHA = 255;
    private static final int CHANGE_BG_DELAY_MS = 500;
    private static final int FADE_DURATION = 500;

    private static final String FRAGMENT_TAG = BackgroundManager.class.getCanonicalName();

    Activity mContext;
    Handler mHandler;
    private View mBgView;
    private BackgroundContinuityService mService;
    private int mThemeDrawableResourceId;
    private BackgroundFragment mFragmentState;
    private boolean mAutoReleaseOnStop = true;

    private int mHeightPx;
    private int mWidthPx;
    int mBackgroundColor;
    Drawable mBackgroundDrawable;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean mAttached;
    private long mLastSetTime;

    private final Interpolator mAccelerateInterpolator;
    private final Interpolator mDecelerateInterpolator;
    final ValueAnimator mAnimator;

    static class BitmapDrawable extends Drawable {

        static final class ConstantState extends Drawable.ConstantState {
            final Bitmap mBitmap;
            final Matrix mMatrix;
            final Paint mPaint = new Paint();

            ConstantState(Bitmap bitmap, Matrix matrix) {
                mBitmap = bitmap;
                mMatrix = matrix != null ? matrix : new Matrix();
                mPaint.setFilterBitmap(true);
            }

            ConstantState(ConstantState copyFrom) {
                mBitmap = copyFrom.mBitmap;
                mMatrix = copyFrom.mMatrix != null ? new Matrix(copyFrom.mMatrix) : new Matrix();
                if (copyFrom.mPaint.getAlpha() != FULL_ALPHA) {
                    mPaint.setAlpha(copyFrom.mPaint.getAlpha());
                }
                if (copyFrom.mPaint.getColorFilter() != null) {
                    mPaint.setColorFilter(copyFrom.mPaint.getColorFilter());
                }
                mPaint.setFilterBitmap(true);
            }

            @Override
            public Drawable newDrawable() {
                return new BitmapDrawable(this);
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        }

        ConstantState mState;
        boolean mMutated;

        BitmapDrawable(Resources resources, Bitmap bitmap) {
            this(resources, bitmap, null);
        }

        BitmapDrawable(Resources resources, Bitmap bitmap, Matrix matrix) {
            mState = new ConstantState(bitmap, matrix);
        }

        BitmapDrawable(ConstantState state) {
            mState = state;
        }

        Bitmap getBitmap() {
            return mState.mBitmap;
        }

        @Override
        public void draw(Canvas canvas) {
            if (mState.mBitmap == null) {
                return;
            }
            if (mState.mPaint.getAlpha() < FULL_ALPHA && mState.mPaint.getColorFilter() != null) {
                throw new IllegalStateException("Can't draw with translucent alpha and color filter");
            }
            canvas.drawBitmap(mState.mBitmap, mState.mMatrix, mState.mPaint);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setAlpha(int alpha) {
            mutate();
            if (mState.mPaint.getAlpha() != alpha) {
                mState.mPaint.setAlpha(alpha);
                invalidateSelf();
            }
        }

        /**
         * Does not invalidateSelf to avoid recursion issues.
         * Caller must ensure appropriate invalidation.
         */
        @Override
        public void setColorFilter(ColorFilter cf) {
            mutate();
            mState.mPaint.setColorFilter(cf);
            invalidateSelf();
        }

        @Override
        public ColorFilter getColorFilter() {
            return mState.mPaint.getColorFilter();
        }

        @Override
        public ConstantState getConstantState() {
            return mState;
        }

        @NonNull
        @Override
        public Drawable mutate() {
            if (!mMutated) {
                mMutated = true;
                mState = new ConstantState(mState);
            }
            return this;
        }
    }

    static final class DrawableWrapper {
        int mAlpha = FULL_ALPHA;
        final Drawable mDrawable;

        public DrawableWrapper(Drawable drawable) {
            mDrawable = drawable;
        }
        public DrawableWrapper(DrawableWrapper wrapper, Drawable drawable) {
            mDrawable = drawable;
            mAlpha = wrapper.mAlpha;
        }

        public Drawable getDrawable() {
            return mDrawable;
        }

        public void setColor(int color) {
            ((ColorDrawable) mDrawable).setColor(color);
        }
    }

    static final class TranslucentLayerDrawable extends LayerDrawable {
        DrawableWrapper[] mWrapper;
        int mAlpha = FULL_ALPHA;
        boolean mSuspendInvalidation;
        WeakReference<BackgroundManager> mManagerWeakReference;

        TranslucentLayerDrawable(BackgroundManager manager, Drawable[] drawables) {
            super(drawables);
            mManagerWeakReference = new WeakReference(manager);
            int count = drawables.length;
            mWrapper = new DrawableWrapper[count];
            for (int i = 0; i < count; i++) {
                mWrapper[i] = new DrawableWrapper(drawables[i]);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            if (mAlpha != alpha) {
                mAlpha = alpha;
                invalidateSelf();
                BackgroundManager manager = mManagerWeakReference.get();
                if (manager != null) {
                    manager.postChangeRunnable();
                }
            }
        }

        void setWrapperAlpha(int wrapperIndex, int alpha) {
            if (mWrapper[wrapperIndex] != null) {
                mWrapper[wrapperIndex].mAlpha = alpha;
                invalidateSelf();
            }
        }

        // Queried by system transitions
        @Override
        public int getAlpha() {
            return mAlpha;
        }

        @Override
        public Drawable mutate() {
            Drawable drawable = super.mutate();
            int count = getNumberOfLayers();
            for (int i = 0; i < count; i++) {
                if (mWrapper[i] != null) {
                    mWrapper[i] = new DrawableWrapper(mWrapper[i], getDrawable(i));
                }
            }
            return drawable;
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean setDrawableByLayerId(int id, Drawable drawable) {
            return updateDrawable(id, drawable) != null;
        }

        public DrawableWrapper updateDrawable(int id, Drawable drawable) {
            super.setDrawableByLayerId(id, drawable);
            for (int i = 0; i < getNumberOfLayers(); i++) {
                if (getId(i) == id) {
                    mWrapper[i] = new DrawableWrapper(drawable);
                    // Must come after mWrapper was updated so it can be seen by updateColorFilter
                    invalidateSelf();
                    return mWrapper[i];
                }
            }
            return null;
        }

        public void clearDrawable(int id, Context context) {
            for (int i = 0; i < getNumberOfLayers(); i++) {
                if (getId(i) == id) {
                    mWrapper[i] = null;
                    if (!(getDrawable(i) instanceof EmptyDrawable)) {
                        super.setDrawableByLayerId(id, createEmptyDrawable(context));
                    }
                    break;
                }
            }
        }

        public int findWrapperIndexById(int id) {
            for (int i = 0; i < getNumberOfLayers(); i++) {
                if (getId(i) == id) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void invalidateDrawable(Drawable who) {
            // Prevent invalidate when temporarily change child drawable's alpha in draw()
            if (!mSuspendInvalidation) {
                super.invalidateDrawable(who);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            for (int i = 0; i < mWrapper.length; i++) {
                final Drawable d;
                // For each child drawable, we multiple Wrapper's alpha and LayerDrawable's alpha
                // temporarily using mSuspendInvalidation to suppress invalidate event.
                if (mWrapper[i] != null && (d = mWrapper[i].getDrawable()) != null) {
                    int alpha = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                            ? DrawableCompat.getAlpha(d) : FULL_ALPHA;
                    final int savedAlpha = alpha;
                    int multiple = 0;
                    if (mAlpha < FULL_ALPHA) {
                        alpha = alpha * mAlpha;
                        multiple++;
                    }
                    if (mWrapper[i].mAlpha < FULL_ALPHA) {
                        alpha = alpha * mWrapper[i].mAlpha;
                        multiple++;
                    }
                    if (multiple == 0) {
                        d.draw(canvas);
                    } else {
                        if (multiple == 1) {
                            alpha = alpha / FULL_ALPHA;
                        } else if (multiple == 2) {
                            alpha = alpha / (FULL_ALPHA * FULL_ALPHA);
                        }
                        try {
                            mSuspendInvalidation = true;
                            d.setAlpha(alpha);
                            d.draw(canvas);
                            d.setAlpha(savedAlpha);
                        } finally {
                            mSuspendInvalidation = false;
                        }
                    }
                }
            }
        }
    }

    TranslucentLayerDrawable createTranslucentLayerDrawable(
            LayerDrawable layerDrawable) {
        int numChildren = layerDrawable.getNumberOfLayers();
        Drawable[] drawables = new Drawable[numChildren];
        for (int i = 0; i < numChildren; i++) {
            drawables[i] = layerDrawable.getDrawable(i);
        }
        TranslucentLayerDrawable result = new TranslucentLayerDrawable(this, drawables);
        for (int i = 0; i < numChildren; i++) {
            result.setId(i, layerDrawable.getId(i));
        }
        return result;
    }

    TranslucentLayerDrawable mLayerDrawable;
    int mImageInWrapperIndex;
    int mImageOutWrapperIndex;
    ChangeBackgroundRunnable mChangeRunnable;
    private boolean mChangeRunnablePending;

    private final Animator.AnimatorListener mAnimationListener = new Animator.AnimatorListener() {
        final Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                postChangeRunnable();
            }
        };

        @Override
        public void onAnimationStart(Animator animation) {
        }
        @Override
        public void onAnimationRepeat(Animator animation) {
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            if (mLayerDrawable != null) {
                mLayerDrawable.clearDrawable(R.id.background_imageout, mContext);
            }
            mHandler.post(mRunnable);
        }
        @Override
        public void onAnimationCancel(Animator animation) {
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mAnimationUpdateListener =
            new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int fadeInAlpha = (Integer) animation.getAnimatedValue();
            if (mImageInWrapperIndex != -1) {
                mLayerDrawable.setWrapperAlpha(mImageInWrapperIndex, fadeInAlpha);
            }
        }
    };

    /**
     * Shared memory continuity service.
     */
    private static class BackgroundContinuityService {
        private static final String TAG = "BackgroundContinuity";
        private static final boolean DEBUG = BackgroundManager.DEBUG;

        private static BackgroundContinuityService sService = new BackgroundContinuityService();

        private int mColor;
        private Drawable mDrawable;
        private int mCount;

        /** Single cache of theme drawable */
        private int mLastThemeDrawableId;
        private WeakReference<Drawable.ConstantState> mLastThemeDrawableState;

        private BackgroundContinuityService() {
            reset();
        }

        private void reset() {
            mColor = Color.TRANSPARENT;
            mDrawable = null;
        }

        public static BackgroundContinuityService getInstance() {
            final int count = sService.mCount++;
            if (DEBUG) Log.v(TAG, "Returning instance with new count " + count);
            return sService;
        }

        public void unref() {
            if (mCount <= 0) throw new IllegalStateException("Can't unref, count " + mCount);
            if (--mCount == 0) {
                if (DEBUG) Log.v(TAG, "mCount is zero, resetting");
                reset();
            }
        }
        public int getColor() {
            return mColor;
        }
        public Drawable getDrawable() {
            return mDrawable;
        }
        public void setColor(int color) {
            mColor = color;
            mDrawable = null;
        }
        public void setDrawable(Drawable drawable) {
            mDrawable = drawable;
        }
        public Drawable getThemeDrawable(Context context, int themeDrawableId) {
            Drawable drawable = null;
            if (mLastThemeDrawableState != null && mLastThemeDrawableId == themeDrawableId) {
                Drawable.ConstantState drawableState = mLastThemeDrawableState.get();
                if (DEBUG) Log.v(TAG, "got cached theme drawable state " + drawableState);
                if (drawableState != null) {
                    drawable = drawableState.newDrawable();
                }
            }
            if (drawable == null) {
                drawable = ContextCompat.getDrawable(context, themeDrawableId);
                if (DEBUG) Log.v(TAG, "loaded theme drawable " + drawable);
                mLastThemeDrawableState = new WeakReference<Drawable.ConstantState>(
                        drawable.getConstantState());
                mLastThemeDrawableId = themeDrawableId;
            }
            // No mutate required because this drawable is never manipulated.
            return drawable;
        }
    }

    Drawable getDefaultDrawable() {
        if (mBackgroundColor != Color.TRANSPARENT) {
            return new ColorDrawable(mBackgroundColor);
        } else {
            return getThemeDrawable();
        }
    }

    private Drawable getThemeDrawable() {
        Drawable drawable = null;
        if (mThemeDrawableResourceId != -1) {
            drawable = mService.getThemeDrawable(mContext, mThemeDrawableResourceId);
        }
        if (drawable == null) {
            drawable = createEmptyDrawable(mContext);
        }
        return drawable;
    }

    /**
     * Returns the BackgroundManager associated with the given Activity.
     * <p>
     * The BackgroundManager will be created on-demand for each individual
     * Activity. Subsequent calls will return the same BackgroundManager created
     * for this Activity.
     */
    public static BackgroundManager getInstance(Activity activity) {
        BackgroundFragment fragment = (BackgroundFragment) activity.getFragmentManager()
                .findFragmentByTag(FRAGMENT_TAG);
        if (fragment != null) {
            BackgroundManager manager = fragment.getBackgroundManager();
            if (manager != null) {
                return manager;
            }
            // manager is null: this is a fragment restored by FragmentManager,
            // fall through to create a BackgroundManager attach to it.
        }
        return new BackgroundManager(activity);
    }

    private BackgroundManager(Activity activity) {
        mContext = activity;
        mService = BackgroundContinuityService.getInstance();
        mHeightPx = mContext.getResources().getDisplayMetrics().heightPixels;
        mWidthPx = mContext.getResources().getDisplayMetrics().widthPixels;
        mHandler = new Handler();

        Interpolator defaultInterpolator = new FastOutLinearInInterpolator();
        mAccelerateInterpolator = AnimationUtils.loadInterpolator(mContext,
                android.R.anim.accelerate_interpolator);
        mDecelerateInterpolator = AnimationUtils.loadInterpolator(mContext,
                android.R.anim.decelerate_interpolator);

        mAnimator = ValueAnimator.ofInt(0, FULL_ALPHA);
        mAnimator.addListener(mAnimationListener);
        mAnimator.addUpdateListener(mAnimationUpdateListener);
        mAnimator.setInterpolator(defaultInterpolator);

        TypedArray ta = activity.getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.windowBackground });
        mThemeDrawableResourceId = ta.getResourceId(0, -1);
        if (mThemeDrawableResourceId < 0) {
            if (DEBUG) Log.v(TAG, "BackgroundManager no window background resource!");
        }
        ta.recycle();

        createFragment(activity);
    }

    private void createFragment(Activity activity) {
        // Use a fragment to ensure the background manager gets detached properly.
        BackgroundFragment fragment = (BackgroundFragment) activity.getFragmentManager()
                .findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new BackgroundFragment();
            activity.getFragmentManager().beginTransaction().add(fragment, FRAGMENT_TAG).commit();
        } else {
            if (fragment.getBackgroundManager() != null) {
                throw new IllegalStateException("Created duplicated BackgroundManager for same "
                        + "activity, please use getInstance() instead");
            }
        }
        fragment.setBackgroundManager(this);
        mFragmentState = fragment;
    }

    DrawableWrapper getImageInWrapper() {
        return mLayerDrawable == null
                ? null : mLayerDrawable.mWrapper[mImageInWrapperIndex];
    }

    DrawableWrapper getImageOutWrapper() {
        return mLayerDrawable == null
                ? null : mLayerDrawable.mWrapper[mImageOutWrapperIndex];
    }

    /**
     * Synchronizes state when the owning Activity is started.
     * At that point the view becomes visible.
     */
    void onActivityStart() {
        updateImmediate();
    }

    void onStop() {
        if (isAutoReleaseOnStop()) {
            release();
        }
    }

    void onResume() {
        if (DEBUG) Log.v(TAG, "onResume " + this);
        postChangeRunnable();
    }

    private void syncWithService() {
        int color = mService.getColor();
        Drawable drawable = mService.getDrawable();

        if (DEBUG) Log.v(TAG, "syncWithService color " + Integer.toHexString(color)
                + " drawable " + drawable);

        mBackgroundColor = color;
        mBackgroundDrawable = drawable == null ? null :
            drawable.getConstantState().newDrawable().mutate();

        updateImmediate();
    }

    /**
     * Makes the background visible on the given Window. The background manager must be attached
     * when the background is set.
     */
    public void attach(Window window) {
        attachToViewInternal(window.getDecorView());
    }

    /**
     * Sets the resource id for the drawable to be shown when there is no background set.
     * Overrides the window background drawable from the theme. This should
     * be called before attaching.
     */
    public void setThemeDrawableResourceId(int resourceId) {
        mThemeDrawableResourceId = resourceId;
    }

    /**
     * Adds the composite drawable to the given view.
     */
    public void attachToView(View sceneRoot) {
        attachToViewInternal(sceneRoot);
        // clear background to reduce overdraw since the View will act as background.
        // Activity transition below O has ghost effect for null window background where we
        // need set a transparent background to force redraw the whole window.
        mContext.getWindow().getDecorView().setBackground(
                Build.VERSION.SDK_INT >= 26 ? null : new ColorDrawable(Color.TRANSPARENT));
    }

    void attachToViewInternal(View sceneRoot) {
        if (mAttached) {
            throw new IllegalStateException("Already attached to " + mBgView);
        }
        mBgView = sceneRoot;
        mAttached = true;
        syncWithService();
    }

    /**
     * Returns true if the background manager is currently attached; false otherwise.
     */
    public boolean isAttached() {
        return mAttached;
    }

    /**
     * Release references to Drawables and put the BackgroundManager into the
     * detached state. Called when the associated Activity is destroyed.
     */
    void detach() {
        if (DEBUG) Log.v(TAG, "detach " + this);
        release();

        mBgView = null;
        mAttached = false;

        if (mService != null) {
            mService.unref();
            mService = null;
        }
    }

    /**
     * Release references to Drawable/Bitmap. Typically called in Activity onStop() to reduce memory
     * overhead when not visible. It's app's responsibility to restore the drawable/bitmap in
     * Activity onStart(). The method is automatically called in onStop() when
     * {@link #isAutoReleaseOnStop()} is true.
     * @see #setAutoReleaseOnStop(boolean)
     */
    public void release() {
        if (DEBUG) Log.v(TAG, "release " + this);
        if (mChangeRunnable != null) {
            mHandler.removeCallbacks(mChangeRunnable);
            mChangeRunnable = null;
        }
        if (mAnimator.isStarted()) {
            mAnimator.cancel();
        }
        if (mLayerDrawable != null) {
            mLayerDrawable.clearDrawable(R.id.background_imagein, mContext);
            mLayerDrawable.clearDrawable(R.id.background_imageout, mContext);
            mLayerDrawable = null;
        }
        mBackgroundDrawable = null;
    }

    /**
     * Sets the drawable used as a dim layer.
     * @deprecated No longer support dim layer.
     */
    @Deprecated
    public void setDimLayer(Drawable drawable) {
    }

    /**
     * Returns the drawable used as a dim layer.
     * @deprecated No longer support dim layer.
     */
    @Deprecated
    public Drawable getDimLayer() {
        return null;
    }

    /**
     * Returns the default drawable used as a dim layer.
     * @deprecated No longer support dim layer.
     */
    @Deprecated
    public Drawable getDefaultDimLayer() {
        return ContextCompat.getDrawable(mContext, R.color.lb_background_protection);
    }

    void postChangeRunnable() {
        if (mChangeRunnable == null || !mChangeRunnablePending) {
            return;
        }

        // Postpone a pending change runnable until: no existing change animation in progress &&
        // activity is resumed (in the foreground) && layerdrawable fully opaque.
        // If the layerdrawable is translucent then an activity transition is in progress
        // and we want to use the optimized drawing path for performance reasons (see
        // OptimizedTranslucentLayerDrawable).
        if (mAnimator.isStarted()) {
            if (DEBUG) Log.v(TAG, "animation in progress");
        } else if (!mFragmentState.isResumed()) {
            if (DEBUG) Log.v(TAG, "not resumed");
        } else if (mLayerDrawable.getAlpha() < FULL_ALPHA) {
            if (DEBUG) Log.v(TAG, "in transition, alpha " + mLayerDrawable.getAlpha());
        } else {
            long delayMs = getRunnableDelay();
            if (DEBUG) Log.v(TAG, "posting runnable delayMs " + delayMs);
            mLastSetTime = System.currentTimeMillis();
            mHandler.postDelayed(mChangeRunnable, delayMs);
            mChangeRunnablePending = false;
        }
    }

    private void lazyInit() {
        if (mLayerDrawable != null) {
            return;
        }

        LayerDrawable layerDrawable = (LayerDrawable)
                ContextCompat.getDrawable(mContext, R.drawable.lb_background).mutate();
        mLayerDrawable = createTranslucentLayerDrawable(layerDrawable);
        mImageInWrapperIndex = mLayerDrawable.findWrapperIndexById(R.id.background_imagein);
        mImageOutWrapperIndex = mLayerDrawable.findWrapperIndexById(R.id.background_imageout);
        BackgroundHelper.setBackgroundPreservingAlpha(mBgView, mLayerDrawable);
    }

    private void updateImmediate() {
        if (!mAttached) {
            return;
        }
        lazyInit();

        if (mBackgroundDrawable == null) {
            if (DEBUG) Log.v(TAG, "Use defefault background");
            mLayerDrawable.updateDrawable(R.id.background_imagein, getDefaultDrawable());
        } else {
            if (DEBUG) Log.v(TAG, "Background drawable is available " + mBackgroundDrawable);
            mLayerDrawable.updateDrawable(R.id.background_imagein, mBackgroundDrawable);
        }
        mLayerDrawable.clearDrawable(R.id.background_imageout, mContext);
    }

    /**
     * Sets the background to the given color. The timing for when this becomes
     * visible in the app is undefined and may take place after a small delay.
     */
    public void setColor(@ColorInt int color) {
        if (DEBUG) Log.v(TAG, "setColor " + Integer.toHexString(color));

        mService.setColor(color);
        mBackgroundColor = color;
        mBackgroundDrawable = null;
        if (mLayerDrawable == null) {
            return;
        }
        setDrawableInternal(getDefaultDrawable());
    }

    /**
     * Sets the given drawable into the background. The provided Drawable will be
     * used unmodified as the background, without any scaling or cropping
     * applied to it. The timing for when this becomes visible in the app is
     * undefined and may take place after a small delay.
     */
    public void setDrawable(Drawable drawable) {
        if (DEBUG) Log.v(TAG, "setBackgroundDrawable " + drawable);

        mService.setDrawable(drawable);
        mBackgroundDrawable = drawable;
        if (mLayerDrawable == null) {
            return;
        }
        if (drawable == null) {
            setDrawableInternal(getDefaultDrawable());
        } else {
            setDrawableInternal(drawable);
        }
    }

    /**
     * Clears the Drawable set by {@link #setDrawable(Drawable)} or {@link #setBitmap(Bitmap)}.
     * BackgroundManager will show a solid color set by {@link #setColor(int)} or theme drawable
     * if color is not provided.
     */
    public void clearDrawable() {
        setDrawable(null);
    }

    private void setDrawableInternal(Drawable drawable) {
        if (!mAttached) {
            throw new IllegalStateException("Must attach before setting background drawable");
        }

        if (mChangeRunnable != null) {
            if (sameDrawable(drawable, mChangeRunnable.mDrawable)) {
                if (DEBUG) Log.v(TAG, "new drawable same as pending");
                return;
            }
            mHandler.removeCallbacks(mChangeRunnable);
            mChangeRunnable = null;
        }

        mChangeRunnable = new ChangeBackgroundRunnable(drawable);
        mChangeRunnablePending = true;

        postChangeRunnable();
    }

    private long getRunnableDelay() {
        return Math.max(0, mLastSetTime + CHANGE_BG_DELAY_MS - System.currentTimeMillis());
    }

    /**
     * Sets the given bitmap into the background. When using setCoverImageBitmap to set the
     * background, the provided bitmap will be scaled and cropped to correctly
     * fit within the dimensions of the view. The timing for when this becomes
     * visible in the app is undefined and may take place after a small delay.
     */
    public void setBitmap(Bitmap bitmap) {
        if (DEBUG) {
            Log.v(TAG, "setCoverImageBitmap " + bitmap);
        }

        if (bitmap == null) {
            setDrawable(null);
            return;
        }

        if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            if (DEBUG) {
                Log.v(TAG, "invalid bitmap width or height");
            }
            return;
        }

        Matrix matrix = null;

        if ((bitmap.getWidth() != mWidthPx || bitmap.getHeight() != mHeightPx)) {
            int dwidth = bitmap.getWidth();
            int dheight = bitmap.getHeight();
            float scale;

            // Scale proportionately to fit width and height.
            if (dwidth * mHeightPx > mWidthPx * dheight) {
                scale = (float) mHeightPx / (float) dheight;
            } else {
                scale = (float) mWidthPx / (float) dwidth;
            }

            int subX = Math.min((int) (mWidthPx / scale), dwidth);
            int dx = Math.max(0, (dwidth - subX) / 2);

            matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.preTranslate(-dx, 0);

            if (DEBUG) {
                Log.v(TAG, "original image size " + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + " scale " + scale + " dx " + dx);
            }
        }

        BitmapDrawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), bitmap, matrix);

        setDrawable(bitmapDrawable);
    }

    /**
     * Enable or disable call release() in Activity onStop(). Default is true.
     * @param autoReleaseOnStop True to call release() in Activity onStop(), false otherwise.
     */
    public void setAutoReleaseOnStop(boolean autoReleaseOnStop) {
        mAutoReleaseOnStop = autoReleaseOnStop;
    }

    /**
     * @return True if release() in Activity.onStop(), false otherwise.
     */
    public boolean isAutoReleaseOnStop() {
        return mAutoReleaseOnStop;
    }

    /**
     * Returns the current background color.
     */
    @ColorInt
    public final int getColor() {
        return mBackgroundColor;
    }

    /**
     * Returns the current background {@link Drawable}.
     */
    public Drawable getDrawable() {
        return mBackgroundDrawable;
    }

    boolean sameDrawable(Drawable first, Drawable second) {
        if (first == null || second == null) {
            return false;
        }
        if (first == second) {
            return true;
        }
        if (first instanceof BitmapDrawable && second instanceof BitmapDrawable) {
            if (((BitmapDrawable) first).getBitmap().sameAs(((BitmapDrawable) second).getBitmap())) {
                return true;
            }
        }
        if (first instanceof ColorDrawable && second instanceof ColorDrawable) {
            if (((ColorDrawable) first).getColor() == ((ColorDrawable) second).getColor()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Task which changes the background.
     */
    final class ChangeBackgroundRunnable implements Runnable {
        final Drawable mDrawable;

        ChangeBackgroundRunnable(Drawable drawable) {
            mDrawable = drawable;
        }

        @Override
        public void run() {
            runTask();
            mChangeRunnable = null;
        }

        private void runTask() {
            if (mLayerDrawable == null) {
                if (DEBUG) Log.v(TAG, "runTask while released - should not happen");
                return;
            }

            DrawableWrapper imageInWrapper = getImageInWrapper();
            if (imageInWrapper != null) {
                if (sameDrawable(mDrawable, imageInWrapper.getDrawable())) {
                    if (DEBUG) Log.v(TAG, "new drawable same as current");
                    return;
                }

                if (DEBUG) Log.v(TAG, "moving image in to image out");
                // Order is important! Setting a drawable "removes" the
                // previous one from the view
                mLayerDrawable.clearDrawable(R.id.background_imagein, mContext);
                mLayerDrawable.updateDrawable(R.id.background_imageout,
                        imageInWrapper.getDrawable());
            }

            applyBackgroundChanges();
        }

        void applyBackgroundChanges() {
            if (!mAttached) {
                return;
            }

            if (DEBUG) Log.v(TAG, "applyBackgroundChanges drawable " + mDrawable);

            DrawableWrapper imageInWrapper = getImageInWrapper();
            if (imageInWrapper == null && mDrawable != null) {
                if (DEBUG) Log.v(TAG, "creating new imagein drawable");
                imageInWrapper = mLayerDrawable.updateDrawable(
                        R.id.background_imagein, mDrawable);
                if (DEBUG) Log.v(TAG, "imageInWrapper animation starting");
                mLayerDrawable.setWrapperAlpha(mImageInWrapperIndex, 0);
            }

            mAnimator.setDuration(FADE_DURATION);
            mAnimator.start();

        }

    }

    static class EmptyDrawable extends BitmapDrawable {
        EmptyDrawable(Resources res) {
            super(res, (Bitmap) null);
        }
    }

    static Drawable createEmptyDrawable(Context context) {
        return new EmptyDrawable(context.getResources());
    }

}
