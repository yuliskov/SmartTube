package com.r0adkll.slidr.model;


public class SlidrListenerAdapter implements SlidrListener {

    @Override
    public void onSlideStateChanged(int state) {
    }

    @Override
    public void onSlideChange(float percent) {
    }

    @Override
    public void onSlideOpened() {
    }

    @Override
    public boolean onSlideClosed() {
        return false;
    }
}
