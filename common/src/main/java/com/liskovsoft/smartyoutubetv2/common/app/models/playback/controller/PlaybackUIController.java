package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;

import java.util.List;

public interface PlaybackUIController {
    int BUTTON_STATE_DISABLED = -1;
    int BUTTON_STATE_OFF = 0;
    int BUTTON_STATE_ON = 1;
    int REPEAT_MODE_CLOSE = 0;
    int REPEAT_MODE_ONE = 1;
    int REPEAT_MODE_ALL = 2;
    int REPEAT_MODE_PAUSE = 3;
    int REPEAT_MODE_LIST = 4;
    int REPEAT_MODE_SHUFFLE = 5;
    void updateSuggestions(VideoGroup group);
    void removeSuggestions(VideoGroup group);
    int getSuggestionsIndex(VideoGroup group);
    VideoGroup getSuggestionsByIndex(int index);
    void focusSuggestedItem(int index);
    void resetSuggestedPosition();
    boolean isSuggestionsEmpty();
    void clearSuggestions();
    void showOverlay(boolean show);
    boolean isOverlayShown();
    void showSuggestions(boolean show);
    boolean isSuggestionsShown();
    void showControls(boolean show);
    boolean isControlsShown();
    void setRepeatButtonState(int modeIndex);
    void setLikeButtonState(boolean like);
    void setDislikeButtonState(boolean dislike);
    void setSubscribeButtonState(boolean subscribe);
    void setPlaylistAddButtonState(boolean selected);
    void setSubtitleButtonState(boolean selected);
    void setSpeedButtonState(boolean selected);
    void setContentBlockButtonState(boolean selected);
    void setChatButtonState(boolean selected);
    void setChannelIcon(String iconUrl);
    void setSeekPreviewTitle(String title);
    void setNextTitle(String title);
    void setDebugButtonState(boolean show);
    void showDebugInfo(boolean show);
    void showSubtitles(boolean show);
    void loadStoryboard();
    void showError(String errorInfo);
    void showProgressBar(boolean show);
    void setSeekBarSegments(List<SeekBarSegment> segments);
    void updateEndingTime();
    void setChatReceiver(ChatReceiver chatReceiver);
}
