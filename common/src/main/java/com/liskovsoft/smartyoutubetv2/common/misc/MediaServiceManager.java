package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;
import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.data.Account;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaServiceManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static MediaServiceManager sInstance;
    private final MediaItemService mItemManager;
    private final MediaGroupService mGroupManager;
    private final SignInService mSingInManager;
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

    public MediaServiceManager() {
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemService();
        mGroupManager = service.getMediaGroupService();
        mSingInManager = service.getSignInService();
    }

    public static MediaServiceManager instance() {
        if (sInstance == null) {
            sInstance = new MediaServiceManager();
        }

        return sInstance;
    }

    public SignInService getSingInManager() {
        return mSingInManager;
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
            observable = mItemManager.getMetadataObserve(video.mediaItem);
        } else {
            // Simply load
            observable = mItemManager.getMetadataObserve(video.videoId, video.getPlaylistId(), video.playlistIndex, video.playlistParams);
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

        observable = mItemManager.getMetadataObserve(mediaItem);

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

        Observable<MediaGroup> observable = mGroupManager.getGroupObserve(item);

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

        Observable<MediaGroup> observable = mGroupManager.getSubscribedChannelsByUpdateObserve();

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
                mGroupManager.getChannelObserve(item.mediaItem) : mGroupManager.getChannelObserve(item.channelId);

        mRowsAction = observable
                .subscribe(
                        onMediaGroupList::onMediaGroupList,
                        error -> Log.e(TAG, "loadChannelRows error: %s", error.getMessage())
                );
    }

    public void loadChannelPlaylist(Video item, OnMediaGroup group) {
        loadChannelRows(
                item,
                mediaGroupList -> group.onMediaGroup(mediaGroupList.get(0))
        );
    }

    public void loadFormatInfo(Video item, OnFormatInfo onFormatInfo) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mFormatInfoAction);

        Observable<MediaItemFormatInfo> observable = mItemManager.getFormatInfoObserve(item.videoId);

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

        Observable<MediaGroup> observable = mGroupManager.getEmptyPlaylistsObserve();

        mPlaylistGroupAction = observable
                .subscribe(
                        onPlaylistGroup::onMediaGroup,
                        error -> Log.e(TAG, "loadPlaylists error: %s", error.getMessage())
                );
    }

    public void getPlaylistInfos(OnPlaylistInfos onPlaylistInfos) {
        RxHelper.disposeActions(mPlaylistInfosAction);

        Observable<List<PlaylistInfo>> observable = mItemManager.getPlaylistsInfoObserve(null);

        mPlaylistInfosAction = observable
                .subscribe(
                        onPlaylistInfos::onPlaylistInfos,
                        error -> Log.e(TAG, "getPlaylistInfos error: %s", error.getMessage())
                );
    }

    public void loadAccounts(OnAccountList onAccountList) {
        RxHelper.disposeActions(mAccountListAction);

        mAccountListAction = mSingInManager.getAccountsObserve()
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

        mSignCheckAction = mSingInManager.isSignedObserve()
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
    public void shouldContinueTheGroup(Context context, VideoGroup group, Runnable onNeedContinue) {
        shouldContinueTheGroup(context, group, onNeedContinue, true);
    }

    public void shouldContinueTheGroup(Context context, VideoGroup group, Runnable onNeedContinue, boolean showLoading) {
        shouldContinueTheGroup(context, group, onNeedContinue, showLoading, false);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public void shouldContinueTheGroup(Context context, VideoGroup group, Runnable onNeedContinue, boolean showLoading, boolean isGrid) {
        if (group == null || group.getMediaGroup() == null) {
            return;
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

        if (groupTooSmall) {
            if (onNeedContinue != null) {
                onNeedContinue.run();
            }
        } else {
            totalSize = 0;
        }

        if (showLoading) {
            // Show loading on subscriptions if almost all videos are hidden
            LoadingManager.showLoading(context, groupTooSmall);
        }
        mContinuations.put(group.getId(), new Pair<>(totalSize, currentTimeMillis));
    }

    public void enableHistory(boolean enable) {
        RxHelper.runAsyncUser(() -> mGroupManager.enableHistory(enable));
    }

    public void clearHistory() {
        RxHelper.runAsyncUser(mGroupManager::clearHistory);
    }

    public void clearSearchHistory() {
        RxHelper.runAsyncUser(mGroupManager::clearSearchHistory);
    }

    public void updateHistory(Video video, long positionMs) {
        if (video == null) {
            return;
        }

        Observable<Void> historyObservable;

        if (video.mediaItem != null) {
            historyObservable = mItemManager.updateHistoryPositionObserve(video.mediaItem, positionMs / 1_000f);
        } else { // video launched form ATV channels
            historyObservable = mItemManager.updateHistoryPositionObserve(video.videoId, positionMs / 1_000f);
        }

        RxHelper.execute(historyObservable);
    }
}
