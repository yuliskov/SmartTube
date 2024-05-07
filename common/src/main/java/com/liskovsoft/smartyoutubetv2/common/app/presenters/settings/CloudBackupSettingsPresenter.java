package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.googleapi.service.GoogleSignInService;
import com.liskovsoft.mediaserviceinterfaces.google.data.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.GDriveBackupManager;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

public class CloudBackupSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = CloudBackupSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static CloudBackupSettingsPresenter sInstance;
    private final GoogleSignInService mSignInService;
    private final GDriveBackupManager mBackupManager;
    private Disposable mAccountListAction;

    private CloudBackupSettingsPresenter(Context context) {
        super(context);
        mSignInService = GoogleSignInService.instance();
        mBackupManager = new GDriveBackupManager(context);
    }

    public static CloudBackupSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new CloudBackupSettingsPresenter(context);
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

        appendSelectAccountSection(accounts, settingsPresenter);
        appendAddAccountButton(settingsPresenter);
        appendRemoveAccountSection(accounts, settingsPresenter);
        appendBackupSettings(settingsPresenter);
        appendRestoreSettings(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.app_backup_restore), this::unhold);
    }

    private void appendSelectAccountSection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        optionItems.add(UiOptionItem.from(
                getContext().getString(R.string.dialog_account_none), optionItem -> {
                    AccountSelectionPresenter.instance(getContext()).selectAccount(null);
                    settingsPresenter.closeDialog();
                }, true
        ));

        String accountName = " (" + getContext().getString(R.string.dialog_account_none) + ")";

        for (Account account : accounts) {
            optionItems.add(UiOptionItem.from(
                    getFullName(account), option -> {
                        mSignInService.selectAccount(account);
                        settingsPresenter.closeDialog();
                    }, account.isSelected()
            ));

            if (account.isSelected()) {
                accountName = " (" + getSimpleName(account) + ")";
            }
        }

        settingsPresenter.appendRadioCategory("Google Drive: " + getContext().getString(R.string.dialog_account_list) + accountName, optionItems);
    }

    private void appendRemoveAccountSection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        for (Account account : accounts) {
            optionItems.add(UiOptionItem.from(
                    getFullName(account), option ->
                        AppDialogUtil.showConfirmationDialog(
                                getContext(), getContext().getString(R.string.dialog_remove_account), () -> {
                                    removeAccount(account);
                                    settingsPresenter.closeDialog();
                                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                                })
            ));
        }

        settingsPresenter.appendStringsCategory("Google Drive: " + getContext().getString(R.string.dialog_remove_account), optionItems);
    }

    private void appendRestoreSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from("Google Drive: " + getContext().getString(R.string.app_restore), optionItem -> mBackupManager.restore()));
    }

    private void appendBackupSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from("Google Drive: " + getContext().getString(R.string.app_backup), optionItem -> mBackupManager.backup()));
    }

    private void appendAddAccountButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                "Google Drive: " + getContext().getString(R.string.dialog_add_account), option -> GoogleSignInPresenter.instance(getContext()).start()));
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
