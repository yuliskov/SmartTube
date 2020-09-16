package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import java.util.List;

public class VideoSettingsPresenter implements Presenter<VideoSettingsView> {
    private static VideoSettingsPresenter sInstance;
    private final Context mContext;
    private VideoSettingsView mView;
    private String mTitle;
    private List<OptionItem> mItems;

    public VideoSettingsPresenter(Context context) {
        mContext = context;
    }

    public static VideoSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new VideoSettingsPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void register(VideoSettingsView view) {
        mView = view;
    }

    @Override
    public void unregister(VideoSettingsView view) {
        mView = null;
    }

    @Override
    public void onInitDone() {
        mView.addCategory(mTitle, mItems);
    }

    public void showDialog(String title, List<OptionItem> items) {
        if (mView == null) {
            mTitle = title;
            mItems = items;
            ViewManager.instance(mContext).startView(VideoSettingsView.class);
        }
    }
}
