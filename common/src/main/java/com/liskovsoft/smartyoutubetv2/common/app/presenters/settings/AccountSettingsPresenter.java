package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class AccountSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSettingsPresenter sInstance;
    private final SignInManager mSignInManager;
    private Disposable mAccountsAction;
    private final List<Account> mPendingRemove = new ArrayList<>();
    private Account mSelectedAccount = null;

    public AccountSettingsPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mSignInManager = service.getSignInManager();
    }

    public static AccountSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSettingsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        RxUtils.disposeActions(mAccountsAction);
        mPendingRemove.clear();
        mSelectedAccount = null;
        sInstance = null;
    }

    public void show() {
        mAccountsAction = mSignInManager.getAccountsObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::createAndShowDialog,
                        error -> Log.e(TAG, "Get signed accounts error: %s", error.getMessage())
                );
    }

    private void createAndShowDialog(List<Account> accounts) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        appendSelectAccountOnBoot(settingsPresenter);
        appendSelectAccountSection(accounts, settingsPresenter);
        appendAddAccountButton(settingsPresenter);
        appendRemoveAccountSection(accounts, settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_accounts), () -> {
            for (Account account : mPendingRemove) {
                mSignInManager.removeAccount(account);
            }

            if (!mPendingRemove.isEmpty()) {
                MessageHelpers.showMessage(getContext(), R.string.msg_done);
            }

            if (!mPendingRemove.contains(mSelectedAccount)) {
                mSignInManager.selectAccount(mSelectedAccount);
            }

            unhold();
        });
    }

    private void appendSelectAccountSection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
        List<OptionItem> optionItems = new ArrayList<>();

        optionItems.add(UiOptionItem.from(
                getContext().getString(R.string.dialog_account_none), optionItem -> mSelectedAccount = null, true
        ));

        for (Account account : accounts) {
            if (account.isSelected()) {
                mSelectedAccount = account;
            }

            optionItems.add(UiOptionItem.from(
                    formatAccount(account), option -> mSelectedAccount = account, account.isSelected()
            ));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_account_list), optionItems);
    }

    private void appendRemoveAccountSection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
        List<OptionItem> optionItems = new ArrayList<>();

        for (Account account : accounts) {
            optionItems.add(UiOptionItem.from(
                    formatAccount(account), option -> {
                        if (option.isSelected()) {
                            mPendingRemove.add(account);
                        } else {
                            mPendingRemove.remove(account);
                        }
                    }, false
            ));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_remove_account), optionItems);
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

    private String formatAccount(Account account) {
        String format;

        if (account.getEmail() != null) {
            format = String.format("%s (%s)", account.getName(), account.getEmail());
        } else {
            format = account.getName();
        }

        return format;
    }
}
