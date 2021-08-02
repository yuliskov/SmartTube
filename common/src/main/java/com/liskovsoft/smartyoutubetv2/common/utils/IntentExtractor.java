package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Intent;
import android.net.Uri;
import com.liskovsoft.sharedutils.helpers.Helpers;
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
    private static final String SUBSCRIPTIONS_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEsubscriptions&resume";
    private static final String HISTORY_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEmy_youtube&resume";
    private static final String RECOMMENDED_URL = "https://www.youtube.com/tv#/zylon-surface?c=default&resume";
    private static final String PLAYLIST_KEY = "list";

    public static String extractVideoId(Intent intent) {
        if (isEmptyIntent(intent)) {
            return null;
        }

        if (extractVoiceQuery(intent.getData()) != null) {
            return null;
        }

        // Don't Uri directly or you might get UnsupportedOperationException on some urls.
        UrlQueryString parser = UrlQueryStringFactory.parse(intent.getData());
        String videoId = parser.get(VIDEO_ID_KEY);

        if (videoId == null) {
            // Suppose that link type is https://youtu.be/lBeMDqcWTG8
            videoId = intent.getData().getLastPathSegment();
        }

        if (!isValid(videoId)) {
            return null;
        }

        return videoId;
    }

    /**
     * Browser: https://www.youtube.com/results?search_query=twice<br/>
     * Amazon: youtube://search?query=linkin+park&isVoice=true
     */
    public static String extractSearchText(Intent intent) {
        if (isEmptyIntent(intent)) {
            return null;
        }

        String voiceQuery = extractVoiceQuery(intent.getData());

        if (voiceQuery != null) {
            return voiceQuery;
        }

        // Don't Uri directly or you might get UnsupportedOperationException on some urls.
        UrlQueryString parser = UrlQueryStringFactory.parse(intent.getData());

        for (String searchKey : SEARCH_KEYS) {
            String searchText = parser.get(searchKey);

            if (searchText != null) {
                return searchText;
            }
        }

        return null;
    }

    /**
     * Data: https://www.youtube.com/channel/UCtDjOV5nk982w35AIdVDuNw
     */
    public static String extractChannelId(Intent intent) {
        if (isEmptyIntent(intent)) {
            return null;
        }

        String[] split = intent.getData().toString().split(CHANNEL_URL);

        return split.length == 2 ? split[1] : null;
    }

    /**
     * Data: https://www.youtube.com/playlist?list=RDCLAK5uy_mk6AmqcHgCRhyJuYsQz5CCVdCF4SRGivs
     */
    public static String extractPlaylistId(Intent intent) {
        if (isEmptyIntent(intent)) {
            return null;
        }

        UrlQueryString parser = UrlQueryStringFactory.parse(intent.getData());

        return parser.get(PLAYLIST_KEY);
    }

    private static boolean isValid(String videoId) {
        return videoId != null && videoId.length() == 11;
    }

    public static boolean hasData(Intent intent) {
        return intent != null && intent.getData() != null;
    }

    /**
     * ATV: Channel icon url
     */
    public static boolean isChannelUrl(Intent intent) {
        return intent != null
                && intent.getData() != null
                && Helpers.contains(new String[] {SUBSCRIPTIONS_URL, HISTORY_URL, RECOMMENDED_URL}, intent.getData().toString());
    }

    public static boolean isStartVoiceCommand(Intent intent) {
        return intent != null && intent.getData() != null && intent.getData().toString().contains("launch=voice");
    }

    /**
     * Example: https://www.youtube.com/tv?voice={"youtubeAssistantRequest":{"query":"Russian YouTube","queryIntent":"CgxTZWFyY2hJbnRlbnQSFAoFcXVlcnkSCxoJCgdSdXNzaWFuEiYKCGRvY190eXBlEhoaGAoWWU9VVFVCRV9ET0NfVFlQRV9WSURFTw==","youtubeAssistantParams":{"personalDataParams":{"showPersonalData":false}},"enablePrefetchLogging":true},"updateYoutubeSettings":{"enableSafetyMode":false,"enablePersonalResults":false},"hasEntityBar":false}&command_id=CWGIYL6nN8Gi3AP_5Y6wAQ&launch=voice&vq=Russian%20YouTube
     */
    private static String extractVoiceQuery(Uri data) {
        return Helpers.runMultiMatcher(data.toString(), ":\\{\"query\":\"([^\"]*)\"");
    }

    private static boolean isEmptyIntent(Intent intent) {
        return intent == null || intent.getData() == null || !Intent.ACTION_VIEW.equals(intent.getAction());
    }
}
