package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import java.util.ArrayList;
import java.util.List;

public class AppDialogPresenter extends BasePresenter<AppDialogView> {
    @SuppressLint("StaticFieldLeak")
    private static AppDialogPresenter sInstance;
    private final List<SettingsCategory> mCategories;
    private final Handler mHandler;
    private final Runnable mCloseDialog = this::closeDialog;
    private final List<Runnable> mOnFinish = new ArrayList<>();
    private String mTitle;
    private long mTimeoutMs;
    private boolean mIsTransparent;

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

    public AppDialogPresenter(Context context) {
        super(context);
        mCategories = new ArrayList<>();
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static AppDialogPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppDialogPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /**
     * Called after {@link #onFinish}
     */
    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        clear();
    }

    /**
     * Called when user pressed back button.
     */
    @Override
    public void onFinish() {
        super.onFinish();
        clear();

        for (Runnable callback : mOnFinish) {
            if (callback != null) {
                callback.run();
            }
        }

        mOnFinish.clear();
    }

    public void clear() {
        mTimeoutMs = 0;
        mIsTransparent = false;
        mHandler.removeCallbacks(mCloseDialog);
        mCategories.clear();
    }

    @Override
    public void onViewInitialized() {
        getView().setTitle(mTitle);
        getView().addCategories(mCategories);
    }

    public void showDialog() {
        showDialog(null, null);
    }

    public void showDialog(String dialogTitle) {
        showDialog(dialogTitle, null);
    }

    public void showDialog(Runnable onClose) {
        showDialog(null, onClose);
    }

    public void showDialog(String dialogTitle, Runnable onFinish) {
        mTitle = dialogTitle;
        mOnFinish.add(onFinish);

        if (getView() != null) {
            getView().clear();
            onViewInitialized();
        }

        ViewManager.instance(getContext()).startView(AppDialogView.class, true);

        setupTimeout();
    }

    public void closeDialog() {
        if (getView() != null) {
            getView().finish();
        }
    }

    public boolean isDialogShown() {
        // Also check that current dialog almost closed (new view start is pending from a menu item)
        // Hmm. Maybe current dialog is pending. Check that view is null.
        // Also check that we aren't started the same view (nested dialog).
        return !mCategories.isEmpty() && (!ViewManager.instance(getContext()).isNewViewPending(AppDialogView.class) || getView() == null);
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

    public void showDialogMessage(String dialogTitle, Runnable onClose, int timeoutMs) {
        showDialog(dialogTitle, onClose);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getView() != null) {
                getView().finish();
            }
        }, timeoutMs);
    }

    public void setCloseTimeoutMs(long timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    public boolean isTransparent() {
        return mIsTransparent;
    }

    public boolean isEmpty() {
        return mCategories == null || mCategories.isEmpty();
    }

    private void setupTimeout() {
        mHandler.removeCallbacks(mCloseDialog);

        if (mTimeoutMs > 0) {
            mHandler.postDelayed(mCloseDialog, mTimeoutMs);
        }
    }
}
