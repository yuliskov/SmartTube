package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppSettingsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import java.util.ArrayList;
import java.util.List;

public class AppSettingsPresenter implements Presenter<AppSettingsView> {
    @SuppressLint("StaticFieldLeak")
    private static AppSettingsPresenter sInstance;
    private final Context mContext;
    private AppSettingsView mView;
    private final List<SettingsCategory> mCategories;
    private String mTitle;
    private Runnable mOnClose;
    private PlayerUiManager mUiManager;
    private boolean mIsEngineBlocked;

    public static class SettingsCategory {
        public static SettingsCategory radioList(String title, List<OptionItem> items) {
            return new SettingsCategory(title, items, TYPE_RADIO_LIST);
        }

        public static SettingsCategory checkedList(String title, List<OptionItem> items) {
            return new SettingsCategory(title, items, TYPE_CHECKBOX_LIST);
        }

        public static SettingsCategory stringList(String title, List<OptionItem> items) {
            return new SettingsCategory(title, items, TYPE_STRING_LIST);
        }

        public static SettingsCategory singleSwitch(OptionItem item) {
            ArrayList<OptionItem> items = new ArrayList<>();
            items.add(item);
            return new SettingsCategory(null, items, TYPE_SINGLE_SWITCH);
        }

        public static SettingsCategory singleButton(OptionItem item) {
            ArrayList<OptionItem> items = new ArrayList<>();
            items.add(item);
            return new SettingsCategory(null, items, TYPE_SINGLE_BUTTON);
        }

        private SettingsCategory(String title, List<OptionItem> items, int type) {
            this.type = type;
            this.title = title;
            this.items = items;
        }

        public static final int TYPE_RADIO_LIST = 0;
        public static final int TYPE_CHECKBOX_LIST = 1;
        public static final int TYPE_SINGLE_SWITCH = 2;
        public static final int TYPE_SINGLE_BUTTON = 3;
        public static final int TYPE_STRING_LIST = 4;
        public int type;
        public String title;
        public List<OptionItem> items;
    }

    public AppSettingsPresenter(Context context) {
        mContext = context;
        mCategories = new ArrayList<>();
    }

    public static AppSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppSettingsPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void register(AppSettingsView view) {
        mView = view;
    }

    @Override
    public void unregister(AppSettingsView view) {
        mView = null;
        clear();
    }

    public void onClose() {
        clear();

        enablePlayerUiAutoHide(true);
        enableOldAndroidFix(false);

        if (mOnClose != null) {
            mOnClose.run();
        }
    }

    public void clear() {
        mCategories.clear();
    }

    @Override
    public void onInitDone() {
        mView.setTitle(mTitle);
        mView.addCategories(mCategories);
    }

    public void setPlayerUiManager(PlayerUiManager uiManager) {
        mUiManager = uiManager;
    }

    public void showDialog() {
        showDialog(null);
    }

    public void showDialog(Runnable onClose) {
        showDialog(null, onClose);
    }

    public void showDialog(String dialogTitle, Runnable onClose) {
        mTitle = dialogTitle;
        mOnClose = onClose;

        enablePlayerUiAutoHide(false);
        enableOldAndroidFix(true);

        if (mView != null) {
            onInitDone();
        }

        ViewManager.instance(mContext).startView(AppSettingsView.class);
    }

    public void appendRadioCategory(String categoryTitle, List<OptionItem> items) {
        mCategories.add(SettingsCategory.radioList(categoryTitle, items));
    }

    public void appendCheckedCategory(String categoryTitle, List<OptionItem> items) {
        mCategories.add(SettingsCategory.checkedList(categoryTitle, items));
    }

    public void appendStringsCategory(String categoryTitle, List<OptionItem> items) {
        mCategories.add(SettingsCategory.stringList(categoryTitle, items));
    }

    public void appendSingleSwitch(OptionItem optionItem) {
        mCategories.add(SettingsCategory.singleSwitch(optionItem));
    }

    public void appendSingleButton(OptionItem optionItem) {
        mCategories.add(SettingsCategory.singleButton(optionItem));
    }

    private void enableOldAndroidFix(boolean enable) {
        if (mUiManager != null) {
            // Old Android fix: don't destroy player while dialog is open
            if (VERSION.SDK_INT < 25) {
                if (enable) {
                    mIsEngineBlocked = mUiManager.getController().isEngineBlocked();
                    mUiManager.getController().blockEngine(true);
                } else {
                    mUiManager.getController().blockEngine(mIsEngineBlocked);
                }
            }
        }
    }

    private void enablePlayerUiAutoHide(boolean enable) {
        if (mUiManager != null) {
            if (enable) {
                mUiManager.enableUiAutoHideTimeout();
            } else {
                mUiManager.disableUiAutoHideTimeout();
            }
        }
    }
}
