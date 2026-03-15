package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;

public class AppDialogActivity extends MotherActivity {
    private static final String TAG = AppDialogActivity.class.getSimpleName();
    private AppDialogFragment mFragment;
    private GlobalKeyTranslator mGlobalKeyTranslator;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_app_settings);
        // Can't use getSupportFragmentManager because AppDialogFragment isn't subclass of androidx fragment
        mFragment = (AppDialogFragment) getFragmentManager().findFragmentById(R.id.app_settings_fragment);

        mGlobalKeyTranslator = new PlayerKeyTranslator(this);
        mGlobalKeyTranslator.apply();
    }

    @Override
    protected void initTheme() {
        int settingsThemeResId = MainUIData.instance(this).getColorScheme().settingsThemeResId;
        if (settingsThemeResId > 0) {
            setTheme(settingsThemeResId);
        }
    }

    private void setupActivity() {
        // Fix crash in AppSettingsActivity: "Only fullscreen opaque activities can request orientation"
        // Error happen only on Android O (api 26) when you set "portrait" orientation in manifest
        // So, to fix the problem, set orientation here instead of manifest
        // More info: https://stackoverflow.com/questions/48072438/java-lang-illegalstateexception-only-fullscreen-opaque-activities-can-request-o
        if (VERSION.SDK_INT != 26) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //if (KeyHelpers.isConfirmKey(event.getKeyCode()) && !event.isLongPress()) {
        //    return true;
        //}

        //return mGlobalKeyTranslator.translate(event) || super.dispatchKeyEvent(event);
        KeyEvent newEvent = mGlobalKeyTranslator.translate(event);
        return handleCommentsDialogMediaControls(newEvent) || handleNavigation(newEvent) || super.dispatchKeyEvent(newEvent);
    }

    private boolean handleCommentsDialogMediaControls(KeyEvent event) {
        if (event == null || mFragment == null || !mFragment.isCommentsDialogShown()) {
            return false;
        }

        PlaybackView view = PlaybackPresenter.instance(this).getView();
        if (!(view instanceof Fragment)) {
            return false;
        }

        Fragment playbackView = (Fragment) view;
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && playbackView instanceof PlaybackFragment) {
                ((PlaybackFragment) playbackView).rewind();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && playbackView instanceof PlaybackFragment) {
                ((PlaybackFragment) playbackView).fastForward();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            Activity activity = playbackView.getActivity();
            if (activity != null) {
                activity.dispatchKeyEvent(event);
            }
            return true;
        }

        return false;
    }
    
    private boolean handleNavigation(KeyEvent event) {
        if (event == null) {
            return false;
        }

        // Toggle dialog
        if (!mFragment.isOverlay() && (KeyHelpers.isLeftRightKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            return true;
        }

        // Notification dialog type. Imitate notification behavior.
        if (mFragment.isOverlay() && (KeyHelpers.isNavigationKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            PlaybackView view = PlaybackPresenter.instance(this).getView();
            if (view instanceof Fragment) {
                Activity activity = ((Fragment) view).getActivity();
                if (activity != null) {
                    activity.dispatchKeyEvent(event);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void finish() {
        // NOTE: Fragment's onDestroy/onDestroyView are not reliable way to catch dialog finish
        Log.d(TAG, "Dialog finish");
        if (mFragment != null) { // fragment isn't created yet (expandable = true)
            mFragment.onFinish();
        }

        // Destroy dialog when BACK is pressed. NoHistory isn't reliable if combined with singleInstance
        finishReally();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Respect PIP mode
        if (ViewManager.instance(this).getTopView() == PlaybackView.class && PlaybackPresenter.instance(this).getContext() instanceof PlaybackActivity) {
            ((PlaybackActivity) PlaybackPresenter.instance(this).getContext()).onUserLeaveHint();
        }

        finish();
    }
}
