package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainUIData {
    private static final String MAIN_UI_DATA = "main_ui_data";
    public static final int CHANNEL_SORTING_UPDATE = 0;
    public static final int CHANNEL_SORTING_AZ = 1;
    public static final int CHANNEL_SORTING_LAST_VIEWED = 2;
    public static final int PLAYLISTS_STYLE_GRID = 0;
    public static final int PLAYLISTS_STYLE_ROWS = 1;
    public static final int EXIT_NONE = 0;
    public static final int EXIT_DOUBLE_BACK = 1;
    public static final int EXIT_SINGLE_BACK = 2;
    @SuppressLint("StaticFieldLeak")
    private static MainUIData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsCardAnimatedPreviewsEnabled;
    private boolean mIsCardMultilineTitleEnabled;
    private boolean mIsCardTextAutoScrollEnabled;
    private boolean mIsSettingsCategoryEnabled;
    private int mCardTitleLinesNum;
    private int mBootCategoryId;
    private final Map<Integer, Integer> mLeftPanelCategories = new LinkedHashMap<>();
    private final Set<Integer> mEnabledLeftPanelCategories = new HashSet<>();
    private float mUIScale;
    private float mVideoGridScale;
    private final List<ColorScheme> mColorSchemes = new ArrayList<>();
    private int mColorSchemeIndex;
    private int mChannelCategorySorting;
    private int mPlaylistsStyle;
    private int mAppExitShortcut;
    private boolean mIsReturnToLauncherEnabled;

    private MainUIData(Context context) {
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

    public void enableCardAnimatedPreviews(boolean enable) {
        mIsCardAnimatedPreviewsEnabled = enable;
        persistState();
    }

    public boolean isCardAnimatedPreviewsEnabled() {
        return mIsCardAnimatedPreviewsEnabled;
    }

    public void enableCardMultilineTitle(boolean enable) {
        mIsCardMultilineTitleEnabled = enable;
        persistState();
    }

    public boolean isCardMultilineTitleEnabled() {
        return mIsCardMultilineTitleEnabled;
    }

    public void enableCardTextAutoScroll(boolean enable) {
        mIsCardTextAutoScrollEnabled = enable;
        persistState();
    }

    public boolean isCardTextAutoScrollEnabled() {
        return mIsCardTextAutoScrollEnabled;
    }

    public void setCartTitleLinesNum(int lines) {
        mCardTitleLinesNum = lines;
        persistState();
    }

    public int getCardTitleLinesNum() {
        return mCardTitleLinesNum;
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
                R.string.color_scheme_teal,
                null,
                null,
                null,
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_dark_grey,
                "App.Theme.DarkGrey.Player",
                "App.Theme.DarkGrey.Browse",
                "App.Theme.DarkGrey.Preferences",
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_red,
                "App.Theme.Red.Player",
                "App.Theme.Red.Browse",
                "App.Theme.Red.Preferences",
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_dark_grey_oled,
                "App.Theme.DarkGrey.OLED.Player",
                "App.Theme.DarkGrey.OLED.Browse",
                "App.Theme.DarkGrey.Preferences",
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_teal_oled,
                "App.Theme.Leanback.OLED.Player",
                "App.Theme.Leanback.OLED.Browse",
                null,
                mContext));
    }

    private void restoreState() {
        String data = mPrefs.getData(MAIN_UI_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsCardAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0, true);
        String selectedCategories = Helpers.parseStr(split, 1);
        mBootCategoryId = Helpers.parseInt(split, 2, MediaGroup.TYPE_HOME);
        mVideoGridScale = Helpers.parseFloat(split, 3, 1.0f);
        mUIScale = Helpers.parseFloat(split, 4, 1.0f);
        mColorSchemeIndex = Helpers.parseInt(split, 5, 1);
        mIsCardMultilineTitleEnabled = Helpers.parseBoolean(split, 6, true);
        mIsSettingsCategoryEnabled = Helpers.parseBoolean(split, 7, true);
        mChannelCategorySorting = Helpers.parseInt(split, 8, CHANNEL_SORTING_LAST_VIEWED);
        mPlaylistsStyle = Helpers.parseInt(split, 9, PLAYLISTS_STYLE_GRID);
        mAppExitShortcut = Helpers.parseInt(split, 10, EXIT_DOUBLE_BACK);
        mCardTitleLinesNum = Helpers.parseInt(split, 11, 1);
        mIsCardTextAutoScrollEnabled = Helpers.parseBoolean(split, 12, true);
        mIsReturnToLauncherEnabled = Helpers.parseBoolean(split, 13, true);

        if (selectedCategories != null) {
            String[] selectedCategoriesArr = Helpers.splitArrayLegacy(selectedCategories);

            for (String categoryId : selectedCategoriesArr) {
                mEnabledLeftPanelCategories.add(Helpers.parseInt(categoryId));
            }
        } else {
            mEnabledLeftPanelCategories.addAll(mLeftPanelCategories.values());
        }
    }

    private void persistState() {
        String selectedCategories = Helpers.mergeArray(mEnabledLeftPanelCategories.toArray());
        mPrefs.setData(MAIN_UI_DATA, Helpers.mergeObject(mIsCardAnimatedPreviewsEnabled, selectedCategories, mBootCategoryId, mVideoGridScale, mUIScale,
                mColorSchemeIndex, mIsCardMultilineTitleEnabled, mIsSettingsCategoryEnabled, mChannelCategorySorting,
                mPlaylistsStyle, mAppExitShortcut, mCardTitleLinesNum, mIsCardTextAutoScrollEnabled, mIsReturnToLauncherEnabled));
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
