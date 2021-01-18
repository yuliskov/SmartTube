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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeviceLinkData;
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
    private Disposable mCommandAction;
    private Disposable mPostPlayAction;
    private Disposable mPostStateAction;

    public RemoteControlManager(Context context) {
        MediaService mediaService = YouTubeMediaService.instance();
        mRemoteManager = mediaService.getRemoteManager();
        mDeviceLinkData = DeviceLinkData.instance(context);
        mDeviceLinkData.onChange(this::tryListening);
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
        postStartPlaying(item);
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
    public void onEngineReleased() {
        postPlay(false);
    }

    private void postStartPlaying(@Nullable Video item) {
        String videoId = null;
        long positionMs = -1;
        long lengthMs = -1;

        if (item != null && getController() != null) {
            videoId = item.videoId;
            positionMs = getController().getPositionMs();
            lengthMs = getController().getLengthMs();
        }

        postStartPlaying(videoId, positionMs, lengthMs);
    }

    private void postStartPlaying(String videoId, long positionMs, long durationMs) {
        if (!mDeviceLinkData.isDeviceLinkEnabled()) {
            return;
        }

        mPostPlayAction = RxUtils.subscribe(
                mRemoteManager.postStartPlayingObserve(videoId, positionMs, durationMs)
        );
    }

    private void postState(long positionMs, long durationMs, boolean isPlaying) {
        if (!mDeviceLinkData.isDeviceLinkEnabled()) {
            return;
        }

        mPostStateAction = RxUtils.subscribe(
                mRemoteManager.postStateChangeObserve(positionMs, durationMs, isPlaying)
        );
    }

    private void moveAppToForeground() {
        if (!Utils.isAppInForeground()) {
            ViewManager.instance(getActivity()).startView(SplashView.class);
        }
    }

    private void postPlay(boolean isPlay) {
        postState(getController().getPositionMs(), getController().getLengthMs(), isPlay);
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
                            String msg = "startListening error: " + error;
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
                PlaybackPresenter.instance(getActivity()).openVideo(Video.from(command.getVideoId(), command.getPlaylistId(), command.getPlaylistIndex()));
                break;
            case Command.TYPE_SEEK:
                if (getController() != null) {
                    moveAppToForeground();
                    getController().setPositionMs(command.getCurrentTimeMs());
                    postSeek(command.getCurrentTimeMs());
                }
                break;
            case Command.TYPE_PLAY:
                if (getController() != null) {
                    moveAppToForeground();
                    getController().setPlay(true);
                    postPlay(true);
                }
                break;
            case Command.TYPE_PAUSE:
                if (getController() != null) {
                    moveAppToForeground();
                    getController().setPlay(false);
                    postPlay(false);
                }
                break;
            case Command.TYPE_GET_STATE:
                if (getController() != null) {
                    postStartPlaying(getController().getVideo());
                } else {
                    postStartPlaying(null);
                }
                break;
            case Command.TYPE_CONNECTED:
                if (getActivity() != null) {
                    moveAppToForeground();
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
}
