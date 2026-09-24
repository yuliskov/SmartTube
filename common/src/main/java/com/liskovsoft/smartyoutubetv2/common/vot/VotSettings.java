package com.liskovsoft.smartyoutubetv2.common.vot;

import android.content.Context;

import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

/** Small, profile-aware settings shared by the player and its TV dialog. */
public final class VotSettings {
    public static final String[] SOURCE_LANGUAGES = {
            "en", "ru", "zh", "ko", "ar", "fr", "it", "es", "de", "ja"
    };
    public static final String[] TARGET_LANGUAGES = {"ru", "en", "kk"};
    public static final String SOURCE_YANDEX = "yandex";
    public static final String SOURCE_AUTO = "auto";
    public static final String SOURCE_YOUTUBE = "youtube";
    private static final String[] VOICE_SOURCES = {SOURCE_YANDEX, SOURCE_AUTO, SOURCE_YOUTUBE};
    private static final String SOURCE_KEY = "vot_source_language";
    private static final String TARGET_KEY = "vot_target_language";
    private static final String VOICE_KEY = "vot_voice";
    private static final String ORIGINAL_VOLUME_KEY = "vot_original_volume";
    private static final String TRANSLATION_VOLUME_KEY = "vot_translation_volume";
    private static final String ADAPTIVE_VOLUME_KEY = "vot_adaptive_volume";
    private static final String ENABLED_KEY = "vot_enabled";
    private static final String SHOW_BUTTON_KEY = "vot_show_button";
    private static final String STRONG_DUCK_KEY = "vot_strong_duck";
    private static final String VOICE_SOURCE_KEY = "vot_voice_source";
    private final AppPrefs mPrefs;
    private final Context mContext;

    public VotSettings(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
    }

    public String getSourceLanguage() {
        String target = getTargetLanguage();
        String fallback = "en".equals(target) ? "ru" : "en";
        return language(SOURCE_KEY, SOURCE_LANGUAGES, fallback);
    }

    public boolean hasSelectedSourceLanguage() {
        return contains(SOURCE_LANGUAGES, mPrefs.getProfileData(SOURCE_KEY));
    }

    public boolean shouldSkipSameLanguage(String source, String target, boolean automatic) {
        return automatic && !hasSelectedSourceLanguage() && target.equals(source);
    }

    public void setSourceLanguage(String language) {
        if (language == null || contains(SOURCE_LANGUAGES, language))
            mPrefs.setProfileData(SOURCE_KEY, language == null ? "auto" : language);
    }

    public String getTargetLanguage() {
        return language(TARGET_KEY, TARGET_LANGUAGES,
                defaultTargetLanguage(LocaleUtility.getCurrentLanguage(mContext)));
    }

    public void setTargetLanguage(String language) {
        if (contains(TARGET_LANGUAGES, language)) mPrefs.setProfileData(TARGET_KEY, language);
    }

    static String defaultTargetLanguage(String uiLanguage) {
        if ("en".equals(uiLanguage)) return "en";
        if ("kk".equals(uiLanguage)) return "kk";
        return "ru";
    }

    public boolean useLivelyVoice() {
        return !"standard".equals(mPrefs.getProfileData(VOICE_KEY));
    }

    public void setLivelyVoice(boolean lively) {
        mPrefs.setProfileData(VOICE_KEY, lively ? "lively" : "standard");
    }

    public int getOriginalVolume() {
        return volume(ORIGINAL_VOLUME_KEY, 15);
    }

    public void setOriginalVolume(int percent) {
        mPrefs.setProfileData(ORIGINAL_VOLUME_KEY, String.valueOf(clamp(percent)));
    }

    public int getTranslationVolume() {
        return volume(TRANSLATION_VOLUME_KEY, 100);
    }

    public void setTranslationVolume(int percent) {
        mPrefs.setProfileData(TRANSLATION_VOLUME_KEY, String.valueOf(clamp(percent)));
    }

    public boolean isAdaptiveVolumeEnabled() {
        return !"false".equals(mPrefs.getProfileData(ADAPTIVE_VOLUME_KEY));
    }

    public void setAdaptiveVolumeEnabled(boolean enabled) {
        mPrefs.setProfileData(ADAPTIVE_VOLUME_KEY, String.valueOf(enabled));
    }

    public boolean isEnabled() {
        return "true".equals(mPrefs.getProfileData(ENABLED_KEY));
    }

    public void setEnabled(boolean enabled) {
        mPrefs.setProfileData(ENABLED_KEY, String.valueOf(enabled));
    }

    public String getVoiceSource() {
        return language(VOICE_SOURCE_KEY, VOICE_SOURCES, SOURCE_YANDEX);
    }

    public void setVoiceSource(String source) {
        if (contains(VOICE_SOURCES, source)) mPrefs.setProfileData(VOICE_SOURCE_KEY, source);
    }

    public boolean isButtonShown() {
        return !"false".equals(mPrefs.getProfileData(SHOW_BUTTON_KEY));
    }

    public boolean shouldShowButton() {
        return isEnabled() && isButtonShown();
    }

    public void setButtonShown(boolean shown) {
        mPrefs.setProfileData(SHOW_BUTTON_KEY, String.valueOf(shown));
    }

    public boolean isStrongDuckingEnabled() {
        return !"false".equals(mPrefs.getProfileData(STRONG_DUCK_KEY));
    }

    public void setStrongDuckingEnabled(boolean enabled) {
        mPrefs.setProfileData(STRONG_DUCK_KEY, String.valueOf(enabled));
    }

    private String language(String key, String[] supported, String fallback) {
        String saved = mPrefs.getProfileData(key);
        return contains(supported, saved) ? saved : fallback;
    }

    private int volume(String key, int fallback) {
        try {
            return clamp(Integer.parseInt(mPrefs.getProfileData(key)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) if (candidate.equals(value)) return true;
        return false;
    }
}
