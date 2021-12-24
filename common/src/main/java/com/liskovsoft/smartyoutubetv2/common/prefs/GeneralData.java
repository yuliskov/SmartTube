package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.utils.HashList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeneralData {
    public static final int SCREEN_DIMMING_NEVER = 0;
    private static final String GENERAL_DATA = "general_data";
    public static final int EXIT_NONE = 0;
    public static final int EXIT_DOUBLE_BACK = 1;
    public static final int EXIT_SINGLE_BACK = 2;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME = 0;
    public static final int BACKGROUND_PLAYBACK_SHORTCUT_HOME_N_BACK = 1;
    @SuppressLint("StaticFieldLeak")
    private static GeneralData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsSettingsSectionEnabled;
    private int mBootSectionId;
    private final Map<Integer, Integer> mDefaultSections = new LinkedHashMap<>();
    private final List<Integer> mEnabledSections = new HashList<>();
    private final List<Video> mPinnedItems = new HashList<>();
    private int mAppExitShortcut;
    private boolean mIsReturnToLauncherEnabled;
    private int mBackgroundShortcut;
    private boolean mIsHideShortsEnabled;
    private boolean mIsRemapFastForwardToNextEnabled;
    private int mScreenDimmingTimeoutMin;
    private boolean mIsProxyEnabled;
    private boolean mIsBridgeCheckEnabled;
    private boolean mIsOkButtonLongPressDisabled;
    private String mLastPlaylistId;

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
        return mPinnedItems;
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

            if (index == -1) {
                mEnabledSections.add(sectionId);
            } else {
                mEnabledSections.add(index, sectionId);
            }
        } else {
            mEnabledSections.remove((Object) sectionId);
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

    public Collection<Integer> getEnabledSections() {
        return mEnabledSections;
    }

    /**
     * Contains sections and pinned items!
     */
    public boolean isSectionEnabled(int sectionId) {
        return mEnabledSections.contains(sectionId) ||
                Helpers.findFirst(mPinnedItems, item -> item.hashCode() == sectionId) != null; // by default enable all pinned items
    }

    //public void setSectionIndex(int sectionId, int index) {
    //    // 1) distinguish section from pinned item
    //    // 2) add pinned items after the sections
    //}

    public int getSectionIndex(int sectionId) {
        // 1) Distinguish section from pinned item
        // 2) Add pinned items after the sections

        int index = findSectionIndex(sectionId);

        if (index == -1) {
            index = findPinnedItemIndex(sectionId);

            // Add pinned items after the sections
            if (index != -1) {
                index += mEnabledSections.size();
            }
        }

        return index;
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

    private int findSectionIndex(int sectionId) {
        return mEnabledSections.indexOf(sectionId);
    }

    private boolean canShiftSection(int sectionId, int shift) {
        int index = findSectionIndex(sectionId);

        if (index != -1) {
            return index + shift >= 0 && index + shift < mEnabledSections.size();
        }

        index = findPinnedItemIndex(sectionId);

        if (index != -1) {
            return  index + shift >= 0 && index + shift < mPinnedItems.size();
        }

        return false;
    }

    private void shiftSection(int sectionId, int shift) {
        if (!canShiftSection(sectionId, shift)) {
            return;
        }

        int index = findSectionIndex(sectionId);

        if (index != -1) {
            mEnabledSections.add(index + shift, mEnabledSections.get(index));
            persistState();
            return;
        }

        index = findPinnedItemIndex(sectionId);

        if (index != -1) {
            mPinnedItems.add(index + shift, mPinnedItems.get(index));
            persistState();
        }
    }

    private int findPinnedItemIndex(int sectionId) {
        int index = -1;

        for (Video item : mPinnedItems) {
            // Distinguish pinned items by hashCode!
            if (item.hashCode() == sectionId) {
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
        PlayerData playerData = PlayerData.instance(mContext);

        if (playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_DEFAULT) {
            playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PIP);
        }

        mBackgroundShortcut = type;
        persistState();
    }

    public void hideShorts(boolean enable) {
        mIsHideShortsEnabled = enable;
        persistState();
    }

    public boolean isHideShortsEnabled() {
        return mIsHideShortsEnabled;
    }

    public void remapFastForwardToNext(boolean enable) {
        mIsRemapFastForwardToNextEnabled = enable;
        persistState();
    }

    public boolean isRemapFastForwardToNextEnabled() {
        return mIsRemapFastForwardToNextEnabled;
    }

    public void setScreenDimmingTimeoutMin(int timeoutMin) {
        mScreenDimmingTimeoutMin = timeoutMin;
        persistState();
    }

    public int getScreenDimmingTimeoutMin() {
        return mScreenDimmingTimeoutMin;
    }

    public void enableProxy(boolean enable) {
        mIsProxyEnabled = enable;
        persistState();
    }

    public boolean isProxyEnabled() {
        return mIsProxyEnabled;
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

    private void initSections() {
        mDefaultSections.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mDefaultSections.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        mDefaultSections.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        mDefaultSections.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mDefaultSections.put(R.string.header_channels, MediaGroup.TYPE_CHANNEL_UPLOADS);
        mDefaultSections.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mDefaultSections.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mDefaultSections.put(R.string.header_playlists, MediaGroup.TYPE_USER_PLAYLISTS);
        mDefaultSections.put(R.string.header_settings, MediaGroup.TYPE_SETTINGS);
    }

    private void restoreState() {
        String data = mPrefs.getData(GENERAL_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        // Zero index is skipped. Selected sections were there.
        mBootSectionId = Helpers.parseInt(split, 1, MediaGroup.TYPE_HOME);
        mIsSettingsSectionEnabled = Helpers.parseBoolean(split, 2, true);
        mAppExitShortcut = Helpers.parseInt(split, 3, EXIT_DOUBLE_BACK);
        mIsReturnToLauncherEnabled = Helpers.parseBoolean(split, 4, true);
        mBackgroundShortcut = Helpers.parseInt(split, 5, BACKGROUND_PLAYBACK_SHORTCUT_HOME);
        String pinnedItems = Helpers.parseStr(split, 6);
        mIsHideShortsEnabled = Helpers.parseBoolean(split, 7, false);
        mIsRemapFastForwardToNextEnabled = Helpers.parseBoolean(split, 8, false);
        mScreenDimmingTimeoutMin = Helpers.parseInt(split, 9, 1);
        mIsProxyEnabled = Helpers.parseBoolean(split, 10, false);
        mIsBridgeCheckEnabled = Helpers.parseBoolean(split, 11, true);
        mIsOkButtonLongPressDisabled = Helpers.parseBoolean(split, 12, false);
        mLastPlaylistId = Helpers.parseStr(split, 13);
        String selectedSections = Helpers.parseStr(split, 14);

        if (selectedSections != null) {
            String[] selectedSectionsArr = Helpers.splitArrayLegacy(selectedSections);

            for (String sectionId : selectedSectionsArr) {
                mEnabledSections.add(Helpers.parseInt(sectionId));
            }
        } else {
            mEnabledSections.addAll(mDefaultSections.values());
        }

        if (pinnedItems != null) {
            String[] pinnedItemsArr = Helpers.splitArray(pinnedItems);

            for (String pinnedItem : pinnedItemsArr) {
                mPinnedItems.add(Video.fromString(pinnedItem));
            }
        }
    }

    private void persistState() {
        String selectedCategories = Helpers.mergeArray(mEnabledSections.toArray());
        String pinnedItems = Helpers.mergeArray(mPinnedItems.toArray());
        // Zero index is skipped. Selected sections were there.
        mPrefs.setData(GENERAL_DATA, Helpers.mergeObject(null, mBootSectionId, mIsSettingsSectionEnabled, mAppExitShortcut,
                mIsReturnToLauncherEnabled,mBackgroundShortcut, pinnedItems,
                mIsHideShortsEnabled, mIsRemapFastForwardToNextEnabled, mScreenDimmingTimeoutMin,
                mIsProxyEnabled, mIsBridgeCheckEnabled, mIsOkButtonLongPressDisabled, mLastPlaylistId, selectedCategories));
    }
}
