package com.r0adkll.slidr;

import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.os.Build;
import androidx.annotation.ColorInt;

import com.r0adkll.slidr.widget.SliderPanel;


class ColorPanelSlideListener implements SliderPanel.OnPanelSlideListener {

    private final Activity activity;
    private final int primaryColor;
    private final int secondaryColor;
    private final ArgbEvaluator evaluator = new ArgbEvaluator();


    ColorPanelSlideListener(Activity activity, @ColorInt int primaryColor, @ColorInt int secondaryColor) {
        this.activity = activity;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }


    @Override
    public void onStateChanged(int state) {
        // Unused.
    }


    @Override
    public void onClosed() {
        activity.finish();
        activity.overridePendingTransition(0, 0);
    }


    @Override
    public void onOpened() {
        // Unused.
    }


    @Override
    public void onSlideChange(float percent) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && areColorsValid()){
            int newColor = (int) evaluator.evaluate(percent, getPrimaryColor(), getSecondaryColor());
            activity.getWindow().setStatusBarColor(newColor);
        }
    }


    protected int getPrimaryColor() {
        return primaryColor;
    }


    protected int getSecondaryColor() {
        return secondaryColor;
    }


    protected boolean areColorsValid() {
        return getPrimaryColor() != -1 && getSecondaryColor() != -1;
    }
}
