package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerHandlerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.AutoFrameRateManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.HqDialogManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.StateUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.VideoLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerUiEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MainPlayerEventBridge implements PlayerEventListener {
    private static final String TAG = MainPlayerEventBridge.class.getSimpleName();
    private final ArrayList<PlayerHandlerEventListener> mEventListeners = new ArrayList<PlayerHandlerEventListener>() {
        @Override
        public boolean add(PlayerHandlerEventListener listener) {
            ((PlayerEventListenerHelper) listener).setBridge(MainPlayerEventBridge.this);

            return super.add(listener);
        }
    };
    @SuppressLint("StaticFieldLeak")
    private static MainPlayerEventBridge sInstance;
    private WeakReference<PlaybackController> mController = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);

    public MainPlayerEventBridge() {
        PlayerUiManager uiManager = new PlayerUiManager();
        VideoLoader videoLoader = new VideoLoader();
        StateUpdater stateUpdater = new StateUpdater();
        SuggestionsLoader suggestionsLoader = new SuggestionsLoader();
        HqDialogManager hqDialogManager = new HqDialogManager(stateUpdater);
        suggestionsLoader.addMetadataListener(uiManager);

        // NOTE: position matters!!!
        mEventListeners.add(new AutoFrameRateManager(hqDialogManager));
        mEventListeners.add(uiManager);
        mEventListeners.add(hqDialogManager);
        mEventListeners.add(stateUpdater);
        mEventListeners.add(suggestionsLoader);
        mEventListeners.add(videoLoader);
    }

    public static MainPlayerEventBridge instance() {
        if (sInstance == null) {
            sInstance = new MainPlayerEventBridge();
        }

        return sInstance;
    }
    
    public void setController(PlaybackController controller) {
        if (controller != null) {
            if (mController.get() != controller) { // Be ready to re-init after app exit
                mController = new WeakReference<>(controller);
                mActivity = new WeakReference<>(((Fragment) controller).getActivity());
                process(PlayerHandlerEventListener::onInitDone);
            }
        }
    }

    public PlaybackController getController() {
        return mController.get();
    }

    public Activity getActivity() {
        return mActivity.get();
    }

    @Override
    public void openVideo(Video item) {
        process(listener -> listener.openVideo(item));
    }

    // Helpers

    private boolean chainProcess(ChainProcessor processor) {
        boolean result = false;

        for (PlayerHandlerEventListener listener : mEventListeners) {
            result = processor.process(listener);

            if (result) {
                break;
            }
        }

        return result;
    }

    private interface ChainProcessor {
        boolean process(PlayerHandlerEventListener listener);
    }

    private void process(Processor processor) {
        for (PlayerHandlerEventListener listener : mEventListeners) {
            processor.process(listener);
        }
    }

    private interface Processor {
        void process(PlayerHandlerEventListener listener);
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

    // Start engine events

    @Override
    public void onSourceChanged(Video item) {
        process(listener -> listener.onSourceChanged(item));
    }

    @Override
    public void onEngineInitialized() {
        process(PlayerHandlerEventListener::onEngineInitialized);
    }

    @Override
    public void onEngineReleased() {
        process(PlayerHandlerEventListener::onEngineReleased);
    }

    @Override
    public void onEngineError(int type) {
        process(listener -> listener.onEngineError(type));
    }

    @Override
    public void onPlay() {
        process(PlayerHandlerEventListener::onPlay);
    }

    @Override
    public void onPause() {
        process(PlayerHandlerEventListener::onPause);
    }

    @Override
    public void onPlayClicked() {
        process(PlayerHandlerEventListener::onPlayClicked);
    }

    @Override
    public void onPauseClicked() {
        process(PlayerHandlerEventListener::onPauseClicked);
    }
    
    @Override
    public void onSeek() {
        process(PlayerHandlerEventListener::onSeek);
    }

    @Override
    public void onPlayEnd() {
        process(PlayerHandlerEventListener::onPlayEnd);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        return chainProcess(listener -> listener.onKeyDown(keyCode));
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        process(listener -> listener.onRepeatModeClicked(modeIndex));
    }

    @Override
    public void onVideoLoaded(Video item) {
        process(listener -> listener.onVideoLoaded(item));
    }

    // End engine events

    // Start UI events

    @Override
    public void onSuggestionItemClicked(Video item) {
        process(listener -> listener.onSuggestionItemClicked(item));
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        process(listener -> listener.onSuggestionItemLongClicked(item));
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        process(listener -> listener.onScrollEnd(group));
    }

    @Override
    public boolean onPreviousClicked() {
        return chainProcess(PlayerHandlerEventListener::onPreviousClicked);
    }

    @Override
    public boolean onNextClicked() {
        return chainProcess(PlayerHandlerEventListener::onNextClicked);
    }

    @Override
    public void onHighQualityClicked() {
        process(PlayerUiEventListener::onHighQualityClicked);
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        process(listener -> listener.onSubscribeClicked(subscribed));
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        process(listener -> listener.onThumbsDownClicked(thumbsDown));
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        process(listener -> listener.onThumbsUpClicked(thumbsUp));
    }

    @Override
    public void onChannelClicked() {
        process(PlayerUiEventListener::onChannelClicked);
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        process(listener -> listener.onTrackSelected(track));
    }

    @Override
    public void onSubtitlesClicked() {
        process(PlayerUiEventListener::onSubtitlesClicked);
    }

    @Override
    public void onControlsShown(boolean shown) {
        process(listener -> listener.onControlsShown(shown));
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        process(listener -> listener.onTrackChanged(track));
    }

    @Override
    public void onPlaylistAddClicked() {
        process(PlayerUiEventListener::onPlaylistAddClicked);
    }

    @Override
    public void onVideoStatsClicked(boolean enabled) {
        process(listener -> listener.onVideoStatsClicked(enabled));
    }

    @Override
    public void onVideoSpeedClicked() {
        process(PlayerUiEventListener::onVideoSpeedClicked);
    }

    @Override
    public void onSearchClicked() {
        process(PlayerUiEventListener::onSearchClicked);
    }

    // End UI events
}
