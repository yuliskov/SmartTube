package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners.HistoryUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners.StateUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners.SuggestionsLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners.VideoLoader;

import java.util.ArrayList;

public class MainPlayerEventBridge implements PlayerEventBridge {
    private static final String TAG = MainPlayerEventBridge.class.getSimpleName();
    private final ArrayList<PlayerEventBridge> mEventListeners;
    private static MainPlayerEventBridge sInstance;

    public MainPlayerEventBridge() {
        mEventListeners = new ArrayList<>();

        // NOTE: position matters!!!
        mEventListeners.add(new StateUpdater());
        mEventListeners.add(new PlayerUiManager());
        mEventListeners.add(new HistoryUpdater());
        mEventListeners.add(new SuggestionsLoader());
        mEventListeners.add(new VideoLoader());
    }

    public static MainPlayerEventBridge instance() {
        if (sInstance == null) {
            sInstance = new MainPlayerEventBridge();
        }

        return sInstance;
    }

    @Override
    public void setController(PlayerController controller) {
        for (PlayerEventBridge listener : mEventListeners) {
            listener.setController(controller);
        }
    }

    @Override
    public void openVideo(Video item) {
        if (item == null) {
            Log.e(TAG, "load: item is null");
            return;
        }

        for (PlayerEventBridge listener : mEventListeners) {
            listener.openVideo(item);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        if (item == null) {
            Log.e(TAG, "onSuggestionItemClicked: item is null");
            return;
        }

        for (PlayerEventListener listener : mEventListeners) {
            listener.onSuggestionItemClicked(item);
        }
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        if (item == null) {
            Log.e(TAG, "onSuggestionItemLongClicked: item is null");
            return;
        }

        for (PlayerEventListener listener : mEventListeners) {
            listener.onSuggestionItemLongClicked(item);
        }
    }

    @Override
    public boolean onPreviousClicked() {
        return chainProcess(PlayerEventListener::onPreviousClicked);
    }

    @Override
    public boolean onNextClicked() {
        return chainProcess(PlayerEventListener::onNextClicked);
    }

    @Override
    public void onViewCreated() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onViewCreated();
        }
    }

    @Override
    public void onViewDestroyed() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onViewDestroyed();
        }
    }

    @Override
    public void onViewPaused() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onViewPaused();
        }
    }

    @Override
    public void onViewResumed() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onViewResumed();
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onVideoLoaded(item);
        }
    }

    @Override
    public void onEngineInitialized() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onEngineInitialized();
        }
    }

    @Override
    public void onEngineReleased() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onEngineReleased();
        }
    }

    @Override
    public void onPlay() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onPlay();
        }
    }

    @Override
    public void onPause() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onPause();
        }
    }

    @Override
    public void onPlayClicked() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onPlayClicked();
        }
    }

    @Override
    public void onPauseClicked() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onPauseClicked();
        }
    }

    // Not implemented
    @Override
    public void onSeek() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onSeek();
        }
    }

    @Override
    public void onPlayEnd() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onPlayEnd();
        }
    }

    @Override
    public void onKeyDown(int keyCode) {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onKeyDown(keyCode);
        }
    }

    private boolean chainProcess(ChainProcessor processor) {
        boolean result = false;

        for (PlayerEventListener listener : mEventListeners) {
            result = processor.process(listener);

            if (result) {
                break;
            }
        }

        return result;
    }

    private interface ChainProcessor {
        boolean process(PlayerEventListener listener);
    }
}
