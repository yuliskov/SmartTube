package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.HeaderVideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * Channels: subscribed channels on the start side, uploads of the selected channel on the end side.<br/>
 * Groups of the first column are the channels, groups of the second one the uploads.
 */
@SuppressLint("ViewConstructor") // created from code with its presenter, never inflated from a layout
public class ModernChannelsView extends ModernSideListView {
    private final SubscribedChannelsFilter mChannelsFilter = new SubscribedChannelsFilter();
    private static final int COLUMN_CHANNELS = 0;
    private static final int COLUMN_UPLOADS = 1;
    /**
     * Fast scrolling through the channels doesn't load every one of them.
     */
    private static final long SELECT_DELAY_MS = 300;
    private final VideoGroupPresenter mPresenter;
    private final VerticalGridView mList;
    private final ChannelsAdapter mChannels;
    private final Object mFilterHeader = new Object();
    private String mFilter;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Video mSelectedChannel;
    private Video mShownChannel;
    private int mPendingPosition = -1;
    private Video mPendingItem;
    private final Runnable mLoadSelected = this::loadSelected;

    public ModernChannelsView(Context context, String title, VideoGroupPresenter presenter, PresenterSelector cardPresenter) {
        super(context, title, presenter, cardPresenter);
        mPresenter = presenter;

        ClassPresenterSelector presenters = new ClassPresenterSelector();
        presenters.addClassPresenter(Video.class, new ModernChannelItemPresenter());
        presenters.addClassPresenter(Object.class, new ModernChannelItemPresenter.IconItemPresenter(R.drawable.modern_ic_search));
        mChannels = new ChannelsAdapter(presenters);
        mList = new VerticalGridView(context);
        mList.setNumColumns(1);
        mList.setVerticalScrollBarEnabled(false);
        mList.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        mList.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ALIGNED);

        ItemBridgeAdapter bridge = new ItemBridgeAdapter(mChannels);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
                if (!(viewHolder.getItem() instanceof Video)) {
                    viewHolder.itemView.setOnClickListener(v -> showFilterDialog());
                    ((TextView) viewHolder.itemView.findViewById(R.id.modern_subs_name)).setText(
                            TextUtils.isEmpty(mFilter) ? getContext().getString(R.string.lb_search_bar_hint) : mFilter);
                    return;
                }

