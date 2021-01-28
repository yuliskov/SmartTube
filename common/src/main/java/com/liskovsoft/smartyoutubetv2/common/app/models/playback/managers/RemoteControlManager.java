package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import androidx.annotation.Nullable;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.RemoteManager;
import com.liskovsoft.mediaserviceinterfaces.data.Command;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUiController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeviceLinkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class RemoteControlManager extends PlayerEventListenerHelper {
    private static final String TAG = RemoteControlManager.class.getSimpleName();
    private final RemoteManager mRemoteManager;
    private final DeviceLinkData mDeviceLinkData;
    private final SuggestionsLoader mSuggestionsLoader;
    private Disposable mCommandAction;
    private Disposable mPostPlayAction;
    private Disposable mPostStateAction;
    private Video mVideo;

    public RemoteControlManager(Context context, SuggestionsLoader suggestionsLoader) {
        MediaService mediaService = YouTubeMediaService.instance();
        mSuggestionsLoader = suggestionsLoader;
        mRemoteManager = mediaService.getRemoteManager();
        mDeviceLinkData = DeviceLinkData.instance(context);
        mDeviceLinkData.setOnChange(this::tryListening);
        tryListening();
    }

    @Override
    public void onInitDone() {
        tryListening();
    }

    @Override
    public void onViewResumed() {
        tryListening();
    }

    @Override
    public void onVideoLoaded(Video item) {
        postStartPlaying(item, true);
        mVideo = item;
    }

    @Override
    public void onPlay() {
        postPlay(true);
    }

    @Override
    public void onPause() {
        postPlay(false);
    }

    @Override
    public void onPlayEnd() {
        switch (PlayerData.instance(getActivity()).getRepeatMode()) {
            case PlaybackUiController.REPEAT_PAUSE:
                postPlay(false);
                break;
            case PlaybackUiController.REPEAT_ONE:
                postStartPlaying(getController().getVideo(), true);
                break;
        }
    }

    @Override
    public void onEngineReleased() {
        postPlay(false);
        // Below doesn't work on Vanced
        //postStartPlaying(null);
    }

    private void postStartPlaying(@Nullable Video item, boolean isPlaying) {
        String videoId = null;
        long positionMs = -1;
        long lengthMs = -1;

        if (item != null && getController() != null) {
            videoId = item.videoId;
            positionMs = getController().getPositionMs();
            lengthMs = getController().getLengthMs();
        }

        postStartPlaying(videoId, positionMs, lengthMs, isPlaying);
    }

    private void postStartPlaying(String videoId, long positionMs, long durationMs, boolean isPlaying) {
        if (!mDeviceLinkData.isDeviceLinkEnabled()) {
            return;
        }

        mPostPlayAction = RxUtils.execute(
                mRemoteManager.postStartPlayingObserve(videoId, positionMs, durationMs, isPlaying)
        );
    }

    private void postState(long positionMs, long durationMs, boolean isPlaying) {
        if (!mDeviceLinkData.isDeviceLinkEnabled()) {
            return;
        }

        mPostStateAction = RxUtils.execute(
                mRemoteManager.postStateChangeObserve(positionMs, durationMs, isPlaying)
        );
    }

    private void postPlay(boolean isPlaying) {
        postState(getController().getPositionMs(), getController().getLengthMs(), isPlaying);
    }

    private void postSeek(long positionMs) {
        postState(positionMs, getController().getLengthMs(), getController().isPlaying());
    }

    private void postIdle() {
        postState(-1, -1, false);
    }

    private void tryListening() {
        if (mDeviceLinkData.isDeviceLinkEnabled()) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {
        if (mCommandAction != null && !mCommandAction.isDisposed()) {
            return;
        }

        mCommandAction = mRemoteManager.getCommandObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::processCommand,
                        error -> {
                            String msg = "startListening error: " + error.getMessage();
                            Log.e(TAG, msg);
                            MessageHelpers.showMessage(getActivity(), msg);
                        }
                );
    }

    private void stopListening() {
        RxUtils.disposeActions(mCommandAction, mPostPlayAction, mPostStateAction);
    }

    private void processCommand(Command command) {
        switch (command.getType()) {
            case Command.TYPE_OPEN_VIDEO:
                Video newVideo = Video.from(command.getVideoId(), command.getPlaylistId(), command.getPlaylistIndex());
                openNewVideo(newVideo);
                break;
            case Command.TYPE_UPDATE_PLAYLIST:
                if (getController() != null) {
                    Video video = getController().getVideo();
                    video.playlistId = command.getPlaylistId();
                    mSuggestionsLoader.loadSuggestions(video);
                }
                break;
            case Command.TYPE_SEEK:
                if (getController() != null) {
                    Utils.movePlayerToForeground(getActivity());
                    getController().setPositionMs(command.getCurrentTimeMs());
                    postSeek(command.getCurrentTimeMs());
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_PLAY:
                if (getController() != null) {
                    Utils.movePlayerToForeground(getActivity());
                    getController().setPlay(true);
                    postPlay(true);
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_PAUSE:
                if (getController() != null) {
                    Utils.movePlayerToForeground(getActivity());
                    getController().setPlay(false);
                    postPlay(false);
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_GET_STATE:
                if (getController() != null) {
                    postStartPlaying(getController().getVideo(), getController().isPlaying());
                } else {
                    postStartPlaying(null, false);
                }
                break;
            case Command.TYPE_CONNECTED:
                if (getActivity() != null) {
                    Utils.moveAppToForeground(getActivity());
                    MessageHelpers.showLongMessage(getActivity(), getActivity().getString(R.string.device_connected, command.getDeviceName()));
                }
                break;
            case Command.TYPE_DISCONNECTED:
                if (getActivity() != null) {
                    MessageHelpers.showLongMessage(getActivity(), getActivity().getString(R.string.device_disconnected, command.getDeviceName()));
                }
                break;
        }
    }

    private void openNewVideo(Video newVideo) {
        if (newVideo != null) {
            newVideo.isRemote = true;
            PlaybackPresenter.instance(getActivity()).openVideo(newVideo);
        }
    }
}
