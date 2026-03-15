package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;

import java.lang.ref.WeakReference;

public abstract class ContextMenuProvider {
    public static int MENU_TYPE_ANY = 0;
    public static int MENU_TYPE_VIDEO = 1;
    public static int MENU_TYPE_SECTION = 2;
    private final long mId;
    private static final long START_ID = 1L << 50;
    private final WeakReference<Context> mContext;

    public ContextMenuProvider(Context context, int idx) {
        // NOTE: We can't use the ApplicationContext because providers often use EditDialog internally
        mContext = new WeakReference<>(context);
        mId = START_ID << idx;
    }

    public abstract int getTitleResId();
    public abstract void onClicked(Video item, VideoMenuCallback callback);
    public abstract boolean isEnabled(Video item);
    public abstract int getMenuType();

    public final Context getContext() {
        return mContext.get();
    }

    public final long getId() {
        return mId;
    }
}
