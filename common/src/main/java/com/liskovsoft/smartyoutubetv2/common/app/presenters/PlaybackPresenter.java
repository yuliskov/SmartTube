package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.MainPlayerEventBridge;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class PlaybackPresenter extends BasePresenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static PlaybackPresenter sInstance;
    private final ViewManager mViewManager;
    private final MainPlayerEventBridge mMainPlayerEventBridge;

    private PlaybackPresenter(Context context) {
        super(context);
        mViewManager = ViewManager.instance(context);
        mMainPlayerEventBridge = MainPlayerEventBridge.instance();
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
        mMainPlayerEventBridge.setController(getView().getController());
        getView().setEventListener(mMainPlayerEventBridge);
    }

    /**
     * Opens video item from browser, search or channel views
     */
    public void openVideo(String videoId) {
        openVideo(Video.from(videoId));
    }

    /**
     * Opens video item from browser, search or channel views
     */
    public void openVideo(Video item) {
        mMainPlayerEventBridge.openVideo(item);

        focusView();
    }

    private void focusView() {
        //if (getView() != null && (getView().getController().isInPIPMode() || getView().getController().isEngineBlocked())) {
        //    return;
        //}

        mViewManager.startView(PlaybackView.class);
    }

    public Video getVideo() {
        if (getView() == null || getView().getController() == null) {
            return null;
        }

        return getView().getController().getVideo();
    }
}
