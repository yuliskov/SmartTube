package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.CopyOnWriteHashList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class GeneralData implements ProfileChangeListener {
    public static final int SCREENSAVER_TIMEOUT_NEVER = 0;
    private static final String GENERAL_DATA = "general_data";
    public static final int EXIT_NONE = 0;
    public static final int EXIT_DOUBLE_BACK = 1;
    public static final int EXIT_SINGLE_BACK = 2;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME = 0;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK = 1;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_BACK = 2;
    public static final int TIME_FORMAT_24 = 0;
    public static final int TIME_FORMAT_12 = 1;
    public static final int HISTORY_AUTO = 0;
    public static final int HISTORY_ENABLED = 1;
    public static final int HISTORY_DISABLED = 2;
    @SuppressLint("StaticFieldLeak")
    private static GeneralData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsSettingsSectionEnabled;
    private int mBootSectionId;
    private int mAppExitShortcut;
    private int mPlayerExitShortcut;
    private boolean mIsPlayerOnlyModeEnabled;
    private int mBackgroundShortcut;
    private boolean mIsHideShortsFromSubscriptionsEnabled;
    private boolean mIsHideUpcomingEnabled;
    private boolean mIsRemapFastForwardToNextEnabled;
    private int mScreensaverTimeoutMs;
    private int mScreensaverDimmingPercents;
    private int mTimeFormat;
    private boolean mIsProxyEnabled;
    private boolean mIsBridgeCheckEnabled;
    private boolean mIsOkButtonLongPressDisabled;
    private String mLastPlaylistId;
    private String mLastPlaylistTitle;
    private boolean mIsRemapPageUpToNextEnabled;
    private boolean mIsRemapPageUpToLikeEnabled;
    private boolean mIsRemapChannelUpToNextEnabled;
    private boolean mIsRemapChannelUpToLikeEnabled;
    private boolean mIsRemapChannelUpToVolumeEnabled;
    private boolean mIsRemapPageUpToSpeedEnabled;
    private boolean mIsRemapChannelUpToSpeedEnabled;
    private boolean mIsRemapFastForwardToSpeedEnabled;
    private boolean mIsRemapNextToFastForwardEnabled;
    private boolean mIsRemapNextToSpeedEnabled;
    private boolean mIsRemapNumbersToSpeedEnabled;
    private boolean mIsRemapPlayToOKEnabled;
    private boolean mIsRemapChannelUpToSearchEnabled;
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
    private boolean mIsOldHomeLookEnabled;
    private boolean mIsOldChannelLookEnabled;
    private boolean mIsOldUpdateNotificationsEnabled;
    private boolean mRememberSubscriptionsPosition;
    private boolean mRememberPinnedPosition;
    private boolean mIsRemapDpadUpToSpeedEnabled;
    private boolean mIsRemapDpadUpToVolumeEnabled;
    private boolean mIsRemapDpadLeftToVolumeEnabled;
    private boolean mIsHideWatchedFromNotificationsEnabled;
    private boolean mIsHideWatchedFromWatchLaterEnabled;
    private List<String> mChangelog;
    private Map<String, Integer> mPlaylistOrder;
    private final Map<Integer, Integer> mDefaultSections = new LinkedHashMap<>();
    private List<Video> mPinnedItems;
    private List<Video> mPendingStreams;
    private boolean mIsFullscreenModeEnabled;
    private Map<Integer, Video> mSelectedItems;

    private GeneralData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        initSections();
        restoreState();
    }

    public static GeneralData instance(Context context) {
        if (sInstance == null) {
            sInstance = new GeneralData(context.getApplicationContext());
        }

        return sInstance;
    }

    public Collection<Video> getPinnedItems() {
        return Collections.unmodifiableList(mPinnedItems);
    }

    public void addPinnedItem(Video item) {
        mPinnedItems.add(item);
        persistState();
    }

    public void removePinnedItem(Video item) {
        mPinnedItems.remove(item);
        persistState();
    }

    public Map<Integer, Integer> getDefaultSections() {
        return mDefaultSections;
    }

    public void enableSection(int sectionId, boolean enabled) {
        if (enabled) {
            if (sectionId == MediaGroup.TYPE_SETTINGS) {
                mIsSettingsSectionEnabled = true; // prevent Settings lock
            }

            Video item = new Video();
            item.sectionId = sectionId;

            if (mPinnedItems.contains(item)) { // don't reorder if item already exists
                return;
            }

            int index = getDefaultSectionIndex(sectionId);

            if (index == -1 || index > mPinnedItems.size()) {
                mPinnedItems.add(item);
            } else {
                mPinnedItems.add(index, item);
            }
        } else {
            Helpers.removeIf(mPinnedItems, value -> value.sectionId == sectionId);
        }

        persistState();
    }

    private int getDefaultSectionIndex(int sectionId) {
        int index = -1;

        Collection<Integer> values = mDefaultSections.values();

        for (int item : values) {
            index++;
            if (item == sectionId) {
                break;
            }
        }

        return index;
    }

    /**
     * Contains sections and pinned items!
     */
    public boolean isSectionPinned(int sectionId) {
        Video section = Helpers.findFirst(mPinnedItems, item -> getSectionId(item) == sectionId);
        return section != null; // by default enable all pinned items
    }

    public int getSectionIndex(int sectionId) {
        // 1) Distinguish section from pinned item
        // 2) Add pinned items after the sections

        int index = findPinnedItemIndex(sectionId);

        return index;
    }

    public void renameSection(int sectionId, String newTitle) {
        int index = findPinnedItemIndex(sectionId);
        Video video = mPinnedItems.get(index);
        video.title = newTitle;
        persistState();
    }

    public void moveSectionUp(int sectionId) {
        shiftSection(sectionId, -1);
    }

    public void moveSectionDown(int sectionId) {
        shiftSection(sectionId, 1);
    }

    public boolean canMoveSectionUp(int sectionId) {
        return canShiftSection(sectionId, -1);
    }

    public boolean canMoveSectionDown(int sectionId) {
        return canShiftSection(sectionId, 1);
    }

    private boolean canShiftSection(int sectionId, int shift) {
        int index = findPinnedItemIndex(sectionId);

        if (index != -1) {
            return  index + shift >= 0 && index + shift < mPinnedItems.size();
        }

        return false;
    }

    private void shiftSection(int sectionId, int shift) {
        if (!canShiftSection(sectionId, shift)) {
            return;
        }

        int index = findPinnedItemIndex(sectionId);

        if (index != -1) {
            Video current = mPinnedItems.get(index);
            mPinnedItems.remove(current);

            mPinnedItems.add(index + shift, current);
            persistState();
        }
    }

    private int findPinnedItemIndex(int sectionId) {
        int index = -1;

        for (Video item : mPinnedItems) {
            // Distinguish pinned items by hashCode or extra field (default section)!
            if (getSectionId(item) == sectionId) {
                index = mPinnedItems.indexOf(item);
                break;
            }
        }

        return index;
    }

    public void setBootSectionId(int sectionId) {
        mBootSectionId = sectionId;

        persistState();
    }

    public int getBootSectionId() {
        return mBootSectionId;
    }

    public void enableSettingsSection(boolean enabled) {
        mIsSettingsSectionEnabled = enabled;

        persistState();
    }

    public boolean isSettingsSectionEnabled() {
        return mIsSettingsSectionEnabled;
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

    public void enablePlayerOnlyMode(boolean enable) {
        mIsPlayerOnlyModeEnabled = enable;
        persistState();
    }

    public boolean isPlayerOnlyModeEnabled() {
        return mIsPlayerOnlyModeEnabled;
    }

    public int getBackgroundPlaybackShortcut() {
        return mBackgroundShortcut;
    }

    public void setBackgroundPlaybackShortcut(int type) {
        mBackgroundShortcut = type;
        persistState();
    }

    public void hideShortsFromSubscriptions(boolean enable) {
        GlobalPreferences.sInstance.hideShortsFromSubscriptions(enable);
    }

    public boolean isHideShortsFromSubscriptionsEnabled() {
        return GlobalPreferences.sInstance.isHideShortsFromSubscriptionsEnabled();
    }

    public void hideShortsFromChannel(boolean enable) {
        GlobalPreferences.sInstance.hideShortsFromChannel(enable);
    }

    public boolean isHideShortsFromChannelEnabled() {
        return GlobalPreferences.sInstance.isHideShortsFromChannelEnabled();
    }

    public void hideStreamsFromSubscriptions(boolean enable) {
        GlobalPreferences.sInstance.hideStreamsFromSubscriptions(enable);
    }

    public void rememberSubscriptionsPosition(boolean remember) {
        mRememberSubscriptionsPosition = remember;
        persistState();
    }

    public boolean isRememberSubscriptionsPositionEnabled() {
        return mRememberSubscriptionsPosition;
    }

    public void rememberPinnedPosition(boolean remember) {
        mRememberPinnedPosition = remember;
        persistState();
    }

    public boolean isRememberPinnedPositionEnabled() {
        return mRememberPinnedPosition;
    }

    public void hideWatchedFromHome(boolean enable) {
        GlobalPreferences.sInstance.hideWatchedFromHome(enable);
    }

    public boolean isHideWatchedFromHomeEnabled() {
        return GlobalPreferences.sInstance.isHideWatchedFromHomeEnabled();
    }

    public void hideWatchedFromSubscriptions(boolean enable) {
        GlobalPreferences.sInstance.hideWatchedFromSubscriptions(enable);
    }

    public boolean isHideWatchedFromSubscriptionsEnabled() {
        return GlobalPreferences.sInstance.isHideWatchedFromSubscriptionsEnabled();
    }

    public void hideWatchedFromNotifications(boolean enable) {
        mIsHideWatchedFromNotificationsEnabled = enable;
        persistState();
    }

    public boolean isHideWatchedFromNotificationsEnabled() {
        return mIsHideWatchedFromNotificationsEnabled;
    }

    public void hideWatchedFromWatchLater(boolean enable) {
        mIsHideWatchedFromWatchLaterEnabled = enable;
        persistState();
    }

    public boolean isHideWatchedFromWatchLaterEnabled() {
        return mIsHideWatchedFromWatchLaterEnabled;
    }

    public boolean isHideStreamsFromSubscriptionsEnabled() {
        return GlobalPreferences.sInstance.isHideStreamsFromSubscriptionsEnabled();
    }

    public void hideShortsFromHome(boolean enable) {
        GlobalPreferences.sInstance.hideShortsFromHome(enable);
    }

    public boolean isHideShortsFromHomeEnabled() {
        return GlobalPreferences.sInstance.isHideShortsFromHomeEnabled();
    }

    public void hideShortsFromHistory(boolean enable) {
        GlobalPreferences.sInstance.hideShortsFromHistory(enable);
    }

    public boolean isHideShortsFromHistoryEnabled() {
        return GlobalPreferences.sInstance.isHideShortsFromHistoryEnabled();
    }

    public void hideShortsFromTrending(boolean enable) {
        GlobalPreferences.sInstance.hideShortsFromTrending(enable);
    }

    public boolean isHideShortsFromTrendingEnabled() {
        return GlobalPreferences.sInstance.isHideShortsFromTrendingEnabled();
    }

    public void hideUpcomingFromSubscriptions(boolean enable) {
        GlobalPreferences.sInstance.hideUpcomingFromSubscriptions(enable);
    }

    public boolean isHideUpcomingFromSubscriptionsEnabled() {
        return GlobalPreferences.sInstance.isHideUpcomingFromSubscriptionsEnabled();
    }

    public void hideUpcomingFromChannel(boolean enable) {
        GlobalPreferences.sInstance.hideUpcomingFromChannel(enable);
    }

    public boolean isHideUpcomingFromChannelEnabled() {
        return GlobalPreferences.sInstance.isHideUpcomingFromChannelEnabled();
    }

    public void hideUpcomingFromHome(boolean enable) {
        GlobalPreferences.sInstance.hideUpcomingFromHome(enable);
    }

    public boolean isHideUpcomingFromHomeEnabled() {
        return GlobalPreferences.sInstance.isHideUpcomingFromHomeEnabled();
    }

    public void disableScreensaver(boolean enable) {
        mIsScreensaverDisabled = enable;
        persistState();
    }

    public boolean isScreensaverDisabled() {
        return mIsScreensaverDisabled;
    }

    public void remapFastForwardToNext(boolean enable) {
        resetFastForwardSettings();
        mIsRemapFastForwardToNextEnabled = enable;
        persistState();
    }

    public boolean isRemapFastForwardToNextEnabled() {
        return mIsRemapFastForwardToNextEnabled;
    }

    public void remapFastForwardToSpeed(boolean enable) {
        resetFastForwardSettings();
        mIsRemapFastForwardToSpeedEnabled = enable;
        persistState();
    }

    public boolean isRemapFastForwardToSpeedEnabled() {
        return mIsRemapFastForwardToSpeedEnabled;
    }

    private void resetFastForwardSettings() {
        mIsRemapFastForwardToSpeedEnabled = false;
        mIsRemapFastForwardToNextEnabled = false;
    }

    public void remapNextToFastForward(boolean enable) {
        resetNextSettings();
        mIsRemapNextToFastForwardEnabled = enable;
        persistState();
    }

    public boolean isRemapNextToFastForwardEnabled() {
        return mIsRemapNextToFastForwardEnabled;
    }

    public void remapNextToSpeed(boolean enable) {
        resetNextSettings();
        mIsRemapNextToSpeedEnabled = enable;
        persistState();
    }

    public boolean isRemapNextToSpeedEnabled() {
        return mIsRemapNextToSpeedEnabled;
    }

    private void resetNextSettings() {
        mIsRemapNextToFastForwardEnabled = false;
        mIsRemapNextToSpeedEnabled = false;
    }

    public void remapNumbersToSpeed(boolean enable) {
        mIsRemapNumbersToSpeedEnabled = enable;
        persistState();
    }

    public boolean isRemapNumbersToSpeedEnabled() {
        return mIsRemapNumbersToSpeedEnabled;
    }

    public void remapDpadUpDownToSpeed(boolean enable) {
        resetDpadUpSettings();
        mIsRemapDpadUpToSpeedEnabled = enable;
        persistState();
    }

    public boolean isRemapDpadUpToSpeedEnabled() {
        return mIsRemapDpadUpToSpeedEnabled;
    }

    public void remapDpadUpToVolume(boolean enable) {
        resetDpadUpSettings();
        mIsRemapDpadUpToVolumeEnabled = enable;
        persistState();
    }

    public boolean isRemapDpadUpToVolumeEnabled() {
        return mIsRemapDpadUpToVolumeEnabled;
    }

    private void resetDpadUpSettings() {
        mIsRemapDpadUpToSpeedEnabled = false;
        mIsRemapDpadUpToVolumeEnabled = false;
    }

    public void remapDpadLeftToVolume(boolean enable) {
        mIsRemapDpadLeftToVolumeEnabled = enable;
        persistState();
    }

    public boolean isRemapDpadLeftToVolumeEnabled() {
        return mIsRemapDpadLeftToVolumeEnabled;
    }

    public void remapPlayToOK(boolean enable) {
        mIsRemapPlayToOKEnabled = enable;
        persistState();
    }

    public boolean isRemapPlayToOKEnabled() {
        return mIsRemapPlayToOKEnabled;
    }

    public void remapPageUpToNext(boolean enable) {
        resetPageUpSettings();
        mIsRemapPageUpToNextEnabled = enable;
        persistState();
    }

    public boolean isRemapPageUpToNextEnabled() {
        return mIsRemapPageUpToNextEnabled;
    }

    public void remapPageUpToLike(boolean enable) {
        resetPageUpSettings();
        mIsRemapPageUpToLikeEnabled = enable;
        persistState();
    }

    public boolean isRemapPageUpToLikeEnabled() {
        return mIsRemapPageUpToLikeEnabled;
    }

    public void remapPageUpToSpeed(boolean enable) {
        resetPageUpSettings();
        mIsRemapPageUpToSpeedEnabled = enable;
        persistState();
    }

    private void resetPageUpSettings() {
        mIsRemapPageUpToSpeedEnabled = false;
        mIsRemapPageUpToLikeEnabled = false;
        mIsRemapPageUpToNextEnabled = false;
    }

    public boolean isRemapPageUpToSpeedEnabled() {
        return mIsRemapPageUpToSpeedEnabled;
    }

    public void remapChannelUpToNext(boolean enable) {
        resetChannelUpSettings();
        mIsRemapChannelUpToNextEnabled = enable;
        persistState();
    }

    public boolean isRemapChannelUpToNextEnabled() {
        return mIsRemapChannelUpToNextEnabled;
    }

    public void remapChannelUpToVolume(boolean enable) {
        resetChannelUpSettings();
        mIsRemapChannelUpToVolumeEnabled = enable;
        persistState();
    }

    public boolean isRemapChannelUpToVolumeEnabled() {
        return mIsRemapChannelUpToVolumeEnabled;
    }

    public void remapChannelUpToLike(boolean enable) {
        resetChannelUpSettings();
        mIsRemapChannelUpToLikeEnabled = enable;
        persistState();
    }

    public boolean isRemapChannelUpToLikeEnabled() {
        return mIsRemapChannelUpToLikeEnabled;
    }

    public void remapChannelUpToSpeed(boolean enable) {
        resetChannelUpSettings();
        mIsRemapChannelUpToSpeedEnabled = enable;
        persistState();
    }

    public boolean isRemapChannelUpToSpeedEnabled() {
        return mIsRemapChannelUpToSpeedEnabled;
    }

    public void remapChannelUpToSearch(boolean enable) {
        resetChannelUpSettings();
        mIsRemapChannelUpToSearchEnabled = enable;
        persistState();
    }

    private void resetChannelUpSettings() {
        mIsRemapChannelUpToVolumeEnabled = false;
        mIsRemapChannelUpToNextEnabled = false;
        mIsRemapChannelUpToSearchEnabled = false;
        mIsRemapChannelUpToLikeEnabled = false;
        mIsRemapChannelUpToSpeedEnabled = false;
    }

    public boolean isRemapChannelUpToSearchEnabled() {
        return mIsRemapChannelUpToSearchEnabled;
    }

    public void setScreensaverTimeoutMs(int timeoutMs) {
        mScreensaverTimeoutMs = timeoutMs;
        persistState();
    }

    public int getScreensaverTimeoutMs() {
        return mScreensaverTimeoutMs;
    }

    public void setScreensaverDimmingPercents(int percents) {
        mScreensaverDimmingPercents = percents;
        persistState();
    }

    public int getScreensaverDimmingPercents() {
        return mScreensaverDimmingPercents;
    }

    public void setTimeFormat(int format) {
        mTimeFormat = format;
        persistState();
    }

    public int getTimeFormat() {
        return mTimeFormat != -1 ? mTimeFormat : LocaleUtility.is24HourLocale(mContext) ? TIME_FORMAT_24 : TIME_FORMAT_12;
    }

    public void enableProxy(boolean enable) {
        mIsProxyEnabled = enable;
        persistState();
    }

    public boolean isProxyEnabled() {
        return mIsProxyEnabled;
    }

    public void enableVPN(boolean enable) {
        mIsVPNEnabled = enable;
        persistState();
    }

    public boolean isVPNEnabled() {
        return mIsVPNEnabled;
    }

    public void enableBridgeCheck(boolean enable) {
        mIsBridgeCheckEnabled = enable;
        persistState();
    }

    public boolean isBridgeCheckEnabled() {
        return mIsBridgeCheckEnabled;
    }

    public void disableOkButtonLongPress(boolean enable) {
        mIsOkButtonLongPressDisabled = enable;
        persistState();
    }
    
    public boolean isOkButtonLongPressDisabled() {
        return mIsOkButtonLongPressDisabled;
    }

    public void setLastPlaylistId(String playlistId) {
        mLastPlaylistId = playlistId;
        persistState();
    }

    public String getLastPlaylistId() {
        return mLastPlaylistId;
    }

    public void setLastPlaylistTitle(String playlistTitle) {
        mLastPlaylistTitle = playlistTitle;
        persistState();
    }

    public String getLastPlaylistTitle() {
        return mLastPlaylistTitle;
    }

    public void setPlaylistOrder(String playlistId, int playlistOrder) {
        if (playlistOrder == -1) {
            mPlaylistOrder.remove(playlistId);
        } else {
            mPlaylistOrder.put(playlistId, playlistOrder);
        }
        persistState();
    }

    public int getPlaylistOrder(String playlistId) {
        Integer order = mPlaylistOrder.get(playlistId);
        return order != null ? order : -1; // default order unpredictable (depends on site prefs)
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

    public boolean containsPendingStream(Video video) {
        if (video == null || video.videoId == null) {
            return false;
        }

        return Helpers.containsIf(mPendingStreams, item -> video.videoId.equals(item.videoId));
    }

    public List<Video> getPendingStreams() {
        return Collections.unmodifiableList(mPendingStreams);
    }

    public boolean isGlobalClockEnabled() {
        return mIsGlobalClockEnabled;
    }

    public void enableGlobalClock(boolean enable) {
        mIsGlobalClockEnabled = enable;
        persistState();
    }

    public void setSettingsPassword(String password) {
        mSettingsPassword = password;

        persistState();
    }

    public String getSettingsPassword() {
        return mSettingsPassword;
    }

    public void setMasterPassword(String password) {
        mMasterPassword = password;

        persistState();
    }

    public String getMasterPassword() {
        return mMasterPassword;
    }

    public void enableChildMode(boolean enable) {
        mIsChildModeEnabled = enable;

        persistState();
    }

    public boolean isChildModeEnabled() {
        return mIsChildModeEnabled;
    }

    public boolean isHistoryEnabled() {
        return mHistoryState == HISTORY_ENABLED;
    }

    public void enableHistory(boolean enabled) {
        setHistoryState(enabled ? HISTORY_ENABLED : HISTORY_AUTO);
    }

    public void setHistoryState(int historyState) {
        mHistoryState = historyState;

        persistState();
    }

    public int getHistoryState() {
        return mHistoryState;
    }

    public void enableAltAppIcon(boolean enable) {
        mIsAltAppIconEnabled = enable;

        persistState();
    }

    public boolean isAltAppIconEnabled() {
        return mIsAltAppIconEnabled;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public void setVersionCode(int code) {
        mVersionCode = code;

        persistState();
    }

    public void enableSelectChannelSection(boolean enabled) {
        mIsSelectChannelSectionEnabled = enabled;

        persistState();
    }

    public boolean isSelectChannelSectionEnabled() {
        return mIsSelectChannelSectionEnabled;
    }

    public void enableOldHomeLook(boolean enable) {
        mIsOldHomeLookEnabled = enable;
        persistState();
    }

    public boolean isOldHomeLookEnabled() {
        return mIsOldHomeLookEnabled;
    }

    public void enableOldChannelLook(boolean enable) {
        mIsOldChannelLookEnabled = enable;
        persistState();
    }

    public boolean isOldChannelLookEnabled() {
        return mIsOldChannelLookEnabled;
    }

    public void enableOldUpdateNotifications(boolean enable) {
        mIsOldUpdateNotificationsEnabled = enable;
        persistState();
    }

    public boolean isOldUpdateNotificationsEnabled() {
        return mIsOldUpdateNotificationsEnabled;
    }

    public void enableFullscreenMode(boolean enable) {
        mIsFullscreenModeEnabled = enable;
        persistState();
    }

    public boolean isFullscreenModeEnabled() {
        return mIsFullscreenModeEnabled;
    }

    public void setSelectedItem(int sectionId, Video item) {
        if (item == null) {
            return;
        }

        mSelectedItems.put(sectionId, item);

        persistState();
    }

    public Video getSelectedItem(int sectionId) {
        return mSelectedItems.get(sectionId);
    }

    public void removeSelectedItem(int sectionId) {
        mSelectedItems.remove(sectionId);
    }

    public void setChangelog(List<String> changelog) {
        mChangelog = changelog;
        persistState();
    }

    public List<String> getChangelog() {
        return mChangelog;
    }

    private void initSections() {
        mDefaultSections.put(R.string.header_notifications, MediaGroup.TYPE_NOTIFICATIONS);
        mDefaultSections.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mDefaultSections.put(R.string.header_shorts, MediaGroup.TYPE_SHORTS);
        mDefaultSections.put(R.string.header_trending, MediaGroup.TYPE_TRENDING);
        mDefaultSections.put(R.string.header_kids_home, MediaGroup.TYPE_KIDS_HOME);
        mDefaultSections.put(R.string.header_sports, MediaGroup.TYPE_SPORTS);
        mDefaultSections.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        mDefaultSections.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        mDefaultSections.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mDefaultSections.put(R.string.header_channels, MediaGroup.TYPE_CHANNEL_UPLOADS);
        mDefaultSections.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mDefaultSections.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mDefaultSections.put(R.string.header_playlists, MediaGroup.TYPE_USER_PLAYLISTS);
        mDefaultSections.put(R.string.header_settings, MediaGroup.TYPE_SETTINGS);
    }

    private void cleanupPinnedItems() {
        Helpers.removeDuplicates(mPinnedItems);

        Helpers.removeIf(mPinnedItems, value -> {
            if (value == null) {
                return true;
            }

            value.videoId = null;
            return !value.hasPlaylist() && value.channelId == null && value.sectionId == -1 && !value.hasReloadPageKey();
        });
    }

    private void restoreState() {
        String data = mPrefs.getProfileData(GENERAL_DATA);

        String[] split = Helpers.splitData(data);

        // Zero index is skipped. Selected sections were there.
        mBootSectionId = Helpers.parseInt(split, 1, MediaGroup.TYPE_HOME);
        mIsSettingsSectionEnabled = Helpers.parseBoolean(split, 2, true);
        mAppExitShortcut = Helpers.parseInt(split, 3, EXIT_DOUBLE_BACK);
        mIsPlayerOnlyModeEnabled = Helpers.parseBoolean(split, 4, false);
        mBackgroundShortcut = Helpers.parseInt(split, 5, BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
        //String pinnedItems = Helpers.parseStr(split, 6);
        mPinnedItems = Helpers.parseList(split, 6, Video::fromString);
        mIsHideShortsFromSubscriptionsEnabled = Helpers.parseBoolean(split, 7, false);
        mIsRemapFastForwardToNextEnabled = Helpers.parseBoolean(split, 8, false);
        //mScreenDimmingTimeoutMs = Helpers.parseInt(split, 9, 1);
        mIsProxyEnabled = Helpers.parseBoolean(split, 10, false);
        mIsBridgeCheckEnabled = Helpers.parseBoolean(split, 11, true);
        mIsOkButtonLongPressDisabled = Helpers.parseBoolean(split, 12, false);
        mLastPlaylistId = Helpers.parseStr(split, 13);
        //String selectedSections = Helpers.parseStr(split, 14);
        mIsHideUpcomingEnabled = Helpers.parseBoolean(split, 15, false);
        mIsRemapPageUpToNextEnabled = Helpers.parseBoolean(split, 16, false);
        mIsRemapPageUpToLikeEnabled = Helpers.parseBoolean(split, 17, false);
        mIsRemapChannelUpToNextEnabled = Helpers.parseBoolean(split, 18, false);
        mIsRemapChannelUpToLikeEnabled = Helpers.parseBoolean(split, 19, false);
        mIsRemapPageUpToSpeedEnabled = Helpers.parseBoolean(split, 20, false);
        mIsRemapChannelUpToSpeedEnabled = Helpers.parseBoolean(split, 21, false);
        mIsRemapFastForwardToSpeedEnabled = Helpers.parseBoolean(split, 22, false);
        mIsRemapChannelUpToSearchEnabled = Helpers.parseBoolean(split, 23, false);
        mIsHideShortsFromHomeEnabled = Helpers.parseBoolean(split, 24, false);
        mIsHideShortsFromHistoryEnabled = Helpers.parseBoolean(split, 25, false);
        mIsScreensaverDisabled = Helpers.parseBoolean(split, 26, false);
        mIsVPNEnabled = Helpers.parseBoolean(split, 27, false);
        mLastPlaylistTitle = Helpers.parseStr(split, 28);
        mPlaylistOrder = Helpers.parseMap(split, 29, Helpers::parseStr, Helpers::parseInt);
        //String pendingStreams = Helpers.parseStr(split, 30);
        mPendingStreams = Helpers.parseList(split, 30, Video::fromString);
        mIsGlobalClockEnabled = Helpers.parseBoolean(split, 31, true);
        mTimeFormat = Helpers.parseInt(split, 32, -1);
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
        mIsOldHomeLookEnabled = Helpers.parseBoolean(split, 42, Build.VERSION.SDK_INT <= 19);
        mIsOldUpdateNotificationsEnabled = Helpers.parseBoolean(split, 43, false);
        mScreensaverDimmingPercents = Helpers.parseInt(split, 44, 80);
        mIsRemapNextToSpeedEnabled = Helpers.parseBoolean(split, 45, false);
        mIsRemapPlayToOKEnabled = Helpers.parseBoolean(split, 46, false);
        mHistoryState = Helpers.parseInt(split, 47, HISTORY_ENABLED);
        mRememberSubscriptionsPosition = Helpers.parseBoolean(split, 48, false);
        // mSelectedSubscriptionsItem was here
        mIsRemapNumbersToSpeedEnabled = Helpers.parseBoolean(split, 50, false);
        mIsRemapDpadUpToSpeedEnabled = Helpers.parseBoolean(split, 51, false);
        mIsRemapChannelUpToVolumeEnabled = Helpers.parseBoolean(split, 52, false);
        mIsRemapDpadUpToVolumeEnabled = Helpers.parseBoolean(split, 53, false);
        mIsRemapDpadLeftToVolumeEnabled = Helpers.parseBoolean(split, 54, false);
        mIsRemapNextToFastForwardEnabled = Helpers.parseBoolean(split, 55, false);
        mIsHideWatchedFromNotificationsEnabled = Helpers.parseBoolean(split, 56, false);
        mChangelog = Helpers.parseStrList(split, 57);
        mPlayerExitShortcut = Helpers.parseInt(split, 58, EXIT_SINGLE_BACK);
        // StackOverflow on old devices?
        mIsOldChannelLookEnabled = Helpers.parseBoolean(split, 59, Build.VERSION.SDK_INT <= 19);
        mIsFullscreenModeEnabled = Helpers.parseBoolean(split, 60, true);
        mIsHideWatchedFromWatchLaterEnabled = Helpers.parseBoolean(split, 61, false);
        mRememberPinnedPosition = Helpers.parseBoolean(split, 62, false);
        mSelectedItems = Helpers.parseMap(split, 63, Helpers::parseInt, Video::fromString);

        if (mPinnedItems.isEmpty()) {
            initPinnedItems();
        }

        // Backward compatibility
        enableSection(MediaGroup.TYPE_SETTINGS, true);

        cleanupPinnedItems();
    }

    private void initPinnedItems() {
        for (int sectionId : mDefaultSections.values()) {
            enableSection(sectionId, true);
        }
    }

    private void persistState() {
        // Zero index is skipped. Selected sections were there.
        mPrefs.setProfileData(GENERAL_DATA, Helpers.mergeData(null, mBootSectionId, mIsSettingsSectionEnabled, mAppExitShortcut,
                mIsPlayerOnlyModeEnabled, mBackgroundShortcut, mPinnedItems, mIsHideShortsFromSubscriptionsEnabled,
                mIsRemapFastForwardToNextEnabled, null, mIsProxyEnabled, mIsBridgeCheckEnabled, mIsOkButtonLongPressDisabled, mLastPlaylistId,
                null, mIsHideUpcomingEnabled, mIsRemapPageUpToNextEnabled, mIsRemapPageUpToLikeEnabled,
                mIsRemapChannelUpToNextEnabled, mIsRemapChannelUpToLikeEnabled, mIsRemapPageUpToSpeedEnabled,
                mIsRemapChannelUpToSpeedEnabled, mIsRemapFastForwardToSpeedEnabled, mIsRemapChannelUpToSearchEnabled,
                mIsHideShortsFromHomeEnabled, mIsHideShortsFromHistoryEnabled, mIsScreensaverDisabled, mIsVPNEnabled, mLastPlaylistTitle,
                mPlaylistOrder, mPendingStreams, mIsGlobalClockEnabled, mTimeFormat, mSettingsPassword, mIsChildModeEnabled, mIsHistoryEnabled,
                mScreensaverTimeoutMs, null, mIsAltAppIconEnabled, mVersionCode, mIsSelectChannelSectionEnabled, mMasterPassword,
                mIsOldHomeLookEnabled, mIsOldUpdateNotificationsEnabled, mScreensaverDimmingPercents, mIsRemapNextToSpeedEnabled, mIsRemapPlayToOKEnabled,
                mHistoryState, mRememberSubscriptionsPosition, null, mIsRemapNumbersToSpeedEnabled, mIsRemapDpadUpToSpeedEnabled, mIsRemapChannelUpToVolumeEnabled,
                mIsRemapDpadUpToVolumeEnabled, mIsRemapDpadLeftToVolumeEnabled, mIsRemapNextToFastForwardEnabled, mIsHideWatchedFromNotificationsEnabled,
                mChangelog, mPlayerExitShortcut, mIsOldChannelLookEnabled, mIsFullscreenModeEnabled, mIsHideWatchedFromWatchLaterEnabled,
                mRememberPinnedPosition, mSelectedItems));
    }

    private int getSectionId(Video item) {
        if (item == null) {
            return -1;
        }

        return item.sectionId == -1 ? item.hashCode() : item.sectionId;
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
