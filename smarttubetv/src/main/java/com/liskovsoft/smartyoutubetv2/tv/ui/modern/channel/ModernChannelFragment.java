package com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.SinglePresenterSelector;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernRowsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernCardPresenter;

/**
 * Channel page: channel artwork in the background, header with actions, shelves below.
 */
public class ModernChannelFragment extends Fragment implements ChannelView {
    private LinearLayout mActions;
    private ChannelPresenter mPresenter;
    private CrashRestorer mCrashRestorer;
    private View mPage;
    private View mHeader;
    private ImageView mArt;
    private ImageView mAvatar;
    private TextView mName;
    private TextView mInfo;
    private ModernRowsView mRows;
    private View mProgress;
    private Video mFirstVideo;
    private boolean mIsFragmentCreated;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusListener = (oldFocus, newFocus) -> onFocusMoved(newFocus);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null);
        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mPresenter = ChannelPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.modern_channel, container, false);
        mPage = root.findViewById(R.id.modern_channel_page);
        mHeader = root.findViewById(R.id.modern_channel_header);
        mArt = root.findViewById(R.id.modern_channel_art);
        mAvatar = root.findViewById(R.id.modern_channel_avatar);
        mName = root.findViewById(R.id.modern_channel_name);
        mInfo = root.findViewById(R.id.modern_channel_info);
        mProgress = root.findViewById(R.id.modern_channel_progress);
        ModernSpinner.apply((ProgressBar) mProgress);

        ModernCardPresenter cardPresenter = new ModernCardPresenter();
        cardPresenter.setOnItemViewLongPressedListener((viewHolder, item) -> {
            if (item instanceof Video) {
                mPresenter.onVideoItemLongClicked((Video) item);
            }
        });
        mRows = new ModernRowsView(requireContext(), mPresenter, new SinglePresenterSelector(cardPresenter));
        mRows.setContentLeft(getResources().getDimensionPixelSize(R.dimen.modern_channel_left));
        mRows.setEnlargeFocusedShelf(false);
        ((FrameLayout) root.findViewById(R.id.modern_channel_rows)).addView(mRows);

        LinearLayout actions = root.findViewById(R.id.modern_channel_actions);
        mActions = actions;
        addAction(inflater, actions, R.drawable.modern_ic_play_arrow_filled, R.string.play_video).setOnClickListener(v -> {
            if (mFirstVideo != null) {
                mPresenter.onVideoItemClicked(mFirstVideo);
            }
        });
        addAction(inflater, actions, R.drawable.modern_ic_notifications, R.string.action_subscribe).setOnClickListener(v -> {
            if (mPresenter.getChannel() != null) {
                VideoMenuPresenter.instance(getContext()).showMenu(mPresenter.getChannel());
            }
        });

        // UI setting: search inside the channel and sort its videos, like the classic channel header
        if (MainUIData.instance(getContext()).isChannelSearchBarEnabled()) {
            addAction(inflater, actions, R.drawable.modern_ic_search, R.string.lb_search_bar_hint).setOnClickListener(v ->
                    SimpleEditDialog.show(getContext(), getString(R.string.lb_search_bar_hint), null, query -> {
                        if (!TextUtils.isEmpty(query)) {
                            mPresenter.onSearchSubmit(query);
                        }
                        return true;
                    }));
            addAction(inflater, actions, R.drawable.modern_ic_swap_vert, R.string.search_sorting).setOnClickListener(v -> mPresenter.onSearchSettingsClicked());
        }

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusListener);
        mPresenter.onViewInitialized();
        mCrashRestorer.restoreHeader((idx, video) -> setPosition(idx));
        mCrashRestorer.restorePlayback();
        updateHeader();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mCrashRestorer.persistHeaderIndex(outState, mRows.getPosition());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onDestroyView() {
        if (getView() != null) {
            getView().getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusListener);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    // ChannelView

    @Override
    public void update(VideoGroup videoGroup) {
        mRows.update(videoGroup);

        if (mFirstVideo == null && videoGroup != null && !videoGroup.isEmpty()) {
            mFirstVideo = videoGroup.get(0);
            Glide.with(this).load(mFirstVideo.getCardImageUrl()).into(mArt);
        }

        updateHeader();
    }

    @Override
    public void setPosition(int index) {
        mRows.setPosition(index);
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mProgress != null) {
            mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void clear() {
        mFirstVideo = null;
        mRows.clear();
        mArt.setImageDrawable(null);
    }

    // Internal

    private void updateHeader() {
        Video channel = mPresenter.getChannel();

        if (channel == null) {
            String id = mPresenter.getChannelId();
            mName.setText(Helpers.startsWith(id, "@") ? id : null);
            return;
        }

        mName.setText(Helpers.firstNonNull(channel.getAuthor(), channel.getTitle()));
        mInfo.setText(channel.subscriberCount);
        mInfo.setVisibility(TextUtils.isEmpty(channel.subscriberCount) ? View.GONE : View.VISIBLE);

        if (channel.getCardImageUrl() != null) {
            Glide.with(this).load(channel.getCardImageUrl()).transform(new CircleCrop()).into(mAvatar);
        }
    }

    private static View addAction(LayoutInflater inflater, LinearLayout parent, int iconResId, int textResId) {
        View pill = inflater.inflate(R.layout.modern_channel_pill, parent, false);
        ((ImageView) pill.findViewById(R.id.modern_pill_icon)).setImageResource(iconResId);
        ((TextView) pill.findViewById(R.id.modern_pill_text)).setText(textResId);
        parent.addView(pill);
        return pill;
    }

    /**
     * Up from the first shelf goes back to the header actions (the shelves don't let the focus out by themselves).
     * @return true if consumed
     */
    public boolean onUpKey() {
        if (mRows == null || mActions == null || !mRows.getView().hasFocus() || mRows.getPosition() > 0) {
            return false;
        }

        for (int i = 0; i < mActions.getChildCount(); i++) {
            View action = mActions.getChildAt(i);
            if (action.getVisibility() == View.VISIBLE && action.requestFocus()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Shelves scroll the page up and the header fades out, like the reference.
     */
    private void onFocusMoved(View focused) {
        if (mPage == null || focused == null) {
            return;
        }

        boolean inRows = isInside(focused, mRows);
        float target = inRows ? getResources().getDimension(R.dimen.modern_channel_rows_focused_top)
                - getResources().getDimension(R.dimen.modern_channel_rows_top) : 0;

        if (mPage.getTranslationY() != target) {
            mPage.animate().translationY(target).setDuration(ModernTheme.ANIM_SCROLL_MS).setInterpolator(ModernTheme.scrollInterpolator());
            mHeader.animate().alpha(inRows ? 0f : 1f).setDuration(ModernTheme.ANIM_FAST_MS);
            mArt.animate().alpha(inRows ? 0.4f : 1f).setDuration(ModernTheme.ANIM_SCROLL_MS);
        }
    }

    private static boolean isInside(View view, View parent) {
        View current = view;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }
}
