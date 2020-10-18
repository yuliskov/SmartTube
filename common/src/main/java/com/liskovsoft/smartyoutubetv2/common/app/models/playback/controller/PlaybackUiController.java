package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface PlaybackUiController {
    int REPEAT_NONE = 0;
    int REPEAT_ONE = 1;
    int REPEAT_ALL = 2;
    void updateSuggestions(VideoGroup group);
    void resetSuggestedPosition();
    boolean isSuggestionsEmpty();
    void clearSuggestions();
    void showControls(boolean show);
    boolean isSuggestionsShown();
    void setRepeatButtonState(int modeIndex);
    void setLikeButtonState(boolean like);
    void setDislikeButtonState(boolean dislike);
    void setSubscribeButtonState(boolean subscribe);
    void setDebugButtonState(boolean show);
    void showDebugView(boolean show);
}
