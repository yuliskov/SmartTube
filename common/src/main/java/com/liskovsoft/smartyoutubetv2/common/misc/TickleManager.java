package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class TickleManager {
    private static TickleManager mTickleManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateHandler = this::updateTickle;
    // Usually listener is a view. So use weak refs to not hold it forever.
    private final Set<TickleListener> mListeners = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean mIsEnabled = true;
    private static final long DEFAULT_INTERVAL_MS = 10_000;
    private long mIntervalMs = DEFAULT_INTERVAL_MS;

    private TickleManager() {
    }

    public static TickleManager instance() {
        if (mTickleManager == null) {
            mTickleManager = new TickleManager();
        }

        return mTickleManager;
    }

    public void addListener(TickleListener listener) {
        if (listener != null) {
            mListeners.add(listener);
            updateTickle();
        }
    }

    public void removeListener(TickleListener listener) {
        mListeners.remove(listener);
    }

    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
        updateTickle();
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setIntervalMs(long intervalMs) {
        mIntervalMs = intervalMs;
    }

    public long getIntervalMs() {
        return mIntervalMs;
    }

    public void clear() {
        mListeners.clear();
        updateTickle();
    }

    public void runTask(Runnable task, long delayMs) {
        mHandler.removeCallbacks(task);
        mHandler.postDelayed(task, delayMs);
    }

    private void updateTickle() {
        mHandler.removeCallbacks(mUpdateHandler);

        if (isEnabled() && !mListeners.isEmpty()) {
            for (TickleListener listener : mListeners) {
                listener.onTickle();
            }

            mHandler.postDelayed(mUpdateHandler, mIntervalMs);
        }
    }

    public interface TickleListener {
        void onTickle();
    }
}
