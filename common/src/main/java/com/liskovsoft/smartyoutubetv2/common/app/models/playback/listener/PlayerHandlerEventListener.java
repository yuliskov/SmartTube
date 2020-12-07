package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

// is paused, position, tracks (audio, video, subs), codecs, aspect, speed
// title, subtitle (description), subscribed/liked nums, published date, toggle buttons, simple buttons
public interface PlayerHandlerEventListener extends PlayerUiEventListener, PlayerEngineEventListener, ViewEventListener {
    void openVideo(Video item);
    void onInitDone();
}
