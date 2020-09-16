package com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces;

public interface Presenter<T> {
    void register(T view);
    void unregister(T view);
    void onInitDone();
}
