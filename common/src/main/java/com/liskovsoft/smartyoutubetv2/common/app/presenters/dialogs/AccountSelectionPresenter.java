package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.Account;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class AccountSelectionPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSelectionPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSelectionPresenter sInstance;
    private final SignInManager mSignInManager;
    private Disposable mAccountsAction;

    public AccountSelectionPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mSignInManager = service.getSignInManager();
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

        mAccountsAction = mSignInManager.getAccountsObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        accounts -> createAndShowDialog(accounts, force),
                        error -> Log.e(TAG, "Get accounts error: %s", error.getMessage())
                );
    }

    public void unhold() {
        RxUtils.disposeActions(mAccountsAction);
        sInstance = null;
    }

    private void createAndShowDialog(List<Account> accounts, boolean force) {
        if (accounts.size() <= 1 && !force) {
            return;
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        appendAccountSelection(accounts, settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_accounts), this::unhold);
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

    private void selectAccount(Account account) {
        mSignInManager.selectAccount(account);
        BrowsePresenter.instance(getContext()).refresh();
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
