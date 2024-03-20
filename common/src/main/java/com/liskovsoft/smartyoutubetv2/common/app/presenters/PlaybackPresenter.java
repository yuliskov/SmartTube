package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.MainPlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.common.helpers.ServiceHelper;

public class PlaybackPresenter extends BasePresenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static PlaybackPresenter sInstance;
    private final ViewManager mViewManager;
    private final MainPlayerController mMainPlayerController;

    private PlaybackPresenter(Context context) {
        super(context);

        mViewManager = ViewManager.instance(context);
        mMainPlayerController = MainPlayerController.instance(context);
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();

        mMainPlayerController.setPlayer(getView().getPlayer());
        getView().setEventListener(mMainPlayerController);
    }

    /**
     * Opens video item from splash view
     */
    public void openVideo(String videoId, boolean finishOnEnded, long timeMs) {
        if (videoId == null) {
            return;
        }

        Video video = Video.from(videoId);
        video.finishOnEnded = finishOnEnded;
        video.pendingPosMs = timeMs;
        openVideo(video);
    }

    /**
     * Opens video item from browser, search or channel views<br/>
     * Also prepares and start the playback view.
     */
    public void openVideo(Video item) {
        if (item == null) {
            return;
        }

        mMainPlayerController.openVideo(item);

        mViewManager.startView(PlaybackView.class);
    }

    public Video getVideo() {
        if (getView() == null || getView().getPlayer() == null) {
            return null;
        }

        return getView().getPlayer().getVideo();
    }

    public boolean isRunningInBackground() {
        return getView() != null &&
                getView().getPlayer().getBackgroundMode() != PlayerEngine.BACKGROUND_MODE_DEFAULT &&
                getView().getPlayer().isEngineInitialized() &&
                !ViewManager.instance(getContext()).isPlayerInForeground() &&
                getContext() instanceof Activity && Utils.checkActivity((Activity) getContext()); // Check that activity is not in Finishing state
    }

    public boolean isInPipMode() {
        return getView() != null && getView().getPlayer().isInPIPMode();
    }

    public boolean isOverlayShown() {
        return getView() != null && getView().getPlayer().isOverlayShown();
    }

    public boolean isPlaying() {
        return getView() != null && getView().getPlayer().isPlaying();
    }

    public int getBackgroundMode() {
        return getView() != null ? getView().getPlayer().getBackgroundMode() : -1;
    }

    private boolean isPreferBackground() {
        int mode = PlayerData.instance(getContext()).getBackgroundMode();

        return mode != PlayerEngine.BACKGROUND_MODE_DEFAULT;
    }

    public void forceFinish() {
        if (getView() != null) {
            getView().getPlayer().finishReally();
        }
    }

    public void setPosition(String timeCode) {
        setPosition(ServiceHelper.timeTextToMillis(timeCode));
    }

    public void setPosition(long positionMs) {
        // Check that the user isn't open context menu on suggestion item
        // if (Utils.isPlayerInForeground(getContext()) && getView() != null && !getView().getController().isSuggestionsShown()) {
        if (ViewManager.instance(getContext()).isPlayerInForeground() && getView() != null) {
            getView().getPlayer().setPositionMs(positionMs);
            getView().getPlayer().setPlayWhenReady(true);
            getView().getPlayer().showOverlay(false);
        } else {
            Video video = VideoMenuPresenter.sVideoHolder.get();
            if (video != null) {
                video.pendingPosMs = positionMs;
                openVideo(video);
            }
        }
    }
}
