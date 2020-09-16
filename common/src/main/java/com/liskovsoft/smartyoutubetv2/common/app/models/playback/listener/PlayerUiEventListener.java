package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerUiEventListener {
    void onSuggestionItemClicked(Video item);
    void onSuggestionItemLongClicked(Video item);
    boolean onPreviousClicked();
    boolean onNextClicked();
    void onPlayClicked();
    void onPauseClicked();
    void onKeyDown(int keyCode);
    void onRepeatModeClicked(int modeIndex);
    void onHighQualityClicked();
    void onSubscribeClicked(boolean subscribed);
    void onThumbsDownClicked(boolean thumbsDown);
    void onThumbsUpClicked(boolean thumbsUp);
}
