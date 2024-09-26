package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public abstract class ContextMenuProvider {
    public static int MENU_TYPE_ANY = 0;
    public static int MENU_TYPE_VIDEO = 1;
    public static int MENU_TYPE_SECTION = 2;
    private final long mId;
    private static final long START_ID = 1L << 50;

    public ContextMenuProvider(int idx) {
        mId = START_ID << idx;
    }

    public abstract int getTitleResId();
    public abstract void onClicked(Video item);
    public abstract boolean isEnabled(Video item);
    public abstract int getMenuType();

    public final long getId() {
        return mId;
    }
}
