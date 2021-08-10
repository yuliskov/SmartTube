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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.lang.ref.WeakReference;

public class ScreensaverManager {
    private static final String TAG = ScreensaverManager.class.getSimpleName();
    private final Handler mHandler;
    private final WeakReference<Activity> mActivity;
    private final Runnable mDimScreen = this::dimScreen;
    private final Runnable mUndimScreen = this::undimScreen;
    private final GeneralData mGeneralData;
    private int mDimColorResId;

    public ScreensaverManager(Activity activity) {
        mActivity = new WeakReference<>(activity);
        mHandler = new Handler(Looper.getMainLooper());
        mGeneralData = GeneralData.instance(activity);
        enable();
    }

    public void enable() {
        Log.d(TAG, "Enable screensaver");

        disable();
        int delayMs = mGeneralData.getScreenDimmingTimoutMin() == GeneralData.SCREEN_DIMMING_NEVER ?
                10_000 :
                mGeneralData.getScreenDimmingTimoutMin() * 60 * 1_000;
        Utils.postDelayed(mHandler, mDimScreen, delayMs);
    }

    public void disable() {
        Log.d(TAG, "Disable screensaver");

        mDimColorResId = R.color.dimming;
        Utils.removeCallbacks(mHandler, mDimScreen);
        Utils.postDelayed(mHandler, mUndimScreen, 0);
    }

    public void doScreenOff() {
        disable();
        mDimColorResId = R.color.black;
        Utils.postDelayed(mHandler, mDimScreen, 0);
    }

    private void dimScreen() {
        showHide(true);
    }

    private void undimScreen() {
        showHide(false);
    }

    private void showHide(boolean show) {
        showHideDimming(show);
        showHideScreensaver(true);
    }

    private void showHideDimming(boolean show) {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        if (show && mDimColorResId == R.color.dimming &&
                (isPlaying() || mGeneralData.getScreenDimmingTimoutMin() == GeneralData.SCREEN_DIMMING_NEVER)
        ) {
            return;
        }

        View rootView = activity.getWindow().getDecorView().getRootView();

        View dimContainer = rootView.findViewById(R.id.dim_container);

        if (dimContainer == null) {
            LayoutInflater layoutInflater = activity.getLayoutInflater();
            dimContainer = layoutInflater.inflate(R.layout.dim_container, null);
            if (rootView instanceof ViewGroup) {
                ((ViewGroup) rootView).addView(dimContainer);
            }
        }

        dimContainer.setBackgroundResource(mDimColorResId);
        dimContainer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showHideScreensaver(boolean show) {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }
        
        if (show && isPlaying()) {
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
}
