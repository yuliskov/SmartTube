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
import java.util.Collections;
import java.util.List;

public class AppDialogPresenter extends BasePresenter<AppDialogView> {
    @SuppressLint("StaticFieldLeak")
    private static AppDialogPresenter sInstance;
    private final Handler mHandler;
    private final Runnable mCloseDialog = this::closeDialog;
    private final List<Runnable> mOnFinish = new ArrayList<>();
    private String mTitle;
    private long mTimeoutMs;
    private boolean mIsTransparent;
    private List<OptionCategory> mCategories;

    public static class OptionCategory {
        public static OptionCategory radioList(String title, List<OptionItem> items) {
            return new OptionCategory(title, items, TYPE_RADIO_LIST);
        }

        public static OptionCategory checkedList(String title, List<OptionItem> items) {
            return new OptionCategory(title, items, TYPE_CHECKBOX_LIST);
        }

        public static OptionCategory stringList(String title, List<OptionItem> items) {
            return new OptionCategory(title, items, TYPE_STRING_LIST);
        }

        public static OptionCategory longText(String title, OptionItem item) {
            return new OptionCategory(title, Collections.singletonList(item), TYPE_LONG_TEXT);
        }

        public static OptionCategory chat(String title, OptionItem item) {
            return new OptionCategory(title, Collections.singletonList(item), TYPE_CHAT);
        }

        public static OptionCategory comments(String title, OptionItem item) {
            return new OptionCategory(title, Collections.singletonList(item), TYPE_COMMENTS);
        }

        public static OptionCategory singleSwitch(OptionItem item) {
            ArrayList<OptionItem> items = new ArrayList<>();
            items.add(item);
            return new OptionCategory(null, items, TYPE_SINGLE_SWITCH);
        }

        public static OptionCategory singleButton(OptionItem item) {
            ArrayList<OptionItem> items = new ArrayList<>();
            items.add(item);
            return new OptionCategory(null, items, TYPE_SINGLE_BUTTON);
        }

        private OptionCategory(String title, List<OptionItem> items, int type) {
            this.type = type;
            this.title = title;
            this.items = items;
        }

        public static final int TYPE_RADIO_LIST = 0;
        public static final int TYPE_CHECKBOX_LIST = 1;
        public static final int TYPE_SINGLE_SWITCH = 2;
        public static final int TYPE_SINGLE_BUTTON = 3;
        public static final int TYPE_STRING_LIST = 4;
        public static final int TYPE_LONG_TEXT = 5;
        public static final int TYPE_CHAT = 6;
        public static final int TYPE_COMMENTS = 7;
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

    private void clear() {
        mTimeoutMs = 0;
        mIsTransparent = false;
        mHandler.removeCallbacks(mCloseDialog);
        mCategories = new ArrayList<>();
    }

    @Override
    public void onViewInitialized() {
        getView().show(mCategories, mTitle);
        mCategories = new ArrayList<>();
    }

    /**
     * Called after {@link #onFinish}
     */
    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        clear();
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

    public void goBack() {
        if (getView() != null) {
            getView().goBack();
        }
    }

    public boolean isDialogShown() {
        // Also check that current dialog almost closed (new view start is pending from a menu item)
        // Hmm. Maybe current dialog is pending. Check that view is null.
        // Also check that we aren't started the same view (nested dialog).
        return ViewManager.isVisible(getView()) && (!ViewManager.instance(getContext()).isNewViewPending(AppDialogView.class) || getView() == null);
    }

    public void appendRadioCategory(String categoryTitle, List<OptionItem> items) {
        mCategories.add(OptionCategory.radioList(categoryTitle, items));
    }

    public void appendCheckedCategory(String categoryTitle, List<OptionItem> items) {
        mCategories.add(OptionCategory.checkedList(categoryTitle, items));
    }

    public void appendStringsCategory(String categoryTitle, List<OptionItem> items) {
        mCategories.add(OptionCategory.stringList(categoryTitle, items));
    }

    public void appendLongTextCategory(String categoryTitle, OptionItem item) {
        mCategories.add(OptionCategory.longText(categoryTitle, item));
    }

    public void appendChatCategory(String categoryTitle, OptionItem item) {
        mCategories.add(OptionCategory.chat(categoryTitle, item));
    }

    public void appendCommentsCategory(String categoryTitle, OptionItem item) {
        mCategories.add(OptionCategory.comments(categoryTitle, item));
    }

    public void appendSingleSwitch(OptionItem optionItem) {
        mCategories.add(OptionCategory.singleSwitch(optionItem));
    }

    public void appendSingleButton(OptionItem optionItem) {
        mCategories.add(OptionCategory.singleButton(optionItem));
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
