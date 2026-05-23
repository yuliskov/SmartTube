package com.liskovsoft.smartyoutubetv2.common.misc;

import android.util.Pair;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class BufferingDetector {
    private static final long BUFFERING_THRESHOLD_MS = 3_000;
    private static final long BUFFERING_WINDOW_MS = 60_000;
    private static final long BUFFERING_RECURRENCE_COUNT = 2;
    private static final long BUFFERING_CONTINUATION_MS = 20_000;

    private Pair<Integer, Long> mBufferingCount;
    private final Runnable mOnLongBuffering = this::updateBufferingCountIfNeeded;
    private final OnLongBuffering mCallback;

    public interface OnLongBuffering {
        void onLongBuffering();
    }

    public BufferingDetector(OnLongBuffering callback) {
        mCallback = callback;
    }

    public void onStartBuffering() {
        Utils.postDelayed(mOnLongBuffering, BUFFERING_THRESHOLD_MS);
    }

    public void onStopBuffering() {
        Utils.removeCallbacks(mOnLongBuffering);
    }

    /**
     * Reset buffering stats
     */
    public void reset() {
        mBufferingCount = null;
        onStopBuffering();
    }

    private void updateBufferingCountIfNeeded() {
        updateBufferingCount();
        if (isBufferingRecurrent()) {
            mBufferingCount = null;
            mCallback.onLongBuffering();
        } else {
            // Count continuous buffering as a new occurrences....
            Utils.postDelayed(mOnLongBuffering, BUFFERING_CONTINUATION_MS);
        }
    }

    private void updateBufferingCount() {
        final long currentTimeMs = System.currentTimeMillis();
        int bufferingCount = 0;
        long previousTimeMs = 0;

        if (mBufferingCount != null) {
            bufferingCount = mBufferingCount.first;
            previousTimeMs = mBufferingCount.second;
        }

        if (currentTimeMs - previousTimeMs < BUFFERING_WINDOW_MS) {
            bufferingCount++;
        } else {
            bufferingCount = 1;
        }

        mBufferingCount = new Pair<>(bufferingCount, currentTimeMs);
    }

    private boolean isBufferingRecurrent() {
        return mBufferingCount != null && mBufferingCount.first > BUFFERING_RECURRENCE_COUNT;
    }
}
