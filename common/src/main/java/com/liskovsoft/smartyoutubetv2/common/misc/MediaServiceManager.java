package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;
import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.mediaserviceinterfaces.yt.ContentService;
import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.mediaserviceinterfaces.yt.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.yt.SignInService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.Account;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.yt.data.NotificationState;
import com.liskovsoft.mediaserviceinterfaces.yt.data.PlaylistInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaServiceManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static MediaServiceManager sInstance;
    private final MediaItemService mItemService;
    private final ContentService mContentService;
    private final SignInService mSingInService;
    private final NotificationsService mNotificationsService;
    private Disposable mMetadataAction;
    private Disposable mUploadsAction;
    private Disposable mSignCheckAction;
    private Disposable mRowsAction;
    private Disposable mSubscribedChannelsAction;
    private Disposable mFormatInfoAction;
    private Disposable mPlaylistGroupAction;
    private Disposable mAccountListAction;
    private Disposable mPlaylistInfosAction;
    private static final int MIN_GRID_GROUP_SIZE = 13;
    private static final int MIN_ROW_GROUP_SIZE = 5;
    private static final int MIN_SCALED_GRID_GROUP_SIZE = 35;
    private static final int MIN_SCALED_ROW_GROUP_SIZE = 10;
    private final Map<Integer, Pair<Integer, Long>> mContinuations = new HashMap<>();
    private final List<AccountChangeListener> mAccountListeners = new ArrayList<>();

    public interface OnMetadata {
        void onMetadata(MediaItemMetadata metadata);
    }

    public interface OnMediaGroup {
        void onMediaGroup(MediaGroup group);
    }

    public interface OnMediaGroupList {
        void onMediaGroupList(List<MediaGroup> groupList);
    }

    public interface OnFormatInfo {
        void onFormatInfo(MediaItemFormatInfo formatInfo);
    }

    public interface OnAccountList {
        void onAccountList(List<Account> accountList);
    }

    public interface OnPlaylistInfos {
        void onPlaylistInfos(List<PlaylistInfo> playlistInfos);
    }

    public interface AccountChangeListener {
        void onAccountChanged(Account account);
    }

    private MediaServiceManager() {
        MotherService service = YouTubeMotherService.instance();
        mItemService = service.getMediaItemService();
        mContentService = service.getContentService();
        mSingInService = service.getSignInService();
        mNotificationsService = service.getNotificationsService();

        mSingInService.setOnChange(
                () -> onAccountChanged(YouTubeSignInService.instance().getSelectedAccount())
        );
    }

    public static MediaServiceManager instance() {
        if (sInstance == null) {
            sInstance = new MediaServiceManager();
        }

        return sInstance;
    }

    public SignInService getSingInService() {
        return mSingInService;
    }

    public void loadMetadata(Video video, OnMetadata onMetadata) {
        if (video == null) {
            return;
        }

        RxHelper.disposeActions(mMetadataAction);

        Observable<MediaItemMetadata> observable;

        // NOTE: Load suggestions from mediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager.
        // Video might be loaded from Channels section (has playlistParams)
        if (video.mediaItem != null) {
            // Use additional data like playlist id
            observable = mItemService.getMetadataObserve(video.mediaItem);
        } else {
            // Simply load
            observable = mItemService.getMetadataObserve(video.videoId, video.getPlaylistId(), video.playlistIndex, video.playlistParams);
        }

        mMetadataAction = observable
                .subscribe(
                        onMetadata::onMetadata,
                        error -> Log.e(TAG, "loadMetadata error: %s", error.getMessage())
                );
    }

    /**
     * NOTE: Load suggestions from MediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager.
     */
    public void loadMetadata(MediaItem mediaItem, OnMetadata onMetadata) {
        if (mediaItem == null) {
            return;
        }

        RxHelper.disposeActions(mMetadataAction);

        Observable<MediaItemMetadata> observable;

        observable = mItemService.getMetadataObserve(mediaItem);

        mMetadataAction = observable
                .subscribe(
                        onMetadata::onMetadata,
                        error -> Log.e(TAG, "loadMetadata error: %s", error.getMessage())
                );
    }

    public void loadChannelUploads(Video item, OnMediaGroup onMediaGroup) {
        if (item == null) {
            return;
        }

        loadChannelUploads(item.mediaItem, onMediaGroup);
    }

    public void loadChannelUploads(MediaItem item, OnMediaGroup onMediaGroup) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mUploadsAction);

        Observable<MediaGroup> observable = mContentService.getGroupObserve(item);

        mUploadsAction = observable
                .subscribe(
                        onMediaGroup::onMediaGroup,
                        error -> {
                            onMediaGroup.onMediaGroup(null);
                            Log.e(TAG, "loadChannelUploads error: %s", error.getMessage());
                        }
                );
    }

    public void loadSubscribedChannels(OnMediaGroup onMediaGroup) {
        RxHelper.disposeActions(mSubscribedChannelsAction);

        Observable<MediaGroup> observable = mContentService.getSubscribedChannelsByUpdateObserve();

        mSubscribedChannelsAction = observable
                .subscribe(
                        onMediaGroup::onMediaGroup,
                        error -> Log.e(TAG, "loadSubscribedChannels error: %s", error.getMessage())
                );
    }

    public void loadChannelRows(Video item, OnMediaGroupList onMediaGroupList) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mRowsAction);

        Observable<List<MediaGroup>> observable = item.mediaItem != null ?
                mContentService.getChannelObserve(item.mediaItem) : mContentService.getChannelObserve(item.channelId);

        mRowsAction = observable
                .subscribe(
                        onMediaGroupList::onMediaGroupList,
                        error -> Log.e(TAG, "loadChannelRows error: %s", error.getMessage())
                );
    }

    public void loadChannelPlaylist(Video item, OnMediaGroup callback) {
        loadChannelRows(
                item,
                mediaGroupList -> callback.onMediaGroup(mediaGroupList.get(0))
        );
    }

    public void loadFormatInfo(Video item, OnFormatInfo onFormatInfo) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mFormatInfoAction);

        Observable<MediaItemFormatInfo> observable = mItemService.getFormatInfoObserve(item.videoId);

        mFormatInfoAction = observable
                .subscribe(
                        onFormatInfo::onFormatInfo,
                        error -> Log.e(TAG, "loadFormatInfo error: %s", error.getMessage())
                );
    }

    public void loadPlaylists(Video item, OnMediaGroup onPlaylistGroup) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mPlaylistGroupAction);

        Observable<MediaGroup> observable = mContentService.getEmptyPlaylistsObserve();

        mPlaylistGroupAction = observable
                .subscribe(
                        onPlaylistGroup::onMediaGroup,
                        error -> Log.e(TAG, "loadPlaylists error: %s", error.getMessage())
                );
    }

    public void getPlaylistInfos(OnPlaylistInfos onPlaylistInfos) {
        RxHelper.disposeActions(mPlaylistInfosAction);

        Observable<List<PlaylistInfo>> observable = mItemService.getPlaylistsInfoObserve(null);

        mPlaylistInfosAction = observable
                .subscribe(
                        onPlaylistInfos::onPlaylistInfos,
                        error -> Log.e(TAG, "getPlaylistInfos error: %s", error.getMessage())
                );
    }

    public void loadAccounts(OnAccountList onAccountList) {
        RxHelper.disposeActions(mAccountListAction);

        mAccountListAction = mSingInService.getAccountsObserve()
                .subscribe(
                        onAccountList::onAccountList,
                        error -> Log.e(TAG, "Get signed accounts error: %s", error.getMessage())
                );
    }

    public void authCheck(Runnable onSuccess, Runnable onError) {
        if (onSuccess == null && onError == null) {
            return;
        }

        RxHelper.disposeActions(mSignCheckAction);

        mSignCheckAction = mSingInService.isSignedObserve()
                .subscribe(
                        isSigned -> {
                            if (isSigned) {
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            } else {
                                if (onError != null) {
                                    onError.run();
                                }
                            }
                        },
                        error -> Log.e(TAG, "Sign check error: %s", error.getMessage())
                );

    }

    public void disposeActions() {
        RxHelper.disposeActions(mMetadataAction, mUploadsAction, mSignCheckAction);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public void shouldContinueGridGroup(Context context, VideoGroup group, Runnable onNeedContinue) {
        shouldContinueTheGroup(context, group, onNeedContinue, true);
    }

    public void shouldContinueRowGroup(Context context, VideoGroup group, Runnable onNeedContinue) {
        shouldContinueTheGroup(context, group, onNeedContinue, false);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public void shouldContinueTheGroup(Context context, VideoGroup group, Runnable onNeedContinue, boolean isGrid) {
        if (shouldContinueTheGroup(context, group, isGrid) && onNeedContinue != null) {
            onNeedContinue.run();
        }
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public boolean shouldContinueGridGroup(Context context, VideoGroup group) {
        return shouldContinueTheGroup(context, group, true);
    }

    public boolean shouldContinueRowGroup(Context context, VideoGroup group) {
        return shouldContinueTheGroup(context, group,false);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public boolean shouldContinueTheGroup(Context context, VideoGroup group, boolean isGrid) {
        if (group == null || group.getMediaGroup() == null) {
            return false;
        }

        MediaGroup mediaGroup = group.getMediaGroup();

        Pair<Integer, Long> sizeTimestamp = mContinuations.get(group.getId());

        long currentTimeMillis = System.currentTimeMillis();
        if (sizeTimestamp != null && currentTimeMillis - sizeTimestamp.second > 3_000) { // seems that section is refreshed
            sizeTimestamp = null;
        }

        int prevSize = sizeTimestamp != null ? sizeTimestamp.first : 0;
        int newSize = mediaGroup.getMediaItems() != null ? mediaGroup.getMediaItems().size() : 0;
        int totalSize = prevSize + newSize;

        MainUIData mainUIData = MainUIData.instance(context);

        boolean isScaledUIEnabled = mainUIData.getUIScale() < 0.8f || mainUIData.getVideoGridScale() < 0.8f;
        int minScaledSize = isGrid ? MIN_SCALED_GRID_GROUP_SIZE : MIN_SCALED_ROW_GROUP_SIZE;
        int minSize = isGrid ? MIN_GRID_GROUP_SIZE : MIN_ROW_GROUP_SIZE;
        boolean groupTooSmall = isScaledUIEnabled ? totalSize < minScaledSize : totalSize < minSize;

        mContinuations.put(group.getId(), new Pair<>(groupTooSmall ? totalSize : 0, currentTimeMillis));

        return groupTooSmall;
    }

    public void enableHistory(boolean enable) {
        RxHelper.runAsyncUser(() -> mContentService.enableHistory(enable));
    }

    public void clearHistory() {
        RxHelper.runAsyncUser(mContentService::clearHistory);
    }

    public void clearSearchHistory() {
        RxHelper.runAsyncUser(mContentService::clearSearchHistory);
    }

    public void updateHistory(Video video, long positionMs) {
        if (video == null) {
            return;
        }

        Observable<Void> historyObservable;

        if (video.mediaItem != null) {
            historyObservable = mItemService.updateHistoryPositionObserve(video.mediaItem, positionMs / 1_000f);
        } else { // video launched form ATV channels
            historyObservable = mItemService.updateHistoryPositionObserve(video.videoId, positionMs / 1_000f);
        }

        RxHelper.execute(historyObservable);
    }

    public void hideNotification(Video item) {
        if (item != null && item.belongsToNotifications()) {
            RxHelper.execute(mNotificationsService.hideNotificationObserve(item.mediaItem));
        }
    }

    public void setNotificationState(NotificationState state) {
        RxHelper.execute(mNotificationsService.setNotificationStateObserve(state));
    }

    public void addAccountListener(AccountChangeListener listener) {
        if (!mAccountListeners.contains(listener)) {
            if (listener instanceof AccountsData ||
                listener instanceof AppPrefs) {
                mAccountListeners.add(0, listener); // data classes should be called before regular listeners
            } else {
                mAccountListeners.add(listener);
            }
        }
    }

    public void removeAccountListener(AccountChangeListener listener) {
        mAccountListeners.remove(listener);
    }

    public Account getSelectedAccount() {
        return YouTubeSignInService.instance().getSelectedAccount();
    }

    private void onAccountChanged(Account account) {
        for (AccountChangeListener listener : mAccountListeners) {
            listener.onAccountChanged(account);
        }
    }
}
