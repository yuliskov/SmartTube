package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyCharacterMap.UnavailableException;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import java.util.ArrayList;
import java.util.List;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    protected static boolean sIsInPipMode;
    private ScreensaverManager mScreensaverManager;
    // Make static in case Don't keep activities enabled in Developer settings
    private static List<OnPermissions> mOnPermissions;
    private static List<OnResult> mOnResults;
    private long mLastKeyDownTime;
    private boolean mEnableThrottleKeyDown;
    private boolean mIsOculusQuestFixEnabled;
    private boolean mIsFullscreenModeEnabled;

    public interface OnPermissions {
        void onPermissions(int requestCode, String[] permissions, int[] grantResults);
    }

    public interface OnResult {
        void onResult(int requestCode, int resultCode, Intent data);
    }

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

        mIsOculusQuestFixEnabled = PlayerTweaksData.instance(this).isOculusQuestFixEnabled();
        mIsFullscreenModeEnabled = GeneralData.instance(this).isFullscreenModeEnabled();

        initDpi();
        initTheme();

        // Search Fullscreen routine inside onPause() method
        if (!mIsFullscreenModeEnabled) {
            // There's no way to do this programmatically!
            setTheme(R.style.FitSystemWindows);

            // totally disabling the translucency or any color placed on the status bar and navigation bar
            //if (Build.VERSION.SDK_INT >= 19) {
            //    Window w = getWindow();
            //    w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            //}
        }

        if (mIsOculusQuestFixEnabled) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        mScreensaverManager = new ScreensaverManager(this); // moved below the theme to fix side effects

        //Helpers.addFullscreenListener(this);
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

        try {
            return super.dispatchTouchEvent(event);
        } catch (NullPointerException | SecurityException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            // Attempt to invoke interface method 'boolean android.app.trust.ITrustManager.isDeviceLocked(int)' on a null object reference
            // Permission Denial: starting Intent
            // IllegalStateException: exitFreeformMode: You can only go fullscreen from freeform.
            e.printStackTrace();
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null) { // handled
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean isKeepScreenOff = mScreensaverManager.isScreenOff() && Helpers.equalsAny(event.getKeyCode(),
                    new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN});
            if (!isKeepScreenOff) {
                mScreensaverManager.enable();
            }
        }

        try {
            return super.dispatchKeyEvent(event);
        } catch (NullPointerException | IllegalArgumentException | IllegalStateException | SecurityException | UnavailableException e) {
            // NullPointerException: 'android.view.Window androidx.core.app.ComponentActivity.getWindow()' on a null object reference
            // IllegalArgumentException: View is not a direct child of HorizontalGridView
            // Fatal Exception: java.lang.IllegalStateException
            // android.permission.RECORD_AUDIO required for search (Android 5 mostly)
            // Fatal Exception: java.lang.SecurityException
            // Not allowed to bind to service Intent { act=android.speech.RecognitionService cmp=com.xgimi.duertts/com.baidu.duer.services.tvser
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) { // shortcut for closing PIP
            PlaybackPresenter.instance(this).forceFinish();
            return true;
        }

        boolean result = super.onKeyDown(keyCode, event);

        // Fix buggy G20s menu key (focus lost on key press)
        return KeyHelpers.isMenuKey(keyCode) || throttleKeyDown(keyCode) || result;
    }

    public void finishReally() {
        try {
            super.finish();
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    @Override
    protected void attachBaseContext(Context context) {
        Context contextWrapper = null;

        if (context != null) {
            contextWrapper = LocaleContextWrapper.wrap(context, LocaleUpdater.getSavedLocale(context));
        }

        super.attachBaseContext(contextWrapper);
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        // 4K fix with AFR
        applyCustomConfig();

        applyFullscreenModeIfNeeded();

        // Remove screensaver from the previous activity when closing current one.
        // Called on player's next track. Reason unknown.
        mScreensaverManager.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Remove screensaver from the previous activity when closing current one.
        // Called on player's next track. Reason unknown.
        mScreensaverManager.disable();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        applyCustomConfig();

        // Refresh metrics for new config (orientation, screen size, etc.)
        //sCachedDisplayMetrics = null;
        //
        //DisplayMetrics updatedMetrics = getCustomDisplayMetrics(this);
        //newConfig.densityDpi = (int)(updatedMetrics.density * 160);
        //
        //applyOverrideConfiguration(newConfig);
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
        getResources().getDisplayMetrics().setTo(getDisplayMetrics(this));
    }

    private static DisplayMetrics getDisplayMetrics(Context context) {
        // BUG: adapt to resolution change (e.g. on AFR)
        // Don't disable caching or you will experience weird sizes on cards in video suggestions (e.g. after exit from PIP)!
        if (sCachedDisplayMetrics == null) {
            WindowManager wm = getWindowManager(context);
            if (wm == null) {
                return null;
            }
            // NOTE: Don't replace with getResources().getDisplayMetrics(). Shows wrong metrics here!
            //getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            DisplayMetrics displayMetrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(displayMetrics);
            float uiScale = MainUIData.instance(context).getUIScale();
            // Take into the account screen orientation (e.g. when running on phone)
            int widthPixels = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            float widthRatio = DEFAULT_WIDTH / widthPixels;
            float density = DEFAULT_DENSITY / widthRatio * uiScale;
            displayMetrics.density = density;
            displayMetrics.scaledDensity = density;
            sCachedDisplayMetrics = displayMetrics;
        }

        return sCachedDisplayMetrics;
    }

    private static WindowManager getWindowManager(Context context) {
        try {
            return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        } catch (IllegalStateException e) {
            // IllegalStateException: System services not available to Activities before onCreate()
        }
        return null;
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

    private void applyFullscreenModeIfNeeded() {
        if (mIsFullscreenModeEnabled) {
            // Most of the fullscreen tweaks could be performed in styles but not all.
            // E.g. Hide bottom navigation bar (couldn't be done in styles).
            Helpers.makeActivityFullscreen2(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (mOnPermissions != null) {
            for (OnPermissions callback : mOnPermissions) {
                callback.onPermissions(requestCode, permissions, grantResults);
            }
            mOnPermissions.clear();
            mOnPermissions = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mOnResults != null) {
            for (OnResult callback : mOnResults) {
                callback.onResult(requestCode, resultCode, data);
            }
            mOnResults.clear();
            mOnResults = null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Oculus Quest fix: back button not closing the activity
        if (mIsOculusQuestFixEnabled) {
            finish();
        }
    }

    /**
     * NOTE: When enabled, you could face IllegalStateException: Can not perform this action after onSaveInstanceState<br/>
     * https://stackoverflow.com/questions/7469082/getting-exception-illegalstateexception-can-not-perform-this-action-after-onsa<br/>
     * https://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit?page=1&tab=scoredesc#tab-top
     */
    //@Override
    //protected void onSaveInstanceState(@NonNull Bundle outState) {
    //    // No call for super(). Bug on API Level > 11.
    //    //if (Utils.checkActivity(this)) {
    //    //    super.onSaveInstanceState(outState);
    //    //}
    //
    //    // Workaround is to override onSaveInstanceState and add something to the bundle prior to calling the super
    //    // https://stackoverflow.com/questions/7469082/getting-exception-illegalstateexception-can-not-perform-this-action-after-onsa
    //    outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
    //    super.onSaveInstanceState(outState);
    //}

    /**
     * Fatal Exception: java.lang.IllegalStateException <br/>
     * Can not perform this action after onSaveInstanceState <br/>
     * <a href="https://issuetracker.google.com/issues/37094575#comment28">More info</a>
     */
    //@Override
    //protected void onSaveInstanceState(@NonNull Bundle outState) {
    //    super.onSaveInstanceState(outState);
    //
    //    // Bug on Android 4, 5, 6
    //    if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT <= 23) {
    //        final View rootView = findViewById(android.R.id.content);
    //        if (rootView != null) {
    //            rootView.cancelPendingInputEvents();
    //        }
    //    }
    //}

    public void addOnPermissions(OnPermissions onPermissions) {
        if (mOnPermissions == null) {
            mOnPermissions = new ArrayList<>();
        }

        mOnPermissions.remove(onPermissions);
        mOnPermissions.add(onPermissions);
    }

    public void addOnResult(OnResult onResult) {
        if (mOnResults == null) {
            mOnResults = new ArrayList<>();
        }

        mOnResults.remove(onResult);
        mOnResults.add(onResult);
    }

    /**
     * Use this method only upon exiting from the app.<br/>
     * Big troubles with AFR resolution switch!
     */
    public static void invalidate() {
        sCachedDisplayMetrics = null;
        sIsInPipMode = false;
    }

    public static DisplayMetrics getCachedDisplayMetrics() {
        return sCachedDisplayMetrics;
    }

    /**
     * Comments focus fix<br/>
     * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
     */
    private boolean throttleKeyDown(int keyCode) {
        if (mEnableThrottleKeyDown && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            long current = System.currentTimeMillis();
            if (current - mLastKeyDownTime < 100) {
                return true;
            }

            mLastKeyDownTime = current;
        }

        return false;
    }

    /**
     * Comments focus fix<br/>
     * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
     */
    public void enableThrottleKeyDown(boolean enable) {
        mEnableThrottleKeyDown = enable;
    }

    //@Override
    //public void setTheme(int resid) {
    //    super.setTheme(resid);
    //
    //    // No way to do this programmatically!
    //    if (!GeneralData.instance(this).isFullscreenModeEnabled()) {
    //        super.setTheme(R.style.FitSystemWindows);
    //    }
    //}

    protected ViewManager getViewManager() {
        return ViewManager.instance(this);
    }

    protected GeneralData getGeneralData() {
        return GeneralData.instance(this);
    }

    protected PlayerTweaksData getPlayerTweaksData() {
        return PlayerTweaksData.instance(this);
    }

    protected PlayerData getPlayerData() {
        return PlayerData.instance(this);
    }
}
