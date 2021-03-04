package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LangHelper;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.Locale;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    private static Locale sCachedLocale;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());

        initDpi();
        initTheme();
    }

    public void destroyActivity() {
        super.finish();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase, getLocale(newBase)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        Helpers.makeActivityFullscreen(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // NOTE: dpi should come after locale update to prevent resources overriding.

        // Fix sudden language change.
        // Could happen when screen goes off or after PIP mode.
        LocaleContextWrapper.apply(this, getLocale(this));

        // Fix sudden dpi change.
        // Could happen when screen goes off or after PIP mode.
        initDpi();
    }

    protected void initTheme() {
        int rootThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (rootThemeResId > 0) {
            setTheme(rootThemeResId);
        }
    }

    private void initDpi() {
        // Do caching to prevent sudden dpi change.
        // Could happen when screen goes off or after PIP mode.
        if (sCachedDisplayMetrics == null) {
            float uiScale = MainUIData.instance(this).getUIScale();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float widthRatio = DEFAULT_WIDTH / displayMetrics.widthPixels;
            displayMetrics.density = DEFAULT_DENSITY / widthRatio * uiScale;
            displayMetrics.scaledDensity = DEFAULT_DENSITY / widthRatio * uiScale;
            sCachedDisplayMetrics = displayMetrics;
        }

        getResources().getDisplayMetrics().setTo(sCachedDisplayMetrics);
    }

    private static Locale getLocale(Context context) {
        if (sCachedLocale == null) {
            LangUpdater updater = new LangUpdater(context);
            String langCode = updater.getUpdatedLocale();
            sCachedLocale = LangHelper.parseLangCode(langCode);
        }

        return sCachedLocale;
    }
}
