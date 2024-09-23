package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

public abstract class ContextMenuProvider {
    private final long mId;
    private static final long START_ID = 1L << 50;

    public ContextMenuProvider(int pos) {
        mId = START_ID << pos;
    }

    public abstract String getTitle();
    public abstract void onClicked();

    public final long getId() {
        return mId;
    }
}
