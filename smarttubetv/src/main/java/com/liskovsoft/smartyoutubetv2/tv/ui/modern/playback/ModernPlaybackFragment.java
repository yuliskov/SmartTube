package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.FormatInfoPrefetcher;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue;

import java.util.List;

/**
 * Player with the modern controls overlay. Playback logic is inherited unchanged.
 */
public class ModernPlaybackFragment extends PlaybackFragment {
    private static final long FOCUS_PLAY_ON_SHOW_MS = 1_000;
    private final ViewTreeObserver.OnGlobalLayoutListener mStyleProgress = this::styleOverlay;
    private final OnChildViewHolderSelectedListener mRowSelected = new OnChildViewHolderSelectedListener() {
        @Override
        public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
            showControlsRow(position);
        }
    };
    private VerticalGridView mRowsGrid;
    private ModernVideoPlayerGlue mGlue;
    private ModernShortsOverlay mShorts;
    private Video mVideo;
    private boolean mIsShortsMode;
    private ImageView mPoster;
    private String mPosterVideoId;
    /**
     * The controls just appeared: the base row presenter moves the focus to the progress bar, the reference keeps it on play/pause.
     */
    private boolean mFocusPlayOnShow;
    private final Runnable mEndFocusPlayOnShow = () -> mFocusPlayOnShow = false;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChanged = this::onFocusChanged;

    @Override
    protected VideoPlayerGlue onCreatePlayerGlue(Context context, PlayerAdapter playerAdapter, VideoPlayerGlue.OnActionClickedListener actionListener) {
        mGlue = new ModernVideoPlayerGlue(context, playerAdapter, actionListener);
        return mGlue;
    }

    @Override
    protected LongClickPresenter onCreateCardPresenter() {
        return new ModernCardPresenter(R.dimen.modern_player_tile_width, R.dimen.modern_player_tile_thumb_height);
    }

    @Override
    protected LongClickPresenter onCreateShortsPresenter() {
        return onCreateCardPresenter();
    }

    /**
     * Chapters are many and short: smaller tiles, more of them on screen.
     */
    @Override
    protected LongClickPresenter onCreateChaptersPresenter() {
        return new ModernCardPresenter(R.dimen.modern_player_chapter_width, R.dimen.modern_player_chapter_thumb_height);
    }

    @Override
    protected int getRowFocusZoomFactor() {
        return FocusHighlight.ZOOM_FACTOR_NONE;
    }

    @Override
    protected boolean isRowFocusDimmerEnabled() {
        return false;
    }

    @Override
    protected void onRowPresenterCreated(ListRowPresenter presenter) {
        // Item wrappers clip the focus ring drawn outside of the tile
        presenter.enableChildRoundedCorners(false);
        presenter.setShadowEnabled(false);
    }

    /**
     * Suggestions behave like home shelves: focused tile aligned to the start, focus ring not clipped.
     */
    @Override
    protected void onRowViewHolderCreated(RowPresenter.ViewHolder holder) {
        if (!(holder instanceof ListRowPresenter.ViewHolder)) {
            return;
        }

        HorizontalGridView grid = ((ListRowPresenter.ViewHolder) holder).getGridView();
        int margin = getResources().getDimensionPixelSize(R.dimen.modern_player_margin);
        grid.setPadding(margin, getResources().getDimensionPixelSize(R.dimen.modern_tile_top_margin), margin, grid.getPaddingBottom());
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setHorizontalSpacing(getResources().getDimensionPixelSize(R.dimen.modern_tile_gap));
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
        grid.setWindowAlignmentOffset(margin);
        grid.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        grid.setItemAlignmentOffset(0);
        grid.setItemAlignmentOffsetPercent(0);

        for (View parent = (View) grid.getParent(); parent instanceof ViewGroup && parent != holder.view.getParent(); parent = (View) parent.getParent()) {
            ((ViewGroup) parent).setClipChildren(false);
            ((ViewGroup) parent).setClipToPadding(false);
        }
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        boolean wasVisible = isControlsOverlayVisible();
        super.showControlsOverlay(runAnimation);

        // Like the reference, the controls always appear with the focus on play/pause
        // (after the key that showed them has been handled)
        if (!wasVisible && !mIsShortsMode && getView() != null) {
            mFocusPlayOnShow = true;
            getView().removeCallbacks(mEndFocusPlayOnShow);
            getView().postDelayed(mEndFocusPlayOnShow, FOCUS_PLAY_ON_SHOW_MS);
            getView().post(this::focusPlayPause);
        }
    }

    private void onFocusChanged(View oldFocus, View newFocus) {
        boolean onProgress = newFocus != null && newFocus.getId() == R.id.playback_progress;

        if (onProgress && mFocusPlayOnShow) {
            mFocusPlayOnShow = false;
            newFocus.post(this::focusPlayPause);
            return;
        }

        // Like the reference, the progress bar takes the accent color (red by default) while it's the seek target
        if (newFocus instanceof SeekBar || oldFocus instanceof SeekBar) {
            SeekBar seekBar = (SeekBar) (newFocus instanceof SeekBar ? newFocus : oldFocus);
            int color = onProgress ? ModernColorScheme.accent(seekBar.getContext()) : ContextCompat.getColor(seekBar.getContext(), R.color.modern_text_primary);
            seekBar.setProgressColor(color);
            seekBar.setKnobColor(color);
        }
    }

    private void focusPlayPause() {
        View playPause = findPlayPause(getView());

        if (playPause != null) {
            playPause.requestFocus();
        }
    }

    private static View findPlayPause(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return null;
        }

        if (Boolean.TRUE.equals(view.getTag(R.id.modern_play_pause))) {
            return view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findPlayPause(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    @Override
    protected int getControlsBackgroundResId() {
        return ModernColorScheme.drawable(requireContext(), R.attr.modernPlayerScrimControls, R.drawable.modern_player_scrim_controls);
    }

    @Override
    protected int getSuggestionsBackgroundResId() {
        return ModernColorScheme.drawable(requireContext(), R.attr.modernPlayerScrimSuggestions, R.drawable.modern_player_scrim_suggestions);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.getViewTreeObserver().addOnGlobalLayoutListener(mStyleProgress);
        view.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusChanged);

        // The player's loading spinner looks like the rest of the interface
        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                if (root.getChildAt(i) instanceof ProgressBar) {
                    ModernSpinner.apply((ProgressBar) root.getChildAt(i));
                }
            }
        }
    }

    // Shorts

    @Override
    public void setVideo(Video video) {
        super.setVideo(video);
        mVideo = video;
        updateShortsMode();
        showPoster(video);
    }

    @Override
    protected void onRenderedFirstFrame() {
        super.onRenderedFirstFrame();
        hidePoster();

        if (mIsShortsMode) {
            prefetchNextShort();
        }
    }

    /**
     * Shorts are swiped quickly: the next one is loaded while the current one plays.
     */
    private void prefetchNextShort() {
        if (mVideo == null || mVideo.getGroup() == null || mVideo.getGroup().getVideos() == null) {
            return;
        }

        List<Video> videos = mVideo.getGroup().getVideos();
        int index = videos.indexOf(mVideo);

        if (index != -1 && index + 1 < videos.size()) {
            FormatInfoPrefetcher.instance().prefetch(videos.get(index + 1).videoId);
        }
    }

    // Poster: the thumbnail covers the video area until the first frame, like the reference

    private void showPoster(Video video) {
        View root = getView();

        if (root == null || video == null || video.videoId == null || video.videoId.equals(mPosterVideoId)) {
            return;
        }

        mPosterVideoId = video.videoId;

        if (mPoster == null) {
            View surface = root.findViewById(R.id.surface_root);
            if (surface == null || !(surface.getParent() instanceof ViewGroup)) {
                return;
            }
            ViewGroup parent = (ViewGroup) surface.getParent();
            mPoster = new ImageView(root.getContext());
            mPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mPoster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            parent.addView(mPoster, parent.indexOfChild(surface) + 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            syncPosterBounds(surface);
        }

        mPoster.animate().cancel();
        mPoster.setAlpha(1f);
        mPoster.setVisibility(View.VISIBLE);
        Glide.with(mPoster).load(video.getCardImageUrl()).into(mPoster);
    }

    private void hidePoster() {
        if (mPoster == null || mPoster.getVisibility() != View.VISIBLE) {
            return;
        }

        mPoster.animate().alpha(0f).setDuration(ModernTheme.ANIM_FAST_MS).withEndAction(() -> {
            mPoster.setVisibility(View.GONE);
            Glide.with(mPoster).clear(mPoster);
        });
    }

    private void syncPosterBounds(View surface) {
        if (mPoster != null && surface.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams source = (FrameLayout.LayoutParams) surface.getLayoutParams();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((ViewGroup.MarginLayoutParams) source);
            params.gravity = source.gravity;
            mPoster.setLayoutParams(params);
        }
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        super.setChannelIcon(iconUrl);
        if (mShorts != null) {
            mShorts.setChannelIcon(iconUrl);
        }
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        super.setButtonState(buttonId, buttonState);
        if (mShorts != null && mIsShortsMode) {
            mShorts.refreshActions();
        }
    }

    /**
     * Arrow keys that behave like the reference:<br/>
     * up shows the controls too (the base one only moves the focus), unless up is set to skip to the previous video;<br/>
     * down from the progress bar goes back to play/pause (not to the button that happens to be below the knob).
     * @return true if consumed
     */
    public boolean dispatchNavigationKey(KeyEvent event) {
        if (mIsShortsMode || getView() == null) {
            return false;
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && !isControlsOverlayVisible()
                && !PlayerTweaksData.instance(getContext()).isQuickSkipVideosAltEnabled()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                notifyKey(event);
                showControlsOverlay(true);
            }
            return true;
        }

        View focus = getView().findFocus();
        View thumbs = getView().findViewById(R.id.thumbs_row_wrapper);
        boolean inSeek = thumbs != null && thumbs.getVisibility() == View.VISIBLE;

        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && focus != null && focus.getId() == R.id.playback_progress && !inSeek) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                notifyKey(event);
                focusPlayPause();
            }
            return true;
        }

        return false;
    }

    /**
     * The player controller still sees the key (e.g. it restarts the controls auto hide timeout).
     */
    private void notifyKey(KeyEvent event) {
        if (mGlue != null) {
            mGlue.onKey(getView(), event.getKeyCode(), event);
        }
    }

    /**
     * The shorts screen places the video itself: the gravity set around dialogs (fit the video next to a panel and back)
     * would move it out of its area.
     */
    @Override
    public void setVideoGravity(int gravity) {
        if (mIsShortsMode) {
            return;
        }

        super.setVideoGravity(gravity);
    }

    /**
     * Shorts screen handles the keys itself (no controls overlay).
     * @return true if consumed
     */
    public boolean dispatchShortsKey(KeyEvent event) {
        if (!mIsShortsMode || mShorts == null) {
            return false;
        }

        if (mShorts.isPanelFocused()) {
            return mShorts.onPanelKey(event);
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return false;
        }

        return mShorts.onVideoKey(event);
    }

    private void updateShortsMode() {
        boolean shorts = mVideo != null && (mVideo.isShorts || mVideo.belongsToShortsGroup());
        View root = getView();

        if (root == null) {
            return;
        }

        if (shorts && mShorts == null) {
            createShortsOverlay((ViewGroup) root);
        }

        mIsShortsMode = shorts;

        View surface = root.findViewById(R.id.surface_root);
        if (surface != null && surface.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
            if (shorts) {
                params.width = getResources().getDimensionPixelSize(R.dimen.modern_shorts_video_width);
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.START | Gravity.TOP;
                params.leftMargin = getResources().getDimensionPixelSize(R.dimen.modern_shorts_video_left);
            } else {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.CENTER;
                params.leftMargin = 0;
            }
            surface.setLayoutParams(params);
            syncPosterBounds(surface);
        }

        centerProgressBar((ViewGroup) root, shorts);

        if (mShorts != null) {
            mShorts.setVisibility(shorts ? View.VISIBLE : View.GONE);
            if (shorts) {
                hideControlsOverlay(false);
                mShorts.bind(mVideo);
                int left = getResources().getDimensionPixelSize(R.dimen.modern_shorts_video_left);
                // Before the first layout the root has no size yet
                int height = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
                mShorts.setVideoRect(left, 0, left + getResources().getDimensionPixelSize(R.dimen.modern_shorts_video_width), height);
            }
        }
    }

    /**
     * The loading spinner is centered on the screen: in shorts it follows the video area instead.
     */
    private void centerProgressBar(ViewGroup root, boolean shorts) {
        int videoCenter = getResources().getDimensionPixelSize(R.dimen.modern_shorts_video_left)
                + getResources().getDimensionPixelSize(R.dimen.modern_shorts_video_width) / 2;
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        float shift = shorts ? videoCenter - width / 2f : 0;

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ProgressBar) {
                child.setTranslationX(shift);
            }
        }
    }

    private void createShortsOverlay(ViewGroup root) {
        mShorts = ModernShortsOverlay.inflate(root);
        mShorts.setListener(new ModernShortsOverlay.Listener() {
            @Override
            public void onShortsAction(int actionId) {
                if (mGlue != null) {
                    mGlue.performAction(actionId);
                }
            }

            @Override
            public void onShortsNext() {
                if (mGlue != null) {
                    mGlue.next();
                }
            }

            @Override
            public void onShortsPrevious() {
                if (mGlue != null) {
                    mGlue.previous();
                }
            }

            @Override
            public void onShortsTogglePlayback() {
                if (mGlue != null) {
                    mGlue.togglePlayback();
                }
            }

            @Override
            public void onShortsMore() {
                if (mVideo != null) {
                    VideoMenuPresenter.instance(getContext()).showMenu(mVideo);
                }
            }

            @Override
            public boolean isShortsActionActive(int actionId) {
                return mGlue != null && mGlue.isActionActive(actionId);
            }

            @Override
            public long getShortsPositionMs() {
                return getPositionMs();
            }

            @Override
            public long getShortsDurationMs() {
                return getDurationMs();
            }

            @Override
            public boolean isShortsPlaying() {
                // Not isPlaying(): false while the next short loads, the play icon is only for a pause
                return getPlayWhenReady();
            }
        });
        // Above the video, below nothing: the controls overlay is not used for shorts
        root.addView(mShorts);
    }

    @Override
    public void onDestroyView() {
        if (getView() != null) {
            getView().getViewTreeObserver().removeOnGlobalLayoutListener(mStyleProgress);
            getView().getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusChanged);
            getView().removeCallbacks(mEndFocusPlayOnShow);
        }
        super.onDestroyView();
    }

    private void styleOverlay() {
        View root = getView();

        if (root == null) {
            return;
        }

        styleRowsGrid(root);
        styleDescription(root);
        styleProgress(root);
    }

    /**
     * Controls row starts at the top of the screen, suggestions peek from the bottom.
     */
    private void styleRowsGrid(View root) {
        View view = root.findViewById(R.id.container_list);

        if (!(view instanceof VerticalGridView)) {
            return;
        }

        if (view != mRowsGrid) {
            mRowsGrid = (VerticalGridView) view;
            mRowsGrid.addOnChildViewHolderSelectedListener(mRowSelected);
        }

        // The base fragment may restore its own alignment (and the rows their alpha) when the overlay is shown again
        alignRows(mRowsGrid);
        showControlsRow(mRowsGrid.getSelectedPosition());
    }

    @Override
    protected void setVerticalGridViewLayout(VerticalGridView listview) {
        if (listview != null) {
            alignRows(listview);
        }
    }

    /**
     * One window alignment for every row: suggestions sit in the middle of the screen,
     * the controls row reaches the top through its own item alignment (see {@link ModernVideoPlayerGlue}).
     * Nothing changes while scrolling, so the rows move in one smooth step.
     */
    private void alignRows(VerticalGridView grid) {
        int offset = getResources().getDimensionPixelSize(R.dimen.modern_player_suggestions_top);

        if (grid.getWindowAlignment() != BaseGridView.WINDOW_ALIGN_NO_EDGE || grid.getWindowAlignmentOffset() != offset) {
            grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
            grid.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            grid.setItemAlignmentOffset(0);
            grid.setItemAlignmentOffsetPercent(0);
            grid.setWindowAlignmentOffset(offset);
        }
    }

    /**
     * Like the reference, controls and title make room for the suggestions.
     */
    private void showControlsRow(int selectedPosition) {
        View root = getView();

        if (root == null) {
            return;
        }

        float alpha = selectedPosition <= 0 ? 1f : 0f;

        for (int id : new int[] {R.id.controls_card, R.id.transport_row, R.id.additional_info}) {
            View view = root.findViewById(id);
            // Self-correcting (also called on layout): only start an animation when the target changes
            if (view != null && view.getAlpha() != alpha && !Float.valueOf(alpha).equals(view.getTag(R.id.modern_action_group))) {
                view.setTag(R.id.modern_action_group, alpha);
                view.animate().cancel();
                view.animate().alpha(alpha).setDuration(ModernTheme.ANIM_FAST_MS)
                        .withEndAction(() -> view.setTag(R.id.modern_action_group, null));
            }
        }
    }

    /**
     * Title line height and margins are recomputed by the description presenter on every bind.
     */
    private void styleDescription(View root) {
        View title = root.findViewById(R.id.lb_details_description_title);
        View subtitle = root.findViewById(R.id.lb_details_description_subtitle);

        if (title instanceof TextView) {
            TextView titleView = (TextView) title;
            float extra = getResources().getDimensionPixelSize(R.dimen.modern_player_title_line_height) - titleView.getPaint().getFontMetricsInt(null);
            if (titleView.getLineSpacingExtra() != extra) {
                titleView.setLineSpacing(extra, 1f);
            }
            setTopMargin(titleView, 0);
        }

        if (subtitle != null) {
            setTopMargin(subtitle, getResources().getDimensionPixelSize(R.dimen.modern_player_meta_top));
        }

        // Metadata line: author, views, date and the SmartTube extras (likes / dislikes counter setting,
        // subscribers): two lines so the extras after the date are not cut
        if (subtitle instanceof TextView && ((TextView) subtitle).getMaxLines() != 2) {
            ((TextView) subtitle).setSingleLine(false);
            ((TextView) subtitle).setMaxLines(2);
            ((TextView) subtitle).setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
    }

    private static void setTopMargin(View view, int margin) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (params.topMargin != margin) {
                params.topMargin = margin;
                view.setLayoutParams(params);
            }
        }
    }

    /**
     * The progress bar is created by the controls row presenter. Style it once it exists.
     */
    private void styleProgress(View root) {
        View view = root.findViewById(R.id.playback_progress);

        if (!(view instanceof SeekBar) || Boolean.TRUE.equals(view.getTag(R.id.modern_action_group))) {
            return;
        }

        SeekBar seekBar = (SeekBar) view;
        seekBar.setTag(R.id.modern_action_group, Boolean.TRUE);
        seekBar.setBarHeight(getResources().getDimensionPixelSize(R.dimen.modern_player_bar_height));
        seekBar.setActiveBarHeight(getResources().getDimensionPixelSize(R.dimen.modern_player_bar_active_height));
        seekBar.setActiveRadius(getResources().getDimensionPixelSize(R.dimen.modern_player_knob_radius));
        seekBar.setTrackColor(ContextCompat.getColor(requireContext(), R.color.modern_white_30));
    }
}
