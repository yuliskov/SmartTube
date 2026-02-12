package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseFragment;
import androidx.leanback.app.VerticalGridFragment;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Manager for showing/hiding progress bar widget. This class lets user specify an initial
 * delay after which the progress bar will be shown. This is currently being used in
 * {@link BrowseFragment} & {@link VerticalGridFragment} to show {@link ProgressBar}
 * while the data is being loaded.
 */
public final class ProgressBarManager {
    // Default delay for progress bar widget.
    private static final long SHOW_DELAY_MS = 500;
    private static final long HIDE_DELAY_MS = 50;
    private long mHideTimeMs;
    
    ViewGroup rootView;
    View mProgressBarView;
    boolean mEnableProgressBar = true;
    boolean mUserProvidedProgressBar;
    boolean mIsShowing;
    private int mPosition = Gravity.CENTER;

    private final Runnable showRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mEnableProgressBar || (!mUserProvidedProgressBar && rootView == null)) {
                return;
            }

            if (mIsShowing) {
                if (mProgressBarView == null) {
                    mProgressBarView = createProgressBar(rootView, mPosition);
                } else if (mUserProvidedProgressBar) {
                    mProgressBarView.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            if (mUserProvidedProgressBar) {
                mProgressBarView.setVisibility(View.INVISIBLE);
            } else if (mProgressBarView != null) {
                rootView.removeView(mProgressBarView);
                mProgressBarView = null;
            }
        }
    };

    private static View createProgressBar(ViewGroup rootView, int position) {
        ProgressBar progressBarView = new ProgressBar(
                rootView.getContext(), null, android.R.attr.progressBarStyleLarge);
        progressBarView.setIndeterminate(true);
        progressBarView.setIndeterminateDrawable(
                ContextCompat.getDrawable(rootView.getContext(), R.drawable.progress_large_holo));

        FrameLayout.LayoutParams progressBarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        progressBarParams.gravity = position;
        rootView.addView(progressBarView, progressBarParams);

        return progressBarView;
    }

    /**
     * Sets the root view on which the progress bar will be attached. This class assumes the
     * root view to be {@link FrameLayout} in order to position the progress bar widget
     * in the center of the screen.
     *
     * @param rootView view that will contain the progress bar.
     */
    public void setRootView(ViewGroup rootView) {
        this.rootView = rootView;
    }

    /**
     * Displays the progress bar.
     */
    public void show() {
        if (mEnableProgressBar) {
            mIsShowing = true;
            Utils.removeCallbacks(hideRunnable);
            Utils.postDelayed(showRunnable, System.currentTimeMillis() - mHideTimeMs > HIDE_DELAY_MS ? SHOW_DELAY_MS : 0);
        }
    }

    /**
     * Hides the progress bar.
     */
    public void hide() {
        mHideTimeMs = System.currentTimeMillis();
        mIsShowing = false;
        Utils.removeCallbacks(showRunnable);
        Utils.postDelayed(hideRunnable, HIDE_DELAY_MS);
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    /**
     * Sets a custom view to be shown in place of the default {@link ProgressBar}. This
     * view must have a parent. Once set, we maintain the visibility property of this view.
     *
     * @param progressBarView custom view that will be shown to indicate progress.
     */
    public void setProgressBarView(View progressBarView) {
        if (progressBarView.getParent() == null) {
            throw new IllegalArgumentException("Must have a parent");
        }

        this.mProgressBarView = progressBarView;
        this.mProgressBarView.setVisibility(View.INVISIBLE);
        mUserProvidedProgressBar = true;
    }

    /**
     * Disables progress bar.
     */
    public void disableProgressBar() {
        mEnableProgressBar = false;
    }

    /**
     * Enables progress bar.
     */
    public void enableProgressBar() {
        mEnableProgressBar = true;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public static void setup(androidx.leanback.app.ProgressBarManager manager, ViewGroup rootView) {
        if (rootView != null) {
            manager.setProgressBarView(createProgressBar(rootView, Gravity.CENTER));
        }
    }
}
