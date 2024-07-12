package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.utils.WeakHashSet;

import java.lang.ref.WeakReference;

public class ScreensaverManager {
    private static final String TAG = ScreensaverManager.class.getSimpleName();
    private static final int MODE_SCREENSAVER = 0;
    private static final int MODE_SCREEN_OFF = 1;
    private static final WeakHashSet<ScreensaverManager> sInstances = new WeakHashSet<>();
    private static boolean sLockInstance;
    private WeakReference<Activity> mActivity;
    private final WeakReference<View> mDimContainer;
    private final Runnable mDimScreen = this::dimScreen;
    private final Runnable mUndimScreen = this::undimScreen;
    private final Runnable mUnlockInstance = () -> sLockInstance = false;
    private final GeneralData mGeneralData;
    private PlayerTweaksData mTweaksData;
    private int mMode = MODE_SCREENSAVER;
    private boolean mIsScreenOff;
    private boolean mIsBlocked;
    private final Runnable mTimeoutHandler = () -> {
        // Playing the video and dialog overlay isn't shown
        if (ViewManager.instance(mActivity.get()).getTopView() != PlaybackView.class || !mTweaksData.isScreenOffTimeoutEnabled()) {
            return;
        }

        if (!AppDialogPresenter.instance(mActivity.get()).isDialogShown()) {
            doScreenOff();
        } else {
            // showing dialog... or recheck...
            enableTimeout();
        }
    };

    public ScreensaverManager(Activity activity) {
        mActivity = new WeakReference<>(activity);
        mDimContainer = new WeakReference<>(createDimContainer(activity));
        mGeneralData = GeneralData.instance(activity);
        mTweaksData = PlayerTweaksData.instance(activity);
        enable();
        addToRegistry();
    }

    private View createDimContainer(Activity activity) {
        View rootView = activity.getWindow().getDecorView().getRootView();

        View dimContainer = rootView.findViewById(R.id.dim_container);

        if (dimContainer == null) {
            LayoutInflater layoutInflater = activity.getLayoutInflater();
            dimContainer = layoutInflater.inflate(R.layout.dim_container, null);
            if (rootView instanceof ViewGroup) {
                // NOTE: zoom will be bugged! Frames on top and bottom.
                // Add negative margin to fix un-proper viewport positioning on some devices
                // NOTE: below code is not working!!!
                // NOTE: comment out code below if you don't want this
                //LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                //        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                //params.setMargins(-30, -30, -30, -30);
                //((ViewGroup) rootView).addView(dimContainer, params);

                ((ViewGroup) rootView).addView(dimContainer);
            }
        }

        return dimContainer;
    }

    /**
     * Screen off check
     */
    public void enableChecked() {
        if (mMode == MODE_SCREEN_OFF) {
            return;
        }

        enable();
    }

    /**
     * Screen off check
     */
    public void disableChecked() {
        if (mMode == MODE_SCREEN_OFF) {
            return;
        }

        disable();
    }

    public void enable() {
        if (mIsBlocked) {
            Log.d(TAG, "Screensaver blocked!");
            return;
        }

        Log.d(TAG, "Enable screensaver");

        disable();
        int delayMs = mGeneralData.getScreensaverTimeoutMs() == GeneralData.SCREENSAVER_TIMEOUT_NEVER ?
                10_000 :
                mGeneralData.getScreensaverTimeoutMs();
        Utils.postDelayed(mDimScreen, delayMs);
    }

    public void disable() {
        if (mIsBlocked) {
            Log.d(TAG, "Screensaver blocked!");
            return;
        }

        Log.d(TAG, "Disable screensaver");
        mMode = MODE_SCREENSAVER;
        Utils.removeCallbacks(mDimScreen);
        Utils.postDelayed(mUndimScreen, 0);
    }

    public void doScreenOff() {
        //if (mIsScreenOff) {
        //    return;
        //}

        disable();
        mMode = MODE_SCREEN_OFF;
        Utils.postDelayed(mDimScreen, 0);
    }

    public boolean isScreenOff() {
        return mIsScreenOff;
    }

    public void setBlocked(boolean blocked) {
        mIsBlocked = blocked;
    }

    private void enableTimeout() {
        // Playing the video and dialog overlay isn't shown
        if (ViewManager.instance(mActivity.get()).getTopView() != PlaybackView.class || !mTweaksData.isScreenOffTimeoutEnabled()) {
            disableTimeout();
            return;
        }

        Log.d(TAG, "Starting auto hide ui timer...");
        disableTimeout();
        Utils.postDelayed(mTimeoutHandler, mTweaksData.getScreenOffTimeoutSec() * 1_000L);
    }

    private void disableTimeout() {
        Log.d(TAG, "Stopping auto hide ui timer...");
        Utils.removeCallbacks(mTimeoutHandler);
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
        View dimContainer = mDimContainer.get();

        if (activity == null || dimContainer == null) {
            return;
        }

        if (!show) {
            enableTimeout();
        }

        // Disable dimming on certain circumstances
        if (show && mMode == MODE_SCREENSAVER &&
                (       isPlaying() ||
                        isSigning() ||
                        mGeneralData.getScreensaverTimeoutMs() == GeneralData.SCREENSAVER_TIMEOUT_NEVER
                )
        ) {
            return;
        }

        int screenOffColor = Utils.getColor(activity, R.color.black, mTweaksData.getScreenOffDimmingPercents());
        //int screenOffColorResId = mTweaksData.getScreenOffDimmingPercents() == 50 ? DIM_50 : DIM_100;
        int screensaverColor = Utils.getColor(activity, R.color.black, mGeneralData.getScreensaverDimmingPercents());
        //int screensaverColorResId = mGeneralData.getScreensaverMode() == GeneralData.SCREENSAVER_MODE_NORMAL ? DIM_50 : DIM_100;

        dimContainer.setBackgroundColor(mMode == MODE_SCREENSAVER ? screensaverColor : screenOffColor);
        //dimContainer.setBackgroundResource(mMode == MODE_SCREENSAVER ? screensaverColorResId : screenOffColorResId);
        dimContainer.setVisibility(show ? View.VISIBLE : View.GONE);

        mIsScreenOff = mMode == MODE_SCREEN_OFF && mTweaksData.getScreenOffDimmingPercents() == 100 && show;

        if (mIsScreenOff) {
            hidePlayerOverlay();
        }

        notifyRegistry();
    }

    private void showHideScreensaver(boolean show) {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        // Disable screensaver on certain circumstances
        // Fix screen off before the video started
        if (show && (isPlaying() || isSigning() || mGeneralData.isScreensaverDisabled() || (mMode == MODE_SCREEN_OFF && getPosition() == 0))) {
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
        return playbackView != null && playbackView.getPlayer().isPlaying();
    }

    private long getPosition() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return 0;
        }

        PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();
        // Fix screen off before the video started
        return playbackView != null ? playbackView.getPlayer().getPositionMs() : 0;
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
            playbackView.getPlayer().showOverlay(false);
        }
    }

    private void addToRegistry() {
        sInstances.add(this);
    }

    private void notifyRegistry() {
        if (sLockInstance) {
            return;
        }

        sLockInstance = true;

        sInstances.forEach(item -> {
            if (item != this) {
                item.disableChecked();
            }
        });

        Utils.postDelayed(mUnlockInstance, 0);
    }
}
