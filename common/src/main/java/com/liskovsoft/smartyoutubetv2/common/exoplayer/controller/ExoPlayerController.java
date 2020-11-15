package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import android.content.Context;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackInfoFormatter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

import java.io.InputStream;
import java.util.List;

public class ExoPlayerController implements Player.EventListener, PlayerController {
    private static final String TAG = ExoPlayerController.class.getSimpleName();
    private final ExoPlayer mPlayer;
    private final Context mContext;
    private final DefaultTrackSelector mTrackSelector;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private final TrackSelectorManager mTrackSelectorManager;
    private PlayerEventListener mEventListener;
    private Video mVideo;
    private boolean mOnSourceChanged;
    private PlayerView mPlayerView;

    public ExoPlayerController(ExoPlayer player, DefaultTrackSelector trackSelector, Context context) {
        mPlayer = player;
        mContext = context;
        player.addListener(this);

        mTrackSelector = trackSelector;
        mMediaSourceFactory = ExoMediaSourceFactory.instance(context);
        mTrackSelectorManager = new TrackSelectorManager(trackSelector);

        // fallback selection (in case listener == null)
        selectFormatSilent(FormatItem.VIDEO_HD_AVC_30);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        openMediaSource(mediaSource);
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl);
        openMediaSource(mediaSource);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromUrlList(urlList);
        openMediaSource(mediaSource);
    }

    private void openMediaSource(MediaSource mediaSource) {
        mPlayer.prepare(mediaSource);

        if (mEventListener != null) {
            mTrackSelectorManager.invalidate();
            mOnSourceChanged = true;
            mEventListener.onSourceChanged(mVideo);
        } else {
            MessageHelpers.showMessage(mContext, "Oops. Event listener didn't initialized yet");
        }
    }

    @Override
    public long getPositionMs() {
        return mPlayer.getCurrentPosition();
    }

    @Override
    public void setPositionMs(long positionMs) {
        if (positionMs >= 0) {
            mPlayer.seekTo(positionMs);
        }
    }

    @Override
    public long getLengthMs() {
        return mPlayer.getDuration();
    }

    @Override
    public void setPlay(boolean isPlaying) {
        mPlayer.setPlayWhenReady(isPlaying);
    }

    @Override
    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    @Override
    public boolean hasNoMedia() {
        return mPlayer.getPlaybackState() == Player.STATE_IDLE;
    }

    @Override
    public void setEventListener(PlayerEventListener eventListener) {
        mEventListener = eventListener;
    }

    @Override
    public void setPlayerView(PlayerView playerView) {
        mPlayerView = playerView;
    }

    @Override
    public void setVideo(Video video) {
        mVideo = video;
    }

    @Override
    public Video getVideo() {
        return mVideo;
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return ExoFormatItem.from(mTrackSelectorManager.getVideoTracks());
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return ExoFormatItem.from(mTrackSelectorManager.getAudioTracks());
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return ExoFormatItem.from(mTrackSelectorManager.getSubtitleTracks());
    }

    @Override
    public void selectFormatSilent(FormatItem option) {
        mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(option));
    }

    @Override
    public void selectFormat(FormatItem option) {
        mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(option));
        // TODO: move to the {@link #onTrackChanged()} somehow
        mEventListener.onTrackSelected(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return ExoFormatItem.from(mTrackSelectorManager.getVideoTrack());
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d(TAG, "onTracksChanged: start: groups length: " + trackGroups.length);

        notifyOnVideoLoad();

        if (trackGroups.length == 0) {
            Log.i(TAG, "onTracksChanged: Hmm. Strange. Received empty groups, no selections. Why is this happens only on next/prev videos?");
        }

        for (TrackSelection selection : trackSelections.getAll()) {
            if (selection != null) {
                Format format = selection.getSelectedFormat();
                mEventListener.onTrackChanged(ExoFormatItem.from(format));

                if (mPlayerView != null && ExoFormatItem.isVideo(format)) {
                    mPlayerView.setQualityInfo(TrackInfoFormatter.formatQualityLabel(format));
                }
            }
        }
    }

    private void notifyOnVideoLoad() {
        if (mOnSourceChanged) {
            mOnSourceChanged = false;
            mEventListener.onVideoLoaded(mVideo);
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, "onPlayerError: " + error);
        mEventListener.onEngineError(error.type);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onPlayerStateChanged: " + TrackSelectorUtil.stateToString(playbackState));
        }

        boolean playPressed = Player.STATE_READY == playbackState && playWhenReady;
        boolean pausePressed = Player.STATE_READY == playbackState && !playWhenReady;
        boolean playbackEnded = Player.STATE_ENDED == playbackState && playWhenReady;

        if (playPressed) {
            mEventListener.onPlay();
        } else if (pausePressed) {
            mEventListener.onPause();
        } else if (playbackEnded) {
            mEventListener.onPlayEnd();
        }
    }

    @Override
    public void setSpeed(float speed) {
        if (mPlayer != null && speed > 0) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1.0f));
        }
    }

    @Override
    public float getSpeed() {
        if (mPlayer != null) {
            return mPlayer.getPlaybackParameters().speed;
        } else {
            return -1;
        }
    }
}
