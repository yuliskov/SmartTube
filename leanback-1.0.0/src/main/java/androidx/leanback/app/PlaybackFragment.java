// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from PlaybackSupportFragment.java.  DO NOT MODIFY. */

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
package androidx.leanback.app;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import android.app.Fragment;
import androidx.leanback.R;
import androidx.leanback.animation.LogAccelerateInterpolator;
import androidx.leanback.animation.LogDecelerateInterpolator;
import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseOnItemViewClickedListener;
import androidx.leanback.widget.BaseOnItemViewSelectedListener;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.ItemAlignmentFacet;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackSeekDataProvider;
import androidx.leanback.widget.PlaybackSeekUi;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SparseArrayObjectAdapter;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A fragment for displaying playback controls and related content.
 *
 * <p>
 * A PlaybackFragment renders the elements of its {@link ObjectAdapter} as a set
 * of rows in a vertical list.  The Adapter's {@link PresenterSelector} must maintain subclasses
 * of {@link RowPresenter}.
 * </p>
 * <p>
 * A playback row is a row rendered by {@link PlaybackRowPresenter}.
 * App can call {@link #setPlaybackRow(Row)} to set playback row for the first element of adapter.
 * App can call {@link #setPlaybackRowPresenter(PlaybackRowPresenter)} to set presenter for it.
 * {@link #setPlaybackRow(Row)} and {@link #setPlaybackRowPresenter(PlaybackRowPresenter)} are
 * optional, app can pass playback row and PlaybackRowPresenter in the adapter using
 * {@link #setAdapter(ObjectAdapter)}.
 * </p>
 * <p>
 * Auto hide controls upon playing: best practice is calling
 * {@link #setControlsOverlayAutoHideEnabled(boolean)} upon play/pause.
 * Theme attribute {@link R.attr#playbackControlsAutoHideTimeout} controls how long auto-hide will
 * wait after media starts playing.
 * The auto hiding timer will be cancelled upon {@link #tickle()} triggered by input event.
 * By default fragment does not auto hide controls after user interaction. To enable it: set
 * theme attribute {@link R.attr#playbackControlsAutoHideTickleTimeout}, an auto hide
 * timer will be created when tickle() is triggered by input event.
 * </p>
 * @deprecated use {@link PlaybackSupportFragment}
 */
@Deprecated
public class PlaybackFragment extends Fragment {
    static final String BUNDLE_CONTROL_VISIBLE_ON_CREATEVIEW = "controlvisible_oncreateview";

    /**
     * No background.
     */
    public static final int BG_NONE = 0;

    /**
     * A dark translucent background.
     */
    public static final int BG_DARK = 1;
    PlaybackGlueHost.HostCallback mHostCallback;

    PlaybackSeekUi.Client mSeekUiClient;
    boolean mInSeek;
    ProgressBarManager mProgressBarManager = new ProgressBarManager();

    /**
     * Resets the focus on the button in the middle of control row.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void resetFocus() {
        ItemBridgeAdapter.ViewHolder vh = (ItemBridgeAdapter.ViewHolder) getVerticalGridView()
                .findViewHolderForAdapterPosition(0);
        if (vh != null && vh.getPresenter() instanceof PlaybackRowPresenter) {
            ((PlaybackRowPresenter) vh.getPresenter()).onReappear(
                    (RowPresenter.ViewHolder) vh.getViewHolder());
        }
    }

    private class SetSelectionRunnable implements Runnable {
        int mPosition;
        boolean mSmooth = true;

        SetSelectionRunnable() {
        }

        @Override
        public void run() {
            if (mRowsFragment == null) {
                return;
            }
            mRowsFragment.setSelectedPosition(mPosition, mSmooth);
        }
    }

    /**
     * A light translucent background.
     */
    public static final int BG_LIGHT = 2;
    RowsFragment mRowsFragment;
    ObjectAdapter mAdapter;
    PlaybackRowPresenter mPresenter;
    Row mRow;
    BaseOnItemViewSelectedListener mExternalItemSelectedListener;
    BaseOnItemViewClickedListener mExternalItemClickedListener;
    BaseOnItemViewClickedListener mPlaybackItemClickedListener;

    private final BaseOnItemViewClickedListener mOnItemViewClickedListener =
            new BaseOnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder,
                                          Object item,
                                          RowPresenter.ViewHolder rowViewHolder,
                                          Object row) {
                    if (mPlaybackItemClickedListener != null
                            && rowViewHolder instanceof PlaybackRowPresenter.ViewHolder) {
                        mPlaybackItemClickedListener.onItemClicked(
                                itemViewHolder, item, rowViewHolder, row);
                    }
                    if (mExternalItemClickedListener != null) {
                        mExternalItemClickedListener.onItemClicked(
                                itemViewHolder, item, rowViewHolder, row);
                    }
                }
            };

    private final BaseOnItemViewSelectedListener mOnItemViewSelectedListener =
            new BaseOnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder,
                                           Object item,
                                           RowPresenter.ViewHolder rowViewHolder,
                                           Object row) {
                    if (mExternalItemSelectedListener != null) {
                        mExternalItemSelectedListener.onItemSelected(
                                itemViewHolder, item, rowViewHolder, row);
                    }
                }
            };

    private final SetSelectionRunnable mSetSelectionRunnable = new SetSelectionRunnable();

    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Listener allowing the application to receive notification of fade in and/or fade out
     * completion events.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static class OnFadeCompleteListener {
        public void onFadeInComplete() {
        }

        public void onFadeOutComplete() {
        }
    }

    private static final String TAG = "PlaybackFragment";
    private static final boolean DEBUG = false;
    private static final int ANIMATION_MULTIPLIER = 1;

    private static final int START_FADE_OUT = 1;

    // Fading status
    private static final int IDLE = 0;
    private static final int ANIMATING = 1;

    int mPaddingBottom;
    int mOtherRowsCenterToBottom;
    View mRootView;
    View mBackgroundView;
    int mBackgroundType = BG_DARK;
    int mBgDarkColor;
    int mBgLightColor;
    int mAutohideTimerAfterPlayingInMs;
    int mAutohideTimerAfterTickleInMs;
    int mMajorFadeTranslateY, mMinorFadeTranslateY;
    int mAnimationTranslateY;
    OnFadeCompleteListener mFadeCompleteListener;
    View.OnKeyListener mInputEventHandler;
    boolean mFadingEnabled = true;
    boolean mControlVisibleBeforeOnCreateView = true;
    boolean mControlVisible = true;
    int mBgAlpha;
    ValueAnimator mBgFadeInAnimator, mBgFadeOutAnimator;
    ValueAnimator mControlRowFadeInAnimator, mControlRowFadeOutAnimator;
    ValueAnimator mOtherRowFadeInAnimator, mOtherRowFadeOutAnimator;

    private final Animator.AnimatorListener mFadeListener =
            new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    enableVerticalGridAnimations(false);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (DEBUG) Log.v(TAG, "onAnimationEnd " + mBgAlpha);
                    if (mBgAlpha > 0) {
                        enableVerticalGridAnimations(true);
                        if (mFadeCompleteListener != null) {
                            mFadeCompleteListener.onFadeInComplete();
                        }
                    } else {
                        VerticalGridView verticalView = getVerticalGridView();
                        // reset focus to the primary actions only if the selected row was the controls row
                        if (verticalView != null && verticalView.getSelectedPosition() == 0) {
                            ItemBridgeAdapter.ViewHolder vh = (ItemBridgeAdapter.ViewHolder)
                                    verticalView.findViewHolderForAdapterPosition(0);
                            if (vh != null && vh.getPresenter() instanceof PlaybackRowPresenter) {
                                ((PlaybackRowPresenter)vh.getPresenter()).onReappear(
                                        (RowPresenter.ViewHolder) vh.getViewHolder());
                            }
                        }
                        if (mFadeCompleteListener != null) {
                            mFadeCompleteListener.onFadeOutComplete();
                        }
                    }
                }
            };

    public PlaybackFragment() {
        mProgressBarManager.setInitialDelay(500);
    }

    VerticalGridView getVerticalGridView() {
        if (mRowsFragment == null) {
            return null;
        }
        return mRowsFragment.getVerticalGridView();
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            if (message.what == START_FADE_OUT && mFadingEnabled) {
                hideControlsOverlay(true);
            }
        }
    };

    private final VerticalGridView.OnTouchInterceptListener mOnTouchInterceptListener =
            new VerticalGridView.OnTouchInterceptListener() {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    return onInterceptInputEvent(event);
                }
            };

    private final VerticalGridView.OnKeyInterceptListener mOnKeyInterceptListener =
            new VerticalGridView.OnKeyInterceptListener() {
                @Override
                public boolean onInterceptKeyEvent(KeyEvent event) {
                    return onInterceptInputEvent(event);
                }
            };

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void setBgAlpha(int alpha) {
        mBgAlpha = alpha;
        if (mBackgroundView != null) {
            mBackgroundView.getBackground().setAlpha(alpha);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void enableVerticalGridAnimations(boolean enable) {
        if (getVerticalGridView() != null) {
            getVerticalGridView().setAnimateChildLayout(enable);
        }
    }

    /**
     * Enables or disables auto hiding controls overlay after a short delay fragment is resumed.
     * If enabled and fragment is resumed, the view will fade out after a time period.
     * {@link #tickle()} will kill the timer, next time fragment is resumed,
     * the timer will be started again if {@link #isControlsOverlayAutoHideEnabled()} is true.
     *  <p>
     *  In most cases app does not need call tickle() as it's automatically called by
     *  {@link androidx.leanback.media.PlaybackBaseControlGlue} on user interactions.
     */
    public void setControlsOverlayAutoHideEnabled(boolean enabled) {
        if (DEBUG) Log.v(TAG, "setControlsOverlayAutoHideEnabled " + enabled);
        if (enabled != mFadingEnabled) {
            mFadingEnabled = enabled;
            if (isResumed() && getView().hasFocus()) {
                showControlsOverlay(true);
                if (enabled) {
                    // StateGraph 7->2 5->2
                    startFadeTimer(mAutohideTimerAfterPlayingInMs);
                } else {
                    // StateGraph 4->5 2->5
                    stopFadeTimer();
                }
            } else {
                // StateGraph 6->1 1->6
            }
        }
    }

    /**
     * Returns true if controls will be auto hidden after a delay when fragment is resumed.
     */
    public boolean isControlsOverlayAutoHideEnabled() {
        return mFadingEnabled;
    }

    /**
     * @deprecated Uses {@link #setControlsOverlayAutoHideEnabled(boolean)}
     */
    @Deprecated
    public void setFadingEnabled(boolean enabled) {
        setControlsOverlayAutoHideEnabled(enabled);
    }

    /**
     * @deprecated Uses {@link #isControlsOverlayAutoHideEnabled()}
     */
    @Deprecated
    public boolean isFadingEnabled() {
        return isControlsOverlayAutoHideEnabled();
    }

    /**
     * Sets the listener to be called when fade in or out has completed.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void setFadeCompleteListener(OnFadeCompleteListener listener) {
        mFadeCompleteListener = listener;
    }

    /**
     * Returns the listener to be called when fade in or out has completed.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public OnFadeCompleteListener getFadeCompleteListener() {
        return mFadeCompleteListener;
    }

    /**
     * Sets the input event handler.
     */
    public final void setOnKeyInterceptListener(View.OnKeyListener handler) {
        mInputEventHandler = handler;
    }

    /**
     * Tickles the playback controls. Fades in the view if it was faded out. {@link #tickle()} will
     * also kill the timer created by {@link #setControlsOverlayAutoHideEnabled(boolean)}. When
     * next time fragment is resumed, the timer will be started again if
     * {@link #isControlsOverlayAutoHideEnabled()} is true. The timer will also be restarted if
     * app sets a positive value on theme attribute
     * {@link R.attr#playbackControlsAutoHideTickleTimeout}.
     *  <p>
     *  In most cases app does not need call tickle() as it's automatically called by
     *  {@link androidx.leanback.media.PlaybackBaseControlGlue} on user interactions.
     */
    public void tickle() {
        if (DEBUG) Log.v(TAG, "tickle enabled " + mFadingEnabled + " isResumed " + isResumed());
        //StateGraph 2->4
        stopFadeTimer();
        showControlsOverlay(true);
        // Optionally start fading out timer if it's currently playing (mFadingEnabled is true)
        if (mAutohideTimerAfterTickleInMs > 0 && mFadingEnabled) {
            startFadeTimer(mAutohideTimerAfterTickleInMs);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean onInterceptInputEvent(InputEvent event) {
        final boolean controlsHidden = !mControlVisible;
        if (DEBUG) Log.v(TAG, "onInterceptInputEvent hidden " + controlsHidden + " " + event);
        boolean consumeEvent = false;
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        int keyAction = 0;

        if (event instanceof KeyEvent) {
            keyCode = ((KeyEvent) event).getKeyCode();
            keyAction = ((KeyEvent) event).getAction();
            if (mInputEventHandler != null) {
                consumeEvent = mInputEventHandler.onKey(getView(), keyCode, (KeyEvent) event);
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Event may be consumed; regardless, if controls are hidden then these keys will
                // bring up the controls.
                if (controlsHidden) {
                    consumeEvent = true;
                }
                if (keyAction == KeyEvent.ACTION_DOWN) {
                    tickle();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (mInSeek) {
                    // when in seek, the SeekUi will handle the BACK.
                    return false;
                }
                // If controls are not hidden, back will be consumed to fade
                // them out (even if the key was consumed by the handler).
                if (!controlsHidden) {
                    consumeEvent = true;

                    if (((KeyEvent) event).getAction() == KeyEvent.ACTION_UP) {
                        hideControlsOverlay(true);
                    }
                }
                break;
            default:
                if (consumeEvent) {
                    if (keyAction == KeyEvent.ACTION_DOWN) {
                        tickle();
                    }
                }
        }
        return consumeEvent;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // controls view are initially visible, make it invisible
        // if app has called hideControlsOverlay() before view created.
        mControlVisible = true;
        if (!mControlVisibleBeforeOnCreateView) {
            showControlsOverlay(false, false);
            mControlVisibleBeforeOnCreateView = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mControlVisible) {
            //StateGraph: 6->5 1->2
            if (mFadingEnabled) {
                // StateGraph 1->2
                startFadeTimer(mAutohideTimerAfterPlayingInMs);
            }
        } else {
            //StateGraph: 6->7 1->3
        }
        getVerticalGridView().setOnTouchInterceptListener(mOnTouchInterceptListener);
        getVerticalGridView().setOnKeyInterceptListener(mOnKeyInterceptListener);
        if (mHostCallback != null) {
            mHostCallback.onHostResume();
        }
    }

    private void stopFadeTimer() {
        if (mHandler != null) {
            mHandler.removeMessages(START_FADE_OUT);
        }
    }

    private void startFadeTimer(int fadeOutTimeout) {
        if (mHandler != null) {
            mHandler.removeMessages(START_FADE_OUT);
            mHandler.sendEmptyMessageDelayed(START_FADE_OUT, fadeOutTimeout);
        }
    }

    private static ValueAnimator loadAnimator(Context context, int resId) {
        ValueAnimator animator = (ValueAnimator) AnimatorInflater.loadAnimator(context, resId);
        animator.setDuration(animator.getDuration() * ANIMATION_MULTIPLIER);
        return animator;
    }

    private void loadBgAnimator() {
        AnimatorUpdateListener listener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                setBgAlpha((Integer) arg0.getAnimatedValue());
            }
        };

        Context context = FragmentUtil.getContext(PlaybackFragment.this);
        mBgFadeInAnimator = loadAnimator(context, R.animator.lb_playback_bg_fade_in);
        mBgFadeInAnimator.addUpdateListener(listener);
        mBgFadeInAnimator.addListener(mFadeListener);

        mBgFadeOutAnimator = loadAnimator(context, R.animator.lb_playback_bg_fade_out);
        mBgFadeOutAnimator.addUpdateListener(listener);
        mBgFadeOutAnimator.addListener(mFadeListener);
    }

    private TimeInterpolator mLogDecelerateInterpolator = new LogDecelerateInterpolator(100, 0);
    private TimeInterpolator mLogAccelerateInterpolator = new LogAccelerateInterpolator(100, 0);

    private void loadControlRowAnimator() {
        final AnimatorUpdateListener updateListener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                if (getVerticalGridView() == null) {
                    return;
                }
                RecyclerView.ViewHolder vh = getVerticalGridView()
                        .findViewHolderForAdapterPosition(0);
                if (vh == null) {
                    return;
                }
                View view = vh.itemView;
                if (view != null) {
                    final float fraction = (Float) arg0.getAnimatedValue();
                    if (DEBUG) Log.v(TAG, "fraction " + fraction);
                    view.setAlpha(fraction);
                    view.setTranslationY((float) mAnimationTranslateY * (1f - fraction));
                }
            }
        };

        Context context = FragmentUtil.getContext(PlaybackFragment.this);
        mControlRowFadeInAnimator = loadAnimator(context, R.animator.lb_playback_controls_fade_in);
        mControlRowFadeInAnimator.addUpdateListener(updateListener);
        mControlRowFadeInAnimator.setInterpolator(mLogDecelerateInterpolator);

        mControlRowFadeOutAnimator = loadAnimator(context,
                R.animator.lb_playback_controls_fade_out);
        mControlRowFadeOutAnimator.addUpdateListener(updateListener);
        mControlRowFadeOutAnimator.setInterpolator(mLogAccelerateInterpolator);
    }

    private void loadOtherRowAnimator() {
        final AnimatorUpdateListener updateListener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                if (getVerticalGridView() == null) {
                    return;
                }
                final float fraction = (Float) arg0.getAnimatedValue();
                final int count = getVerticalGridView().getChildCount();
                for (int i = 0; i < count; i++) {
                    View view = getVerticalGridView().getChildAt(i);
                    if (getVerticalGridView().getChildAdapterPosition(view) > 0) {
                        view.setAlpha(fraction);
                        view.setTranslationY((float) mAnimationTranslateY * (1f - fraction));
                    }
                }
            }
        };

        Context context = FragmentUtil.getContext(PlaybackFragment.this);
        mOtherRowFadeInAnimator = loadAnimator(context, R.animator.lb_playback_controls_fade_in);
        mOtherRowFadeInAnimator.addUpdateListener(updateListener);
        mOtherRowFadeInAnimator.setInterpolator(mLogDecelerateInterpolator);

        mOtherRowFadeOutAnimator = loadAnimator(context, R.animator.lb_playback_controls_fade_out);
        mOtherRowFadeOutAnimator.addUpdateListener(updateListener);
        mOtherRowFadeOutAnimator.setInterpolator(new AccelerateInterpolator());
    }

    /**
     * Fades out the playback overlay immediately.
     * @deprecated Call {@link #hideControlsOverlay(boolean)}
     */
    @Deprecated
    public void fadeOut() {
        showControlsOverlay(false, false);
    }

    /**
     * Show controls overlay.
     *
     * @param runAnimation True to run animation, false otherwise.
     */
    public void showControlsOverlay(boolean runAnimation) {
        showControlsOverlay(true, runAnimation);
    }

    /**
     * Returns true if controls overlay is visible, false otherwise.
     *
     * @return True if controls overlay is visible, false otherwise.
     * @see #showControlsOverlay(boolean)
     * @see #hideControlsOverlay(boolean)
     */
    public boolean isControlsOverlayVisible() {
        return mControlVisible;
    }

    /**
     * Hide controls overlay.
     *
     * @param runAnimation True to run animation, false otherwise.
     */
    public void hideControlsOverlay(boolean runAnimation) {
        showControlsOverlay(false, runAnimation);
    }

    /**
     * if first animator is still running, reverse it; otherwise start second animator.
     */
    static void reverseFirstOrStartSecond(ValueAnimator first, ValueAnimator second,
            boolean runAnimation) {
        if (first.isStarted()) {
            first.reverse();
            if (!runAnimation) {
                first.end();
            }
        } else {
            second.start();
            if (!runAnimation) {
                second.end();
            }
        }
    }

    /**
     * End first or second animator if they are still running.
     */
    static void endAll(ValueAnimator first, ValueAnimator second) {
        if (first.isStarted()) {
            first.end();
        } else if (second.isStarted()) {
            second.end();
        }
    }

    /**
     * Fade in or fade out rows and background.
     *
     * @param show True to fade in, false to fade out.
     * @param animation True to run animation.
     */
    void showControlsOverlay(boolean show, boolean animation) {
        if (DEBUG) Log.v(TAG, "showControlsOverlay " + show);
        if (getView() == null) {
            mControlVisibleBeforeOnCreateView = show;
            return;
        }
        // force no animation when fragment is not resumed
        if (!isResumed()) {
            animation = false;
        }
        if (show == mControlVisible) {
            if (!animation) {
                // End animation if needed
                endAll(mBgFadeInAnimator, mBgFadeOutAnimator);
                endAll(mControlRowFadeInAnimator, mControlRowFadeOutAnimator);
                endAll(mOtherRowFadeInAnimator, mOtherRowFadeOutAnimator);
            }
            return;
        }
        // StateGraph: 7<->5 4<->3 2->3
        mControlVisible = show;
        if (!mControlVisible) {
            // StateGraph 2->3
            stopFadeTimer();
        }

        mAnimationTranslateY = (getVerticalGridView() == null
                || getVerticalGridView().getSelectedPosition() == 0)
                ? mMajorFadeTranslateY : mMinorFadeTranslateY;

        if (show) {
            reverseFirstOrStartSecond(mBgFadeOutAnimator, mBgFadeInAnimator, animation);
            reverseFirstOrStartSecond(mControlRowFadeOutAnimator, mControlRowFadeInAnimator,
                    animation);
            reverseFirstOrStartSecond(mOtherRowFadeOutAnimator, mOtherRowFadeInAnimator, animation);
        } else {
            reverseFirstOrStartSecond(mBgFadeInAnimator, mBgFadeOutAnimator, animation);
            reverseFirstOrStartSecond(mControlRowFadeInAnimator, mControlRowFadeOutAnimator,
                    animation);
            reverseFirstOrStartSecond(mOtherRowFadeInAnimator, mOtherRowFadeOutAnimator, animation);
        }
        if (animation) {
            getView().announceForAccessibility(getString(show
                    ? R.string.lb_playback_controls_shown
                    : R.string.lb_playback_controls_hidden));
        }
    }

    /**
     * Sets the selected row position with smooth animation.
     */
    public void setSelectedPosition(int position) {
        setSelectedPosition(position, true);
    }

    /**
     * Sets the selected row position.
     */
    public void setSelectedPosition(int position, boolean smooth) {
        mSetSelectionRunnable.mPosition = position;
        mSetSelectionRunnable.mSmooth = smooth;
        if (getView() != null && getView().getHandler() != null) {
            getView().getHandler().post(mSetSelectionRunnable);
        }
    }

    private void setupChildFragmentLayout() {
        setVerticalGridViewLayout(mRowsFragment.getVerticalGridView());
    }

    void setVerticalGridViewLayout(VerticalGridView listview) {
        if (listview == null) {
            return;
        }

        // we set the base line of alignment to -paddingBottom
        listview.setWindowAlignmentOffset(-mPaddingBottom);
        listview.setWindowAlignmentOffsetPercent(
                VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);

        // align other rows that arent the last to center of screen, since our baseline is
        // -mPaddingBottom, we need subtract that from mOtherRowsCenterToBottom.
        listview.setItemAlignmentOffset(mOtherRowsCenterToBottom - mPaddingBottom);
        listview.setItemAlignmentOffsetPercent(50);

        // Push last row to the bottom padding
        // Padding affects alignment when last row is focused
        listview.setPadding(listview.getPaddingLeft(), listview.getPaddingTop(),
                listview.getPaddingRight(), mPaddingBottom);
        listview.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_HIGH_EDGE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOtherRowsCenterToBottom = getResources()
                .getDimensionPixelSize(R.dimen.lb_playback_other_rows_center_to_bottom);
        mPaddingBottom =
                getResources().getDimensionPixelSize(R.dimen.lb_playback_controls_padding_bottom);
        mBgDarkColor =
                getResources().getColor(R.color.lb_playback_controls_background_dark);
        mBgLightColor =
                getResources().getColor(R.color.lb_playback_controls_background_light);
        TypedValue outValue = new TypedValue();
        FragmentUtil.getContext(PlaybackFragment.this).getTheme().resolveAttribute(
                R.attr.playbackControlsAutoHideTimeout, outValue, true);
        mAutohideTimerAfterPlayingInMs = outValue.data;
        FragmentUtil.getContext(PlaybackFragment.this).getTheme().resolveAttribute(
                R.attr.playbackControlsAutoHideTickleTimeout, outValue, true);
        mAutohideTimerAfterTickleInMs = outValue.data;
        mMajorFadeTranslateY =
                getResources().getDimensionPixelSize(R.dimen.lb_playback_major_fade_translate_y);
        mMinorFadeTranslateY =
                getResources().getDimensionPixelSize(R.dimen.lb_playback_minor_fade_translate_y);

        loadBgAnimator();
        loadControlRowAnimator();
        loadOtherRowAnimator();
    }

    /**
     * Sets the background type.
     *
     * @param type One of BG_LIGHT, BG_DARK, or BG_NONE.
     */
    public void setBackgroundType(int type) {
        switch (type) {
            case BG_LIGHT:
            case BG_DARK:
            case BG_NONE:
                if (type != mBackgroundType) {
                    mBackgroundType = type;
                    updateBackground();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid background type");
        }
    }

    /**
     * Returns the background type.
     */
    public int getBackgroundType() {
        return mBackgroundType;
    }

    private void updateBackground() {
        if (mBackgroundView != null) {
            int color = mBgDarkColor;
            switch (mBackgroundType) {
                case BG_DARK:
                    break;
                case BG_LIGHT:
                    color = mBgLightColor;
                    break;
                case BG_NONE:
                    color = Color.TRANSPARENT;
                    break;
            }
            mBackgroundView.setBackground(new ColorDrawable(color));
            setBgAlpha(mBgAlpha);
        }
    }

    private final ItemBridgeAdapter.AdapterListener mAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
                @Override
                public void onAttachedToWindow(ItemBridgeAdapter.ViewHolder vh) {
                    if (DEBUG) Log.v(TAG, "onAttachedToWindow " + vh.getViewHolder().view);
                    if (!mControlVisible) {
                        if (DEBUG) Log.v(TAG, "setting alpha to 0");
                        vh.getViewHolder().view.setAlpha(0);
                    }
                }

                @Override
                public void onCreate(ItemBridgeAdapter.ViewHolder vh) {
                    Presenter.ViewHolder viewHolder = vh.getViewHolder();
                    if (viewHolder instanceof PlaybackSeekUi) {
                        ((PlaybackSeekUi) viewHolder).setPlaybackSeekUiClient(mChainedClient);
                    }
                }

                @Override
                public void onDetachedFromWindow(ItemBridgeAdapter.ViewHolder vh) {
                    if (DEBUG) Log.v(TAG, "onDetachedFromWindow " + vh.getViewHolder().view);
                    // Reset animation state
                    vh.getViewHolder().view.setAlpha(1f);
                    vh.getViewHolder().view.setTranslationY(0);
                    vh.getViewHolder().view.setAlpha(1f);
                }

                @Override
                public void onBind(ItemBridgeAdapter.ViewHolder vh) {
                }
            };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.lb_playback_fragment, container, false);
        mBackgroundView = mRootView.findViewById(R.id.playback_fragment_background);
        mRowsFragment = (RowsFragment) getChildFragmentManager().findFragmentById(
                R.id.playback_controls_dock);
        if (mRowsFragment == null) {
            mRowsFragment = new RowsFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.playback_controls_dock, mRowsFragment)
                    .commit();
        }
        if (mAdapter == null) {
            setAdapter(new ArrayObjectAdapter(new ClassPresenterSelector()));
        } else {
            mRowsFragment.setAdapter(mAdapter);
        }
        mRowsFragment.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
        mRowsFragment.setOnItemViewClickedListener(mOnItemViewClickedListener);

        mBgAlpha = 255;
        updateBackground();
        mRowsFragment.setExternalAdapterListener(mAdapterListener);
        ProgressBarManager progressBarManager = getProgressBarManager();
        if (progressBarManager != null) {
            progressBarManager.setRootView((ViewGroup) mRootView);
        }
        return mRootView;
    }

    /**
     * Sets the {@link PlaybackGlueHost.HostCallback}. Implementor of this interface will
     * take appropriate actions to take action when the hosting fragment starts/stops processing.
     */
    public void setHostCallback(PlaybackGlueHost.HostCallback hostCallback) {
        this.mHostCallback = hostCallback;
    }

    @Override
    public void onStart() {
        super.onStart();
        setupChildFragmentLayout();
        mRowsFragment.setAdapter(mAdapter);
        if (mHostCallback != null) {
            mHostCallback.onHostStart();
        }
    }

    @Override
    public void onStop() {
        if (mHostCallback != null) {
            mHostCallback.onHostStop();
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        if (mHostCallback != null) {
            mHostCallback.onHostPause();
        }
        if (mHandler.hasMessages(START_FADE_OUT)) {
            // StateGraph: 2->1
            mHandler.removeMessages(START_FADE_OUT);
        } else {
            // StateGraph: 5->6, 7->6, 4->1, 3->1
        }
        super.onPause();
    }

    /**
     * This listener is called every time there is a selection in {@link RowsFragment}. This can
     * be used by users to take additional actions such as animations.
     */
    public void setOnItemViewSelectedListener(final BaseOnItemViewSelectedListener listener) {
        mExternalItemSelectedListener = listener;
    }

    /**
     * This listener is called every time there is a click in {@link RowsFragment}. This can
     * be used by users to take additional actions such as animations.
     */
    public void setOnItemViewClickedListener(final BaseOnItemViewClickedListener listener) {
        mExternalItemClickedListener = listener;
    }

    /**
     * Sets the {@link BaseOnItemViewClickedListener} that would be invoked for clicks
     * only on {@link androidx.leanback.widget.PlaybackRowPresenter.ViewHolder}.
     */
    public void setOnPlaybackItemViewClickedListener(final BaseOnItemViewClickedListener listener) {
        mPlaybackItemClickedListener = listener;
    }

    @Override
    public void onDestroyView() {
        mRootView = null;
        mBackgroundView = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (mHostCallback != null) {
            mHostCallback.onHostDestroy();
        }
        super.onDestroy();
    }

    /**
     * Sets the playback row for the playback controls. The row will be set as first element
     * of adapter if the adapter is {@link ArrayObjectAdapter} or {@link SparseArrayObjectAdapter}.
     * @param row The row that represents the playback.
     */
    public void setPlaybackRow(Row row) {
        this.mRow = row;
        setupRow();
        setupPresenter();
    }

    /**
     * Sets the presenter for rendering the playback row set by {@link #setPlaybackRow(Row)}. If
     * adapter does not set a {@link PresenterSelector}, {@link #setAdapter(ObjectAdapter)} will
     * create a {@link ClassPresenterSelector} by default and map from the row object class to this
     * {@link PlaybackRowPresenter}.
     *
     * @param  presenter Presenter used to render {@link #setPlaybackRow(Row)}.
     */
    public void setPlaybackRowPresenter(PlaybackRowPresenter presenter) {
        this.mPresenter = presenter;
        setupPresenter();
        setPlaybackRowPresenterAlignment();
    }

    void setPlaybackRowPresenterAlignment() {
        if (mAdapter != null && mAdapter.getPresenterSelector() != null) {
            Presenter[] presenters = mAdapter.getPresenterSelector().getPresenters();
            if (presenters != null) {
                for (int i = 0; i < presenters.length; i++) {
                    if (presenters[i] instanceof PlaybackRowPresenter
                            && presenters[i].getFacet(ItemAlignmentFacet.class) == null) {
                        ItemAlignmentFacet itemAlignment = new ItemAlignmentFacet();
                        ItemAlignmentFacet.ItemAlignmentDef def =
                                new ItemAlignmentFacet.ItemAlignmentDef();
                        def.setItemAlignmentOffset(0);
                        def.setItemAlignmentOffsetPercent(100);
                        itemAlignment.setAlignmentDefs(new ItemAlignmentFacet.ItemAlignmentDef[]
                                {def});
                        presenters[i].setFacet(ItemAlignmentFacet.class, itemAlignment);
                    }
                }
            }
        }
    }

    /**
     * Updates the ui when the row data changes.
     */
    public void notifyPlaybackRowChanged() {
        if (mAdapter == null) {
            return;
        }
        mAdapter.notifyItemRangeChanged(0, 1);
    }

    /**
     * Sets the list of rows for the fragment. A default {@link ClassPresenterSelector} will be
     * created if {@link ObjectAdapter#getPresenterSelector()} is null. if user provides
     * {@link #setPlaybackRow(Row)} and {@link #setPlaybackRowPresenter(PlaybackRowPresenter)},
     * the row and presenter will be set onto the adapter.
     *
     * @param adapter The adapter that contains related rows and optional playback row.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        setupRow();
        setupPresenter();
        setPlaybackRowPresenterAlignment();

        if (mRowsFragment != null) {
            mRowsFragment.setAdapter(adapter);
        }
    }

    private void setupRow() {
        if (mAdapter instanceof ArrayObjectAdapter && mRow != null) {
            ArrayObjectAdapter adapter = ((ArrayObjectAdapter) mAdapter);
            if (adapter.size() == 0) {
                adapter.add(mRow);
            } else {
                adapter.replace(0, mRow);
            }
        } else if (mAdapter instanceof SparseArrayObjectAdapter && mRow != null) {
            SparseArrayObjectAdapter adapter = ((SparseArrayObjectAdapter) mAdapter);
            adapter.set(0, mRow);
        }
    }

    private void setupPresenter() {
        if (mAdapter != null && mRow != null && mPresenter != null) {
            PresenterSelector selector = mAdapter.getPresenterSelector();
            if (selector == null) {
                selector = new ClassPresenterSelector();
                ((ClassPresenterSelector) selector).addClassPresenter(mRow.getClass(), mPresenter);
                mAdapter.setPresenterSelector(selector);
            } else if (selector instanceof ClassPresenterSelector) {
                ((ClassPresenterSelector) selector).addClassPresenter(mRow.getClass(), mPresenter);
            }
        }
    }

    final PlaybackSeekUi.Client mChainedClient = new PlaybackSeekUi.Client() {
        @Override
        public boolean isSeekEnabled() {
            return mSeekUiClient == null ? false : mSeekUiClient.isSeekEnabled();
        }

        @Override
        public void onSeekStarted() {
            if (mSeekUiClient != null) {
                mSeekUiClient.onSeekStarted();
            }
            setSeekMode(true);
        }

        @Override
        public PlaybackSeekDataProvider getPlaybackSeekDataProvider() {
            return mSeekUiClient == null ? null : mSeekUiClient.getPlaybackSeekDataProvider();
        }

        @Override
        public void onSeekPositionChanged(long pos) {
            if (mSeekUiClient != null) {
                mSeekUiClient.onSeekPositionChanged(pos);
            }
        }

        @Override
        public void onSeekFinished(boolean cancelled) {
            if (mSeekUiClient != null) {
                mSeekUiClient.onSeekFinished(cancelled);
            }
            setSeekMode(false);
        }
    };

    /**
     * Interface to be implemented by UI widget to support PlaybackSeekUi.
     */
    public void setPlaybackSeekUiClient(PlaybackSeekUi.Client client) {
        mSeekUiClient = client;
    }

    /**
     * Show or hide other rows other than PlaybackRow.
     * @param inSeek True to make other rows visible, false to make other rows invisible.
     */
    void setSeekMode(boolean inSeek) {
        if (mInSeek == inSeek) {
            return;
        }
        mInSeek = inSeek;
        getVerticalGridView().setSelectedPosition(0);
        if (mInSeek) {
            stopFadeTimer();
        }
        // immediately fade in control row.
        showControlsOverlay(true);
        final int count = getVerticalGridView().getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getVerticalGridView().getChildAt(i);
            if (getVerticalGridView().getChildAdapterPosition(view) > 0) {
                view.setVisibility(mInSeek ? View.INVISIBLE : View.VISIBLE);
            }
        }
    }

    /**
     * Called when size of the video changes. App may override.
     * @param videoWidth Intrinsic width of video
     * @param videoHeight Intrinsic height of video
     */
    protected void onVideoSizeChanged(int videoWidth, int videoHeight) {
    }

    /**
     * Called when media has start or stop buffering. App may override. The default initial state
     * is not buffering.
     * @param start True for buffering start, false otherwise.
     */
    protected void onBufferingStateChanged(boolean start) {
        ProgressBarManager progressBarManager = getProgressBarManager();
        if (progressBarManager != null) {
            if (start) {
                progressBarManager.show();
            } else {
                progressBarManager.hide();
            }
        }
    }

    /**
     * Called when media has error. App may override.
     * @param errorCode Optional error code for specific implementation.
     * @param errorMessage Optional error message for specific implementation.
     */
    protected void onError(int errorCode, CharSequence errorMessage) {
    }

    /**
     * Returns the ProgressBarManager that will show or hide progress bar in
     * {@link #onBufferingStateChanged(boolean)}.
     * @return The ProgressBarManager that will show or hide progress bar in
     * {@link #onBufferingStateChanged(boolean)}.
     */
    public ProgressBarManager getProgressBarManager() {
        return mProgressBarManager;
    }
}
