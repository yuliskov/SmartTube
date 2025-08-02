package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;

public class BrowseProcessorManager implements BrowseProcessor {
    private final ArrayList<BrowseProcessor> mProcessors;

    public BrowseProcessorManager(Context context, OnItemReady onItemReady) {
        this(context, onItemReady, null);
    }

    public BrowseProcessorManager(Context context, OnItemReady onItemReady, DuplicateRecommendationsProcessor.OnContinuationNeeded continuationCallback) {
        mProcessors = new ArrayList<>();
        mProcessors.add(new DeArrowProcessor(context, onItemReady));
        mProcessors.add(new UnlocalizedTitleProcessor(context, onItemReady));
        
        if (continuationCallback != null) {
            mProcessors.add(new DuplicateRecommendationsProcessor(context, onItemReady, continuationCallback));
        } else {
            mProcessors.add(new DuplicateRecommendationsProcessor(context, onItemReady));
        }
    }

    @Override
    public void process(VideoGroup videoGroup) {
        for (BrowseProcessor processor : mProcessors) {
            processor.process(videoGroup);
        }
    }

    @Override
    public void dispose() {
        for (BrowseProcessor processor : mProcessors) {
            processor.dispose();
        }
    }
}
