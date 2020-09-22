package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerHandlerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.AutoFrameRateManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.HistoryUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.StateUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.VideoLoader;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerUiEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.util.ArrayList;

public class MainPlayerEventBridge implements PlayerEventListener {
    private static final String TAG = MainPlayerEventBridge.class.getSimpleName();
    private final ArrayList<PlayerHandlerEventListener> mEventListeners;
    private static MainPlayerEventBridge sInstance;
    private PlaybackController mController;
    private Activity mMainActivity;
    private Activity mParentActivity;

    public MainPlayerEventBridge() {
        mEventListeners = new ArrayList<>();

        PlayerUiManager uiManager = new PlayerUiManager();

        // NOTE: position matters!!!
        mEventListeners.add(new AutoFrameRateManager(uiManager));
        mEventListeners.add(uiManager);
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
    
    public void setController(PlaybackController controller) {
        if (controller != null) {
            Fragment fragment = (Fragment) controller;
            Activity mainActivity = fragment.getActivity();

            if (mController != controller) {
                mController = controller;

                process(listener -> listener.onController(controller));
            }

            if (mMainActivity != mainActivity) {
                mMainActivity = mainActivity;

                process(listener -> listener.onMainActivity(mainActivity));
            }
        }
    }
    
    public void setParentView(Object parentView) {
        if (parentView instanceof Fragment) {
            Fragment fragment = (Fragment) parentView;
            Activity parentActivity = fragment.getActivity();

            if (mParentActivity != parentActivity) {
                mParentActivity = parentActivity;

                process(listener -> {
                    listener.onParentActivity(parentActivity);
                });
            }
        }
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
        return chainProcess(PlayerHandlerEventListener::onPreviousClicked);
    }

    @Override
    public boolean onNextClicked() {
        return chainProcess(PlayerHandlerEventListener::onNextClicked);
    }

    @Override
    public void onVideoLoaded(Video item) {
        process(listener -> listener.onVideoLoaded(item));
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
    public void onTrackClicked(FormatItem track) {
        process(listener -> listener.onTrackClicked(track));
    }

    @Override
    public void onClosedCaptionsClicked() {
        process(PlayerUiEventListener::onClosedCaptionsClicked);
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
    public void onVideoStatsClicked() {
        process(PlayerUiEventListener::onVideoStatsClicked);
    }
}
