package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Intent;

public class IntentExtractor {
    private static final String TAG = IntentExtractor.class.getSimpleName();
    /**
     * Browser: https://www.youtube.com/results?search_query=twice<br/>
     * Amazon: youtube://search?query=linkin+park&isVoice=true
     */
    private static final String[] SEARCH_KEYS = {"search_query", "query"};
    private static final String VIDEO_ID_KEY = "v";
    private static final String CHANNEL_URL = "/channel/";
    private static final String USER_URL = "/user/";

    public static String extractVideoId(Intent intent) {
        if (intent == null || intent.getData() == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }

        return intent.getData().getQueryParameter(VIDEO_ID_KEY);
    }

    /**
     * Browser: https://www.youtube.com/results?search_query=twice<br/>
     * Amazon: youtube://search?query=linkin+park&isVoice=true
     */
    public static String extractSearchText(Intent intent) {
        if (intent == null || intent.getData() == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }

        for (String searchKey : SEARCH_KEYS) {
            String searchText = intent.getData().getQueryParameter(searchKey);

            if (searchText != null) {
                return searchText;
            }
        }

        return null;
    }
}
