package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.RemoteManager;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

import java.util.ArrayList;
import java.util.List;

public class RemoteControlSettingsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static RemoteControlSettingsPresenter sInstance;
    private final RemoteControlData mDeviceLinkData;
    private final RemoteManager mRemoteManager;

    public RemoteControlSettingsPresenter(Context context) {
        super(context);
        MediaService mediaService = YouTubeMediaService.instance();
        mRemoteManager = mediaService.getRemoteManager();
        mDeviceLinkData = RemoteControlData.instance(context);
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
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        //appendLinkEnableSwitch(settingsPresenter);
        appendAddDeviceButton(settingsPresenter);
        appendRemoveAllDevicesButton(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_linked_devices), this::unhold);
    }

    private void appendRemoveAllDevicesButton(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem confirmItem = UiOptionItem.from(
                getContext().getString(R.string.btn_confirm), option -> {
                    RxUtils.execute(mRemoteManager.resetDataObserve());
                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    settingsPresenter.closeDialog();
                }
        );

        options.add(confirmItem);

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.dialog_remove_all_devices), options);
    }

    private void appendAddDeviceButton(AppSettingsPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_device), option -> AddDevicePresenter.instance(getContext()).start()));
    }

    private void appendLinkEnableSwitch(AppSettingsPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.device_link_enabled), optionItem -> {
            mDeviceLinkData.enableDeviceLink(optionItem.isSelected());
        }, mDeviceLinkData.isDeviceLinkEnabled()));
    }
}
