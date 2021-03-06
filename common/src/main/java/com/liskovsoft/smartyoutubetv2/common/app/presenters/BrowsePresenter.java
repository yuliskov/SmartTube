package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Category;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.CategoryPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowsePresenter extends BasePresenter<BrowseView> implements CategoryPresenter, VideoGroupPresenter {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    private static final long HEADER_REFRESH_PERIOD_MS = 120 * 60 * 1_000;
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final Handler mHandler = new Handler();
    private final PlaybackPresenter mPlaybackPresenter;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private final MainUIData mMainUIData;
    private final List<Category> mCategories;
    private final Map<Integer, Observable<MediaGroup>> mGridMapping;
    private final Map<Integer, Observable<List<MediaGroup>>> mRowMapping;
    private final Map<Integer, List<SettingsItem>> mTextGridMapping;
    private final AppDataSourceManager mDataSourcePresenter;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Disposable mSignCheckAction;
    private int mCurrentCategoryId;
    private long mLastUpdateTimeMs;
    private int mBootToIndex;

    private BrowsePresenter(Context context) {
        super(context);
        mDataSourcePresenter = AppDataSourceManager.instance();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
        mCategories = new ArrayList<>();
        mGridMapping = new HashMap<>();
        mRowMapping = new HashMap<>();
        mTextGridMapping = new HashMap<>();
        mMainUIData = MainUIData.instance(context);
        initCategories();
    }

    public static BrowsePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BrowsePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        if (getView() == null) {
            return;
        }

        updateChannelCategorySorting();
        updatePlaylistsStyle();
        updateCategories();
        getView().selectCategory(mBootToIndex);
        checkForUpdates();
    }

    private void initCategories() {
        initCategoryHeaders();

        initCategoryCallbacks();

        initSettingsSubCategories();
    }

    private void initCategoryHeaders() {
        mCategories.add(new Category(MediaGroup.TYPE_HOME, getContext().getString(R.string.header_home), Category.TYPE_ROW, R.drawable.icon_home));
        mCategories.add(new Category(MediaGroup.TYPE_GAMING, getContext().getString(R.string.header_gaming), Category.TYPE_ROW, R.drawable.icon_gaming));
        mCategories.add(new Category(MediaGroup.TYPE_NEWS, getContext().getString(R.string.header_news), Category.TYPE_ROW, R.drawable.icon_news));
        mCategories.add(new Category(MediaGroup.TYPE_MUSIC, getContext().getString(R.string.header_music), Category.TYPE_ROW, R.drawable.icon_music));
        mCategories.add(new Category(MediaGroup.TYPE_CHANNELS_SECTION, getContext().getString(R.string.header_channels), Category.TYPE_GRID, R.drawable.icon_channels, true));
        mCategories.add(new Category(MediaGroup.TYPE_SUBSCRIPTIONS, getContext().getString(R.string.header_subscriptions), Category.TYPE_GRID, R.drawable.icon_subscriptions, true));
        mCategories.add(new Category(MediaGroup.TYPE_HISTORY, getContext().getString(R.string.header_history), Category.TYPE_GRID, R.drawable.icon_history, true));
        mCategories.add(new Category(MediaGroup.TYPE_PLAYLISTS_SECTION, getContext().getString(R.string.header_playlists), Category.TYPE_ROW, R.drawable.icon_playlist, true));

        if (mMainUIData.isSettingsCategoryEnabled()) {
            mCategories.add(new Category(MediaGroup.TYPE_SETTINGS, getContext().getString(R.string.header_settings), Category.TYPE_TEXT_GRID, R.drawable.icon_settings));
        }
    }

    private void initCategoryCallbacks() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mRowMapping.put(MediaGroup.TYPE_HOME, mediaGroupManager.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mediaGroupManager.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mediaGroupManager.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mediaGroupManager.getGamingObserve());
        mRowMapping.put(MediaGroup.TYPE_PLAYLISTS_SECTION, mediaGroupManager.getPlaylistsObserve());

        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mediaGroupManager.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mediaGroupManager.getHistoryObserve());
        mGridMapping.put(MediaGroup.TYPE_CHANNELS_SECTION, mediaGroupManager.getSubscribedChannelsUpdateObserve());
    }

    private void initSettingsSubCategories() {
        mTextGridMapping.put(MediaGroup.TYPE_SETTINGS, mDataSourcePresenter.getSettingItems(this));
    }

    public void updateCategories() {
        int index = 0;

        for (Category category : mCategories) {
            category.setEnabled(category.getId() == MediaGroup.TYPE_SETTINGS || mMainUIData.isCategoryEnabled(category.getId()));

            if (category.isEnabled()) {
                if (category.getId() == mMainUIData.getBootCategoryId()) {
                    mBootToIndex = index;
                }
                getView().addCategory(index++, category);
            } else {
                getView().removeCategory(category);
            }
        }
    }

    public void updateChannelCategorySorting() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        int sortingType = mMainUIData.getChannelCategorySorting();

        switch (sortingType) {
            case MainUIData.CHANNEL_SORTING_UPDATE:
                mGridMapping.put(MediaGroup.TYPE_CHANNELS_SECTION, mediaGroupManager.getSubscribedChannelsUpdateObserve());
                break;
            case MainUIData.CHANNEL_SORTING_AZ:
                mGridMapping.put(MediaGroup.TYPE_CHANNELS_SECTION, mediaGroupManager.getSubscribedChannelsAZObserve());
                break;
            case MainUIData.CHANNEL_SORTING_LAST_VIEWED:
                mGridMapping.put(MediaGroup.TYPE_CHANNELS_SECTION, mediaGroupManager.getSubscribedChannelsLastViewedObserve());
                break;
        }
    }

    public void updatePlaylistsStyle() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        int playlistsStyle = mMainUIData.getPlaylistsStyle();

        switch (playlistsStyle) {
            case MainUIData.PLAYLISTS_STYLE_GRID:
                mRowMapping.remove(MediaGroup.TYPE_PLAYLISTS_SECTION);
                mGridMapping.put(MediaGroup.TYPE_PLAYLISTS_SECTION, mediaGroupManager.getEmptyPlaylistsObserve());
                updateCategoryType(MediaGroup.TYPE_PLAYLISTS_SECTION, Category.TYPE_GRID);
                break;
            case MainUIData.PLAYLISTS_STYLE_ROWS:
                mGridMapping.remove(MediaGroup.TYPE_PLAYLISTS_SECTION);
                mRowMapping.put(MediaGroup.TYPE_PLAYLISTS_SECTION, mediaGroupManager.getPlaylistsObserve());
                updateCategoryType(MediaGroup.TYPE_PLAYLISTS_SECTION, Category.TYPE_ROW);
                break;
        }
    }

    private void updateCategoryType(int categoryId, int categoryType) {
        if (categoryType == -1 || categoryId == -1 || mCategories == null) {
            return;
        }

        for (Category category : mCategories) {
            if (category.getId() == categoryId) {
                category.setType(categoryType);
                break;
            }
        }
    }

    @Override
    public void onViewDestroyed() {
        RxUtils.disposeActions(mUpdateAction, mScrollAction, mSignCheckAction);
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (getView() == null) {
            return;
        }

        if (item.isChannelUploads()) {
            // Doesn't work right now. Api doesn't contains channel id.
            //ChannelPresenter.instance(getContext()).openChannel(item);
            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        } else if (item.isPlaylist()) {
            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        } else if (item.isVideo()) {
            mPlaybackPresenter.openVideo(item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(getContext()).openChannel(item);
        }

        updateRefreshTime();
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (getView() == null) {
            return;
        }

        if (item.isChannelUploads()) {
            ChannelUploadsMenuPresenter.instance(getContext()).showMenu(item);
        } else if (item.isVideo()) {
            item.isSubscribed = mCurrentCategoryId == MediaGroup.TYPE_SUBSCRIPTIONS;
            VideoMenuPresenter.instance(getContext()).showVideoMenu(item);
        } else if (item.isChannel()) {
            VideoMenuPresenter.instance(getContext()).showChannelMenu(item);
        }

        updateRefreshTime();
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd. Group title: " + group.getTitle());

        boolean updateInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (updateInProgress) {
            return;
        }

        continueGroup(group);
    }

    ///**
    // * Called even when closing dialog window
    // */
    //@Override
    //public void onViewResumed() {
    //    maybeRefreshHeader();
    //}

    @Override
    public void onCategoryFocused(int categoryId) {
        updateCategory(categoryId);
    }

    private void maybeRefreshHeader() {
        long timeAfterPauseMs = System.currentTimeMillis() - mLastUpdateTimeMs;
        if (timeAfterPauseMs > HEADER_REFRESH_PERIOD_MS) { // update header every n minutes
            refresh();
        }
    }

    private void checkForUpdates() {
        AppUpdatePresenter updatePresenter = AppUpdatePresenter.instance(getContext());
        updatePresenter.start(false);
        updatePresenter.unhold();
    }

    public void refresh() {
        updateCategory(mCurrentCategoryId);
    }

    private void updateRefreshTime() {
        mLastUpdateTimeMs = System.currentTimeMillis();
    }

    private void updateCategory(int categoryId) {
        mCurrentCategoryId = categoryId;

        if (getView() == null || categoryId < 0) {
            return;
        }

        RxUtils.disposeActions(mUpdateAction, mScrollAction, mSignCheckAction);

        Category category = getCategory(categoryId);

        if (category != null) {
            getView().showProgressBar(true);
            getView().clearCategory(category);
            updateCategory(category);
        }
    }

    private Category getCategory(int categoryId) {
        for (Category category : mCategories) {
            if (category.getId() == categoryId) {
                return category;
            }
        }

        return null;
    }

    private void updateCategory(Category category) {
        switch (category.getType()) {
            case Category.TYPE_GRID:
                Observable<MediaGroup> group = mGridMapping.get(category.getId());
                updateVideoGrid(category, group, category.isAuthOnly());
                break;
            case Category.TYPE_ROW:
                Observable<List<MediaGroup>> groups = mRowMapping.get(category.getId());
                updateVideoRows(category, groups, category.isAuthOnly());
                break;
            case Category.TYPE_TEXT_GRID:
                List<SettingsItem> items = mTextGridMapping.get(category.getId());
                updateTextGrid(category, items);
                break;
        }

        updateRefreshTime();
    }

    private void updateTextGrid(Category category, List<SettingsItem> items) {
        getView().updateCategory(SettingsGroup.from(items, category));
        getView().showProgressBar(false);
    }

    private void updateVideoRows(Category category, Observable<List<MediaGroup>> groups, boolean authCheck) {
        Log.d(TAG, "loadRowsHeader: Start loading category: " + category.getTitle());

        authCheck(authCheck, () -> updateVideoRows(category, groups));
    }

    private void updateVideoGrid(Category category, Observable<MediaGroup> group, boolean authCheck) {
        Log.d(TAG, "loadGridHeader: Start loading category: " + category.getTitle());

        authCheck(authCheck, () -> updateVideoGrid(category, group));
    }

    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mScrollAction = mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueMediaGroup -> getView().updateCategory(VideoGroup.from(continueMediaGroup, group.getCategory())),
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        },
                        () -> getView().showProgressBar(false)
                );
    }

    private void authCheck(boolean check, Runnable callback) {
        if (!check) {
            callback.run();
            return;
        }

        SignInManager signInManager = mMediaService.getSignInManager();

        mSignCheckAction = signInManager.isSignedObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isSigned -> {
                            if (isSigned) {
                                callback.run();
                            } else {
                                if (getView().isProgressBarShowing()) {
                                    getView().showProgressBar(false);
                                    getView().showError(new SignInError(getContext()));
                                }
                            }
                        },
                        error -> Log.e(TAG, "authCheck error: %s", error.getMessage())
                );
                
    }

    private void updateVideoRows(Category category, Observable<List<MediaGroup>> groups) {
        Log.d(TAG, "updateRowsHeader: Start loading category: " + category.getTitle());

        mUpdateAction = groups
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroups -> {
                            updateRowView(category, mediaGroups);

                            // Hide loading as long as first group received
                            if (!mediaGroups.isEmpty()) {
                                getView().showProgressBar(false);
                            }
                        },
                        error -> {
                            Log.e(TAG, "updateRowsHeader error: %s", error.getMessage());
                            getView().showProgressBar(false);
                            getView().showError(new CategoryEmptyError(getContext()));
                        });
    }

    private void updateVideoGrid(Category category, Observable<MediaGroup> group) {
        Log.d(TAG, "updateGridHeader: Start loading category: " + category.getTitle());

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> {
                            VideoGroup videoGroup = VideoGroup.from(mediaGroup, category);
                            getView().updateCategory(videoGroup);

                            // Hide loading as long as first group received
                            if (mediaGroup.getMediaItems() != null) {
                                getView().showProgressBar(false);
                            }

                            loadNextPortionIfNeeded(videoGroup);
                        },
                        error -> {
                            Log.e(TAG, "updateGridHeader error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                                getView().showError(new CategoryEmptyError(getContext()));
                            }
                        });
    }

    private void updateRowView(Category category, List<MediaGroup> mediaGroups) {
        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            VideoGroup videoGroup = VideoGroup.from(mediaGroup, category);

            getView().updateCategory(videoGroup);

            loadNextPortionIfNeeded(videoGroup);
        }
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    private void loadNextPortionIfNeeded(VideoGroup videoGroup) {
        if (mMainUIData.getUIScale() < 0.8f || mMainUIData.getVideoGridScale() < 0.8f) {
            continueGroup(videoGroup);
        }
    }
}
