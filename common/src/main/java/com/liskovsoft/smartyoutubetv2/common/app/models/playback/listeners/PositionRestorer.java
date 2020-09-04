package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

import java.util.HashMap;
import java.util.Map;

public class PositionRestorer extends PlayerEventListenerHelper {
    private Map<String, Long> mPositionMap = new HashMap<>();

    @Override
    public void onVideoLoaded(Video item) {
        restorePosition(item);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        savePosition();
    }

    @Override
    public void onEngineReleased() {
        savePosition();
    }

    private void restorePosition(Video item) {
        Long mPositionMs = mPositionMap.get(item.title + item.description);

        if (mPositionMs != null) {
            mController.setPositionMs(mPositionMs);
        }
    }

    private void savePosition() {
        Video video = mController.getVideo();
        mPositionMap.put(video.title + video.description, mController.getPositionMs());
    }
}
