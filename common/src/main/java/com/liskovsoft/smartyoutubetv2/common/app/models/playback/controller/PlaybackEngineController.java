package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlaybackEngineController {
    int ENGINE_BLOCK_TYPE_NONE = 0;
    int ENGINE_BLOCK_TYPE_AUDIO = 1;
    int ENGINE_BLOCK_TYPE_PIP = 2;
    int ENGINE_BLOCK_TYPE_BEHIND = 3;
    int BUFFER_LOW = 0;
    int BUFFER_MED = 1;
    int BUFFER_HIGH = 2;
    int ZOOM_MODE_DEFAULT = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    int ZOOM_MODE_FIT_WIDTH = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
    int ZOOM_MODE_FIT_HEIGHT = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT;
    int ZOOM_MODE_FIT_BOTH = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    int ZOOM_MODE_STRETCH = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    void openDash(InputStream dashManifest);
    void openDashUrl(String dashManifestUrl);
    void openHlsUrl(String hlsPlaylistUrl);
    void openUrlList(List<String> urlList);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getLengthMs();
    void setPlay(boolean play);
    boolean isPlaying();
    List<FormatItem> getVideoFormats();
    List<FormatItem> getAudioFormats();
    List<FormatItem> getSubtitleFormats();
    void selectFormat(FormatItem option);
    FormatItem getVideoFormat();
    boolean isEngineInitialized();
    void restartEngine();
    void reloadPlayback();
    void setEngineBlockType(int type);
    int getEngineBlockType();
    boolean isInPIPMode();
    boolean hasNoMedia();
    void setSpeed(float speed);
    float getSpeed();
    void setBufferType(int bufferType);
    int getBufferType();
    void setVideoZoomMode(int mode);
    int getVideoZoomMode();
    void releasePlayer();
    interface OnBufferSelected {
        void onBufferSelected(int type);
    }
    interface OnSelectZoomMode {
        void onSelectZoomMode(int mode);
    }
}
