package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerUiEventListener {
    void onSuggestionItemClicked(Video item);
    void onSuggestionItemLongClicked(Video item);
    void onScrollEnd(Video item);
    boolean onPreviousClicked();
    boolean onNextClicked();
    void onPlayClicked();
    void onPauseClicked();
    boolean onKeyDown(int keyCode);
    void onHighQualityClicked();
    void onDislikeClicked(boolean dislike);
    void onLikeClicked(boolean like);
    void onChannelClicked();
    void onSubtitleClicked(boolean enabled);
    void onSubtitleLongClicked(boolean enabled);
    void onPlaylistAddClicked();
    void onDebugInfoClicked(boolean enabled);
    void onSpeedClicked(boolean enabled);
    void onSpeedLongClicked(boolean enabled);
    void onSeekIntervalClicked();
    void onChatClicked(boolean enabled);
    void onChatLongClicked(boolean enabled);
    void onVideoInfoClicked();
    void onShareLinkClicked();
    void onSearchClicked();
    void onVideoZoomClicked();
    void onPipClicked();
    void onPlaybackQueueClicked();
    void onButtonClicked(int buttonId, int buttonState);
    void onButtonLongClicked(int buttonId, int buttonState);
    void onControlsShown(boolean shown);
}
