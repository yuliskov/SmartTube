package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

import java.util.HashMap;
import java.util.Map;

public class PositionRestorer extends PlayerEventListenerHelper {
    private Map<String, Long> mPositionMap = new HashMap<>();

    @Override
    public void onVideoLoaded() {
        Long mPositionMs = mPositionMap.get(mController.getTitle() + mController.getSubtitle());

        if (mPositionMs != null) {
            mController.setPositionMs(mPositionMs);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        savePosition();
    }

    @Override
    public void onViewPaused() {
        savePosition();
    }

    private void savePosition() {
        mPositionMap.put(mController.getTitle() + mController.getSubtitle(), mController.getPositionMs());
    }
}
