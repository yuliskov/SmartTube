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
    @SuppressLint("StaticFieldLeak")
    private static MainUIData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsAnimatedPreviewsEnabled;
    private int mBootCategoryId;
    private final Map<Integer, Integer> mLeftPanelCategories = new LinkedHashMap<>();
    private final Set<Integer> mEnabledLeftPanelCategories = new HashSet<>();
    private float mUIScale;
    private float mVideoGridScale;
    private final List<ColorScheme> mColorSchemes = new ArrayList<>();
    private int mColorSchemeIndex;

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

    private void initColorSchemes() {
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_default,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_red_grey,
                Helpers.getResourceId("App.Theme.RedGrey", "style", mContext),
                Helpers.getResourceId("text_badge_image_view_red", "layout", mContext),
                Helpers.getResourceId("scheme_red_grey_background_dark", "color", mContext),
                Helpers.getResourceId("App.Theme.Leanback.Browse.RedGrey", "style", mContext),
                Helpers.getResourceId("scheme_red_grey_shelf_background_dark", "color", mContext),
                Helpers.getResourceId("scheme_red_grey_card_background_dark", "color", mContext)));
    }

    private void persistState() {
        String selectedCategories = Helpers.mergeArray(mEnabledLeftPanelCategories.toArray());
        mPrefs.setMainUIData(Helpers.mergeObject(
                mIsAnimatedPreviewsEnabled, selectedCategories, mBootCategoryId, mVideoGridScale, mUIScale, mColorSchemeIndex));
    }

    private void restoreState() {
        String data = mPrefs.getMainUIData();

        String[] split = Helpers.splitObject(data);

        mIsAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0, true);
        String selectedCategories = Helpers.parseStr(split, 1);
        mBootCategoryId = Helpers.parseInt(split, 2, MediaGroup.TYPE_HOME);
        mVideoGridScale = Helpers.parseFloat(split, 3, 1.0f);
        mUIScale = Helpers.parseFloat(split, 4, 1.0f);
        mColorSchemeIndex = Helpers.parseInt(split, 5, 0);

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
        public final int cardLayoutResId;
        public final int brandColorId;
        public final int browseThemeResId;
        public final int shelfBackgroundColorId;
        public final int cardColorId;

        public ColorScheme(int nameResId, int playerThemeResId, int cardLayoutResId, int brandColorId, int browseThemeResId, int shelfBackgroundColorId, int cardColorId) {
            this.nameResId = nameResId;
            this.playerThemeResId = playerThemeResId;
            this.cardLayoutResId = cardLayoutResId;
            this.brandColorId = brandColorId;
            this.browseThemeResId = browseThemeResId;
            this.shelfBackgroundColorId = shelfBackgroundColorId;
            this.cardColorId = cardColorId;
        }
    }
}
