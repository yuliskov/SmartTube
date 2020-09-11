package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

// is paused, position, tracks (audio, video, subs), codecs, aspect, speed
// title, subtitle (description), subscribed/liked nums, published date, toggle buttons, simple buttons
public interface PlayerEventListener extends ViewEventListener {
    /** UI **/
    void onSuggestionItemClicked(Video item);
    void onSuggestionItemLongClicked(Video item);
    boolean onPreviousClicked();
    boolean onNextClicked();
    void onPlayClicked();
    void onPauseClicked();
    void onKeyDown(int keyCode);

    /** Engine **/
    void onPlay();
    void onPause();
    void onSeek();
    void onVideoLoaded(Video item);
    void onEngineInitialized();
    void onEngineReleased();
    void onPlayEnd();
}
