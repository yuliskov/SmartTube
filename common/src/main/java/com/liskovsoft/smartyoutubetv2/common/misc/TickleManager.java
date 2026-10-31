package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.sharedutils.misc.WeakHashSet;

public class TickleManager {
    private static final String TAG = TickleManager.class.getSimpleName();
    private static TickleManager sInstance;
    private final Runnable mUpdateHandler = this::updateTickle;
    // Usually listener is a view. So use weak refs to not hold it forever.
    private final WeakHashSet<TickleListener> mListeners = new WeakHashSet<>();
    private boolean mIsEnabled = true;

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

    public void clear() {
        mListeners.clear();
        updateTickle();
    }

    public void runTask(Runnable task, long delayMs) {
        Utils.removeCallbacks(task);
        Utils.postDelayed(task, delayMs);
    }

    private void updateTickle() {
        Utils.removeCallbacks(mUpdateHandler);

        if (isEnabled() && !mListeners.isEmpty()) {
            mListeners.forEach(TickleListener::onTickle);

            // Align tickle by clock minutes
            long timeMillis = System.currentTimeMillis();
            long delayMs = 60_000 - timeMillis % 60_000;
            Log.d(TAG, "Updating tickle in %s ms...", delayMs);
            Utils.postDelayed(mUpdateHandler, delayMs);
        }
    }
}
