package com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces;

import android.content.Context;

public interface Presenter<T> {
    void setView(T view);
    T getView();
    void setContext(Context context);
    Context getContext();
    void onViewInitialized();
    void onViewDestroyed();
    void onViewPaused();
    void onViewResumed();
    void onFinish();
}
