package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.os.Bundle;
import android.widget.Toast;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;

import java.util.HashMap;
import java.util.Map;

public class MultiRowFragment extends RowsSupportFragment {
    private final ArrayObjectAdapter mRowsAdapter;
    private final Map<Integer, VideoGroupObjectAdapter> mMediaGroupAdapters;

    public MultiRowFragment() {
        mMediaGroupAdapters = new HashMap<>();
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        setAdapter(mRowsAdapter);

        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            Toast.makeText(getActivity(), "Implement click handler", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
    }

    public void updateRow(VideoGroup group) {
        HeaderItem rowHeader = new HeaderItem(group.getTitle());
        int mediaGroupId = group.getId(); // Create unique int from category.

        VideoGroupObjectAdapter existingAdapter = mMediaGroupAdapters.get(mediaGroupId);

        if (existingAdapter == null) {
            VideoGroupObjectAdapter mediaGroupAdapter = new VideoGroupObjectAdapter(group);

            mMediaGroupAdapters.put(mediaGroupId, mediaGroupAdapter);

            ListRow row = new ListRow(rowHeader, mediaGroupAdapter);
            mRowsAdapter.add(row);
        } else {
            existingAdapter.append(group); // continue row
        }
    }
}
