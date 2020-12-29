package com.liskovsoft.smartyoutubetv2.tv.adapter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public class DeferredVideoGroupObjectAdapter extends VideoGroupObjectAdapter {
    private long mPrevAppendTimeMs;

    public DeferredVideoGroupObjectAdapter(VideoGroup group) {
        super(group);
    }

    @Override
    public void append(VideoGroup group) {
        long currentTimeMillis = System.currentTimeMillis();

        if (currentTimeMillis - mPrevAppendTimeMs < 3_000) {
            return;
        }

        mPrevAppendTimeMs = currentTimeMillis;

        super.append(group);
    }
}
