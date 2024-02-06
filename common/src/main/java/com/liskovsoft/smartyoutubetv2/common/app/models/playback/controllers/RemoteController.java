package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import com.liskovsoft.mediaserviceinterfaces.HubService;
import com.liskovsoft.mediaserviceinterfaces.RemoteControlService;
import com.liskovsoft.mediaserviceinterfaces.data.Command;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeHubService;
import io.reactivex.disposables.Disposable;

public class RemoteController extends PlayerEventListenerHelper implements OnDataChange {
    private static final String TAG = RemoteController.class.getSimpleName();
    private final RemoteControlService mRemoteControlService;
    private final RemoteControlData mRemoteControlData;
    private final SuggestionsController mSuggestionsLoader;
    private final VideoLoaderController mVideoLoader;
    private Disposable mListeningAction;
    private Disposable mPostStartPlayAction;
    private Disposable mPostStateAction;
    private Disposable mPostVolumeAction;
    private Video mVideo;
    private boolean mConnected;
    private int mIsGlobalVolumeWorking = -1;
    private long mNewVideoPositionMs;
    private Disposable mActionDown;
    private Disposable mActionUp;

    public RemoteController(Context context, SuggestionsController suggestionsLoader, VideoLoaderController videoLoader) {
        HubService hubService = YouTubeHubService.instance();
        mSuggestionsLoader = suggestionsLoader;
        mVideoLoader = videoLoader;
        mRemoteControlService = hubService.getRemoteControlService();
        mRemoteControlData = RemoteControlData.instance(context);
        mRemoteControlData.setOnChange(this);
        tryListening();
    }

    @Override
    public void onDataChange() {
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
    public void onInit() {
        tryListening();
    }

    @Override
    public void onViewResumed() {
        tryListening();
    }

    @Override
    public void onVideoLoaded(Video item) {
        if (mNewVideoPositionMs > 0) {
            getPlayer().setPositionMs(mNewVideoPositionMs);
            mNewVideoPositionMs = 0;
        }

        postStartPlaying(item, getPlayer().getPlayWhenReady());
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
        switch (PlayerData.instance(getContext()).getRepeatMode()) {
            case PlayerUI.REPEAT_MODE_CLOSE:
            case PlayerUI.REPEAT_MODE_PAUSE:
            case PlayerUI.REPEAT_MODE_ALL:
                postPlay(false);
                break;
            case PlayerUI.REPEAT_MODE_ONE:
                postStartPlaying(getPlayer().getVideo(), true);
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
        mVideo = null;
    }

    private void postStartPlaying(@Nullable Video item, boolean isPlaying) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        String videoId = null;
        long positionMs = -1;
        long durationMs = -1;

        if (item != null && getPlayer() != null) {
            videoId = item.videoId;
            positionMs = getPlayer().getPositionMs();
            durationMs = getPlayer().getDurationMs();
        }

        postStartPlaying(videoId, positionMs, durationMs, isPlaying);
    }

    private void postStartPlaying(String videoId, long positionMs, long durationMs, boolean isPlaying) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        RxHelper.disposeActions(mPostStartPlayAction);

        mPostStartPlayAction = RxHelper.execute(
                mRemoteControlService.postStartPlayingObserve(videoId, positionMs, durationMs, isPlaying)
        );
    }

    private void postState(long positionMs, long durationMs, boolean isPlaying) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        RxHelper.disposeActions(mPostStateAction);

