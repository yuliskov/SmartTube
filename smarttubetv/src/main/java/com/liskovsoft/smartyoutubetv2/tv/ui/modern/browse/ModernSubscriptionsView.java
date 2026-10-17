package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Subscriptions: title, column of subscribed channels on the start side, videos grid on the end side.<br/>
 * The grid shows the videos of the focused item of the column: "All" (the subscriptions feed) or one channel.
 */
@SuppressLint("ViewConstructor") // created from code with its presenter, never inflated from a layout
public class ModernSubscriptionsView extends ModernSideListView {
    private static final long COLUMN_FALLBACK_MS = 2_000;
    /**
     * Fast scrolling through the channels doesn't load every one of them.
     */
    private static final long SELECT_DELAY_MS = 300;
    private final BrowsePresenter mPresenter;
    private final VerticalGridView mList;
    private final ArrayObjectAdapter mItems;
    private final Object mAllItem = new Object();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mLoadSelected = this::loadSelected;
    private Object mSelected = mAllItem;
    private Object mShown = mAllItem;

    public ModernSubscriptionsView(Context context, String title, BrowsePresenter presenter, PresenterSelector cardPresenter) {
        super(context, title, presenter, cardPresenter);
        mPresenter = presenter;

        ClassPresenterSelector presenters = new ClassPresenterSelector();
        presenters.addClassPresenter(Video.class, new ModernChannelItemPresenter());
        presenters.addClassPresenter(Object.class, new ModernChannelItemPresenter.IconItemPresenter(R.drawable.modern_ic_subscriptions));
        mItems = new ArrayObjectAdapter(presenters);
        mItems.add(mAllItem);

        mList = new VerticalGridView(context);
        mList.setNumColumns(1);
        mList.setVerticalScrollBarEnabled(false);
        mList.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        mList.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ALIGNED);

        ItemBridgeAdapter bridge = new ItemBridgeAdapter(mItems);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
                Object item = viewHolder.getItem();
                View view = viewHolder.itemView;
                view.setActivated(item == mShown);

                if (item == mAllItem) {
                    ((TextView) view.findViewById(R.id.modern_subs_name)).setText(R.string.modern_all);
                    view.setOnClickListener(v -> mGrid.focusContent());
                    view.setOnLongClickListener(null);
                    return;
                }

                Video channel = (Video) item;
                view.setOnClickListener(v -> ChannelPresenter.instance(getContext()).openChannel(channel));
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
                onItemSelected(position);
            }
        });
        setColumn(mList);

        MediaServiceManager.instance().loadSubscribedChannels(this::addChannels);
    }

    private void addChannels(MediaGroup group) {
        if (group == null || group.getMediaItems() == null || getContext() == null) {
            showColumn();
            return;
        }

        SubscribedChannelsFilter filter = new SubscribedChannelsFilter();

        for (MediaItem mediaItem : group.getMediaItems()) {
            Video channel = Video.from(mediaItem);
            if (filter.accept(channel)) {
                mItems.add(channel);
            }
        }

        showColumn();
    }

    @Override
    public void update(VideoGroup group) {
        // The section was loaded again by the presenter (e.g. refresh): the grid shows the feed
        if (group.getAction() == VideoGroup.ACTION_REPLACE && mShown != mAllItem && isFeed(group)) {
            mHandler.removeCallbacks(mLoadSelected);
            mSelected = mAllItem;
            setShown(mAllItem);
        }

        mGrid.update(group);

        // The channels didn't load: at least the "All" item
        if (!mGrid.isEmpty() && !isColumnShown()) {
            postDelayed(this::showColumn, COLUMN_FALLBACK_MS);
        }
    }

    private static boolean isFeed(VideoGroup group) {
        return group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_SUBSCRIPTIONS;
    }

    private void onItemSelected(int position) {
        if (position < 0 || position >= mItems.size() || !mList.hasFocus()) {
            return;
        }

        mSelected = mItems.get(position);
        mHandler.removeCallbacks(mLoadSelected);
        mHandler.postDelayed(mLoadSelected, SELECT_DELAY_MS);
    }

    private void loadSelected() {
        if (mSelected == mShown) {
            return;
        }

        setShown(mSelected);
        mPresenter.showChannelInSection(mShown instanceof Video ? (Video) mShown : null);
    }

    private void setShown(Object item) {
        mShown = item;

        for (int i = 0; i < mList.getChildCount(); i++) {
            View child = mList.getChildAt(i);
            RecyclerView.ViewHolder holder = mList.getChildViewHolder(child);
            if (holder instanceof ItemBridgeAdapter.ViewHolder) {
                child.setActivated(((ItemBridgeAdapter.ViewHolder) holder).getItem() == item);
            }
        }
    }

    @Override
    public void reloadShown() {
        mPresenter.showChannelInSection(mShown instanceof Video ? (Video) mShown : null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mLoadSelected);
    }

    @Override
    protected boolean focusColumn() {
        return mList.requestFocus();
    }

    /**
     * Like the channels section: from the rail the focus goes to the list, right from the list to the videos.
     */
    @Override
    public boolean focusContent() {
        return isColumnShown() ? mList.requestFocus() : mGrid.focusContent();
    }
}
