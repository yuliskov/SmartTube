package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.SidebarFolderDialog;

public class FolderEmptyError implements ErrorFragmentData {
    private final Context mContext;
    private final int mFolderId;

    public FolderEmptyError(Context context, int folderId) {
        mContext = context;
        mFolderId = folderId;
    }

    @Override
    public void onAction() {
        SidebarFolderDialog.showItemsDialog(mContext, mFolderId);
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.folder_is_empty);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.choose_folder_items);
    }
}
