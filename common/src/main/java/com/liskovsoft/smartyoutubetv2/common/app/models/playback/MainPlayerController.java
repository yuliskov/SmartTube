package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;

import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerUiEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.AutoFrameRateController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ContentBlockController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.HQDialogController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ChatController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.PlayerUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.RemoteController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainPlayerController implements PlayerEventListener {
    private static final String TAG = MainPlayerController.class.getSimpleName();
    private final List<PlayerEventListener> mEventListeners = new CopyOnWriteArrayList<PlayerEventListener>() {
        @Override
        public boolean add(PlayerEventListener listener) {
            ((PlayerEventListenerHelper) listener).setMainController(MainPlayerController.this);

            return super.add(listener);
        }
    };
    @SuppressLint("StaticFieldLeak")
    private static MainPlayerController sInstance;
    private WeakReference<PlayerManager> mPlayer = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private Video mPendingVideo;

    private MainPlayerController(Context context) {
        if (context instanceof Activity) {
            mActivity = new WeakReference<>((Activity) context);
        }

        // NOTE: position matters!!!
        //mEventListeners.add(new AutoFrameRateController());
        //mEventListeners.add(new PlayerUIController());
        //mEventListeners.add(new HQDialogController());
        //mEventListeners.add(new VideoStateController());
        //mEventListeners.add(new SuggestionsController());
        //mEventListeners.add(new VideoLoaderController());
        //mEventListeners.add(new RemoteController(context));
        //mEventListeners.add(new ContentBlockController());
        //mEventListeners.add(new ChatController());
        //mEventListeners.add(new CommentsController());

        // NOTE: position matters!!!
        mEventListeners.add(new VideoStateController());
        mEventListeners.add(new SuggestionsController());
        mEventListeners.add(new VideoLoaderController());
        mEventListeners.add(new RemoteController(context));
        mEventListeners.add(new ContentBlockController());
        mEventListeners.add(new AutoFrameRateController());
        mEventListeners.add(new PlayerUIController());
        mEventListeners.add(new HQDialogController());
        mEventListeners.add(new ChatController());
        mEventListeners.add(new CommentsController());
    }

    public static MainPlayerController instance(Context context) {
        if (sInstance == null) {
            sInstance = new MainPlayerController(context);
        }

        return sInstance;
    }
    
    public void setPlayer(PlayerManager player) {
        if (player != null) {
            if (mPlayer.get() != player) { // Be ready to re-init after app exit
                mPlayer = new WeakReference<>(player);
                mActivity = new WeakReference<>(((Fragment) player).getActivity());
                process(PlayerEventListener::onInit);
            }

            if (mPendingVideo != null) {
                openVideo(mPendingVideo);
                mPendingVideo = null;
            }
        }
    }

    public PlayerManager getPlayer() {
        return mPlayer.get();
    }

    public Activity getActivity() {
        return mActivity.get();
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
    public void openVideo(Video video) {
        if (mPlayer.get() == null) {
            mPendingVideo = video;
            return;
        }

        process(listener -> listener.openVideo(video));
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

    // Start engine events

    @Override
    public void onSourceChanged(Video item) {
        process(listener -> listener.onSourceChanged(item));
    }

    @Override
    public void onEngineInitialized() {
        TickleManager.instance().addListener(this);

        process(PlayerEventListener::onEngineInitialized);
    }

    @Override
    public void onEngineReleased() {
        TickleManager.instance().removeListener(this);

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
    public void onChatClicked(boolean enabled) {
        process(listener -> listener.onChatClicked(enabled));
    }

    @Override
    public void onChatLongClicked(boolean enabled) {
        process(listener -> listener.onChatLongClicked(enabled));
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
