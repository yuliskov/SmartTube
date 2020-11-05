package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MainUIData {
    @SuppressLint("StaticFieldLeak")
    private static MainUIData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsAnimatedPreviewsEnabled;
    private int mBootToCategoryId;
    private final Map<Integer, Integer> mLeftPanelCategories = new LinkedHashMap<>();
    private final Set<Integer> mEnabledLeftPanelCategories = new HashSet<>();

    public MainUIData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        initLeftPanelCategories();
        restoreState();
    }

    public static MainUIData instance(Context context) {
        if (sInstance == null) {
            sInstance = new MainUIData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableAnimatedPreviews(boolean enable) {
        mIsAnimatedPreviewsEnabled = enable;
        persistState();
    }

    public boolean isAnimatedPreviewsEnabled() {
        return mIsAnimatedPreviewsEnabled;
    }

    public Map<Integer, Integer> getCategories() {
        return mLeftPanelCategories;
    }

    public void setCategoryEnabled(int categoryId, boolean enabled) {
        if (enabled) {
            mEnabledLeftPanelCategories.add(categoryId);
        } else {
            mEnabledLeftPanelCategories.remove(categoryId);
        }

        persistState();
    }

    public boolean isCategoryEnabled(int categoryId) {
        return mEnabledLeftPanelCategories.contains(categoryId);
    }

    public void setBootToCategoryId(int categoryId) {
        mBootToCategoryId = categoryId;

        persistState();
    }

    public int getBootToCategoryId() {
        return mBootToCategoryId;
    }

    private void initLeftPanelCategories() {
        mLeftPanelCategories.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mLeftPanelCategories.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        mLeftPanelCategories.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        mLeftPanelCategories.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mLeftPanelCategories.put(R.string.header_channels, MediaGroup.TYPE_CHANNELS_SUB);
        mLeftPanelCategories.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mLeftPanelCategories.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mLeftPanelCategories.put(R.string.header_playlists, MediaGroup.TYPE_PLAYLISTS);
    }

    private void persistState() {
        String selectedCategories = Helpers.mergeArray(mEnabledLeftPanelCategories.toArray());
        mPrefs.setMainUIData(Helpers.mergeObject(mIsAnimatedPreviewsEnabled, selectedCategories, mBootToCategoryId));
    }

    private void restoreState() {
        String data = mPrefs.getMainUIData();

        String[] split = Helpers.splitObject(data);

        mIsAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0, true);
        String selectedCategories = Helpers.parseStr(split, 1);
        mBootToCategoryId = Helpers.parseInt(split, 2, MediaGroup.TYPE_HOME);

        if (selectedCategories != null) {
            String[] selectedCategoriesArr = Helpers.splitArray(selectedCategories);

            for (String categoryId : selectedCategoriesArr) {
                mEnabledLeftPanelCategories.add(Helpers.parseInt(categoryId));
            }
        } else {
            mEnabledLeftPanelCategories.addAll(mLeftPanelCategories.values());
        }
    }
}
