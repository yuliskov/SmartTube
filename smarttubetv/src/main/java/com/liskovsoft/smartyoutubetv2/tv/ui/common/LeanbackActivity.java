package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler.DoubleBackManager2;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

/**
 * This parent class contains common methods that run in every activity such as search.
 */
public abstract class LeanbackActivity extends MotherActivity {
    private static final String TAG = LeanbackActivity.class.getSimpleName();
    private UriBackgroundManager mBackgroundManager;
    private ViewManager mViewManager;
    private ModeSyncManager mModeSyncManager;
    private DoubleBackManager2 mDoubleBackManager;
    private GeneralData mGeneralData;
    private GlobalKeyTranslator mGlobalKeyTranslator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fix situations when the app is killed but the activity is restored by the system
        //if (savedInstanceState != null) {
        //    finishTheApp();
        //}
        mBackgroundManager = new UriBackgroundManager(this);
        mViewManager = ViewManager.instance(this);
        mModeSyncManager = ModeSyncManager.instance();
        mDoubleBackManager = new DoubleBackManager2(this);
        mGeneralData = GeneralData.instance(this);
        mGlobalKeyTranslator = this instanceof PlaybackActivity ?
                new PlayerKeyTranslator(this) :
                new GlobalKeyTranslator(this);
        mGlobalKeyTranslator.apply();
    }

    @Override
    public boolean onSearchRequested() {
        SearchPresenter.instance(this).startSearch(null);
        return true;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, event);

        KeyEvent newEvent = mGlobalKeyTranslator.translateAlt(event);
        return super.dispatchKeyEvent(newEvent);
    }

    public UriBackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    @Override
    protected void onStart() {
        super.onStart();

        mBackgroundManager.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // PIP fix: While entering/exiting PIP mode only Pause/Resume is called

        mGlobalKeyTranslator.apply(); // adapt to state changes (like enter/exit from PIP mode)

        mModeSyncManager.restore(this);

        mViewManager.addTop(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBackgroundManager.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBackgroundManager.onDestroy();
    }

    @Override
    public void finish() {
        // user pressed back key
        if (!mViewManager.hasParentView(this)) {
            switch (mGeneralData.getAppExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBackManager.enableDoubleBackExit(this::finishTheApp);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishTheApp();
                    break;
            }
        } else if (this instanceof PlaybackActivity) {
            switch (mGeneralData.getPlayerExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBackManager.enableDoubleBackExit(this::finishReally);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishReally();
                    break;
            }
        } else {
            finishReally();
        }
    }

    @Override
    public void finishReally() {
        // Mandatory line. Fix un-proper view order (especially for playback view).
        mViewManager.startParentView(this);
        super.finishReally();
    }

    private void finishTheApp() {
        mViewManager.properlyFinishTheApp(this);
    }
}
