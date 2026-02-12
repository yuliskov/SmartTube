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
import com.liskovsoft.smartyoutubetv2.common.misc.LocalDriveBackupWorker;
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

    public void showLocalRestoreDialogApi30() {
        BackupAndRestoreManager backupManager = new BackupAndRestoreManager(getContext(), true);

        backupManager.getBackupNames(names -> showLocalRestoreDialog(backupManager, names));
    }

    private void createAndShowDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendLocalBackupCategory(settingsPresenter);

        appendDriveBackupCategory(settingsPresenter);

        appendSubscriptionsBackupButton(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.app_backup_restore), this::unhold);
    }

    private void appendDriveBackupCategory(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from("Google Drive", optionItem -> {
            AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
            appendGDriveBackupSettings(settingsPresenter2);
            appendGDriveRestoreSettings(settingsPresenter2);
            // NOTE: google account doesn't have a name or email
            appendGDriveAutoBackupButton(settingsPresenter2);
            appendGDriveMiscButtons(settingsPresenter2);
            settingsPresenter2.showDialog("Google Drive");
        }));
    }

    private void appendGDriveRestoreSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.app_restore), optionItem -> mGDriveBackupManager.restore()));
    }

    private void appendGDriveBackupSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.app_backup), optionItem -> mGDriveBackupManager.backup()));
    }

    private void appendLocalDriveAutoBackupOption(List<OptionItem> options) {
        options.add(
                UiOptionItem.from(
                        getContext().getString(R.string.auto_backup_category), option -> {
                            AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
                            List<OptionItem> options2 = new ArrayList<>();

                            for (int[] pair : new int[][] {
                                    {R.string.dialog_account_none, -1},
                                    {R.string.once_a_day, 1},
                                    {R.string.once_a_week, 7},
                                    {R.string.once_a_month, 30},
                            }) {
                                options2.add(UiOptionItem.from(getContext().getString(pair[0]),
                                        optionItem -> {
                                            mGeneralData.setLocalDriveBackupFreqDays(pair[1]);
                                            if (pair[1] > 0) {
                                                LocalDriveBackupWorker.forceSchedule(getContext());
                                            } else {
                                                LocalDriveBackupWorker.cancel(getContext());
                                            }
                                        },
                                        mGeneralData.getLocalDriveBackupFreqDays() == pair[1]
                                ));
                            }

                            settingsPresenter2.appendRadioCategory(getContext().getString(R.string.auto_backup_category), options2);
                            settingsPresenter2.showDialog();
                        })
        );
    }

    private void appendGDriveAutoBackupButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.auto_backup_category), option -> {
                    AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
                    List<OptionItem> options = new ArrayList<>();

                    for (int[] pair : new int[][] {
                            {R.string.dialog_account_none, -1},
                            {R.string.once_a_day, 1},
                            {R.string.once_a_week, 7},
                            {R.string.once_a_month, 30},
                    }) {
                        options.add(UiOptionItem.from(getContext().getString(pair[0]),
                                optionItem -> {
                                    mGeneralData.setGDriveBackupFreqDays(pair[1]);
                                    if (pair[1] > 0) {
                                        GDriveBackupWorker.forceSchedule(getContext());
                                    } else {
                                        GDriveBackupWorker.cancel(getContext());
                                    }
                                },
                                mGeneralData.getGDriveBackupFreqDays() == pair[1]
                        ));
                    }

                    settingsPresenter2.appendRadioCategory(getContext().getString(R.string.auto_backup_category), options);
                    settingsPresenter2.showDialog();
                }));
    }

    private void appendGDriveMiscButtons(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.device_specific_backup),
                option2 -> mGeneralData.setDeviceSpecificBackupEnabled(option2.isSelected()),
                mGeneralData.isDeviceSpecificBackupEnabled()
        ));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_account), option2 -> GoogleSignInPresenter.instance(getContext()).start()));
    }

    private void appendLocalBackupCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        appendBackupRestoreOptions(options);

        appendLocalDriveAutoBackupOption(options);

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.local_backup), options);
    }

    private void appendBackupRestoreOptions(List<OptionItem> options) {
        BackupAndRestoreManager backupManager = new BackupAndRestoreManager(getContext());

        String backupPath = backupManager.getBackupRootPath();

        options.add(UiOptionItem.from(
                backupPath == null ? getContext().getString(R.string.app_backup) :
                    String.format("%s:\n%s", getContext().getString(R.string.app_backup), backupPath),
                option -> {
                    AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_backup), () -> {
                        mSidebarService.enableSection(MediaGroup.TYPE_SETTINGS, true); // prevent Settings lock
                        backupManager.checkPermAndBackup();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    });
                }));

        options.add(UiOptionItem.from(
                backupPath == null ? getContext().getString(R.string.app_restore) :
                    String.format("%s:\n%s", getContext().getString(R.string.app_restore), backupPath),
                option -> {
                    backupManager.getBackupNames(names -> showLocalRestoreDialog(backupManager, names));
                }));
    }

    private void appendSubscriptionsBackupButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(AppDialogUtil.createSubscriptionsBackupButton(getContext()));
    }

    private void showLocalRestoreDialog(BackupAndRestoreManager backupManager, List<String> backups) {
        if (backups != null && !backups.isEmpty()) {
            showLocalRestoreSelectorDialog(backups, backupManager);
        } else {
            MessageHelpers.showLongMessage(getContext(), R.string.nothing_found);
        }
    }

    private void showLocalRestoreSelectorDialog(List<String> backups, BackupAndRestoreManager backupManager) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (String name : backups) {
            options.add(UiOptionItem.from(name, optionItem -> {
                AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_restore), () -> {
                    backupManager.checkPermAndRestore(name);
                });
            }));
        }

        dialog.appendStringsCategory(getContext().getString(R.string.app_restore), options);
        dialog.showDialog();
    }
}
