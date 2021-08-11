package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

/**
 * Shows boot dialogs one by one.
 */
public class BootDialogsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static BootDialogsPresenter sInstance;

    public BootDialogsPresenter(Context context) {
        super(context);
    }

    public static BootDialogsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BootDialogsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start() {
        AppUpdatePresenter updatePresenter = AppUpdatePresenter.instance(getContext());
        updatePresenter.start(false);
        updatePresenter.unhold();
    }
}
