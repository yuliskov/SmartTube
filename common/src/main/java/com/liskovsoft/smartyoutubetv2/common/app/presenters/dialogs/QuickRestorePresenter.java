package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreManager;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

public class QuickRestorePresenter extends BasePresenter<Void> {
    private static QuickRestorePresenter sInstance;

    public QuickRestorePresenter(Context context) {
        super(context);
    }

    public static QuickRestorePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new QuickRestorePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start() {
        BackupAndRestoreManager backupManager = new BackupAndRestoreManager(getContext());

        //if (backupManager.hasBackup()) {
        //    AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_restore), () -> {
        //        backupManager.checkPermAndRestore();
        //        MessageHelpers.showMessage(getContext(), R.string.msg_done);
        //    });
        //}
    }
}
