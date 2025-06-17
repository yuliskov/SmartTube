package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

public class VideoStateController extends BasePlayerController {
    private static final String TAG = VideoStateController.class.getSimpleName();
    private static final long MUSIC_VIDEO_MAX_DURATION_MS = 6 * 60 * 1000;
    private static final long RESTORE_LIVE_BUFFER_MS = 60_000;
    private static final long DEFAULT_LIVE_BUFFER_MS = 60_000; // Minimum issues
    private static final long OFFICIAL_LIVE_BUFFER_MS = 15_000; // Official app buffer
    private static final long LIVE_BUFFER_MS = OFFICIAL_LIVE_BUFFER_MS;
    private static final long SHORT_LIVE_BUFFER_MS = 0; // Note, on buffer lower than the 60sec you'll notice segment skip
    private static final long BEGIN_THRESHOLD_MS = 10_000;
    private static final long EMBED_THRESHOLD_MS = 30_000;
    private static final int HISTORY_UPDATE_INTERVAL_MINUTES = 5; // Sync history every five minutes
    private boolean mIsPlayEnabled;
    private boolean mIsPlayBlocked;
    private int mTickleLeft;
    private boolean mIncognito;
    private final Runnable mUpdateHistory = () -> { saveState(); persistState(); };
    private long mNewVideoTimeMs;

