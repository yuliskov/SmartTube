package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.InputStream;
import java.util.List;

/**
 * https://chatgpt.com/c/6806b729-1ab0-8010-94f0-56f6b71cdbfb
 */
public class EmbedPlayerView extends PlayerView implements PlaybackView {
    private static final String TAG = EmbedPlayerView.class.getSimpleName();
    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_NORMAL = 1;
    private SimpleExoPlayer mPlayer;
    private ExoPlayerInitializer mPlayerInitializer;
    private PlayerController mExoPlayerController;
    private PlaybackPresenter mPlaybackPresenter;
    private Video mVideo;
    private boolean mIsMute;
    private final Runnable mShowView = this::showView;
    private final Runnable mStopPlayback = this::finish;
    private int mQuality;
    private float mPercentWatched;

    public EmbedPlayerView(Context context) {
        super(context);
        hideView();
    }

    public EmbedPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        hideView();
    }

    public EmbedPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        hideView();
    }

    private void hideView() {
        setAlpha(0);
    }

    private void showView() {
        setAlpha(1);
    }

    @Override
    public void updateSuggestions(VideoGroup group) {

    }

    @Override
    public void removeSuggestions(VideoGroup group) {

    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return 0;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        return null;
    }

    @Override
    public void focusSuggestedItem(int index) {

    }

    @Override
    public void focusSuggestedItem(Video video) {

    }

    @Override
    public void resetSuggestedPosition() {

    }

    @Override
    public boolean isSuggestionsEmpty() {
        return false;
    }

    @Override
    public void clearSuggestions() {

    }

    @Override
    public void showOverlay(boolean show) {

    }

    @Override
    public boolean isOverlayShown() {
        return false;
    }

    @Override
    public void showSuggestions(boolean show) {

    }

    @Override
    public boolean isSuggestionsShown() {
        return false;
    }

    @Override
    public void showControls(boolean show) {

    }

    @Override
    public boolean isControlsShown() {
        return false;
    }

    @Override
    public void setLikeButtonState(boolean like) {

    }

    @Override
    public void setDislikeButtonState(boolean dislike) {

    }

    @Override
    public void setPlaylistAddButtonState(boolean selected) {

    }

    @Override
    public void setSubtitleButtonState(boolean selected) {

    }

    @Override
    public void setSpeedButtonState(boolean selected) {

    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {

    }

    @Override
    public void setChannelIcon(String iconUrl) {

    }

    @Override
    public void setSeekPreviewTitle(String title) {

    }

    @Override
    public void setNextTitle(Video nextVideo) {

    }

    @Override
    public void setDebugButtonState(boolean show) {

    }

    @Override
    public void showDebugInfo(boolean show) {

    }

    @Override
    public void showSubtitles(boolean show) {

    }

    @Override
    public void loadStoryboard() {

    }

    @Override
    public void setTitle(String title) {

    }

    @Override
    public void showProgressBar(boolean show) {
        
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {

    }

    @Override
    public void updateEndingTime() {

    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {

    }

    @Override
    public void setVideo(Video item) {
        mVideo = item;

        if (mExoPlayerController != null) {
            mExoPlayerController.setVideo(mVideo);
        }
    }

    @Override
    public Video getVideo() {
        return mVideo;
    }

    @Override
    public void finish() {
        destroyPlayerObjects();
    }

    @Override
    public void finishReally() {
        finish();
    }

    @Override
    public void showBackground(String url) {

    }

    @Override
    public void showBackgroundColor(int colorResId) {

    }

    @Override
    public void resetPlayerState() {

    }

    @Override
    public boolean isEmbed() {
        return true;
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        long durationMs = mExoPlayerController.getDurationMs();

        long liveDurationMs = getVideo() != null ? getVideo().getLiveDurationMs() : 0;

        if (durationMs > Video.MAX_LIVE_DURATION_MS && liveDurationMs != 0) {
            durationMs = liveDurationMs;
        }

        return durationMs;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        // NOP. Use internal selection
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {

    }

    @Override
    public void reloadPlayback() {

    }

    @Override
    public void blockEngine(boolean block) {

    }

    @Override
    public boolean isEngineBlocked() {
        return false;
    }

    @Override
    public boolean isInPIPMode() {
        return false;
    }

    @Override
    public boolean containsMedia() {
        if (mExoPlayerController == null) {
            return false;
        }

        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setSpeed(speed);
        }
    }

    @Override
    public float getSpeed() {
        if (mExoPlayerController == null) {
            return 1f;
        }

        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        if (mExoPlayerController != null) {
            mExoPlayerController.setPitch(pitch);
        }
    }

    @Override
    public float getPitch() {
        if (mExoPlayerController == null) {
            return 1f;
        }

        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        if (!mIsMute && mExoPlayerController != null) {
            mExoPlayerController.setVolume(volume);
        }
    }

    @Override
    public float getVolume() {
        if (mExoPlayerController == null) {
            return 1f;
        }

        return mExoPlayerController.getVolume();
    }

    @Override
    public void setZoomPercents(int percents) {

    }

    @Override
    public int getResizeMode() {
        return super.getResizeMode();
    }

    @Override
    public void setAspectRatio(float ratio) {

    }

    @Override
    public void setRotationAngle(int angle) {

    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {

    }

    @Override
    protected void finalize() throws Throwable {
        try {
            finish();
        } finally {
            super.finalize();
        }
    }

    public void setQuality(int quality) {
        mQuality = quality;
    }

    public void openVideo(String videoId) {
        openVideo(Video.from(videoId));
    }

    public void openVideo(@Nullable Video video) {
        if (video == null) {
            return;
        }

        if (mPlaybackPresenter == null) {
            mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        }

        // Fullscreen playback is running. Skipping
        PlaybackView view = mPlaybackPresenter.getView();
        if (view == null || view instanceof EmbedPlayerView || !PlaybackPresenter.instance(getContext()).isEngineInitialized()) {
            initPlayer();
            createPlayerObjects();
            mPlaybackPresenter.onNewVideo(video);
            mPercentWatched = video.percentWatched;
        }
    }

    private void initPlayer() {
        if (isEngineInitialized()) {
            mPlaybackPresenter.setView(this);
            return;
        }

        mPlayerInitializer = new ExoPlayerInitializer(getContext());
        mPlaybackPresenter.setView(this);
        mExoPlayerController = new ExoPlayerController(getContext(), mPlaybackPresenter);
        mExoPlayerController.setOnVideoLoaded(this::onVideoLoaded);
        mPlaybackPresenter.onViewInitialized(); // init all controllers

        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); // Fix unfilled borders
    }

    private void createPlayerObjects() {
        if (isEngineInitialized()) {
            setPlayer(mPlayer);
            return;
        }

        // Use default or pass your bandwidthMeter here: bandwidthMeter = new DefaultBandwidthMeter.Builder(getContext()).build()
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(getContext());
        mPlayer = mPlayerInitializer.createPlayer(getContext(), renderersFactory, trackSelector);
        mPlayer.setPlayWhenReady(true);
        //mPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

        // Fix seeking on TextureView (some devices only)
        if (PlayerTweaksData.instance(getContext()).isTextureViewEnabled()) {
            // Also, live stream (dash) seeking fix
            mPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        }

        mExoPlayerController.setPlayer(mPlayer);
        //mExoPlayerController.setVideo(mVideo);
        mExoPlayerController.selectFormat(mQuality == QUALITY_LOW ? FormatItem.VIDEO_SUB_SD_AVC_30 : FormatItem.VIDEO_SD_AVC_30);
        // Don't use subs! Not efficient. High cpu load. Cause input lags.
        mExoPlayerController.selectFormat(FormatItem.SUBTITLE_NONE);
        if (mIsMute) {
            mExoPlayerController.setVolume(0);
        }

        if (PlayerTweaksData.instance(getContext()).isAudioFocusEnabled()) {
            ExoPlayerInitializer.enableAudioFocus(mPlayer, true);
        }

        setPlayer(mPlayer);

        mPlaybackPresenter.onEngineInitialized(); // start playback
    }

    private void destroyPlayerObjects() {
        if (isEngineInitialized()) {
            Utils.removeCallbacks(mShowView);
            Utils.removeCallbacks(mStopPlayback);
            // Don't replace main player!
            if (mPlaybackPresenter.getView() == null || mPlaybackPresenter.getView() == this) {
                mPlaybackPresenter.onEngineReleased();
            }
            mExoPlayerController.setOnVideoLoaded(null);
            // Fix access calls when player isn't initialized
            mExoPlayerController.release();
            mPlayer = null;
            setPlayer(null);
            hideView();
            syncPositionIfNeeded();
        }
    }

    private void syncPositionIfNeeded() {
        if (!mIsMute && isPositionChanged()) {
            BasePresenter<?> presenter = ViewManager.instance(getContext()).getCurrentPresenter();
            if (presenter != null) {
                presenter.syncItem(mVideo);
            }
        }
    }

    private boolean isPositionChanged() {
        return mVideo != null && Math.abs(mPercentWatched - mVideo.percentWatched) > 20;
    }

    private void onVideoLoaded() {
        syncPositionIfNeeded();
        // Fix the screen becomes black for a moment
        Utils.postDelayed(mShowView, 1_000);
        if (mIsMute) { // Save bandwidth if the previews are muted
            Utils.postDelayed(mStopPlayback, 5 * 60 * 1_000);
        }
    }

    public void setMute(boolean mute) {
        mIsMute = mute;

        if (mExoPlayerController != null) {
            mExoPlayerController.setVolume(mute ? 0 : 1f);
        }
    }
}
