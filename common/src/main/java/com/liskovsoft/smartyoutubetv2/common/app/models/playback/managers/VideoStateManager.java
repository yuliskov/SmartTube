package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class VideoStateManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final long MUSIC_VIDEO_MAX_DURATION_MS = 6 * 60 * 1000;
    private static final long LIVE_THRESHOLD_MS = 90_000;
    private static final String TAG = VideoStateManager.class.getSimpleName();
    private boolean mIsPlayEnabled;
    private Video mVideo = new Video();
    private FormatItem mTempVideoFormat;
    private Disposable mHistoryAction;
    private PlayerData mPlayerData;
    private GeneralData mGeneralData;
    private PlayerTweaksData mPlayerTweaksData;
    private VideoStateService mStateService;
    private boolean mIsPlayBlocked;
    private int mTickleLeft;
    private boolean mIsBuffering;
    private final Runnable mStreamEndCheck = () -> {
        if (getVideo() != null && getVideo().isLive && mIsBuffering &&
                getController().getDurationMs() - getController().getPositionMs() < 3 * 60_000) {
            getController().reloadPlayback();
        }
    };

    @Override
    public void onInitDone() { // called each time a video opened from the browser
        mPlayerData = PlayerData.instance(getActivity());
        mGeneralData = GeneralData.instance(getActivity());
        mPlayerTweaksData = PlayerTweaksData.instance(getActivity());
        mStateService = VideoStateService.instance(getActivity());

        // onInitDone usually called after openVideo (if PlaybackView is destroyed)
        // So, we need to repeat some things again.
        resetPositionIfNeeded(getVideo());
    }

    /**
     * Fired after user clicked on video in browse activity<br/>
     * or video is opened from the intent
     */
    @Override
    public void openVideo(Video item) {
        // Ensure that we aren't running on presenter init stage
        if (getController() != null) {
            if (!item.equals(getVideo())) { // video might be opened twice (when remote connection enabled). Fix for that.
                // Reset auto-save history timer
                mTickleLeft = 0;
                // Save state of the previous video.
                // In case video opened from phone and other stuff.
                saveState();
            }
        }

        setPlayEnabled(true); // video just added

        mTempVideoFormat = null;

        // Don't do reset on videoLoaded state because this will influences minimized music videos.
        resetPositionIfNeeded(item);
        resetGlobalSpeedIfNeeded();
    }

    @Override
    public boolean onPreviousClicked() {
        boolean isFarFromStart = getController().getPositionMs() > 10_000;

        if (isFarFromStart) {
            saveState(); // in case the user wants to go to previous video
            getController().setPositionMs(0);
            return true;
        }

        return false;
    }

    @Override
    public boolean onNextClicked() {
        setPlayEnabled(true);

        saveState();

        clearStateOfNextVideo();

        return false;
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        setPlayEnabled(true); // autoplay video from suggestions

        saveState();
    }

    @Override
    public void onEngineInitialized() {
        // Reset auto-save history timer
        mTickleLeft = 0;

        // Restore before video loaded.
        // This way we override auto track selection mechanism.
        //restoreFormats();

        // Show user info instead of black screen.
        if (!getPlayEnabled()) {
            getController().showOverlay(true);
        }
    }

    @Override
    public void onEngineReleased() {
        // Save previous state
        if (getController().containsMedia()) {
            setPlayEnabled(getController().getPlayWhenReady());
            saveState();
        }

        Utils.removeCallbacks(mStreamEndCheck);
    }

    @Override
    public void onTickle() {
        // Sync history every five minutes
        if (++mTickleLeft > 5) {
            mTickleLeft = 0;
            updateHistory();
        }

        // Restore speed on LIVE end
        restoreSpeed();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        updateHistory();
    }

    @Override
    public void onEngineError(int type) {
        // Oops. Error happens while playing (network lost etc).
        if (getController().getPositionMs() > 1_000) {
            saveState();
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        mIsBuffering = false;
        // Actual video that match currently loaded one.
        mVideo = item;

        // Restore formats again.
        // Maybe this could help with Shield format problem.
        // NOTE: produce multi thread exception:
        // Attempt to read from field 'java.util.TreeMap$TreeMapEntry java.util.TreeMap$TreeMapEntry.left' on a null object reference (TrackSelectorManager.java:181)
        //restoreFormats();

        // In this state video length is not undefined.
        restorePosition();
        restorePendingPosition();
        restoreSpeed();
        // Player thinks that subs not enabled if I enable it too early (e.g. on source change event).
        restoreSubtitleFormat();

        restoreVolume();
    }

    @Override
    public void onPlay() {
        mIsBuffering = false;
        setPlayEnabled(true);
        showHideScreensaver(false);
    }

    @Override
    public void onPause() {
        setPlayEnabled(false);
        //saveState();
        showHideScreensaver(true);
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        if (!getController().isInPIPMode()) {
            if (track.getType() == FormatItem.TYPE_VIDEO) {
                if (mPlayerData.getFormat(FormatItem.TYPE_VIDEO).isPreset()) {
                    mTempVideoFormat = track;
                } else {
                    mTempVideoFormat = null;
                    mPlayerData.setFormat(track);
                }
            } else {
                mPlayerData.setFormat(track);
            }
        }
    }

    @Override
    public void onPlayEnd() {
        saveState();

        // Don't enable screensaver here or you'll broke 'screen off' logic.
        showHideScreensaver(true);
    }

    @Override
    public void onBuffering() {
        mIsBuffering = true;

        // Live stream starts to buffer after the end
        showHideScreensaver(true);

        // Restore speed on LIVE end or after seek
        restoreSpeed();

        Utils.postDelayed(mStreamEndCheck, 10_000);
    }

    @Override
    public void onSeekEnd() {
        // Scenario: user opens ui and does some seeking
        // NOTE: dangerous: there's possibility of simultaneous seeks (e.g. when sponsor block is enabled)
        //saveState();
    }

    @Override
    public void onControlsShown(boolean shown) {
        // NOTE: bug: current position saving to wrong video id. Explanation below.
        // Bug in casting: current video doesn't match currently loaded one into engine.
        //if (shown) {
        //    // Scenario: user clicked on channel button
        //    saveState();
        //}
    }

    @Override
    public void onSourceChanged(Video item) {
        restoreFormats();
    }

    @Override
    public void onViewPaused() {
        persistState();
    }

    @Override
    public void onVideoSpeedClicked(boolean enabled) {
        if (Helpers.floatEquals(mPlayerData.getLastSpeed(), 1.0f) || mPlayerTweaksData.isSpeedButtonOldBehaviorEnabled()) {
            onVideoSpeedLongClicked(enabled);
        } else {
            State state = mStateService.getByVideoId(getVideo() != null ? getVideo().videoId : null);
            float lastSpeed = mPlayerData.getLastSpeed();
            if (state != null && mPlayerData.isRememberSpeedEachEnabled()) {
                lastSpeed = !Helpers.floatEquals(1.0f, state.speed) ? state.speed : lastSpeed;
                mPlayerData.setLastSpeed(lastSpeed);
                mStateService.save(new State(state.videoId, state.positionMs, state.durationMs, enabled ? 1.0f : lastSpeed));
            }
            mPlayerData.setSpeed(enabled ? 1.0f : lastSpeed);
            getController().setSpeed(enabled ? 1.0f : lastSpeed);
        }
    }

    @Override
    public void onVideoSpeedLongClicked(boolean enabled) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.clear();

        // suppose live stream if buffering near the end
        // boolean isStream = Math.abs(player.getDuration() - player.getCurrentPosition()) < 10_000;
        AppDialogUtil.appendSpeedDialogItems(getActivity(), settingsPresenter, getController(), mPlayerData);

        settingsPresenter.showDialog(() -> {
            State state = mStateService.getByVideoId(getVideo() != null ? getVideo().videoId : null);
            if (state != null && mPlayerData.isRememberSpeedEachEnabled()) {
                mStateService.save(new State(state.videoId, state.positionMs, state.durationMs, mPlayerData.getSpeed()));
            }
        });
    }

    private void clearStateOfNextVideo() {
        if (getVideo() != null && getVideo().nextMediaItem != null) {
            resetPosition(getVideo().nextMediaItem.getVideoId());
        }
    }

    /**
     * Reset position of currently opened music and live videos.
     */
    private void resetPositionIfNeeded(Video item) {
        if (mStateService == null || item == null) {
            return;
        }

        State state = mStateService.getByVideoId(item.videoId);

        // Reset position of music videos
        boolean isShort = state != null && state.durationMs < MUSIC_VIDEO_MAX_DURATION_MS && !mPlayerTweaksData.isRememberPositionOfShortVideosEnabled();

        if (isShort || item.isLive || !mGeneralData.isHistoryEnabled()) {
            resetPosition(item);
        }
    }

    private void resetGlobalSpeedIfNeeded() {
        if (mPlayerData != null && !mPlayerData.isRememberSpeedEnabled()) {
            mPlayerData.setSpeed(1.0f);
        }
    }

    private void resetPosition(Video video) {
        video.percentWatched = 0;
        resetPosition(video.videoId);
    }

    private void resetPosition(String videoId) {
        State state = mStateService.getByVideoId(videoId);

        if (state != null) {
            if (mPlayerData.isRememberSpeedEachEnabled()) {
                mStateService.save(new State(videoId, 0, state.durationMs, state.speed));
            } else {
                mStateService.removeByVideoId(videoId);
            }
        }
    }

    private void persistState() {
        if (AppDialogPresenter.instance(getActivity()).isDialogShown()) {
            return;
        }

        mStateService.persistState();
    }

    private void restoreVideoFormat() {
        if (mTempVideoFormat != null) {
            getController().setFormat(mTempVideoFormat);
        } else {
            getController().setFormat(mPlayerData.getFormat(FormatItem.TYPE_VIDEO));
        }
    }

    private void restoreAudioFormat() {
        getController().setFormat(mPlayerData.getFormat(FormatItem.TYPE_AUDIO));
    }

    private void restoreSubtitleFormat() {
        getController().setFormat(mPlayerData.getFormat(FormatItem.TYPE_SUBTITLE));
    }

    private void saveState() {
        savePosition();
        updateHistory();
        //persistState();
    }

    private void savePosition() {
        Video video = getVideo();

        if (video == null || getController() == null || !getController().containsMedia()) {
            return;
        }

        // Exceptional cases:
        // 1) Track is ended
        // 2) Pause on end enabled
        // 3) Watching live stream in real time
        long durationMs = getController().getDurationMs();
        long positionMs = getController().getPositionMs();
        long remainsMs = durationMs - positionMs;
        boolean isPositionActual = remainsMs > 1_000;
        boolean isRealLiveStream = video.isLive && remainsMs < LIVE_THRESHOLD_MS;
        if ((isPositionActual && !isRealLiveStream) || !getPlayEnabled()) { // Is pause after each video enabled?
            mStateService.save(new State(video.videoId, positionMs, durationMs, getController().getSpeed()));
            // Sync video. You could safely use it later to restore state.
            video.percentWatched = positionMs / (durationMs / 100f);
        } else {
            // Mark video as fully viewed. This could help to restore proper progress marker on the video card later.
            mStateService.save(new State(video.videoId, durationMs, durationMs, 1.0f));
            video.percentWatched = 100;

            // NOTE: Storage optimization!!!
            // Reset position when video almost ended
            //resetPosition(video);
        }

        Playlist.instance().sync(video);
    }

    private void restorePosition() {
        Video item = getVideo();

        State state = mStateService.getByVideoId(item.videoId);

        boolean stateIsOutdated = state == null || state.timestamp < item.timestamp;
        if (item.getPositionMs() > 0 && stateIsOutdated) {
            // Web state is buggy on short videos (e.g. video clips)
            boolean isLongVideo = getController().getDurationMs() > MUSIC_VIDEO_MAX_DURATION_MS;
            if (isLongVideo) {
                state = new State(item.videoId, item.getPositionMs());
            }
        }

        // Web live position is broken. Ignore it.
        if (stateIsOutdated && item.isLive) {
            state = null;
        }

        // Set actual position for live videos with uncommon length
        if ((state == null || state.positionMs == state.durationMs) && item.isLive) {
            // Add buffer. Should I take into account segment offset???
            state = new State(item.videoId, getController().getDurationMs() - 60_000);
        }

        // Do I need to check that item isn't live? (state != null && !item.isLive)
        if (state != null) {
            long remainsMs = getController().getDurationMs() - state.positionMs;
            // Url list videos at this stage has undefined (-1) length. So, we need to ensure that remains is positive.
            boolean isVideoEnded = remainsMs >= 0 && remainsMs < (item.isLive ? 30_000 : 1_000); // live buffer fix
            if (!isVideoEnded || !getPlayEnabled()) {
                setPositionMs(state.positionMs);
            }
        }

        if (!mIsPlayBlocked) {
            getController().setPlayWhenReady(getPlayEnabled());
        }
    }

    private void updateHistory() {
        Video video = getVideo();

        if (video == null || !getController().containsMedia()) {
            return;
        }

        // Is this really safe? Could I lost history because of this?
        //RxUtils.disposeActions(mHistoryAction);

        MediaService service = YouTubeMediaService.instance();
        MediaItemService mediaItemManager = service.getMediaItemService();

        Observable<Void> historyObservable;

        long positionSec = video.isLive ? 0 : getController().getPositionMs() / 1_000;

        if (video.mediaItem != null) {
            historyObservable = mediaItemManager.updateHistoryPositionObserve(video.mediaItem, positionSec);
        } else { // video launched form ATV channels
            historyObservable = mediaItemManager.updateHistoryPositionObserve(video.videoId, positionSec);
        }

        mHistoryAction = RxUtils.execute(historyObservable);
    }

    /**
     * Restore position from description time code
     */
    private void restorePendingPosition() {
        Video item = getVideo();

        if (item.pendingPosMs > 0) {
            getController().setPositionMs(item.pendingPosMs);
            item.pendingPosMs = 0;
        }
    }

    private void restoreSpeed() {
        Video item = getVideo();

        if (isLiveThreshold() || isMusicVideo()) {
            getController().setSpeed(1.0f);
        } else {
            State state = mStateService.getByVideoId(item.videoId);
            getController().setSpeed(state != null && mPlayerData.isRememberSpeedEachEnabled() ? state.speed : mPlayerData.getSpeed());
        }
    }

    public void blockPlay(boolean block) {
        mIsPlayBlocked = block;
    }

    public void setPlayEnabled(boolean isPlayEnabled) {
        Log.d(TAG, "setPlayEnabled %s", isPlayEnabled);
        mIsPlayEnabled = isPlayEnabled;
    }

    public boolean getPlayEnabled() {
        return mIsPlayEnabled;
    }

    private void restoreVolume() {
        getController().setVolume(mPlayerData.getPlayerVolume());
    }

    private void restoreFormats() {
        restoreVideoFormat();
        restoreAudioFormat();
        restoreSubtitleFormat();
    }

    private void showHideScreensaver(boolean show) {
        if (getActivity() instanceof MotherActivity) {
            ScreensaverManager screensaverManager = ((MotherActivity) getActivity()).getScreensaverManager();

            if (show) {
                screensaverManager.enableChecked();
            } else {
                screensaverManager.disableChecked();
            }
        }
    }

    /**
     * Actual video that match currently loaded one.
     */
    private Video getVideo() {
        return mVideo;
    }

    private boolean isLiveThreshold() {
        Video item = getVideo();
        boolean isLiveThreshold = getController().getDurationMs() - getController().getPositionMs() < LIVE_THRESHOLD_MS;
        return item.isLive && isLiveThreshold;
    }

    private boolean isMusicVideo() {
        Video item = getVideo();
        return item.belongsToMusic();
    }

    private void setPositionMs(long positionMs) {
        boolean samePositions = Math.abs(positionMs - getController().getPositionMs()) < 10_000;
        if (!samePositions) {
            getController().setPositionMs(positionMs);
        }
    }
}
