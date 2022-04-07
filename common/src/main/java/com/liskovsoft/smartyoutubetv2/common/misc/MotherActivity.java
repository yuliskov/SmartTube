package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.ArrayList;
import java.util.List;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    protected static boolean sIsInPipMode;
    private ScreensaverManager mScreensaverManager;
    private List<OnPermissions> mOnPermissions;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Fixing: Only fullscreen opaque activities can request orientation (api 26)
        // NOTE: You should remove 'screenOrientation' from the manifest.
        // NOTE: Possible side effect: initDpi() won't work: "When you setRequestedOrientation() the view may be restarted"
        //if (VERSION.SDK_INT != 26) {
        //    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //}
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());

        initDpi();
        initTheme();

        mScreensaverManager = new ScreensaverManager(this);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchTouchEvent(event);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) { // shortcut for closing PIP
            PlaybackPresenter.instance(this).forceFinish();
            return true;
        }

        boolean result = super.onKeyDown(keyCode, event);

        // Fix buggy G20s menu key (focus lost on key press)
        return KeyHelpers.isMenuKey(keyCode) || result;
    }

    public void finishReally() {
        try {
            super.finish();
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Context contextWrapper = LocaleContextWrapper.wrap(newBase, LocaleUpdater.getSavedLocale(newBase));

        super.attachBaseContext(contextWrapper);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mOnPermissions != null) {
            for (OnPermissions callback : mOnPermissions) {
                callback.onPermissions(requestCode, permissions, grantResults);
            }
            mOnPermissions.clear();
            mOnPermissions = null;
        }
    }

    public void addOnPermissions(OnPermissions onPermissions) {
        if (mOnPermissions == null) {
            mOnPermissions = new ArrayList<>();
        }

        mOnPermissions.add(onPermissions);
    }

    public interface OnPermissions {
        void onPermissions(int requestCode, String[] permissions, int[] grantResults);
    }
}
