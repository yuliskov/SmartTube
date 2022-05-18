package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import android.content.Context;
import android.os.Build.VERSION;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.TrackErrorFixer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackInfoFormatter2;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.VideoTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import java.io.InputStream;
import java.util.List;

public class ExoPlayerController implements Player.EventListener, PlayerController {
    private static final String TAG = ExoPlayerController.class.getSimpleName();
    private final Context mContext;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private final TrackSelectorManager mTrackSelectorManager;
    private final TrackInfoFormatter2 mTrackFormatter;
    private boolean mOnSourceChanged;
    private Video mVideo;
    private PlayerEventListener mEventListener;
    private SimpleExoPlayer mPlayer;
    private PlayerView mPlayerView;
    private float mCurrentSpeed = 1.0f;

    public ExoPlayerController(Context context) {
        mContext = context.getApplicationContext();
        mMediaSourceFactory = ExoMediaSourceFactory.instance(context);
        mTrackSelectorManager = new TrackSelectorManager();
        mTrackFormatter = new TrackInfoFormatter2();

        mMediaSourceFactory.setTrackErrorFixer(new TrackErrorFixer(mTrackSelectorManager));

        // Shield 720p fix???
        initFormats();
        VideoTrack.sIsNoFpsPresetsEnabled = PlayerTweaksData.instance(context).isNoFpsPresetsEnabled();
    }

