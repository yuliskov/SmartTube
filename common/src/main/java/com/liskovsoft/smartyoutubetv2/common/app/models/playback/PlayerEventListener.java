package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

// is paused, position, tracks (audio, video, subs), codecs, aspect, speed
// title, subtitle (description), subscribed/liked nums, published date, toggle buttons, simple buttons
public interface PlayerEventListener extends ViewEventListener {
    void onSuggestionItemClicked(Video item);
    void onSuggestionItemLongClicked(Video item);
    void onPrevious();
    void onNext();
    void onVideoLoaded(Video item);
    void onEngineInitialized();
    void onEngineReleased();
}
