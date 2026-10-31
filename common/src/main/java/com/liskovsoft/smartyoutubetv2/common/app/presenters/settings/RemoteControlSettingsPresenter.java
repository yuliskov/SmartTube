package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.RemoteControlService;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

public class RemoteControlSettingsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static RemoteControlSettingsPresenter sInstance;
    private final RemoteControlData mRemoteControlData;
    private final RemoteControlService mRemoteManager;

    public RemoteControlSettingsPresenter(Context context) {
        super(context);
        ServiceManager service = YouTubeServiceManager.instance();
        mRemoteManager = service.getRemoteControlService();
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

            // Background playback on Android 10 and above
            // Shows overlay dialog if needed (alive activity required)
            if (optionItem.isSelected() && !PermissionHelpers.hasOverlayPermissions(getContext())) {
                AppDialogUtil.showConfirmationDialog(
                        getContext(), getContext().getString(R.string.remote_control_permission),
                        () -> PermissionHelpers.verifyOverlayPermissions(getContext())
                );
            }
        }, mRemoteControlData.isDeviceLinkEnabled()));
    }

    private void appendAddDeviceButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_device), option -> {
                    mRemoteControlData.enableDeviceLink(true);
                    Utils.updateRemoteControlService(getContext());

                    // Background playback on Android 10 and above
                    // Shows overlay dialog if needed (alive activity required)
                    if (!PermissionHelpers.hasOverlayPermissions(getContext())) {
                        AppDialogUtil.showConfirmationDialog(
                                getContext(), getContext().getString(R.string.remote_control_permission), () -> {
                                    PermissionHelpers.verifyOverlayPermissions(getContext());
                                    // Service that prevents the app from destroying
                                    if (getContext() instanceof MotherActivity) {
                                        ((MotherActivity) getContext()).addOnResult(
                                                (request, response, data) -> AddDevicePresenter.instance(getContext()).start()
                                        );
                                    }
                                }, () -> AddDevicePresenter.instance(getContext()).start()
                        );
                    } else {
                        AddDevicePresenter.instance(getContext()).start();
                    }
                }));
    }

    private void appendRemoveAllDevicesButton(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem confirmItem = UiOptionItem.from(
                getContext().getString(R.string.btn_confirm), option -> {
                    RxHelper.execute(mRemoteManager.resetDataObserve());
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
        //options.add(UiOptionItem.from(getContext().getString(R.string.show_connect_messages),
        //        option -> mRemoteControlData.enableConnectMessages(option.isSelected()),
        //        mRemoteControlData.isConnectMessagesEnabled()));
        options.add(UiOptionItem.from(getContext().getString(R.string.disable_remote_history),
                option -> mRemoteControlData.disableRemoteHistory(option.isSelected()),
                mRemoteControlData.isRemoteHistoryDisabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
