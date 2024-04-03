package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.yt.ContentService;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.DeArrowProcessor;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class ChannelPresenter extends BasePresenter<ChannelView> implements VideoGroupPresenter {
    private static final String TAG = ChannelPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelPresenter sInstance;
    private final MotherService mService;
    private final MediaServiceManager mServiceManager;
    private final DeArrowProcessor mDeArrowProcessor;
    private String mChannelId;
    private final List<List<MediaGroup>> mPendingGroups = new ArrayList<>();
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private int mSortIdx;
    private Video mChannel;

    private interface OnChannelId {
        void onChannelId(String channelId);
    }

    public interface OnUploadsRow {
        void onUploadsRow(Observable<MediaGroup> row);
    }

    public ChannelPresenter(Context context) {
        super(context);
        mService = YouTubeMotherService.instance();
        mServiceManager = MediaServiceManager.instance();
        mDeArrowProcessor = new DeArrowProcessor(getContext(), this::syncItem);
    }

    public static ChannelPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();

        if (mChannelId != null) {
            getView().clear();
            updateRows(mChannelId);
        } else if (!mPendingGroups.isEmpty()) {
            getView().clear();
            for (List<MediaGroup> group : mPendingGroups) {
                updateRows(group);
            }
            mPendingGroups.clear();
        }
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        disposeActions();
    }

    @Override
    public void onFinish() {
        super.onFinish();

        // Destroy the cache only (!) when user pressed back (e.g. wants to explicitly kill the activity)
        // Otherwise keep the cache to easily restore in case activity is killed by the system.
        mChannelId = null;
        mPendingGroups.clear();
    }

    @Override
    public void onVideoItemSelected(Video item) {
        // NOP
    }

    @Override
    public void onVideoItemClicked(Video item) {
        VideoActionPresenter.instance(getContext()).apply(item);
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getContext()).showMenu(item);
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        if (item.getGroup() == null) {
            Log.e(TAG, "Can't scroll. Video group is null.");
            return;
        }

        VideoGroup group = item.getGroup();

        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        continueGroup(group);
    }

    @Override
    public boolean hasPendingActions() {
        return RxHelper.isAnyActionRunning(mScrollAction, mUpdateAction);
    }

    public static boolean canOpenChannel(Video item) {
        if (item == null) {
            return false;
        }

        return item.videoId != null || item.channelId != null || item.belongsToChannelUploads();
    }

    public void openChannel(Video item) {
        mChannel = item;
        extractChannelId(item, this::openChannel);
    }

    public void openChannel(String channelId) {
        if (channelId == null) {
            return;
        }

        disposeActions();

        mChannelId = channelId;

        if (getView() != null) {
            getView().clear();
            updateRows(channelId);
            // Fix double results. Prevent from doing the same in onViewInitialized()
            mChannelId = null;
        }

        ViewManager.instance(getContext()).startView(ChannelView.class);
    }

    public String getChannelId() {
        return mChannel != null ? mChannel.channelId : mChannelId;
    }

    public void setChannelId(String channelId) {
        mChannelId = channelId;
    }

    public Video getChannel() {
        return mChannel;
    }

    public void setChannel(Video channel) {
        mChannel = channel;
    }

    private void disposeActions() {
        RxHelper.disposeActions(mUpdateAction, mScrollAction);
        mServiceManager.disposeActions();
        mSortIdx = 0;
    }

    private void updateRows(String channelId) {
        if (channelId == null) {
            return;
        }

        Log.d(TAG, "updateRows: Start loading...");

        getView().showProgressBar(true);

        Observable<List<MediaGroup>> channelObserve = obtainChannelObservable(channelId);

        mUpdateAction = channelObserve
                .subscribe(
                        this::updateRows,
                        error -> {
                            Log.e(TAG, "updateRows error: %s", error.getMessage());
                            getView().showProgressBar(false);
                        }
                 );
    }

    public Observable<List<MediaGroup>> obtainChannelObservable(String channelId) {
        return GeneralData.instance(getContext()).isOldChannelLookEnabled() ?
                mService.getContentService().getChannelV1Observe(channelId) :  mService.getContentService().getChannelObserve(channelId);
    }

    public void updateRows(List<MediaGroup> mediaGroups) {
        if (getView() == null) { // starting from outside (e.g. MediaServiceManager)
            disposeActions();
            mChannelId = null;
            mPendingGroups.add(mediaGroups);
            return;
        }

        if (GeneralData.instance(getContext()).isOldChannelLookEnabled()) {
            moveToTopIfNeeded(mediaGroups);
        }

        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "updateRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            VideoGroup group = VideoGroup.from(mediaGroup);
            getView().update(group);
            mDeArrowProcessor.process(group);
        }

        getView().showProgressBar(false);
    }

    private void continueGroup(VideoGroup group) {
        boolean scrollInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (scrollInProgress) {
            return;
        }

        if (getView() == null) {
            Log.e(TAG, "Can't continue group. The view is null.");
            return;
        }

        if (group == null) {
            Log.e(TAG, "Can't continue group. The group is null.");
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        ContentService contentService = mService.getContentService();

        mScrollAction = contentService.continueGroupObserve(mediaGroup)
                .subscribe(
                        continueMediaGroup -> {
                            VideoGroup newGroup = VideoGroup.from(group, continueMediaGroup);
                            getView().update(newGroup);
                            mDeArrowProcessor.process(newGroup);
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

    /**
     * Sort channel content: move Uploads on top.
     */
    private void moveToTopIfNeeded(List<MediaGroup> mediaGroups) {
        moveToTop(mediaGroups, R.string.playlists_row_name);
        moveToTop(mediaGroups, R.string.popular_uploads_row_name);
        moveToTop(mediaGroups, R.string.uploads_row_name);
        moveToTop(mediaGroups, R.string.live_now_row_name);
    }

    private void moveToTop(List<MediaGroup> mediaGroups, int rowNameResId) {
        if (rowNameResId <= 0) {
            return;
        }

        String rowName = getContext().getString(rowNameResId);

        List<MediaGroup> group = Helpers.removeIf(mediaGroups, value -> rowName.equals(value.getTitle()));

        if (group != null) {
            mediaGroups.addAll(0, group);
        }
    }

    public void clear() {
        if (getView() != null) {
            getView().clear();
        }
        mChannel = null;
        mChannelId = null;
    }

    private void extractChannelId(Video item, OnChannelId callback) {
        if (item != null) {
            if (item.channelId != null) {
                callback.onChannelId(item.channelId);
            } else if (item.videoId != null) {
                LoadingManager.showLoading(getContext(), true);
                //MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                mServiceManager.loadMetadata(item, metadata -> {
                    LoadingManager.showLoading(getContext(), false);
                    callback.onChannelId(metadata.getChannelId());
                    item.channelId = metadata.getChannelId();
                });
            } else if (item.belongsToChannelUploads()) {
                LoadingManager.showLoading(getContext(), true);
                // Maybe this is subscribed items view
                ChannelUploadsPresenter.instance(getContext())
                        .obtainVideoGroup(item, group -> {
                            LoadingManager.showLoading(getContext(), false);
                            // Some uploads groups doesn't contain channel button.
                            // Use data from first item instead.
                            if (group.getChannelId() == null) {
                                List<MediaItem> mediaItems = group.getMediaItems();

                                if (mediaItems != null && mediaItems.size() > 0) {
                                    extractChannelId(Video.from(mediaItems.get(0)), callback);
                                }

                                return;
                            }

                            callback.onChannelId(group.getChannelId());
                            item.channelId = group.getChannelId();
                        });
            }
        }
    }

    public void onSearchSettingsClicked() {
        Observable<List<MediaGroup>> sorting = mService.getContentService().getChannelSortingObserve(getChannelId());
        Disposable result = sorting.subscribe(
                items -> {
                    AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                    List<OptionItem> options = new ArrayList<>();
                    int idx = 0;
                    for (MediaGroup group : items) {
                        final int tempIdx = idx;
                        options.add(UiOptionItem.from(group.getTitle(), item -> {
                            //dialogPresenter.closeDialog();
                            Observable<MediaGroup> continuation = mService.getContentService().continueGroupObserve(group);
                            Disposable result2 = continuation.subscribe(mediaGroup -> {
                                VideoGroup replace = VideoGroup.from(mediaGroup);
                                replace.setId(144);
                                replace.setPosition(0);
                                replace.setAction(VideoGroup.ACTION_REPLACE);
                                getView().update(replace);
                                //getView().setPosition(1);
                                mSortIdx = tempIdx;
                            });
                        }, mSortIdx == idx));
                        idx++;
                    }
                    dialogPresenter.appendRadioCategory(getContext().getString(R.string.search_sorting), options);
                    dialogPresenter.showDialog();
                }
        );
    }

    public boolean onSearchSubmit(String query) {
        Observable<MediaGroup> search = mService.getContentService().getChannelSearchObserve(getChannelId(), query);
        Disposable result = search.subscribe(
                items -> {
                    VideoGroup update = VideoGroup.from(items);

                    if (update.isEmpty()) {
                        MessageHelpers.showMessage(getContext(), R.string.nothing_found);
                        return;
                    }

                    update.setId(112);
                    update.setPosition(0);
                    update.setAction(VideoGroup.ACTION_REPLACE);
                    getView().update(update);
                    getView().setPosition(1);
                }
        );

        return true;
    }
}
