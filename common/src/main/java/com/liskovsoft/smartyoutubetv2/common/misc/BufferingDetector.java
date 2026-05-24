package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class BufferingDetector {
    private static final long BUFFERING_WINDOW_MS = 60_000;
    private static final long BUFFERING_DURATION_MS = 20_000;
    
    private long mBeginTimeMs;
    private long mStartTimeMs;
    private long mTotalDurationMs;
    private final Runnable mOnLongBuffering = this::onLongBuffering;
    private final OnLongBuffering mCallback;

    public interface OnLongBuffering {
        void onLongBuffering();
    }

    public BufferingDetector(@NonNull OnLongBuffering callback) {
        mCallback = callback;
    }

    public void onStartBuffering() {
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - mBeginTimeMs > BUFFERING_WINDOW_MS) {
            mBeginTimeMs = currentTimeMs;
            mTotalDurationMs = 0;
        }
        mStartTimeMs = currentTimeMs;
        Utils.postDelayed(mOnLongBuffering, BUFFERING_DURATION_MS - mTotalDurationMs);
    }

    public void onStopBuffering() {
        Utils.removeCallbacks(mOnLongBuffering);
        long stopTimeMs = System.currentTimeMillis();
        mTotalDurationMs += (stopTimeMs - mStartTimeMs);
    }

    /**
     * Reset buffering stats
     */
    public void reset() {
        mBeginTimeMs = mStartTimeMs = mTotalDurationMs = 0;
        Utils.removeCallbacks(mOnLongBuffering);
    }

    private void onLongBuffering() {
        reset();
        mCallback.onLongBuffering();
    }
}
