package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;

import java.util.ArrayList;

public class PlayerProcessorFacade implements PlayerCommandProcessor {
    private static final String TAG = PlayerProcessorFacade.class.getSimpleName();
    private static PlayerProcessorFacade sInstance;
    private final ArrayList<PlayerCommandProcessor> mProcessors;

    public PlayerProcessorFacade() {
        mProcessors = new ArrayList<>();

        mProcessors.add(new HistoryUpdater());
        mProcessors.add(new SuggestionsLoader());
        mProcessors.add(new VideoLoader());
    }

    public static PlayerProcessorFacade instance() {
        if (sInstance == null) {
            sInstance = new PlayerProcessorFacade();
        }

        return sInstance;
    }

    @Override
    public void setCommandHandler(PlayerCommandHandler commandHandler) {
        for (PlayerCommandProcessor processor : mProcessors) {
             processor.setCommandHandler(commandHandler);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        if (item == null) {
            Log.e(TAG, "onSuggestionItemClicked: item is null");
            return;
        }

        for (PlayerCommandProcessor processor : mProcessors) {
            processor.onSuggestionItemClicked(item);
        }
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        if (item == null) {
            Log.e(TAG, "onSuggestionItemLongClicked: item is null");
            return;
        }

        for (PlayerCommandProcessor processor : mProcessors) {
            processor.onSuggestionItemLongClicked(item);
        }
    }

    @Override
    public void onPrevious() {
        for (PlayerCommandProcessor processor : mProcessors) {
            processor.onPrevious();
        }
    }

    @Override
    public void onNext() {
        for (PlayerCommandProcessor processor : mProcessors) {
            processor.onNext();
        }
    }

    @Override
    public void onOpenVideo(Video item) {
        if (item == null) {
            Log.e(TAG, "onOpenVideo: item is null");
            return;
        }

        for (PlayerCommandProcessor processor : mProcessors) {
            processor.onOpenVideo(item);
        }
    }
}
