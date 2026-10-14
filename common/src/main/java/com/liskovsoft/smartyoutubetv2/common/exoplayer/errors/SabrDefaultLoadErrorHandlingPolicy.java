package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrBackoffError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrReloadPlayerError;
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
        if (Helpers.contains(exception.getMessage(), "Wait 5 sec")) {
            return 5_000;
        }

        Throwable cause = exception.getCause();

        // The server asked for a fresh player response. Retrying the same request is pointless -
        // it just hammers the server. Make the error fatal so the app reloads the video.
        if (cause instanceof SabrReloadPlayerError) {
            Log.e(TAG, "Player response reload requested, errorCount: " + errorCount);
            return C.TIME_UNSET; // fatal, no retry. Propagate to ErrorFixerController
        }

        // The server sent NextRequestPolicy.backoff_time_ms instead of media. Wait it out
        // rather than re-requesting at full speed.
        if (cause instanceof SabrBackoffError) {
            long delayMs = ((SabrBackoffError) cause).backoffMs;
            Log.d(TAG, "Honouring SABR backoff: " + delayMs + " ms, errorCount: " + errorCount);
            //return delayMs;
            return C.TIME_UNSET; // fatal, no retry. Propagate to ErrorFixerController
        }

        return super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount);
    }
}
