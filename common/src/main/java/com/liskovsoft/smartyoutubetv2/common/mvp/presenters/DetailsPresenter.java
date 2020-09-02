package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.mvp.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.DetailsView;

public class DetailsPresenter implements Presenter<DetailsView> {
    private static DetailsPresenter sInstance;
    private final Context mContext;
    private final ViewManager mViewManager;
    private DetailsView mView;
    private Video mVideo;

    private DetailsPresenter(Context context) {
        mContext = context;
        mViewManager = ViewManager.instance(context);
    }

    public static DetailsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new DetailsPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void register(DetailsView view) {
        mView = view;
    }

    @Override
    public void unregister(DetailsView view) {
        mView = null;
    }

    @Override
    public void onInitDone() {
        mView.openVideo(mVideo);
    }

    public void openVideo(Video item) {
        mVideo = item;
        mViewManager.startView(DetailsView.class);
    }
}
