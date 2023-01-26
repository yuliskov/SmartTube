package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.fragment.app.Fragment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngine;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Loads PlaybackFragment and delegates input from a game controller.
 * <br>
 * For more information on game controller capabilities with leanback, review the
 * <a href="https://developer.android.com/training/game-controllers/controller-input.html">docs</href>.
 */
public class PlaybackActivity extends LeanbackActivity {
    private static final String TAG = PlaybackActivity.class.getSimpleName();
    private static final float GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f;
    // Off-condition slightly smaller for button debouncing.
    private static final float GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f;
    private boolean gamepadTriggerPressed = false;
    private PlaybackFragment mPlaybackFragment;
    private ViewManager mViewManager;
    private PlayerTweaksData mPlayerTweaksData;
    private GeneralData mGeneralData;
    private boolean mSkipPip;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_playback);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PlaybackFragment) {
            mPlaybackFragment = (PlaybackFragment) fragment;
        }
        mViewManager = ViewManager.instance(this);
        mPlayerTweaksData = PlayerTweaksData.instance(this);
        mGeneralData = GeneralData.instance(this);
    }

    @Override
    protected void initTheme() {
        int playerThemeResId = MainUIData.instance(this).getColorScheme().playerThemeResId;
        if (playerThemeResId > 0) {
            setTheme(playerThemeResId);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchKeyEvent(event);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchGenericMotionEvent(event);
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            mPlaybackFragment.skipToNext();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            mPlaybackFragment.skipToPrevious();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            mPlaybackFragment.rewind();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            mPlaybackFragment.fastForward();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // This method will handle gamepad events.
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.rewind();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.fastForward();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF
                && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF) {
            gamepadTriggerPressed = false;
        }
        return super.onGenericMotionEvent(event);
    }

    // For N devices that support it, not "officially"
    // More: https://medium.com/s23nyc-tech/drop-in-android-video-exoplayer2-with-picture-in-picture-e2d4f8c1eb30
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        if (Helpers.isPictureInPictureSupported(this)) {
            if (wannaEnterToPip()) {
                Log.d(TAG, "Entering PIP mode...");

                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                        enterPictureInPictureMode(params.build());
                    } else {
                        enterPictureInPictureMode();
                    }
                } catch (Exception e) {
                    // Device doesn't support picture-in-picture mode
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    private boolean wannaEnterToPip() {
        return mPlaybackFragment != null && mPlaybackFragment.getBackgroundMode() == PlaybackEngine.BACKGROUND_MODE_PIP && !isInPictureInPictureMode();
    }

    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");

        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        // NOTE: block back button for PIP.
        // User pressed PIP button in the player.
        if (!mSkipPip) {
            enterPipMode(); // NOTE: without this call app will hangs when pressing on PIP button
        }

        if (doNotDestroy() && !mSkipPip) {
            // Ensure to opening this activity when the user is returning to the app
            mViewManager.blockTop(this);
            mViewManager.startParentView(this);
        } else {
            if (mPlayerTweaksData.isKeepFinishedActivityEnabled()) {
                //moveTaskToBack(true); // Don't do this or you'll have problems when player overlaps other apps (e.g. casting)
                mViewManager.startParentView(this);
            } else {
                mPlaybackFragment.onFinish();
                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {
        mPlaybackFragment.onFinish();
        super.finishReally();
    }

    @Override
    public void onBackPressed() {
        mSkipPip = mGeneralData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        mSkipPip = false;
        super.onResume();
    }

    private boolean doNotDestroy() {
        sIsInPipMode = isInPipMode();
        return sIsInPipMode || mPlaybackFragment.getBackgroundMode() == PlaybackEngine.BACKGROUND_MODE_SOUND;
    }

    @SuppressWarnings("deprecation")
    private void enterBackgroundPlayMode() {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 26) {
            if (Build.VERSION.SDK_INT == 21) {
                // Playback pause fix?
                mPlaybackFragment.showOverlay(true);
            }

            if (mPlaybackFragment.isPlaying()) {
                // Argument equals true to notify the system that the activity
                // wishes to be visible behind other translucent activities
                if (!requestVisibleBehind(true)) {
                    // App-specific method to stop playback and release resources
                    // because call to requestVisibleBehind(true) failed
                    mPlaybackFragment.onDestroy();
                }
            } else {
                // Argument equals false because the activity is not playing
                requestVisibleBehind(false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onVisibleBehindCanceled() {
        // App-specific method to stop playback and release resources
        mPlaybackFragment.onDestroy();
        super.onVisibleBehindCanceled();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        mPlaybackFragment.onPIPChanged(isInPictureInPictureMode);

        if (!isInPictureInPictureMode) {
            // Disable collapse app to Home launcher
            mViewManager.enableMoveToBack(false);
        }
    }

    @Override
    public void onUserLeaveHint() {
        // Handle HOME only. Note, BACK is handled inside finish()
        // Check that user not open dialog/search activity instead of really leaving the activity
        // Activity may be overlapped by the dialog, back is pressed or new view started
        if (AppDialogPresenter.instance(this).isDialogShown() || mSkipPip || mViewManager.isNewViewPending() ||
                mGeneralData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK) {
            return;
        }

        switch (mPlaybackFragment.getBackgroundMode()) {
            case PlaybackEngine.BACKGROUND_MODE_PLAY_BEHIND:
                enterBackgroundPlayMode();
                // Do we need to do something additional when running Play Behind?
                break;
            case PlaybackEngine.BACKGROUND_MODE_PIP:
                enterPipMode();
                if (doNotDestroy()) {
                    // Ensure to opening this activity when the user is returning to the app
                    mViewManager.blockTop(this);
                    // Return to previous activity (create point from that app could be launched)
                    mViewManager.startParentView(this);
                    // Enable collapse app to Home launcher
                    mViewManager.enableMoveToBack(true);
                }
                break;
            case PlaybackEngine.BACKGROUND_MODE_SOUND:
                if (doNotDestroy()) {
                    // Ensure to continue a playback
                    mViewManager.blockTop(this);
                }
                break;
        }
    }

    public boolean isInPipMode() {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }

        return isInPictureInPictureMode();
    }

    public PlaybackView getPlaybackView() {
        return mPlaybackFragment;
    }
}
