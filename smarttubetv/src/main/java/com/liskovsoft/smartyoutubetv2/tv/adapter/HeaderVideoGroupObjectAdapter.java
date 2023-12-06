package com.liskovsoft.smartyoutubetv2.tv.adapter;

import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public class HeaderVideoGroupObjectAdapter extends VideoGroupObjectAdapter {
    private Object mHeader;

    public HeaderVideoGroupObjectAdapter(VideoGroup videoGroup, Presenter presenter) {
        super(videoGroup, presenter);
    }

    public HeaderVideoGroupObjectAdapter(VideoGroup videoGroup, PresenterSelector presenter) {
        super(videoGroup, presenter);
    }

    public HeaderVideoGroupObjectAdapter(Presenter presenter) {
        super(presenter);
    }

    public HeaderVideoGroupObjectAdapter(PresenterSelector presenter) {
        super(presenter);
    }

    @Override
    public int size() {
        return super.size() + (mHeader != null ? 1 : 0);
    }

    @Override
    public Object get(int index) {
        if (index == 0 && mHeader != null) {
            return mHeader;
        }

        return super.get(mHeader != null ? index - 1 : index);
    }

    public void setHeader(Object header) {
        mHeader = header;
    }

    @Override
    public void notifyItemRangeChanged(int positionStart, int itemCount) {
        super.notifyItemRangeChanged(mHeader != null ? positionStart + 1 : positionStart, itemCount);
    }

    @Override
    protected void notifyItemRangeInserted(int positionStart, int itemCount) {
        super.notifyItemRangeInserted(mHeader != null ? positionStart + 1 : positionStart, itemCount);
    }

    @Override
    protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
        super.notifyItemRangeRemoved(mHeader != null ? positionStart + 1 : positionStart, itemCount);
    }
}
