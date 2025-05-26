package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.TrackErrorFixer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.VolumeBooster;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackInfoFormatter2;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.VideoTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

public class ExoPlayerController implements Player.EventListener, PlayerController {
    private static final String TAG = ExoPlayerController.class.getSimpleName();
    private final Context mContext;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private final TrackSelectorManager mTrackSelectorManager;
    private final TrackInfoFormatter2 mTrackFormatter;
    private final TrackErrorFixer mTrackErrorFixer;
    private boolean mOnSourceChanged;
    private WeakReference<Video> mVideo;
    private final PlayerEventListener mEventListener;
    private SimpleExoPlayer mPlayer;
    private PlayerView mPlayerView;
    private VolumeBooster mVolumeBooster;
    private boolean mIsEnded;
    private Runnable mOnVideoLoaded;

    public ExoPlayerController(Context context, PlayerEventListener eventListener) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        mContext = context.getApplicationContext();
        mMediaSourceFactory = new ExoMediaSourceFactory(context);
        mTrackSelectorManager = new TrackSelectorManager(context);
        mTrackFormatter = new TrackInfoFormatter2();
        mTrackFormatter.enableBitrate(PlayerTweaksData.instance(context).isQualityInfoBitrateEnabled());
        mTrackErrorFixer = new TrackErrorFixer(mTrackSelectorManager);

        mMediaSourceFactory.setTrackErrorFixer(mTrackErrorFixer);
        mEventListener = eventListener;
        
