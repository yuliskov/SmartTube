package com.liskovsoft.smartyoutubetv2.presenters;

import java.util.ArrayList;
import java.util.List;

public abstract class Presenter<T> {
    protected final List<T> mViews = new ArrayList<T>();

    public void subscribe(T view) {
        mViews.add(view);
    }

    public void unsubscribe(T view) {
        mViews.remove(view);
    }
}
