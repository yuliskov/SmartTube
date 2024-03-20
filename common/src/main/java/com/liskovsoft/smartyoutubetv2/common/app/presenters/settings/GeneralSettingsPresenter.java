package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.proxy.WebProxyDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    private final MainUIData mMainUIData;
    private boolean mRestartApp;

    private GeneralSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
        mMainUIData = MainUIData.instance(context);
    }

    public static GeneralSettingsPresenter instance(Context context) {
        return new GeneralSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendBootToSection(settingsPresenter);
        appendEnabledSections(settingsPresenter);
        appendContextMenuItemsCategory(settingsPresenter);
        appendVariousButtonsCategory(settingsPresenter);
        appendHideUnwantedContent(settingsPresenter);
        appendAppExitCategory(settingsPresenter);
        appendBackgroundPlaybackCategory(settingsPresenter);
        //appendBackgroundPlaybackActivationCategory(settingsPresenter);
        appendScreensaverDimmingCategory(settingsPresenter);
        appendScreensaverTimeoutCategory(settingsPresenter);
        appendTimeFormatCategory(settingsPresenter);
        appendKeyRemappingCategory(settingsPresenter);
        appendAppBackupCategory(settingsPresenter);
        appendInternetCensorship(settingsPresenter);
        appendHistoryCategory(settingsPresenter);
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
            }, mGeneralData.isSectionPinned(sectionId)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.side_panel_sections), options);
    }

    private void appendHideUnwantedContent(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_watch_later),
                option -> mGeneralData.hideWatchedFromWatchLater(option.isSelected()),
                mGeneralData.isHideWatchedFromWatchLaterEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_home),
                option -> mGeneralData.hideWatchedFromHome(option.isSelected()),
                mGeneralData.isHideWatchedFromHomeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_subscriptions),
                option -> mGeneralData.hideWatchedFromSubscriptions(option.isSelected()),
                mGeneralData.isHideWatchedFromSubscriptionsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_notifications),
                option -> mGeneralData.hideWatchedFromNotifications(option.isSelected()),
                mGeneralData.isHideWatchedFromNotificationsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_home),
                option -> mGeneralData.hideShortsFromHome(option.isSelected()),
                mGeneralData.isHideShortsFromHomeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_channel),
                option -> mGeneralData.hideShortsFromChannel(option.isSelected()),
                mGeneralData.isHideShortsFromChannelEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_history),
                option -> mGeneralData.hideShortsFromHistory(option.isSelected()),
                mGeneralData.isHideShortsFromHistoryEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_trending),
                option -> mGeneralData.hideShortsFromTrending(option.isSelected()),
                mGeneralData.isHideShortsFromTrendingEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_streams),
                option -> mGeneralData.hideStreamsFromSubscriptions(option.isSelected()),
                mGeneralData.isHideStreamsFromSubscriptionsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming),
                option -> mGeneralData.hideUpcomingFromSubscriptions(option.isSelected()),
                mGeneralData.isHideUpcomingFromSubscriptionsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming_home),
                option -> mGeneralData.hideUpcomingFromHome(option.isSelected()),
                mGeneralData.isHideUpcomingFromHomeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming_channel),
                option -> mGeneralData.hideUpcomingFromChannel(option.isSelected()),
                mGeneralData.isHideUpcomingFromChannelEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.hide_unwanted_content), options);
    }

    private void appendContextMenuItemsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId == null) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.enableMenuItem(menuItem);
                    showMenuItemOrderDialog(menuItem);
                } else {
                    mMainUIData.disableMenuItem(menuItem);
                }
            }, mMainUIData.isMenuItemEnabled(menuItem)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.context_menu), options);
    }

    private void showMenuItemOrderDialog(Long menuItem) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        Integer currentNameResId = menuNames.get(menuItem);

        if (currentNameResId == null) {
            return;
        }

        List<Long> menuItemsOrdered = mMainUIData.getMenuItemsOrdered();
        int size = menuItemsOrdered.size();
        int currentIndex = mMainUIData.getMenuItemIndex(menuItem);

        for (int i = 0; i < size; i++) {
            Integer nameResId = menuNames.get(menuItemsOrdered.get(i));

            if (nameResId == null) {
                continue;
            }

            final int index = i;
            options.add(UiOptionItem.from((i + 1) + " " + getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setMenuItemIndex(index, menuItem);

                    AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
                    settingsPresenter.clearBackstack();
                    appendContextMenuItemsCategory(settingsPresenter);
                    settingsPresenter.showDialog();
                }
            }, currentIndex == i));
        }

        String itemName = getContext().getString(currentNameResId);
        dialog.appendRadioCategory(getContext().getString(R.string.item_postion) + " " + itemName, options);

        dialog.showDialog();
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
            if (item != null && item.getTitle() != null) {
                options.add(
                        UiOptionItem.from(
                                item.getTitle(),
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

    private void appendKeyRemappingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("Play/Pause -> OK",
                option -> mGeneralData.remapPlayToOK(option.isSelected()),
                mGeneralData.isRemapPlayToOKEnabled()));

        options.add(UiOptionItem.from("DPAD RIGHT/LEFT -> Volume Up/Down",
                option -> mGeneralData.remapDpadLeftToVolume(option.isSelected()),
                mGeneralData.isRemapDpadLeftToVolumeEnabled()));

        options.add(UiOptionItem.from("DPAD UP/DOWN -> Volume Up/Down",
                option -> mGeneralData.remapDpadUpToVolume(option.isSelected()),
                mGeneralData.isRemapDpadUpToVolumeEnabled()));

        options.add(UiOptionItem.from("DPAD UP/DOWN -> Speed Up/Down",
                option -> mGeneralData.remapDpadUpDownToSpeed(option.isSelected()),
                mGeneralData.isRemapDpadUpToSpeedEnabled()));

        options.add(UiOptionItem.from("Numbers 3/1 -> Speed Up/Down",
                option -> mGeneralData.remapNumbersToSpeed(option.isSelected()),
                mGeneralData.isRemapNumbersToSpeedEnabled()));

        options.add(UiOptionItem.from("Next/Previous -> Fast Forward/Rewind",
                option -> mGeneralData.remapNextToFastForward(option.isSelected()),
                mGeneralData.isRemapNextToFastForwardEnabled()));

        options.add(UiOptionItem.from("Next/Previous -> Speed Up/Down",
                option -> mGeneralData.remapNextToSpeed(option.isSelected()),
                mGeneralData.isRemapNextToSpeedEnabled()));

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

        options.add(UiOptionItem.from("Channel Up/Down -> Volume Up/Down",
                option -> mGeneralData.remapChannelUpToVolume(option.isSelected()),
                mGeneralData.isRemapChannelUpToVolumeEnabled()));

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

    private void appendScreensaverDimmingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int activeMode = mGeneralData.getScreensaverDimmingPercents();

        for (int dimPercents : Helpers.range(10, 100, 10)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screensaver_dimming), options);
    }

    @SuppressLint("StringFormatMatches")
    private void appendScreensaverTimeoutCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int screensaverTimeoutMs = mGeneralData.getScreensaverTimeoutMs();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mGeneralData.setScreensaverTimeoutMs(GeneralData.SCREENSAVER_TIMEOUT_NEVER),
                screensaverTimeoutMs == GeneralData.SCREENSAVER_TIMEOUT_NEVER));

        for (int timeoutSec : new int[] {5, 15, 30}) {
            int timeoutMs = timeoutSec * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ui_hide_timeout_sec, timeoutSec),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        for (int i = 1; i <= 15; i++) {
            int timeoutMs = i * 60 * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.screen_dimming_timeout_min, i),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screensaver_timout), options);
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

        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_backup), backupManager.getBackupPath()),
                option -> {
                    AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_backup), () -> {
                        mGeneralData.enableSection(MediaGroup.TYPE_SETTINGS, true); // prevent Settings lock
                        backupManager.checkPermAndBackup();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    });
                }));

        String backupPathCheck = backupManager.getBackupPathCheck();
        options.add(UiOptionItem.from(
                String.format("%s:\n%s", getContext().getString(R.string.app_restore), backupPathCheck != null ? backupPathCheck : ""),
                option -> {
                    backupManager.getBackupNames(names -> showRestoreDialog(backupManager, names));
                }));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.app_backup_restore), options);
    }

    private void showRestoreDialog(BackupAndRestoreManager backupManager, List<String> backups) {
        if (backups != null && backups.size() > 1) {
            showRestoreSelectorDialog(backups, backupManager);
        } else {
            AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.app_restore), () -> {
                backupManager.checkPermAndRestore();
            });
        }
    }

    private void showRestoreSelectorDialog(List<String> backups, BackupAndRestoreManager backupManager) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (String name : backups) {
            options.add(UiOptionItem.from(name, optionItem -> {
                backupManager.checkPermAndRestore(name);
            }));
        }

        dialog.appendStringsCategory(getContext().getString(R.string.app_restore), options);
        dialog.showDialog();
    }

    private void appendHistoryCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.auto_history, GeneralData.HISTORY_AUTO},
                {R.string.enable_history, GeneralData.HISTORY_ENABLED},
                {R.string.disable_history, GeneralData.HISTORY_DISABLED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mGeneralData.setHistoryState(pair[1]);
                MediaServiceManager.instance().enableHistory(pair[1] == GeneralData.HISTORY_AUTO || pair[1] == GeneralData.HISTORY_ENABLED);
            }, mGeneralData.getHistoryState() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.header_history), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_only_mode),
                option -> mGeneralData.enablePlayerOnlyMode(option.isSelected()),
                mGeneralData.isPlayerOnlyModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.multi_profiles),
                option -> {
                    AppPrefs.instance(getContext()).enableMultiProfiles(option.isSelected());
                    BrowsePresenter.instance(getContext()).updateSections();
                },
                AppPrefs.instance(getContext()).isMultiProfilesEnabled()));

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

        options.add(UiOptionItem.from(getContext().getString(R.string.enable_master_password),
                option -> {
                    if (option.isSelected()) {
                        showMasterPasswordDialog(settingsPresenter, null);
                    } else {
                        mGeneralData.setMasterPassword(null);
                    }
                },
                mGeneralData.getMasterPassword() != null));

        options.add(UiOptionItem.from(getContext().getString(R.string.app_corner_clock),
                option -> {
                    mGeneralData.enableGlobalClock(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_clock),
                option -> mPlayerData.enableGlobalClock(option.isSelected()),
                mPlayerData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_ending_time),
                option -> mPlayerData.enableGlobalEndingTime(option.isSelected()),
                mPlayerData.isGlobalEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.old_home_look),
                option -> {
                    mGeneralData.enableOldHomeLook(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isOldHomeLookEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.old_channel_look),
                option -> {
                    mGeneralData.enableOldChannelLook(option.isSelected());
                    mMainUIData.enableChannelSearchBar(!option.isSelected());
                },
                mGeneralData.isOldChannelLookEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.fullscreen_mode),
                option -> {
                    mGeneralData.enableFullscreenMode(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isFullscreenModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_subscriptions),
                option -> mGeneralData.rememberSubscriptionsPosition(option.isSelected()),
                mGeneralData.isRememberSubscriptionsPositionEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_screensaver),
                option -> mGeneralData.disableScreensaver(option.isSelected()),
                mGeneralData.isScreensaverDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.select_channel_section),
                option -> mGeneralData.enableSelectChannelSection(option.isSelected()),
                mGeneralData.isSelectChannelSectionEnabled()));

        //// Disable long press on buggy controllers.
        //options.add(UiOptionItem.from(getContext().getString(R.string.disable_ok_long_press),
        //        option -> mGeneralData.disableOkButtonLongPress(option.isSelected()),
        //        mGeneralData.isOkButtonLongPressDisabled()));

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
                        // Proxy with authentication supported only by OkHttp
                        mPlayerTweaksData.setPlayerDataSource(
                                option.isSelected() ? PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET);
                        mGeneralData.enableProxy(option.isSelected());
                        new WebProxyDialog(getContext()).enable(option.isSelected());
                        if (option.isSelected()) {
                            settingsPresenter.closeDialog();
                        }

                        ExoMediaSourceFactory.unhold(); // reset data source
                        OkHttpManager.unhold();
                    },
                    mGeneralData.isProxyEnabled()));
        }
    }

    private void enableChildMode(boolean enable) {
        mGeneralData.enableChildMode(enable);

        int topButtons = MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS;
        int playerButtons = PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE | PlayerTweaksData.PLAYER_BUTTON_NEXT | PlayerTweaksData.PLAYER_BUTTON_PREVIOUS |
                    PlayerTweaksData.PLAYER_BUTTON_DISLIKE | PlayerTweaksData.PLAYER_BUTTON_LIKE | PlayerTweaksData.PLAYER_BUTTON_SCREEN_OFF_TIMEOUT |
                    PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL | PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE | PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL |
                    PlayerTweaksData.PLAYER_BUTTON_PIP | PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED | PlayerTweaksData.PLAYER_BUTTON_SUBTITLES |
                    PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM | PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST;
        long menuItems = MainUIData.MENU_ITEM_SHOW_QUEUE | MainUIData.MENU_ITEM_ADD_TO_QUEUE | MainUIData.MENU_ITEM_PLAY_NEXT |
                    MainUIData.MENU_ITEM_SELECT_ACCOUNT | MainUIData.MENU_ITEM_STREAM_REMINDER | MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST;

        PlayerTweaksData tweaksData = PlayerTweaksData.instance(getContext());
        SearchData searchData = SearchData.instance(getContext());

        // Remove all
        mMainUIData.disableTopButton(Integer.MAX_VALUE);
        tweaksData.disablePlayerButton(Integer.MAX_VALUE);
        mMainUIData.disableMenuItem(Integer.MAX_VALUE);
        BrowsePresenter.instance(getContext()).enableAllSections(false);
        searchData.disablePopularSearches(true);

        if (enable) {
            // apply child tweaks
            mMainUIData.enableTopButton(topButtons);
            tweaksData.enablePlayerButton(playerButtons);
            mMainUIData.enableMenuItem(menuItems);
            mPlayerData.setRepeatMode(PlayerUI.REPEAT_MODE_LIST);
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
            mPlayerData.setRepeatMode(PlayerUI.REPEAT_MODE_ALL);
            searchData.disablePopularSearches(false);
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
                    return true;
                },
                getContext().getString(R.string.protect_settings_with_password),
                true
        );
    }

    private void showMasterPasswordDialog(AppDialogPresenter settingsPresenter, Runnable onSuccess) {
        if (mGeneralData.getMasterPassword() != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.show(
                getContext(),
                "", newValue -> {
                    mGeneralData.setMasterPassword(newValue);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return true;
                },
                getContext().getString(R.string.enable_master_password),
                true
        );
    }

    private Map<Long, Integer> getMenuNames() {
        Map<Long, Integer> menuNames = new HashMap<>();
        menuNames.put(MainUIData.MENU_ITEM_EXIT_FROM_PIP, R.string.return_to_background_video);
        menuNames.put(MainUIData.MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, R.string.content_block_exclude_channel);
        menuNames.put(MainUIData.MENU_ITEM_MARK_AS_WATCHED, R.string.mark_as_watched);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_CHANNEL, R.string.open_channel);
        menuNames.put(MainUIData.MENU_ITEM_UPDATE_CHECK, R.string.check_for_updates);
        menuNames.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, R.string.clear_history);
        menuNames.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, R.string.pause_history);
        menuNames.put(MainUIData.MENU_ITEM_PLAYLIST_ORDER, R.string.playlist_order);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_NEXT, R.string.play_next);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_QUEUE, R.string.add_remove_from_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_SHOW_QUEUE, R.string.action_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_STREAM_REMINDER, R.string.set_stream_reminder);
        menuNames.put(MainUIData.MENU_ITEM_SUBSCRIBE, R.string.subscribe_unsubscribe_from_channel);
        menuNames.put(MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST, R.string.save_remove_playlist);
        menuNames.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, R.string.create_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_PLAYLIST, R.string.rename_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST, R.string.add_video_to_new_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST, R.string.dialog_add_to_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RECENT_PLAYLIST, R.string.add_remove_from_recent_playlist);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO, R.string.play_video);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO, R.string.play_video_incognito);
        menuNames.put(MainUIData.MENU_ITEM_NOT_INTERESTED, R.string.not_interested);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, R.string.remove_from_history);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, R.string.remove_from_subscriptions);
        menuNames.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, R.string.pin_unpin_from_sidebar);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_LINK, R.string.share_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_EMBED_LINK, R.string.share_embed_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_QR_LINK, R.string.share_qr_link);
        menuNames.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, R.string.dialog_account_list);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_UP, R.string.move_section_up);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_DOWN, R.string.move_section_down);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_SECTION, R.string.rename_section);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_DESCRIPTION, R.string.action_video_info);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_COMMENTS, R.string.open_comments);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_PLAYLIST, R.string.open_playlist);
        return menuNames;
    }
}
