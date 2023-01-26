package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.MainPlayerEventBridge;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngine;
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
    private final MainPlayerEventBridge mMainPlayerEventBridge;

    private PlaybackPresenter(Context context) {
        super(context);

        mViewManager = ViewManager.instance(context);
        mMainPlayerEventBridge = MainPlayerEventBridge.instance(context);
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

        mMainPlayerEventBridge.setController(getView().getController());
        getView().setEventListener(mMainPlayerEventBridge);
    }

    /**
     * Opens video item from splash view
     */
    public void openVideo(String videoId, boolean finishOnEnded) {
        if (videoId == null) {
            return;
        }

        Video video = Video.from(videoId);
        video.finishOnEnded = finishOnEnded;
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

        mMainPlayerEventBridge.openVideo(item);

        mViewManager.startView(PlaybackView.class);

        if (getView() != null) {
            getView().getController().showControls(true);
        }
    }

    public Video getVideo() {
        if (getView() == null || getView().getController() == null) {
            return null;
        }

        return getView().getController().getVideo();
    }

    public boolean isRunningInBackground() {
        return getView() != null &&
                getView().getController().getBackgroundMode() != PlaybackEngine.BACKGROUND_MODE_DEFAULT &&
                getView().getController().isEngineInitialized() &&
                !Utils.isPlayerInForeground(getContext()) &&
                getContext() instanceof Activity && Utils.checkActivity((Activity) getContext()); // Check that activity is not in Finishing state
    }

    public boolean isInPipMode() {
        return getView() != null && getView().getController().isInPIPMode();
    }

    private boolean isPreferBackground() {
        int mode = PlayerData.instance(getContext()).getBackgroundMode();

        return mode != PlaybackEngine.BACKGROUND_MODE_DEFAULT;
    }

    public void forceFinish() {
        if (getView() != null) {
            getView().getController().finishReally();
        }
    }

    public void setPosition(String timeCode) {
        setPosition(ServiceHelper.timeTextToMillis(timeCode));
    }

    public void setPosition(long positionMs) {
        // Check that the user isn't open context menu on suggestion item
        // if (Utils.isPlayerInForeground(getContext()) && getView() != null && !getView().getController().isSuggestionsShown()) {
        if (Utils.isPlayerInForeground(getContext()) && getView() != null) {
            getView().getController().setPositionMs(positionMs);
            getView().getController().setPlayWhenReady(true);
            getView().getController().showOverlay(false);
        } else {
            Video video = VideoMenuPresenter.sVideoHolder.get();
            if (video != null) {
                video.pendingPosMs = positionMs;
                openVideo(video);
            }
        }
    }
}
