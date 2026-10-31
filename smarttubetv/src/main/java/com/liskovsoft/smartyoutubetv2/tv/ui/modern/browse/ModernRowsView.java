package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
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
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTiles;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernText;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernVideoCard;

/**
 * Vertical list of shelves. The focused shelf is aligned to the top and shows bigger tiles,
 * the focused tile is aligned to the content start.
 */
@SuppressLint("ViewConstructor") // created from code with its presenter, never inflated from a layout
public class ModernRowsView extends FrameLayout implements ModernSection, ShelfListModel.Listener {
    private final VerticalGridView mShelvesView;
    // Cards are shared by all the shelves: a new shelf reuses cards instead of inflating them
    private final RecyclerView.RecycledViewPool mCardPool = new RecyclerView.RecycledViewPool();
    private final ShelfListModel mModel;
    private final ShelvesAdapter mAdapter = new ShelvesAdapter();
    private final VideoGroupPresenter mPresenter;
    private int mContentLeft;
    private boolean mEnlargeFocusedShelf = true;
    private final int mTileGap;
    private final int mFocusedWidth;
    private final int mFocusedThumbHeight;
    private final int mWidth;
    private final int mThumbHeight;
    private final int mTextArea;
    private final int mTileTopMargin;
    private final int mChannelSize;
    private final int mShortsWidth;
    private final int mShortsHeight;
    private final int mHeaderFocusedColor;
    private final int mHeaderColor;
    private int mFocusedShelf = 0;
    private int mPendingPosition = -1;

