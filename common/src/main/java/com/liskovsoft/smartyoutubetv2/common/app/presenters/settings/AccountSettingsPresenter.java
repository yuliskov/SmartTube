package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

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

    public void nextAccountOrDialog() {
        mMediaServiceManager.loadAccounts(this::nextAccountOrDialog);
    }

    private void createAndShowDialog(List<Account> accounts) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendSelectAccountSection(accounts, settingsPresenter);
        appendAddAccountButton(settingsPresenter);
        appendRemoveAccountSection(accounts, settingsPresenter);
        appendSelectAccountOnBoot(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_accounts), this::unhold);
    }

    private void appendSelectAccountSection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        optionItems.add(UiOptionItem.from(
                getContext().getString(R.string.dialog_account_none), optionItem -> {
                    selectAccount(null);
                    settingsPresenter.closeDialog();
                }, true
        ));

        String accountName = " (" + getContext().getString(R.string.dialog_account_none) + ")";

        for (Account account : accounts) {
            optionItems.add(UiOptionItem.from(
                    getFullName(account), option -> {
                        selectAccount(account);
                        settingsPresenter.closeDialog();
                    }, account.isSelected()
            ));

            if (account.isSelected()) {
                accountName = " (" + getSimpleName(account) + ")";
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_account_list) + accountName, optionItems);
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

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.dialog_remove_account), optionItems);
    }

    private void appendAddAccountButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_account), option -> SignInPresenter.instance(getContext()).start()));
    }

    private void appendSelectAccountOnBoot(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.select_account_on_boot), optionItem -> {
            AccountsData.instance(getContext()).selectAccountOnBoot(optionItem.isSelected());
        }, AccountsData.instance(getContext()).isSelectAccountOnBootEnabled()));
    }

    private void nextAccountOrDialog(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            createAndShowDialog(accounts);
            return;
        }

        Account current = null;

        for (Account account : accounts) {
            if (account.isSelected()) {
                current = account;
                break;
            }
        }

        int index = accounts.indexOf(current);

        int nextIndex = index + 1;
        // null == 'without account'
        selectAccount(nextIndex == accounts.size() ? null : accounts.get(nextIndex));
        //selectAccount(accounts.get(nextIndex == accounts.size() ? 0 : nextIndex));
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

    private void selectAccount(Account account) {
        mMediaServiceManager.getSingInManager().selectAccount(account);
        ExoMediaSourceFactory.unhold();
        BrowsePresenter.instance(getContext()).refresh(false);
    }

    private void removeAccount(Account account) {
        mMediaServiceManager.getSingInManager().removeAccount(account);
        ExoMediaSourceFactory.unhold();
        BrowsePresenter.instance(getContext()).refresh(false);
    }
}
