package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTiles;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernVideoCard;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.List;

/**
 * Grid section: all groups merged into one stream of tiles, three per row.
 */
@SuppressLint("ViewConstructor") // created from code with its presenter, never inflated from a layout
public class ModernGridView extends FrameLayout implements ModernSection {
    private static final float STALE_ALPHA = 0.5f;
    private final VerticalGridView mGridView;
    private final VideoGroupObjectAdapter mAdapter;
    private final VideoGroupPresenter mPresenter;
    private int mWidth;
    private int mThumbHeight;
    private final int mTextArea;
    private final int mGap;
    private final int mAvailableWidth;
    private int mColumns;
    private boolean mChannelGrid;
    private final SubscribedChannelsFilter mChannelsFilter = new SubscribedChannelsFilter();
    private int mPendingPosition = -1;
    private Video mPendingItem;
    private boolean mSoftReplace;
    private boolean mStale;

    public ModernGridView(Context context, VideoGroupPresenter presenter, PresenterSelector cardPresenter) {
        super(context);

        mPresenter = presenter;
        mAdapter = new VideoGroupObjectAdapter(cardPresenter);
        // The page clips the grid to its bounds: rows scrolled above the content top never show under the top bar
        setClipChildren(true);

        Resources res = getResources();
        mWidth = ModernTiles.size(context, R.dimen.modern_tile_width_row);
        mThumbHeight = ModernTiles.size(context, R.dimen.modern_tile_thumb_height_row);
        mTextArea = res.getDimensionPixelSize(R.dimen.modern_tile_text_area);
        int contentLeft = res.getDimensionPixelSize(R.dimen.modern_content_left);
        int gap = res.getDimensionPixelSize(R.dimen.modern_tile_gap);
        mGap = gap;
        // Columns that fit from the content start (side list pages move the grid there while it has the focus)
        mAvailableWidth = res.getDisplayMetrics().widthPixels - contentLeft - res.getDimensionPixelSize(R.dimen.modern_grid_end_margin);
        mColumns = ModernTiles.columns(mAvailableWidth, mWidth, gap);

        mGridView = new VerticalGridView(context);
        mGridView.setNumColumns(mColumns);
        // Columns as wide as the tiles: the space between them is the tile gap, not the leftover width
        mGridView.setColumnWidth(mWidth);
        // Tiles draw their focus ring outside of their bounds (the padding keeps room for the ring of the first row)
        mGridView.setClipChildren(false);
        mGridView.setClipToPadding(false);
        int topMargin = res.getDimensionPixelSize(R.dimen.modern_tile_top_margin);
        mGridView.setPadding(contentLeft, topMargin, 0, 0);
        mGridView.setHorizontalSpacing(gap);
        mGridView.setVerticalSpacing(res.getDimensionPixelSize(R.dimen.modern_shelf_spacing));
        mGridView.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
        // The focused row stays below the top padding: room for its focus ring
        mGridView.setWindowAlignmentOffset(topMargin);
        mGridView.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        mGridView.setItemAlignmentOffset(0);
        mGridView.setItemAlignmentOffsetPercent(0);

        ItemBridgeAdapter bridge = new ItemBridgeAdapter(mAdapter);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
                applyCardSize(viewHolder.itemView);
                viewHolder.itemView.setOnClickListener(v -> mPresenter.onVideoItemClicked((Video) viewHolder.getItem()));
            }
        });
        mGridView.setAdapter(bridge);
        mGridView.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                onItemSelected(position);
            }
        });

        addView(mGridView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public View getView() {
        return this;
    }

    /**
     * Vertical tiles, more columns.
     */
    public void setShorts(boolean shorts) {
        if (!shorts) {
            return;
        }

        mWidth = ModernTiles.size(getContext(), R.dimen.modern_shorts_tile_width);
        mThumbHeight = ModernTiles.size(getContext(), R.dimen.modern_shorts_tile_height);
        mColumns = ModernTiles.columns(mAvailableWidth, mWidth, mGap);
        mGridView.setNumColumns(mColumns);
        mGridView.setColumnWidth(mWidth);
    }

    /**
     * Width of the columns (tiles and the gaps between them).
     */
    public int getColumnsWidth() {
        return mColumns * mWidth + (mColumns - 1) * mGap;
    }

    /**
     * Start of the first column. Call before the first update.
     */
    public void setContentLeft(int contentLeft) {
        mGridView.setPadding(contentLeft, mGridView.getPaddingTop(), mGridView.getPaddingRight(), mGridView.getPaddingBottom());
    }

    /**
     * A replace keeps the old tiles, dimmed, until the new ones arrive (see {@link #onLoadFinished()}).
     */
    public void setSoftReplace(boolean soft) {
        mSoftReplace = soft;
    }

    @Override
    public void update(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (mSoftReplace && !isEmpty()) {
                markStale();
            } else {
                clear();
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            mAdapter.remove(group);
            return;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            mAdapter.removeAuthor(group);
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            mAdapter.sync(group);
            return;
        }

        if (action == VideoGroup.ACTION_REPLACE) {
            mChannelsFilter.reset();
        }

        // Subscribed channels (old look of the channels section): no "A-Z" divider, each channel once
        List<Video> videos = group.getVideos();
        if (videos != null && !videos.isEmpty() && ModernVideoCard.kindOf(videos.get(0)) == ModernVideoCard.KIND_CHANNEL) {
            mChannelsFilter.filter(videos);
        }

        if (group.isEmpty()) {
            return;
        }

        if (mStale) {
            mStale = false;
            mAdapter.clear();
            mGridView.setSelectedPosition(0);
            fadeIn();
        }

        if (isEmpty()) {
            applyContentKind(group);
        }

        mAdapter.add(group);
        restorePosition();
    }

    /**
     * A grid of channels (Channels section, old look) uses the round channel tiles: narrower columns, more of them.
     */
    private void applyContentKind(VideoGroup group) {
        Video first = group.getVideos() != null && !group.getVideos().isEmpty() ? group.getVideos().get(0) : null;

        if (first == null || ModernVideoCard.kindOf(first) != ModernVideoCard.KIND_CHANNEL || mChannelGrid) {
            return;
        }

        mChannelGrid = true;
        mWidth = ModernTiles.size(getContext(), R.dimen.modern_channel_tile_size);
        mThumbHeight = mWidth;
        mColumns = ModernTiles.columns(mAvailableWidth, mWidth, mGap);
        mGridView.setNumColumns(mColumns);
        mGridView.setColumnWidth(mWidth);
    }

    /**
     * Loading ended: drop the old tiles if nothing replaced them.
     */
    public void onLoadFinished() {
        if (mStale) {
            mStale = false;
            mAdapter.clear();
            fadeIn();
        }
    }

    @Override
    public void clear() {
        mStale = false;
        mGridView.animate().cancel();
        mGridView.setAlpha(1f);
        mAdapter.clear();
    }

    private void markStale() {
        mStale = true;
        mGridView.animate().alpha(STALE_ALPHA).setDuration(ModernTheme.ANIM_FAST_MS).setInterpolator(ModernTheme.fastInterpolator());
    }

    /**
     * The new tiles replace the dimmed ones in place and brighten: no blank frame between the two.
     */
    private void fadeIn() {
        mGridView.animate().cancel();
        mGridView.animate().alpha(1f).setDuration(ModernTheme.ANIM_FAST_MS).setInterpolator(ModernTheme.fadeInterpolator());
    }

    @Override
    public boolean isEmpty() {
        return mAdapter.size() == 0;
    }

    @Override
    public int getPosition() {
        return mGridView.getSelectedPosition();
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
        return mGridView.requestFocus();
    }

    private void restorePosition() {
        if (mPendingItem != null) {
            int index = mAdapter.indexOfAlt(mPendingItem);
            if (index != -1) {
                mGridView.setSelectedPosition(index);
                mPendingItem = null;
            }
        } else if (mPendingPosition >= 0 && mPendingPosition < mAdapter.size()) {
            mGridView.setSelectedPosition(mPendingPosition);
            mPendingPosition = -1;
        }
    }

    private void applyCardSize(View view) {
        if (view instanceof ModernVideoCard) {
            ModernVideoCard card = (ModernVideoCard) view;
            card.setThumbSize(mWidth, mThumbHeight);
            ViewGroup.LayoutParams params = card.getLayoutParams();
            if (params != null) {
                params.height = card.getThumbHeight() + mTextArea;
                card.setLayoutParams(params);
            }
        }
    }

    private void onItemSelected(int position) {
        if (position < 0 || position >= mAdapter.size() || !mGridView.hasFocus()) {
            return;
        }

        mPresenter.onVideoItemSelected((Video) mAdapter.get(position));

        if (position > mAdapter.size() - ViewUtil.GRID_SCROLL_CONTINUE_NUM) {
            mPresenter.onScrollEnd((Video) mAdapter.get(mAdapter.size() - 1));
        }
    }
}