        mPostStateAction = RxHelper.execute(
                mRemoteControlService.postStateChangeObserve(positionMs, durationMs, isPlaying)
        );
    }

    private void postPlay(boolean isPlaying) {
        postState(getPlayer().getPositionMs(), getPlayer().getDurationMs(), isPlaying);
    }

    private void postSeek(long positionMs) {
        postState(positionMs, getPlayer().getDurationMs(), getPlayer().isPlaying());
    }

    private void postIdle() {
        postState(-1, -1, false);
    }

    private void postVolumeChange(int volume) {
        if (!mRemoteControlData.isDeviceLinkEnabled()) {
            return;
        }

        RxHelper.disposeActions(mPostVolumeAction);

        mPostVolumeAction = RxHelper.execute(
                mRemoteControlService.postVolumeChangeObserve(volume)
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

        mListeningAction = mRemoteControlService.getCommandObserve()
                .subscribe(
                        this::processCommand,
                        error -> {
                            String msg = "startListening error: " + error.getMessage();
                            Log.e(TAG, msg);
                            MessageHelpers.showLongMessage(getContext(), msg);
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
        RxHelper.disposeActions(mListeningAction, mPostStartPlayAction, mPostStateAction, mPostVolumeAction);
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

        switch (command.getType()) {
            case Command.TYPE_OPEN_VIDEO:
            case Command.TYPE_SUBTITLES: // open same video fix
                if (getPlayer() != null) {
                    getPlayer().showOverlay(false);
                }
                movePlayerToForeground();
                Video newVideo = Video.from(command.getVideoId());
                newVideo.remotePlaylistId = command.getPlaylistId();
                newVideo.playlistIndex = command.getPlaylistIndex();
                newVideo.isRemote = true;
                mNewVideoPositionMs = command.getCurrentTimeMs();
                openNewVideo(newVideo);
                break;
            case Command.TYPE_UPDATE_PLAYLIST:
                if (getPlayer() != null && mConnected) {
                    Video video = getPlayer().getVideo();
                    if (video != null) {
                        video.remotePlaylistId = command.getPlaylistId();
                        video.playlistParams = null;
                        video.isRemote = true;
                        mSuggestionsLoader.loadSuggestions(video);
                    }
                }
                break;
            case Command.TYPE_SEEK:
                if (getPlayer() != null) {
                    getPlayer().showOverlay(false);
                    movePlayerToForeground();
                    getPlayer().setPositionMs(command.getCurrentTimeMs());
                    postSeek(command.getCurrentTimeMs());
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_PLAY:
                if (getPlayer() != null) {
                    movePlayerToForeground();
                    getPlayer().setPlayWhenReady(true);
                    //postStartPlaying(getController().getVideo(), true);
                    postPlay(true);
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_PAUSE:
                if (getPlayer() != null) {
                    movePlayerToForeground();
                    getPlayer().setPlayWhenReady(false);
                    //postStartPlaying(getController().getVideo(), false);
                    postPlay(false);
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_NEXT:
                if (getMainController() != null) {
                    movePlayerToForeground();
                    mVideoLoader.loadNext();
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_PREVIOUS:
                if (getMainController() != null && getPlayer() != null) {
                    movePlayerToForeground();
                    // Switch immediately. Skip position reset logic.
                    mVideoLoader.loadPrevious();
                } else {
                    openNewVideo(mVideo);
                }
                break;
            case Command.TYPE_GET_STATE:
                if (getPlayer() != null) {
                    ViewManager.instance(getContext()).moveAppToForeground();
                    postStartPlaying(getPlayer().getVideo(), getPlayer().isPlaying());
                } else {
                    postStartPlaying(null, false);
                }
                break;
            case Command.TYPE_VOLUME:
                //Utils.setGlobalVolume(getActivity(), command.getVolume());
                Utils.setVolume(getContext(), getPlayer(), command.getVolume(), true);

                //postVolumeChange(Utils.getGlobalVolume(getActivity()));
                postVolumeChange(Utils.getVolume(getContext(), getPlayer(), true)); // Just in case volume cannot be changed (e.g. Fire TV stick)
                break;
            case Command.TYPE_STOP:
                // Close player
                if (getPlayer() != null) {
                    getPlayer().finish();
                }
                //// Finish the app
                //if (getActivity() != null) {
                //    ViewManager.instance(getActivity()).properlyFinishTheApp(getActivity());
                //}
                break;
            case Command.TYPE_CONNECTED:
                //movePlayerToForeground();
                // NOTE: there are possible false calls when mobile client unloaded from the memory.
                //if (getActivity() != null && mRemoteControlData.isFinishOnDisconnectEnabled()) {
                //    // NOTE: It's not a good idea to remember connection state (mConnected) at this point.
                //    Utils.moveAppToForeground(getActivity());
                //    MessageHelpers.showLongMessage(getActivity(), getActivity().getString(R.string.device_connected, command.getDeviceName()));
                //}
                //if (mRemoteControlData.isConnectMessagesEnabled()) {
                //    MessageHelpers.showLongMessage(getActivity(), getActivity().getString(R.string.device_connected, command.getDeviceName()));
                //}
                break;
            case Command.TYPE_DISCONNECTED:
                // NOTE: there are possible false calls when mobile client unloaded from the memory.
                if (getContext() != null && mRemoteControlData.isFinishOnDisconnectEnabled()) {
                    // NOTE: It's not a good idea to remember connection state (mConnected) at this point.
                    MessageHelpers.showLongMessage(getContext(), getContext().getString(R.string.device_disconnected, command.getDeviceName()));
                    ViewManager.instance(getContext()).properlyFinishTheApp(getContext());
                }
                //if (mRemoteControlData.isConnectMessagesEnabled()) {
                //    MessageHelpers.showLongMessage(getContext(), getContext().getString(R.string.device_disconnected, command.getDeviceName()));
                //}
                break;
            case Command.TYPE_DPAD:
                int key = KeyEvent.KEYCODE_UNKNOWN;
                boolean isLongAction = false;
                switch (command.getKey()) {
                    case Command.KEY_UP:
                        key = KeyEvent.KEYCODE_DPAD_UP;
                        break;
                    case Command.KEY_DOWN:
                        key = KeyEvent.KEYCODE_DPAD_DOWN;
                        break;
                    case Command.KEY_LEFT:
                        key = KeyEvent.KEYCODE_DPAD_LEFT;
                        isLongAction = true; // enable fast seeking
                        break;
                    case Command.KEY_RIGHT:
                        key = KeyEvent.KEYCODE_DPAD_RIGHT;
                        isLongAction = true; // enable fast seeking
                        break;
                    case Command.KEY_ENTER:
                        key = KeyEvent.KEYCODE_DPAD_CENTER;
                        break;
                    case Command.KEY_BACK:
                        key = KeyEvent.KEYCODE_BACK;
                        break;
                }
                if (key != KeyEvent.KEYCODE_UNKNOWN) {
                    RxHelper.disposeActions(mActionDown, mActionUp);

                    final int resultKey = key;

                    if (isLongAction) {
                        mActionDown = RxHelper.runAsync(() ->
                                Utils.sendKey(new KeyEvent(KeyEvent.ACTION_DOWN, resultKey)));
                        mActionUp = RxHelper.runAsync(() ->
                                Utils.sendKey(new KeyEvent(KeyEvent.ACTION_UP, resultKey)), 500);
                    } else {
                        mActionDown = RxHelper.runAsync(() -> Utils.sendKey(resultKey));
                    }
                }
                break;
            case Command.TYPE_VOICE:
                if (command.isVoiceStarted()) {
                    SearchPresenter.instance(getContext()).startVoice();
                } else {
                    SearchPresenter.instance(getContext()).forceFinish();
                }
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        //postVolumeChange(Utils.getGlobalVolume(getActivity()));
        postVolumeChange(Utils.getVolume(getContext(), getPlayer(), true));

        return false;
    }

    private void openNewVideo(Video newVideo) {
        if (Video.equals(mVideo, newVideo) && ViewManager.instance(getContext()).isPlayerInForeground()) { // same video already playing
            mVideo.playlistId = newVideo.playlistId;
            mVideo.playlistIndex = newVideo.playlistIndex;
            mVideo.playlistParams = newVideo.playlistParams;
            if (mNewVideoPositionMs > 0) {
                getPlayer().setPositionMs(mNewVideoPositionMs);
                mNewVideoPositionMs = 0;
            }
            postStartPlaying(mVideo, getPlayer().isPlaying());
        } else if (newVideo != null) {
            newVideo.isRemote = true;
            PlaybackPresenter.instance(getContext()).openVideo(newVideo);
        }
    }

    private void movePlayerToForeground() {
        ViewManager.instance(getContext()).movePlayerToForeground();
        // Device wake fix when player isn't started yet or been closed
        if (getPlayer() == null || !Utils.checkActivity(getActivity())) {
            new Handler(Looper.myLooper()).postDelayed(() -> ViewManager.instance(getContext()).movePlayerToForeground(), 5_000);
        }
    }
}
