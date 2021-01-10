package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.Account;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class DeviceLinkSettingsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static DeviceLinkSettingsPresenter sInstance;
    private final SignInManager mSignInManager;
    private Disposable mDevicesAction;
    private final List<Account> mPendingRemove = new ArrayList<>();
    private Account mSelectedAccount = null;

    public DeviceLinkSettingsPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mSignInManager = service.getSignInManager();
    }

    public static DeviceLinkSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new DeviceLinkSettingsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        RxUtils.disposeActions(mDevicesAction);
        mPendingRemove.clear();
        mSelectedAccount = null;
        sInstance = null;
    }

    public void show() {
        mDevicesAction = mSignInManager.getAccountsObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::createAndShowDialog);
    }

    private void createAndShowDialog(List<Account> accounts) {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        appendDeviceLinkEnableSwitch(settingsPresenter);
        appendRemoveDeviceSection(accounts, settingsPresenter);
        appendAddDeviceButton(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_linked_devices), () -> {
            //for (Account account : mPendingRemove) {
            //    mSignInManager.removeAccount(account);
            //}
            //
            //mSignInManager.selectAccount(mSelectedAccount);

            unhold();
        });
    }

    private void appendRemoveDeviceSection(List<Account> accounts, AppSettingsPresenter settingsPresenter) {
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

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_remove_device), optionItems);
    }

    private void appendAddDeviceButton(AppSettingsPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_device), option -> AddDevicePresenter.instance(getContext()).start()));
    }

    private void appendDeviceLinkEnableSwitch(AppSettingsPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.device_link_enabled), optionItem -> {
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
