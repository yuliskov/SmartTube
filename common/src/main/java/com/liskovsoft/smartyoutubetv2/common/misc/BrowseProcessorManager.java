package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroupProcessor;

import java.util.ArrayList;

public class BrowseProcessorManager implements BrowseProcessor {
    private final ArrayList<BrowseProcessor> mProcessors;

    public BrowseProcessorManager(Context context, OnItemReady onItemReady) {
        mProcessors = new ArrayList<>();
        mProcessors.add(new DeArrowProcessor(context, onItemReady));
        //mProcessors.add(new ChannelGroupProcessor(context, onItemReady));
    }

    @Override
    public void process(VideoGroup videoGroup) {
        for (BrowseProcessor processor : mProcessors) {
            processor.process(videoGroup);
        }
    }
}
