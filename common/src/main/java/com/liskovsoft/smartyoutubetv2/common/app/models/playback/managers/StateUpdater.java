package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import androidx.annotation.NonNull;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.Map;

public class StateUpdater extends PlayerEventListenerHelper {
    private static final String TAG = StateUpdater.class.getSimpleName();
    private static final long MUSIC_VIDEO_LENGTH_MS = 6 * 60 * 1000;
    private static final int MAX_PERSISTENT_STATE_SIZE = 30;
    private boolean mIsPlayEnabled;
    private FormatItem mTempVideoFormat;
    // Don't store state inside Video object.
    // As one video might correspond to multiple Video objects.
    private final Map<String, State> mStates = Utils.createLRUMap(MAX_PERSISTENT_STATE_SIZE);
    private AppPrefs mPrefs;
    private Disposable mHistoryAction;
    private PlayerData mPlayerData;
    private boolean mIsPlayBlocked;

    @Override
    public void onInitDone() {
        mPrefs = AppPrefs.instance(getActivity());
        mPlayerData = PlayerData.instance(getActivity());

        restoreState();
    }

    /**
     * Fired after user clicked on video in browse activity<br/>
     * or video is opened from the intent
     */
    @Override
    public void openVideo(Video item) {
        setPlayEnabled(true); // video just added
        
        mTempVideoFormat = null;

        resetStateIfNeeded(item); // reset position of music videos
        resetSpeedIfNeeded();

        // Ensure that we aren't running on presenter init stage
        if (getController() != null) {
            // Save state of the previous video.
            // In case video opened from phone and other stuff.
            saveState();

            // Restore format according to profile on every new video
            restoreVideoFormat();
        }
    }

    @Override
    public boolean onPreviousClicked() {
        // Skip auto seek logic when running remote session
        if (getController().getVideo() != null && getController().getVideo().isRemote) {
            return false;
        }

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
        // Fragment might be destroyed by system at this point.
        // So, to be sure, repeat format selection.
        restoreVideoFormat();
        restoreAudioFormat();
        restoreSubtitleFormat();

        // Show user info instead of black screen.
        if (!getPlayEnabled()) {
            getController().showControls(true);
        }
    }

    @Override
    public void onEngineReleased() {
        // Save previous state
        if (getController().getLengthMs() > 0) { // contains any media
            setPlayEnabled(getController().getPlay());
        }

        saveState();
    }

    @Override
    public void onVideoLoaded(Video item) {
        // In this state video length is not undefined.
        restorePosition(item);
        restoreSpeed(item);
        // Player thinks that subs not enabled if I enable it too early (e.g. on source change event).
        restoreSubtitleFormat();
    }

    @Override
    public void onPlay() {
        setPlayEnabled(true);
        Helpers.disableScreensaver(getActivity());
    }

    @Override
    public void onPause() {
        setPlayEnabled(false);
        Helpers.enableScreensaver(getActivity());
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

        // Take into account different playback states
        Helpers.enableScreensaver(getActivity());
    }

    private void clearStateOfNextVideo() {
        if (getController().getVideo() != null && getController().getVideo().nextMediaItem != null) {
            mStates.remove(getController().getVideo().nextMediaItem.getVideoId());
        }
    }
    
    private void resetStateIfNeeded(Video item) {
        State state = mStates.get(item.videoId);

        // Reset position of music videos
        if (state != null && state.lengthMs < MUSIC_VIDEO_LENGTH_MS) {
            mStates.remove(item.videoId);
        }
    }

    private void restoreState() {
        restoreClipData();
    }

    private void persistState() {
        updateHistory();
        persistVideoState();
    }

    private void persistVideoState() {
        if (getController().getLengthMs() <= MUSIC_VIDEO_LENGTH_MS) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        for (State state : mStates.values()) {
            if (state.lengthMs <= MUSIC_VIDEO_LENGTH_MS) {
                continue;
            }

            if (sb.length() != 0) {
                sb.append("|");
            }

            sb.append(state);
        }

        mPrefs.setStateUpdaterData(sb.toString());
    }

