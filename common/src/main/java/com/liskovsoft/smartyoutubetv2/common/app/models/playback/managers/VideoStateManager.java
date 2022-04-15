package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class VideoStateManager extends PlayerEventListenerHelper {
    private static final String TAG = VideoStateManager.class.getSimpleName();
    private static final float RESTORE_POSITION_PERCENTS = 12;
    private boolean mIsPlayEnabled;
    private Video mVideo;
    private FormatItem mTempVideoFormat;
    private Disposable mHistoryAction;
    private PlayerData mPlayerData;
    private VideoStateService mStateService;
    private boolean mIsPlayBlocked;

    @Override
    public void onInitDone() { // called each time a video opened from the browser
        mPlayerData = PlayerData.instance(getActivity());
        mStateService = VideoStateService.instance(getActivity());

        // onInitDone usually called after openVideo (if PlaybackView is destroyed)
        // So, we need to repeat some things again.
        resetPositionOfNewVideo(mVideo);
    }

    /**
     * Fired after user clicked on video in browse activity<br/>
     * or video is opened from the intent
     */
    @Override
    public void openVideo(Video item) {
        // Ensure that we aren't running on presenter init stage
        if (getController() != null) {
            // Save state of the previous video.
            // In case video opened from phone and other stuff.
            if (!item.equals(mVideo)) { // video might be opened twice (when remote connection enabled). Fix for that.
                saveState();
            }
        }

        setPlayEnabled(true); // video just added

        mVideo = item;
        mTempVideoFormat = null;

        resetPositionOfNewVideo(item);
        resetSpeedOfNewVideo();
    }

    @Override
    public boolean onPreviousClicked() {
        boolean isFarFromStart = getController().getPositionMs() > 10_000;

        if (isFarFromStart) {
            saveState(); // in case user want to go to previous video
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
            setPlayEnabled(getController().getPlay());
            saveState();
        }
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
        // Restore formats again.
        // Maybe this could help with Shield format problem.
        // NOTE: produce multi thread exception:
        // Attempt to read from field 'java.util.TreeMap$TreeMapEntry java.util.TreeMap$TreeMapEntry.left' on a null object reference (TrackSelectorManager.java:181)
        //restoreFormats();

        // In this state video length is not undefined.
        restorePosition(item);
        restorePendingPosition(item);
        restoreSpeed(item);
        // Player thinks that subs not enabled if I enable it too early (e.g. on source change event).
        restoreSubtitleFormat();

        updateHistory();

        restoreVolume();
    }

    @Override
    public void onPlay() {
        showHideScreensaver(false);
        setPlayEnabled(true);
    }

    @Override
    public void onPause() {
        showHideScreensaver(true);
        setPlayEnabled(false);
        saveState();
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
        // Check LIVE threshold and set speed to normal
        restoreSpeed(getController().getVideo());
    }

    @Override
    public void onSeekEnd() {
        // Scenario: user opens ui and does some seeking
        saveState();
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
        persistVideoState();
    }

    private void clearStateOfNextVideo() {
        if (getController().getVideo() != null && getController().getVideo().nextMediaItem != null) {
            resetPosition(getController().getVideo().nextMediaItem.getVideoId());
        }
    }

    /**
     * Reset position of currently opened music and live videos.
     */
    private void resetPositionOfNewVideo(Video item) {
        if (mStateService == null || item == null) {
            return;
        }

        State state = mStateService.getByVideoId(item.videoId);

        // Reset position of music videos
        if (state != null && (state.lengthMs < VideoStateService.MUSIC_VIDEO_LENGTH_MS || item.isLive)) {
            resetPosition(item);
        }
    }

    private void resetSpeedOfNewVideo() {
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
                mStateService.save(new State(videoId, 0, state.lengthMs, state.speed));
            } else {
                mStateService.removeByVideoId(videoId);
            }
        }
    }

    private void persistVideoState() {
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
        if (!getController().containsMedia()) {
            return;
        }

        Video video = getController().getVideo();

        if (video != null) {
            savePosition(video);
            updateHistory();
            //persistVideoState();
            //persistVolume();
        }
    }

    private void savePosition(Video video) {
        // Exceptional cases:
        // 1) Track is ended
        // 2) Pause on end enabled
        // 3) Watching live stream in real time
        long lengthMs = getController().getLengthMs();
        long positionMs = getController().getPositionMs();
        long remainsMs = lengthMs - positionMs;
        boolean isPositionActual = remainsMs > 1_000;
        boolean isRealLiveStream = video.isLive && remainsMs < VideoStateService.LIVE_THRESHOLD_MS;
        if ((isPositionActual && !isRealLiveStream) || !getPlayEnabled()) { // Is pause after each video enabled?
            mStateService.save(new State(video.videoId, positionMs, lengthMs, getController().getSpeed()));
            // Sync video. You could safely use it later to restore state.
            video.percentWatched = positionMs / (lengthMs / 100f);
        } else {
            // Mark video as fully viewed. This could help to restore proper progress marker on the video card later.
            mStateService.save(new State(video.videoId, lengthMs, lengthMs, 1.0f));
            video.percentWatched = 100;

            // NOTE: Storage optimization!!!
            // Reset position when video almost ended
            //resetPosition(video);
        }

        Playlist.instance().sync(video);
    }

    private void restorePosition(Video item) {
        State state = mStateService.getByVideoId(item.videoId);

        // Ignore up to 10% watched because the video might be opened on phone and closed immediately.
        boolean containsWebPosition = item.percentWatched >= RESTORE_POSITION_PERCENTS;
        boolean stateIsOutdated = state == null || state.timestamp < item.timestamp;
        if (containsWebPosition && stateIsOutdated) {
            // Web state is buggy on short videos (e.g. video clips)
            boolean isLongVideo = getController().getLengthMs() > VideoStateService.MUSIC_VIDEO_LENGTH_MS;
            if (isLongVideo) {
                state = new State(item.videoId, convertToMs(item.percentWatched));
            }
        }

        // Do I need to check that item isn't live? (state != null && !item.isLive)
        if (state != null) {
            long remainsMs = getController().getLengthMs() - state.positionMs;
            // Url list videos at this stage has undefined (-1) length. So, we need to ensure that remains is positive.
            boolean isVideoEnded = remainsMs >= 0 && remainsMs < 1_000;
            if (!isVideoEnded || !getPlayEnabled()) {
                getController().setPositionMs(state.positionMs);
            }
        }

        if (!mIsPlayBlocked) {
            getController().setPlay(getPlayEnabled());
        }
    }

    /**
     * Restore position from description time code
     */
    private void restorePendingPosition(Video item) {
        if (item.pendingPosMs > 0) {
            getController().setPositionMs(item.pendingPosMs);
            item.pendingPosMs = 0;
        }
    }

    private void restoreSpeed(Video item) {
        boolean isLiveThreshold = getController().getLengthMs() - getController().getPositionMs() < VideoStateService.LIVE_THRESHOLD_MS;

        if (item.isLive && isLiveThreshold) {
            getController().setSpeed(1.0f);
        } else {
            State state = mStateService.getByVideoId(item.videoId);
            getController().setSpeed(state != null && mPlayerData.isRememberSpeedEachEnabled() ? state.speed : mPlayerData.getSpeed());
        }
    }

    private long convertToMs(float percentWatched) {
        if (percentWatched >= 100) {
            return -1;
        }

        long newPositionMs = (long) (getController().getLengthMs() / 100 * percentWatched);

        boolean samePositions = Math.abs(newPositionMs - getController().getPositionMs()) < 10_000;

        if (samePositions) {
            newPositionMs = -1;
        }

        return newPositionMs;
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

    //private void persistVolume() {
    //    mVolume = getController().getVolume();
    //}

    private void restoreVolume() {
        getController().setVolume(mPlayerData.getPlayerVolume());
    }

    private void restoreFormats() {
        restoreVideoFormat();
        restoreAudioFormat();
        restoreSubtitleFormat();
    }

    private void updateHistory() {
        Video video = getController().getVideo();

        if (video == null) {
            return;
        }

        RxUtils.disposeActions(mHistoryAction);
        
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> historyObservable;

        long positionSec = getController().getPositionMs() / 1_000;

        if (video.mediaItem != null) {
            historyObservable = mediaItemManager.updateHistoryPositionObserve(video.mediaItem, positionSec);
        } else { // video launched form ATV channels
            historyObservable = mediaItemManager.updateHistoryPositionObserve(video.videoId, positionSec);
        }

        mHistoryAction = RxUtils.execute(historyObservable);
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
}
