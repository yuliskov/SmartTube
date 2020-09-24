package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import android.content.Context;
import android.net.Uri;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;

import java.io.InputStream;
import java.util.List;

public class ExoPlayerController implements EventListener, PlayerController {
    private static final String TAG = ExoPlayerController.class.getSimpleName();
    private final ExoPlayer mPlayer;
    private final Context mContext;
    private final DefaultTrackSelector mTrackSelector;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private final TrackSelectorManager mTrackSelectorManager;
    private PlayerEventListener mEventListener;
    private Video mVideo;

    public ExoPlayerController(ExoPlayer player, DefaultTrackSelector trackSelector, Context context) {
        mPlayer = player;
        mContext = context;
        player.addListener(this);

        mTrackSelector = trackSelector;
        mMediaSourceFactory = ExoMediaSourceFactory.instance(context);
        mTrackSelectorManager = new TrackSelectorManager(trackSelector);
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

    @Override
    public void openDash(InputStream dashManifest) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        mPlayer.prepare(mediaSource);

        if (mEventListener != null) {
            mTrackSelectorManager.invalidate();
            mEventListener.onVideoLoaded(mVideo);
        }
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(Uri.parse(hlsPlaylistUrl));
        mPlayer.prepare(mediaSource);

        if (mEventListener != null) {
            mTrackSelectorManager.invalidate();
            mEventListener.onVideoLoaded(mVideo);
        }
    }

    @Override
    public long getPosition() {
        return mPlayer.getCurrentPosition();
    }

    @Override
    public void setPosition(long positionMs) {
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
    public void setEventListener(PlayerEventListener eventListener) {
        mEventListener = eventListener;
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
    public void setRepeatMode(int modeIndex) {
        mPlayer.setRepeatMode(modeIndex);
        mEventListener.onRepeatModeChange(modeIndex);
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
        return ExoFormatItem.from(mTrackSelectorManager.getAudioTracks());
    }

    @Override
    public void selectFormat(FormatItem option) {
        mTrackSelectorManager.selectTrack(ExoFormatItem.toMediaTrack(option));
        mEventListener.onTrackSelected(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return ExoFormatItem.from(mTrackSelectorManager.getVideoTrack());
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d(TAG, "onTracksChanged: start: groups length: " + trackGroups.length);

        if (trackGroups.length == 0) {
            Log.i(TAG, "onTracksChanged: Hmm. Strange. Received empty groups, no selections. Why is this happens only on next/prev videos?");
        }

        for (TrackSelection selection : trackSelections.getAll()) {
            if (selection != null) {
                mEventListener.onTrackChanged(ExoFormatItem.from(selection.getSelectedFormat()));
            }
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, "onPlayerError: " + error);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "onPlayerStateChanged: State: " + playbackState);

        boolean playPressed = Player.STATE_READY == playbackState && playWhenReady;
        boolean pausePressed = Player.STATE_READY == playbackState && !playWhenReady;
        boolean playbackEnded = Player.STATE_ENDED == playbackState && playWhenReady;

        if (playPressed) {
            mEventListener.onPlay();
            //mTrackSelectorManager.applyPendingSelection();
        } else if (pausePressed) {
            mEventListener.onPause();
        } else if (playbackEnded) {
            mEventListener.onPlayEnd();
        }
    }
}
