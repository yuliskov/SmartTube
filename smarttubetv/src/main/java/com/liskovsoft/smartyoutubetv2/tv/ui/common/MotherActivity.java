package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.os.Bundle;
import android.util.DisplayMetrics;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

public class MotherActivity extends FragmentActivity {
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //forceDpi2();
    }

    private void forceDpi1() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayMetrics.density = DEFAULT_DENSITY;
        displayMetrics.scaledDensity = DEFAULT_DENSITY;
        getResources().getDisplayMetrics().setTo(displayMetrics);
    }

    private void forceDpi2() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        //getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
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
}
