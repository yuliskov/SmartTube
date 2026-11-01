package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.SinglePresenterSelector;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.LanguageSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernStatePage;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernNavRail;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time.DateTimeView;

import java.util.List;

/**
 * Modern main screen: navigation rail, top search bar and the content of the selected section.
 */
public class ModernBrowseFragment extends Fragment implements BrowseView, ModernNavRail.Listener, AccountChangeListener {
    private static final int SECTION_SELECTED_DELAY_MS = 100;
    private final RailModel mRailModel = new RailModel();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private BrowsePresenter mPresenter;
    private ModernNavRail mRail;
    private FrameLayout mContent;
    private View mTopBar;
    private View mProgress;
    private ModernSection mSection;
    private View mErrorView;
    /**
     * Page with a list showing the state of its shown item in place of the videos.
     */
    private ModernSideListView mStateHost;
    private ModernAccountPickerView mAccountPicker;
    private SinglePresenterSelector mCardPresenter;
    private CrashRestorer mCrashRestorer;
    private int mSectionId = -1;
    private boolean mFocusOnContent;
    private boolean mIsFragmentCreated;
    private boolean mIsLoading;
    private int mPendingItemIndex = -1;
    private Video mPendingItem;
    private final Runnable mOnSectionSelected = this::onSectionSelected;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null);

        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mPresenter = BrowsePresenter.instance(getContext());
        mPresenter.setView(this);
        ModernCardPresenter cardPresenter = new ModernCardPresenter();
        cardPresenter.setOnItemViewLongPressedListener((viewHolder, item) -> {
            if (item instanceof Video) {
                mPresenter.onVideoItemLongClicked((Video) item);
            }
        });
        mCardPresenter = new SinglePresenterSelector(cardPresenter);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && routeKey(event.getKeyCode())) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };

        View frame = inflater.inflate(R.layout.modern_browse, root, false);
        root.addView(frame);
        mContent = frame.findViewById(R.id.modern_browse_content);
        mTopBar = frame.findViewById(R.id.modern_browse_top_bar);
        mProgress = frame.findViewById(R.id.modern_browse_progress);
        ModernSpinner.apply((ProgressBar) mProgress);
        ((DateTimeView) mTopBar.findViewById(R.id.modern_top_bar_clock)).showDate(false);
        DateTimeView date = mTopBar.findViewById(R.id.modern_top_bar_date);
        date.showTime(false);
        date.showDate(true);
        updateBadge();

        mRail = new ModernNavRail(requireContext());
        mRail.setListener(this);
        root.addView(mRail);

        View.OnClickListener search = v -> SearchPresenter.instance(getContext()).startSearch(null);
        mTopBar.findViewById(R.id.modern_top_bar_mic).setOnClickListener(search);
        mTopBar.findViewById(R.id.modern_top_bar_search).setOnClickListener(search);
        mTopBar.findViewById(R.id.modern_top_bar_language).setOnClickListener(v -> LanguageSettingsPresenter.instance(getContext()).show());
        TextView backgroundVideo = mTopBar.findViewById(R.id.modern_top_bar_background_video);
        TextViewCompat.setCompoundDrawableTintList(backgroundVideo, ContextCompat.getColorStateList(requireContext(), R.color.modern_search_text));
        backgroundVideo.setOnClickListener(v -> ViewManager.instance(getContext()).startView(PlaybackView.class));

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MediaServiceManager.instance().addAccountListener(this);
        updateAccount();

        mPresenter.onViewInitialized();

        mCrashRestorer.restoreHeader((idx, video) -> {
            selectSection(idx, true);
            selectSectionItem(video);
        });
        mCrashRestorer.restorePlayback();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mCrashRestorer.persistHeaderIndex(outState, mRailModel.indexOf(mSectionId));
        mCrashRestorer.persistVideo(outState, mPresenter.getCurrentVideo());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
        updateTopBar();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!mIsFragmentCreated) {
            mPresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MediaServiceManager.instance().removeAccountListener(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    /**
     * @return true if the back key was consumed
     */
    public boolean onBackPressed() {
        if (mAccountPicker != null) {
            hideAccountPicker();
            return true;
        }

        if (mRail != null && !mRail.hasFocus() && mRailModel.size() > 0) {
            mRail.focusSelected();
            return true;
        }

        return false;
    }

    // BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        int existing = mRailModel.indexOf(section.getId());

        if (existing != -1 && (index == -1 || existing == index)) {
            return;
        }

        mRailModel.add(index, section);
        mRail.setModel(mRailModel);
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        mRailModel.remove(section.getId());
        mRail.setModel(mRailModel);
    }

    @Override
    public void removeAllSections() {
        mRailModel.clear();
        mRail.setModel(mRailModel);
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || mRailModel.size() == 0) {
            return;
        }

        mFocusOnContent = focusOnContent;
        int fixedIndex = index < mRailModel.size() ? index : mRailModel.size() - 1;
        showSection(mRailModel.get(fixedIndex));
    }

    @Override
    public void updateSection(VideoGroup group) {
        // Fast scrolling in the rail: drop late results of a section that isn't shown anymore
        if (group != null && !isShown(group.getSection())) {
            return;
        }

        restoreSection();

        if (mSection != null) {
            boolean wasEmpty = mSection.isEmpty();
            mSection.update(group);
            applyPendingSelection();

            // The first content fades in instead of popping (side list pages fade their parts themselves)
            if (wasEmpty && !mSection.isEmpty() && !(mSection instanceof ModernSideListView)) {
                fadeIn(mSection.getView());
            }
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group != null && !isShown(group.getCategory())) {
            return;
        }

        restoreSection();

        if (mSection instanceof ModernSettingsView) {
            ((ModernSettingsView) mSection).update(group);
        }
    }

    @Override
    public void clearSection(BrowseSection section) {
        if (mSection != null && isShown(section)) {
            mSection.clear();
        }
    }

    @Override
    public void selectSectionItem(int index) {
        if (index < 0) {
            return;
        }

        mPendingItemIndex = index;
        mPendingItem = null;
        applyPendingSelection();
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item == null) {
            return;
        }

        mPendingItem = item;
        mPendingItemIndex = -1;
        applyPendingSelection();
    }

    @Override
    public void showError(ErrorFragmentData data) {
        showErrorView(data);
    }

    @Override
    public void showProgressBar(boolean show) {
        mHandler.post(() -> {
            mIsLoading = show;
            applyProgress();
        });
    }

    @Override
    public boolean isProgressBarShowing() {
        return mIsLoading;
    }

    @Override
    public void focusOnContent() {
        if (mErrorView != null) {
            ModernStatePage.focus(mErrorView);
        } else if (mSection != null) {
            mSection.focusContent();
        }
    }

    @Override
    public boolean isEmpty() {
        return mSection == null || mSection.isEmpty();
    }

    @Override
    public void updateBadge() {
        if (getContext() == null || mTopBar == null) {
            return;
        }

        // Started through a bridge app: its icon replaces the logo, like the classic title
        SplashPresenter splashPresenter = SplashPresenter.instance(getContext());
        Drawable bridgeIcon = Utils.getDrawable(getContext(), splashPresenter.getBridgePackageName(), "app_icon");
        ImageView logo = mTopBar.findViewById(R.id.modern_top_bar_logo);

        // Logo of the color scheme, none on the OLED schemes (like the classic interface)
        int logoResId = ModernColorScheme.logo(getContext());

        if (bridgeIcon != null) {
            logo.setImageDrawable(bridgeIcon);
        } else if (logoResId != 0) {
            logo.setImageResource(logoResId);
        }

        logo.setVisibility(bridgeIcon != null || logoResId != 0 ? View.VISIBLE : View.GONE);
    }

    // Rail

    @Override
    public void onSectionFocused(int sectionId) {
        int index = mRailModel.indexOf(sectionId);

        if (index != -1) {
            showSection(mRailModel.get(index));
        }
    }

    @Override
    public void onSectionClicked(int sectionId) {
        if (sectionId != mSectionId) {
            mFocusOnContent = true;
            onSectionFocused(sectionId);
        } else {
            mPresenter.onSectionFocused(sectionId);
            focusOnContent();
        }
    }

    @Override
    public void onSectionLongPressed(int sectionId) {
        mPresenter.onSectionLongPressed(sectionId);
    }

    @Override
    public void onSearchClicked() {
        SearchPresenter.instance(getContext()).startSearch(null);
    }

    @Override
    public void onAccountClicked() {
        MediaServiceManager.instance().loadAccounts(this::showAccountPicker);
    }

    @Override
    public void onAccountLongClicked() {
        AccountSettingsPresenter.instance(getContext()).show();
    }

    @Override
    public void onExpandChanged(float fraction) {
        float shift = mRail.getExpandShift() * fraction;
        mContent.setTranslationX(shift);
        // The clock and the logo stay at the end of the screen, only the search group follows the content
        mTopBar.findViewById(R.id.modern_top_bar_search_group).setTranslationX(shift);
        // Spinner and page states are centered in the page body: they follow the content too
        mProgress.setTranslationX(shift);
    }

    @Override
    public void onAccountChanged(Account account) {
        Utils.postDelayed(this::updateAccount, 1_000); // give a time to engine to fetch an updated icon url
    }

    // Internal

    private boolean routeKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && mRail.hasFocus()) {
            focusOnContent();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !mRail.hasFocus() && isAtStartEdge()) {
            if (mSection instanceof ModernSideListView && ((ModernSideListView) mSection).onStartEdge()) {
                return true;
            }
            return mRail.focusSelected();
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && mContent.hasFocus() && isAtTopEdge()) {
            // Pages without the top bar (or with the search button off) keep the focus where it is
            View search = mTopBar.findViewById(R.id.modern_top_bar_search);
            if (search.isShown()) {
                search.requestFocus();
            }
            return true;
        }

        return false;
    }

    /**
     * Left from the first column (or from the top bar) enters the rail.
     */
    private boolean isAtStartEdge() {
        View focused = getView() != null ? getView().findFocus() : null;

        if (focused == null) {
            return false;
        }

        Boolean gridEdge = isAtGridEdge(focused, View.FOCUS_LEFT);

        if (gridEdge != null) {
            return gridEdge;
        }

        View next = focused.focusSearch(View.FOCUS_LEFT);
        return next == null || next == focused || isInside(next, mRail);
    }

    /**
     * Up from the first shelf (or grid row) reaches the search bar.
     */
    private boolean isAtTopEdge() {
        View focused = getView() != null ? getView().findFocus() : null;

        if (focused == null) {
            return false;
        }

        Boolean gridEdge = isAtGridEdge(focused, View.FOCUS_UP);

        if (gridEdge != null) {
            return gridEdge;
        }

        View next = focused.focusSearch(View.FOCUS_UP);
        return next == null || next == focused || !isInside(next, mContent);
    }

    /**
     * Edge check by item position, not by geometry: while a row scrolls (or with tiles partially
     * outside the screen) a geometric focus search may wrongly hit the rail or the top bar.
     * @return null when the focus isn't inside a grid
     */
    @Nullable
    private Boolean isAtGridEdge(View focused, int direction) {
        View child = focused;
        ViewParent parent = focused.getParent();

        while (parent instanceof View && parent != mContent) {
            if (parent instanceof BaseGridView) {
                BaseGridView grid = (BaseGridView) parent;
                int position = grid.getChildAdapterPosition(child);

                if (position != RecyclerView.NO_POSITION && grid.getLayoutManager() != null) {
                    boolean horizontal = grid instanceof HorizontalGridView;

                    if (direction == View.FOCUS_LEFT) {
                        if (horizontal) {
                            return position == 0;
                        }
                        if (!isSingleColumn(grid)) {
                            return child.getLeft() <= minChildLeft(grid);
                        }
                    } else if (!horizontal) {
                        View first = grid.getLayoutManager().findViewByPosition(0);
                        return first != null && first.getTop() == child.getTop();
                    }
                }
            }

            child = (View) parent;
            parent = parent.getParent();
        }

        return null;
    }

    private static boolean isSingleColumn(ViewGroup grid) {
        return grid.getChildCount() < 2 || minChildLeft(grid) == maxChildLeft(grid);
    }

    private static int minChildLeft(ViewGroup grid) {
        int left = Integer.MAX_VALUE;
        for (int i = 0; i < grid.getChildCount(); i++) {
            left = Math.min(left, grid.getChildAt(i).getLeft());
        }
        return left;
    }

    private static int maxChildLeft(ViewGroup grid) {
        int left = Integer.MIN_VALUE;
        for (int i = 0; i < grid.getChildCount(); i++) {
            left = Math.max(left, grid.getChildAt(i).getLeft());
        }
        return left;
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

    private void showAccountPicker(List<Account> accounts) {
        if (getView() == null) {
            return;
        }

        if (mAccountPicker == null) {
            ViewGroup root = (ViewGroup) getView();
            mAccountPicker = ModernAccountPickerView.inflate(root);
            mAccountPicker.setListener(new ModernAccountPickerView.Listener() {
                @Override
                public void onAccountSelected(@Nullable Account account) {
                    AccountSelectionPresenter.instance(getContext()).selectAccount(account);
                    hideAccountPicker();
                }

                @Override
                public void onAccountSettings() {
                    AccountSettingsPresenter.instance(getContext()).show();
                }

                @Override
                public void onAddAccount() {
                    hideAccountPicker();
                    YTSignInPresenter.instance(getContext()).start();
                }
            });
            // Below the rail, like the reference
            root.addView(mAccountPicker, root.indexOfChild(mRail));
        }

        mAccountPicker.setAccounts(accounts);
    }

    private void hideAccountPicker() {
        if (mAccountPicker != null) {
            ((ViewGroup) mAccountPicker.getParent()).removeView(mAccountPicker);
            mAccountPicker = null;
            focusOnContent();
        }
    }

    private void showSection(BrowseSection section) {
        if (mAccountPicker != null) {
            ((ViewGroup) mAccountPicker.getParent()).removeView(mAccountPicker);
            mAccountPicker = null;
        }

        mSectionId = section.getId();
        mRail.setSelectedSection(mSectionId);

        removeErrorView();
        mContent.removeAllViews();
        mSection = createSection(section);

        if (mSection != null) {
            mContent.addView(mSection.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Pages with their own title (like subscriptions) have no search bar, the clock and the logo stay
        mTopBar.findViewById(R.id.modern_top_bar_search_group).setVisibility(
                mSection instanceof ModernSideListView || mSection instanceof ModernSettingsView ? View.INVISIBLE : View.VISIBLE);

        applyProgress();

        mHandler.removeCallbacks(mOnSectionSelected);
        mHandler.postDelayed(mOnSectionSelected, SECTION_SELECTED_DELAY_MS);
    }

    /**
     * Pages with a side list show the spinner over their grid only, not over the list.
     */
    private void applyProgress() {
        if (mProgress == null) {
            return;
        }

        boolean ownLoading = mSection instanceof ModernSection.LoadingHost;
        mProgress.setVisibility(mIsLoading && !ownLoading ? View.VISIBLE : View.GONE);

        if (ownLoading) {
            ((ModernSection.LoadingHost) mSection).showLoading(mIsLoading);
        }
    }

    private boolean isShown(@Nullable BrowseSection section) {
        return section == null || section.getId() == mSectionId;
    }

    private void onSectionSelected() {
        if (mFocusOnContent) {
            mFocusOnContent = false;
            focusOnContent();
        }

        mPresenter.onSectionFocused(mSectionId);
    }

    private ModernSection createSection(BrowseSection section) {
        Context context = requireContext();

        switch (section.getType()) {
            case BrowseSection.TYPE_ROW:
                return new ModernRowsView(context, mPresenter, mCardPresenter);
            case BrowseSection.TYPE_GRID:
                if (section.getId() == MediaGroup.TYPE_SUBSCRIPTIONS) {
                    return new ModernSubscriptionsView(context, section.getTitle(), mPresenter, mCardPresenter);
                }
                return new ModernGridView(context, mPresenter, mCardPresenter);
            case BrowseSection.TYPE_MULTI_GRID:
                if (section.getId() == MediaGroup.TYPE_CHANNEL_UPLOADS) {
                    return new ModernChannelsView(context, section.getTitle(), mPresenter, mCardPresenter);
                }
                return new ModernGridView(context, mPresenter, mCardPresenter);
            case BrowseSection.TYPE_SHORTS_GRID:
                ModernGridView shorts = new ModernGridView(context, mPresenter, mCardPresenter);
                shorts.setShorts(true);
                return shorts;
            case BrowseSection.TYPE_SETTINGS_GRID:
                return new ModernSettingsView(context, section.getTitle());
            case BrowseSection.TYPE_ERROR:
                mHandler.post(() -> showErrorView((ErrorFragmentData) section.getData()));
                return null;
        }

        return null;
    }

    private void applyPendingSelection() {
        if (mSection == null || mSection.isEmpty()) {
            return;
        }

        if (mPendingItemIndex != -1) {
            mSection.setPosition(mPendingItemIndex);
            mPendingItemIndex = -1;
        }

        if (mPendingItem != null) {
            mSection.selectItem(mPendingItem);
            mPendingItem = null;
        }
    }

    private void showErrorView(ErrorFragmentData data) {
        if (data == null || getContext() == null) {
            return;
        }

        boolean hadFocus = mContent.hasFocus();
        removeErrorView();

        // Page with a list (subscriptions, channels): the state of the shown item takes the place of the videos only
        if (mSection instanceof ModernSideListView && ((ModernSideListView) mSection).isColumnShown()) {
            mStateHost = (ModernSideListView) mSection;
            mStateHost.showState(ModernStatePage.create(getContext(), mStateHost, data, mStateHost::reloadShown));
            return;
        }

        if (mSection != null) {
            mSection.getView().setVisibility(View.GONE);
        }

        mErrorView = ModernStatePage.create(getContext(), mContent, data, () -> mPresenter.refresh());
        mErrorView.setAlpha(0);
        mContent.addView(mErrorView);
        mErrorView.animate().alpha(1).setDuration(ModernTheme.ANIM_FADE_MS).setInterpolator(ModernTheme.fadeInterpolator());

        if (hadFocus) {
            ModernStatePage.focus(mErrorView);
        }
    }

    private static void fadeIn(View view) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(ModernTheme.ANIM_FAST_MS).setInterpolator(ModernTheme.fadeInterpolator());
    }

    private void restoreSection() {
        if (mStateHost != null) {
            removeErrorView();
        }

        if (mErrorView != null) {
            removeErrorView();
            if (mSection != null) {
                mSection.getView().setVisibility(View.VISIBLE);
            }
        }
    }

    private void removeErrorView() {
        if (mStateHost != null) {
            mStateHost.clearState();
            mStateHost = null;
        }

        if (mErrorView != null) {
            mContent.removeView(mErrorView);
            mErrorView = null;
        }
    }

    /**
     * Same options as the classic title bar: top buttons (search, language, account),
     * the global clock and the way back to a video playing in the background.
     */
    private void updateTopBar() {
        if (mTopBar == null || getContext() == null) {
            return;
        }

        MainUIData mainUIData = MainUIData.instance(getContext());
        boolean search = mainUIData.isTopButtonEnabled(MainUIData.TOP_BUTTON_SEARCH);
        mTopBar.findViewById(R.id.modern_top_bar_mic).setVisibility(search ? View.VISIBLE : View.GONE);
        mTopBar.findViewById(R.id.modern_top_bar_search).setVisibility(search ? View.VISIBLE : View.GONE);
        mTopBar.findViewById(R.id.modern_top_bar_language).setVisibility(
                mainUIData.isTopButtonEnabled(MainUIData.TOP_BUTTON_CHANGE_LANGUAGE) ? View.VISIBLE : View.GONE);
        mRail.setAccountVisible(mainUIData.isTopButtonEnabled(MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS));

        int clock = GeneralData.instance(getContext()).isGlobalClockEnabled() ? View.VISIBLE : View.GONE;
        mTopBar.findViewById(R.id.modern_top_bar_clock).setVisibility(clock);
        mTopBar.findViewById(R.id.modern_top_bar_date).setVisibility(clock);

        TextView backgroundVideo = mTopBar.findViewById(R.id.modern_top_bar_background_video);
        PlaybackPresenter playback = PlaybackPresenter.instance(getContext());
        Video video = playback.isRunningInBackground() ? playback.getVideo() : null;
        backgroundVideo.setVisibility(video != null ? View.VISIBLE : View.GONE);
        backgroundVideo.setText(video != null ? video.getTitle() : null);
    }

    private void updateAccount() {
        if (mRail == null) {
            return;
        }

        Account current = MediaServiceManager.instance().getSelectedAccount();

        if (current != null) {
            mRail.setAccount(current.getName() != null ? current.getName() : current.getEmail(), current.getAvatarImageUrl());
        } else {
            mRail.setAccount(getString(R.string.dialog_add_account), null);
        }
    }
}
