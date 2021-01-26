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
import com.liskovsoft.smartyoutubetv2.common.prefs.DeviceLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

public class DeviceLinkSettingsPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static DeviceLinkSettingsPresenter sInstance;
    private final DeviceLinkData mDeviceLinkData;
    private final RemoteManager mRemoteManager;

    public DeviceLinkSettingsPresenter(Context context) {
        super(context);
        MediaService mediaService = YouTubeMediaService.instance();
        mRemoteManager = mediaService.getRemoteManager();
        mDeviceLinkData = DeviceLinkData.instance(context);
    }

    public static DeviceLinkSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new DeviceLinkSettingsPresenter(context);
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

        appendLinkEnableSwitch(settingsPresenter);
        appendAddDeviceButton(settingsPresenter);
        appendRemoveAllDevicesButton(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_linked_devices), this::unhold);
    }

    private void appendRemoveAllDevicesButton(AppSettingsPresenter settingsPresenter) {
        OptionItem optionItem = UiOptionItem.from(
                getContext().getString(R.string.dialog_remove_all_devices), option -> {
                    RxUtils.execute(mRemoteManager.resetDataObserve());
                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                }
        );

        settingsPresenter.appendSingleButton(optionItem);
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
