package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;

import androidx.annotation.NonNull;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.Map;

public class StateUpdater extends PlayerEventListenerHelper {
    private static final String TAG = StateUpdater.class.getSimpleName();
    private static final long MUSIC_VIDEO_LENGTH_MS = 6 * 60 * 1000;
    private static final int MAX_PERSISTENT_STATE_SIZE = 30;
    private boolean mIsPlaying;
    private FormatItem mVideoFormat;
    private FormatItem mAudioFormat;
    private FormatItem mSubtitleFormat;
    // Don't store state inside Video object.
    // As one video might correspond to multiple Video objects.
    private final Map<String, State> mStates = Utils.createLRUMap(MAX_PERSISTENT_STATE_SIZE);
    private float mLastSpeed = -1;
    private AppPrefs mPrefs;
    private Disposable mHistoryAction;

    public StateUpdater() {
    }

    @Override
    public void onInitDone() {
        mPrefs = AppPrefs.instance(getActivity());
        mVideoFormat = mPrefs.getFormat(FormatItem.TYPE_VIDEO, FormatItem.VIDEO_HD_AVC_30);
        mAudioFormat = mPrefs.getFormat(FormatItem.TYPE_AUDIO, FormatItem.AUDIO_HQ_MP4A);
        mSubtitleFormat = mPrefs.getFormat(FormatItem.TYPE_SUBTITLE, null);

        restoreState();
    }

    /**
     * Fired after user clicked on video in browse activity<br/>
     * or video is opened from the intent
     */
    @Override
    public void openVideo(Video item) {
        //mLastSpeed = -1; // Save global speed on per-view basis

        ensureVideoSize(item); // reset position of music videos

        mIsPlaying = true; // video just added

        // Ensure that we aren't running on presenter init stage
        if (getController() != null && getController().isEngineBlocked()) {
            // In background mode some event not called.
            // So, for proper state persistence, we need to save state here.
            saveState();
        }
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
        saveState();

        clearStateOfNextVideo();

        return false;
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        saveState();
        mIsPlaying = true; // autoplay video from suggestions
    }

    //@Override
    //public void onEngineInitialized() {
    //    // This is a backup place for format restoration.
    //    // Usually you don't need to use it.
    //    // There is rare bug when format didn't restore at all.
    //    restoreVideoFormatSilent();
    //}

    @Override
    public void onEngineInitialized() {
        // Fragment might be destroyed by system at this point.
        // So, to be sure, repeat format selection.
        restoreVideoFormat();
        restoreAudioFormat();
    }

    @Override
    public void onEngineReleased() {
        saveState();
    }

    //@Override
    //public void onSourceChanged(Video item) {
    //    // called before engine attempt to auto select track by itself
    //    restoreVideoFormat();
    //    restoreAudioFormat();
    //}

    @Override
    public void onVideoLoaded(Video item) {
        // In this state video length is not undefined.
        restorePosition(item);
        restoreSpeed(item);
        // Player thinks that subs not enabled if did it too early (e.g. on source change event).
        restoreSubtitleFormat();
    }

    @Override
    public void onPlay() {
        mIsPlaying = true;
        Helpers.disableScreensaver(getActivity());
    }

    @Override
    public void onPause() {
        mIsPlaying = false;
        Helpers.enableScreensaver(getActivity());
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO && !getController().isInPIPMode()) {
            mVideoFormat = track;
        } else if (track.getType() == FormatItem.TYPE_AUDIO) {
            mAudioFormat = track;
        } else if (track.getType() == FormatItem.TYPE_SUBTITLE) {
            mSubtitleFormat = track;
        }

        if (!getController().isInPIPMode()) {
            mPrefs.setFormat(track);
        }
    }

    @Override
    public void onPlayEnd() {
        Video video = getController().getVideo();

        // In case we start to watch the video again
        if (video != null) {
            // Add null state to prevent restore position from history
            mStates.put(video.videoId, new State(video.videoId, 0));
            saveState();
        }

        // Take into account different playback states
        Helpers.enableScreensaver(getActivity());
    }

    private void clearStateOfNextVideo() {
        if (getController().getVideo() != null && getController().getVideo().nextMediaItem != null) {
            mStates.remove(getController().getVideo().nextMediaItem.getVideoId());
        }
    }

    private void ensureVideoSize(Video item) {
        State state = mStates.get(item.videoId);

        // Trying to start music video from beginning
        if (state != null && state.lengthMs < MUSIC_VIDEO_LENGTH_MS) {
            mStates.remove(item.videoId);
        }
    }

    private void saveState() {
        Video video = getController().getVideo();

        if (video != null) {
            if (getController().getLengthMs() - getController().getPositionMs() > 500) { // don't save position if track is ended
                mStates.put(video.videoId, new State(video.videoId, getController().getPositionMs(), getController().getLengthMs(), getController().getSpeed()));
            }

            persistState();
        }

        mLastSpeed = getController().getSpeed();
    }

    private void restoreState() {
        restoreClipData();
        restoreParams();
    }

    private void persistState() {
        updateHistory();
        persistVideoState();
        persistParams();
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

        mPrefs.setStateUpdaterClipData(sb.toString());
    }

    private void restoreClipData() {
        String data = mPrefs.getStateUpdaterItemsData();

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

    private void persistParams() {
        mPrefs.setStateUpdaterParams(String.format("%s", mLastSpeed));
    }

    private void restoreParams() {
        String params = mPrefs.getStateUpdaterParams();

        if (params != null) {
            mLastSpeed = Helpers.parseFloat(params);
        }
    }

    ///**
    // * Mirrors {@link #restoreVideoFormat()} to be sure that selection perfroms in any case
    // */
    //private void restoreVideoFormatSilent() {
    //    if (getController().isInPIPMode()) {
    //        getController().selectFormatSilent(FormatItem.VIDEO_SD_AVC_30);
    //    } else if (mVideoFormat != null) {
    //        getController().selectFormatSilent(mVideoFormat);
    //    }
    //}

    private void restoreVideoFormat() {
        if (getController().isInPIPMode()) {
            getController().selectFormat(FormatItem.VIDEO_SD_AVC_30);
        } else if (mVideoFormat != null) {
            getController().selectFormat(mVideoFormat);
        }
    }

    private void restoreAudioFormat() {
        if (mAudioFormat != null) {
            getController().selectFormat(mAudioFormat);
        }
    }

    private void restoreSubtitleFormat() {
        if (mSubtitleFormat != null) {
            getController().selectFormat(mSubtitleFormat);
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

        if (state != null) {
            boolean isVideoEnded = Math.abs(getController().getLengthMs() - state.positionMs) < 1_000;
            if (!isVideoEnded) {
                getController().setPositionMs(state.positionMs);
            }
        }

        getController().setPlay(mIsPlaying);
    }

    private void restoreSpeed(Video item) {
        boolean isLive = getController().getLengthMs() - getController().getPositionMs() < 30_000;

        if (!isLive) {
            if (mLastSpeed != -1) {
                getController().setSpeed(mLastSpeed);
            } else {
                getController().setSpeed(1.0f); // speed may be changed before, so do reset to default
            }
        } else {
            getController().setSpeed(1.0f); // speed may be changed before, so do reset to default
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

    public FormatItem getVideoPreset() {
        return mVideoFormat;
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

        mHistoryAction = historyObservable
                .subscribeOn(Schedulers.newThread())
                .subscribe((Void v) -> {}, error -> Log.e(TAG, "History update error: " + error));
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
            long positionMs = Helpers.parseInt(split[1]);
            long lengthMs = Helpers.parseInt(split[2]);
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
