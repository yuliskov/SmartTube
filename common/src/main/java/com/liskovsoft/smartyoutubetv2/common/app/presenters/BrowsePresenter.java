package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.CategoryPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
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

public class BrowsePresenter implements CategoryPresenter, VideoGroupPresenter, Presenter<BrowseView> {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    private static final long HEADER_REFRESH_PERIOD_MS = 120 * 60 * 1_000;
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final PlaybackPresenter mPlaybackPresenter;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private BrowseView mView;
    private final List<Category> mCategories;
    private final Map<Integer, Observable<MediaGroup>> mGridMapping;
    private final Map<Integer, Observable<List<MediaGroup>>> mRowMapping;
    private final Map<Integer, List<SettingsItem>> mTextGridMapping;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Disposable mSignCheckAction;
    private long mCurrentCategoryId = -1;
    private long mLastUpdateTimeMs;

    private BrowsePresenter(Context context) {
        GlobalPreferences.instance(context); // auth token storage
        mContext = context;
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
        mCategories = new ArrayList<>();
        mGridMapping = new HashMap<>();
        mRowMapping = new HashMap<>();
        mTextGridMapping = new HashMap<>();
        initCategories();
    }

    public static BrowsePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BrowsePresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mView == null) {
            return;
        }

        addHeaders();
    }

    private void initCategories() {
        initCategoryHeaders();

        initVideoCategories();

        initSettingsCategories();
    }

    private void initCategoryHeaders() {
        mCategories.add(new Category(MediaGroup.TYPE_HOME, mContext.getString(R.string.header_home), Category.TYPE_ROW, R.drawable.icon_home));
        mCategories.add(new Category(MediaGroup.TYPE_GAMING, mContext.getString(R.string.header_gaming), Category.TYPE_ROW, R.drawable.icon_gaming));
        mCategories.add(new Category(MediaGroup.TYPE_NEWS, mContext.getString(R.string.header_news), Category.TYPE_ROW, R.drawable.icon_news));
        mCategories.add(new Category(MediaGroup.TYPE_MUSIC, mContext.getString(R.string.header_music), Category.TYPE_ROW, R.drawable.icon_music));
        mCategories.add(new Category(MediaGroup.TYPE_SUBSCRIPTIONS, mContext.getString(R.string.header_subscriptions), Category.TYPE_GRID, R.drawable.icon_subscriptions, true));
        mCategories.add(new Category(MediaGroup.TYPE_HISTORY, mContext.getString(R.string.header_history), Category.TYPE_GRID, R.drawable.icon_history, true));
        mCategories.add(new Category(MediaGroup.TYPE_PLAYLISTS, mContext.getString(R.string.header_playlists), Category.TYPE_ROW, R.drawable.icon_playlist, true));
        mCategories.add(new Category(MediaGroup.TYPE_SETTINGS, mContext.getString(R.string.header_settings), Category.TYPE_TEXT_GRID, R.drawable.icon_settings));
    }

    private void initVideoCategories() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mRowMapping.put(MediaGroup.TYPE_HOME, mediaGroupManager.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mediaGroupManager.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mediaGroupManager.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mediaGroupManager.getGamingObserve());
        mRowMapping.put(MediaGroup.TYPE_PLAYLISTS, mediaGroupManager.getPlaylistsObserve());

        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mediaGroupManager.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mediaGroupManager.getHistoryObserve());
    }

    private void initSettingsCategories() {
        List<SettingsItem> settingItems = new ArrayList<>();
        
        settingItems.add(new SettingsItem(
                mContext.getString(R.string.settings_accounts), () -> AccountSettingsPresenter.instance(mContext).show(), R.drawable.action_channel));
        //settingItems.add(new SettingsItem(
        //        mContext.getString(R.string.settings_language), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented)));
        //settingItems.add(new SettingsItem(
        //        mContext.getString(R.string.settings_linked_devices), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented)));
        //settingItems.add(new SettingsItem(
        //        mContext.getString(R.string.settings_left_panel), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented)));
        //settingItems.add(new SettingsItem(
        //        mContext.getString(R.string.settings_themes), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented)));
        //settingItems.add(new SettingsItem(
        //        mContext.getString(R.string.settings_player), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented)));
        //settingItems.add(new SettingsItem(
        //        mContext.getString(R.string.settings_other), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented)));
        settingItems.add(new SettingsItem(
                mContext.getString(R.string.settings_about), () -> MessageHelpers.showMessage(mContext, R.string.not_implemented), R.mipmap.app_icon));

        mTextGridMapping.put(MediaGroup.TYPE_SETTINGS, settingItems);
    }

    private void addHeaders() {
        for (Category category : mCategories) {
            mView.addCategory(category);
        }
    }

    @Override
    public void register(BrowseView view) {
        mView = view;
    }

    @Override
    public void unregister(BrowseView view) {
        mView = null;
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (mView == null) {
            return;
        }

        if (item.isVideo()) {
            mPlaybackPresenter.openVideo(item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(mContext).openChannel(item);
        }

        updateRefreshTime();
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (mView == null) {
            return;
        }

        VideoMenuPresenter.instance(mContext).showMenu(item);
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

    @Override
    public void onViewResumed() {
        long timeAfterPauseMs = System.currentTimeMillis() - mLastUpdateTimeMs;
        if (timeAfterPauseMs > HEADER_REFRESH_PERIOD_MS) { // update header every n minutes
            refresh();
        }
    }

    @Override
    public void onCategoryFocused(long categoryId) {
        updateCategory(categoryId);
    }

    public void refresh() {
        updateCategory(mCurrentCategoryId);
    }

    private void updateRefreshTime() {
        mLastUpdateTimeMs = System.currentTimeMillis();
    }

    private void updateCategory(long categoryId) {
        mCurrentCategoryId = categoryId;

        if (categoryId == -1 || mView == null) {
            return;
        }

        RxUtils.disposeActions(mUpdateAction, mScrollAction, mSignCheckAction);

        for (Category category : mCategories) {
            if (category.getId() == categoryId) {
                mView.showProgressBar(true);
                mView.clearCategory(category);
                updateCategory(category);
            }
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
            case Category.TYPE_TEXT_GRID:
                List<SettingsItem> items = mTextGridMapping.get(category.getId());
                updateTextGrid(category, items);
                break;
        }
    }

    private void updateTextGrid(Category category, List<SettingsItem> items) {
        mView.updateCategory(SettingsGroup.from(items, category));
        mView.showProgressBar(false);
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
                        continueMediaGroup -> mView.updateCategory(VideoGroup.from(continueMediaGroup, group.getCategory()))
                        , error -> Log.e(TAG, "continueGroup error: " + error)
                        , () -> mView.showProgressBar(false));
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
                                mView.updateErrorIfEmpty(new SignInError(mContext));
                                mView.showProgressBar(false);
                            }
                        }
                );
                
    }

    private void updateVideoRows(Category category, Observable<List<MediaGroup>> groups) {
        Log.d(TAG, "updateRowsHeader: Start loading category: " + category.getTitle());

        mUpdateAction = groups
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroups -> updateVideoRows(category, mediaGroups)
                        , error -> Log.e(TAG, "updateRowsHeader error: " + error)
                        , () -> {
                            mView.showProgressBar(false);
                            mView.updateErrorIfEmpty(new CategoryEmptyError(mContext));
                        });
    }

    private void updateVideoRows(Category category, List<MediaGroup> mediaGroups) {
        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            mView.updateCategory(VideoGroup.from(mediaGroup, category));

            updateRefreshTime();
        }
    }

    private void updateVideoGrid(Category category, Observable<MediaGroup> group) {
        Log.d(TAG, "updateGridHeader: Start loading category: " + category.getTitle());

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> {
                            mView.updateCategory(VideoGroup.from(mediaGroup, category));
                            updateRefreshTime();
                        }
                        , error -> Log.e(TAG, "updateGridHeader error: " + error)
                        , () -> {
                            mView.showProgressBar(false);
                            mView.updateErrorIfEmpty(new CategoryEmptyError(mContext));
                        });
    }
}