    /**
     * Fired after user clicked on video in browse activity<br/>
     * or video is opened from the intent
     */
    @Override
    public void onNewVideo(Video item) {
        // Ensure that we aren't running on presenter init stage
        if (getPlayer() != null && getPlayer().containsMedia()) {
            if (!item.equals(getVideo())) { // a video might be opened twice (when remote connection enabled). Fix for that.
                // Reset auto-save history timer
                mTickleLeft = 0;
                // Save state of the previous video.
                // In case video opened from phone and other stuff.
                removeFromHistoryIfNeeded();
                saveState();
            }
        }

        if (!item.equals(getVideo())) {
            mNewVideoTimeMs = System.currentTimeMillis();
        }

        setPlayEnabled(true); // video just added

        getPlayerData().setTempVideoFormat(null);

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
        if (getVideo() != null && getVideo().isLive && (getPlayer().getDurationMs() - getPlayer().getPositionMs() > getLiveThreshold())) {
            getPlayer().setPositionMs(getPlayer().getDurationMs() - getLiveBuffer());
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
        if (getPlayer() == null) {
            return;
        }

        // Save previous state
        if (getPlayer().containsMedia()) {
            setPlayEnabled(getPlayer().getPlayWhenReady());
            saveState();
            persistState();
        }
    }

    @Override
    public void onTickle() {
        if (getPlayer() == null || !getPlayer().isEngineInitialized()) {
            return;
        }

        if (++mTickleLeft > HISTORY_UPDATE_INTERVAL_MINUTES && getPlayer().isPlaying()) {
            mTickleLeft = 0;
            saveState();
            persistState(); // ???
        }
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        // NOTE: needed for the restore after oom crash?
        //saveState(); // start watching?
        //persistState(); // restore on crash???

        // Channel info should be loaded at this point
        restoreSubtitleFormat();

        // Need to contain channel id
        restoreSpeedAndPositionIfNeeded();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        if (getPlayer() == null) {
            return;
        }

        // Oops. Error happens while playing (network lost etc).
        if (getPlayer().getPositionMs() > 1_000) {
            saveState();
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        // Actual video that match currently loaded one.
        //mVideo = item;

        // Restore formats again.
        // Maybe this could help with Shield format problem.
        // NOTE: produce multi thread exception:
        // Attempt to read from field 'java.util.TreeMap$TreeMapEntry java.util.TreeMap$TreeMapEntry.left' on a null object reference (TrackSelectorManager.java:181)
        //restoreFormats();

        // In this state video length is not undefined.
        restoreState();
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
        //        if (getPlayerData().getFormat(FormatItem.TYPE_VIDEO).isPreset()) {
        //            mTempVideoFormat = track;
        //        } else {
        //            mTempVideoFormat = null;
        //            getPlayerData().setFormat(track);
        //        }
        //    } else {
        //        getPlayerData().setFormat(track);
        //    }
        //}

        //if (!getPlayer().isInPIPMode()) {
        //    if (track.getType() == FormatItem.TYPE_VIDEO) {
        //        mTempVideoFormat = getPlayerData().getFormat(FormatItem.TYPE_VIDEO).isPreset() ? track : null;
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
        restoreSpeedAndPositionIfNeeded();

        // Live stream starts to buffer after the end
        showHideScreensaver(true);
    }

    @Override
    public void onSourceChanged(Video item) {
        // At this stage video isn't loaded yet. So format switch isn't take any resources.
        restoreFormats();
    }

    //@Override
    //public void onViewPaused() {
    //    if (!AppDialogPresenter.instance(getContext()).isDialogShown()) {
    //        persistState();
    //    }
    //}

    @Override
    public void onSpeedChanged(float speed) {
        getPlayerData().setSpeed(getVideo().channelId, speed);
    }

    @Override
    public void onSpeedClicked(boolean enabled) {
        if (getVideo() == null) {
            return;
        }

        float lastSpeed = getPlayerData().getSpeed(getVideo().channelId);
        if (Helpers.floatEquals(lastSpeed, 1.0f)) {
            lastSpeed = getPlayerData().getLastSpeed();
        }
        State state = getStateService().getByVideoId(getVideo() != null ? getVideo().videoId : null);
        if (state != null && getPlayerData().isSpeedPerVideoEnabled()) {
            lastSpeed = !Helpers.floatEquals(1.0f, state.speed) ? state.speed : lastSpeed;
            getStateService().save(new State(state.video, state.positionMs, state.durationMs, enabled ? 1.0f : lastSpeed));
        }

        if (Helpers.floatEquals(lastSpeed, 1.0f) || getPlayerTweaksData().isSpeedButtonOldBehaviorEnabled()) {
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
        settingsPresenter.appendCategory(AppDialogUtil.createSpeedListCategory(getContext(), getPlayer()));

        //settingsPresenter.appendCategory(AppDialogUtil.createRememberSpeedCategory(getContext(), getPlayerData()));
        //settingsPresenter.appendCategory(AppDialogUtil.createSpeedMiscCategory(getContext(), getPlayerTweaksData()));

        settingsPresenter.showDialog(getContext().getString(R.string.video_speed), () -> {
            State state = getStateService().getByVideoId(getVideo() != null ? getVideo().videoId : null);
            if (state != null && getPlayerData().isSpeedPerVideoEnabled()) {
                getStateService().save(new State(state.video, state.positionMs, state.durationMs, getPlayerData().getSpeed(getVideo().channelId)));
            }
        });
    }

    @Override
    public void onFinish() {
        mIncognito = false;
        removeFromHistoryIfNeeded();
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
        if (getStateService() == null || item == null) {
            return;
        }

        State state = getStateService().getByVideoId(item.videoId);

        // Reset position of music videos
        boolean isShort = state != null && state.durationMs < MUSIC_VIDEO_MAX_DURATION_MS
                && !getPlayerTweaksData().isRememberPositionOfShortVideosEnabled();
        boolean isVideoEnded = state != null && state.durationMs - state.positionMs < 3_000;
        boolean isLive = item.isLive;

        if (getPlayerTweaksData().isRememberPositionOfLiveVideosEnabled() && item.isFullLive()) {
            isLive = false;
        }

        // Don't reset if doing switch from the embed to the fullscreen one
        boolean sameVideo = item.equals(getVideo());
        if (sameVideo) {
            isShort = false;
        }

        if (isShort || isVideoEnded || isLive) {
            resetPosition(item);
        }
    }

    private void resetGlobalSpeedIfNeeded() {
        if (!getPlayerData().isAllSpeedEnabled()) {
            getPlayerData().setSpeed(1.0f);
        }
    }

    private void resetPosition(Video video) {
        if (video == null) {
            return;
        }

        video.markNotViewed();
        State state = getStateService().getByVideoId(video.videoId);

        if (state != null) {
            if (getPlayerData().isSpeedPerVideoEnabled()) {
                getStateService().save(new State(video, 0, state.durationMs, state.speed));
            } else {
                getStateService().removeByVideoId(video.videoId);
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

    private void restoreVideoFormat() {
        if (getPlayerData().getTempVideoFormat() != null) {
            getPlayer().setFormat(getPlayerData().getTempVideoFormat());
        } else {
            getPlayer().setFormat(getPlayerData().getFormat(FormatItem.TYPE_VIDEO));
        }
    }

    private void restoreAudioFormat() {
        getPlayer().setFormat(getPlayerData().getFormat(FormatItem.TYPE_AUDIO));
    }

    private void restoreSubtitleFormat() {
        FormatItem result = getPlayerData().getFormat(FormatItem.TYPE_SUBTITLE);

        if (getPlayerData().isSubtitlesPerChannelEnabled()) {
            result = getPlayerData().isSubtitlesPerChannelEnabled(getPlayer().getVideo().channelId) ? getPlayerData().getLastSubtitleFormat() : FormatItem.SUBTITLE_NONE;
        }

        getPlayer().setFormat(result);
    }

    public void saveState() {
        // Skip mini player, but don't save for the previews (mute enabled)
        if (isMutedEmbed()) {
            return;
        }

        savePosition();

        if (!isBeginEmbed()) {
            updateHistory();
            syncWithPlaylists();
        }
    }

    private void restoreState() {
        if (getPlayer() == null) {
            return;
        }

        restorePosition();
        restorePendingPosition();
        // Player thinks that subs not enabled if I enable it too early (e.g. on source change event).
        //restoreSubtitleFormat();

        restoreVolume();
        restorePitch();
    }

    private void persistState() {
        // Skip mini player, but don't save for the previews (mute enabled)
        if (isMutedEmbed() || isBeginEmbed()) {
            return;
        }

        getStateService().persistState();
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
        boolean isLiveBroken = video.isLive && durationMs <= 30_000; // the live without a history
        if (isPositionActual && !isLiveBroken) { // partially viewed
            State state = new State(video, positionMs, durationMs, getPlayer().getSpeed());
            getStateService().save(state);
            // Sync video. You could safely use it later to restore state.
            video.sync(state);
        } else { // fully viewed
            // Mark video as fully viewed. This could help to restore proper progress marker on the video card later.
            getStateService().save(new State(video, durationMs, durationMs, getPlayer().getSpeed()));
            video.markFullyViewed();
        }

        Playlist.instance().sync(video);
    }

    private void restorePosition() {
        Video item = getVideo();

        State state = getStateService().getByVideoId(item.videoId);

        boolean stateIsOutdated = isStateOutdated(state, item);
        if (stateIsOutdated) { // check that the user logged in
            // Web state is buggy on short videos (e.g. video clips)
            boolean isLongVideo = getPlayer().getDurationMs() > MUSIC_VIDEO_MAX_DURATION_MS;
            if (isLongVideo) {
                state = new State(item, item.getPositionMs());
            }
        }

        // Set actual position for live videos with uncommon length
        if (item.isLive && (state == null || state.durationMs - state.positionMs < Math.max(RESTORE_LIVE_BUFFER_MS, getLiveThreshold()))) {
            // Add buffer. Should I take into account segment offset???
            state = new State(item, getPlayer().getDurationMs() - getLiveBuffer());
        }

        // Do I need to check that item isn't live? (state != null && !item.isLive)
        if (state != null) {
            getPlayer().setPositionMs(state.positionMs);
        }

        if (!mIsPlayBlocked) {
            getPlayer().setPlayWhenReady(getPlayEnabled());
        }
    }

    private void updateHistory() {
        Video video = getVideo();

        if (video == null || (video.isShorts && getMediaServiceData().isContentHidden(MediaServiceData.CONTENT_SHORTS_HISTORY)) ||
                mIncognito || getPlayer() == null || !getPlayer().containsMedia() ||
                (video.isRemote && getRemoteControlData().isRemoteHistoryDisabled()) ||
                getGeneralData().getHistoryState() == GeneralData.HISTORY_DISABLED || getStateService().isHistoryBroken()) {
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

    private void restoreSpeedAndPositionIfNeeded() {
        if (getPlayer() == null) {
            return;
        }

        Video item = getVideo();

        boolean liveEnd = isLiveEnd();

        // Position
        if (liveEnd) {
            getPlayer().setPositionMs(getPlayer().getDurationMs() - getLiveBuffer());
        }

        // Speed
        if (liveEnd || isMusicVideo()) {
            getPlayer().setSpeed(1.0f);
        } else {
            State state = getStateService().getByVideoId(item.videoId);
            float speed = getPlayerData().getSpeed(item.channelId);
            getPlayer().setSpeed(
                    state != null && getPlayerData().isSpeedPerVideoEnabled() ? state.speed :
                            getPlayerData().isAllSpeedEnabled() || item.channelId != null ? speed : 1.0f
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
        float newVolume = getPlayerData().getPlayerVolume();

        if (getPlayerTweaksData().isPlayerAutoVolumeEnabled()) {
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
        getPlayer().setPitch(getPlayerData().getPitch());
    }

    private void restoreFormats() {
        restoreVideoFormat();
        restoreAudioFormat();
        // We don't know yet do we really need a subs.
        // NOTE: Some subs can hang the app.
        restoreSubtitleFormat();
    }

    private void showHideScreensaver(boolean show) {
        ScreensaverManager screensaverManager = getScreensaverManager();

        if (screensaverManager == null) {
            return;
        }

        if (show) {
            screensaverManager.enableChecked();
        } else {
            screensaverManager.disableChecked();
        }
    }

    private boolean isMusicVideo() {
        Video item = getVideo();
        return item.belongsToMusic();
    }

    //private void setPositionMs(long positionMs) {
    //    boolean samePositions = Math.abs(positionMs - getPlayer().getPositionMs()) < BEGIN_THRESHOLD_MS;
    //    if (!samePositions) {
    //        getPlayer().setPositionMs(positionMs);
    //    }
    //}

    private boolean isStateOutdated(State state, Video item) {
        if (state == null) {
            return true;
        }

        // Web live position is broken. Ignore it.
        if (item.isLive || item.getPositionMs() <= 0) {
            return false;
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

        if (getMediaServiceData().isContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER) && video.percentWatched > 95) { // remove fully watched
            MediaServiceManager.instance().removeFromWatchLaterPlaylist(video);
        }

        if (getGeneralData().isHideWatchedFromNotificationsEnabled()) { // remove any watched length
            MediaServiceManager.instance().hideNotification(video);
        }
    }

    private boolean isLiveEnd() {
        if (getPlayer() == null || getVideo() == null || !getVideo().isLive) {
            return false;
        }

        return getPlayer().getDurationMs() - getPlayer().getPositionMs() <= 1_000;
    }

    private long getLiveThreshold() {
        return getLiveBuffer() + 5_000;
    }

    private long getLiveBuffer() {
        return getPlayerTweaksData().isBufferOnStreamsDisabled() ? SHORT_LIVE_BUFFER_MS : LIVE_BUFFER_MS;
    }

    private void removeFromHistoryIfNeeded() {
        // Maintain history to keep video progress
        if (getGeneralData().getHistoryState() == GeneralData.HISTORY_DISABLED && getStateService().isHistoryBroken()) {
            Video video = getVideo();
            if (video != null) {
                getStateService().removeByVideoId(video.videoId);
            }
        }
    }

    private boolean isMutedEmbed() {
        return isEmbedPlayer() && getPlayer() != null && Helpers.floatEquals(getPlayer().getVolume(), 0);
    }

    private boolean isBeginEmbed() {
        return isEmbedPlayer() && System.currentTimeMillis() - mNewVideoTimeMs <= EMBED_THRESHOLD_MS &&
                getPlayer() != null && getPlayer().getPositionMs() < getPlayer().getDurationMs();
    }
}
