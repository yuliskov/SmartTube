package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers;

import android.content.Context;
import android.net.Uri;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.tracks.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.tracks.FormatOptionItem;

import java.io.InputStream;
import java.util.List;

public class ExoPlayerController implements EventListener, PlayerController {
    private static final String TAG = ExoPlayerController.class.getSimpleName();
    private final ExoPlayer mPlayer;
    private final Context mContext;
    private final DefaultTrackSelector mTrackSelector;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private final TrackSelectorManager mTrackSelectionManager;
    private PlayerEventListener mEventListener;
    private Video mVideo;

    public ExoPlayerController(ExoPlayer player, DefaultTrackSelector trackSelector, Context context) {
        mPlayer = player;
        mContext = context;
        player.addListener(this);

        mTrackSelector = trackSelector;
        mMediaSourceFactory = ExoMediaSourceFactory.instance(context);
        mTrackSelectionManager = new TrackSelectorManager(trackSelector);
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
            mEventListener.onVideoLoaded(mVideo);
        }
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(Uri.parse(hlsPlaylistUrl));
        mPlayer.prepare(mediaSource);

        if (mEventListener != null) {
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
    public List<OptionItem> getVideoFormats() {
        return FormatOptionItem.from(mTrackSelectionManager.getVideoTracks(), mContext.getString(R.string.dialog_video_default));
    }

    @Override
    public List<OptionItem> getAudioFormats() {
        return FormatOptionItem.from(mTrackSelectionManager.getAudioTracks(), mContext.getString(R.string.dialog_audio_default));
    }

    @Override
    public List<OptionItem> getSubtitleFormats() {
        return FormatOptionItem.from(mTrackSelectionManager.getAudioTracks(), mContext.getString(R.string.dialog_subtitile_default));
    }

    @Override
    public void selectFormat(OptionItem option) {
        mTrackSelectionManager.selectMediaTrack(FormatOptionItem.toMediaTrack(option));
    }

    @Override
    public OptionItem getCurrentFormat() {
        return FormatOptionItem.from(mTrackSelectionManager.getCurrentTrack());
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "State: " + playbackState);

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
}
