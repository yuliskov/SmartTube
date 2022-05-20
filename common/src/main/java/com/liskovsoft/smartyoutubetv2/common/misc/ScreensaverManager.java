package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.lang.ref.WeakReference;

public class ScreensaverManager {
    private static final String TAG = ScreensaverManager.class.getSimpleName();
    private static final int SCREEN_OFF_RES_ID = R.color.black;
    private static final int DIMMING_RES_ID = R.color.dimming;
    private final Handler mHandler;
    private final WeakReference<Activity> mActivity;
    private final Runnable mDimScreen = this::dimScreen;
    private final Runnable mUndimScreen = this::undimScreen;
    private final GeneralData mGeneralData;
    private int mDimColorResId;
    private boolean mIsScreenOff;

    public ScreensaverManager(Activity activity) {
        mActivity = new WeakReference<>(activity);
        mHandler = new Handler(Looper.getMainLooper());
        mGeneralData = GeneralData.instance(activity);
        enable();
    }

    /**
     * Screen off check
     */
    public void enableChecked() {
        if (mDimColorResId == SCREEN_OFF_RES_ID) {
            return;
        }

        enable();
    }

    /**
     * Screen off check
     */
    public void disableChecked() {
        if (mDimColorResId == SCREEN_OFF_RES_ID) {
            return;
        }

        disable();
    }

    public void enable() {
        Log.d(TAG, "Enable screensaver");

//        disable();
//        int delayMs = mGeneralData.getScreenDimmingTimoutMin() == GeneralData.SCREEN_DIMMING_NEVER ?
//                10_000 :
//                mGeneralData.getScreenDimmingTimoutMin() * 60 * 1_000;
        Utils.postDelayed(mHandler, mDimScreen, 0);
    }

    public void disable() {
        Log.d(TAG, "Disable screensaver");
        mDimColorResId = DIMMING_RES_ID;
        Utils.removeCallbacks(mHandler, mDimScreen);
        Utils.postDelayed(mHandler, mUndimScreen, 0);
    }

    public void doScreenOff() {
        disable();
        mDimColorResId = SCREEN_OFF_RES_ID;
        Utils.postDelayed(mHandler, mDimScreen, 0);
    }

    public boolean isScreenOff() {
        return mIsScreenOff;
    }

    private void dimScreen() {
        showHide(true);
    }

    private void undimScreen() {
        showHide(false);
    }

    private void showHide(boolean show) {
//        showHideDimming(show);
        showHideScreensaver(show);
    }

    private void showHideDimming(boolean show) {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        // Disable dimming on certain circumstances
        if (show && mDimColorResId == DIMMING_RES_ID &&
                (isPlaying() || isSigning() || mGeneralData.getScreenDimmingTimeoutMin() == GeneralData.SCREEN_DIMMING_NEVER)
        ) {
            return;
        }

        View rootView = activity.getWindow().getDecorView().getRootView();

        View dimContainer = rootView.findViewById(R.id.dim_container);

        if (dimContainer == null) {
            LayoutInflater layoutInflater = activity.getLayoutInflater();
            dimContainer = layoutInflater.inflate(R.layout.dim_container, null);
            if (rootView instanceof ViewGroup) {
                // Add negative margin to fix un-proper viewport positioning on some devices
                // NOTE: below code is not working!!!
                // NOTE: comment out code below if you don't want this
                //LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                //        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                //params.setMargins(-200, -200, -200, -200);
                //((ViewGroup) rootView).addView(dimContainer, params);

                ((ViewGroup) rootView).addView(dimContainer);
            }
        }

        dimContainer.setBackgroundResource(mDimColorResId);
        dimContainer.setVisibility(show ? View.VISIBLE : View.GONE);

        mIsScreenOff = mDimColorResId == SCREEN_OFF_RES_ID && show;

        if (mIsScreenOff) {
            hidePlayerOverlay();
        }
    }

    private void showHideScreensaver(boolean show) {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        // Disable screensaver on certain circumstances
        if (show && (isPlaying() || isSigning() || mGeneralData.isScreensaverDisabled())) {
            Helpers.disableScreensaver(activity);
            return;
        }

        if (show) {
            Helpers.enableScreensaver(activity);
        } else {
            Helpers.disableScreensaver(activity);
        }
    }

    private boolean isPlaying() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return false;
        }

        PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();
        return playbackView != null && (playbackView.getController().isPlaying() || playbackView.getController().isLoading());
    }

    private boolean isSigning() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return false;
        }

        return SignInPresenter.instance(activity).getView() != null || AddDevicePresenter.instance(activity).getView() != null;
    }

    private void hidePlayerOverlay() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();

        if (playbackView != null) {
            playbackView.getController().showOverlay(false);
        }
    }
}
