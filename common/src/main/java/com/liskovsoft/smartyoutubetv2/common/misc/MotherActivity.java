package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.locale.LangHelper;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());
        
        forceDpi1();
        initTheme();
    }

    private void forceDpi1() {
        float uiScale = MainUIData.instance(this).getUIScale();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float widthRatio = DEFAULT_WIDTH / displayMetrics.widthPixels;
        displayMetrics.density = DEFAULT_DENSITY / widthRatio * uiScale;
        displayMetrics.scaledDensity = DEFAULT_DENSITY / widthRatio * uiScale;
        getResources().getDisplayMetrics().setTo(displayMetrics);
    }

    private void forceDpi2() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayMetrics.density = 2.0f;
        displayMetrics.densityDpi = 320;
        displayMetrics.heightPixels = 1080;
        displayMetrics.widthPixels = 1920;
        displayMetrics.scaledDensity = 2.0f;
        displayMetrics.xdpi = 30.48f;
        displayMetrics.ydpi = 30.48f;
        getResources().getDisplayMetrics().setTo(displayMetrics);
    }

    private void forceDpi3() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayMetrics.densityDpi = DisplayMetrics.DENSITY_XHIGH;
        getResources().getDisplayMetrics().setTo(displayMetrics);
    }

    protected void initTheme() {
        int rootThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (rootThemeResId > 0) {
            setTheme(rootThemeResId);
        }
    }

    public void destroyActivity() {
        super.finish();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        LangUpdater updater = new LangUpdater(newBase);
        String langCode = updater.getUpdatedLocale();
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase, LangHelper.parseLangCode(langCode)));
    }
}
