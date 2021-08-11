package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Category;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.BootDialogsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ChannelUploadsMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.CategoryPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.ScreenHelper;
import com.liskovsoft.youtubeapi.common.helpers.ServiceHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BrowsePresenter extends BasePresenter<BrowseView> implements CategoryPresenter, VideoGroupPresenter {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    private static final long HEADER_REFRESH_PERIOD_MS = 120 * 60 * 1_000;
    private static final int MIN_GROUP_SIZE = 13;
    private static final int SHORTS_LEN_MS = 30 * 1_000;
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final Handler mHandler = new Handler();
    private final PlaybackPresenter mPlaybackPresenter;
    private final ViewManager mViewManager;
    private final MainUIData mMainUIData;
    private final GeneralData mGeneralData;
    private final List<Category> mCategories;
    private final Map<Integer, Observable<MediaGroup>> mGridMapping;
    private final Map<Integer, Observable<List<MediaGroup>>> mRowMapping;
    private final Map<Integer, List<SettingsItem>> mSettingsGridMapping;
    private final AppDataSourceManager mDataSourcePresenter;
    private final MediaGroupManager mGroupManager;
    private final MediaItemManager mItemManager;
    private final SignInManager mSignInManager;
    private Disposable mUpdateAction;
    private Disposable mContinueAction;
    private Disposable mSignCheckAction;
    private int mCurrentCategoryId;
    private long mLastUpdateTimeMs;
    private int mStartCategoryIndex;
    private int mUploadsType;

    private BrowsePresenter(Context context) {
        super(context);
        mDataSourcePresenter = AppDataSourceManager.instance();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mViewManager = ViewManager.instance(context);
        mCategories = new ArrayList<>();
        mGridMapping = new HashMap<>();
        mRowMapping = new HashMap<>();
        mSettingsGridMapping = new HashMap<>();
        mMainUIData = MainUIData.instance(context);
        mGeneralData = GeneralData.instance(context);
        ScreenHelper.initPipMode(context);
        ScreenHelper.updateScreenInfo(context);

        MediaService mediaService = YouTubeMediaService.instance();
        mGroupManager = mediaService.getMediaGroupManager();
        mItemManager = mediaService.getMediaItemManager();
        mSignInManager = mediaService.getSignInManager();

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

        updateChannelSorting();
        updatePlaylistsStyle();
        updateCategories();
        getView().selectCategory(mStartCategoryIndex);
        showBootDialogs();
    }

    private void initCategories() {
        initCategoryHeaders();
        initPinnedHeaders();

        initCategoryCallbacks();
        initPinnedCallbacks();

        initSettingsSubCategories();
    }

    private void initCategoryHeaders() {
        mUploadsType = mMainUIData.isUploadsOldLookEnabled() ? Category.TYPE_GRID : Category.TYPE_MULTI_GRID;

        mCategories.add(new Category(MediaGroup.TYPE_HOME, getContext().getString(R.string.header_home), Category.TYPE_ROW, R.drawable.icon_home));
        mCategories.add(new Category(MediaGroup.TYPE_GAMING, getContext().getString(R.string.header_gaming), Category.TYPE_ROW, R.drawable.icon_gaming));
        mCategories.add(new Category(MediaGroup.TYPE_NEWS, getContext().getString(R.string.header_news), Category.TYPE_ROW, R.drawable.icon_news));
        mCategories.add(new Category(MediaGroup.TYPE_MUSIC, getContext().getString(R.string.header_music), Category.TYPE_ROW, R.drawable.icon_music));
        mCategories.add(new Category(MediaGroup.TYPE_CHANNEL_UPLOADS, getContext().getString(R.string.header_channels), mUploadsType, R.drawable.icon_channels, true));
        mCategories.add(new Category(MediaGroup.TYPE_SUBSCRIPTIONS, getContext().getString(R.string.header_subscriptions), Category.TYPE_GRID, R.drawable.icon_subscriptions, true));
        mCategories.add(new Category(MediaGroup.TYPE_HISTORY, getContext().getString(R.string.header_history), Category.TYPE_GRID, R.drawable.icon_history, true));
        mCategories.add(new Category(MediaGroup.TYPE_USER_PLAYLISTS, getContext().getString(R.string.header_playlists), Category.TYPE_ROW, R.drawable.icon_playlist, true));

        if (mGeneralData.isSettingsCategoryEnabled()) {
            mCategories.add(new Category(MediaGroup.TYPE_SETTINGS, getContext().getString(R.string.header_settings), Category.TYPE_SETTINGS_GRID, R.drawable.icon_settings));
        }
    }

    private void initCategoryCallbacks() {
        mRowMapping.put(MediaGroup.TYPE_HOME, mGroupManager.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mGroupManager.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mGroupManager.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mGroupManager.getGamingObserve());
        mRowMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, mGroupManager.getPlaylistsObserve());

        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mGroupManager.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mGroupManager.getHistoryObserve());
        mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupManager.getSubscribedChannelsUpdateObserve());
    }

    private void initPinnedHeaders() {
        Set<Video> pinnedItems = mGeneralData.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null) {
                Category category = new Category(item.hashCode(), item.title, Category.TYPE_GRID, item.cardImageUrl, true, item);
                mCategories.add(category);
            }
        }
    }

    private void initPinnedCallbacks() {
        Set<Video> pinnedItems = mGeneralData.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null) {
                mGridMapping.put(item.hashCode(), ChannelUploadsPresenter.instance(getContext()).obtainVideoGroupObservable(item));
            }
        }
    }

    private void initSettingsSubCategories() {
        mSettingsGridMapping.put(MediaGroup.TYPE_SETTINGS, mDataSourcePresenter.getSettingItems(this));
    }

    public void updateCategories() {
        int index = 0;

        for (Category category : mCategories) {
            category.setEnabled(category.getId() == MediaGroup.TYPE_SETTINGS || mGeneralData.isCategoryEnabled(category.getId()));

            if (category.isEnabled()) {
                if (category.getId() == mGeneralData.getBootCategoryId()) {
                    mStartCategoryIndex = index;
                }
                getView().addCategory(index++, category);
            } else {
                getView().removeCategory(category);
            }
        }
    }

    public void updateChannelSorting() {
        int sortingType = mMainUIData.getChannelCategorySorting();

        switch (sortingType) {
            case MainUIData.CHANNEL_SORTING_UPDATE:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupManager.getSubscribedChannelsUpdateObserve());
                break;
            case MainUIData.CHANNEL_SORTING_AZ:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupManager.getSubscribedChannelsAZObserve());
                break;
            case MainUIData.CHANNEL_SORTING_LAST_VIEWED:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupManager.getSubscribedChannelsLastViewedObserve());
                break;
        }
    }

    public void updatePlaylistsStyle() {
        int playlistsStyle = mMainUIData.getPlaylistsStyle();

        switch (playlistsStyle) {
            case MainUIData.PLAYLISTS_STYLE_GRID:
                mRowMapping.remove(MediaGroup.TYPE_USER_PLAYLISTS);
                mGridMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, mGroupManager.getEmptyPlaylistsObserve());
                updateCategoryType(MediaGroup.TYPE_USER_PLAYLISTS, Category.TYPE_GRID);
                break;
            case MainUIData.PLAYLISTS_STYLE_ROWS:
                mGridMapping.remove(MediaGroup.TYPE_USER_PLAYLISTS);
                mRowMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, mGroupManager.getPlaylistsObserve());
                updateCategoryType(MediaGroup.TYPE_USER_PLAYLISTS, Category.TYPE_ROW);
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
        super.onViewDestroyed();
        disposeActions();
    }

    @Override
    public void onVideoItemSelected(Video item) {
        if (getView() == null) {
            return;
        }

        if (item.isChannelUploadsSection() && mUploadsType == Category.TYPE_MULTI_GRID) {
            if (mMainUIData.isUploadsAutoLoadEnabled()) {
                updateMultiGrid(item);
            } else {
                updateMultiGrid(null); // clear
            }
        }
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (getView() == null) {
            return;
        }

        if (item.isChannelUploadsSection()) {
            // Below doesn't work right now. Api doesn't contains channel id.
            //ChannelPresenter.instance(getContext()).openChannel(item);

            if (mUploadsType == Category.TYPE_MULTI_GRID) { // Is Channels new look enabled?
                updateMultiGrid(item);
            } else {
                ChannelUploadsPresenter.instance(getContext()).openChannel(item);
            }
        } else if (item.isPlaylistSection()) {
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

        if (item.isChannelUploadsSection()) {
            ChannelUploadsMenuPresenter.instance(getContext()).showMenu(item);
        } else if (item.isVideo()) {
            item.isSubscribed = mCurrentCategoryId == MediaGroup.TYPE_SUBSCRIPTIONS;
            Category category = getCategory(mCurrentCategoryId);
            VideoMenuPresenter.instance(getContext()).showVideoMenu(item, category != null ? category.getData() : null);
        } else if (item.isChannel()) {
            VideoMenuPresenter.instance(getContext()).showChannelMenu(item);
        } else if (item.isPlaylistSection()) {
            VideoMenuPresenter.instance(getContext()).showPlaylistMenu(item);
        }

        updateRefreshTime();
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        VideoGroup group = item.group;

        Log.d(TAG, "onScrollEnd. Group title: " + group.getTitle());

        continueGroup(group);
    }

    @Override
    public void onCategoryFocused(int categoryId) {
        updateCategory(categoryId);
    }

    @Override
    public boolean hasPendingActions() {
        return RxUtils.isAnyActionRunning(mUpdateAction, mContinueAction, mSignCheckAction);
    }

    public boolean isItemPinned(Video item) {
        Set<Video> items = mGeneralData.getPinnedItems();

        return items.contains(item);
    }

    public void pinItem(Video item) {
        Set<Video> items = mGeneralData.getPinnedItems();
        items.add(item);
        mGeneralData.setPinnedItems(items);

        Category category = new Category(item.hashCode(), item.title, Category.TYPE_GRID, item.cardImageUrl, true, item);
        mCategories.add(category);
        mGridMapping.put(item.hashCode(), ChannelUploadsPresenter.instance(getContext()).obtainVideoGroupObservable(item));

        if (getView() != null) {
            getView().addCategory(-1, category); // add last
        }
    }

    public void unpinItem(Video item) {
        Set<Video> items = mGeneralData.getPinnedItems();
        items.remove(item);
        mGeneralData.setPinnedItems(items);

        Category category = null;

        for (Category cat : mCategories) {
            if (cat.getId() == item.hashCode()) {
                category = cat;
                break;
            }
        }

        mGridMapping.remove(item.hashCode());

        if (getView() != null) {
            getView().removeCategory(category);
        }
    }

    private void maybeRefreshHeader() {
        long timeAfterPauseMs = System.currentTimeMillis() - mLastUpdateTimeMs;
        if (timeAfterPauseMs > HEADER_REFRESH_PERIOD_MS) { // update header every n minutes
            refresh();
        }
    }

    private void showBootDialogs() {
        BootDialogsPresenter updatePresenter = BootDialogsPresenter.instance(getContext());
        updatePresenter.start();
        updatePresenter.unhold();
    }

    public void refresh() {
        updateCategory(mCurrentCategoryId);
    }

    private void updateRefreshTime() {
        mLastUpdateTimeMs = System.currentTimeMillis();
    }

    private void updateCategory(int categoryId) {
        disposeActions();

        mCurrentCategoryId = categoryId;

        if (getView() == null || categoryId < 0) {
            return;
        }

        Category category = getCategory(categoryId);

        if (category != null) {
            Log.d(TAG, "Update category %s", category.getTitle());
            updateCategory(category);
        }
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
            case Category.TYPE_SETTINGS_GRID:
                List<SettingsItem> items = mSettingsGridMapping.get(category.getId());
                updateSettingsGrid(category, items);
                break;
            case Category.TYPE_MULTI_GRID:
                Observable<MediaGroup> group2 = mGridMapping.get(category.getId());
                updateVideoGrid(category, group2, 0, category.isAuthOnly());
                break;
        }

        updateRefreshTime();
    }

    private void updateSettingsGrid(Category category, List<SettingsItem> items) {
        getView().updateCategory(SettingsGroup.from(items, category));
        getView().showProgressBar(false);
    }

    private void updateVideoRows(Category category, Observable<List<MediaGroup>> groups, boolean authCheck) {
        Log.d(TAG, "loadRowsHeader: Start loading category: " + category.getTitle());

        authCheck(authCheck, () -> updateVideoRows(category, groups));
    }

    private void updateVideoGrid(Category category, Observable<MediaGroup> group, boolean authCheck) {
        updateVideoGrid(category, group, -1, authCheck);
    }

    private void updateVideoGrid(Category category, Observable<MediaGroup> group, int position, boolean authCheck) {
        Log.d(TAG, "loadMultiGridHeader: Start loading category: " + category.getTitle());

        authCheck(authCheck, () -> updateVideoGrid(category, group, position));
    }

    private void updateVideoRows(Category category, Observable<List<MediaGroup>> groups) {
        Log.d(TAG, "updateRowsHeader: Start loading category: " + category.getTitle());

        disposeActions();
        getView().showProgressBar(true);

        getView().updateCategory(VideoGroup.from(category, true));

        mUpdateAction = groups
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroups -> {
                            for (MediaGroup mediaGroup : mediaGroups) {
                                if (mediaGroup.isEmpty()) {
                                    Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                                    continue;
                                }

                                VideoGroup videoGroup = VideoGroup.from(mediaGroup, category);

                                filterIfNeeded(videoGroup);
                                getView().updateCategory(videoGroup);

                                loadNextPortionIfNeeded(videoGroup);
                            }

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

    private void updateVideoGrid(Category category, Observable<MediaGroup> group, int position) {
        Log.d(TAG, "updateGridHeader: Start loading category: " + category.getTitle());

        disposeActions();
        getView().showProgressBar(true);

        getView().updateCategory(VideoGroup.from(category, position, true));

        if (group == null) {
            // No group. Maybe just clear.
            getView().showProgressBar(false);
            return;
        }

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> {
                            if (getView() == null) {
                                Log.e(TAG, "Browse view has been unloaded from the memory. Low RAM?");
                                ViewManager.instance(getContext()).startView(BrowseView.class);
                                return;
                            }

                            VideoGroup videoGroup = VideoGroup.from(mediaGroup, category, position);
                            filterIfNeeded(videoGroup);
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

    private void continueGroup(VideoGroup group) {
        if (RxUtils.isAnyActionRunning(mContinueAction)) {
            Log.e(TAG, "Can't continue group. Another action is running.");
            return;
        }

        if (getView() == null) {
            Log.e(TAG, "Can't continue group. The view is null.");
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());
        
        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        Observable<MediaGroup> continuation;

        if (mediaGroup.getType() == MediaGroup.TYPE_SUGGESTIONS) { // Pinned playlist
            continuation = mItemManager.continueGroupObserve(mediaGroup);
        } else {
            continuation = mGroupManager.continueGroupObserve(mediaGroup);
        }

        mContinueAction = continuation
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueGroup -> {
                            VideoGroup videoGroup = VideoGroup.from(continueGroup, group.getCategory(), group.getPosition());
                            filterIfNeeded(videoGroup);
                            getView().updateCategory(videoGroup);
                        },
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
        disposeActions();

        if (!check) {
            callback.run();
            return;
        }

        getView().showProgressBar(true);

        mSignCheckAction = mSignInManager.isSignedObserve()
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

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    private void loadNextPortionIfNeeded(VideoGroup videoGroup) {
        boolean groupTooSmall = videoGroup.getVideos() != null && videoGroup.getVideos().size() < MIN_GROUP_SIZE;
        if (groupTooSmall || mMainUIData.getUIScale() < 0.8f || mMainUIData.getVideoGridScale() < 0.8f) {
            continueGroup(videoGroup);
        }
    }

    private void disposeActions() {
        RxUtils.disposeActions(mUpdateAction, mContinueAction, mSignCheckAction);
    }

    private void updateMultiGrid(Video item) {
        Category category = getCategory(mCurrentCategoryId);

        if (category == null) {
            return;
        }

        updateVideoGrid(category, ChannelUploadsPresenter.instance(getContext()).obtainVideoGroupObservable(item), 1, true);
    }

    private Category getCategory(int categoryId) {
        for (Category category : mCategories) {
            if (category.getId() == categoryId) {
                return category;
            }
        }

        return null;
    }

    private void filterIfNeeded(VideoGroup videoGroup) {
        if (mGeneralData.isHideShortsEnabled() &&
            videoGroup.getCategory().getId() == MediaGroup.TYPE_SUBSCRIPTIONS &&
            videoGroup.getVideos() != null) {
            videoGroup.getVideos().removeIf(value -> {
                if (value.title == null) {
                    return false;
                }

                int lengthMs = ServiceHelper.timeTextToMillis(value.badge);
                return lengthMs > 0 && lengthMs < SHORTS_LEN_MS;
                //return value.title.toLowerCase().contains("#short")  ||
                //       value.title.toLowerCase().contains("#shorts") ||
                //       value.title.toLowerCase().contains("#tiktok");
            });
        }
    }
}
