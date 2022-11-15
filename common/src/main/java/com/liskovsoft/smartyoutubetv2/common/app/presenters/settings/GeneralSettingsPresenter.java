package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.proxy.WebProxyDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final MainUIData mMainUIData;
    private boolean mRestartApp;

    private GeneralSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mMainUIData = MainUIData.instance(context);
    }

    public static GeneralSettingsPresenter instance(Context context) {
        return new GeneralSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        appendBootToSection(settingsPresenter);
        appendEnabledSections(settingsPresenter);
        appendContextMenuItemsCategory(settingsPresenter);
        appendVariousButtonsCategory(settingsPresenter);
        appendAppExitCategory(settingsPresenter);
        appendBackgroundPlaybackCategory(settingsPresenter);
        //appendBackgroundPlaybackActivationCategory(settingsPresenter);
        appendScreenDimmingCategory(settingsPresenter);
        appendTimeFormatCategory(settingsPresenter);
        appendKeyRemappingCategory(settingsPresenter);
        appendAppBackupCategory(settingsPresenter);
        appendInternetCensorship(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_general), () -> {
            if (mRestartApp) {
                mRestartApp = false;
                MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
            }
        });
    }

    private void appendEnabledSections(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mGeneralData.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            int sectionResId = section.getKey();
            int sectionId = section.getValue();

            if (sectionId == MediaGroup.TYPE_SETTINGS) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(sectionResId), optionItem -> {
                BrowsePresenter.instance(getContext()).enableSection(sectionId, optionItem.isSelected());
            }, mGeneralData.isSectionEnabled(sectionId)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.side_panel_sections), options);
    }

    private void appendContextMenuItemsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.clear_history, MainUIData.MENU_ITEM_CLEAR_HISTORY},
                {R.string.pause_history, MainUIData.MENU_ITEM_TOGGLE_HISTORY},
                {R.string.playlist_order, MainUIData.MENU_ITEM_PLAYLIST_ORDER},
                {R.string.add_remove_from_playback_queue, MainUIData.MENU_ITEM_ADD_TO_QUEUE},
                {R.string.action_playback_queue, MainUIData.MENU_ITEM_SHOW_QUEUE},
                {R.string.set_stream_reminder, MainUIData.MENU_ITEM_STREAM_REMINDER},
                {R.string.subscribe_unsubscribe_from_channel, MainUIData.MENU_ITEM_SUBSCRIBE},
                {R.string.save_remove_playlist, MainUIData.MENU_ITEM_SAVE_PLAYLIST},
                {R.string.create_playlist, MainUIData.MENU_ITEM_CREATE_PLAYLIST},
                {R.string.add_video_to_new_playlist, MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST},
                {R.string.dialog_add_to_playlist, MainUIData.MENU_ITEM_ADD_TO_PLAYLIST},
                {R.string.add_remove_from_recent_playlist, MainUIData.MENU_ITEM_RECENT_PLAYLIST},
                {R.string.play_video, MainUIData.MENU_ITEM_PLAY_VIDEO},
                {R.string.not_interested, MainUIData.MENU_ITEM_NOT_INTERESTED},
                {R.string.remove_from_history, MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY},
                {R.string.pin_unpin_from_sidebar, MainUIData.MENU_ITEM_PIN_TO_SIDEBAR},
                {R.string.share_link, MainUIData.MENU_ITEM_SHARE_LINK},
                {R.string.share_embed_link, MainUIData.MENU_ITEM_SHARE_EMBED_LINK},
                {R.string.dialog_account_list, MainUIData.MENU_ITEM_SELECT_ACCOUNT},
                {R.string.move_section_up, MainUIData.MENU_ITEM_MOVE_SECTION_UP},
                {R.string.move_section_down, MainUIData.MENU_ITEM_MOVE_SECTION_DOWN},
                {R.string.rename_section, MainUIData.MENU_ITEM_RENAME_SECTION},
                {R.string.action_video_info, MainUIData.MENU_ITEM_OPEN_DESCRIPTION}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.enableMenuItem(pair[1]);
                } else {
                    mMainUIData.disableMenuItem(pair[1]);
                }
            }, mMainUIData.isMenuItemEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.context_menu), options);
    }

    private void appendVariousButtonsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.settings_search, MainUIData.TOP_BUTTON_SEARCH},
                {R.string.settings_language_country, MainUIData.TOP_BUTTON_CHANGE_LANGUAGE},
                {R.string.settings_accounts, MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.enableTopButton(pair[1]);
                } else {
                    mMainUIData.disableTopButton(pair[1]);
                }
            }, mMainUIData.isTopButtonEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.various_buttons), options);
    }

    private void appendBootToSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mGeneralData.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            options.add(
                    UiOptionItem.from(
                            getContext().getString(section.getKey()),
                            optionItem -> mGeneralData.setBootSectionId(section.getValue()),
                            section.getValue().equals(mGeneralData.getBootSectionId())
                    )
            );
        }

        Collection<Video> pinnedItems = mGeneralData.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null && item.title != null) {
                options.add(
                        UiOptionItem.from(
                                item.title,
                                optionItem -> mGeneralData.setBootSectionId(item.hashCode()),
                                item.hashCode() == mGeneralData.getBootSectionId()
                        )
                );
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.boot_to_section), options);
    }

    private void appendAppExitCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_exit_none, GeneralData.EXIT_NONE},
                {R.string.app_double_back_exit, GeneralData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, GeneralData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mGeneralData.setAppExitShortcut(pair[1]),
                    mGeneralData.getAppExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.app_exit_shortcut), options);
    }

    private void appendBackgroundPlaybackCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createBackgroundPlaybackCategory(getContext(), mPlayerData, mGeneralData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    //private void appendBackgroundPlaybackActivationCategory(AppDialogPresenter settingsPresenter) {
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    options.add(UiOptionItem.from("HOME",
    //            option -> mGeneralData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME),
    //            mGeneralData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));
    //
    //    options.add(UiOptionItem.from("HOME/BACK",
    //            option -> mGeneralData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_N_BACK),
    //            mGeneralData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_N_BACK));
    //
    //    settingsPresenter.appendRadioCategory(getContext().getString(R.string.background_playback_activation), options);
    //}

    private void appendKeyRemappingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Next/Previous",
                option -> mGeneralData.remapFastForwardToNext(option.isSelected()),
                mGeneralData.isRemapFastForwardToNextEnabled()));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Speed Up/Down",
                option -> mGeneralData.remapFastForwardToSpeed(option.isSelected()),
                mGeneralData.isRemapFastForwardToSpeedEnabled()));

        options.add(UiOptionItem.from("Page Up/Down -> Next/Previous",
                option -> mGeneralData.remapPageUpToNext(option.isSelected()),
                mGeneralData.isRemapPageUpToNextEnabled()));

        options.add(UiOptionItem.from("Page Up/Down -> Like/Dislike",
                option -> mGeneralData.remapPageUpToLike(option.isSelected()),
                mGeneralData.isRemapPageUpToLikeEnabled()));

        options.add(UiOptionItem.from("Page Up/Down -> Speed Up/Down",
                option -> mGeneralData.remapPageUpToSpeed(option.isSelected()),
                mGeneralData.isRemapPageUpToSpeedEnabled()));

        options.add(UiOptionItem.from("Channel Up/Down -> Next/Previous",
                option -> mGeneralData.remapChannelUpToNext(option.isSelected()),
                mGeneralData.isRemapChannelUpToNextEnabled()));

        options.add(UiOptionItem.from("Channel Up/Down -> Like/Dislike",
                option -> mGeneralData.remapChannelUpToLike(option.isSelected()),
                mGeneralData.isRemapChannelUpToLikeEnabled()));

        options.add(UiOptionItem.from("Channel Up/Down -> Speed Up/Down",
                option -> mGeneralData.remapChannelUpToSpeed(option.isSelected()),
                mGeneralData.isRemapChannelUpToSpeedEnabled()));

        options.add(UiOptionItem.from("Channel Up/Down -> Search",
                option -> mGeneralData.remapChannelUpToSearch(option.isSelected()),
                mGeneralData.isRemapChannelUpToSearchEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.key_remapping), options);
    }

    private void appendScreenDimmingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mGeneralData.setScreenDimmingTimeoutMin(GeneralData.SCREEN_DIMMING_NEVER),
                mGeneralData.getScreenDimmingTimeoutMin() == GeneralData.SCREEN_DIMMING_NEVER));

        for (int i = 1; i <= 15; i++) {
            int timeoutMin = i;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.screen_dimming_timeout_min, i),
                    option -> mGeneralData.setScreenDimmingTimeoutMin(timeoutMin),
                    mGeneralData.getScreenDimmingTimeoutMin() == i));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming), options);
    }

    private void appendTimeFormatCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_24),
                option -> {
                    mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_24);
                    mRestartApp = true;
                },
                mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_24));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_12),
                option -> {
                    mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_12);
                    mRestartApp = true;
                },
                mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_12));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    }

    private void appendAppBackupCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        BackupAndRestoreManager backupManager = new BackupAndRestoreManager(getContext());

        if (getContext() instanceof MotherActivity) {
            ((MotherActivity) getContext()).addOnPermissions(backupManager);
        }

        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_backup), backupManager.getBackupPath()),
                option -> {
                    AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_backup), () -> {
                        mGeneralData.enableSection(MediaGroup.TYPE_SETTINGS, true); // prevent Settings lock
                        mGeneralData.enableSettingsSection(true); // prevent Settings lock
                        backupManager.checkPermAndBackup();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    });
                }));

        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_restore), backupManager.getBackupPath()),
                option -> {
                    AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_restore), () -> {
                        backupManager.checkPermAndRestore();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    });
                }));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.app_backup_restore), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.child_mode),
                getContext().getString(R.string.child_mode_desc),
                option -> {
                    if (option.isSelected()) {
                        AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.lost_setting_warning),
                                () -> showPasswordDialog(settingsPresenter, () -> enableChildMode(option.isSelected())),
                                settingsPresenter::closeDialog);
                    } else {
                        mGeneralData.setSettingsPassword(null);
                        enableChildMode(option.isSelected());
                        settingsPresenter.closeDialog();
                    }
                },
                mGeneralData.isChildModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.protect_settings_with_password),
                option -> {
                    if (option.isSelected()) {
                        showPasswordDialog(settingsPresenter, null);
                    } else {
                        mGeneralData.setSettingsPassword(null);
                    }
                },
                mGeneralData.getSettingsPassword() != null));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_global_clock),
                option -> {
                    mGeneralData.enableGlobalClock(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_home),
                option -> mGeneralData.hideShortsFromHome(option.isSelected()),
                mGeneralData.isHideShortsFromHomeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts),
                option -> mGeneralData.hideShortsFromSubscriptions(option.isSelected()),
                mGeneralData.isHideShortsFromSubscriptionsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_history),
                option -> mGeneralData.hideShortsFromHistory(option.isSelected()),
                mGeneralData.isHideShortsFromHistoryEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming),
                option -> mGeneralData.hideUpcoming(option.isSelected()),
                mGeneralData.isHideUpcomingEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_screensaver),
                option -> mGeneralData.disableScreensaver(option.isSelected()),
                mGeneralData.isScreensaverDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.return_to_launcher),
                option -> mGeneralData.enableReturnToLauncher(option.isSelected()),
                mGeneralData.isReturnToLauncherEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_settings_section),
                option -> {
                    mGeneralData.enableSettingsSection(!option.isSelected());
                    mRestartApp = true;
                },
                !mGeneralData.isSettingsSectionEnabled()));

        // Disable long press on buggy controllers.
        options.add(UiOptionItem.from(getContext().getString(R.string.disable_ok_long_press),
                option -> mGeneralData.disableOkButtonLongPress(option.isSelected()),
                mGeneralData.isOkButtonLongPressDisabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }

    private void appendInternetCensorship(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        appendProxyManager(settingsPresenter, options);

        //appendOpenVPNManager(settingsPresenter, options);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.internet_censorship), options);
    }

    private void appendProxyManager(AppDialogPresenter settingsPresenter, List<OptionItem> options) {
        ProxyManager proxyManager = new ProxyManager(getContext());

        if (proxyManager.isProxySupported()) {
            options.add(UiOptionItem.from(getContext().getString(R.string.enable_web_proxy),
                    option -> {
                        mGeneralData.enableProxy(option.isSelected());
                        new WebProxyDialog(getContext()).enable(option.isSelected());
                        if (option.isSelected()) {
                            settingsPresenter.closeDialog();
                        }
                    },
                    mGeneralData.isProxyEnabled()));
        }
    }

    private void enableChildMode(boolean enable) {
        mGeneralData.enableChildMode(enable);

        int topButtons = MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS;
        int playerButtons = PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE | PlayerTweaksData.PLAYER_BUTTON_NEXT | PlayerTweaksData.PLAYER_BUTTON_PREVIOUS |
                    PlayerTweaksData.PLAYER_BUTTON_DISLIKE | PlayerTweaksData.PLAYER_BUTTON_LIKE | PlayerTweaksData.PLAYER_BUTTON_SCREEN_OFF |
                    PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL | PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE | PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL |
                    PlayerTweaksData.PLAYER_BUTTON_PIP | PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED | PlayerTweaksData.PLAYER_BUTTON_SUBTITLES |
                    PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM | PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST;
        int menuItems = MainUIData.MENU_ITEM_SHOW_QUEUE | MainUIData.MENU_ITEM_ADD_TO_QUEUE | MainUIData.MENU_ITEM_SELECT_ACCOUNT |
                    MainUIData.MENU_ITEM_STREAM_REMINDER | MainUIData.MENU_ITEM_SAVE_PLAYLIST;

        PlayerTweaksData tweaksData = PlayerTweaksData.instance(getContext());

        // Remove all
        mMainUIData.disableTopButton(Integer.MAX_VALUE);
        tweaksData.disablePlayerButton(Integer.MAX_VALUE);
        mMainUIData.disableMenuItem(Integer.MAX_VALUE);
        BrowsePresenter.instance(getContext()).enableAllSections(false);

        if (enable) {
            // apply child tweaks
            mMainUIData.enableTopButton(topButtons);
            tweaksData.enablePlayerButton(playerButtons);
            mMainUIData.enableMenuItem(menuItems);
            mPlayerData.setRepeatMode(PlaybackUIController.REPEAT_MODE_LIST);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_HISTORY, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_USER_PLAYLISTS, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_SUBSCRIPTIONS, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_CHANNEL_UPLOADS, true);
        } else {
            // apply default tweaks
            mMainUIData.enableTopButton(MainUIData.TOP_BUTTON_DEFAULT);
            tweaksData.enablePlayerButton(PlayerTweaksData.PLAYER_BUTTON_DEFAULT);
            mMainUIData.enableMenuItem(MainUIData.MENU_ITEM_DEFAULT);
            BrowsePresenter.instance(getContext()).enableAllSections(true);
            tweaksData.disableSuggestions(false);
            mPlayerData.setRepeatMode(PlaybackUIController.REPEAT_MODE_ALL);
        }
    }

    private void showPasswordDialog(AppDialogPresenter settingsPresenter, Runnable onSuccess) {
        if (mGeneralData.getSettingsPassword() != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.show(
                getContext(),
                "", newValue -> {
                    mGeneralData.setSettingsPassword(newValue);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                },
                getContext().getString(R.string.protect_settings_with_password),
                true
        );
    }

    //private void appendOpenVPNManager(AppDialogPresenter settingsPresenter, List<OptionItem> options) {
    //    OpenVPNDialog openVPNDialog = new OpenVPNDialog(getContext());
    //
    //    if (getContext() instanceof MotherActivity) {
    //        ((MotherActivity) getContext()).addOnPermissions(openVPNDialog);
    //    }
    //
    //    if (openVPNDialog.isOpenVPNSupported()) {
    //        options.add(UiOptionItem.from(getContext().getString(R.string.enable_openvpn),
    //                option -> {
    //                    mGeneralData.enableVPN(option.isSelected());
    //                    openVPNDialog.enable(option.isSelected());
    //                    if (option.isSelected()) {
    //                        settingsPresenter.closeDialog();
    //                    }
    //                },
    //                mGeneralData.isVPNEnabled()));
    //    }
    //}
}
