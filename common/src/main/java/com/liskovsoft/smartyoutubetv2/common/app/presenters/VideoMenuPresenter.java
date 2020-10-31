package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.VideoPlaylistInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
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
    private final SignInManager mAuthManager;
    private final AppSettingsPresenter mSettingsPresenter;
    private Disposable mPlaylistAction;
    private Disposable mAddAction;
    private Disposable mSignCheckAction;
    private Video mVideo;

    private VideoMenuPresenter(Context context) {
        mContext = context;
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mAuthManager = service.getSignInManager();
        mSettingsPresenter = AppSettingsPresenter.instance(context);
    }

    public static VideoMenuPresenter instance(Context context) {
        return new VideoMenuPresenter(context.getApplicationContext());
    }

    public void showMenu(Video video) {
        if (video == null || !video.isVideo()) {
            return;
        }

        authCheck(() -> obtainPlaylistsAndShow(video),
                  () -> MessageHelpers.showMessage(mContext, R.string.msg_signed_users_only));;
    }

    private void obtainPlaylistsAndShow(Video video) {
        mVideo = video;

        mPlaylistAction = mItemManager.getVideoPlaylistsInfosObserve(video.videoId)
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

        mSettingsPresenter.clear();
        mSettingsPresenter.appendCheckedCategory(mContext.getString(R.string.dialog_add_to_playlist), options);
        mSettingsPresenter.showDialog(() -> RxUtils.disposeActions(mPlaylistAction, mAddAction, mSignCheckAction));
    }

    private void addToPlaylist(String playlistId, boolean checked) {
        RxUtils.disposeActions(mPlaylistAction, mAddAction, mSignCheckAction);
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

    private void authCheck(Runnable onSuccess, Runnable onError) {
        mSignCheckAction = mAuthManager.isSignedObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isSigned -> {
                            if (isSigned) {
                                onSuccess.run();
                            } else {
                                onError.run();
                            }
                        }
                );

    }
}
