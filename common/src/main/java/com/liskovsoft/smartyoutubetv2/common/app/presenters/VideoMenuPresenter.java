package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.VideoPlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class VideoMenuPresenter {
    private final Context mContext;
    private final MediaItemManager mItemManager;
    private Disposable mMenuAction;
    private Disposable mAddAction;
    private Video mVideo;

    private VideoMenuPresenter(Context context) {
        mContext = context;
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
    }

    public static VideoMenuPresenter instance(Context context) {
        return new VideoMenuPresenter(context.getApplicationContext());
    }

    public void showMenu(Video video) {
        if (video == null) {
            return;
        }

        mVideo = video;

        mMenuAction = mItemManager.getVideoPlaylistsInfosObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::prepareAndShowDialog);
    }

    private void prepareAndShowDialog(List<VideoPlaylistInfo> videoPlaylistInfos) {
        List<OptionItem> options = new ArrayList<>();

        for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
            options.add(UiOptionItem.from(
                    playlistInfo.getTitle(),
                    (item) -> this.addToPlaylist(playlistInfo.getPlaylistId(), item.isSelected()),
                    playlistInfo.isSelected()));
        }

        AppSettingsPresenter appSettingsPresenter = AppSettingsPresenter.instance(mContext);
        appSettingsPresenter.appendCheckedCategory(mContext.getString(R.string.dialog_add_to_playlist), options);
        appSettingsPresenter.showDialog(() -> RxUtils.disposeActions(mMenuAction, mAddAction));
    }

    private void addToPlaylist(String playlistId, boolean checked) {
        RxUtils.disposeActions(mMenuAction);
        Observable<Void> editObserve;

        if (checked) {
            editObserve = mItemManager.addToPlaylistObserve(playlistId, mVideo.videoId);
        } else {
            editObserve = mItemManager.removeFromPlaylistObserve(playlistId, mVideo.videoId);
        }

        mAddAction = editObserve
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }
}
