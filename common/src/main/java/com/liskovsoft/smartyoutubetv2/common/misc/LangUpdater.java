package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LangHelper;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

public class LangUpdater {
    private static final String TAG = LangUpdater.class.getSimpleName();
    private final AppPrefs mPrefs;
    private final Context mContext;

    public LangUpdater(Context ctx) {
        mContext = ctx;
        mPrefs = AppPrefs.instance(ctx);
    }

    public void update() {
        String locale = getUpdatedLocale();

        Log.d(TAG, "Updating locale to " + locale);

        LangHelper.forceLocale(mContext, locale);
    }

    public String getUpdatedLocale() {
        String locale = LangHelper.guessLocale(mContext);

        String langCode = getPreferredLocale();

        // not set or default language selected
        if (langCode != null && !langCode.isEmpty()) {
            locale = langCode;
        }

        return locale;
    }

    /**
     * Get locale as lang code (e.g. zh, ru_RU etc)
     * @return lang code
     */
    public String getPreferredLocale() {
        String language = mPrefs.getPreferredLanguage();

        language = appendCountry(language);

        return language != null ? language : "";
    }

    /**
     * Get locale in http format (e.g. zh, ru-RU etc)<br/>
     * Example: <code>ru,en-US;q=0.9,en;q=0.8,uk;q=0.7</code>
     * @return lang code
     */
    public String getPreferredBrowserLocale() {
        String locale = getPreferredLocale();

        if (locale == null || locale.isEmpty()) {
            locale = LangHelper.getDefaultLocale();
        }

        return locale.replace("_", "-").toLowerCase();
    }

    /**
     * E.g. ru, uk, en
     * @param langCode lang
     */
    public void setPreferredLocale(String langCode) {
        mPrefs.setPreferredLanguage(langCode);
    }

    public String getPreferredCountry() {
        String country = mPrefs.getPreferredCountry();
        return country != null ? country : "";
    }

    public void setPreferredCountry(String countryCode) {
        mPrefs.setPreferredCountry(countryCode);
    }

    /**
     * Gets map of Human readable locale names and their respective lang codes
     * @return locale name/code map
     */
    public Map<String, String> getSupportedLocales() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(mContext.getResources().getString(R.string.default_lang), "");
        return Helpers.getMap(mContext.getResources().getStringArray(R.array.supported_languages), "|", map);
    }

    public Map<String, String> getSupportedCountries() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(mContext.getResources().getString(R.string.default_lang), "");
        return Helpers.getMap(mContext.getResources().getStringArray(R.array.supported_countries), "|", map);
    }

    private String appendCountry(String langCode) {
        if (langCode != null && !langCode.isEmpty()) {
            String preferredCountry = getPreferredCountry();

            if (preferredCountry != null && !preferredCountry.isEmpty()) {
                StringTokenizer tokenizer = new StringTokenizer(langCode, "_");
                String lang = tokenizer.nextToken();

                langCode = String.format("%s_%s", lang, preferredCountry);
            }
        }

        return langCode;
    }
}
