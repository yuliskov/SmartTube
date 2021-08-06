package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public interface PlayerUiEventListener {
    int REPEAT_NONE = 0;
    int REPEAT_ONE = 1;
    int REPEAT_ALL = 2;
    void onSuggestionItemClicked(Video item);
    void onSuggestionItemLongClicked(Video item);
    void onScrollEnd(Video item);
    boolean onPreviousClicked();
    boolean onNextClicked();
    void onPlayClicked();
    void onPauseClicked();
    boolean onKeyDown(int keyCode);
    void onRepeatModeClicked(int modeIndex);
    void onHighQualityClicked();
    void onSubscribeClicked(boolean subscribed);
    void onThumbsDownClicked(boolean thumbsDown);
    void onThumbsUpClicked(boolean thumbsUp);
    void onChannelClicked();
    void onTrackSelected(FormatItem track);
    void onSubtitlesClicked();
    void onPlaylistAddClicked();
    void onDebugInfoClicked(boolean enabled);
    void onVideoSpeedClicked();
    void onSearchClicked();
    void onVideoZoomClicked();
    void onPipClicked();
    void onScreenOffClicked();
    void onPlaybackQueueClicked();
    void onControlsShown(boolean shown);
}
