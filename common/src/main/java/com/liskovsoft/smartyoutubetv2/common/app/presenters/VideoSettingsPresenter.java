package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUiManager.SwitchCallback;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import java.util.ArrayList;
import java.util.List;

public class VideoSettingsPresenter implements Presenter<VideoSettingsView> {
    private static VideoSettingsPresenter sInstance;
    private final Context mContext;
    private VideoSettingsView mView;
    private final List<SettingsCategory> mCategories;
    private Runnable mOnClose;

    public interface OptionCallback {
        void onSelect(OptionItem optionItem);
    }

    public static class SettingsCategory {
        private SettingsCategory(String title, List<OptionItem> items, OptionCallback callback, int type) {
            this.type = type;
            this.title = title;
            this.items = items;
            this.callback = callback;
        }

        public static SettingsCategory radioList(String title, List<OptionItem> items, OptionCallback callback) {
            return new SettingsCategory(title, items, callback, TYPE_RADIO);
        }

        public static SettingsCategory checkList(String title, List<OptionItem> items, OptionCallback callback) {
            return new SettingsCategory(title, items, callback, TYPE_CHECKBOX);
        }

        public static final int TYPE_RADIO = 0;
        public static final int TYPE_CHECKBOX = 1;
        public int type;
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
        onClose();
    }

    public void onClose() {
        clear();
        if (mOnClose != null) {
            mOnClose.run();
        }
    }

    public void clear() {
        mCategories.clear();
    }

    @Override
    public void onInitDone() {
        mView.addCategories(mCategories);
    }

    public void showDialog(Runnable onClose) {
        mOnClose = onClose;
        ViewManager.instance(mContext).startView(VideoSettingsView.class);
    }

    public void append(String title, List<OptionItem> items, OptionCallback callback) {
        mCategories.add(SettingsCategory.radioList(title, items, callback));
    }

    public void append(String title, SwitchCallback callback) {
        // TODO: not implemented
    }
}
