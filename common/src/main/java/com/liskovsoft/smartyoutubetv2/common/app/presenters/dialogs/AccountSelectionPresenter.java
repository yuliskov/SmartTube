package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AccountSelectionView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.List;

public class AccountSelectionPresenter extends BasePresenter<AccountSelectionView> {
    private static final String TAG = AccountSelectionPresenter.class.getSimpleName();
    private static final long SWITCH_ACCOUNT_DELAY_MS = 1_500;
    @SuppressLint("StaticFieldLeak")
    private static AccountSelectionPresenter sInstance;
    private final SignInService mSignInService;

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

        List<Account> accounts = mSignInService.getAccounts();

        if ((accounts == null || accounts.size() <= 1) && !force) {
            return;
        }

        getViewManager().startView(AccountSelectionView.class);
    }

    public void nextAccountOrDialog() {
        MediaServiceManager.instance().loadAccounts(this::nextAccountOrDialog);
    }

    public void unhold() {
        sInstance = null;
    }

    @Override
    public void onViewResumed() {
        super.onViewResumed();

        // Refresh on every resume: the list could change after returning from the sign in screen
        if (getView() != null) {
            getView().update(mSignInService.getAccounts());
        }
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    public void onAccountClicked(Account account) {
        if (getView() != null) {
            getView().close();
        }

        boolean isAccountChanged = account != null ? !account.isSelected() : mSignInService.getSelectedAccount() != null;
        PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());

        if (isAccountChanged && playbackPresenter.getView() != null) {
            // Start a new session: the previous video (e.g. playing in background or PIP) shouldn't
            // keep running and end up in the watch history of the newly selected account.
            // Switch after a delay to let the player save its last position under the previous account.
            playbackPresenter.forceFinish();
            Utils.postDelayed(() -> selectAccount(account), SWITCH_ACCOUNT_DELAY_MS);
        } else {
            selectAccount(account);
        }
    }

    public void onAddAccountClicked() {
        YTSignInPresenter.instance(getContext()).start();
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
        Utils.updateChannels(getContext());

        // Account history might be turned off (common issue).
        GeneralData generalData = GeneralData.instance(getContext());
        int historyState = generalData.getHistoryState();
        if (historyState != GeneralData.HISTORY_AUTO) {
            MediaServiceManager.instance().enableHistory(historyState != GeneralData.HISTORY_DISABLED);
        }
    }
}
