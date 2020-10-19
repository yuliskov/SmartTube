package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StateUpdater extends PlayerEventListenerHelper {
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

    @Override
    public void onInitDone() {
        AppPrefs prefs = AppPrefs.instance(mActivity);
        mVideoFormat = prefs.getFormat(FormatItem.TYPE_VIDEO, FormatItem.VIDEO_HD_AVC);
        mAudioFormat = prefs.getFormat(FormatItem.TYPE_AUDIO, FormatItem.AUDIO_HQ_MP4A);
        mSubtitleFormat = prefs.getFormat(FormatItem.TYPE_SUBTITLE, null);

        String data = prefs.getStateUpdaterData();
        restoreState(data);
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
        if (mController != null && mController.isEngineBlocked()) {
            // In background mode some event not called.
            // So, for proper state persistence, we need to save state here.
            saveState();
        }
    }

    @Override
    public boolean onPreviousClicked() {
        boolean isFarFromStart = mController.getPositionMs() > 10_000;

        if (isFarFromStart) {
            mController.setPositionMs(0);
            return true;
        }

        saveState();

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
    }

    @Override
    public void onEngineReleased() {
        saveState();
    }

    @Override
    public void onSourceChanged(Video item) {
        // called before engine attempt to auto select track by itself
        restoreVideoFormat();
        restoreAudioFormat();
    }

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
        Helpers.disableScreensaver(mActivity);
    }

    @Override
    public void onPause() {
        mIsPlaying = false;
        Helpers.enableScreensaver(mActivity);
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO && !mController.isInPIPMode()) {
            mVideoFormat = track;
        } else if (track.getType() == FormatItem.TYPE_AUDIO) {
            mAudioFormat = track;
        } else if (track.getType() == FormatItem.TYPE_SUBTITLE) {
            mSubtitleFormat = track;
        }

        if (!mController.isInPIPMode()) {
            AppPrefs.instance(mActivity).setFormat(track);
        }
    }

    @Override
    public void onPlayEnd() {
        Video video = mController.getVideo();

        // In case we start to watch the video again
        if (video != null) {
            mStates.remove(video.videoId);
        }
    }

    private void clearStateOfNextVideo() {
        if (mController.getVideo() != null && mController.getVideo().nextMediaItem != null) {
            mStates.remove(mController.getVideo().nextMediaItem.getVideoId());
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
        Video video = mController.getVideo();

        if (video != null) {
            mStates.put(video.videoId, new State(video.videoId, mController.getPositionMs(), mController.getLengthMs(), mController.getSpeed()));

            persistState();
        }

        mLastSpeed = mController.getSpeed();
    }

    private void persistState() {
        if (mController.getLengthMs() <= MUSIC_VIDEO_LENGTH_MS) {
            return;
        }

        AppPrefs prefs = AppPrefs.instance(mActivity);
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

        prefs.setStateUpdaterData(sb.toString());
    }

    private void restoreState(String data) {
        if (data == null) {
            return;
        }

        String[] split = data.split("\\|");

        for (String spec : split) {
            State state = State.from(spec);

            if (state != null) {
                mStates.put(state.videoId, state);
            }
        }
    }

    private void restoreVideoFormat() {
        if (mController.isInPIPMode()) {
            mController.selectFormat(FormatItem.VIDEO_SD_AVC);
        } else if (mVideoFormat != null) {
            mController.selectFormat(mVideoFormat);
        }
    }

    private void restoreAudioFormat() {
        if (mAudioFormat != null) {
            mController.selectFormat(mAudioFormat);
        }
    }

    private void restoreSubtitleFormat() {
        if (mSubtitleFormat != null) {
            mController.selectFormat(mSubtitleFormat);
        }
    }

    private void restorePosition(Video item) {
        State state = mStates.get(item.videoId);

        // internal storage has priority over item data loaded from network
        if (state == null && item.percentWatched > 0 && item.percentWatched < 100) {
            state = new State(item.videoId, getNewPosition(item.percentWatched));
        }

        if (state != null) {
            boolean isVideoEnded = Math.abs(mController.getLengthMs() - state.positionMs) < 1_000;
            if (!isVideoEnded) {
                mController.setPositionMs(state.positionMs);
            }
        }

        mController.setPlay(mIsPlaying);
    }

    private void restoreSpeed(Video item) {
        boolean isLive = mController.getLengthMs() - mController.getPositionMs() < 30_000;

        if (!isLive) {
            State state = mStates.get(item.videoId);

            if (state != null) {
                mController.setSpeed(state.speed);
            } else if (mLastSpeed != -1) {
                mController.setSpeed(mLastSpeed);
            } else {
                mController.setSpeed(1.0f); // speed may be changed before, so do reset to default
            }
        } else {
            mController.setSpeed(1.0f); // speed may be changed before, so do reset to default
        }
    }

    private long getNewPosition(int percentWatched) {
        long newPositionMs = mController.getLengthMs() / 100 * percentWatched;

        boolean samePositions = Math.abs(newPositionMs - mController.getPositionMs()) < 10_000;

        if (samePositions) {
            newPositionMs = -1;
        }

        return newPositionMs;
    }

    @Override
    public void onVideoSpeedClicked() {
        List<OptionItem> items = new ArrayList<>();

        // suppose live stream if buffering near the end
        // boolean isStream = Math.abs(player.getDuration() - player.getCurrentPosition()) < 10_000;
        intSpeedItems(items, new float[]{0.25f, 0.5f, 0.75f, 1.0f, 1.1f, 1.15f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3.0f});

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mActivity);
        settingsPresenter.clear();
        settingsPresenter.appendRadioCategory(mActivity.getString(R.string.video_speed), items);
        settingsPresenter.showDialog();
    }

    private void intSpeedItems(List<OptionItem> items, float[] speedValues) {
        for (float speed : speedValues) {
            items.add(UiOptionItem.from(
                    String.valueOf(speed),
                    optionItem -> mController.setSpeed(speed),
                    mController.getSpeed() == speed));
        }
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
