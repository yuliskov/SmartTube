package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.AutoFrameRateController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ChatController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ContentBlockController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.HQDialogController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.PlayerUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.RemoteController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerUiEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils.ChainProcessor;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils.Processor;
import com.liskovsoft.youtubeapi.common.helpers.ServiceHelper;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlaybackPresenter extends BasePresenter<PlaybackView> implements PlayerEventListener {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static PlaybackPresenter sInstance;
    private final List<PlayerEventListener> mEventListeners = new CopyOnWriteArrayList<PlayerEventListener>() {
        @Override
        public boolean add(PlayerEventListener listener) {
            ((BasePlayerController) listener).setMainController(PlaybackPresenter.this);

            return super.add(listener);
        }
    };
    private WeakReference<Video> mVideo;
    private Video mPendingVideo;
    // Fix for using destroyed view
    private WeakReference<PlaybackView> mPlayer = new WeakReference<>(null);
    private long mNewVideoStartedTimeMs;

    private PlaybackPresenter(Context context) {
        super(context);

        // NOTE: position matters!!!
        mEventListeners.add(new VideoStateController());
        mEventListeners.add(new SuggestionsController());
        mEventListeners.add(new PlayerUIController());
        mEventListeners.add(new VideoLoaderController());
        mEventListeners.add(new RemoteController(context));
        mEventListeners.add(new ContentBlockController());
        mEventListeners.add(new AutoFrameRateController());
        mEventListeners.add(new HQDialogController());
        mEventListeners.add(new ChatController());
        mEventListeners.add(new CommentsController());
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        
        initControllers();
    }

    private void initControllers() {
        // Re-init after app exit
        process(PlayerEventListener::onInit);

        if (mPendingVideo != null) {
            onNewVideo(mPendingVideo);
            mPendingVideo = null;
        }
    }

    public boolean hasPendingVideo() {
        return mPendingVideo != null;
    }

    public void openVideo(String videoId) {
        openVideo(videoId, false, -1);
    }

    /**
     * Opens video item from splash view
     */
    public void openVideo(String videoId, boolean finishOnEnded, long timeMs) {
        if (videoId == null) {
            return;
        }

        Video video = Video.from(videoId);
        video.finishOnEnded = finishOnEnded;
        video.pendingPosMs = timeMs;
        openVideo(video);
    }

    public void openVideo(Video video) {
        if (video == null) {
            return;
        }

        if (getView() == null) {
            mPendingVideo = video;
        } else if (getView().isEmbed()) { // switching from the embed player to the fullscreen one
            // The embed player doesn't disposed properly
            // NOTE: don't release after init check because this depends on timings
            getView().finishReally();
            setView(null);
            getController(VideoStateController.class).saveState();
            onNewVideo(video);
        } else {
            onNewVideo(video);
        }

        getViewManager().startView(PlaybackView.class);
    }

    public Video getVideo() {
        return mVideo != null ? mVideo.get() : null;
    }

    public boolean isRunningInBackground() {
        return getView() != null &&
                getView().isEngineBlocked() &&
                //getView().getBackgroundMode() != PlayerEngine.BACKGROUND_MODE_DEFAULT &&
                getView().isEngineInitialized() &&
                !getViewManager().isPlayerInForeground() &&
                getContext() instanceof Activity && Utils.checkActivity((Activity) getContext()); // Check that activity is not in Finishing state
    }

    public boolean isInPipMode() {
        return getView() != null && getView().isInPIPMode();
    }

    public boolean isOverlayShown() {
        return getView() != null && getView().isOverlayShown();
    }

    public boolean isPlaying() {
        return getView() != null && getView().isPlaying();
    }

    public boolean isEngineBlocked() {
        return getView() != null && getView().isEngineBlocked();
    }

    public boolean isEngineInitialized() {
        return getView() != null && getView().isEngineInitialized();
    }

    //public int getBackgroundMode() {
    //    return getView() != null ? getView().getBackgroundMode() : -1;
    //}

    public void forceFinish() {
        if (getView() != null) {
            getView().finishReally();
        }
    }

    public void setPosition(String timeCode) {
        setPosition(ServiceHelper.timeTextToMillis(timeCode));
    }

    public void setPosition(long positionMs) {
        // Check that the user isn't open context menu on suggestion item
        // if (Utils.isPlayerInForeground(getContext()) && getView() != null && !getView().getController().isSuggestionsShown()) {
        if (getViewManager().isPlayerInForeground() && getView() != null) {
            getView().setPositionMs(positionMs);
            getView().setPlayWhenReady(true);
            getView().showOverlay(false);
        } else {
            Video video = VideoMenuPresenter.sVideoHolder.get();
            if (video != null) {
                video.pendingPosMs = positionMs;
                openVideo(video);
            }
        }
    }

    // Controller methods

    @Override
    public void setView(PlaybackView view) {
        super.setView(view);
        mPlayer = new WeakReference<>(view);

        // Fix playing the previous video when switching between embed and fullscreen players.
        // E.g. when the user pressed back on the Channel content screen
        if (view != null && view.getVideo() != null && isNewVideoExpired()) {
            mVideo = new WeakReference<>(view.getVideo());
        }
    }

    public PlaybackView getPlayer() {
        return mPlayer.get(); // return view even if the one is destroyed
    }

    public Activity getActivity() {
        return getContext() instanceof Activity ? (Activity) getContext() : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends PlayerEventListener> T getController(Class<T> clazz) {
        for (PlayerEventListener listener : mEventListeners) {
            if (clazz.isInstance(listener)) {
                return (T) listener;
            }
        }

        return null;
    }

    // Core events

    @Override
    public void onNewVideo(Video video) {
        process(listener -> listener.onNewVideo(video));
        mVideo = new WeakReference<>(video);
        mNewVideoStartedTimeMs = System.currentTimeMillis();
    }

    @Override
    public void onFinish() {
        process(PlayerEventListener::onFinish);
    }

    @Override
    public void onInit() {
        // NOP. Internal event.
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        process(listener -> listener.onMetadata(metadata));
    }

    // End core events

    // Helpers

    private boolean chainProcess(ChainProcessor<PlayerEventListener> processor) {
        return Utils.chainProcess(mEventListeners, processor);
    }

    private void process(Processor<PlayerEventListener> processor) {
        Utils.process(mEventListeners, processor);
    }

    private boolean isNewVideoExpired() {
        return System.currentTimeMillis() - mNewVideoStartedTimeMs > 1_000;
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
        getTickleManager().addListener(this);

        process(PlayerEventListener::onEngineInitialized);
    }

    @Override
    public void onEngineReleased() {
        getTickleManager().removeListener(this);

        process(PlayerEventListener::onEngineReleased);
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        process(listener -> listener.onEngineError(type, rendererIndex, error));
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
    public void onSeekEnd() {
        process(PlayerEventListener::onSeekEnd);
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        process(listener -> listener.onSeekPositionChanged(positionMs));
    }

    @Override
    public void onSpeedChanged(float speed) {
        process(listener -> listener.onSpeedChanged(speed));
    }

    @Override
    public void onPlayEnd() {
        process(PlayerEventListener::onPlayEnd);
    }

    @Override
    public void onBuffering() {
        process(PlayerEventListener::onBuffering);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        return chainProcess(listener -> listener.onKeyDown(keyCode));
    }

    @Override
    public void onVideoLoaded(Video item) {
        process(listener -> listener.onVideoLoaded(item));
    }

    @Override
    public void onTickle() {
        process(PlayerEventListener::onTickle);
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
    public void onScrollEnd(Video item) {
        process(listener -> listener.onScrollEnd(item));
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
    public void onHighQualityClicked() {
        process(PlayerUiEventListener::onHighQualityClicked);
    }

    @Override
    public void onDislikeClicked(boolean dislike) {
        process(listener -> listener.onDislikeClicked(dislike));
    }

    @Override
    public void onLikeClicked(boolean like) {
        process(listener -> listener.onLikeClicked(like));
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        process(listener -> listener.onTrackSelected(track));
    }

    @Override
    public void onSubtitleClicked(boolean enabled) {
        process(listener -> listener.onSubtitleClicked(enabled));
    }

    @Override
    public void onSubtitleLongClicked(boolean enabled) {
        process(listener -> listener.onSubtitleLongClicked(enabled));
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
    public void onDebugInfoClicked(boolean enabled) {
        process(listener -> listener.onDebugInfoClicked(enabled));
    }

    @Override
    public void onSpeedClicked(boolean enabled) {
        process(listener -> listener.onSpeedClicked(enabled));
    }

    @Override
    public void onSpeedLongClicked(boolean enabled) {
        process(listener -> listener.onSpeedLongClicked(enabled));
    }

    @Override
    public void onSeekIntervalClicked() {
        process(PlayerUiEventListener::onSeekIntervalClicked);
    }

    @Override
    public void onVideoInfoClicked() {
        process(PlayerUiEventListener::onVideoInfoClicked);
    }

    @Override
    public void onShareLinkClicked() {
        process(PlayerUiEventListener::onShareLinkClicked);
    }

    @Override
    public void onSearchClicked() {
        process(PlayerUiEventListener::onSearchClicked);
    }

    @Override
    public void onVideoZoomClicked() {
        process(PlayerUiEventListener::onVideoZoomClicked);
    }

    @Override
    public void onPipClicked() {
        process(PlayerUiEventListener::onPipClicked);
    }

    @Override
    public void onPlaybackQueueClicked() {
        process(PlayerUiEventListener::onPlaybackQueueClicked);
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        process(listener -> listener.onButtonClicked(buttonId, buttonState));
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        process(listener -> listener.onButtonLongClicked(buttonId, buttonState));
    }

    // End UI events
}
