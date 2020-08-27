package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

public interface Presenter<T> {
    void register(T view);
    void unregister(T view);
    void onInitDone();
}