                Video channel = (Video) viewHolder.getItem();
                View view = viewHolder.itemView;
                view.setActivated(isSame(channel, mShownChannel));
                view.setOnClickListener(v -> onChannelClicked(channel));
                view.setOnLongClickListener(v -> {
                    mPresenter.onVideoItemLongClicked(channel);
                    return true;
                });
            }
        });
        mList.setAdapter(bridge);
        mList.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                onChannelSelected(position);
            }
        });
        setColumn(mList);
    }

    @Override
    public void update(VideoGroup group) {
        if (group.getPosition() == COLUMN_UPLOADS) {
            mGrid.update(group);
            return;
        }

        if (group.getPosition() != COLUMN_CHANNELS) {
            return;
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            // The uploads depend on the channels
            mChannelsFilter.reset();
            mChannels.clear();
            mGrid.clear();
            mShownChannel = null;
        } else if (action == VideoGroup.ACTION_REMOVE) {
            mChannels.remove(group);
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            mChannels.sync(group);
            return;
        }

        mChannelsFilter.filter(group.getVideos());

        if (group.isEmpty()) {
            return;
        }

        boolean first = isEmpty();
        mChannels.add(group);

        if (first && mChannels.getHeader() == null && MainUIData.instance(getContext()).isChannelsFilterEnabled()) {
            // UI setting: field that filters the channels by name, like the classic look
            mChannels.showHeader(mFilterHeader);
        }

        restorePosition();

        if (first) {
            showColumn();
            // The uploads of the first channel, like the classic look
            mSelectedChannel = channelAt(Math.max(mList.getSelectedPosition(), firstChannelIndex()));
            if (mList.getSelectedPosition() < firstChannelIndex()) {
                mList.setSelectedPosition(firstChannelIndex());
            }
            mHandler.post(mLoadSelected);
        }
    }

    @Override
    public void clear() {
        mHandler.removeCallbacks(mLoadSelected);
        mChannels.clear();
        mShownChannel = null;
        super.clear();
    }

    @Override
    public boolean isEmpty() {
        return mChannels.getAll().isEmpty();
    }

    @Override
    public int getPosition() {
        return mList.getSelectedPosition();
    }

    @Override
    public void setPosition(int index) {
        mPendingPosition = index;
        mPendingItem = null;
        restorePosition();
    }

    @Override
    public void selectItem(Video item) {
        mPendingItem = item;
        mPendingPosition = -1;
        restorePosition();
    }

    @Override
    public boolean focusContent() {
        return !isEmpty() ? mList.requestFocus() : mGrid.focusContent();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mLoadSelected);
    }

    private void restorePosition() {
        if (mPendingItem != null) {
            int index = mChannels.indexOfAlt(mPendingItem);
            if (index != -1) {
                mList.setSelectedPosition(index);
                mPendingItem = null;
            }
        } else if (mPendingPosition >= 0 && mPendingPosition < mChannels.size()) {
            mList.setSelectedPosition(mPendingPosition);
            mPendingPosition = -1;
        }
    }

    private void onChannelSelected(int position) {
        if (position < 0 || position >= mChannels.size() || !mList.hasFocus()) {
            return;
        }

        Video channel = channelAt(position);

        if (channel == null) {
            return;
        }

        mSelectedChannel = channel;
        mHandler.removeCallbacks(mLoadSelected);
        mHandler.postDelayed(mLoadSelected, SELECT_DELAY_MS);

        if (position > mChannels.size() - ViewUtil.GRID_SCROLL_CONTINUE_NUM && TextUtils.isEmpty(mFilter)) {
            mPresenter.onScrollEnd(channelAt(mChannels.size() - 1));
        }
    }

    private void onChannelClicked(Video channel) {
        mHandler.removeCallbacks(mLoadSelected);

        // Opens the channel with auto load, loads its uploads without
        if (!isAutoLoad()) {
            setShownChannel(channel);
        }

        mPresenter.onVideoItemClicked(channel);
    }

    private void loadSelected() {
        if (mSelectedChannel == null || isSame(mSelectedChannel, mShownChannel)) {
            return;
        }

        // Loads the uploads with auto load, clears them without
        setShownChannel(isAutoLoad() ? mSelectedChannel : null);
        mPresenter.onVideoItemSelected(mSelectedChannel);
    }

    @Override
    public void reloadShown() {
        if (mShownChannel == null) {
            return;
        }

        if (isAutoLoad()) {
            mPresenter.onVideoItemSelected(mShownChannel);
        } else {
            mPresenter.onVideoItemClicked(mShownChannel);
        }
    }

    private boolean isAutoLoad() {
        return MainUIData.instance(getContext()).isUploadsAutoLoadEnabled();
    }

    @Override
    protected boolean focusColumn() {
        return !isEmpty() && mList.requestFocus();
    }

    @Nullable
    private Video channelAt(int position) {
        Object item = position >= 0 && position < mChannels.size() ? mChannels.get(position) : null;
        return item instanceof Video ? (Video) item : null;
    }

    private int firstChannelIndex() {
        return mChannels.getHeader() != null ? 1 : 0;
    }

    private void showFilterDialog() {
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.lb_search_bar_hint), mFilter, text -> {
            mFilter = text;
            mChannels.filter(text);
            mChannels.refresh(); // the filter row shows the text
            return true;
        });
    }

    private void setShownChannel(Video channel) {
        mShownChannel = channel;

        for (int i = 0; i < mList.getChildCount(); i++) {
            View child = mList.getChildAt(i);
            RecyclerView.ViewHolder holder = mList.getChildViewHolder(child);
            if (holder instanceof ItemBridgeAdapter.ViewHolder && ((ItemBridgeAdapter.ViewHolder) holder).getItem() instanceof Video) {
                child.setActivated(isSame((Video) ((ItemBridgeAdapter.ViewHolder) holder).getItem(), channel));
            }
        }
    }

    private static boolean isSame(Video first, Video second) {
        return first != null && second != null && first.equals(second);
    }

    /**
     * The filter row is added (and its text changed) with a notification, unlike the base adapter.
     */
    private static final class ChannelsAdapter extends HeaderVideoGroupObjectAdapter {
        ChannelsAdapter(PresenterSelector presenter) {
            super(presenter);
        }

        void showHeader(Object header) {
            setHeader(header);
            notifyChanged();
        }

        void refresh() {
            notifyChanged();
        }
    }
}
