package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GDriveBackupManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GDriveBackupWorker;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.ArrayList;
import java.util.List;

public class BackupSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = BackupSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static BackupSettingsPresenter sInstance;
    private final GDriveBackupManager mGDriveBackupManager;
    private final GeneralData mGeneralData;
    private final SidebarService mSidebarService;

    private BackupSettingsPresenter(Context context) {
        super(context);
        mGDriveBackupManager = GDriveBackupManager.instance(context);
        mGeneralData = GeneralData.instance(context);
        mSidebarService = SidebarService.instance(context);
    }

    public static BackupSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupSettingsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void show() {
        createAndShowDialog();
    }

    private void createAndShowDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleButton(UiOptionItem.from("Google Drive", optionItem -> {
            AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
            appendGDriveBackupSettings(settingsPresenter2);
            appendGDriveRestoreSettings(settingsPresenter2);
            // NOTE: google account doesn't have a name or email
            appendGDriveMiscButton(settingsPresenter2);
            settingsPresenter2.showDialog("Google Drive");
        }));

        appendLocalBackupCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.app_backup_restore), this::unhold);
    }

    private void appendGDriveRestoreSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.app_restore), optionItem -> mGDriveBackupManager.restore()));
    }

    private void appendGDriveBackupSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.app_backup), optionItem -> mGDriveBackupManager.backup()));
    }

    private void appendGDriveMiscButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.player_other), option -> {
                    AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
                    settingsPresenter2.appendSingleSwitch(UiOptionItem.from(
                            getContext().getString(R.string.auto_backup),
                            option2 -> {
                                mGeneralData.enableAutoBackup(option2.isSelected());
                                if (option2.isSelected()) {
                                    GDriveBackupWorker.forceSchedule(getContext());
                                } else {
                                    GDriveBackupWorker.cancel(getContext());
                                }
                            },
                            mGeneralData.isAutoBackupEnabled()
                    ));
                    settingsPresenter2.appendSingleSwitch(UiOptionItem.from(
                            getContext().getString(R.string.device_specific_backup),
                            option2 -> mGeneralData.enableDeviceSpecificBackup(option2.isSelected()),
                            mGeneralData.isDeviceSpecificBackupEnabled()
                    ));
                    settingsPresenter2.appendSingleButton(UiOptionItem.from(
                            getContext().getString(R.string.dialog_add_account), option2 -> GoogleSignInPresenter.instance(getContext()).start()));
                    settingsPresenter2.showDialog(getContext().getString(R.string.player_other));
                }));
    }

    private void appendLocalBackupCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        BackupAndRestoreManager backupManager = new BackupAndRestoreManager(getContext());

        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_backup), backupManager.getBackupPath()),
                option -> {
                    AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_backup), () -> {
                        mSidebarService.enableSection(MediaGroup.TYPE_SETTINGS, true); // prevent Settings lock
                        backupManager.checkPermAndBackup();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    });
                }));

        String backupPathCheck = backupManager.getBackupPathCheck();
        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_restore), backupPathCheck != null ? backupPathCheck : ""),
                option -> {
                    backupManager.getBackupNames(names -> showLocalRestoreDialog(backupManager, names));
                }));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.local_backup), options);
    }

    private void showLocalRestoreDialog(BackupAndRestoreManager backupManager, List<String> backups) {
        if (backups != null && backups.size() > 1) {
            showLocalRestoreSelectorDialog(backups, backupManager);
        } else {
            AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_restore), () -> {
                backupManager.checkPermAndRestore();
            });
        }
    }

    private void showLocalRestoreSelectorDialog(List<String> backups, BackupAndRestoreManager backupManager) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (String name : backups) {
            options.add(UiOptionItem.from(name, optionItem -> {
                backupManager.checkPermAndRestore(name);
            }));
        }

        dialog.appendStringsCategory(getContext().getString(R.string.app_restore), options);
        dialog.showDialog();
    }
}
