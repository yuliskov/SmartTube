package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GeneralData implements ProfileChangeListener {
    public static final int SCREENSAVER_TIMEOUT_NEVER = 0;
    private static final String GENERAL_DATA = "general_data";
    public static final int EXIT_NONE = 0;
    public static final int EXIT_DOUBLE_BACK = 1;
    public static final int EXIT_SINGLE_BACK = 2;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME = 0;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK = 1;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_BACK = 2;
    public static final int HISTORY_AUTO = 0;
    public static final int HISTORY_ENABLED = 1;
    public static final int HISTORY_DISABLED = 2;
    public static final int ACTION_UNDEFINED = -1;
    public static final int ACTION_VOLUME_UP_DOWN = 0;
    public static final int ACTION_NEXT_PREVIOUS = 1;
    public static final int ACTION_LIKE_DISLIKE = 2;
    public static final int ACTION_SPEED_UP_DOWN = 3;
    public static final int ACTION_SPEED_DOWN_UP = 4;
    public static final int ACTION_SPEED_TOGGLE = 5;
    public static final int ACTION_FAST_FORWARD_REWIND = 6;
    public static final int ACTION_SEARCH = 7;
    private static GeneralData sInstance;
    private final AppPrefs mPrefs;
    private String mBackupZipName;
    private int mAppExitShortcut;
    private int mPlayerExitShortcut;
    private int mSearchExitShortcut;
    private boolean mIsReturnToLauncherEnabled;
    private int mBackgroundShortcut;
    private boolean mIsHideShortsFromSubscriptionsEnabled;
    private boolean mIsHideUpcomingEnabled;
    private int mScreensaverTimeoutMs;
    private int mScreensaverDimmingPercents;
    private boolean mIsProxyEnabled;
    private boolean mIsBridgeCheckEnabled;
    private boolean mIsOkButtonLongPressDisabled;
    private String mLastPlaylistId;
    private String mLastPlaylistTitle;
    private boolean mIsRemapNumbersToSpeedEnabled;
    private boolean mIsRemapPlayToOKEnabled;
    private int mDpadUpDownAction;
    private int mNextPreviousAction;
    private int mFastForwardRewindAction;
    private int mPageUpDownAction;
    private int mChannelUpDownAction;
    private boolean mIsHideShortsFromHomeEnabled;
    private boolean mIsHideShortsFromHistoryEnabled;
    private boolean mIsScreensaverDisabled;
    private boolean mIsVPNEnabled;
    private boolean mIsGlobalClockEnabled;
    private String mSettingsPassword;
    private String mMasterPassword;
    private boolean mIsChildModeEnabled;
    private boolean mIsHistoryEnabled;
    private int mHistoryState;
    private boolean mIsAltAppIconEnabled;
    private int mVersionCode;
    private boolean mIsSelectChannelSectionEnabled;
    private boolean mIsOldUpdateNotificationsEnabled;
    private boolean mIsRememberSubscriptionsPositionEnabled;
    private boolean mIsRememberPinnedPositionEnabled;
    private boolean mIsRemapDpadLeftToVolumeEnabled;
    private boolean mIsHideWatchedFromNotificationsEnabled;
    private List<String> mChangelog;
    private Map<String, Integer> mPlaylistOrder;
    private List<Video> mPendingStreams;
    private boolean mIsFullscreenModeEnabled;
    private Map<Integer, Video> mSelectedItems;
    private boolean mIsFirstUseTooltipEnabled;
    private boolean mIsDeviceSpecificBackupEnabled;
    private int mGDriveBackupFreqDays;
    private int mLocalDriveBackupFreqDays;
    private List<Video> mOldPinnedItems;
    private boolean mIsRemapSToSpeedToggleEnabled;
    private final Runnable mPersistStateInt = this::persistStateInt;

    private GeneralData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static GeneralData instance(Context context) {
        if (sInstance == null) {
            sInstance = new GeneralData(context);
        }

        return sInstance;
    }

    public String getBackupZipName() {
        return mBackupZipName;
    }

    public void setBackupZipName(String backupZipName) {
        mBackupZipName = backupZipName;
        persistState();
    }

    public int getAppExitShortcut() {
        return mAppExitShortcut;
    }

    public void setAppExitShortcut(int type) {
        mAppExitShortcut = type;
        persistState();
    }

    public int getPlayerExitShortcut() {
        return mPlayerExitShortcut;
    }

    public void setPlayerExitShortcut(int type) {
        mPlayerExitShortcut = type;
        persistState();
    }

    public int getSearchExitShortcut() {
        return mSearchExitShortcut;
    }

    public void setSearchExitShortcut(int type) {
        mSearchExitShortcut = type;
        persistState();
    }

    public boolean isReturnToLauncherEnabled() {
        return mIsReturnToLauncherEnabled;
    }

    public void setReturnToLauncherEnabled(boolean enable) {
        mIsReturnToLauncherEnabled = enable;
        persistState();
    }

    public int getBackgroundPlaybackShortcut() {
        return mBackgroundShortcut;
    }

    public void setBackgroundPlaybackShortcut(int type) {
        mBackgroundShortcut = type;
        persistState();
    }

    public List<Video> getOldPinnedItems() {
        return mOldPinnedItems;
    }

    public boolean isRememberSubscriptionsPositionEnabled() {
        return mIsRememberSubscriptionsPositionEnabled;
    }

    public void setRememberSubscriptionsPositionEnabled(boolean enable) {
        mIsRememberSubscriptionsPositionEnabled = enable;
        persistState();
    }

    public boolean isRememberPinnedPositionEnabled() {
        return mIsRememberPinnedPositionEnabled;
    }

    public void setRememberPinnedPositionEnabled(boolean enable) {
        mIsRememberPinnedPositionEnabled = enable;
        persistState();
    }

    public boolean isHideWatchedFromNotificationsEnabled() {
        return mIsHideWatchedFromNotificationsEnabled;
    }

    public void setHideWatchedFromNotificationsEnabled(boolean enable) {
        mIsHideWatchedFromNotificationsEnabled = enable;
        persistState();
    }

    public boolean isScreensaverDisabled() {
        return mIsScreensaverDisabled;
    }

    public void setScreensaverDisabled(boolean disable) {
        mIsScreensaverDisabled = disable;
        persistState();
    }

    public boolean isRemapNumbersToSpeedEnabled() {
        return mIsRemapNumbersToSpeedEnabled;
    }

    public void setRemapNumbersToSpeedEnabled(boolean enable) {
        mIsRemapNumbersToSpeedEnabled = enable;
        persistState();
    }

    public boolean isRemapDpadLeftToVolumeEnabled() {
        return mIsRemapDpadLeftToVolumeEnabled;
    }

    public void setRemapDpadLeftToVolumeEnabled(boolean enable) {
        resetDpadLeftRightSettings();
        mIsRemapDpadLeftToVolumeEnabled = enable;
        persistState();
    }

    public void resetDpadLeftRightSettings() {
        mIsRemapDpadLeftToVolumeEnabled = false;
        persistState();
    }

    public boolean isRemapPlayToOKEnabled() {
        return mIsRemapPlayToOKEnabled;
    }

    public void setRemapPlayToOKEnabled(boolean enable) {
        mIsRemapPlayToOKEnabled = enable;
        persistState();
    }

    public int getScreensaverTimeoutMs() {
        return mScreensaverTimeoutMs;
    }

    public void setScreensaverTimeoutMs(int timeoutMs) {
        mScreensaverTimeoutMs = timeoutMs;
        persistState();
    }

    public int getScreensaverDimmingPercents() {
        return mScreensaverDimmingPercents;
    }

    public void setScreensaverDimmingPercents(int percents) {
        mScreensaverDimmingPercents = percents;
        persistState();
    }

    public boolean is24HourLocaleEnabled() {
        return GlobalPreferences.sInstance.is24HourLocaleEnabled();
    }

    public void set24HourLocaleEnabled(boolean enable) {
        GlobalPreferences.sInstance.set24HourLocaleEnabled(enable);
    }

    public boolean isProxyEnabled() {
        return mIsProxyEnabled;
    }

    public void setProxyEnabled(boolean enable) {
        mIsProxyEnabled = enable;
        persistState();
    }

    public boolean isVPNEnabled() {
        return mIsVPNEnabled;
    }

    public void setVPNEnabled(boolean enable) {
        mIsVPNEnabled = enable;
        persistState();
    }

    public boolean isBridgeCheckEnabled() {
        return mIsBridgeCheckEnabled;
    }

    public void setBridgeCheckEnabled(boolean enable) {
        mIsBridgeCheckEnabled = enable;
        persistState();
    }

    public boolean isOkButtonLongPressDisabled() {
        return mIsOkButtonLongPressDisabled;
    }

    public void setOkButtonLongPressDisabled(boolean enable) {
        mIsOkButtonLongPressDisabled = enable;
        persistState();
    }

    public String getLastPlaylistId() {
        return mLastPlaylistId;
    }

    public void setLastPlaylistId(String playlistId) {
        mLastPlaylistId = playlistId;
        persistState();
    }

    public String getLastPlaylistTitle() {
        return mLastPlaylistTitle;
    }

    public void setLastPlaylistTitle(String playlistTitle) {
        mLastPlaylistTitle = playlistTitle;
        persistState();
    }

    public int getPlaylistOrder(String playlistId) {
        Integer order = mPlaylistOrder.get(playlistId);
        return order != null ? order : -1; // default order unpredictable (depends on site prefs)
    }

    public void setPlaylistOrder(String playlistId, int playlistOrder) {
        if (playlistOrder == -1) {
            mPlaylistOrder.remove(playlistId);
        } else {
            mPlaylistOrder.put(playlistId, playlistOrder);
        }
        persistState();
    }

    public List<Video> getPendingStreams() {
        return Collections.unmodifiableList(mPendingStreams);
    }

    public boolean containsPendingStream(Video video) {
        if (video == null || video.videoId == null) {
            return false;
        }

        return Helpers.containsIf(mPendingStreams, item -> video.videoId.equals(item.videoId));
    }

    public void addPendingStream(Video video) {
        if (video == null || video.videoId == null || containsPendingStream(video)) {
            return;
        }

        mPendingStreams.add(video);
        persistState();
    }

    public void removePendingStream(Video video) {
        if (video == null || video.videoId == null || !containsPendingStream(video)) {
            return;
        }

        Helpers.removeIf(mPendingStreams, item -> video.videoId.equals(item.videoId));
        persistState();
    }

    public boolean isGlobalClockEnabled() {
        return mIsGlobalClockEnabled;
    }

    public void setGlobalClockEnabled(boolean enable) {
        mIsGlobalClockEnabled = enable;
        persistState();
    }

    public String getSettingsPassword() {
        return mSettingsPassword;
    }

    public void setSettingsPassword(String password) {
        mSettingsPassword = password;

        persistState();
    }

    public String getMasterPassword() {
        return mMasterPassword;
    }

    public void setMasterPassword(String password) {
        mMasterPassword = password;

        persistState();
    }

    public boolean isChildModeEnabled() {
        return mIsChildModeEnabled;
    }

    public void setChildModeEnabled(boolean enable) {
        mIsChildModeEnabled = enable;

        persistState();
    }

    public boolean isHistoryEnabled() {
        return mHistoryState == HISTORY_ENABLED;
    }

    public void setHistoryEnabled(boolean enabled) {
        setHistoryState(enabled ? HISTORY_ENABLED : HISTORY_AUTO);
    }

    public int getHistoryState() {
        return mHistoryState;
    }

    public void setHistoryState(int historyState) {
        mHistoryState = historyState;

        persistState();
    }

    public boolean isAltAppIconEnabled() {
        return mIsAltAppIconEnabled;
    }

    public void setAltAppIconEnabled(boolean enable) {
        mIsAltAppIconEnabled = enable;

        persistState();
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public void setVersionCode(int code) {
        mVersionCode = code;

        persistState();
    }

    public boolean isSelectChannelSectionEnabled() {
        return mIsSelectChannelSectionEnabled;
    }

    public void setSelectChannelSectionEnabled(boolean enabled) {
        mIsSelectChannelSectionEnabled = enabled;

        persistState();
    }

    public boolean isOldUpdateNotificationsEnabled() {
        return mIsOldUpdateNotificationsEnabled;
    }

    public void setOldUpdateNotificationsEnabled(boolean enable) {
        mIsOldUpdateNotificationsEnabled = enable;
        persistState();
    }

    public boolean isFullscreenModeEnabled() {
        return mIsFullscreenModeEnabled;
    }

    public void setFullscreenModeEnabled(boolean enable) {
        mIsFullscreenModeEnabled = enable;
        persistState();
    }

    public Video getSelectedItem(int sectionId) {
        return mSelectedItems.get(sectionId);
    }

    public void setSelectedItem(int sectionId, Video item) {
        if (item == null) {
            return;
        }

        mSelectedItems.put(sectionId, item);

        persistState();
    }

    public void removeSelectedItem(int sectionId) {
        mSelectedItems.remove(sectionId);
    }

    public List<String> getChangelog() {
        return mChangelog;
    }

    public void setChangelog(List<String> changelog) {
        mChangelog = changelog;
        persistState();
    }

    public boolean isFirstUseTooltipEnabled() {
        return mIsFirstUseTooltipEnabled;
    }

    public void setFirstUseTooltipEnabled(boolean enable) {
        mIsFirstUseTooltipEnabled = enable;
        persistState();
    }

    public boolean isDeviceSpecificBackupEnabled() {
        return mIsDeviceSpecificBackupEnabled;
    }

    public void setDeviceSpecificBackupEnabled(boolean enable) {
        mIsDeviceSpecificBackupEnabled = enable;
        persistState();
    }

    public int getGDriveBackupFreqDays() {
        return mGDriveBackupFreqDays;
    }

    public void setGDriveBackupFreqDays(int freqDays) {
        mGDriveBackupFreqDays = freqDays;
        persistState();
    }

    public int getLocalDriveBackupFreqDays() {
        return mLocalDriveBackupFreqDays;
    }

    public void setLocalDriveBackupFreqDays(int freqDays) {
        mLocalDriveBackupFreqDays = freqDays;
        persistState();
    }

    public boolean isRemapSToSpeedToggleEnabled() {
        return mIsRemapSToSpeedToggleEnabled;
    }

    public void setRemapSToSpeedToggleEnabled(boolean enable) {
        mIsRemapSToSpeedToggleEnabled = enable;
        persistState();
    }

    public int getDpadUpDownAction() {
        return mDpadUpDownAction;
    }

    public void setDpadUpDownAction(int action) {
        mDpadUpDownAction = action;
        persistState();
    }

    public int getNextPreviousAction() {
        return mNextPreviousAction;
    }

    public void setNextPreviousAction(int action) {
        mNextPreviousAction = action;
        persistState();
    }

    public int getFastForwardRewindAction() {
        return mFastForwardRewindAction;
    }

    public void setFastForwardRewindAction(int action) {
        mFastForwardRewindAction = action;
        persistState();
    }

    public int getPageUpDownAction() {
        return mPageUpDownAction;
    }

    public void setPageUpDownAction(int action) {
        mPageUpDownAction = action;
        persistState();
    }

    public int getChannelUpDownAction() {
        return mChannelUpDownAction;
    }

    public void setChannelUpDownAction(int action) {
        mChannelUpDownAction = action;
        persistState();
    }

    /**
     * Fixed ConcurrentModificationException after onProfileChanged()<br/>
     * Happened inside cleanupPinnedItems()
     */
    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(GENERAL_DATA);

        String[] split = Helpers.splitData(data);

        mBackupZipName = Helpers.parseStr(split, 0);
        //mBootSectionId = Helpers.parseInt(split, 1, MediaGroup.TYPE_HOME);
        //mIsSettingsSectionEnabled = Helpers.parseBoolean(split, 2, true);
        mAppExitShortcut = Helpers.parseInt(split, 3, EXIT_DOUBLE_BACK);
        mIsReturnToLauncherEnabled = Helpers.parseBoolean(split, 4, false);
        mBackgroundShortcut = Helpers.parseInt(split, 5, BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
        mOldPinnedItems = Helpers.parseList(split, 6, Video::fromString);
        mIsHideShortsFromSubscriptionsEnabled = Helpers.parseBoolean(split, 7, false);
        mFastForwardRewindAction = Helpers.parseInt(split, 8, ACTION_UNDEFINED);
        //mScreenDimmingTimeoutMs = Helpers.parseInt(split, 9, 1);
        mIsProxyEnabled = Helpers.parseBoolean(split, 10, false);
        mIsBridgeCheckEnabled = Helpers.parseBoolean(split, 11, true);
        mIsOkButtonLongPressDisabled = Helpers.parseBoolean(split, 12, false);
        mLastPlaylistId = Helpers.parseStr(split, 13);
        //String selectedSections = Helpers.parseStr(split, 14);
        mIsHideUpcomingEnabled = Helpers.parseBoolean(split, 15, false);
        mPageUpDownAction = Helpers.parseInt(split, 16, ACTION_UNDEFINED);
        //mIsRemapPageUpToLikeEnabled = Helpers.parseBoolean(split, 17, false);
        mChannelUpDownAction = Helpers.parseInt(split, 18, ACTION_UNDEFINED);
        //mIsRemapChannelUpToLikeEnabled = Helpers.parseBoolean(split, 19, false);
        //mIsRemapPageUpToSpeedEnabled = Helpers.parseBoolean(split, 20, false);
        //mIsRemapChannelUpToSpeedEnabled = Helpers.parseBoolean(split, 21, false);
        //mIsRemapFastForwardToSpeedEnabled = Helpers.parseBoolean(split, 22, false);
        //mIsRemapChannelUpToSearchEnabled = Helpers.parseBoolean(split, 23, false);
        mIsHideShortsFromHomeEnabled = Helpers.parseBoolean(split, 24, false);
        mIsHideShortsFromHistoryEnabled = Helpers.parseBoolean(split, 25, false);
        mIsScreensaverDisabled = Helpers.parseBoolean(split, 26, true);
        mIsVPNEnabled = Helpers.parseBoolean(split, 27, false);
        mLastPlaylistTitle = Helpers.parseStr(split, 28);
        mPlaylistOrder = Helpers.parseMap(split, 29, Helpers::parseStr, Helpers::parseInt);
        //String pendingStreams = Helpers.parseStr(split, 30);
        mPendingStreams = Helpers.parseList(split, 30, Video::fromString);
        mIsGlobalClockEnabled = Helpers.parseBoolean(split, 31, true);
        //mTimeFormat = Helpers.parseInt(split, 32, -1);
        mSettingsPassword = Helpers.parseStr(split, 33);
        mIsChildModeEnabled = Helpers.parseBoolean(split, 34, false);
        mIsHistoryEnabled = Helpers.parseBoolean(split, 35, true);
        mScreensaverTimeoutMs = Helpers.parseInt(split, 36, 60 * 1_000);
        // ScreensaverMode was here
        mIsAltAppIconEnabled = Helpers.parseBoolean(split, 38, false);
        mVersionCode = Helpers.parseInt(split, 39, -1);
        mIsSelectChannelSectionEnabled = Helpers.parseBoolean(split, 40, true);
        mMasterPassword = Helpers.parseStr(split, 41);
        // StackOverflow on old devices?
        //mIsOldHomeLookEnabled = Helpers.parseBoolean(split, 42, Build.VERSION.SDK_INT <= 19);
        mIsOldUpdateNotificationsEnabled = Helpers.parseBoolean(split, 43, false);
        mScreensaverDimmingPercents = Helpers.parseInt(split, 44, 80);
        mNextPreviousAction = Helpers.parseInt(split, 45, ACTION_UNDEFINED);
        mIsRemapPlayToOKEnabled = Helpers.parseBoolean(split, 46, false);
        mHistoryState = Helpers.parseInt(split, 47, HISTORY_AUTO);
        mIsRememberSubscriptionsPositionEnabled = Helpers.parseBoolean(split, 48, false);
        // mSelectedSubscriptionsItem was here
        mIsRemapNumbersToSpeedEnabled = Helpers.parseBoolean(split, 50, false);
        mDpadUpDownAction = Helpers.parseInt(split, 51, ACTION_UNDEFINED);
        //mIsRemapChannelUpToVolumeEnabled = Helpers.parseBoolean(split, 52, false);
        //mIsRemapDpadUpToVolumeEnabled = Helpers.parseBoolean(split, 53, false);
        mIsRemapDpadLeftToVolumeEnabled = Helpers.parseBoolean(split, 54, false);
        //mIsRemapNextToFastForwardEnabled = Helpers.parseBoolean(split, 55, false);
        mIsHideWatchedFromNotificationsEnabled = Helpers.parseBoolean(split, 56, false);
        mChangelog = Helpers.parseStrList(split, 57);
        mPlayerExitShortcut = Helpers.parseInt(split, 58, EXIT_SINGLE_BACK);
        // StackOverflow on old devices?
        //mIsOldChannelLookEnabled = Helpers.parseBoolean(split, 59, Build.VERSION.SDK_INT <= 19);
        mIsFullscreenModeEnabled = Helpers.parseBoolean(split, 60, true);
        //mIsHideWatchedFromWatchLaterEnabled = Helpers.parseBoolean(split, 61, false);
        mIsRememberPinnedPositionEnabled = Helpers.parseBoolean(split, 62, false);
        mSelectedItems = Helpers.parseMap(split, 63, Helpers::parseInt, Video::fromString);
        mIsFirstUseTooltipEnabled = Helpers.parseBoolean(split, 64, true);
        mIsDeviceSpecificBackupEnabled = Helpers.parseBoolean(split, 65, false);
        //mIsAutoBackupEnabled = Helpers.parseBoolean(split, 66, false);
        //mIsRemapPageDownToSpeedEnabled = Helpers.parseBoolean(split, 67, false);
        mSearchExitShortcut = Helpers.parseInt(split, 68, EXIT_SINGLE_BACK);
        mGDriveBackupFreqDays = Helpers.parseInt(split, 69, -1);
        mLocalDriveBackupFreqDays = Helpers.parseInt(split, 70, -1);
        //mIsRemapFastForwardToSpeedToggleEnabled = Helpers.parseBoolean(split, 71, false);
        mIsRemapSToSpeedToggleEnabled = Helpers.parseBoolean(split, 72, true);
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        // Zero index is skipped. Selected sections were there.
        mPrefs.setProfileData(GENERAL_DATA, Helpers.mergeData(mBackupZipName, null, null, mAppExitShortcut, mIsReturnToLauncherEnabled,
                mBackgroundShortcut, mOldPinnedItems, mIsHideShortsFromSubscriptionsEnabled,
                mFastForwardRewindAction, null, mIsProxyEnabled, mIsBridgeCheckEnabled, mIsOkButtonLongPressDisabled, mLastPlaylistId,
                null, mIsHideUpcomingEnabled, mPageUpDownAction, null, mChannelUpDownAction, null, null, null, null, null,
                mIsHideShortsFromHomeEnabled, mIsHideShortsFromHistoryEnabled, mIsScreensaverDisabled, mIsVPNEnabled, mLastPlaylistTitle,
                mPlaylistOrder, mPendingStreams, mIsGlobalClockEnabled, null, mSettingsPassword, mIsChildModeEnabled, mIsHistoryEnabled,
                mScreensaverTimeoutMs, null, mIsAltAppIconEnabled, mVersionCode, mIsSelectChannelSectionEnabled, mMasterPassword,
                null, mIsOldUpdateNotificationsEnabled, mScreensaverDimmingPercents, mNextPreviousAction, mIsRemapPlayToOKEnabled,
                mHistoryState, mIsRememberSubscriptionsPositionEnabled, null, mIsRemapNumbersToSpeedEnabled, mDpadUpDownAction,
                null, null, mIsRemapDpadLeftToVolumeEnabled, null,
                mIsHideWatchedFromNotificationsEnabled, mChangelog, mPlayerExitShortcut, null, mIsFullscreenModeEnabled, null,
                mIsRememberPinnedPositionEnabled, mSelectedItems, mIsFirstUseTooltipEnabled, mIsDeviceSpecificBackupEnabled, null,
                null, mSearchExitShortcut, mGDriveBackupFreqDays, mLocalDriveBackupFreqDays, null,
                mIsRemapSToSpeedToggleEnabled));
    }

    @Override
    public void onProfileChanged() {
        Utils.removeCallbacks(mPersistStateInt);
        restoreState();
    }
}
