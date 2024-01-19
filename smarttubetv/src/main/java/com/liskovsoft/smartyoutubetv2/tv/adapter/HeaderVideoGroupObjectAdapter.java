package com.liskovsoft.smartyoutubetv2.tv.adapter;

import android.text.TextUtils;

import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HeaderVideoGroupObjectAdapter extends VideoGroupObjectAdapter {
    private Object mHeader;
    private List<Video> mAllItems;
    private List<VideoGroup> mAllGroups; // keep away from garbage collector

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

    @Override
    public int indexOf(Video item) {
        int index = super.indexOf(item);
        return mHeader != null && index != -1 ? index + 1 : index;
    }

    @Override
    public int indexOfAlt(Video item) {
        int index = super.indexOfAlt(item);
        return mHeader != null && index != -1 ? index + 1 : index;
    }

    @Override
    public void clear() {
        super.clear();

        mAllItems = null;
        mAllGroups = null;
    }

    public void setHeader(Object header) {
        if (header == null && mHeader != null) {
            notifyItemRangeRemoved(0, 1);
        }

        mHeader = header;
    }

    public Object getHeader() {
        return mHeader;
    }

    public void filter(String text) {
        if (mAllItems == null) {
            mAllItems = new ArrayList<>(getAll());
            mAllGroups = new ArrayList<>(getAllGroups());
        }

        super.clear();

        if (TextUtils.isEmpty(text)) {
            add(mAllItems);
            return;
        }

        List<Video> result = Helpers.filter(mAllItems, video -> {
            if (text.length() > 1 || Helpers.isNumeric(text)) {
                return Helpers.contains(video.getTitle(), text);
            } else {
                return Helpers.startsWith(video.getTitle(), text);
            }
        });

        // Move 'started with text' channels to the top
        if (result != null && (text.length() > 1 || Helpers.isNumeric(text))) {
            Collections.sort(result, (o1, o2) -> {
                String title1 = o1.getTitle();
                String title2 = o2.getTitle();
                boolean starts1 = Helpers.startsWith(title1, text);
                boolean starts2 = Helpers.startsWith(title2, text);

                return starts1 == starts2 ? title1.compareTo(title2) : starts1 ? -1 : 1;
            });
        }

        add(result);
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
