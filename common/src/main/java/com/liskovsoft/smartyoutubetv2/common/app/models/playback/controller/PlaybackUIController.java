package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;

import java.util.List;

public interface PlaybackUIController {
    void updateSuggestions(VideoGroup group);
    void resetSuggestedPosition();
    boolean isSuggestionsEmpty();
    void clearSuggestions();
    void showControls(boolean show);
    boolean isControlsShown();
    void showSuggestions(boolean show);
    boolean isSuggestionsShown();
    void setRepeatButtonState(int modeIndex);
    void setLikeButtonState(boolean like);
    void setDislikeButtonState(boolean dislike);
    void setSubscribeButtonState(boolean subscribe);
    void setDebugButtonState(boolean show);
    void showDebugInfo(boolean show);
    List<SubtitleStyle> getSubtitleStyles();
    void setSubtitleStyle(SubtitleStyle subtitleStyle);
    SubtitleStyle getSubtitleStyle();
    void loadStoryboard();
    void showError(String errorInfo);
    void showProgressBar(boolean show);
}
