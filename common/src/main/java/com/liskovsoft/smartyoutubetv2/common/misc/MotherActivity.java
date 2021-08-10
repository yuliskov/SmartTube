package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    private static int sNumActivities;
    protected static boolean sIsInPipMode;
    private ScreensaverManager mScreensaverManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());

        initDpi();
        initTheme();

        sNumActivities++;
        mScreensaverManager = new ScreensaverManager(this);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchKeyEvent(event);
    }

    public void finishReally() {
        try {
            super.finish();
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sNumActivities--;

        if (ViewManager.instance(this).isFinishing()) {
            boolean noActivities = sNumActivities == 0;
            boolean singlePipActivity = sNumActivities == 1 && sIsInPipMode;
            if (noActivities || singlePipActivity) {
                ViewManager.instance(this).forceFinishTheApp();
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase, LocaleUpdater.getSavedLocale(newBase)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 4K fix with AFR
        applyCustomConfig();
        // Most of the fullscreen tweaks could be performed in styles but not all.
        // E.g. Hide bottom navigation bar (couldn't be done in styles).
        Helpers.makeActivityFullscreen(this);

        mScreensaverManager.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mScreensaverManager.disable();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        applyCustomConfig();
    }

    public ScreensaverManager getScreensaverManager() {
        return mScreensaverManager;
    }

    protected void initTheme() {
        int rootThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (rootThemeResId > 0) {
            setTheme(rootThemeResId);
        }
    }

    //private void initDpi() {
    //    // Do caching to prevent sudden dpi change.
    //    // Could happen when screen goes off or after PIP mode.
    //    if (sCachedDisplayMetrics == null) {
    //        float uiScale = MainUIData.instance(this).getUIScale();
    //        DisplayMetrics displayMetrics = new DisplayMetrics();
    //        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    //        float widthRatio = DEFAULT_WIDTH / displayMetrics.widthPixels;
    //        displayMetrics.density = DEFAULT_DENSITY / widthRatio * uiScale;
    //        displayMetrics.scaledDensity = DEFAULT_DENSITY / widthRatio * uiScale;
    //        sCachedDisplayMetrics = displayMetrics;
    //    }
    //
    //    getResources().getDisplayMetrics().setTo(sCachedDisplayMetrics);
    //}

    private void initDpi() {
        // To adapt to resolution change (e.g. on AFR) we can't do caching.

        if (sCachedDisplayMetrics == null) {
            float uiScale = MainUIData.instance(this).getUIScale();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float widthRatio = DEFAULT_WIDTH / displayMetrics.widthPixels;
            float density = DEFAULT_DENSITY / widthRatio * uiScale;
            displayMetrics.density = density;
            displayMetrics.scaledDensity = density;
            sCachedDisplayMetrics = displayMetrics;
        }

        getResources().getDisplayMetrics().setTo(sCachedDisplayMetrics);
    }

    private void applyCustomConfig() {
        // NOTE: dpi should come after locale update to prevent resources overriding.

        // Fix sudden language change.
        // Could happen when screen goes off or after PIP mode.
        LocaleUpdater.applySavedLocale(this);

        // Fix sudden dpi change.
        // Could happen when screen goes off or after PIP mode.
        initDpi();
    }
}
