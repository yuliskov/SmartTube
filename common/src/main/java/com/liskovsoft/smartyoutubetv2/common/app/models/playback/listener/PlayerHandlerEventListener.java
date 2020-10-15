package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import android.app.Activity;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;

// is paused, position, tracks (audio, video, subs), codecs, aspect, speed
// title, subtitle (description), subscribed/liked nums, published date, toggle buttons, simple buttons
public interface PlayerHandlerEventListener extends PlayerUiEventListener, PlayerEngineEventListener, ViewEventListener {
    void openVideo(Video item);
    void onController(PlaybackController controller);
    void onActivity(Activity activity);
    void onInitDone();
}
