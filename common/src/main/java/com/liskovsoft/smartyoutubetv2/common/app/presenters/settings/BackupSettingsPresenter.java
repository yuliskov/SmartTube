package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.googleapi.oauth2.impl.GoogleSignInService;
import com.liskovsoft.mediaserviceinterfaces.google.data.Account;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GDriveBackupManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

public class BackupSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = BackupSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static BackupSettingsPresenter sInstance;
    private final GoogleSignInService mSignInService;
    private final GDriveBackupManager mBackupManager;
    private final GeneralData mGeneralData;
    private Disposable mAccountListAction;

    private BackupSettingsPresenter(Context context) {
        super(context);
        mSignInService = GoogleSignInService.instance();
        mBackupManager = GDriveBackupManager.instance(context);
        mGeneralData = GeneralData.instance(context);
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
        RxHelper.disposeActions(mAccountListAction);

        mAccountListAction = mSignInService.getAccountsObserve()
                .subscribe(
                        this::createAndShowDialog,
                        error -> Log.e(TAG, "Get signed accounts error: %s", error.getMessage())
                );
    }

    private void createAndShowDialog(List<Account> accounts) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleButton(UiOptionItem.from("Google Drive", optionItem -> {
            AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
            appendBackupSettings(settingsPresenter2);
            appendRestoreSettings(settingsPresenter2);
            // NOTE: google account doesn't have a name or email
            appendMiscButton(settingsPresenter2);
            settingsPresenter2.showDialog("Google Drive");
        }));

        appendLocalBackupCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.app_backup_restore), this::unhold);
    }

    private void appendRestoreSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.app_restore), optionItem -> mBackupManager.restore()));
    }

    private void appendBackupSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.app_backup), optionItem -> mBackupManager.backup()));
    }

    private void appendMiscButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.player_other), option -> {
                    AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
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
                        mGeneralData.enableSection(MediaGroup.TYPE_SETTINGS, true); // prevent Settings lock
                        backupManager.checkPermAndBackup();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    });
                }));

        String backupPathCheck = backupManager.getBackupPathCheck();
        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_restore), backupPathCheck != null ? backupPathCheck : ""),
                option -> {
                    backupManager.getBackupNames(names -> showRestoreDialog(backupManager, names));
                }));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.local_backup), options);
    }

    private void showRestoreDialog(BackupAndRestoreManager backupManager, List<String> backups) {
        if (backups != null && backups.size() > 1) {
            showRestoreSelectorDialog(backups, backupManager);
        } else {
            AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_restore), () -> {
                backupManager.checkPermAndRestore();
            });
        }
    }

    private void showRestoreSelectorDialog(List<String> backups, BackupAndRestoreManager backupManager) {
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

    private String getFullName(Account account) {
        String format;

        if (account.getEmail() != null) {
            format = String.format("%s (%s)", account.getName(), account.getEmail());
        } else {
            format = account.getName();
        }

        return format;
    }

    private String getSimpleName(Account account) {
        return account.getName() != null ? account.getName() : account.getEmail();
    }

    private void removeAccount(Account account) {
        mSignInService.removeAccount(account);
    }
}
