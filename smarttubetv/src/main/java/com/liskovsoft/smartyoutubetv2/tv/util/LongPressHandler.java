package com.liskovsoft.smartyoutubetv2.tv.util;

import android.view.KeyEvent;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class LongPressHandler {
    // Long press handler
    private long mKeyTimeMs;
    private int mKeyCode;
    private int mKeyRepeatTimes;

    public void updateLongPressHandler(KeyEvent event) {
        int keyCode = event.getKeyCode();
        long currentTimeMs = System.currentTimeMillis();

        if (mKeyCode == keyCode && (currentTimeMs - mKeyTimeMs) < 100) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Utils.removeCallbacks(mOnKeyUpShortPress);
                mKeyRepeatTimes++;
            }

            if (mKeyRepeatTimes > 3 && event.getAction() == KeyEvent.ACTION_UP) {
                onKeyUpLongPress(keyCode);
            }
        } else {
            mKeyRepeatTimes = 0;
            Utils.postDelayed(mOnKeyUpShortPress, 500);
        }

        mKeyTimeMs = currentTimeMs;
        mKeyCode = keyCode;
    }

    private final Runnable mOnKeyUpShortPress = () -> {
        boolean result = onKeyUpShortPress(mKeyCode);
        if (!result) {
            //Utils.sendKey(this, mKeyCode);
        }
    };

    private boolean onKeyUpShortPress(int keyCode) {
        return false;
    }

    private void onKeyUpLongPress(int keyCode) {
        // run callback
    }
}