    public ModernRowsView(Context context, VideoGroupPresenter presenter, PresenterSelector cardPresenter) {
        super(context);

        mPresenter = presenter;
        mModel = new ShelfListModel(cardPresenter, this);
        // Shelves scrolled above the content top never show under the top bar
        setClipChildren(true);

        Resources res = getResources();
        mContentLeft = res.getDimensionPixelSize(R.dimen.modern_content_left);
        mTileGap = res.getDimensionPixelSize(R.dimen.modern_tile_gap);
        mFocusedWidth = ModernTiles.size(context, R.dimen.modern_tile_width_focused_row);
        mFocusedThumbHeight = ModernTiles.size(context, R.dimen.modern_tile_thumb_height_focused_row);
        mWidth = ModernTiles.size(context, R.dimen.modern_tile_width_row);
        mThumbHeight = ModernTiles.size(context, R.dimen.modern_tile_thumb_height_row);
        mTextArea = res.getDimensionPixelSize(R.dimen.modern_tile_text_area);
        mTileTopMargin = res.getDimensionPixelSize(R.dimen.modern_tile_top_margin);
        mChannelSize = ModernTiles.size(context, R.dimen.modern_channel_tile_size);
        mShortsWidth = ModernTiles.size(context, R.dimen.modern_shorts_tile_width);
        mShortsHeight = ModernTiles.size(context, R.dimen.modern_shorts_tile_height);
        mHeaderFocusedColor = ContextCompat.getColor(context, R.color.modern_text_primary);
        mHeaderColor = ContextCompat.getColor(context, R.color.modern_text_header_unfocused);

        mCardPool.setMaxRecycledViews(0, 30);
        mShelvesView = new VerticalGridView(context);
        mShelvesView.setClipChildren(false);
        mShelvesView.setVerticalSpacing(res.getDimensionPixelSize(R.dimen.modern_shelf_spacing));
        mShelvesView.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
        mShelvesView.setWindowAlignmentOffset(0);
        mShelvesView.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        mShelvesView.setItemAlignmentOffset(0);
        mShelvesView.setItemAlignmentOffsetPercent(0);
        mShelvesView.setAdapter(mAdapter);
        mShelvesView.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                setFocusedShelf(position);
            }
        });

        addView(mShelvesView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public View getView() {
        return this;
    }

    /**
     * Start of shelf titles and tiles. Call before the first update.
     */
    public void setContentLeft(int contentLeft) {
        mContentLeft = contentLeft;
    }

    /**
     * Pages without the navigation rail keep the same tile size in every shelf.
     */
    public void setEnlargeFocusedShelf(boolean enlarge) {
        mEnlargeFocusedShelf = enlarge;
    }

    @Override
    public void update(VideoGroup group) {
        mModel.apply(group);

        if (mPendingPosition != -1 && mPendingPosition < mModel.size()) {
            setPosition(mPendingPosition);
        }
    }

    @Override
    public void clear() {
        mModel.clear();
        mFocusedShelf = 0;
    }

    @Override
    public boolean isEmpty() {
        return mModel.isEmpty();
    }

    @Override
    public int getPosition() {
        return mShelvesView.getSelectedPosition();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        if (index < mModel.size()) {
            mShelvesView.setSelectedPosition(index);
            mPendingPosition = -1;
        } else {
            mPendingPosition = index;
        }
    }

    @Override
    public void selectItem(Video item) {
        // NOP: rows restore the shelf position only (same as classic rows)
    }

    @Override
    public boolean focusContent() {
        return mShelvesView.requestFocus();
    }

    @Override
    public void onShelfInserted(int position) {
        mAdapter.notifyItemInserted(position);
    }

    @Override
    public void onShelfRemoved(int position) {
        mAdapter.notifyItemRemoved(position);
    }

    @SuppressLint("NotifyDataSetChanged") // every shelf is gone
    @Override
    public void onShelvesCleared() {
        mAdapter.notifyDataSetChanged();
    }

    private void setFocusedShelf(int position) {
        if (position == mFocusedShelf) {
            return;
        }

        int previous = mFocusedShelf;
        mFocusedShelf = position;
        applyShelfState(previous);
        applyShelfState(position);
    }

    private void applyShelfState(int position) {
        RecyclerView.ViewHolder holder = mShelvesView.findViewHolderForAdapterPosition(position);

        if (holder instanceof ShelfViewHolder) {
            ((ShelfViewHolder) holder).applyFocusedState(position == mFocusedShelf);
        }
    }

    private void applyCardSize(View view, boolean focusedShelf, boolean shorts) {
        focusedShelf = focusedShelf && mEnlargeFocusedShelf;

        if (view instanceof ModernVideoCard) {
            ModernVideoCard card = (ModernVideoCard) view;
            int width = shorts ? mShortsWidth : focusedShelf ? mFocusedWidth : mWidth;
            int thumbHeight = shorts ? mShortsHeight : focusedShelf ? mFocusedThumbHeight : mThumbHeight;
            card.setThumbSize(width, thumbHeight);
            ViewGroup.LayoutParams params = card.getLayoutParams();
            if (params != null) {
                params.height = card.getThumbHeight() + mTextArea;
                card.setLayoutParams(params);
            }
        }
    }

    private final class ShelvesAdapter extends RecyclerView.Adapter<ShelfViewHolder> {
        @NonNull
        @Override
        public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.modern_shelf, parent, false);
            return new ShelfViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
            holder.bind(mModel.get(position), position);
        }

        @Override
        public void onViewRecycled(@NonNull ShelfViewHolder holder) {
            // Give the cards back to the shared pool
            holder.mItems.setAdapter(null);
        }

        @Override
        public int getItemCount() {
            return mModel.size();
        }
    }

    private final class ShelfViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final HorizontalGridView mItems;
        private boolean mIsFocused;
        private boolean mIsShorts;
        private int mShelfPosition;

        ShelfViewHolder(View view) {
            super(view);
            mTitle = view.findViewById(R.id.modern_shelf_title);
            mItems = view.findViewById(R.id.modern_shelf_items);
            mItems.setHorizontalSpacing(mTileGap);
            mItems.setRecycledViewPool(mCardPool);
            // The row is clipped a little before the content start: room for the focus ring,
            // previous tiles never slide under the navigation rail
            int clipRoom = getResources().getDimensionPixelSize(R.dimen.modern_tile_clip_room);
            mItems.setPadding(clipRoom, mItems.getPaddingTop(), mItems.getPaddingRight(), mItems.getPaddingBottom());
            ViewGroup.MarginLayoutParams itemsParams = (ViewGroup.MarginLayoutParams) mItems.getLayoutParams();
            itemsParams.leftMargin = mContentLeft - clipRoom;
            mItems.setLayoutParams(itemsParams);
            ViewGroup.MarginLayoutParams titleParams = (ViewGroup.MarginLayoutParams) mTitle.getLayoutParams();
            titleParams.leftMargin = mContentLeft;
            titleParams.setMarginStart(mContentLeft);
            mTitle.setLayoutParams(titleParams);
            mItems.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
            mItems.setWindowAlignmentOffset(clipRoom);
            mItems.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            mItems.setItemAlignmentOffset(0);
            mItems.setItemAlignmentOffsetPercent(0);
            mItems.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
                @Override
                public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                    onItemSelected(position);
                }
            });
        }

        void bind(ShelfListModel.Shelf shelf, int position) {
            mShelfPosition = position;
            mIsShorts = shelf.isShorts();
            mTitle.setText(ModernText.sentenceCase(shelf.getTitle()));
            mTitle.setVisibility(shelf.getTitle() == null ? View.GONE : View.VISIBLE);

            ItemBridgeAdapter bridge = new ItemBridgeAdapter(shelf.getAdapter());
            bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
                @Override
                public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
                    applyCardSize(viewHolder.itemView, mIsFocused, mIsShorts);
                    viewHolder.itemView.setOnClickListener(v -> mPresenter.onVideoItemClicked((Video) viewHolder.getItem()));
                }
            });
            mItems.setAdapter(bridge);
            applyFocusedState(position == mFocusedShelf);
        }

        void applyFocusedState(boolean focused) {
            mIsFocused = focused;
            mTitle.setTextColor(focused ? mHeaderFocusedColor : mHeaderColor);

            boolean big = focused && mEnlargeFocusedShelf;
            int rowHeight = (mIsShorts ? mShortsHeight : isChannelShelf() ? mChannelSize : big ? mFocusedThumbHeight : mThumbHeight) + mTextArea;
            mItems.setRowHeight(rowHeight);
            ViewGroup.LayoutParams params = mItems.getLayoutParams();
            params.height = rowHeight + mTileTopMargin;
            mItems.setLayoutParams(params);

            for (int i = 0; i < mItems.getChildCount(); i++) {
                applyCardSize(mItems.getChildAt(i), focused, mIsShorts);
            }
        }

        private boolean isChannelShelf() {
            int position = getAdapterPosition();

            if (position == RecyclerView.NO_POSITION || position >= mModel.size()) {
                return false;
            }

            VideoGroupObjectAdapter adapter = mModel.get(position).getAdapter();
            return adapter.size() > 0 && ModernVideoCard.kindOf((Video) adapter.get(0)) == ModernVideoCard.KIND_CHANNEL;
        }

        private void onItemSelected(int position) {
            int shelfPosition = getAdapterPosition();

            if (shelfPosition == RecyclerView.NO_POSITION || shelfPosition >= mModel.size() || !mItems.hasFocus()) {
                return;
            }

            ShelfListModel.Shelf shelf = mModel.get(shelfPosition);

            if (position < 0 || position >= shelf.getAdapter().size()) {
                return;
            }

            Video video = (Video) shelf.getAdapter().get(position);
            mPresenter.onVideoItemSelected(video);

            if (mModel.isNearEnd(shelfPosition, position)) {
                mPresenter.onScrollEnd((Video) shelf.getAdapter().get(shelf.getAdapter().size() - 1));
            }
        }
    }
}
