package com.r0adkll.slidr;


import android.app.Activity;
import androidx.annotation.NonNull;
import com.r0adkll.slidr.model.SlidrConfig;


class ConfigPanelSlideListener extends ColorPanelSlideListener {

    private final SlidrConfig config;


    ConfigPanelSlideListener(@NonNull Activity activity, @NonNull SlidrConfig config) {
        super(activity, -1, -1);
        this.config = config;
    }


    @Override
    public void onStateChanged(int state) {
        if(config.getListener() != null){
            config.getListener().onSlideStateChanged(state);
        }
    }


    @Override
    public void onClosed() {
        if(config.getListener() != null){
            if(config.getListener().onSlideClosed()) {
                return;
            }
        }
        super.onClosed();
    }


    @Override
    public void onOpened() {
        if(config.getListener() != null){
            config.getListener().onSlideOpened();
        }
    }


    @Override
    public void onSlideChange(float percent) {
        super.onSlideChange(percent);
        if(config.getListener() != null){
            config.getListener().onSlideChange(percent);
        }
    }


    @Override
    protected int getPrimaryColor() {
        return config.getPrimaryColor();
    }


    @Override
    protected int getSecondaryColor() {
        return config.getSecondaryColor();
    }
}
