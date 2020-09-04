package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.MainPlayerEventBridge;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

public class PlaybackPresenter implements Presenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    private static PlaybackPresenter sInstance;
    private final Context mContext;
    private final ViewManager mViewManager;
    private PlaybackView mView;
    private Video mVideo;

    private PlaybackPresenter(Context context) {
        mContext = context;
        mViewManager = ViewManager.instance(context);
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        MainPlayerEventBridge mainPlayerEventBridge = new MainPlayerEventBridge(mView.getController());
        mView.setListener(mainPlayerEventBridge);
        mainPlayerEventBridge.onInit(mVideo);
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
