package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

final class PlaybackRequestHeaders {
    private PlaybackRequestHeaders() {
    }

    static String resolveUserAgent(MediaItemFormatInfo.ClientInfo clientInfo, String defaultUserAgent) {
        String userAgent = clientInfo != null ? clientInfo.getUserAgent() : null;
        return userAgent != null && !userAgent.trim().isEmpty() ?
                userAgent : defaultUserAgent;
    }

    static void applyUserAgent(HttpDataSource.RequestProperties requestProperties, String userAgent) {
        if (requestProperties != null && userAgent != null && !userAgent.trim().isEmpty()) {
            requestProperties.set("User-Agent", userAgent);
        }
    }

    static boolean shouldRebuildFactory(
            String currentUserAgent,
            String nextUserAgent,
            long currentGeneration,
            long nextGeneration) {
        return currentGeneration != nextGeneration ||
                (currentUserAgent == null ? nextUserAgent != null : !currentUserAgent.equals(nextUserAgent));
    }
}
