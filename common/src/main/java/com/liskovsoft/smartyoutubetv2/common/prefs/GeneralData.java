package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GeneralData {
    public static final int SCREEN_DIMMING_NEVER = 0;
    private static final String GENERAL_DATA = "general_data";
    public static final int EXIT_NONE = 0;
    public static final int EXIT_DOUBLE_BACK = 1;
    public static final int EXIT_SINGLE_BACK = 2;
    public static final int BACKGROUND_SHORTCUT_HOME = 0;
    public static final int BACKGROUND_SHORTCUT_HOME_N_BACK = 1;
    @SuppressLint("StaticFieldLeak")
    private static GeneralData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsSettingsCategoryEnabled;
    private int mBootCategoryId;
    private final Map<Integer, Integer> mLeftPanelCategories = new LinkedHashMap<>();
    private final Set<Integer> mEnabledLeftPanelCategories = new HashSet<>();
    private int mAppExitShortcut;
    private boolean mIsReturnToLauncherEnabled;
    private int mBackgroundShortcut;
    private Set<Video> mPinnedItems = new LinkedHashSet<>();
    private boolean mIsHideShortsEnabled;
    private boolean mIsRemapFastForwardToNextEnabled;
    private int mScreenDimmingTimeoutMin;
    private boolean mIsProxyEnabled;

    private GeneralData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        initLeftPanelCategories();
        restoreState();
    }

    public static GeneralData instance(Context context) {
        if (sInstance == null) {
            sInstance = new GeneralData(context.getApplicationContext());
        }

        return sInstance;
    }

    public Map<Integer, Integer> getCategories() {
        return mLeftPanelCategories;
    }

    public void enableCategory(int categoryId, boolean enabled) {
        if (enabled) {
            mEnabledLeftPanelCategories.add(categoryId);
        } else {
            mEnabledLeftPanelCategories.remove(categoryId);
        }

        persistState();
    }

    public boolean isCategoryEnabled(int categoryId) {
        // Enable by default pinned sidebar items
        return mEnabledLeftPanelCategories.contains(categoryId) || !mLeftPanelCategories.containsValue(categoryId);
    }

    public void setBootCategoryId(int categoryId) {
        mBootCategoryId = categoryId;

        persistState();
    }

    public int getBootCategoryId() {
        return mBootCategoryId;
    }

    public void enableSettingsCategory(boolean enabled) {
        mIsSettingsCategoryEnabled = enabled;

        persistState();
    }

    public boolean isSettingsCategoryEnabled() {
        return mIsSettingsCategoryEnabled;
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

    public int getBackgroundShortcut() {
        return mBackgroundShortcut;
    }

    public void setBackgroundShortcut(int type) {
        PlayerData playerData = PlayerData.instance(mContext);

        if (playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_DEFAULT) {
            playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PIP);
        }

        mBackgroundShortcut = type;
        persistState();
    }

    public Set<Video> getPinnedItems() {
        return mPinnedItems;
    }

    public void setPinnedItems(Set<Video> items) {
        mPinnedItems = items;
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

    public void setScreenDimmingTimoutMin(int timeoutMin) {
        mScreenDimmingTimeoutMin = timeoutMin;
        persistState();
    }

    public int getScreenDimmingTimoutMin() {
        return mScreenDimmingTimeoutMin;
    }

    public void enableProxy(boolean enable) {
        mIsProxyEnabled = enable;
        persistState();
    }

    public boolean isProxyEnabled() {
        return mIsProxyEnabled;
    }

    private void initLeftPanelCategories() {
        mLeftPanelCategories.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mLeftPanelCategories.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        mLeftPanelCategories.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        mLeftPanelCategories.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mLeftPanelCategories.put(R.string.header_channels, MediaGroup.TYPE_CHANNEL_UPLOADS);
        mLeftPanelCategories.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mLeftPanelCategories.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mLeftPanelCategories.put(R.string.header_playlists, MediaGroup.TYPE_USER_PLAYLISTS);
    }

    private void restoreState() {
        String data = mPrefs.getData(GENERAL_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        String selectedCategories = Helpers.parseStr(split, 0);
        mBootCategoryId = Helpers.parseInt(split, 1, MediaGroup.TYPE_HOME);
        mIsSettingsCategoryEnabled = Helpers.parseBoolean(split, 2, true);
        mAppExitShortcut = Helpers.parseInt(split, 3, EXIT_DOUBLE_BACK);
        mIsReturnToLauncherEnabled = Helpers.parseBoolean(split, 4, true);
        mBackgroundShortcut = Helpers.parseInt(split, 5, BACKGROUND_SHORTCUT_HOME);
        String pinnedItems = Helpers.parseStr(split, 6);
        mIsHideShortsEnabled = Helpers.parseBoolean(split, 7, false);
        mIsRemapFastForwardToNextEnabled = Helpers.parseBoolean(split, 8, false);
        mScreenDimmingTimeoutMin = Helpers.parseInt(split, 9, 1);
        mIsProxyEnabled = Helpers.parseBoolean(split, 10, false);

        if (selectedCategories != null) {
            String[] selectedCategoriesArr = Helpers.splitArrayLegacy(selectedCategories);

            for (String categoryId : selectedCategoriesArr) {
                mEnabledLeftPanelCategories.add(Helpers.parseInt(categoryId));
            }
        } else {
            mEnabledLeftPanelCategories.addAll(mLeftPanelCategories.values());
        }

        if (pinnedItems != null) {
            String[] pinnedItemsArr = Helpers.splitArray(pinnedItems);

            for (String pinnedItem : pinnedItemsArr) {
                mPinnedItems.add(Video.fromString(pinnedItem));
            }
        }
    }

    private void persistState() {
        String selectedCategories = Helpers.mergeArray(mEnabledLeftPanelCategories.toArray());
        String pinnedItems = Helpers.mergeArray(mPinnedItems.toArray());
        mPrefs.setData(GENERAL_DATA, Helpers.mergeObject(selectedCategories,
                mBootCategoryId, mIsSettingsCategoryEnabled, mAppExitShortcut,
                mIsReturnToLauncherEnabled,mBackgroundShortcut, pinnedItems,
                mIsHideShortsEnabled, mIsRemapFastForwardToNextEnabled, mScreenDimmingTimeoutMin,
                mIsProxyEnabled));
    }
}
