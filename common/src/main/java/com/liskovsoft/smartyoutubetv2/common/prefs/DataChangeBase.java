package com.liskovsoft.smartyoutubetv2.common.prefs;

import java.util.HashSet;
import java.util.Set;

public class DataChangeBase {
    private final Set<Runnable> mOnChangeList = new HashSet<>();

    public void setOnChange(Runnable callback) {
        mOnChangeList.add(callback);
    }

    public void removeOnChange(Runnable callback) {
        mOnChangeList.remove(callback);
    }

    protected void persistState() {
        for (Runnable callback : mOnChangeList) {
            callback.run();
        }
    }
}
