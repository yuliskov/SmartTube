package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LangHelper;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.misc.LangUpdater;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler.DoubleBackManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler.LongClickManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity;

/**
 * This parent class contains common methods that run in every activity such as search.
 */
public abstract class LeanbackActivity extends MotherActivity {
    private static final String TAG = LeanbackActivity.class.getSimpleName();
    private LongClickManager mLongClickManager;
    private UriBackgroundManager mBackgroundManager;
    private ViewManager mViewManager;
    private ModeSyncManager mModeSyncManager;
    private DoubleBackManager mDoubleBackManager;
    private GestureDetector mGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLongClickManager = new LongClickManager();
        mBackgroundManager = new UriBackgroundManager(this);
        mViewManager = ViewManager.instance(this);
        mModeSyncManager = ModeSyncManager.instance();
        mDoubleBackManager = new DoubleBackManager(this);
    }

    @Override
    public boolean onSearchRequested() {
        // prevent start SearchTagsActivity from SearchTagsActivity
        if (!(this instanceof SearchTagsActivity)) {
            SearchPresenter.instance(this).startSearch(null);
        }
        return true;
    }
    
    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, event);

        mLongClickManager.dispatchKeyEvent(event);

        if (mDoubleBackManager.checkDoubleBack(event)) {
            properlyFinishTheApp();
        }

        return super.dispatchKeyEvent(event);
    }

    public boolean isLongClick() {
        return mLongClickManager.isLongClick();
    }

    public UriBackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    @Override
    protected void onStart() {
        super.onStart();

        mBackgroundManager.onStart();

        Helpers.makeActivityFullscreen(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // PIP fix: While entering/exiting PIP mode only Pause/Resume is called

        mModeSyncManager.restore(this);

        // We can't do it in the ViewManager because activity may be started from outside
        if (!mViewManager.addTop(this)) {
            // not added, probably move task to back is active
            destroyActivity();
        }
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
        if (!mViewManager.startParentView(this)) {
            mDoubleBackManager.enableDoubleBackExit();
        }
    }

    private void properlyFinishTheApp() {
        SplashPresenter.instance(this).unhold();
        mViewManager.clearCaches();
        destroyActivity();
    }
}
