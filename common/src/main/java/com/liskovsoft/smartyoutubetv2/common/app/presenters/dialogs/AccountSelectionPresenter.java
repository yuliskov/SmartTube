package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.yt.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.yt.SignInService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.Account;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

public class AccountSelectionPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSelectionPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSelectionPresenter sInstance;
    private final SignInService mSignInService;
    private Disposable mAccountsAction;

    public AccountSelectionPresenter(Context context) {
        super(context);
        ServiceManager service = YouTubeServiceManager.instance();
        mSignInService = service.getSignInService();
    }

    public static AccountSelectionPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSelectionPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void show() {
        show(false);
    }

    public void show(boolean force) {
        if (!AccountsData.instance(getContext()).isSelectAccountOnBootEnabled() && !force) {
            // user don't want to see selection dialog
            return;
        }

        mAccountsAction = mSignInService.getAccountsObserve()
                .subscribe(
                        accounts -> createAndShowDialog(accounts, force),
                        error -> Log.e(TAG, "Get accounts error: %s", error.getMessage())
                );
    }

    public void nextAccountOrDialog() {
        MediaServiceManager.instance().loadAccounts(this::nextAccountOrDialog);
    }

    public void unhold() {
        RxHelper.disposeActions(mAccountsAction);
        sInstance = null;
    }

    private void createAndShowDialog(List<Account> accounts, boolean force) {
        if (accounts.size() <= 1 && !force) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        appendAccountSelection(accounts, dialogPresenter);

        dialogPresenter.showDialog(getContext().getString(R.string.settings_accounts), this::unhold);
    }

    private void appendAccountSelection(List<Account> accounts, AppDialogPresenter settingsPresenter) {
        List<OptionItem> optionItems = new ArrayList<>();

        optionItems.add(UiOptionItem.from(
                getContext().getString(R.string.dialog_account_none), optionItem -> {
                    selectAccount(null);
                    settingsPresenter.closeDialog();
                }, true
        ));

        for (Account account : accounts) {
            optionItems.add(UiOptionItem.from(
                    formatAccount(account), option -> {
                        selectAccount(account);
                        settingsPresenter.closeDialog();
                    }, account.isSelected()
            ));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_account_list), optionItems);
    }

    private void nextAccountOrDialog(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            AccountSettingsPresenter.instance(getContext()).show();
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

    public void selectAccount(Account account) {
        mSignInService.selectAccount(account);
        //BrowsePresenter.instance(getContext()).refresh(false);
        Utils.updateChannels(getContext());
        //BrowsePresenter.instance(getContext()).onViewInitialized(); // reset state

        // Account history might be turned off (common issue).
        GeneralData generalData = GeneralData.instance(getContext());
        if (generalData.getHistoryState() != GeneralData.HISTORY_AUTO) {
            MediaServiceManager.instance().enableHistory(generalData.isHistoryEnabled());
        }
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
