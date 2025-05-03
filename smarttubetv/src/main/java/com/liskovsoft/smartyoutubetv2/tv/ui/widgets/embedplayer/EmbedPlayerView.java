package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.exoplayer2.ui.PlayerView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * https://chatgpt.com/c/6806b729-1ab0-8010-94f0-56f6b71cdbfb
 */
public class EmbedPlayerView extends PlayerView implements PlaybackView {
    public EmbedPlayerView(Context context) {
        super(context);
    }

    public EmbedPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EmbedPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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

    }

    @Override
    public Video getVideo() {
        return null;
    }

    @Override
    public void finish() {

    }

    @Override
    public void finishReally() {

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
    public void openDash(InputStream dashManifest) {

    }

    @Override
    public void openDashUrl(String dashManifestUrl) {

    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {

    }

    @Override
    public void openUrlList(List<String> urlList) {

    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {

    }

    @Override
    public long getPositionMs() {
        return 0;
    }

    @Override
    public void setPositionMs(long positionMs) {

    }

    @Override
    public long getDurationMs() {
        return 0;
    }

    @Override
    public void setPlayWhenReady(boolean play) {

    }

    @Override
    public boolean getPlayWhenReady() {
        return false;
    }

    @Override
    public boolean isPlaying() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return Collections.emptyList();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return Collections.emptyList();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return Collections.emptyList();
    }

    @Override
    public void setFormat(FormatItem option) {

    }

    @Override
    public FormatItem getVideoFormat() {
        return null;
    }

    @Override
    public FormatItem getAudioFormat() {
        return null;
    }

    @Override
    public boolean isEngineInitialized() {
        return false;
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
        return false;
    }

    @Override
    public void setSpeed(float speed) {

    }

    @Override
    public float getSpeed() {
        return 0;
    }

    @Override
    public void setPitch(float pitch) {

    }

    @Override
    public float getPitch() {
        return 0;
    }

    @Override
    public void setVolume(float volume) {

    }

    @Override
    public float getVolume() {
        return 0;
    }

    @Override
    public void setResizeMode(int mode) {

    }

    @Override
    public void setZoomPercents(int percents) {

    }

    @Override
    public int getResizeMode() {
        return 0;
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
}
