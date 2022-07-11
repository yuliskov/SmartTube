package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class SearchData {
    private static final String SEARCH_DATA = "search_data";
    @SuppressLint("StaticFieldLeak")
    private static SearchData sInstance;
    private final AppPrefs mAppPrefs;
    private boolean mIsInstantVoiceSearchEnabled;
    private int mSearchOptions;
    private boolean mIsFocusOnResultsEnabled;
    private boolean mIsKeyboardAutoShowEnabled;
    private boolean mIsBackgroundPlaybackEnabled;

    private SearchData(Context context) {
        mAppPrefs = AppPrefs.instance(context);
        restoreData();
    }

    public static SearchData instance(Context context) {
        if (sInstance == null) {
            sInstance = new SearchData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setInstantVoiceSearchEnabled(boolean enabled) {
        mIsInstantVoiceSearchEnabled = enabled;
        persistData();
    }

    public boolean isInstantVoiceSearchEnabled() {
        return mIsInstantVoiceSearchEnabled;
    }

    public void setFocusOnResultsEnabled(boolean enabled) {
        mIsFocusOnResultsEnabled = enabled;
        persistData();
    }

    public boolean isFocusOnResultsEnabled() {
        return mIsFocusOnResultsEnabled;
    }

    public void setSearchOptions(int searchOptions) {
        mSearchOptions = searchOptions;
        persistData();
    }

    public int getSearchOptions() {
        return mSearchOptions;
    }

    public void enableKeyboardAutoShow(boolean enabled) {
        mIsKeyboardAutoShowEnabled = enabled;
        persistData();
    }

    public boolean isKeyboardAutoShowEnabled() {
        return mIsKeyboardAutoShowEnabled;
    }

    public void enableBackgroundPlayback(boolean enabled) {
        mIsBackgroundPlaybackEnabled = enabled;
        persistData();
    }

    public boolean isBackgroundPlaybackEnabled() {
        return mIsBackgroundPlaybackEnabled;
    }

    private void restoreData() {
        String data = mAppPrefs.getData(SEARCH_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsInstantVoiceSearchEnabled = Helpers.parseBoolean(split, 0, true);
        mSearchOptions = Helpers.parseInt(split, 1, 0);
        mIsFocusOnResultsEnabled = Helpers.parseBoolean(split, 2, true);
        mIsKeyboardAutoShowEnabled = Helpers.parseBoolean(split, 3, false);
        mIsBackgroundPlaybackEnabled = Helpers.parseBoolean(split, 4, false);
    }

    private void persistData() {
        mAppPrefs.setData(SEARCH_DATA,
                Helpers.mergeObject(mIsInstantVoiceSearchEnabled, mSearchOptions, mIsFocusOnResultsEnabled,
                        mIsKeyboardAutoShowEnabled, mIsBackgroundPlaybackEnabled));
    }
}
