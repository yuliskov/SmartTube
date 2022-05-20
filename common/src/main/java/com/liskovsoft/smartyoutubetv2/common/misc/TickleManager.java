package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class TickleManager {
    private static TickleManager mTickleManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateHandler = this::updateTickle;
    // Usually listener is a view. So use weak refs to not hold it forever.
    private final List<WeakReference<TickleListener>> mListeners = new ArrayList<>();
    private boolean mIsEnabled = true;
    private static final long DEFAULT_INTERVAL_MS = 60_000;
    private long mIntervalMs = DEFAULT_INTERVAL_MS;

    public interface TickleListener {
        void onTickle();
    }

    private TickleManager() {
    }

    public static TickleManager instance() {
        if (mTickleManager == null) {
            mTickleManager = new TickleManager();
        }

        return mTickleManager;
    }

    public void addListener(TickleListener listener) {
        if (listener != null && !contains(listener)) {
            cleanup();
            mListeners.add(new WeakReference<>(listener));
            updateTickle();
        }
    }

    public void removeListener(TickleListener listener) {
        if (listener != null) {
            remove(listener);
        }
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
            for (WeakReference<TickleListener> listener : mListeners) {
                if (listener.get() != null) {
                    listener.get().onTickle();
                }
            }

            mHandler.postDelayed(mUpdateHandler, mIntervalMs);
        }
    }

    private boolean contains(TickleListener listener) {
        return Helpers.containsIf(mListeners, item -> listener.equals(item.get()));
    }

    private void remove(TickleListener listener) {
        Helpers.removeIf(mListeners, item -> listener.equals(item.get()));
    }

    private void cleanup() {
        Helpers.removeIf(mListeners, item -> item.get() == null);
    }
}
