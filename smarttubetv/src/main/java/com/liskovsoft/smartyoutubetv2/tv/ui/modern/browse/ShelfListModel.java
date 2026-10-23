package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shelves (rows) of a section.<br/>
 * Applies {@link VideoGroup} actions with the same semantics as the classic rows fragment.
 */
public class ShelfListModel {
    private static final Pattern VERTICAL_THUMB = Pattern.compile("/(oar\\d*|oardefault|sardefault)\\.");
    private final PresenterSelector mCardPresenter;
    private final Listener mListener;
    private final List<Shelf> mShelves = new ArrayList<>();
    private final Map<Integer, VideoGroupObjectAdapter> mAdapters = new HashMap<>();

    public interface Listener {
        void onShelfInserted(int position);
        void onShelfRemoved(int position);
        void onShelvesCleared();
    }

    public static class Shelf {
        private final String mTitle;
        private final boolean mIsShorts;
        private final VideoGroupObjectAdapter mAdapter;

        private Shelf(String title, boolean isShorts, VideoGroupObjectAdapter adapter) {
            mTitle = title;
            mIsShorts = isShorts;
            mAdapter = adapter;
        }

        public String getTitle() {
            return mTitle;
        }

        public boolean isShorts() {
            return mIsShorts;
        }

        public VideoGroupObjectAdapter getAdapter() {
            return mAdapter;
        }
    }

    public ShelfListModel(PresenterSelector cardPresenter, Listener listener) {
        mCardPresenter = cardPresenter;
        mListener = listener;
    }

    public void apply(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear();
            } else {
                removeById(group.getId());
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            VideoGroupObjectAdapter adapter = mAdapters.get(group.getId());
            if (adapter != null) {
                adapter.remove(group);
            }
            return;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            VideoGroupObjectAdapter adapter = mAdapters.get(group.getId());
            if (adapter != null) {
                adapter.removeAuthor(group);
            }
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            VideoGroupObjectAdapter adapter = mAdapters.get(group.getId());
            if (adapter != null) {
                adapter.sync(group);
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        VideoGroupObjectAdapter existing = GridFragmentHelper.findRelatedAdapter(mAdapters, group, freeze -> {});

        if (existing != null) {
            existing.add(group); // continue
            return;
        }

        VideoGroupObjectAdapter adapter = new VideoGroupObjectAdapter(group, mCardPresenter);
        mAdapters.put(group.getId(), adapter);
        Shelf shelf = new Shelf(group.getTitle(), isShorts(group), adapter);

        int position = group.getPosition();
        if (position == -1 || position > mShelves.size()) {
            position = mShelves.size();
        }

        mShelves.add(position, shelf);
        mListener.onShelfInserted(position);
    }

    /**
     * Some sources (search) don't flag their shorts: vertical thumbnails tell them apart.
     */
    static boolean isShorts(VideoGroup group) {
        if (group.isShorts()) {
            return true;
        }

        List<Video> videos = group.getVideos();

        if (videos == null || videos.isEmpty()) {
            return false;
        }

        for (int i = 0; i < Math.min(8, videos.size()); i++) {
            String url = videos.get(i).cardImageUrl;
            if (url == null || !VERTICAL_THUMB.matcher(url).find()) {
                return false;
            }
        }

        return true;
    }

    public void clear() {
        mShelves.clear();
        mAdapters.clear();
        mListener.onShelvesCleared();
    }

    public int size() {
        return mShelves.size();
    }

    public Shelf get(int position) {
        return mShelves.get(position);
    }

    public boolean isEmpty() {
        return mShelves.isEmpty();
    }

    public boolean isNearEnd(int shelfPosition, int itemIndex) {
        if (shelfPosition < 0 || shelfPosition >= mShelves.size()) {
            return false;
        }

        return itemIndex > mShelves.get(shelfPosition).getAdapter().size() - ViewUtil.ROW_SCROLL_CONTINUE_NUM;
    }

    private void removeById(int id) {
        VideoGroupObjectAdapter adapter = mAdapters.remove(id);

        if (adapter == null) {
            return;
        }

        for (int i = 0; i < mShelves.size(); i++) {
            if (mShelves.get(i).getAdapter() == adapter) {
                mShelves.remove(i);
                mListener.onShelfRemoved(i);
                return;
            }
        }
    }
}
