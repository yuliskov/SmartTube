package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class SearchData {
    public static final int SPEECH_RECOGNIZER_SYSTEM = 0;
    public static final int SPEECH_RECOGNIZER_INTENT = 1;
    public static final int SPEECH_RECOGNIZER_GOTEV = 2;
    private static final String SEARCH_DATA = "search_data";
    @SuppressLint("StaticFieldLeak")
    private static SearchData sInstance;
    private final AppPrefs mAppPrefs;
    private boolean mIsInstantVoiceSearchEnabled;
    private int mSearchOptions;
    private boolean mIsFocusOnResultsEnabled;
    private boolean mIsKeyboardAutoShowEnabled;
    private boolean mIsTempBackgroundModeEnabled;
    private int mSpeechRecognizerType;
    private Class<?> mTempBackgroundModeClass;
    private boolean mIsTrendingSearchesEnabled;
    private boolean mIsSearchHistoryDisabled;
    private boolean mIsPopularSearchesDisabled;
    private boolean mIsKeyboardFixEnabled;
    private boolean mIsTypingCorrectionDisabled;

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

    public boolean isInstantVoiceSearchEnabled() {
        return mIsInstantVoiceSearchEnabled;
    }

    public void setInstantVoiceSearchEnabled(boolean enabled) {
        mIsInstantVoiceSearchEnabled = enabled;
        persistData();
    }

    public boolean isFocusOnResultsEnabled() {
        return mIsFocusOnResultsEnabled;
    }

    public void setFocusOnResultsEnabled(boolean enabled) {
        mIsFocusOnResultsEnabled = enabled;
        persistData();
    }

    public int getSearchOptions() {
        return mSearchOptions;
    }

    public void setSearchOptions(int searchOptions) {
        mSearchOptions = searchOptions;
        persistData();
    }

    public boolean isKeyboardAutoShowEnabled() {
        return mIsKeyboardAutoShowEnabled;
    }

    public void setKeyboardAutoShowEnabled(boolean enabled) {
        mIsKeyboardAutoShowEnabled = enabled;
        persistData();
    }

    public boolean isKeyboardFixEnabled() {
        return mIsKeyboardFixEnabled;
    }

    public void setKeyboardFixEnabled(boolean enabled) {
        mIsKeyboardFixEnabled = enabled;
        persistData();
    }

    public boolean isTypingCorrectionDisabled() {
        return mIsTypingCorrectionDisabled;
    }

    public void setTypingCorrectionDisabled(boolean disabled) {
        mIsTypingCorrectionDisabled = disabled;
        persistData();
    }

    public void setTrendingSearchesEnabled(boolean enabled) {
        mIsTrendingSearchesEnabled = enabled;
        persistData();
    }

    public boolean isTrendingSearchesEnabled() {
        return mIsTrendingSearchesEnabled;
    }

    public boolean isTempBackgroundModeEnabled() {
        return mIsTempBackgroundModeEnabled;
    }

    public void setTempBackgroundModeEnabled(boolean enabled) {
        mIsTempBackgroundModeEnabled = enabled;
        persistData();
    }

    public Class<?> getTempBackgroundModeClass() {
        return mTempBackgroundModeClass;
    }

    public void setTempBackgroundModeClass(Class<?> clazz) {
        mTempBackgroundModeClass = clazz;
    }

    public int getSpeechRecognizerType() {
        return mSpeechRecognizerType;
    }

    public void setSpeechRecognizerType(int type) {
        mSpeechRecognizerType = type;
        persistData();
    }

    public boolean isSearchHistoryDisabled() {
        return mIsSearchHistoryDisabled;
    }

    public void setSearchHistoryDisabled(boolean disabled) {
        mIsSearchHistoryDisabled = disabled;
        persistData();
    }

    public boolean isPopularSearchesDisabled() {
        return mIsPopularSearchesDisabled;
    }

    public void setPopularSearchesDisabled(boolean disabled) {
        mIsPopularSearchesDisabled = disabled;
        persistData();
    }

    private void restoreData() {
        String data = mAppPrefs.getData(SEARCH_DATA);

        String[] split = Helpers.splitData(data);

        // WARN: Don't enable Instant Voice Search
        // Serious bug on Nvidia Shield. Can't type anything with soft keyboard.
        // Other devices probably affected too.
        mIsInstantVoiceSearchEnabled = Helpers.parseBoolean(split, 0, false);
        mSearchOptions = Helpers.parseInt(split, 1, 0);
        mIsFocusOnResultsEnabled = Helpers.parseBoolean(split, 2, true);
        mIsKeyboardAutoShowEnabled = Helpers.parseBoolean(split, 3, false);
        mIsTempBackgroundModeEnabled = Helpers.parseBoolean(split, 4, false);
        //mIsAltSpeechRecognizerEnabled
        mSpeechRecognizerType = Helpers.parseInt(split, 6, SPEECH_RECOGNIZER_SYSTEM);
        mIsTrendingSearchesEnabled = Helpers.parseBoolean(split, 7, true);
        mIsSearchHistoryDisabled = Helpers.parseBoolean(split, 8, false);
        mIsPopularSearchesDisabled = Helpers.parseBoolean(split, 9, false);
        mIsKeyboardFixEnabled = Helpers.parseBoolean(split, 10, false);
        mIsTypingCorrectionDisabled = Helpers.parseBoolean(split, 11, false);
    }

    private void persistData() {
        mAppPrefs.setData(SEARCH_DATA,
                Helpers.mergeData(mIsInstantVoiceSearchEnabled, mSearchOptions, mIsFocusOnResultsEnabled,
                        mIsKeyboardAutoShowEnabled, mIsTempBackgroundModeEnabled, null, mSpeechRecognizerType,
                        mIsTrendingSearchesEnabled, mIsSearchHistoryDisabled, mIsPopularSearchesDisabled,
                        mIsKeyboardFixEnabled, mIsTypingCorrectionDisabled));
    }
}
