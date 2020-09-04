package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors.HistoryUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors.PlaylistUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors.StateRestorer;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors.SuggestionsLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors.VideoLoader;

import java.util.ArrayList;

public class MainPlayerEventBridge implements PlayerEventListener {
    private static final String TAG = MainPlayerEventBridge.class.getSimpleName();
    private final ArrayList<PlayerEventListener> mEventListeners;
    private final PlayerController mController;

    public MainPlayerEventBridge(PlayerController controller) {
        mController = controller;
        mEventListeners = new ArrayList<>();
        
        mEventListeners.add(new HistoryUpdater());
        mEventListeners.add(new SuggestionsLoader(controller));
        mEventListeners.add(new VideoLoader(controller));
        mEventListeners.add(new StateRestorer(controller));

        // should come last
        mEventListeners.add(new PlaylistUpdater());
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
    public void onPrevious() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onPrevious();
        }
    }

    @Override
    public void onNext() {
        for (PlayerEventListener listener : mEventListeners) {
            listener.onNext();
        }
    }

    @Override
    public void onInit(Video item) {
        if (item == null) {
            Log.e(TAG, "onOpenVideo: item is null");
            return;
        }

        for (PlayerEventListener listener : mEventListeners) {
            listener.onInit(item);
        }
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
}