    private void restoreClipData() {
        String data = mPrefs.getStateUpdaterData();

        if (data != null) {
            String[] split = data.split("\\|");

            for (String spec : split) {
                State state = State.from(spec);

                if (state != null) {
                    mStates.put(state.videoId, state);
                }
            }
        }
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
        Video video = getController().getVideo();

        if (video != null) {
            // Don't save position if track is ended.
            // Skip if paused.
            boolean isVideoEnded = Math.abs(getController().getLengthMs() - getController().getPositionMs()) < 1_000;
            if (!isVideoEnded || !getPlayEnabled()) {
                mStates.put(video.videoId, new State(video.videoId, getController().getPositionMs(), getController().getLengthMs(), getController().getSpeed()));
            } else {
                // Add null state to prevent restore position from history
                mStates.remove(video.videoId);
                video.percentWatched = 0;
            }

            persistState();
        }
    }

    private void restorePosition(Video item) {
        State state = mStates.get(item.videoId);

        // internal storage has priority over item data loaded from network
        if (state == null) {
            boolean containsWebPosition = item.percentWatched > 0 && item.percentWatched < 100;
            if (containsWebPosition) {
                // Web state is buggy on short videos (e.g. video clips)
                boolean isLongVideo = getController().getLengthMs() > MUSIC_VIDEO_LENGTH_MS;
                if (isLongVideo) {
                    state = new State(item.videoId, getNewPosition(item.percentWatched));
                }
            }
        }

        if (state != null && !item.isLive) {
            long remainsMs = getController().getLengthMs() - state.positionMs;
            boolean isVideoEnded = remainsMs < 1_000;
            if (!isVideoEnded || !getPlayEnabled()) {
                getController().setPositionMs(state.positionMs);
            }
        }

        if (!mIsPlayBlocked) {
            getController().setPlay(getPlayEnabled());
        }
    }

    private void restoreSpeed(Video item) {
        boolean isLive = getController().getLengthMs() - getController().getPositionMs() < 30_000;

        if (isLive) {
            getController().setSpeed(1.0f);
        } else {
            getController().setSpeed(mPlayerData.getSpeed());
        }
    }

    private void resetSpeedIfNeeded() {
        if (mPlayerData != null && !mPlayerData.isRememberSpeedEnabled()) {
            mPlayerData.setSpeed(1.0f);
        }
    }

    private long getNewPosition(int percentWatched) {
        long newPositionMs = getController().getLengthMs() / 100 * percentWatched;

        boolean samePositions = Math.abs(newPositionMs - getController().getPositionMs()) < 10_000;

        if (samePositions) {
            newPositionMs = -1;
        }

        return newPositionMs;
    }

    public void blockPlay(boolean block) {
        mIsPlayBlocked = block;
    }

    private void setPlayEnabled(boolean isPlayEnabled) {
        Log.d(TAG, "setPlayEnabled %s", isPlayEnabled);
        mIsPlayEnabled = isPlayEnabled;
    }

    private boolean getPlayEnabled() {
        return mIsPlayEnabled;
    }

    private void updateHistory() {
        RxUtils.disposeActions(mHistoryAction);

        Video item = getController().getVideo();
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> historyObservable;

        long positionSec = getController().getPositionMs() / 1_000;

        if (item.mediaItem != null) {
            historyObservable = mediaItemManager.updateHistoryPositionObserve(item.mediaItem, positionSec);
        } else { // video launched form ATV channels
            historyObservable = mediaItemManager.updateHistoryPositionObserve(item.videoId, positionSec);
        }

        mHistoryAction = RxUtils.execute(historyObservable);
    }

    private static class State {
        public final String videoId;
        public final long positionMs;
        private final long lengthMs;
        public final float speed;

        public State(String videoId, long positionMs) {
            this(videoId, positionMs, -1);
        }

        public State(String videoId, long positionMs, long lengthMs) {
            this(videoId, positionMs, lengthMs, 1.0f);
        }

        public State(String videoId, long positionMs, long lengthMs, float speed) {
            this.videoId = videoId;
            this.positionMs = positionMs;
            this.lengthMs = lengthMs;
            this.speed = speed;
        }

        public static State from(String spec) {
            if (spec == null) {
                return null;
            }

            String[] split = spec.split(",");

            if (split.length != 4) {
                return null;
            }

            String videoId = split[0];
            long positionMs = Helpers.parseLong(split[1]);
            long lengthMs = Helpers.parseLong(split[2]);
            float speed = Helpers.parseFloat(split[3]);

            return new State(videoId, positionMs, lengthMs, speed);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("%s,%s,%s,%s", videoId, positionMs, lengthMs, speed);
        }
    }
}
