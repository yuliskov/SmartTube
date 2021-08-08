package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import androidx.annotation.Nullable;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.RemoteManager;
import com.liskovsoft.mediaserviceinterfaces.data.Command;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class RemoteControlManager extends PlayerEventListenerHelper {
    private static final String TAG = RemoteControlManager.class.getSimpleName();
    private final RemoteManager mRemoteManager;
    private final RemoteControlData mRemoteControlData;
    private final SuggestionsLoader mSuggestionsLoader;
    private final VideoLoader mVideoLoader;
    private Disposable mListeningAction;
    private Disposable mPostStartPlayAction;
    private Disposable mPostStateAction;
    private Disposable mPostVolumeAction;
    private Video mVideo;
    private boolean mConnected;

    public RemoteControlManager(Context context, SuggestionsLoader suggestionsLoader, VideoLoader videoLoader) {
        MediaService mediaService = YouTubeMediaService.instance();
        mSuggestionsLoader = suggestionsLoader;
        mVideoLoader = videoLoader;
        mRemoteManager = mediaService.getRemoteManager();
        mRemoteControlData = RemoteControlData.instance(context);
        mRemoteControlData.setOnChange(this::tryListening);
        tryListening();
    }

    @Override
    public void openVideo(Video item) {
        if (item != null) {
            Log.d(TAG, "Open video. Is remote connected: %s", mConnected);
            item.isRemote = mConnected;
        }
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
        postStartPlaying(item, getController().getPlay());
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
        switch (PlayerData.instance(getActivity()).getPlaybackMode()) {
            case PlaybackEngineController.PLAYBACK_MODE_CLOSE:
            case PlaybackEngineController.PLAYBACK_MODE_PAUSE:
            case PlaybackEngineController.PLAYBACK_MODE_PLAY_ALL:
                postPlay(false);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_REPEAT_ONE:
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

    @Override
    public void onFinish() {
        // User action detected. Stop remote session.
        mConnected = false;
    }

    private void postStartPlaying(@Nullable Video item, boolean isPlaying) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        String videoId = null;
        long positionMs = -1;
        long durationMs = -1;

        if (item != null && getController() != null) {
            videoId = item.videoId;
            positionMs = getController().getPositionMs();
            durationMs = getController().getLengthMs();
        }

        postStartPlaying(videoId, positionMs, durationMs, isPlaying);
    }

    private void postStartPlaying(String videoId, long positionMs, long durationMs, boolean isPlaying) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        RxUtils.disposeActions(mPostStartPlayAction);

        mPostStartPlayAction = RxUtils.execute(
                mRemoteManager.postStartPlayingObserve(videoId, positionMs, durationMs, isPlaying)
        );
    }

    private void postState(long positionMs, long durationMs, boolean isPlaying) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        RxUtils.disposeActions(mPostStateAction);

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

    private void postVolumeChange(int volume) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        RxUtils.disposeActions(mPostVolumeAction);

        mPostVolumeAction = RxUtils.execute(
                mRemoteManager.postVolumeChangeObserve(volume)
        );
    }

    private void tryListening() {
        if (mRemoteControlData.isDeviceLinkEnabled()) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {
        if (mListeningAction != null && !mListeningAction.isDisposed()) {
            return;
        }

        mListeningAction = mRemoteManager.getCommandObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::processCommand,
                        error -> {
                            String msg = "startListening error: " + error.getMessage();
                            Log.e(TAG, msg);
                            MessageHelpers.showLongMessage(getActivity(), msg);
                        },
                        () -> {
                            // Some users seeing this.
                            // This msg couldn't appear in normal situation.
                            Log.d(TAG, "Remote session has been closed");
                            //MessageHelpers.showMessage(getActivity(), R.string.remote_session_closed);
                        }
                );
    }

    private void stopListening() {
        RxUtils.disposeActions(mListeningAction, mPostStartPlayAction, mPostStateAction, mPostVolumeAction);
    }

    private void processCommand(Command command) {
        switch (command.getType()) {
            case Command.TYPE_IDLE:
            case Command.TYPE_UNDEFINED:
            case Command.TYPE_UPDATE_PLAYLIST:
                break;
            case Command.TYPE_STOP:
            case Command.TYPE_DISCONNECTED:
                mConnected = false;
                break;
            default:
                mConnected = true;
        }

        Log.d(TAG, "Is remote connected: %s, command type: %s", mConnected, command.getType());
        
        //if (getController() != null && getController().getVideo() != null) {
        //    getController().getVideo().isRemote = mConnected;
        //}

        switch (command.getType()) {
            case Command.TYPE_OPEN_VIDEO:
                if (getController() != null) {
                    getController().showControls(false);
                }
                Utils.movePlayerToForeground(getActivity());
                Video newVideo = Video.from(command.getVideoId(), command.getPlaylistId(), command.getPlaylistIndex());
                openNewVideo(newVideo);
                break;
            case Command.TYPE_UPDATE_PLAYLIST:
                if (getController() != null) {
                    Video video = getController().getVideo();
                    if (video != null) {
                        video.playlistId = command.getPlaylistId();
                        mSuggestionsLoader.loadSuggestions(video);
                    }
                }
                break;
            case Command.TYPE_SEEK:
                if (getController() != null) {
                    getController().showControls(false);
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
                    //postStartPlaying(getController().getVideo(), true);
                    postPlay(true);
                } else {
                    openNewVideo(mVideo);
                }
                showHideScreensaver(true);
                break;
            case Command.TYPE_PAUSE:
                if (getController() != null) {
                    Utils.movePlayerToForeground(getActivity());
                    getController().setPlay(false);
                    //postStartPlaying(getController().getVideo(), false);
                    postPlay(false);
                } else {
                    openNewVideo(mVideo);
                }
                showHideScreensaver(true);
                break;
            case Command.TYPE_NEXT:
                if (getBridge() != null) {
                    Utils.movePlayerToForeground(getActivity());
                    mVideoLoader.loadNext();
                } else {
                    openNewVideo(mVideo);
                }
                showHideScreensaver(true);
                break;
            case Command.TYPE_PREVIOUS:
                if (getBridge() != null && getController() != null) {
                    Utils.movePlayerToForeground(getActivity());
                    // Switch immediately. Skip position reset logic.
                    mVideoLoader.loadPrevious();
                } else {
                    openNewVideo(mVideo);
                }
                showHideScreensaver(true);
                break;
            case Command.TYPE_GET_STATE:
                if (getController() != null) {
                    Utils.moveAppToForeground(getActivity());
                    postStartPlaying(getController().getVideo(), getController().isPlaying());
                } else {
                    postStartPlaying(null, false);
                }
                break;
            case Command.TYPE_VOLUME:
                //Utils.setGlobalVolume(getActivity(), command.getVolume());
                if (getController() != null) {
                    getController().setVolume(command.getVolume() / 100f);
                }
                break;
            case Command.TYPE_STOP:
                if (getController() != null) {
                    getController().finish();
                }
                break;
            case Command.TYPE_CONNECTED:
                if (getActivity() != null) {
                    // NOTE: It's not a good idea to remember connection state (mConnected) at this point.
                    //Utils.moveAppToForeground(getActivity());
                    //MessageHelpers.showLongMessage(getActivity(), getActivity().getString(R.string.device_connected, command.getDeviceName()));
                }
                if (getController() != null) {
                    postVolumeChange((int)(getController().getVolume() * 100));
                }
                break;
            case Command.TYPE_DISCONNECTED:
                // Note: there are possible false calls when mobile client unloaded from the memory.
                if (getActivity() != null) {
                    // NOTE: It's not a good idea to remember connection state (mConnected) at this point.
                    //MessageHelpers.showLongMessage(getActivity(), getActivity().getString(R.string.device_disconnected, command.getDeviceName()));
                }
                if (getController() != null) {
                    getController().setVolume(1);
                }
                break;
        }

        //postVolumeChange(Utils.getGlobalVolume(getActivity()));
        if (getController() != null) {
            postVolumeChange((int)(getController().getVolume() * 100));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        //postVolumeChange(Utils.getGlobalVolume(getActivity()));
        if (getController() != null) {
            postVolumeChange((int)(getController().getVolume() * 100));
        }

        return false;
    }

    private void openNewVideo(Video newVideo) {
        if (Video.equals(mVideo, newVideo) && Utils.isPlayerInForeground(getActivity())) { // same video already playing
            mVideo.playlistId = newVideo.playlistId;
            mVideo.playlistIndex = newVideo.playlistIndex;
            postStartPlaying(mVideo, getController().isPlaying());
        } else if (newVideo != null) {
            newVideo.isRemote = true;
            PlaybackPresenter.instance(getActivity()).openVideo(newVideo);
        }

        showHideScreensaver(true);
    }

    private void showHideScreensaver(boolean show) {
        if (getActivity() instanceof MotherActivity) {
            ScreensaverManager screensaverManager = ((MotherActivity) getActivity()).getScreensaverManager();

            if (show) {
                screensaverManager.enable();
            } else {
                screensaverManager.disable();
            }
        }
    }
}
