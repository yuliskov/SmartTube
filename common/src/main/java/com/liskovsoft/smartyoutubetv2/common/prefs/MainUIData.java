package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.FlavorConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainUIData {
    public static final int CHANNEL_SORTING_UPDATE = 0;
    public static final int CHANNEL_SORTING_AZ = 1;
    public static final int CHANNEL_SORTING_LAST_VIEWED = 2;
    public static final int PLAYLISTS_STYLE_GRID = 0;
    public static final int PLAYLISTS_STYLE_ROWS = 1;
    @SuppressLint("StaticFieldLeak")
    private static MainUIData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsAnimatedPreviewsEnabled;
    private boolean mIsMultilineTitlesEnabled;
    private boolean mIsSettingsCategoryEnabled;
    private int mBootCategoryId;
    private final Map<Integer, Integer> mLeftPanelCategories = new LinkedHashMap<>();
    private final Set<Integer> mEnabledLeftPanelCategories = new HashSet<>();
    private float mUIScale;
    private float mVideoGridScale;
    private final List<ColorScheme> mColorSchemes = new ArrayList<>();
    private int mColorSchemeIndex;
    private int mChannelCategorySorting;
    private int mPlaylistsStyle;

    public MainUIData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        initLeftPanelCategories();
        initColorSchemes();
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

    public void enableMultilineTitles(boolean enable) {
        mIsMultilineTitlesEnabled = enable;
        persistState();
    }

    public boolean isMultilineTitlesEnabled() {
        return mIsMultilineTitlesEnabled;
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
        return mEnabledLeftPanelCategories.contains(categoryId);
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

    public void setVideoGridScale(float scale) {
        mVideoGridScale = scale;

        persistState();
    }

    public float getVideoGridScale() {
        return mVideoGridScale;
    }

    public void setUIScale(float scale) {
        mUIScale = scale;

        persistState();
    }

    public float getUIScale() {
        return mUIScale;
    }

    public List<ColorScheme> getColorSchemes() {
        return mColorSchemes;
    }

    public void setColorScheme(ColorScheme scheme) {
        mColorSchemeIndex = mColorSchemes.indexOf(scheme);
        persistState();
    }

    public ColorScheme getColorScheme() {
        return mColorSchemes.get(mColorSchemeIndex);
    }

    public int getChannelCategorySorting() {
        return mChannelCategorySorting;
    }

    public void setChannelCategorySorting(int type) {
        mChannelCategorySorting = type;
        persistState();
    }

    public int getPlaylistsStyle() {
        return mPlaylistsStyle;
    }

    public void setPlaylistsStyle(int type) {
        mPlaylistsStyle = type;
        persistState();
    }

    private void initLeftPanelCategories() {
        mLeftPanelCategories.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mLeftPanelCategories.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        mLeftPanelCategories.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        mLeftPanelCategories.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mLeftPanelCategories.put(R.string.header_channels, MediaGroup.TYPE_CHANNELS_SECTION);
        mLeftPanelCategories.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mLeftPanelCategories.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mLeftPanelCategories.put(R.string.header_playlists, MediaGroup.TYPE_PLAYLISTS_SECTION);
    }

    private void initColorSchemes() {
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_default,
                null,
                null,
                null,
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_dark,
                "App.Theme.Dark.Player",
                "App.Theme.Dark.Browse",
                "App.Theme.Dark.Preferences",
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_red,
                "App.Theme.Red.Player",
                "App.Theme.Red.Browse",
                "App.Theme.Red.Preferences",
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_dark_oled,
                "App.Theme.Dark.OLED.Player",
                "App.Theme.Dark.OLED.Browse",
                "App.Theme.Dark.Preferences",
                mContext));
    }

    private void persistState() {
        String selectedCategories = Helpers.mergeArray(mEnabledLeftPanelCategories.toArray());
        mPrefs.setMainUIData(Helpers.mergeObject(
                mIsAnimatedPreviewsEnabled, selectedCategories, mBootCategoryId, mVideoGridScale, mUIScale,
                mColorSchemeIndex, mIsMultilineTitlesEnabled, mIsSettingsCategoryEnabled, mChannelCategorySorting, mPlaylistsStyle));
    }

    private void restoreState() {
        String data = mPrefs.getMainUIData();

        String[] split = Helpers.splitObject(data);

        mIsAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0, true);
        String selectedCategories = Helpers.parseStr(split, 1);
        mBootCategoryId = Helpers.parseInt(split, 2, MediaGroup.TYPE_HOME);
        mVideoGridScale = Helpers.parseFloat(split, 3, FlavorConfig.AppPrefs.VIDEO_GRID_SCALE);
        mUIScale = Helpers.parseFloat(split, 4, 1.0f);
        mColorSchemeIndex = Helpers.parseInt(split, 5, FlavorConfig.AppPrefs.COLOR_SCHEME_INDEX);
        mIsMultilineTitlesEnabled = Helpers.parseBoolean(split, 6, false);
        mIsSettingsCategoryEnabled = Helpers.parseBoolean(split, 7, true);
        mChannelCategorySorting = Helpers.parseInt(split, 8, CHANNEL_SORTING_UPDATE);
        mPlaylistsStyle = Helpers.parseInt(split, 9, PLAYLISTS_STYLE_GRID);

        if (selectedCategories != null) {
            String[] selectedCategoriesArr = Helpers.splitArray(selectedCategories);

            for (String categoryId : selectedCategoriesArr) {
                mEnabledLeftPanelCategories.add(Helpers.parseInt(categoryId));
            }
        } else {
            mEnabledLeftPanelCategories.addAll(mLeftPanelCategories.values());
        }
    }

    public static class ColorScheme {
        public final int nameResId;
        public final int playerThemeResId;
        public final int browseThemeResId;
        public final int settingsThemeResId;

        public ColorScheme(int nameResId,
                           String playerTheme,
                           String browseTheme,
                           String settingsTheme,
                           Context context) {
            this.nameResId = nameResId;
            this.playerThemeResId = Helpers.getResourceId(playerTheme, "style", context);
            this.browseThemeResId = Helpers.getResourceId(browseTheme, "style", context);
            this.settingsThemeResId = Helpers.getResourceId(settingsTheme, "style", context);
        }
    }
}
