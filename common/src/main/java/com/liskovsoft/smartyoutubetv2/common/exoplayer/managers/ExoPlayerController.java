package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers;

import android.content.Context;
import android.net.Uri;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;

import java.io.InputStream;

public class ExoPlayerController implements EventListener {
    private final ExoPlayer mPlayer;
    private final TrackSelector mTrackSelector;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private PlayerEventListener mEventListener;
    private Video mVideo;

    public ExoPlayerController(ExoPlayer player, TrackSelector trackSelector, Context context) {
        mPlayer = player;
        player.addListener(this);

        mTrackSelector = trackSelector;
        mMediaSourceFactory = ExoMediaSourceFactory.instance(context);
    }

    //private void prepareMediaForPlaying(Uri mediaSourceUri) {
    //    String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
    //    MediaSource mediaSource =
    //            new ExtractorMediaSource(
    //                    mediaSourceUri,
    //                    new DefaultDataSourceFactory(getActivity(), userAgent),
    //                    new DefaultExtractorsFactory(),
    //                    null,
    //                    null);
    //
    //    mPlayer.prepare(mediaSource);
    //}

    public void openDash(InputStream dashManifest) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        mPlayer.prepare(mediaSource);

        if (mEventListener != null) {
            mEventListener.onVideoLoaded(mVideo);
        }
    }

    public void openHls(String hlsPlaylistUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(Uri.parse(hlsPlaylistUrl));
        mPlayer.prepare(mediaSource);

        if (mEventListener != null) {
            mEventListener.onVideoLoaded(mVideo);
        }
    }

    public long getPosition() {
        return mPlayer.getCurrentPosition();
    }

    public void setPosition(long positionMs) {
        mPlayer.seekTo(positionMs);
    }

    public void setPlay(boolean isPlaying) {
        mPlayer.setPlayWhenReady(isPlaying);
    }

    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    public void setEventListener(PlayerEventListener eventListener) {
        mEventListener = eventListener;
    }

    public void setVideo(Video video) {
        mVideo = video;
    }

    public Video getVideo() {
        return mVideo;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (Player.STATE_READY == playbackState) {
            if (playWhenReady) {
                mEventListener.onPlay();
            } else {
                mEventListener.onPause();
            }
        }
    }
}
