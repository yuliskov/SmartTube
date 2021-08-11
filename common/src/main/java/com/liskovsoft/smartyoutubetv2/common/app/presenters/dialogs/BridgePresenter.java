package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

/**
 * Shows boot dialogs one by one.
 */
public class BridgePresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static BridgePresenter sInstance;

    public BridgePresenter(Context context) {
        super(context);
    }

    public static BridgePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BridgePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start() {
        
    }
}
