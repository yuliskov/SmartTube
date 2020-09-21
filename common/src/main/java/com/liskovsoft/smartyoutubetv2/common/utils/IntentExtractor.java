package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Intent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryString;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;

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

    public static boolean isVideo(Intent intent) {
        if (intent == null) {
            return false;
        }

        return Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null;
    }

    public static String getVideoId(Intent intent) {
        if (!isVideo(intent)) {
            return null;
        }

        return intent.getData().getQueryParameter(VIDEO_ID_KEY);
    }

    /**
     * Browser: https://www.youtube.com/results?search_query=twice<br/>
     * Amazon: youtube://search?query=linkin+park&isVoice=true
     */
    private static String extractSearchString(String url) {
        UrlQueryString query = UrlQueryStringFactory.parse(url);

        String result = null;

        for (String key : SEARCH_KEYS) {
            result = query.get(key);

            if (result != null) {
                break;
            }
        }

        if (result == null) {
            Log.w(TAG, "Url isn't a search string: " + url);
            return null;
        }

        return result;
    }
}
