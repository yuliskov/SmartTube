package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.remoteapi.RemoteApiAuthProvider;
import com.liskovsoft.smartyoutubetv2.common.misc.remoteapi.RemoteApiServer;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteApiData;

import java.util.ArrayList;
import java.util.List;

public class RemoteApiSettingsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static RemoteApiSettingsPresenter sInstance;
    private final RemoteApiData mRemoteApiData;
    private final RemoteApiAuthProvider mAuthProvider;

    public RemoteApiSettingsPresenter(Context context) {
        super(context);
        mRemoteApiData = RemoteApiData.instance(context);
        mAuthProvider = new RemoteApiAuthProvider(mRemoteApiData);
    }

    public static RemoteApiSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new RemoteApiSettingsPresenter(context);
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

    private void createAndShowDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendEnableSwitch(settingsPresenter);
        appendAllowAllSwitch(settingsPresenter);
        appendStatusText(settingsPresenter);
        appendShowCodeButton(settingsPresenter);
        appendPortCategory(settingsPresenter);
        appendDeviceNameCategory(settingsPresenter);
        appendRemoveAllDevicesButton(settingsPresenter);
        appendDebugButton(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_remote_api), this::unhold);
    }

    private void appendEnableSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.remote_api_enable), optionItem -> {
            mRemoteApiData.enableApi(optionItem.isSelected());

            if (optionItem.isSelected()) {
                RemoteApiServer.startRemoteApi(getContext());
            } else {
                RemoteApiServer.stopRemoteApi(getContext());
            }
        }, mRemoteApiData.isApiEnabled()));
    }

    private void appendAllowAllSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.remote_api_allow_all),
                optionItem -> mRemoteApiData.setAllowAllConnections(optionItem.isSelected()),
                mRemoteApiData.isAllowAllConnections()));
    }

    private void appendStatusText(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        String status;
        if (mRemoteApiData.isApiEnabled()) {
            status = getContext().getString(R.string.remote_api_status_running);
        } else {
            status = getContext().getString(R.string.remote_api_status_stopped);
        }

        String statusText = getContext().getString(R.string.remote_api_status, status, mRemoteApiData.getPort());
        options.add(UiOptionItem.from(statusText));

        settingsPresenter.appendStringsCategory(null, options);
    }

    private void appendShowCodeButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.remote_api_show_code), option -> {
                    String code = mAuthProvider.generatePairingCode();
                    String message = getContext().getString(R.string.remote_api_current_code, code);
                    MessageHelpers.showMessage(getContext(), message);
                }));
    }

    private void appendPortCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(String.valueOf(mRemoteApiData.getPort()), option -> {
            mRemoteApiData.setPort(Integer.parseInt(option.getData().toString()));
            MessageHelpers.showMessage(getContext(), getContext().getString(R.string.remote_api_restart_port));
        }));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.remote_api_port), options);
    }

    private void appendDeviceNameCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(mRemoteApiData.getDeviceName(), option -> {
            mRemoteApiData.setDeviceName(option.getData().toString());
        }));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.remote_api_device_name), options);
    }

    private void appendRemoveAllDevicesButton(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem confirmItem = UiOptionItem.from(
                getContext().getString(R.string.btn_confirm), option -> {
                    mRemoteApiData.removeAllTokens();
                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    settingsPresenter.closeDialog();
                }
        );

        options.add(confirmItem);

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.remote_api_remove_all), options);
    }

    private void appendDebugButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.remote_api_debug), option -> {
                    try {
                        Intent intent = new Intent();
                        intent.setClassName(getContext(),
                                "com.liskovsoft.smartyoutubetv2.tv.ui.debug.RemoteApiDebugActivity");
                        getContext().startActivity(intent);
                    } catch (Exception e) {
                        MessageHelpers.showMessage(getContext(), "Debug activity not found: " + e.getMessage());
                    }
                }));
    }
}
