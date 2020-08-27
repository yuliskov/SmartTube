package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import java.util.ArrayList;
import java.util.List;

public abstract class PresenterBase<T> {
    protected final List<T> mViews = new ArrayList<T>();

    public void register(T view) {
        mViews.add(view);
    }

    public void unregister(T view) {
        mViews.remove(view);
    }

    public abstract void onInitDone();
}
