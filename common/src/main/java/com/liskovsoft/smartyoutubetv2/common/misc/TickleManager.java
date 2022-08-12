package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.common.utils.WeakHashSet;

public class TickleManager {
    private static TickleManager sInstance;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateHandler = this::updateTickle;
    // Usually listener is a view. So use weak refs to not hold it forever.
    private final WeakHashSet<TickleListener> mListeners = new WeakHashSet<>();
    private boolean mIsEnabled = true;
    private static final long DEFAULT_INTERVAL_MS = 60_000;
    private long mIntervalMs = DEFAULT_INTERVAL_MS;

    public interface TickleListener {
        void onTickle();
    }

    private TickleManager() {
    }

    public static TickleManager instance() {
        if (sInstance == null) {
            sInstance = new TickleManager();
        }

        return sInstance;
    }

    public void addListener(TickleListener listener) {
        if (mListeners.add(listener)) {
            if (mListeners.size() == 1) { // periodic callback not started yet
                updateTickle();
            } else if (isEnabled()) {
                listener.onTickle(); // first run
            }
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
            mListeners.forEach(TickleListener::onTickle);
            mHandler.postDelayed(mUpdateHandler, mIntervalMs);
        }
    }
}
