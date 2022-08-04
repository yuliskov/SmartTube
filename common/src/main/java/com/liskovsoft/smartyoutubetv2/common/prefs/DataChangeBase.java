package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.utils.WeakHashSet;

public class DataChangeBase {
    private final WeakHashSet<Runnable> mOnChangeList = new WeakHashSet<>();

    public void setOnChange(Runnable callback) {
        mOnChangeList.add(callback);
    }

    public void removeOnChange(Runnable callback) {
        mOnChangeList.remove(callback);
    }

    protected void persistState() {
        mOnChangeList.forEach(Runnable::run);
    }
}
