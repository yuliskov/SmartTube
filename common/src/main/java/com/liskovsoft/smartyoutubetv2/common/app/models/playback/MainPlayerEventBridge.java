package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers.HistoryUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers.StateUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers.SuggestionsLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers.VideoLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerUiEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.ViewEventListener;

import java.util.ArrayList;

public class MainPlayerEventBridge implements PlayerEventListener {
    private static final String TAG = MainPlayerEventBridge.class.getSimpleName();
    private final ArrayList<PlayerEventListener> mEventListeners;
    private static MainPlayerEventBridge sInstance;

    public MainPlayerEventBridge() {
        mEventListeners = new ArrayList<>();

        // NOTE: position matters!!!
        mEventListeners.add(new PlayerUiManager());
        mEventListeners.add(new StateUpdater());
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
        process(listener -> listener.setController(controller));
    }

    @Override
    public void openVideo(Video item) {
        process(listener -> listener.openVideo(item));
    }

    // Helpers

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

    private void process(Processor processor) {
        for (PlayerEventListener listener : mEventListeners) {
            processor.process(listener);
        }
    }

    private interface Processor {
        void process(PlayerEventListener listener);
    }

    // End Helpers

    // Common events

    @Override
    public void onViewCreated() {
        process(ViewEventListener::onViewCreated);
    }

    @Override
    public void onViewDestroyed() {
        process(ViewEventListener::onViewDestroyed);
    }

    @Override
    public void onViewPaused() {
        process(ViewEventListener::onViewPaused);
    }

    @Override
    public void onViewResumed() {
        process(ViewEventListener::onViewResumed);
    }

    // End common events

    @Override
    public void onSuggestionItemClicked(Video item) {
        process(listener -> listener.onSuggestionItemClicked(item));
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        process(listener -> listener.onSuggestionItemLongClicked(item));
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
    public void onVideoLoaded(Video item) {
        process(listener -> listener.onVideoLoaded(item));
    }

    @Override
    public void onEngineInitialized() {
        process(PlayerEventListener::onEngineInitialized);
    }

    @Override
    public void onEngineReleased() {
        process(PlayerEventListener::onEngineReleased);
    }

    @Override
    public void onPlay() {
        process(PlayerEventListener::onPlay);
    }

    @Override
    public void onPause() {
        process(PlayerEventListener::onPause);
    }

    @Override
    public void onPlayClicked() {
        process(PlayerEventListener::onPlayClicked);
    }

    @Override
    public void onPauseClicked() {
        process(PlayerEventListener::onPauseClicked);
    }
    
    @Override
    public void onSeek() {
        process(PlayerEventListener::onSeek);
    }

    @Override
    public void onPlayEnd() {
        process(PlayerEventListener::onPlayEnd);
    }

    @Override
    public void onKeyDown(int keyCode) {
        process(listener -> listener.onKeyDown(keyCode));
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        process(listener -> listener.onRepeatModeClicked(modeIndex));
    }

    @Override
    public void onRepeatModeChange(int modeIndex) {
        process(listener -> listener.onRepeatModeChange(modeIndex));
    }

    @Override
    public void onHighQualityClicked() {
        process(PlayerUiEventListener::onHighQualityClicked);
    }
}
