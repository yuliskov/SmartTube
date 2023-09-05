package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.ChannelUploadsMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.SectionMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.SectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.sharedutils.helpers.ScreenHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class BrowsePresenter extends BasePresenter<BrowseView> implements SectionPresenter, VideoGroupPresenter {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    private static final long HEADER_REFRESH_PERIOD_MS = 120 * 60 * 1_000;
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final MainUIData mMainUIData;
    private final GeneralData mGeneralData;
    private final List<BrowseSection> mSections;
    private final List<BrowseSection> mErrorSections;
    private final Map<Integer, Observable<MediaGroup>> mGridMapping;
    private final Map<Integer, Observable<List<MediaGroup>>> mRowMapping;
    private final Map<Integer, Callable<List<SettingsItem>>> mSettingsGridMapping;
    private final Map<Integer, BrowseSection> mSectionsMapping;
    private final AppDataSourceManager mDataSourcePresenter;
    private final MediaGroupService mGroupService;
    private final MediaItemService mItemService;
    private final SignInService mSignInService;
    private final NotificationsService mNotificationsService;
    private final List<Disposable> mActions;
    private final Runnable mRefreshSection = this::refresh;
    private BrowseSection mCurrentSection;
    private Video mCurrentVideo;
    private Video mSelectedVideo;
    private long mLastUpdateTimeMs;
    private int mBootSectionIndex;
    private int mSelectedSectionId = -1;
    private MediaGroup mLastScrollGroup;

    private BrowsePresenter(Context context) {
        super(context);
        mDataSourcePresenter = AppDataSourceManager.instance();
        mSections = new ArrayList<>();
        mErrorSections = new ArrayList<>();
        mGridMapping = new HashMap<>();
        mRowMapping = new HashMap<>();
        mSettingsGridMapping = new HashMap<>();
        mSectionsMapping = new HashMap<>();
        mMainUIData = MainUIData.instance(context);
        mGeneralData = GeneralData.instance(context);
        ScreenHelper.updateScreenInfo(context);

        MediaService mediaService = YouTubeMediaService.instance();
        mGroupService = mediaService.getMediaGroupService();
        mItemService = mediaService.getMediaItemService();
        mSignInService = mediaService.getSignInService();
        mNotificationsService = mediaService.getNotificationsService();
        mActions = new ArrayList<>();

        initSections();
    }

    public static BrowsePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BrowsePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();

        if (getView() == null) {
            return;
        }

        updateChannelSorting();
        updatePlaylistsStyle();
        updateSections();
        int selectedSectionIndex = findSectionIndex(mSelectedSectionId);
        mSelectedSectionId = -1;
        getView().selectSection(selectedSectionIndex != -1 ? selectedSectionIndex : mBootSectionIndex, true);
        restoreSelectedItems();
    }

    @Override
    public void onViewPaused() {
        super.onViewPaused();

        saveSelectedItems();
    }

    private void saveSelectedItems() {
        mGeneralData.setSelectedSubscriptionsItem(mSelectedVideo);
    }

    private void restoreSelectedItems() {
        mSelectedVideo = mGeneralData.getSelectedSubscriptionsItem();
    }

    private void initSections() {
        initSectionMapping();

        initSectionCallbacks();

        initSettingsSubCategories();
    }

    private void initSectionMapping() {
        String country = LocaleUtility.getCurrentLocale(getContext()).getCountry();
        int uploadsType = mMainUIData.isUploadsOldLookEnabled() ? BrowseSection.TYPE_GRID : BrowseSection.TYPE_MULTI_GRID;

        mSectionsMapping.put(MediaGroup.TYPE_HOME, new BrowseSection(MediaGroup.TYPE_HOME, getContext().getString(R.string.header_home), BrowseSection.TYPE_ROW, R.drawable.icon_home));
        mSectionsMapping.put(MediaGroup.TYPE_SHORTS, new BrowseSection(MediaGroup.TYPE_SHORTS, getContext().getString(R.string.header_shorts), BrowseSection.TYPE_SHORTS_GRID, R.drawable.icon_shorts));
        mSectionsMapping.put(MediaGroup.TYPE_TRENDING, new BrowseSection(MediaGroup.TYPE_TRENDING, getContext().getString(R.string.header_trending), BrowseSection.TYPE_ROW, R.drawable.icon_trending));
        mSectionsMapping.put(MediaGroup.TYPE_KIDS_HOME, new BrowseSection(MediaGroup.TYPE_KIDS_HOME, getContext().getString(R.string.header_kids_home), BrowseSection.TYPE_ROW, R.drawable.icon_kids_home));
        mSectionsMapping.put(MediaGroup.TYPE_GAMING, new BrowseSection(MediaGroup.TYPE_GAMING, getContext().getString(R.string.header_gaming), BrowseSection.TYPE_ROW, R.drawable.icon_gaming));
        if (!Helpers.equalsAny(country, "RU", "BY")) {
            mSectionsMapping.put(MediaGroup.TYPE_NEWS, new BrowseSection(MediaGroup.TYPE_NEWS, getContext().getString(R.string.header_news), BrowseSection.TYPE_ROW, R.drawable.icon_news));
        }
        mSectionsMapping.put(MediaGroup.TYPE_MUSIC, new BrowseSection(MediaGroup.TYPE_MUSIC, getContext().getString(R.string.header_music), BrowseSection.TYPE_ROW, R.drawable.icon_music));
        mSectionsMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, new BrowseSection(MediaGroup.TYPE_CHANNEL_UPLOADS, getContext().getString(R.string.header_channels), uploadsType, R.drawable.icon_channels, true));
        mSectionsMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, new BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS, getContext().getString(R.string.header_subscriptions), BrowseSection.TYPE_GRID, R.drawable.icon_subscriptions, true));
        mSectionsMapping.put(MediaGroup.TYPE_HISTORY, new BrowseSection(MediaGroup.TYPE_HISTORY, getContext().getString(R.string.header_history), BrowseSection.TYPE_GRID, R.drawable.icon_history, true));
        mSectionsMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, new BrowseSection(MediaGroup.TYPE_USER_PLAYLISTS, getContext().getString(R.string.header_playlists), BrowseSection.TYPE_ROW, R.drawable.icon_playlist, true));
        mSectionsMapping.put(MediaGroup.TYPE_NOTIFICATIONS, new BrowseSection(MediaGroup.TYPE_NOTIFICATIONS, getContext().getString(R.string.header_notifications), BrowseSection.TYPE_GRID, R.drawable.icon_notification, true));

        if (mGeneralData.isSettingsSectionEnabled()) {
            mSectionsMapping.put(MediaGroup.TYPE_SETTINGS, new BrowseSection(MediaGroup.TYPE_SETTINGS, getContext().getString(R.string.header_settings), BrowseSection.TYPE_SETTINGS_GRID, R.drawable.icon_settings));
        }
    }

    private void initSectionCallbacks() {
        mRowMapping.put(MediaGroup.TYPE_HOME, mGeneralData.isOldHomeLookEnabled() ? mGroupService.getHomeV1Observe() : mGroupService.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_TRENDING, mGroupService.getTrendingObserve());
        mRowMapping.put(MediaGroup.TYPE_KIDS_HOME, mGroupService.getKidsHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mGroupService.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mGroupService.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mGroupService.getGamingObserve());
        mRowMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, mGroupService.getPlaylistsObserve());

        mGridMapping.put(MediaGroup.TYPE_SHORTS, mGroupService.getShortsObserve());
        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mGroupService.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mGroupService.getHistoryObserve());
        mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupService.getSubscribedChannelsByUpdateObserve());
        mGridMapping.put(MediaGroup.TYPE_NOTIFICATIONS, mNotificationsService.getNotificationItemsObserve());
    }

    private void initPinnedSections() {
        mSections.clear();

        Collection<Video> pinnedItems = mGeneralData.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null) {
                if (item.extra == -1) {
                    BrowseSection section = new BrowseSection(item.hashCode(), item.title, BrowseSection.TYPE_GRID, item.cardImageUrl, false, item);
                    mSections.add(section);
                } else {
                    BrowseSection section = mSectionsMapping.get(item.extra);

                    if (section != null) {
                        mSections.add(section);
                    }
                }
            }
        }
    }

    private void initPinnedCallbacks() {
        Collection<Video> pinnedItems = mGeneralData.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null && item.extra == -1) {
                mGridMapping.put(item.hashCode(), createPinnedAction(item));
            }
        }
    }

    private void initSettingsSubCategories() {
        mSettingsGridMapping.put(MediaGroup.TYPE_SETTINGS, () -> mDataSourcePresenter.getSettingItems(getContext()));
    }

    private void updateSections() {
        if (getView() == null) {
            return;
        }

        initPinnedSections();
        initPinnedCallbacks();

        int index = 0;

        //sortSections();

        for (BrowseSection section : mErrorSections) {
            getView().addSection(index++, section);
        }

        for (BrowseSection section : mSections) { // contains sections and pinned items!
            section.setEnabled(section.getId() == MediaGroup.TYPE_SETTINGS || mGeneralData.isSectionEnabled(section.getId()));

            if (section.isEnabled()) {
                if (section.getId() == mGeneralData.getBootSectionId()) {
                    mBootSectionIndex = index;
                }
                getView().addSection(index++, section);
            } else {
                getView().removeSection(section);
            }
        }
    }

    private void sortSections() {
        // NOTE: Comparator.comparingInt API >= 24
        Collections.sort(mSections, (o1, o2) -> {
            return mGeneralData.getSectionIndex(o1.getId()) - mGeneralData.getSectionIndex(o2.getId());
        });
    }

    public void updateChannelSorting() {
        int sortingType = mMainUIData.getChannelCategorySorting();

        switch (sortingType) {
            case MainUIData.CHANNEL_SORTING_DEFAULT:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupService.getSubscribedChannelsObserve());
                break;
            case MainUIData.CHANNEL_SORTING_NAME2:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupService.getSubscribedChannelsByName2Observe());
                break;
            case MainUIData.CHANNEL_SORTING_NAME:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupService.getSubscribedChannelsByNameObserve());
                break;
            case MainUIData.CHANNEL_SORTING_NEW_CONTENT:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupService.getSubscribedChannelsByUpdateObserve());
                break;
            case MainUIData.CHANNEL_SORTING_LAST_VIEWED:
                mGridMapping.put(MediaGroup.TYPE_CHANNEL_UPLOADS, mGroupService.getSubscribedChannelsByViewedObserve());
                break;
        }
    }

    public void updatePlaylistsStyle() {
        int playlistsStyle = mMainUIData.getPlaylistsStyle();

        switch (playlistsStyle) {
            case MainUIData.PLAYLISTS_STYLE_GRID:
                mRowMapping.remove(MediaGroup.TYPE_USER_PLAYLISTS);
                mGridMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, mGroupService.getEmptyPlaylistsObserve());
                updateCategoryType(MediaGroup.TYPE_USER_PLAYLISTS, BrowseSection.TYPE_GRID);
                break;
            case MainUIData.PLAYLISTS_STYLE_ROWS:
                mGridMapping.remove(MediaGroup.TYPE_USER_PLAYLISTS);
                mRowMapping.put(MediaGroup.TYPE_USER_PLAYLISTS, mGroupService.getPlaylistsObserve());
                updateCategoryType(MediaGroup.TYPE_USER_PLAYLISTS, BrowseSection.TYPE_ROW);
                break;
        }
    }

    private void updateCategoryType(int categoryId, int categoryType) {
        if (categoryType == -1 || categoryId == -1 || mSections == null) {
            return;
        }

        BrowseSection section = mSectionsMapping.get(categoryId);

        if (section != null) {
            section.setType(categoryType);
        }

        for (BrowseSection category : mSections) {
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
        saveSelectedItems();
    }

    @Override
    public void onVideoItemSelected(Video item) {
        if (getView() == null) {
            return;
        }

        if (belongsToChannelUploadsMultiGrid(item)) {
            if (mMainUIData.isUploadsAutoLoadEnabled()) {
                updateChannelUploadsMultiGrid(item);
            } else {
                updateChannelUploadsMultiGrid(null); // clear
            }
        }

        mCurrentVideo = item;

        if (isSubscriptionsSection()) {
            mSelectedVideo = item;
        }
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (getContext() == null) {
            return;
        }

        // Check that channels new look enabled and we're on the first column
        if (belongsToChannelUploadsMultiGrid(item)) {
            updateChannelUploadsMultiGrid(item);
            //ChannelPresenter.instance(getContext()).openChannel(item);
        } else {
            VideoActionPresenter.instance(getContext()).apply(item);
        }

        updateRefreshTime();
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (getContext() == null) {
            return;
        }

        if (belongsToChannelUploads(item)) { // We need to be sure we exactly on Channels section
            ChannelUploadsMenuPresenter.instance(getContext()).showMenu(item, (videoItem, action) -> {
                if (action == VideoMenuCallback.ACTION_UNSUBSCRIBE) { // works with any uploads section look
                    removeItem(item);
                }
            });
        } else {
            VideoMenuPresenter.instance(getContext()).showMenu(item, (videoItem, action) -> {
                if (action == VideoMenuCallback.ACTION_REMOVE ||
                    action == VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST) {
                    removeItem(videoItem);
                } else if (action == VideoMenuCallback.ACTION_UNSUBSCRIBE && isMultiGridChannelUploadsSection()) {
                    removeItem(mCurrentVideo);
                    VideoMenuPresenter.instance(getContext()).closeDialog();
                } else if (action == VideoMenuCallback.ACTION_UNSUBSCRIBE && isSubscriptionsSection()) {
                    removeItemAuthor(videoItem);
                    VideoMenuPresenter.instance(getContext()).closeDialog();
                }
            });
        }

        updateRefreshTime();
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        VideoGroup group = item.getGroup();

        if (group == null || group.getMediaGroup() == null) {
            Log.e(TAG, "Can't scroll. Video group is null.");
            return;
        }

        if (mLastScrollGroup == group.getMediaGroup()) {
            Log.d(TAG, "Can't continue group. Another action is running.");
            return;
        }

        mLastScrollGroup = group.getMediaGroup();

        Log.d(TAG, "onScrollEnd. Group title: " + group.getTitle());

        continueGroup(group);
    }

    @Override
    public void onSectionFocused(int sectionId) {
        updateSection(sectionId);
    }

    @Override
    public void onSectionLongPressed(int sectionId) {
        SectionMenuPresenter.instance(getContext()).showMenu(findSectionById(sectionId));
    }

    @Override
    public boolean hasPendingActions() {
        return RxHelper.isAnyActionRunning(mActions);
    }

    public boolean isItemPinned(Video item) {
        Collection<Video> items = mGeneralData.getPinnedItems();

        return items.contains(item);
    }

    public void moveSectionUp(BrowseSection section) {
        mGeneralData.moveSectionUp(section.getId());
        updateSections();

        // Move current focus
        if (getView() != null) {
            getView().selectSection(findSectionIndex(section.getId()), false);
        }
    }

    public void moveSectionDown(BrowseSection section) {
        mGeneralData.moveSectionDown(section.getId());
        updateSections();

        // No need to move focus because section should be already focused.
    }

    public boolean canMoveSectionUp(BrowseSection section) {
        return mGeneralData.canMoveSectionUp(section.getId());
    }

    public boolean canMoveSectionDown(BrowseSection section) {
        return mGeneralData.canMoveSectionDown(section.getId());
    }

    public void renameSection(BrowseSection section) {
        mGeneralData.renameSection(section.getId(), section.getTitle());
        updateSections();
    }

    public void enableAllSections(boolean enable) {
        enableSection(MediaGroup.TYPE_HISTORY, enable);
        enableSection(MediaGroup.TYPE_USER_PLAYLISTS, enable);
        enableSection(MediaGroup.TYPE_SUBSCRIPTIONS, enable);
        enableSection(MediaGroup.TYPE_CHANNEL_UPLOADS, enable);
        enableSection(MediaGroup.TYPE_GAMING, enable);
        enableSection(MediaGroup.TYPE_MUSIC, enable);
        enableSection(MediaGroup.TYPE_NEWS, enable);
        enableSection(MediaGroup.TYPE_HOME, enable);
        enableSection(MediaGroup.TYPE_TRENDING, enable);
        enableSection(MediaGroup.TYPE_SHORTS, enable);
    }

    public void enableSection(int sectionId, boolean enable) {
        mGeneralData.enableSection(sectionId, enable);

        if (enable) {
            updateSections();
        } else {
            if (getView() != null) {
                getView().removeSection(mSectionsMapping.get(sectionId));
            }
        }
    }

    public void pinItem(Video item) {
        mGeneralData.addPinnedItem(item);

        updateSections();
    }

    public void pinItem(String title, int resId, ErrorFragmentData data) {
        int id = title.hashCode();
        Helpers.removeIf(mErrorSections, section -> section.getId() == id);
        mErrorSections.add(new BrowseSection(id, title, BrowseSection.TYPE_ERROR, resId, false, data));

        updateSections();
    }

    public void unpinItem(Video item) {
        mGeneralData.removePinnedItem(item);

        BrowseSection section = null;

        for (BrowseSection cat : mSections) {
            if (cat.getId() == item.hashCode()) {
                section = cat;
                break;
            }
        }

        mGridMapping.remove(item.hashCode());

        if (getView() != null) {
            getView().removeSection(section);
        }
    }

    private void maybeRefreshHeader() {
        long timeAfterPauseMs = System.currentTimeMillis() - mLastUpdateTimeMs;
        if (timeAfterPauseMs > HEADER_REFRESH_PERIOD_MS) { // update header every n minutes
            refresh();
        }
    }

    public void refresh() {
        refresh(true);
    }

    public void refresh(boolean focusOnContent) {
        if (mCurrentSection != null) {
            updateSection(mCurrentSection.getId());
            if (focusOnContent && getView() != null) {
                getView().focusOnContent();
            }
        }
    }

    private void updateRefreshTime() {
        mLastUpdateTimeMs = System.currentTimeMillis();
    }

    private void updateSection(int sectionId) {
        disposeActions();

        mCurrentSection = findSectionById(sectionId);

        if (getView() == null || mCurrentSection == null) {
            return;
        }

        Log.d(TAG, "Update section %s", mCurrentSection.getTitle());
        updateSection(mCurrentSection);
    }

    private void updateSection(BrowseSection section) {
        switch (section.getType()) {
            case BrowseSection.TYPE_GRID:
            case BrowseSection.TYPE_SHORTS_GRID:
                Observable<MediaGroup> group = mGridMapping.get(section.getId());
                updateVideoGrid(section, group, section.isAuthOnly());
                break;
            case BrowseSection.TYPE_ROW:
                Observable<List<MediaGroup>> groups = mRowMapping.get(section.getId());
                updateVideoRows(section, groups, section.isAuthOnly());
                break;
            case BrowseSection.TYPE_SETTINGS_GRID:
                Callable<List<SettingsItem>> items = mSettingsGridMapping.get(section.getId());
                updateSettingsGrid(section, items);
                break;
            case BrowseSection.TYPE_MULTI_GRID:
                Observable<MediaGroup> group2 = mGridMapping.get(section.getId());
                updateVideoGrid(section, group2, 0, section.isAuthOnly());
                break;
            case BrowseSection.TYPE_ERROR:
                getView().showProgressBar(false);
                break;
        }

        updateRefreshTime();
    }

    private void updateSettingsGrid(BrowseSection section, Callable<List<SettingsItem>> items) {
        getView().updateSection(SettingsGroup.from(Helpers.get(items), section));
        getView().showProgressBar(false);
    }

    private void updateVideoRows(BrowseSection section, Observable<List<MediaGroup>> groups, boolean authCheck) {
        Log.d(TAG, "loadRowsHeader: Start loading section: " + section.getTitle());

        authCheck(authCheck, () -> updateVideoRows(section, groups));
    }

    private void updateVideoGrid(BrowseSection section, Observable<MediaGroup> group, boolean authCheck) {
        updateVideoGrid(section, group, -1, authCheck);
    }

    private void updateVideoGrid(BrowseSection section, Observable<MediaGroup> group, int position, boolean authCheck) {
        Log.d(TAG, "loadMultiGridHeader: Start loading section: " + section.getTitle());

        authCheck(authCheck, () -> updateVideoGrid(section, group, position));
    }

    private void updateVideoRows(BrowseSection section, Observable<List<MediaGroup>> groups) {
        Log.d(TAG, "updateRowsHeader: Start loading section: " + section.getTitle());

        disposeActions();
        
        getView().showProgressBar(true);

        VideoGroup firstGroup = VideoGroup.from(section);
        firstGroup.setAction(VideoGroup.ACTION_REPLACE);
        getView().updateSection(firstGroup);

        Disposable updateAction = groups
                .subscribe(
                        mediaGroups -> {
                            filterIfNeeded(mediaGroups);

                            for (MediaGroup mediaGroup : mediaGroups) {
                                if (mediaGroup.isEmpty()) {
                                    Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                                    continue;
                                }
                                
                                // Move to top isn't working properly (too slow)
                                //VideoGroup videoGroup = VideoGroup.from(mediaGroup, section, moveToTopIfNeeded(mediaGroup));
                                VideoGroup videoGroup = VideoGroup.from(mediaGroup, section);

                                getView().updateSection(videoGroup);

                                continueGroupIfNeeded(videoGroup);
                            }

                            // Hide loading as long as first group received
                            if (!mediaGroups.isEmpty()) {
                                getView().showProgressBar(false);
                            }
                        },
                        error -> {
                            Log.e(TAG, "updateRowsHeader error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                            if (getView() != null && getView().isEmpty()) {
                                getView().showError(new CategoryEmptyError(getContext()));
                                Utils.postDelayed(mRefreshSection, 30_000);
                            }
                        },
                        () -> {
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        });

        mActions.add(updateAction);
    }

    private void updateVideoGrid(BrowseSection section, Observable<MediaGroup> group, int position) {
        disposeActions();

        if (getView() == null) {
            Log.e(TAG, "Browse view has been unloaded from the memory. Low RAM?");
            ViewManager.instance(getContext()).startView(BrowseView.class);
            return;
        }

        Log.d(TAG, "updateGridHeader: Start loading section: " + section.getTitle());

        getView().showProgressBar(true);

        VideoGroup firstGroup = VideoGroup.from(section, position);
        firstGroup.setAction(VideoGroup.ACTION_REPLACE);
        getView().updateSection(firstGroup);

        if (group == null) {
            // No group. Maybe just clear.
            getView().showProgressBar(false);
            return;
        }

        if (isSubscriptionsSection() && mGeneralData.isRememberSubscriptionsPositionEnabled()) {
            getView().selectSectionItem(mSelectedVideo);
        }

        Disposable updateAction = group
                .subscribe(
                        mediaGroup -> {
                            if (getView() == null) {
                                Log.e(TAG, "Browse view has been unloaded from the memory. Low RAM?");
                                ViewManager.instance(getContext()).startView(BrowseView.class);
                                return;
                            }

                            VideoGroup videoGroup = VideoGroup.from(mediaGroup, section, position);
                            getView().updateSection(videoGroup);

                            //// Hide loading as long as first group received
                            //if (mediaGroup.getMediaItems() != null) {
                            //    getView().showProgressBar(false);
                            //}

                            continueGroupIfNeeded(videoGroup);
                        },
                        error -> {
                            Log.e(TAG, "updateGridHeader error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                            if (getView() != null && getView().isEmpty()) {
                                getView().showError(new CategoryEmptyError(getContext()));
                                Utils.postDelayed(mRefreshSection, 30_000);
                            }
                        },
                        () -> {
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        });

        mActions.add(updateAction);
    }

    private void continueGroup(VideoGroup group) {
        if (getView() == null) {
            Log.e(TAG, "Can't continue group. The view is null.");
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        // Small amount of items == small load time. Loading bar are useless?
        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        if (mediaGroup == null) {
            Log.e(TAG, "Can't continue group. MediaGroup is null.");
            return;
        }

        Observable<MediaGroup> continuation;

        if (mediaGroup.getType() == MediaGroup.TYPE_SUGGESTIONS) { // Pinned playlist
            continuation = mItemService.continueGroupObserve(mediaGroup);
        } else {
            continuation = mGroupService.continueGroupObserve(mediaGroup);
        }

        Disposable continueAction = continuation
                .subscribe(
                        continueGroup -> {
                            //VideoGroup videoGroup = VideoGroup.from(continueGroup, group.getSection(), group.getPosition());
                            VideoGroup videoGroup = VideoGroup.from(continueGroup, group);
                            getView().updateSection(videoGroup);

                            continueGroupIfNeeded(videoGroup);
                        },
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        },
                        () -> {
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        }
                );

        mActions.add(continueAction);
    }

    private void authCheck(boolean check, Runnable callback) {
        if (!check) {
            callback.run();
            return;
        }

        getView().showProgressBar(true);

        Disposable signCheckAction = mSignInService.isSignedObserve()
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

        mActions.add(signCheckAction);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    private void continueGroupIfNeeded(VideoGroup group) {
        MediaServiceManager.instance().shouldContinueTheGroup(
                getContext(), group, () -> continueGroup(group), false,
                isGridSection()
        );
    }

    private void disposeActions() {
        RxHelper.disposeActions(mActions);
        Utils.removeCallbacks(mRefreshSection);
        mLastScrollGroup = null;
        mLastUpdateTimeMs = 0;
    }

    private void updateChannelUploadsMultiGrid(Video item) {
        if (mCurrentSection == null) {
            return;
        }

        updateVideoGrid(mCurrentSection, ChannelUploadsPresenter.instance(getContext()).obtainPlaylistObservable(item), 1, true);
        //ChannelPresenter.instance(getContext()).obtainUploadsRowObservable(item, row -> updateVideoGrid(mCurrentSection, row, 1, true));
    }

    private boolean belongsToChannelUploadsMultiGrid(Video item) {
        return isMultiGridChannelUploadsSection() && belongsToChannelUploads(item);
    }

    private boolean belongsToChannelUploads(Video item) {
        return item.belongsToChannelUploads() && !item.hasVideo();
    }

    private BrowseSection findSectionById(int sectionId) {
        for (BrowseSection section : mErrorSections) {
            if (section.getId() == sectionId) {
                return section;
            }
        }

        for (BrowseSection section : mSections) {
            if (section.getId() == sectionId) {
                return section;
            }
        }

        return null;
    }

    private int findSectionIndex(int sectionId) {
        if (sectionId == -1) {
            return -1;
        }

        int sectionIndex = -1;

        for (BrowseSection section : mErrorSections) {
            if (section.isEnabled()) {
                sectionIndex++;
                if (section.getId() == sectionId) {
                    return sectionIndex;
                }
            }
        }

        for (BrowseSection section : mSections) {
            if (section.isEnabled()) {
                sectionIndex++;
                if (section.getId() == sectionId) {
                    return sectionIndex;
                }
            }
        }

        return -1;
    }

    private void filterIfNeeded(List<MediaGroup> mediaGroups) {
        if (mediaGroups == null) {
            return;
        }

        Helpers.removeIf(mediaGroups, value -> Helpers.containsAny(
                value.getTitle(),
                "Primetime" // Free movies and shows row
        ));
        
        //Helpers.removeIf(mediaGroups, value -> Helpers.equalsAny(
        //        value.getTitle(),
        //        getContext().getString(R.string.breaking_news_row_name),
        //        getContext().getString(R.string.covid_news_row_name),
        //        getContext().getString(R.string.primetime_shows_row_name),
        //        getContext().getString(R.string.primetime_movies_row_name),
        //        getContext().getString(R.string.primetime_personal_row_name)
        //));
    }

    private int moveToTopIfNeeded(MediaGroup mediaGroup) {
        if (mediaGroup == null) {
            return -1;
        }

        return Helpers.equalsAny(mediaGroup.getTitle(), getContext().getString(R.string.trending_row_name)) ? 0 : -1;
    }

    //private Observable<MediaGroup> createPinnedAction(Video item) {
    //    return (item.hasPlaylist() || item.hasReloadPageKey()) ?
    //            ChannelUploadsPresenter.instance(getContext()).obtainPlaylistObservable(item) :
    //            mGroupManager.getChannelObserve(item.channelId).map(list -> {
    //                MediaGroup group = null;
    //
    //                // Default row is Uploads
    //                for (MediaGroup mediaGroup : list) {
    //                    if (mediaGroup != null && Helpers.equals(mediaGroup.getTitle(), getContext().getString(R.string.uploads_row_name))) {
    //                        group = mediaGroup;
    //                        break;
    //                    }
    //                }
    //
    //                return group != null ? group : list.get(0);
    //            });
    //}

    private Observable<MediaGroup> createPinnedAction(Video item) {
        return ChannelUploadsPresenter.instance(getContext()).obtainPlaylistObservable(item);
    }

    /**
     * Is Channels new look enabled?
     */
    public boolean isMultiGridChannelUploadsSection() {
        return mCurrentSection != null && mCurrentSection.getType() == BrowseSection.TYPE_MULTI_GRID && mCurrentSection.getId() == MediaGroup.TYPE_CHANNEL_UPLOADS;
    }

    public boolean isSettingsSection() {
        return mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_SETTINGS && inForeground();
    }

    public boolean isPlaylistsSection() {
        return mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_USER_PLAYLISTS && inForeground();
    }

    public boolean isHistorySection() {
        return mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_HISTORY && inForeground();
    }

    public boolean isSubscriptionsSection() {
        return mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_SUBSCRIPTIONS && inForeground();
    }

    public void selectSection(int sectionId) {
        ViewManager.instance(getContext()).startView(BrowseView.class); // focus view

        if (getView() == null) {
            mSelectedSectionId = sectionId;
            return;
        }

        int sectionIndex = findSectionIndex(sectionId);

        if (sectionIndex == -1) {
            enableSection(sectionId, true);
            sectionIndex = findSectionIndex(sectionId);
            mGeneralData.enableSection(sectionId, false); // enable temporally (till restart)
        }

        if (sectionIndex != -1) {
            getView().selectSection(sectionIndex, true);
        }
    }

    public boolean inForeground() {
        return ViewManager.instance(getContext()).getTopView() == BrowseView.class;
    }

    private boolean isGridSection() {
        return mCurrentSection != null && mCurrentSection.getType() != BrowseSection.TYPE_ROW;
    }
}
