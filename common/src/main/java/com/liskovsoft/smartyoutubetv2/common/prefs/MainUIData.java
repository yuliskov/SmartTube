package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainUIData extends DataChangeBase implements ProfileChangeListener {
    private static final String MAIN_UI_DATA = "main_ui_data2";
    public static final int CARD_PREVIEW_DISABLED = 0;
    public static final int CARD_PREVIEW_MUTED = 1;
    public static final int CARD_PREVIEW_FULL = 2;
    public static final int CHANNEL_SORTING_NEW_CONTENT = 0;
    public static final int CHANNEL_SORTING_NAME = 1;
    public static final int CHANNEL_SORTING_DEFAULT = 2;
    public static final int CHANNEL_SORTING_LAST_VIEWED = 3;
    public static final int CHANNEL_SORTING_NAME2 = 4;
    public static final int PLAYLISTS_STYLE_GRID = 0;
    public static final int PLAYLISTS_STYLE_ROWS = 1;
    public static final long MENU_ITEM_RECENT_PLAYLIST = 1;
    public static final long MENU_ITEM_ADD_TO_QUEUE = 1 << 1;
    public static final long MENU_ITEM_PIN_TO_SIDEBAR = 1 << 2;
    public static final long MENU_ITEM_SHARE_LINK = 1 << 3;
    public static final long MENU_ITEM_SELECT_ACCOUNT = 1 << 4;
    public static final long MENU_ITEM_NOT_INTERESTED = 1 << 5;
    public static final long MENU_ITEM_REMOVE_FROM_HISTORY = 1 << 6;
    public static final long MENU_ITEM_MOVE_SECTION_UP = 1 << 7;
    public static final long MENU_ITEM_MOVE_SECTION_DOWN = 1 << 8;
    public static final long MENU_ITEM_OPEN_DESCRIPTION = 1 << 9;
    public static final long MENU_ITEM_RENAME_SECTION = 1 << 10;
    public static final long MENU_ITEM_PLAY_VIDEO = 1 << 11;
    public static final long MENU_ITEM_SAVE_REMOVE_PLAYLIST = 1 << 12;
    public static final long MENU_ITEM_ADD_TO_PLAYLIST = 1 << 13;
    public static final long MENU_ITEM_SUBSCRIBE = 1 << 14;
    public static final long MENU_ITEM_CREATE_PLAYLIST = 1 << 15;
    public static final long MENU_ITEM_STREAM_REMINDER = 1 << 16;
    public static final long MENU_ITEM_ADD_TO_NEW_PLAYLIST = 1 << 17;
    public static final long MENU_ITEM_SHARE_EMBED_LINK = 1 << 18;
    public static final long MENU_ITEM_SHOW_QUEUE = 1 << 19;
    public static final long MENU_ITEM_PLAYLIST_ORDER = 1 << 20;
    public static final long MENU_ITEM_TOGGLE_HISTORY = 1 << 21;
    public static final long MENU_ITEM_CLEAR_HISTORY = 1 << 22;
    public static final long MENU_ITEM_UPDATE_CHECK = 1 << 23;
    public static final long MENU_ITEM_OPEN_CHANNEL = 1 << 24;
    public static final long MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS = 1 << 25;
    public static final long MENU_ITEM_PLAY_VIDEO_INCOGNITO = 1 << 26;
    public static final long MENU_ITEM_MARK_AS_WATCHED = 1 << 27;
    public static final long MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK = 1 << 28;
    public static final long MENU_ITEM_OPEN_PLAYLIST = 1 << 29;
    public static final long MENU_ITEM_EXIT_FROM_PIP = 1 << 30;
    public static final long MENU_ITEM_OPEN_COMMENTS = 1L << 31;
    public static final long MENU_ITEM_SHARE_QR_LINK = 1L << 32;
    public static final long MENU_ITEM_PLAY_NEXT = 1L << 33;
    public static final long MENU_ITEM_RENAME_PLAYLIST = 1L << 34;
    public static final long MENU_ITEM_NOT_RECOMMEND_CHANNEL = 1L << 35;
    public static final int TOP_BUTTON_BROWSE_ACCOUNTS = 1;
    public static final int TOP_BUTTON_CHANGE_LANGUAGE = 1 << 1;
    public static final int TOP_BUTTON_SEARCH = 1 << 2;
    public static final int TOP_BUTTON_DEFAULT = TOP_BUTTON_SEARCH | TOP_BUTTON_BROWSE_ACCOUNTS;
    public static final long MENU_ITEM_DEFAULT = MENU_ITEM_PIN_TO_SIDEBAR | MENU_ITEM_NOT_INTERESTED | MENU_ITEM_NOT_RECOMMEND_CHANNEL |
            MENU_ITEM_REMOVE_FROM_HISTORY | MENU_ITEM_MOVE_SECTION_UP | MENU_ITEM_MOVE_SECTION_DOWN | MENU_ITEM_RENAME_SECTION |
            MENU_ITEM_SAVE_REMOVE_PLAYLIST | MENU_ITEM_ADD_TO_PLAYLIST | MENU_ITEM_CREATE_PLAYLIST | MENU_ITEM_RENAME_PLAYLIST |
            MENU_ITEM_ADD_TO_NEW_PLAYLIST | MENU_ITEM_STREAM_REMINDER | MENU_ITEM_PLAYLIST_ORDER | MENU_ITEM_OPEN_CHANNEL |
            MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS | MENU_ITEM_PLAY_NEXT | MENU_ITEM_OPEN_PLAYLIST | MENU_ITEM_SUBSCRIBE | MENU_ITEM_CLEAR_HISTORY;
    private static final Long[] MENU_ITEM_DEFAULT_ORDER = {
            MENU_ITEM_EXIT_FROM_PIP, MENU_ITEM_PLAY_VIDEO, MENU_ITEM_PLAY_VIDEO_INCOGNITO, MENU_ITEM_REMOVE_FROM_HISTORY,
            MENU_ITEM_STREAM_REMINDER, MENU_ITEM_RECENT_PLAYLIST, MENU_ITEM_ADD_TO_PLAYLIST, MENU_ITEM_CREATE_PLAYLIST, MENU_ITEM_RENAME_PLAYLIST,
            MENU_ITEM_ADD_TO_NEW_PLAYLIST, MENU_ITEM_NOT_INTERESTED, MENU_ITEM_NOT_RECOMMEND_CHANNEL, MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS,
            MENU_ITEM_MARK_AS_WATCHED, MENU_ITEM_PLAYLIST_ORDER, MENU_ITEM_PLAY_NEXT, MENU_ITEM_ADD_TO_QUEUE, MENU_ITEM_SHOW_QUEUE, MENU_ITEM_OPEN_CHANNEL,
            MENU_ITEM_OPEN_PLAYLIST, MENU_ITEM_SUBSCRIBE, MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, MENU_ITEM_PIN_TO_SIDEBAR, MENU_ITEM_SAVE_REMOVE_PLAYLIST,
            MENU_ITEM_OPEN_DESCRIPTION, MENU_ITEM_OPEN_COMMENTS, MENU_ITEM_SHARE_LINK, MENU_ITEM_SHARE_EMBED_LINK, MENU_ITEM_SHARE_QR_LINK,
            MENU_ITEM_SELECT_ACCOUNT, MENU_ITEM_TOGGLE_HISTORY, MENU_ITEM_CLEAR_HISTORY, MENU_ITEM_MOVE_SECTION_UP, MENU_ITEM_MOVE_SECTION_DOWN,
            MENU_ITEM_UPDATE_CHECK
    };
    @SuppressLint("StaticFieldLeak")
    private static MainUIData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsCardMultilineTitleEnabled;
    private boolean mIsCardMultilineSubtitleEnabled;
    private boolean mIsCardTextAutoScrollEnabled;
    private int mCardTitleLinesNum;
    private float mUIScale;
    private float mVideoGridScale;
    private final List<ColorScheme> mColorSchemes = new ArrayList<>();
    private int mColorSchemeIndex;
    private int mChannelCategorySorting;
    private int mPlaylistsStyle;
    private boolean mIsUploadsOldLookEnabled;
    private boolean mIsUploadsAutoLoadEnabled;
    private float mCardTextScrollSpeed;
    private long mMenuItems;
    private int mTopButtons;
    private int mThumbQuality;
    private List<Long> mMenuItemsOrdered;
    private boolean mIsChannelsFilterEnabled;
    private boolean mIsChannelSearchBarEnabled;
    private boolean mIsPinnedChannelRowsEnabled;
    private int mCardPreviewType;
    private final Runnable mPersistStateInt = this::persistStateInt;
    private boolean mIsUnlocalizedTitlesEnabled;

    private MainUIData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        initColorSchemes();
        restoreState();
    }

    public static MainUIData instance(Context context) {
        if (sInstance == null) {
            sInstance = new MainUIData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableCardMultilineTitle(boolean enable) {
        mIsCardMultilineTitleEnabled = enable;
        persistState();
    }

    public boolean isCardMultilineTitleEnabled() {
        return mIsCardMultilineTitleEnabled;
    }

    public void enableCardMultilineSubtitle(boolean enable) {
        mIsCardMultilineSubtitleEnabled = enable;
        persistState();
    }

    public boolean isCardMultilineSubtitleEnabled() {
        return mIsCardMultilineSubtitleEnabled;
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

    public void setThumbQuality(int quality) {
        mThumbQuality = quality;
        persistState();
    }

    public int getThumbQuality() {
        return mThumbQuality;
    }

    public void setVideoGridScale(float scale) {
        mVideoGridScale = scale;

        persistState();
    }

    public float getVideoGridScale() {
        // Fixing the bug with chaotic cards positioning on Android 4.4 devices.
        return Build.VERSION.SDK_INT <= 19 ? 1.2f : mVideoGridScale;
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

    public void enableUploadsOldLook(boolean enable) {
        mIsUploadsOldLookEnabled = enable;
        persistState();
    }

    public boolean isUploadsOldLookEnabled() {
        return mIsUploadsOldLookEnabled;
    }

    public void enableChannelsFilter(boolean enable) {
        mIsChannelsFilterEnabled = enable;
        persistState();
    }

    public boolean isChannelsFilterEnabled() {
        return mIsChannelsFilterEnabled;
    }

    public void enablePinnedChannelRows(boolean enable) {
        mIsPinnedChannelRowsEnabled = enable;
        persistState();
    }

    public boolean isPinnedChannelRowsEnabled() {
        return mIsPinnedChannelRowsEnabled;
    }

    public void enableChannelSearchBar(boolean enable) {
        mIsChannelSearchBarEnabled = enable;
        persistState();
    }

    public boolean isChannelSearchBarEnabled() {
        return mIsChannelSearchBarEnabled;
    }

    public void enableUploadsAutoLoad(boolean enable) {
        mIsUploadsAutoLoadEnabled = enable;
        persistState();
    }

    public boolean isUploadsAutoLoadEnabled() {
        return mIsUploadsAutoLoadEnabled;
    }

    public void setCardTextScrollSpeed(float factor) {
        mCardTextScrollSpeed = factor;

        enableCardTextAutoScroll(true);

        persistState();
    }

    public float getCardTextScrollSpeed() {
        return mCardTextScrollSpeed;
    }

    public void enableMenuItem(long menuItems) {
        mMenuItems |= menuItems;
        persistState();
    }

    public void disableMenuItem(long menuItems) {
        mMenuItems &= ~menuItems;
        persistState();
    }

    public boolean isMenuItemEnabled(long menuItems) {
        return (mMenuItems & menuItems) == menuItems;
    }

    public void setMenuItemIndex(int index, Long menuItem) {
        //int currentIndex = getMenuItemIndex(menuItem);
        //index = currentIndex < index ? index - 1 : index;

        mMenuItemsOrdered.remove(menuItem);

        if (index <= mMenuItemsOrdered.size()) {
            mMenuItemsOrdered.add(index, menuItem);
        } else {
            mMenuItemsOrdered.add(menuItem);
        }

        persistState();
    }

    public int getMenuItemIndex(long menuItem) {
        return mMenuItemsOrdered.indexOf(menuItem);
    }

    public List<Long> getMenuItemsOrdered() {
        return Collections.unmodifiableList(mMenuItemsOrdered);
    }

    public void enableTopButton(int button) {
        mTopButtons |= button;
        persistState();
    }

    public void disableTopButton(int button) {
        mTopButtons &= ~button;
        persistState();
    }

    public boolean isTopButtonEnabled(int button) {
        return (mTopButtons & button) == button;
    }

    public int getCardPreviewType() {
        return mCardPreviewType;
    }

    public void setCardPreviewType(int type) {
        mCardPreviewType = type;
        persistState();
    }

    public boolean isUnlocalizedTitlesEnabled() {
        return mIsUnlocalizedTitlesEnabled;
    }

    public void enableUnlocalizedTitles(boolean enabled) {
        mIsUnlocalizedTitlesEnabled = enabled;
        persistState();
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
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_dark_grey_monochrome,
                "App.Theme.DarkGrey2.OLED.Player",
                "App.Theme.DarkGrey2.OLED.Browse",
                "App.Theme.DarkGrey.Preferences",
                mContext));
        mColorSchemes.add(new ColorScheme(
                R.string.color_scheme_dark_blue,
                "App.Theme.Leanback.Blue.Player",
                "App.Theme.Leanback.Blue.Browse",
                "App.Theme.Leanback.Blue.Preferences",
                mContext));
    }

    private void restoreState() {
        String data = mPrefs.getProfileData(MAIN_UI_DATA);

        String[] split = Helpers.splitData(data);

        //mIsCardAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0, true);
        mVideoGridScale = Helpers.parseFloat(split, 1, 1.0f); // 4 cards in a row
        mUIScale = Helpers.parseFloat(split, 2, 1.0f);
        mColorSchemeIndex = Helpers.parseInt(split, 3, 1);
        mIsCardMultilineTitleEnabled = Helpers.parseBoolean(split, 4, true);
        mChannelCategorySorting = Helpers.parseInt(split, 5, CHANNEL_SORTING_LAST_VIEWED);
        mPlaylistsStyle = Helpers.parseInt(split, 6, PLAYLISTS_STYLE_GRID);
        mCardTitleLinesNum = Helpers.parseInt(split, 7, 1);
        mIsCardTextAutoScrollEnabled = Helpers.parseBoolean(split, 8, true);
        mIsUploadsOldLookEnabled = Helpers.parseBoolean(split, 9, false);
        mIsUploadsAutoLoadEnabled = Helpers.parseBoolean(split, 10, true);
        mCardTextScrollSpeed = Helpers.parseFloat(split, 11, 2);
        mMenuItems = Helpers.parseLong(split, 12, MENU_ITEM_DEFAULT);
        mTopButtons = Helpers.parseInt(split, 13, TOP_BUTTON_DEFAULT);
        // 14
        mThumbQuality = Helpers.parseInt(split, 15, ClickbaitRemover.THUMB_QUALITY_DEFAULT);
        mIsCardMultilineSubtitleEnabled = Helpers.parseBoolean(split, 16, true);
        mMenuItemsOrdered = Helpers.parseLongList(split, 17);
        mIsChannelsFilterEnabled = Helpers.parseBoolean(split, 18, true);
        mIsChannelSearchBarEnabled = Helpers.parseBoolean(split, 19, true);
        mIsPinnedChannelRowsEnabled = Helpers.parseBoolean(split, 20, true);
        mCardPreviewType = Helpers.parseInt(split, 21, CARD_PREVIEW_DISABLED);
        mIsUnlocalizedTitlesEnabled = Helpers.parseBoolean(split, 22, false);

        int idx = -1;
        for (Long menuItem : MENU_ITEM_DEFAULT_ORDER) {
            idx++;
            if (!mMenuItemsOrdered.contains(menuItem)) {
                if (idx < mMenuItemsOrdered.size()) {
                    mMenuItemsOrdered.add(idx, menuItem);
                } else {
                    mMenuItemsOrdered.add(menuItem);
                }

                boolean isEnabled = (MENU_ITEM_DEFAULT & menuItem) == menuItem;
                if (isEnabled) {
                    mMenuItems |= menuItem;
                }
            }
        }

        for (ContextMenuProvider provider : new ContextMenuManager(mContext).getProviders()) {
            if (!mMenuItemsOrdered.contains(provider.getId())) {
                mMenuItemsOrdered.add(provider.getId());
            }
        }
        
        updateDefaultValues();
    }

    private void persistState() {
        onDataChange();
        Utils.postDelayed(mPersistStateInt, 10_000);
    }
    
    private void persistStateInt() {
        mPrefs.setProfileData(MAIN_UI_DATA, Helpers.mergeData(null,
                mVideoGridScale, mUIScale, mColorSchemeIndex, mIsCardMultilineTitleEnabled,
                mChannelCategorySorting, mPlaylistsStyle, mCardTitleLinesNum, mIsCardTextAutoScrollEnabled,
                mIsUploadsOldLookEnabled, mIsUploadsAutoLoadEnabled, mCardTextScrollSpeed, mMenuItems, mTopButtons,
                null, mThumbQuality, mIsCardMultilineSubtitleEnabled, Helpers.mergeList(mMenuItemsOrdered),
                mIsChannelsFilterEnabled, mIsChannelSearchBarEnabled, mIsPinnedChannelRowsEnabled, mCardPreviewType,
                mIsUnlocalizedTitlesEnabled));

        //onDataChange();
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

    private void updateDefaultValues() {
        // Enable only certain items (not all, like it was)
        if (mMenuItems >>> 30 == 0b1) { // check leftmost bit (old format)
            int bits = 32 - 27;
            mMenuItems = mMenuItems << bits >>> bits; // remove auto enabled bits
        }

        if (mChannelCategorySorting == CHANNEL_SORTING_NAME2) {
            mChannelCategorySorting = CHANNEL_SORTING_NAME;
        }

        if (mChannelCategorySorting == CHANNEL_SORTING_DEFAULT) {
            mChannelCategorySorting = CHANNEL_SORTING_LAST_VIEWED;
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
        onDataChange();
    }
}