    private void initFormats() {
        PlayerData playerData = PlayerData.instance(mContext);
        mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_VIDEO)));
        mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_AUDIO)));
        mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_SUBTITLE)));
    }

    @Override
    public void openDash(InputStream dashManifest) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        openMediaSource(mediaSource);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifestUrl(dashManifestUrl);
        openMediaSource(mediaSource);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl);
        openMediaSource(mediaSource);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        MediaSource mediaSource = mMediaSourceFactory.fromUrlList(urlList);
        openMediaSource(mediaSource);
    }

    private void openMediaSource(MediaSource mediaSource) {
        setQualityInfo("");

        mTrackSelectorManager.invalidate();
        mOnSourceChanged = true;
        mEventListener.onSourceChanged(mVideo);
        mPlayer.prepare(mediaSource);
    }

    @Override
    public long getPositionMs() {
        if (mPlayer == null) {
            return -1;
        }

        return mPlayer.getCurrentPosition();
    }

    /**
     * NOTE: Pos gathered from content block data may slightly exceed video duration
     * (e.g. 302200 when duration is 302000).
     */
    @Override
    public void setPositionMs(long positionMs) {
        // Url list videos at load stage has undefined (-1) length. So, we need to remove length check.
        if (mPlayer != null && positionMs >= 0) {
            mPlayer.seekTo(positionMs);
        }
    }

    @Override
    public long getLengthMs() {
        if (mPlayer == null) {
            return -1;
        }

        long duration = mPlayer.getDuration();
        return duration != C.TIME_UNSET ? duration : -1;
    }

    @Override
    public void setPlay(boolean isPlaying) {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(isPlaying);
        }
    }

    @Override
    public boolean getPlay() {
        if (mPlayer == null) {
            return false;
        }

        return mPlayer.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return ExoUtils.isPlaying(mPlayer);
    }

    @Override
    public boolean isLoading() {
        return ExoUtils.isLoading(mPlayer);
    }

    @Override
    public boolean containsMedia() {
        if (mPlayer == null) {
            return false;
        }

        return mPlayer.getPlaybackState() != Player.STATE_IDLE;
    }

    @Override
    public void release() {
        mTrackSelectorManager.release();

        if (mPlayer != null) {
            mPlayer.removeListener(this);
            //mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }

        mPlayerView = null;
        // Don't destroy it (needed inside bridge)!
        //mEventListener = null;
    }

    @Override
    public void setPlayer(SimpleExoPlayer player) {
        mPlayer = player;
        player.addListener(this);
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
    public void setTrackSelector(DefaultTrackSelector trackSelector) {
        mTrackSelectorManager.setTrackSelector(trackSelector);

        if (mContext != null && trackSelector != null && PlayerTweaksData.instance(mContext).isTunneledPlaybackEnabled()) {
            // Enable tunneling if supported by the current media and device configuration.
            if (VERSION.SDK_INT >= 21) {
                trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingAudioSessionId(C.generateAudioSessionIdV21(mContext)));
            }
        }
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
    public void selectFormat(FormatItem formatItem) {
        if (formatItem != null) {
            mEventListener.onTrackSelected(formatItem);
            mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(formatItem));
        }
    }

    @Override
    public FormatItem getVideoFormat() {
        // Precise format (may not be loaded yet)
        //if (mPlayer instanceof SimpleExoPlayer) {
        //    Format videoFormat = ((SimpleExoPlayer) mPlayer).getVideoFormat();
        //
        //    if (videoFormat != null) {
        //        return ExoFormatItem.from(videoFormat);
        //    }
        //}

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
                // EXO: 2.12.1
                //Format format = selection.getSelectedFormat();

                // EXO: 2.13.1
                Format format = selection.getFormat(0);

                mEventListener.onTrackChanged(ExoFormatItem.from(format));

                if (ExoFormatItem.isVideo(format)) {
                    mTrackFormatter.setFormat(format);
                    setQualityInfo(mTrackFormatter.getQualityLabel());
                }
            }
        }
    }

    private void notifyOnVideoLoad() {
        if (mVideo == null) {
            return;
        }

        if (mOnSourceChanged) {
            mOnSourceChanged = false;
            mEventListener.onVideoLoaded(mVideo);

            // Produce thread sync problems
            // Attempt to read from field 'java.util.TreeMap$Node java.util.TreeMap$Node.left' on a null object reference
            //mTrackSelectorManager.fixTracksSelection();
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, "onPlayerError: " + error);

        // Player is released at this point. So, there is no sense to restore the playback here.

        //if (error.type == ExoPlaybackException.TYPE_UNEXPECTED &&
        //    error.getCause() instanceof IllegalArgumentException) {
        //    // Maybe it's because of auto frame rate.
        //    // Such error may occur when pausing activity.
        //    return;
        //}

        mEventListener.onEngineError(error.type);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onPlayerStateChanged: " + TrackSelectorUtil.stateToString(playbackState));
        }

        //if (Player.STATE_READY == playbackState) {
        //    notifyOnVideoLoad();
        //}

        boolean isPlayPressed = Player.STATE_READY == playbackState && playWhenReady;
        boolean isPausePressed = Player.STATE_READY == playbackState && !playWhenReady;
        boolean isPlaybackEnded = Player.STATE_ENDED == playbackState && playWhenReady;
        boolean isBuffering = Player.STATE_BUFFERING == playbackState && playWhenReady;

        if (isPlayPressed) {
            mEventListener.onPlay();
        } else if (isPausePressed) {
            mEventListener.onPause();
        } else if (isPlaybackEnded) {
            mEventListener.onPlayEnd();
        } else if (isBuffering) {
            mEventListener.onBuffering();
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.e(TAG, "onPositionDiscontinuity");

        // Fix video loop on 480p with legacy codes enabled
        if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
            mPlayer.stop();
            mEventListener.onPlayEnd();
        }
    }

    @Override
    public void onSeekProcessed() {
        mEventListener.onSeekEnd();
    }

    @Override
    public void setSpeed(float speed) {
        if (mPlayer != null && speed > 0) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(speed, mPlayer.getPlaybackParameters().pitch));
            mCurrentSpeed = speed; // NOTE: backup speed in case params not applied (playback is paused)

            mTrackFormatter.setSpeed(speed);
            setQualityInfo(mTrackFormatter.getQualityLabel());
        }
    }

    @Override
    public float getSpeed() {
        if (mPlayer != null) {
            // NOTE: restore backup speed in case params not applied (playback is paused)
            return isPlaying() ? mPlayer.getPlaybackParameters().speed : mCurrentSpeed;
        } else {
            return -1;
        }
    }

    @Override
    public void setVolume(float volume) {
        if (mPlayer != null && volume >= 0) {
            mPlayer.setVolume(volume);
        }
    }

    @Override
    public float getVolume() {
        if (mPlayer != null) {
            return mPlayer.getVolume();
        } else {
            return 1;
        }
    }

    private void setQualityInfo(String qualityInfoStr) {
        if (mPlayerView != null && qualityInfoStr != null) {
            mPlayerView.setQualityInfo(qualityInfoStr);
        }
    }
}
