package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc;

import android.content.Context;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseFragment;
import androidx.leanback.app.VerticalGridFragment;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;

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
    private boolean mSkeleton;

    private final Runnable showRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mEnableProgressBar || (!mUserProvidedProgressBar && rootView == null)) {
                return;
            }

            if (mIsShowing) {
                if (mProgressBarView == null) {
                    mProgressBarView = mSkeleton ? createSkeleton(rootView) : createProgressBar(rootView, mPosition);
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
                mProgressBarView.clearAnimation(); // a removed view keeps drawing while its (infinite) animation runs
                rootView.removeView(mProgressBarView);
                mProgressBarView = null;
            }
        }
    };

    private static View createProgressBar(ViewGroup rootView, int position) {
        ProgressBar progressBarView = new ProgressBar(
                rootView.getContext(), null, android.R.attr.progressBarStyleLarge);
        progressBarView.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            // MOD: YouTube style loader: the platform material arc (spins while growing/shrinking), tinted white
            progressBarView.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        } else {
            progressBarView.setIndeterminateDrawable(
                    ContextCompat.getDrawable(rootView.getContext(), R.drawable.progress_large_holo));
        }

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
            // skeleton replaces the empty page, so show it right away
            Utils.postDelayed(showRunnable, !mSkeleton && System.currentTimeMillis() - mHideTimeMs > HIDE_DELAY_MS ? SHOW_DELAY_MS : 0);
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

    /**
     * YouTube TV style: placeholder rows of grey cards instead of a spinner
     */
    public void setSkeleton(boolean skeleton) {
        mSkeleton = skeleton;
    }

    private static final int SKELETON_COLOR = 0xFF272727;

    // Mirrors the browse rows layout: rail 78dp, header at 106dp, thumbs at 146dp, 16dp gaps
    private static View createSkeleton(ViewGroup rootView) {
        Context context = rootView.getContext();
        float dp = context.getResources().getDisplayMetrics().density;
        Pair<Integer, Integer> card = GridFragmentHelper.getCardDimensPx(context, R.dimen.card_width, R.dimen.card_height,
                MainUIData.instance(context).getVideoGridScale());
        int width = card.first;
        int height = card.second;

        LinearLayout skeleton = new LinearLayout(context);
        skeleton.setOrientation(LinearLayout.VERTICAL);
        skeleton.setPadding(Math.round(78 * dp), Math.round(106 * dp), 0, 0);

        for (int row = 0; row < 2; row++) {
            LinearLayout.LayoutParams headerParams = bar(context, skeleton, Math.round(180 * dp), Math.round(22 * dp), 0);
            headerParams.bottomMargin = Math.round(18 * dp);

            LinearLayout cards = new LinearLayout(context);
            cards.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams cardsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardsParams.bottomMargin = Math.round(40 * dp);
            skeleton.addView(cards, cardsParams);

            for (int i = 0; i < 4; i++) {
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
                columnParams.rightMargin = Math.round(16 * dp);
                cards.addView(column, columnParams);

                bar(context, column, width, height, 0);
                bar(context, column, Math.round(width * 0.9f), Math.round(18 * dp), Math.round(12 * dp));
                bar(context, column, Math.round(width * 0.6f), Math.round(18 * dp), Math.round(8 * dp));
                bar(context, column, Math.round(width * 0.4f), Math.round(14 * dp), Math.round(16 * dp));
            }
        }

        AlphaAnimation pulse = new AlphaAnimation(1f, 0.4f);
        pulse.setDuration(800);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        skeleton.startAnimation(pulse);

        // index 0: stay below the sidebar and title bar
        rootView.addView(skeleton, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        return skeleton;
    }

    private static LinearLayout.LayoutParams bar(Context context, ViewGroup parent, int width, int height, int topMargin) {
        View bar = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SKELETON_COLOR);
        bg.setCornerRadius(8 * context.getResources().getDisplayMetrics().density);
        bar.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = topMargin;
        parent.addView(bar, params);
        return params;
    }

    public static void setup(androidx.leanback.app.ProgressBarManager manager, ViewGroup rootView) {
        if (rootView != null) {
            manager.setProgressBarView(createProgressBar(rootView, Gravity.CENTER));
        }
    }
}
