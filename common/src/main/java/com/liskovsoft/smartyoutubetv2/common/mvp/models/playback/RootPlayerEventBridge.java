package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;

import java.util.ArrayList;

public class RootPlayerEventBridge implements PlayerEventBridge {
    private static final String TAG = RootPlayerEventBridge.class.getSimpleName();
    private static RootPlayerEventBridge sInstance;
    private final ArrayList<PlayerEventBridge> mProcessors;

    public RootPlayerEventBridge() {
        mProcessors = new ArrayList<>();
        
        mProcessors.add(new HistoryUpdater());
        mProcessors.add(new SuggestionsLoader());
        mProcessors.add(new VideoLoader());
        mProcessors.add(new PlaylistUpdater());
    }

    public static RootPlayerEventBridge instance() {
        if (sInstance == null) {
            sInstance = new RootPlayerEventBridge();
        }

        return sInstance;
    }

    @Override
    public void setController(PlayerController controller) {
        for (PlayerEventBridge processor : mProcessors) {
             processor.setController(controller);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        if (item == null) {
            Log.e(TAG, "onSuggestionItemClicked: item is null");
            return;
        }

        for (PlayerEventBridge processor : mProcessors) {
            processor.onSuggestionItemClicked(item);
        }
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        if (item == null) {
            Log.e(TAG, "onSuggestionItemLongClicked: item is null");
            return;
        }

        for (PlayerEventBridge processor : mProcessors) {
            processor.onSuggestionItemLongClicked(item);
        }
    }

    @Override
    public void onPrevious() {
        for (PlayerEventBridge processor : mProcessors) {
            processor.onPrevious();
        }
    }

    @Override
    public void onNext() {
        for (PlayerEventBridge processor : mProcessors) {
            processor.onNext();
        }
    }

    @Override
    public void onInit(Video item) {
        if (item == null) {
            Log.e(TAG, "onOpenVideo: item is null");
            return;
        }

        for (PlayerEventBridge processor : mProcessors) {
            processor.onInit(item);
        }
    }
}
