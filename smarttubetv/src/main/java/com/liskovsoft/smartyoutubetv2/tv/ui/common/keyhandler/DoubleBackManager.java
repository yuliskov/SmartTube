package com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler;

import android.app.Activity;
import android.os.Handler;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class DoubleBackManager {
    private static final String TAG = DoubleBackManager.class.getSimpleName();
    private static final int DEFAULT_REPEAT_COUNT = 2;
    private final Handler mHandler;
    private final Activity mContext;
    private static final long BACK_PRESS_DURATION_MS = 1_000;
    private boolean mEnableDoubleBackExit;
    private boolean mDownPressed;
    private boolean mIsDoubleBackPressed;
    private int mRepeatCount;
    private Runnable mOnDoubleBack;
    private boolean mIsMsgShown;
    private long mMsgShownTimeMs;

    public DoubleBackManager(Activity ctx) {
        mHandler = new Handler(ctx.getMainLooper());
        mContext = ctx;
    }

    private void checkLongPressExit(KeyEvent event) {
        boolean isBack =
                event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                        event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE ||
                        event.getKeyCode() == KeyEvent.KEYCODE_B;

        if (event.getAction() == KeyEvent.ACTION_DOWN && isBack) {
            if (event.getRepeatCount() == 3) { // same event fires multiple times
                mIsDoubleBackPressed = true;
            }
        }

        if (!isBack) {
            resetBackPressed();
        }
    }
    
    public void checkDoubleBack(KeyEvent event) {
        // Reset if the user didn't do second press within interval
        if (System.currentTimeMillis() - mMsgShownTimeMs > 5_000) {
            resetBackPressed();
        }

        if (ignoreEvent(event)) {
            Log.d(TAG, "Oops. Seems phantom key received. Ignoring... " + event);
            resetBackPressed();
            //return false;
            return;
        }

        if (isReservedKey(event)) {
            Log.d(TAG, "Found globally reserved key. Ignoring..." + event);
            resetBackPressed();
            //return false;
            return;
        }

        checkBackPressed(event);

        if (mIsDoubleBackPressed) {
            mOnDoubleBack.run();
        }

        //return mIsDoubleBackPressed;
    }

    public void enableDoubleBackExit(Runnable onDoubleBack) {
        mOnDoubleBack = onDoubleBack;

        showMsg();

        if (mEnableDoubleBackExit) {
            return;
        }

        resetBackPressed();
        mEnableDoubleBackExit = true;
    }

    private void checkBackPressed(KeyEvent event) {
        if (!mEnableDoubleBackExit) {
            resetBackPressed();
            return;
        }

        boolean isBack =
                event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                        event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE ||
                        event.getKeyCode() == KeyEvent.KEYCODE_B;

        if (event.getAction() == KeyEvent.ACTION_DOWN && isBack) {
            mRepeatCount++;

            if (mRepeatCount >= DEFAULT_REPEAT_COUNT) { // same event fires multiple times
                mIsDoubleBackPressed = true;
            }

            showMsg();
        }

        if (!isBack) {
            resetBackPressed();
        }
    }

    private void showMsg() {
        mIsMsgShown = false;
        if (mRepeatCount == (DEFAULT_REPEAT_COUNT - 1)) {
            MessageHelpers.showMessageThrottled(mContext, R.string.msg_press_again_to_exit);
            mIsMsgShown = true;
            mMsgShownTimeMs = System.currentTimeMillis();
        }
    }

    private void resetBackPressed() {
        mIsDoubleBackPressed = false;
        mEnableDoubleBackExit = false;
        mRepeatCount = 1;
    }

    /**
     * Ignore unpaired ACTION_UP events<br/>
     * Ignore UNKNOWN key codes
     */
    private boolean ignoreEvent(KeyEvent event) {
        if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mDownPressed = true;
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_UP && mDownPressed) {
            mDownPressed = false;
            return false;
        }

        return true;
    }

    private boolean isReservedKey(KeyEvent event) {
        return KeyHelpers.isAmbilightKey(event.getKeyCode());
    }

    public boolean isDoubleBackPressed() {
        return mIsDoubleBackPressed;
    }

    public boolean isMsgShown() {
        return mIsMsgShown;
    }
}
