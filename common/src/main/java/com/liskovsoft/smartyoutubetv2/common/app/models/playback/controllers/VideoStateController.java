package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class VideoStateController extends BasePlayerController {
    private static final String TAG = VideoStateController.class.getSimpleName();
    private static final long MUSIC_VIDEO_MAX_DURATION_MS = 6 * 60 * 1000;
    private static final long LIVE_THRESHOLD_MS = 90_000; // should be greater than the live buffer
    private static final long LIVE_BUFFER_MS = 60_000;
    private static final long SHORT_LIVE_BUFFER_MS = 0; // Note, on buffer lower than the 60sec you'll notice segment skip
    private static final long BEGIN_THRESHOLD_MS = 10_000;
    private static final int HISTORY_UPDATE_INTERVAL_MINUTES = 5; // Sync history every five minutes
    private boolean mIsPlayEnabled;
    private Video mVideo = new Video();
    private PlayerData mPlayerData;
    private GeneralData mGeneralData;
    private PlayerTweaksData mPlayerTweaksData;
    private RemoteControlData mRemoteControlData;
    private VideoStateService mStateService;
    private boolean mIsPlayBlocked;
    private int mTickleLeft;
    private boolean mIncognito;
    private final Runnable mUpdateHistory = this::updateHistory;

    @Override
    public void onInit() { // called each time a video opened from the browser
        mPlayerData = PlayerData.instance(getContext());
        mGeneralData = GeneralData.instance(getContext());
        mPlayerTweaksData = PlayerTweaksData.instance(getContext());
        mRemoteControlData = RemoteControlData.instance(getContext());
        mStateService = VideoStateService.instance(getContext());
    }

    /**
     * Fired after user clicked on video in browse activity<br/>
     * or video is opened from the intent
     */
    @Override
    public void onNewVideo(Video item) {
        // Ensure that we aren't running on presenter init stage
        if (getPlayer() != null) {
            if (!item.equals(getVideo())) { // video might be opened twice (when remote connection enabled). Fix for that.
                // Reset auto-save history timer
                mTickleLeft = 0;
                // Save state of the previous video.
                // In case video opened from phone and other stuff.
                saveState();
            }
        }

        setPlayEnabled(true); // video just added

        mPlayerData.setTempVideoFormat(null);

        enableIncognitoIfNeeded(item);

        // Don't do reset on videoLoaded state because this will influences minimized music videos.
        resetPositionIfNeeded(item);
        resetGlobalSpeedIfNeeded();
    }

    @Override
    public boolean onPreviousClicked() {
        // Seek to the start on prev
        if (getPlayer().getPositionMs() > BEGIN_THRESHOLD_MS && !getVideo().isShorts) {
            saveState(); // in case the user wants to go to previous video
            getPlayer().setPositionMs(100);
            return true;
        }

        // Pass to others handlers
        return false;
    }

    @Override
    public boolean onNextClicked() {
        // Seek to the actual live position on next
        if (getVideo() != null && getVideo().isLive && (getPlayer().getDurationMs() - getPlayer().getPositionMs() > LIVE_THRESHOLD_MS)) {
            long buffer = mPlayerTweaksData.isBufferOnStreamsDisabled() ? SHORT_LIVE_BUFFER_MS : LIVE_BUFFER_MS;
            getPlayer().setPositionMs(getPlayer().getDurationMs() - buffer);
            return true;
        }

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
            getPlayer().showOverlay(true);
        }
    }

    @Override
    public void onEngineReleased() {
        // Save previous state
        if (getPlayer().containsMedia()) {
            setPlayEnabled(getPlayer().getPlayWhenReady());
            saveState();
        }
    }

    @Override
    public void onTickle() {
        if (getPlayer() == null || !getPlayer().isEngineInitialized()) {
            return;
        }

        if (++mTickleLeft > HISTORY_UPDATE_INTERVAL_MINUTES && getPlayer().isPlaying()) {
            mTickleLeft = 0;
            updateHistory();
        }

        // Restore speed on LIVE end
        restoreSpeed();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        updateHistory(); // start watching?

        // Channel info should be loaded at this point
        restoreSubtitleFormat();

        // Need to contain channel id
        restoreSpeed();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        // Oops. Error happens while playing (network lost etc).
        if (getPlayer().getPositionMs() > 1_000) {
            saveState();
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
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
        //restoreSpeed();
        // Player thinks that subs not enabled if I enable it too early (e.g. on source change event).
        //restoreSubtitleFormat();

        restoreVolume();
        restorePitch();
    }

    @Override
    public void onPlay() {
        setPlayEnabled(true);
        showHideScreensaver(false);
        // throttle seeking calls
        Utils.removeCallbacks(mUpdateHistory);
    }

    @Override
    public void onPause() {
        setPlayEnabled(false);
        showHideScreensaver(true);
        // throttle seeking calls
        Utils.postDelayed(mUpdateHistory, 10_000);
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        //if (!getPlayer().isInPIPMode()) {
        //    if (track.getType() == FormatItem.TYPE_VIDEO) {
        //        if (mPlayerData.getFormat(FormatItem.TYPE_VIDEO).isPreset()) {
        //            mTempVideoFormat = track;
        //        } else {
        //            mTempVideoFormat = null;
        //            mPlayerData.setFormat(track);
        //        }
        //    } else {
        //        mPlayerData.setFormat(track);
        //    }
        //}

        //if (!getPlayer().isInPIPMode()) {
        //    if (track.getType() == FormatItem.TYPE_VIDEO) {
        //        mTempVideoFormat = mPlayerData.getFormat(FormatItem.TYPE_VIDEO).isPreset() ? track : null;
        //    }
        //}
    }

    @Override
    public void onPlayEnd() {
        saveState();

        // Don't enable screensaver here or you'll broke 'screen off' logic.
        showHideScreensaver(true);
    }

    @Override
    public void onBuffering() {
        // Restore speed on LIVE end or after seek
        restoreSpeed();

        // Live stream starts to buffer after the end
        showHideScreensaver(true);
    }

    @Override
    public void onSourceChanged(Video item) {
        // At this stage video isn't loaded yet. So format switch isn't take any resources.
        restoreFormats();
    }

    @Override
    public void onViewPaused() {
        persistState();
    }

    @Override
    public void onSpeedChanged(float speed) {
        mPlayerData.setSpeed(getVideo().channelId, speed);
    }

    @Override
    public void onSpeedClicked(boolean enabled) {
        float lastSpeed = mPlayerData.getSpeed(getVideo().channelId);
        if (Helpers.floatEquals(lastSpeed, 1.0f)) {
            lastSpeed = mPlayerData.getLastSpeed();
        }
        State state = mStateService.getByVideoId(getVideo() != null ? getVideo().videoId : null);
        if (state != null && mPlayerData.isSpeedPerVideoEnabled()) {
            lastSpeed = !Helpers.floatEquals(1.0f, state.speed) ? state.speed : lastSpeed;
            mStateService.save(new State(state.video, state.positionMs, state.durationMs, enabled ? 1.0f : lastSpeed));
        }

        if (Helpers.floatEquals(lastSpeed, 1.0f) || mPlayerTweaksData.isSpeedButtonOldBehaviorEnabled()) {
            onSpeedLongClicked(enabled);
        } else {
            getPlayer().setSpeed(enabled ? 1.0f : lastSpeed);
        }
    }

    @Override
    public void onSpeedLongClicked(boolean enabled) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        // suppose live stream if buffering near the end
        // boolean isStream = Math.abs(player.getDuration() - player.getCurrentPosition()) < 10_000;
        settingsPresenter.appendCategory(AppDialogUtil.createSpeedListCategory(getContext(), getPlayer(), mPlayerData));

        //settingsPresenter.appendCategory(AppDialogUtil.createRememberSpeedCategory(getContext(), mPlayerData));
        //settingsPresenter.appendCategory(AppDialogUtil.createSpeedMiscCategory(getContext(), mPlayerTweaksData));

        settingsPresenter.showDialog(getContext().getString(R.string.video_speed), () -> {
            State state = mStateService.getByVideoId(getVideo() != null ? getVideo().videoId : null);
            if (state != null && mPlayerData.isSpeedPerVideoEnabled()) {
                mStateService.save(new State(state.video, state.positionMs, state.durationMs, mPlayerData.getSpeed(getVideo().channelId)));
            }
        });
    }

    @Override
    public void onFinish() {
        mIncognito = false;
    }

    private void clearStateOfNextVideo() {
        if (getVideo() != null && getVideo().nextMediaItem != null) {
            resetPosition(Video.from(getVideo().nextMediaItem));
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
        boolean isVideoEnded = state != null && state.durationMs - state.positionMs < 3_000;
        boolean isLive = item.isLive && !mPlayerTweaksData.isRememberPositionOfLiveVideosEnabled();

        if (isShort || isVideoEnded || isLive) {
            resetPosition(item);
        }
    }

    private void resetGlobalSpeedIfNeeded() {
        if (!mPlayerData.isAllSpeedEnabled()) {
            mPlayerData.setSpeed(1.0f);
        }
    }

    private void resetPosition(Video video) {
        if (video == null) {
            return;
        }

        video.markNotViewed();
        State state = mStateService.getByVideoId(video.videoId);

        if (state != null) {
            if (mPlayerData.isSpeedPerVideoEnabled()) {
                mStateService.save(new State(video, 0, state.durationMs, state.speed));
            } else {
                mStateService.removeByVideoId(video.videoId);
            }
        }
    }

    private void enableIncognitoIfNeeded(Video item) {
        if (item == null) {
            return;
        }

        // Enable incognito per session
        // Reset to default when player finished
        if (item.incognito) {
            mIncognito = true;
            item.incognito = false;
        }
    }

    private void persistState() {
        if (AppDialogPresenter.instance(getContext()).isDialogShown()) {
            return;
        }

        mStateService.persistState();
    }

    private void restoreVideoFormat() {
        if (mPlayerData.getTempVideoFormat() != null) {
            getPlayer().setFormat(mPlayerData.getTempVideoFormat());
        } else {
            getPlayer().setFormat(mPlayerData.getFormat(FormatItem.TYPE_VIDEO));
        }
    }

    private void restoreAudioFormat() {
        getPlayer().setFormat(mPlayerData.getFormat(FormatItem.TYPE_AUDIO));
    }

    private void restoreSubtitleFormat() {
        FormatItem result = mPlayerData.getFormat(FormatItem.TYPE_SUBTITLE);

        if (mPlayerData.isSubtitlesPerChannelEnabled()) {
            result = mPlayerData.isSubtitlesPerChannelEnabled(getPlayer().getVideo().channelId) ? mPlayerData.getLastSubtitleFormat() : FormatItem.SUBTITLE_NONE;
        }

        getPlayer().setFormat(result);
    }

    private void saveState() {
        savePosition();
        updateHistory();
        //persistState(); // persist the state if the device reboots accidentally
        syncWithPlaylists();
    }

    private void savePosition() {
        Video video = getVideo();

        if (video == null || getPlayer() == null || !getPlayer().containsMedia()) {
            return;
        }

        // Exceptional cases:
        // 1) Track is ended
        // 2) Pause on end enabled
        // 3) Watching live stream in real time
        long durationMs = getPlayer().getDurationMs();
        long positionMs = getPlayer().getPositionMs();
        long remainsMs = durationMs - positionMs;
        boolean isPositionActual = remainsMs > 1_000;
        if (isPositionActual) { // partially viewed
            State state = new State(video, positionMs, durationMs, getPlayer().getSpeed());
            mStateService.save(state);
            // Sync video. You could safely use it later to restore state.
            video.sync(state);
        } else { // fully viewed
            // Mark video as fully viewed. This could help to restore proper progress marker on the video card later.
            mStateService.save(new State(video, durationMs, durationMs, getPlayer().getSpeed()));
            video.markFullyViewed();
        }

        Playlist.instance().sync(video);
    }

    private void restorePosition() {
        Video item = getVideo();

        State state = mStateService.getByVideoId(item.videoId);

        boolean stateIsOutdated = isStateOutdated(state, item);
        if (item.getPositionMs() > 0 && stateIsOutdated) { // check that the user logged in
            // Web state is buggy on short videos (e.g. video clips)
            boolean isLongVideo = getPlayer().getDurationMs() > MUSIC_VIDEO_MAX_DURATION_MS;
            if (isLongVideo) {
                state = new State(item, item.getPositionMs());
            }
        }

        // Web live position is broken. Ignore it.
        if (stateIsOutdated && item.isLive) {
            state = null;
        }

        // Set actual position for live videos with uncommon length
        if ((state == null || state.durationMs - state.positionMs < LIVE_THRESHOLD_MS) && item.isLive) {
            // Add buffer. Should I take into account segment offset???
            long buffer = mPlayerTweaksData.isBufferOnStreamsDisabled() ? SHORT_LIVE_BUFFER_MS : LIVE_BUFFER_MS;
            state = new State(item, getPlayer().getDurationMs() - buffer);
        }

        // Do I need to check that item isn't live? (state != null && !item.isLive)
        if (state != null) {
            setPositionMs(state.positionMs);
        }

        if (!mIsPlayBlocked) {
            getPlayer().setPlayWhenReady(getPlayEnabled());
        }
    }

    private void updateHistory() {
        Video video = getVideo();

        if (video == null || (video.isShorts && mGeneralData.isHideShortsFromHistoryEnabled()) ||
                mIncognito || getPlayer() == null || !getPlayer().containsMedia() ||
                (video.isRemote && mRemoteControlData.isRemoteHistoryDisabled()) ||
                mGeneralData.getHistoryState() == GeneralData.HISTORY_DISABLED || mStateService.isHistoryBroken()) {
            return;
        }

        long positionMs = video.isLive ? 0 : getPlayer().getPositionMs();

        MediaServiceManager.instance().updateHistory(video, positionMs);
    }

    /**
     * Restore position from description time code
     */
    private void restorePendingPosition() {
        Video item = getVideo();

        if (item.pendingPosMs > 0) {
            getPlayer().setPositionMs(item.pendingPosMs);
            item.pendingPosMs = 0;
        }
    }

    private void restoreSpeed() {
        Video item = getVideo();

        if (isLiveThreshold() || isMusicVideo()) {
            getPlayer().setSpeed(1.0f);
        } else {
            State state = mStateService.getByVideoId(item.videoId);
            float speed = mPlayerData.getSpeed(item.channelId);
            getPlayer().setSpeed(
                    state != null && mPlayerData.isSpeedPerVideoEnabled() ? state.speed :
                            mPlayerData.isAllSpeedEnabled() || item.channelId != null ? speed : 1.0f
            );
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
        float newVolume = mPlayerData.getPlayerVolume();

        if (mPlayerTweaksData.isPlayerAutoVolumeEnabled()) {
            newVolume *= getVideo().volume;
        }

        //if (getVideo().isShorts || getVideo().getDurationMs() <= 60_000) {
        //    newVolume /= 2;
        //}

        if (getVideo().isShorts) {
            newVolume /= 2;
        }

        getPlayer().setVolume(newVolume);
    }

    private void restorePitch() {
        getPlayer().setPitch(mPlayerData.getPitch());
    }

    private void restoreFormats() {
        restoreVideoFormat();
        restoreAudioFormat();
        // We don't know yet do we really need a subs.
        // NOTE: Some subs can hang the app.
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
        if (getPlayer() == null) {
            return false;
        }

        Video item = getVideo();
        boolean isLiveThreshold = getPlayer().getDurationMs() - getPlayer().getPositionMs() < LIVE_THRESHOLD_MS;
        return item.isLive && isLiveThreshold;
    }

    private boolean isMusicVideo() {
        Video item = getVideo();
        return item.belongsToMusic();
    }

    private void setPositionMs(long positionMs) {
        boolean samePositions = Math.abs(positionMs - getPlayer().getPositionMs()) < BEGIN_THRESHOLD_MS;
        if (!samePositions) {
            getPlayer().setPositionMs(positionMs);
        }
    }

    private boolean isStateOutdated(State state, Video item) {
        if (state == null) {
            return true;
        }

        float posPercents1 = state.positionMs * 100f / state.durationMs;
        float posPercents2 = item.getPositionMs() * 100f / item.getDurationMs();

        return (posPercents2 != 0 && Math.abs(posPercents1 - posPercents2) > 3) && state.timestamp < item.timestamp;
    }

    private void syncWithPlaylists() {
        Video video = getVideo();

        if (video == null) {
            return;
        }

        if (mGeneralData.isHideWatchedFromWatchLaterEnabled() && video.percentWatched > 95) { // remove fully watched
            AppDialogUtil.removeFromWatchLaterPlaylist(getContext(), video);
        }

        if (mGeneralData.isHideWatchedFromNotificationsEnabled()) { // remove any watched length
            MediaServiceManager.instance().hideNotification(video);
        }
    }
}
