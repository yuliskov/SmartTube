package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;

/**
 * <a href="Zoom to fit video: https://stackoverflow.com/questions/33608746/in-android-using-exoplayer-how-to-fill-surfaceview-with-a-video-that-does-not">More info</a>
 */
public class VideoZoomManager {
    public static final int MODE_DEFAULT = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public static final int MODE_FIT_WIDTH = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
    public static final int MODE_FIT_HEIGHT = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT;
    public static final int MODE_FIT_BOTH = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    public static final int MODE_STRETCH = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    private final PlayerView mPlayerView;

    public VideoZoomManager(PlayerView playerView) {
        mPlayerView = playerView;
    }

    public int getZoomMode() {
        return mPlayerView.getResizeMode();
    }

    public void setZoomMode(int mode) {
        mPlayerView.setResizeMode(mode);
    }
}
