package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.DetailsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class DetailsPresenter extends BasePresenter<DetailsView> {
    @SuppressLint("StaticFieldLeak")
    private static DetailsPresenter sInstance;
    private Video mVideo;

    private DetailsPresenter(Context context) {
        super(context);
    }

    public static DetailsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new DetailsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        getView().openVideo(mVideo);
    }

    public void openVideo(Video item) {
        mVideo = item;
        getViewManager().startView(DetailsView.class);
    }
}
