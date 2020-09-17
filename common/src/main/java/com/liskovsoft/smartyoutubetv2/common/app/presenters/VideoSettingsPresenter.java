package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import java.util.ArrayList;
import java.util.List;

public class VideoSettingsPresenter implements Presenter<VideoSettingsView> {
    private static VideoSettingsPresenter sInstance;
    private final Context mContext;
    private VideoSettingsView mView;
    private List<SettingsCategory> mCategories;

    public interface OptionCallback {
        void onSelect(OptionItem optionItem);
    }

    public static class SettingsCategory {
        public SettingsCategory(String title, List<OptionItem> items, OptionCallback callback) {
            this.title = title;
            this.items = items;
            this.callback = callback;
        }

        public String title;
        public List<OptionItem> items;
        public OptionCallback callback;
    }

    public VideoSettingsPresenter(Context context) {
        mContext = context;
        mCategories = new ArrayList<>();
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
        mCategories.clear();
    }

    @Override
    public void onInitDone() {
        //for (DialogCategory dialogCategory : mCategories) {
        //    mView.addCategory(dialogCategory.title, dialogCategory.items);
        //}

        mView.addCategories(mCategories);
    }

    public void showDialog() {
        ViewManager.instance(mContext).startView(VideoSettingsView.class);
    }

    public void append(String title, List<OptionItem> items, OptionCallback callback) {
        mCategories.add(new SettingsCategory(title, items, callback));
    }
}
