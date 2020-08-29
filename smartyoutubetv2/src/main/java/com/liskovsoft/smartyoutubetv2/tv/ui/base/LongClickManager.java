package com.liskovsoft.smartyoutubetv2.tv.ui.base;

import android.view.KeyEvent;

public class LongClickManager {
    private int mEventRepeatNums;
    private int mLastEventCode;
    private long mLastEventTimeMs;

    public void dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        long currentTimeMillis = System.currentTimeMillis();
        boolean sameKey = mLastEventCode == keyCode;
        boolean withinASecond = (currentTimeMillis - mLastEventTimeMs) < 1_000;
        mLastEventCode = keyCode;
        mLastEventTimeMs = currentTimeMillis;

        if (sameKey && withinASecond) {
            mEventRepeatNums++;
        } else {
            mEventRepeatNums = 0;
        }
    }

    public boolean isLongClick() {
        return mEventRepeatNums > 3;
    }
}
