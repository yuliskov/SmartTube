package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.yt.ContentService;
import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SampleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.DeArrowProcessor;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.List;

public class ChannelUploadsPresenter extends BasePresenter<ChannelUploadsView> implements VideoGroupPresenter {
    private static final String TAG = ChannelUploadsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelUploadsPresenter sInstance;
    private final ContentService mContentService;
    private final MediaItemService mItemManager;
    private final DeArrowProcessor mDeArrowProcessor;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Video mVideoItem;
    private MediaGroup mRootGroup;

    public ChannelUploadsPresenter(Context context) {
        super(context);
        MotherService service = YouTubeMotherService.instance();
        mContentService = service.getContentService();
        mItemManager = service.getMediaItemService();
        mDeArrowProcessor = new DeArrowProcessor(getContext(), this::syncItem);
    }

    public static ChannelUploadsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelUploadsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();

        refresh();
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
        mVideoItem = null;
        mRootGroup = null;
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
        VideoMenuPresenter.instance(getContext()).showMenu(item, (videoItem, action) -> {
            if (action == VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST) {
                removeItem(videoItem);
            } else if (action == VideoMenuCallback.ACTION_UNSUBSCRIBE) {
                MessageHelpers.showMessage(getContext(), R.string.unsubscribed_from_channel);
            }
        });
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        VideoGroup group = item.getGroup();

        if (group == null) {
            Log.e(TAG, "Can't scroll. VideoGroup is null.");
            return;
        }

        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        boolean scrollInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (!scrollInProgress) {
            continueGroup(group);
        }
    }

    @Override
    public boolean hasPendingActions() {
        return RxHelper.isAnyActionRunning(mScrollAction, mUpdateAction);
    }

    public void openChannel(Video item) {
        // Working with uploads or playlists
        if (item == null || (!item.hasNestedItems() && !item.hasPlaylist())) {
            return;
        }

        disposeActions();
        ViewManager.instance(getContext()).startView(ChannelUploadsView.class);

        if (getView() != null) {
            mVideoItem = null;
            getView().clear();
            updateGrid(item);
        } else {
            mVideoItem = item;
        }
    }

    public void obtainVideoGroup(Video item, VideoGroupCallback callback) {
        if (item != null && item.mediaItem != null) {
            updateVideoGrid(item.mediaItem, callback);
        }
    }

    public Observable<MediaGroup> obtainUploadsObservable(Video item) {
        if (item == null) {
            return null;
        }

        if (item.mediaItem == null) {
            item.mediaItem = SampleMediaItem.from(item);
        }

        disposeActions();

        return item.hasNestedItems() || item.isChannel() ?
               mContentService.getGroupObserve(item.mediaItem) :
               item.hasReloadPageKey() ?
               mContentService.getGroupObserve(item.getReloadPageKey()) :
               mItemManager.getMetadataObserve(item.videoId, item.playlistId, 0, item.playlistParams)
                       .flatMap(mediaItemMetadata -> Observable.just(findPlaylistRow(mediaItemMetadata)));
    }

    private void updateGrid(Video item) {
        updateVideoGrid(obtainUploadsObservable(item));
    }

    private void disposeActions() {
        RxHelper.disposeActions(mUpdateAction, mScrollAction);
    }

    private void continueGroup(VideoGroup group) {
        disposeActions();

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

        Observable<MediaGroup> continuation;

        if (mediaGroup.getType() == MediaGroup.TYPE_SUGGESTIONS) {
            continuation = mItemManager.continueGroupObserve(mediaGroup);
        } else {
            continuation = mContentService.continueGroupObserve(mediaGroup);
        }

        mScrollAction = continuation
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

    private void updateVideoGrid(Observable<MediaGroup> group) {
        Log.d(TAG, "updateVideoGrid: Start loading group...");

        disposeActions();

        getView().showProgressBar(true);

        mUpdateAction = group
                .subscribe(
                        this::updateGrid,
                        error -> {
                            Log.e(TAG, "updateGridHeader error: %s", error.getMessage());
                            getView().showProgressBar(false);
                        },
                        () -> getView().showProgressBar(false)
                );
    }

    public void updateGrid(MediaGroup mediaGroup) {
        if (getView() == null) { // starting from outside (e.g. MediaServiceManager)
            disposeActions();
            mVideoItem = null;
            mRootGroup = mediaGroup;
            return;
        }

        VideoGroup group = VideoGroup.from(mediaGroup);
        getView().update(group);
        mDeArrowProcessor.process(group);

        // Hide loading as long as first group received
        if (mediaGroup.getMediaItems() != null) {
            getView().showProgressBar(false);
        }
    }

    private void updateVideoGrid(MediaItem mediaItem, VideoGroupCallback callback) {
        Log.d(TAG, "updateVideoGrid: Start loading group...");

        disposeActions();

        Observable<MediaGroup> group = mContentService.getGroupObserve(mediaItem);

        mUpdateAction = group
                .subscribe(
                        callback::onGroup,
                        error -> Log.e(TAG, "updateVideoGrid error: %s", error.getMessage())
                );
    }

    public interface VideoGroupCallback {
        void onGroup(MediaGroup mediaGroup);
    }

    /**
     * Playlist usually is the first row with media items.<br/>
     * NOTE: before playlist may be the video description row
     */
    private MediaGroup findPlaylistRow(MediaItemMetadata mediaItemMetadata) {
        if (mediaItemMetadata == null || mediaItemMetadata.getSuggestions() == null) {
            return null;
        }

        for (MediaGroup group : mediaItemMetadata.getSuggestions()) {
            List<MediaItem> mediaItems = group.getMediaItems();
            if (mediaItems != null && mediaItems.size() > 0) {
                return group;
            }
        }

        return null;
    }

    public void clear() {
        if (getView() != null) {
            getView().clear();
        }
    }

    public void refresh() {
        if (getView() == null) {
            return;
        }

        if (mVideoItem != null) {
            getView().clear();
            updateGrid(mVideoItem);
        } else if (mRootGroup != null) {
            getView().clear();
            updateGrid(mRootGroup);
        }
    }
}
