package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.DateFormatter;
import com.liskovsoft.smartyoutubetv2.common.utils.HashList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralData {
    public static final int SCREEN_DIMMING_NEVER = 0;
    private static final String GENERAL_DATA = "general_data";
    public static final int EXIT_NONE = 0;
    public static final int EXIT_DOUBLE_BACK = 1;
    public static final int EXIT_SINGLE_BACK = 2;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME = 0;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK = 1;
    public static final int TIME_MODE_24 = 0;
    public static final int TIME_MODE_12 = 1;
    @SuppressLint("StaticFieldLeak")
    private static GeneralData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsSettingsSectionEnabled;
    private int mBootSectionId;
    private int mAppExitShortcut;
    private boolean mIsReturnToLauncherEnabled;
    private int mBackgroundShortcut;
    private boolean mIsHideShortsFromSubscriptionsEnabled;
    private boolean mIsHideUpcomingEnabled;
    private boolean mIsRemapFastForwardToNextEnabled;
    private int mScreenDimmingTimeoutMin;
    private int mTimeMode;
    private boolean mIsProxyEnabled;
    private boolean mIsBridgeCheckEnabled;
    private boolean mIsOkButtonLongPressDisabled;
    private String mLastPlaylistId;
    private String mLastPlaylistTitle;
    private boolean mIsRemapPageUpToNextEnabled;
    private boolean mIsRemapPageUpToLikeEnabled;
    private boolean mIsRemapChannelUpToNextEnabled;
    private boolean mIsRemapChannelUpToLikeEnabled;
    private boolean mIsRemapPageUpToSpeedEnabled;
    private boolean mIsRemapChannelUpToSpeedEnabled;
    private boolean mIsRemapFastForwardToSpeedEnabled;
    private boolean mIsRemapChannelUpToSearchEnabled;
    private boolean mIsHideShortsFromHomeEnabled;
    private boolean mIsHideShortsFromHistoryEnabled;
    private boolean mIsScreensaverDisabled;
    private boolean mIsVPNEnabled;
    private boolean mIsGlobalClockEnabled;
    private final Map<Integer, Integer> mDefaultSections = new LinkedHashMap<>();
    private final Map<String, Integer> mPlaylistOrder = new HashMap<>();
    private final List<Video> mPendingStreams = new ArrayList<>();

    private final List<Video> mPinnedItems = new HashList<Video>() {
        @Override
        public boolean add(Video video) {
            if (video == null) {
                return false;
            }

            return super.add(video);
        }

        @Override
        public void add(int index, Video video) {
            if (video == null) {
                return;
            }

            super.add(index, video);
        }
    };

    private GeneralData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
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
            int index = getDefaultSectionIndex(sectionId);

            Video item = new Video();
            item.extra = sectionId;

            if (index == -1) {
                mPinnedItems.add(item);
            } else {
                mPinnedItems.add(index, item);
            }
        } else {
            Helpers.removeIf(mPinnedItems, value -> value.extra == sectionId);
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

        return correctIndex(sectionId, index);
    }

    private int correctIndex(int sectionId, int originIndex) {
        if (originIndex > 0) {
            int limit = Math.min(originIndex + 1, mPinnedItems.size());
            for (int i = 0; i < limit; i++) {
                // Skip non-section items
                int currentSectionId = mPinnedItems.get(i).extra;
                if (currentSectionId == -1 || beforeSection(sectionId, currentSectionId)) {
                    originIndex--;
                }

                if (originIndex == 0) {
                    break;
                }
            }
        }

        return originIndex;
    }

    private boolean beforeSection(int leftId, int rightId) {
        int leftIndex = -1;
        int rightIndex = -1;
        int currentIndex = -1;

        for (int sectionId : mDefaultSections.values()) {
            currentIndex++;
            if (sectionId == leftId) {
                leftIndex = currentIndex;
            } else if (sectionId == rightId) {
                rightIndex = currentIndex;
            }
        }

        return leftIndex < rightIndex;
    }

    public Collection<Integer> getEnabledSections() {
        List<Integer> enabledSections = new ArrayList<>();

        for (Video item : mPinnedItems) {
            if (item.extra != -1) {
                enabledSections.add(item.extra);
            }
        }

        return enabledSections;
    }

    /**
     * Contains sections and pinned items!
     */
    public boolean isSectionEnabled(int sectionId) {
        return Helpers.findFirst(mPinnedItems, item -> item.hashCode() == sectionId || item.extra == sectionId) != null; // by default enable all pinned items
    }

    //public void setSectionIndex(int sectionId, int index) {
    //    // 1) distinguish section from pinned item
    //    // 2) add pinned items after the sections
    //}

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
            mPinnedItems.add(index + shift, mPinnedItems.get(index));
            persistState();
        }
    }

    private int findPinnedItemIndex(int sectionId) {
        int index = -1;

        for (Video item : mPinnedItems) {
            // Distinguish pinned items by hashCode or extra field (default section)!
            if (item.hashCode() == sectionId || item.extra == sectionId) {
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

    public void enableReturnToLauncher(boolean enable) {
        mIsReturnToLauncherEnabled = enable;
        persistState();
    }

    public boolean isReturnToLauncherEnabled() {
        return mIsReturnToLauncherEnabled;
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

    public void hideUpcoming(boolean enable) {
        GlobalPreferences.sInstance.hideUpcoming(enable);
    }

    public boolean isHideUpcomingEnabled() {
        return GlobalPreferences.sInstance.isHideUpcomingEnabled();
    }

    public void disableScreensaver(boolean enable) {
        mIsScreensaverDisabled = enable;
        persistState();
    }

    public boolean isScreensaverDisabled() {
        return mIsScreensaverDisabled;
    }

    public void remapFastForwardToNext(boolean enable) {
        mIsRemapFastForwardToNextEnabled = enable;
        mIsRemapFastForwardToSpeedEnabled = false;
        persistState();
    }

    public boolean isRemapFastForwardToNextEnabled() {
        return mIsRemapFastForwardToNextEnabled;
    }

    public void remapFastForwardToSpeed(boolean enable) {
        mIsRemapFastForwardToSpeedEnabled = enable;
        mIsRemapFastForwardToNextEnabled = false;
        persistState();
    }

    public boolean isRemapFastForwardToSpeedEnabled() {
        return mIsRemapFastForwardToSpeedEnabled;
    }

    public void remapPageUpToNext(boolean enable) {
        mIsRemapPageUpToNextEnabled = enable;
        mIsRemapPageUpToLikeEnabled = false;
        mIsRemapPageUpToSpeedEnabled = false;
        persistState();
    }

    public boolean isRemapPageUpToNextEnabled() {
        return mIsRemapPageUpToNextEnabled;
    }

    public void remapPageUpToLike(boolean enable) {
        mIsRemapPageUpToLikeEnabled = enable;
        mIsRemapPageUpToNextEnabled = false;
        mIsRemapPageUpToSpeedEnabled = false;
        persistState();
    }

    public boolean isRemapPageUpToLikeEnabled() {
        return mIsRemapPageUpToLikeEnabled;
    }

    public void remapPageUpToSpeed(boolean enable) {
        mIsRemapPageUpToSpeedEnabled = enable;
        mIsRemapPageUpToLikeEnabled = false;
        mIsRemapPageUpToNextEnabled = false;
        persistState();
    }

    public boolean isRemapPageUpToSpeedEnabled() {
        return mIsRemapPageUpToSpeedEnabled;
    }

    public void remapChannelUpToNext(boolean enable) {
        mIsRemapChannelUpToNextEnabled = enable;
        mIsRemapChannelUpToSearchEnabled = false;
        mIsRemapChannelUpToLikeEnabled = false;
        mIsRemapChannelUpToSpeedEnabled = false;
        persistState();
    }

    public boolean isRemapChannelUpToNextEnabled() {
        return mIsRemapChannelUpToNextEnabled;
    }

    public void remapChannelUpToLike(boolean enable) {
        mIsRemapChannelUpToLikeEnabled = enable;
        mIsRemapChannelUpToSearchEnabled = false;
        mIsRemapChannelUpToNextEnabled = false;
        mIsRemapChannelUpToSpeedEnabled = false;
        persistState();
    }

    public boolean isRemapChannelUpToLikeEnabled() {
        return mIsRemapChannelUpToLikeEnabled;
    }

    public void remapChannelUpToSpeed(boolean enable) {
        mIsRemapChannelUpToSpeedEnabled = enable;
        mIsRemapChannelUpToSearchEnabled = false;
        mIsRemapChannelUpToLikeEnabled = false;
        mIsRemapChannelUpToNextEnabled = false;
        persistState();
    }

    public boolean isRemapChannelUpToSpeedEnabled() {
        return mIsRemapChannelUpToSpeedEnabled;
    }

    public void remapChannelUpToSearch(boolean enable) {
        mIsRemapChannelUpToSearchEnabled = enable;
        mIsRemapChannelUpToSpeedEnabled = false;
        mIsRemapChannelUpToLikeEnabled = false;
        mIsRemapChannelUpToNextEnabled = false;
        persistState();
    }

    public boolean isRemapChannelUpToSearchEnabled() {
        return mIsRemapChannelUpToSearchEnabled;
    }

    public void setScreenDimmingTimeoutMin(int timeoutMin) {
        mScreenDimmingTimeoutMin = timeoutMin;
        persistState();
    }

    public int getScreenDimmingTimeoutMin() {
        return mScreenDimmingTimeoutMin;
    }

    public void setTimeMode(int mode) {
        mTimeMode = mode;
        persistState();
    }

    public int getTimeMode() {
        return mTimeMode;
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

    private void initSections() {
        String country = LocaleUtility.getCurrentLocale(mContext).getCountry();

        mDefaultSections.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mDefaultSections.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        if (!Helpers.equalsAny(country, "RU")) {
            mDefaultSections.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        }
        mDefaultSections.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mDefaultSections.put(R.string.header_channels, MediaGroup.TYPE_CHANNEL_UPLOADS);
        mDefaultSections.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mDefaultSections.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mDefaultSections.put(R.string.header_playlists, MediaGroup.TYPE_USER_PLAYLISTS);
        mDefaultSections.put(R.string.header_settings, MediaGroup.TYPE_SETTINGS);
    }

    private void cleanupPinnedItems() {
        Helpers.removeIf(mPinnedItems, value -> {
            if (value == null) {
                return true;
            }

            value.videoId = null;
            return !value.hasPlaylist() && value.channelId == null && value.extra == -1 && !value.hasReloadPageKey();
        });
    }

    private void restoreState() {
        String data = mPrefs.getData(GENERAL_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        // Zero index is skipped. Selected sections were there.
        mBootSectionId = Helpers.parseInt(split, 1, MediaGroup.TYPE_HOME);
        mIsSettingsSectionEnabled = Helpers.parseBoolean(split, 2, true);
        mAppExitShortcut = Helpers.parseInt(split, 3, EXIT_DOUBLE_BACK);
        mIsReturnToLauncherEnabled = Helpers.parseBoolean(split, 4, true);
        mBackgroundShortcut = Helpers.parseInt(split, 5, BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
        String pinnedItems = Helpers.parseStr(split, 6);
        mIsHideShortsFromSubscriptionsEnabled = Helpers.parseBoolean(split, 7, false);
        mIsRemapFastForwardToNextEnabled = Helpers.parseBoolean(split, 8, false);
        mScreenDimmingTimeoutMin = Helpers.parseInt(split, 9, 1);
        mIsProxyEnabled = Helpers.parseBoolean(split, 10, false);
        mIsBridgeCheckEnabled = Helpers.parseBoolean(split, 11, true);
        mIsOkButtonLongPressDisabled = Helpers.parseBoolean(split, 12, false);
        mLastPlaylistId = Helpers.parseStr(split, 13);
        String selectedSections = Helpers.parseStr(split, 14);
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
        String playlistOrder = Helpers.parseStr(split, 29);
        String pendingStreams = Helpers.parseStr(split, 30);
        mIsGlobalClockEnabled = Helpers.parseBoolean(split, 31, true);
        mTimeMode = Helpers.parseInt(split, 32, DateFormatter.is24HourLocale(mContext) ? TIME_MODE_24 : TIME_MODE_12);

        if (pinnedItems != null && !pinnedItems.isEmpty()) {
            String[] pinnedItemsArr = Helpers.splitArray(pinnedItems);

            for (String pinnedItem : pinnedItemsArr) {
                mPinnedItems.add(Video.fromString(pinnedItem));
            }
        } else {
            initPinnedItems();
        }

        if (playlistOrder != null && !playlistOrder.isEmpty()) {
            mPlaylistOrder.clear();
            String[] playlistOrderArr = Helpers.splitArray(playlistOrder);

            for (String playlistOrderItem : playlistOrderArr) {
                String[] keyValPair = playlistOrderItem.split("\\|");
                mPlaylistOrder.put(keyValPair[0], Integer.parseInt(keyValPair[1]));
            }
        }

        if (pendingStreams != null && !pendingStreams.isEmpty()) {
            String[] pendingStreamsArr = Helpers.splitArray(pendingStreams);
            for (String pendingStream : pendingStreamsArr) {
                mPendingStreams.add(Video.fromString(pendingStream));
            }
        }

        // Backward compatibility
        if (!isSectionEnabled(MediaGroup.TYPE_SETTINGS)) {
            initPinnedItems();
        }

        cleanupPinnedItems();
    }

    private void initPinnedItems() {
        for (int sectionId : mDefaultSections.values()) {
            enableSection(sectionId, true);
        }
    }

    private void persistState() {
        String pinnedItems = Helpers.mergeArray(mPinnedItems.toArray());
        String pendingStreams = Helpers.mergeArray(mPendingStreams.toArray());
        List<String> playlistOrderPairs = new ArrayList<>();
        for (Entry<String, Integer> pair : mPlaylistOrder.entrySet()) {
            playlistOrderPairs.add(String.format("%s|%s", pair.getKey(), pair.getValue()));
        }
        String playlistOrder = Helpers.mergeArray(playlistOrderPairs.toArray());
        // Zero index is skipped. Selected sections were there.
        mPrefs.setData(GENERAL_DATA, Helpers.mergeObject(null, mBootSectionId, mIsSettingsSectionEnabled, mAppExitShortcut,
                mIsReturnToLauncherEnabled,mBackgroundShortcut, pinnedItems, mIsHideShortsFromSubscriptionsEnabled,
                mIsRemapFastForwardToNextEnabled, mScreenDimmingTimeoutMin,
                mIsProxyEnabled, mIsBridgeCheckEnabled, mIsOkButtonLongPressDisabled, mLastPlaylistId,
                null, mIsHideUpcomingEnabled, mIsRemapPageUpToNextEnabled, mIsRemapPageUpToLikeEnabled,
                mIsRemapChannelUpToNextEnabled, mIsRemapChannelUpToLikeEnabled, mIsRemapPageUpToSpeedEnabled,
                mIsRemapChannelUpToSpeedEnabled, mIsRemapFastForwardToSpeedEnabled, mIsRemapChannelUpToSearchEnabled,
                mIsHideShortsFromHomeEnabled, mIsHideShortsFromHistoryEnabled, mIsScreensaverDisabled, mIsVPNEnabled, mLastPlaylistTitle,
                playlistOrder, pendingStreams, mIsGlobalClockEnabled, mTimeMode));
    }
}
