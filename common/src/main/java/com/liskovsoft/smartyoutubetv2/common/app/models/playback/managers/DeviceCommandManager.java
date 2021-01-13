package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.CommandManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.Command;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeviceLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DeviceCommandManager extends PlayerEventListenerHelper {
    private static final String TAG = DeviceCommandManager.class.getSimpleName();
    private final CommandManager mCommandManager;
    private final DeviceLinkData mDeviceLinkData;
    private Disposable mCommandAction;
    private Disposable mPostAction;

    public DeviceCommandManager(Context context) {
        MediaService mediaService = YouTubeMediaService.instance();
        mCommandManager = mediaService.getCommandManager();
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
        //postPlaying(item);
    }

    private void postPlaying(Video item) {
        mPostAction = mCommandManager.postPlayingObserve(item.videoId, getController().getPositionMs(), getController().getLengthMs())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
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

        mCommandAction = mCommandManager.getDeviceCommandObserve()
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
        RxUtils.disposeActions(mCommandAction, mPostAction);
    }

    private void processCommand(Command command) {
        switch (command.getType()) {
            case Command.TYPE_OPEN:
                PlaybackPresenter.instance(getActivity()).openVideo(command.getVideoId());
                break;
        }
    }
}
