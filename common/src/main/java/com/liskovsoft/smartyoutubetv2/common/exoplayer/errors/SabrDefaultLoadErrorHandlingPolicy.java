package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {
    private static final String TAG = SabrDefaultLoadErrorHandlingPolicy.class.getSimpleName();

    @Override
    public long getBlacklistDurationMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {
        return super.getBlacklistDurationMsFor(dataType, loadDurationMs, exception, errorCount);
    }

    @Override
    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {
        String message = exception.getMessage();

        // The server asked for a fresh player response. Retrying the same request is pointless -
        // it just hammers the server. Make the error fatal so the app reloads the video.
        if (Helpers.contains(message, SabrExtractorInput.RELOAD_MARKER)) {
            Log.e(TAG, "Player response reload requested, errorCount: " + errorCount);
            return C.TIME_UNSET; // fatal, no retry
        }

        // The server sent NextRequestPolicy.backoff_time_ms instead of media. Wait it out
        // rather than re-requesting at full speed.
        if (Helpers.contains(message, SabrExtractorInput.BACKOFF_MARKER)) {
            long delayMs = extractBackoffMs(message);
            Log.d(TAG, "Honouring SABR backoff: " + delayMs + " ms, errorCount: " + errorCount);
            return delayMs;
        }

        if (Helpers.contains(message, "Wait 5 sec")) {
            return 5_000;
        }

        return super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount);
    }

    private static final long MIN_BACKOFF_MS = 250;
    private static final long MAX_BACKOFF_MS = 10_000;

    private static long extractBackoffMs(String message) {
        int idx = message.indexOf(SabrExtractorInput.BACKOFF_MARKER);
        String raw = message.substring(idx + SabrExtractorInput.BACKOFF_MARKER.length()).trim();

        long parsed;

        try {
            parsed = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            parsed = 0;
        }

        return Math.max(MIN_BACKOFF_MS, Math.min(MAX_BACKOFF_MS, parsed));
    }

}
