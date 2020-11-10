package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.MainPlayerEventBridge;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

public class PlaybackPresenter implements Presenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static PlaybackPresenter sInstance;
    private final Context mContext;
    private final ViewManager mViewManager;
    private PlaybackView mView;
    private final MainPlayerEventBridge mMainPlayerEventBridge;

    private PlaybackPresenter(Context context) {
        mContext = context;
        mViewManager = ViewManager.instance(context);
        mMainPlayerEventBridge = MainPlayerEventBridge.instance();
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        mMainPlayerEventBridge.setController(mView.getController());
        mView.setEventListener(mMainPlayerEventBridge);
    }

    @Override
    public void register(PlaybackView view) {
        mView = view;
    }

    @Override
    public void unregister(PlaybackView view) {
        mView = null;
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
        if (mView != null && mView.getController().isInPIPMode()) {
            return;
        }

        mViewManager.startView(PlaybackView.class);
    }

    public Video getVideo() {
        if (mView == null || mView.getController() == null) {
            return null;
        }

        return mView.getController().getVideo();
    }
}