        applyShield720pFix();
        VideoTrack.sIsNoFpsPresetsEnabled = playerTweaksData.isNoFpsPresetsEnabled();
        MediaTrack.preferAvcOverVp9(playerTweaksData.isAvcOverVp9Preferred());
    }

    private void applyShield720pFix() {
        PlayerData playerData = PlayerData.instance(mContext);
        mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_VIDEO)));
        mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_AUDIO)));
        mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_SUBTITLE)));
    }

    @Override
    public void openDash(InputStream dashManifest) {
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        openMediaSource(mediaSource);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifestUrl(dashManifestUrl);
        openMediaSource(mediaSource);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl);
        openMediaSource(mediaSource);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        MediaSource mediaSource = mMediaSourceFactory.fromUrlList(urlList);
        openMediaSource(mediaSource);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        MediaSource dashMediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        MediaSource hlsMediaSource = mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl);
        openMediaSource(new MergingMediaSource(dashMediaSource, hlsMediaSource));
    }

    private void openMediaSource(MediaSource mediaSource) {
        resetPlayerState(); // fixes occasional video artifacts and problems with quality switching
        setQualityInfo("");

        mTrackSelectorManager.setMergedSource(mediaSource instanceof MergingMediaSource);
        mTrackSelectorManager.invalidate();
        mOnSourceChanged = true;
        mEventListener.onSourceChanged(getVideo());
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
        if (mPlayer != null && positionMs >= 0 && positionMs <= getDurationMs()) {
            mPlayer.seekTo(positionMs);
        }
    }

    @Override
    public long getDurationMs() {
        if (mPlayer == null) {
            return -1;
        }

        long duration = mPlayer.getDuration();
        return duration != C.TIME_UNSET ? duration : -1;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(play);
        }
    }

    @Override
    public boolean getPlayWhenReady() {
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
        mMediaSourceFactory.release();
        releasePlayer();
        mPlayerView = null;
        // Don't destroy it (needed inside the bridge)!
        //mEventListener = null;
    }

    @Override
    public void setPlayer(SimpleExoPlayer player) {
        mPlayer = player;
        player.addListener(this);
    }

    //@Override
    //public void setEventListener(PlayerEventListener eventListener) {
    //    mEventListener = eventListener;
    //}

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
        mVideo = new WeakReference<>(video);
    }

    @Override
    public Video getVideo() {
        return mVideo != null ? mVideo.get() : null;
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
            mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(formatItem));
        }
    }

    @Override
    public FormatItem getVideoFormat() {
        return ExoFormatItem.from(mTrackSelectorManager.getVideoTrack());
    }

    @Override
    public FormatItem getAudioFormat() {
        return ExoFormatItem.from(mTrackSelectorManager.getAudioTrack());
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d(TAG, "onTracksChanged: start: groups length: " + trackGroups.length);

        if (trackGroups.length == 0) {
            Log.i(TAG, "onTracksChanged: Hmm. Strange. Received empty groups, no selections. Why is this happens only on next/prev videos?");
            return;
        }

        notifyOnVideoLoad();

        for (TrackSelection selection : trackSelections.getAll()) {
            if (selection != null) {
                // EXO: 2.12.1
                //Format format = selection.getSelectedFormat();

                // EXO: 2.13.1
                Format format = selection.getFormat(0);

                mEventListener.onTrackChanged(ExoFormatItem.from(format));

                mTrackFormatter.setFormat(format);
            }
        }
        
        setQualityInfo(mTrackFormatter.getQualityLabel());

        // Manage audio focus. E.g. use Spotify when audio is disabled. (NOT NEEDED!!!)
        //MediaTrack audioTrack = mTrackSelectorManager.getAudioTrack();
        //ExoPlayerInitializer.enableAudioFocus(mPlayer, audioTrack != null && !audioTrack.isEmpty());
    }

    private void notifyOnVideoLoad() {
        if (mOnSourceChanged) {
            mOnSourceChanged = false;

            mEventListener.onVideoLoaded(getVideo());

            if (mOnVideoLoaded != null) {
                mOnVideoLoaded.run();
            }

            // Produce thread sync problems
            // Attempt to read from field 'java.util.TreeMap$Node java.util.TreeMap$Node.left' on a null object reference
            //mTrackSelectorManager.fixTracksSelection();
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, "onPlayerError: " + error);

        // NOTE: Player is released at this point. So, there is no sense to restore the playback here.

        Throwable nested = error.getCause() != null ? error.getCause() : error;

        mEventListener.onEngineError(error.type, error.rendererIndex, nested);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onPlayerStateChanged: " + TrackSelectorUtil.stateToString(playbackState));
        }

        boolean isPlayPressed = Player.STATE_READY == playbackState && playWhenReady;
        boolean isPausePressed = Player.STATE_READY == playbackState && !playWhenReady;
        boolean isPlaybackEnded = Player.STATE_ENDED == playbackState && playWhenReady;
        boolean isBuffering = Player.STATE_BUFFERING == playbackState && playWhenReady;

        // Fix chapters (seek and play) after playback ends
        if (isPlaybackEnded && mIsEnded) {
            return;
        }

        if (isPlayPressed) {
            mEventListener.onPlay();
        } else if (isPausePressed) {
            mEventListener.onPause();
        } else if (isPlaybackEnded) {
            mEventListener.onPlayEnd();
            mIsEnded = true;
        } else if (isBuffering) {
            mEventListener.onBuffering();
        }

        if (getPositionMs() < getDurationMs()) {
            mIsEnded = false;
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
        if (mPlayer != null && speed > 0 && !Helpers.floatEquals(speed, getSpeed())) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(speed, mPlayer.getPlaybackParameters().pitch));

            mTrackFormatter.setSpeed(speed);
            setQualityInfo(mTrackFormatter.getQualityLabel());
            mEventListener.onSpeedChanged(speed);
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

    @Override
    public void setPitch(float pitch) {
        if (mPlayer != null && pitch > 0 && !Helpers.floatEquals(pitch, getPitch())) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(mPlayer.getPlaybackParameters().speed, pitch));
        }
    }

    @Override
    public float getPitch() {
        if (mPlayer != null) {
            return mPlayer.getPlaybackParameters().pitch;
        } else {
            return -1;
        }
    }

    @Override
    public void setVolume(float volume) {
        if (mPlayer != null && volume >= 0) {
            mPlayer.setVolume(Math.min(volume, 1f));

            applyVolumeBoost(volume);
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

    /**
     * Fixes video artifacts when switching to the next video.<br/>
     * Also could help with memory leaks(??)<br/>
     * Without this also you'll have problems with track quality switching(??).
     */
    @Override
    public void resetPlayerState() {
        if (containsMedia()) {
            mPlayer.stop(true);
        }
    }

    @Override
    public void setOnVideoLoaded(Runnable onVideoLoaded) {
        mOnVideoLoaded = onVideoLoaded;
    }

    private void setQualityInfo(String qualityInfoStr) {
        if (mPlayerView != null && qualityInfoStr != null) {
            mPlayerView.setQualityInfo(qualityInfoStr);
        }
    }

    private void applyVolumeBoost(float volume) {
        if (mPlayer == null) {
            return;
        }

        if (mVolumeBooster != null) {
            mPlayer.removeAudioListener(mVolumeBooster);
            mVolumeBooster = null;
        }

        // 5.1 audio cannot be boosted (format isn't supported error)
        // also, other 2.0 tracks in 5.1 group is already too loud. so cancel them too.
        if (volume > 1f && !contains51Audio() && Build.VERSION.SDK_INT >= 19) {
            mVolumeBooster = new VolumeBooster(true, volume);
            mPlayer.addAudioListener(mVolumeBooster);
        }
    }
    
    private boolean contains51Audio() {
        if (mTrackSelectorManager == null || mTrackSelectorManager.getAudioTracks() == null) {
            return false;
        }

        for (MediaTrack track : mTrackSelectorManager.getAudioTracks()) {
            if (TrackSelectorUtil.is51Audio(track.format)) {
                return true;
            }
        }

        return false;
    }

    private void releasePlayer() {
        if (mPlayer == null) {
            return;
        }

        try {
            mPlayer.removeListener(this);
            mPlayer.stop(true); // Cause input lags due to high cpu load?
            mPlayer.clearVideoSurface();
            mPlayer.release();
            mPlayer = null;
        } catch (ArrayIndexOutOfBoundsException e) { // thrown on stop()
            e.printStackTrace();
        }
    }
}
