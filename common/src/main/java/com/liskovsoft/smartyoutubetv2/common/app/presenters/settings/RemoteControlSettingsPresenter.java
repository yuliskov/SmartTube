package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.RemoteService;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

import java.util.ArrayList;
import java.util.List;

public class RemoteControlSettingsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static RemoteControlSettingsPresenter sInstance;
    private final RemoteControlData mRemoteControlData;
    private final RemoteService mRemoteManager;

    public RemoteControlSettingsPresenter(Context context) {
        super(context);
        MediaService mediaService = YouTubeMediaService.instance();
        mRemoteManager = mediaService.getRemoteService();
        mRemoteControlData = RemoteControlData.instance(context);
    }

    public static RemoteControlSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new RemoteControlSettingsPresenter(context);
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
        settingsPresenter.clear();

        appendDeviceLinkSwitch(settingsPresenter);
        appendAddDeviceButton(settingsPresenter);
        appendRemoveAllDevicesButton(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_remote_control), this::unhold);
    }

    private void appendDeviceLinkSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.settings_remote_control), optionItem -> {
            // Remote link depends on background service
            mRemoteControlData.enableDeviceLink(optionItem.isSelected());
            Utils.updateRemoteControlService(getContext());
        }, mRemoteControlData.isDeviceLinkEnabled()));
    }

    private void appendAddDeviceButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_device), option -> {
                    AddDevicePresenter.instance(getContext()).start();

                    mRemoteControlData.enableDeviceLink(true);
                    Utils.updateRemoteControlService(getContext());
                }));
    }

    private void appendRemoveAllDevicesButton(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem confirmItem = UiOptionItem.from(
                getContext().getString(R.string.btn_confirm), option -> {
                    RxUtils.execute(mRemoteManager.resetDataObserve());
                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    settingsPresenter.closeDialog();

                    mRemoteControlData.enableDeviceLink(false);
                    Utils.updateRemoteControlService(getContext());
                }
        );

        options.add(confirmItem);

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.dialog_remove_all_devices), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.finish_on_disconnect),
                option -> mRemoteControlData.enableFinishOnDisconnect(option.isSelected()),
                mRemoteControlData.isFinishOnDisconnectEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
