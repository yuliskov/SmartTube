package com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class DoubleBackManager2 {
    private static final String TAG = DoubleBackManager2.class.getSimpleName();
    private static final int DEFAULT_REPEAT_COUNT = 2;
    private final Context mContext;
    private int mRepeatCount;
    private long mMsgShownTimeMs;

    public DoubleBackManager2(Context ctx) {
        mContext = ctx;
    }

    public void enableDoubleBackExit(Runnable onDoubleBack) {
        // Reset if the user didn't do second press within interval
        if (System.currentTimeMillis() - mMsgShownTimeMs > 5_000) {
            resetBackPressed();
        }

        mRepeatCount++;

        // same event fires multiple times
        boolean isDoubleBackPressed = mRepeatCount >= DEFAULT_REPEAT_COUNT;

        showMsg();

        if (isDoubleBackPressed) {
            onDoubleBack.run();
        }
    }

    private void showMsg() {
        if (mRepeatCount == (DEFAULT_REPEAT_COUNT - 1)) {
            MessageHelpers.showMessageThrottled(mContext, R.string.msg_press_again_to_exit);
            mMsgShownTimeMs = System.currentTimeMillis();
        }
    }

    private void resetBackPressed() {
        mRepeatCount = 0;
    }
}
