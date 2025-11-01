package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

public class AccountSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSettingsPresenter sInstance;
    private final MediaServiceManager mMediaServiceManager;

    public AccountSettingsPresenter(Context context) {
        super(context);
        mMediaServiceManager = MediaServiceManager.instance();
    }

    public static AccountSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSettingsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void show() {
        mMediaServiceManager.loadAccounts(this::createAndShowDialog);
    }

    private void createAndShowDialog(List<Account> accounts) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendSelectAccountSection(accounts, settingsPresenter);
        appendSignInButton(settingsPresenter);
        appendSignOutSection(accounts, settingsPresenter);
        appendProtectAccountWithPassword(settingsPresenter);
        appendSeparateSettings(settingsPresenter);
        appendSelectAccountOnBoot(settingsPresenter);

        Account account = getSignInService().getSelectedAccount();
        settingsPresenter.showDialog(account != null ? account.getName() : getContext().getString(R.string.settings_accounts), this::unhold);
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
                        AccountSelectionPresenter.instance(getContext()).selectAccount(account);
                        settingsPresenter.closeDialog();
                    }, account.isSelected()
            ));

            if (account.isSelected()) {
                accountName = " (" + getSimpleName(account) + ")";
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_account_list) + accountName, optionItems);
    }

    private void appendSignOutSection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
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

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.dialog_remove_account), optionItems);
    }

    private void appendSignInButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_account), option -> YTSignInPresenter.instance(getContext()).start()));
    }

    private void appendSelectAccountOnBoot(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.select_account_on_boot), optionItem -> {
            AccountsData.instance(getContext()).selectAccountOnBoot(optionItem.isSelected());
        }, AccountsData.instance(getContext()).isSelectAccountOnBootEnabled()));
    }

    private void appendProtectAccountWithPassword(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.protect_account_with_password), optionItem -> {
            if (optionItem.isSelected()) {
                showAddPasswordDialog(settingsPresenter);
            } else {
                showRemovePasswordDialog(settingsPresenter);
            }
        }, AccountsData.instance(getContext()).getAccountPassword() != null));
    }

    private void appendSeparateSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.multi_profiles),
                option -> {
                    AppPrefs.instance(getContext()).enableMultiProfiles(option.isSelected());
                    BrowsePresenter.instance(getContext()).updateSections();
                },
                AppPrefs.instance(getContext()).isMultiProfilesEnabled()));
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
        getSignInService().removeAccount(account);
        BrowsePresenter.instance(getContext()).refresh(false);
    }

    private void showAddPasswordDialog(AppDialogPresenter settingsPresenter) {
        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_account_password),
                null,
                newValue -> {
                    AccountsData.instance(getContext()).setAccountPassword(newValue);
                    BrowsePresenter.instance(getContext()).updateSections();
                    //onSuccess.run();
                    return true;
                });
    }

    private void showRemovePasswordDialog(AppDialogPresenter settingsPresenter) {
        String password = AccountsData.instance(getContext()).getAccountPassword();

        if (password == null) {
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_account_password),
                null,
                newValue -> {
                    if (password.equals(newValue)) {
                        AccountsData.instance(getContext()).setAccountPassword(null);
                        BrowsePresenter.instance(getContext()).updateSections();
                        //onSuccess.run();
                        return true;
                    }
                    return false;
                });
    }

    public void showCheckPasswordDialog() {
        String password = AccountsData.instance(getContext()).getAccountPassword();

        if (password == null) {
            return;
        }

        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_account_password),
                null,
                newValue -> {
                    if (password.equals(newValue)) {
                        AccountsData.instance(getContext()).setPasswordAccepted(true);
                        BrowsePresenter.instance(getContext()).updateSections();
                        //onSuccess.run();
                        return true;
                    }
                    return false;
                });
    }
}
