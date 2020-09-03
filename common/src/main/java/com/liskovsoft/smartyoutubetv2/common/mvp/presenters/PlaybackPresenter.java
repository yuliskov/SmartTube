package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.mvp.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.playback.RootPlayerEventBridge;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;

public class PlaybackPresenter implements Presenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    private static PlaybackPresenter sInstance;
    private final Context mContext;
    private final ViewManager mViewManager;
    private final RootPlayerEventBridge mRootPlayerEventBridge;
    private PlaybackView mView;
    private Video mVideo;

    private PlaybackPresenter(Context context) {
        mContext = context;
        mViewManager = ViewManager.instance(context);
        mRootPlayerEventBridge = RootPlayerEventBridge.instance();
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        mView.setListener(mRootPlayerEventBridge);
        mRootPlayerEventBridge.setController(mView.getController());
        mRootPlayerEventBridge.onInit(mVideo);
    }

    @Override
    public void register(PlaybackView view) {
        mView = view;
    }

    @Override
    public void unregister(PlaybackView view) {
        mView = null;
    }

    public void openVideo(Video item) {
        mVideo = item;
        mViewManager.startView(PlaybackView.class);
    }
}
