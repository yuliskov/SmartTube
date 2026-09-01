package com.liskovsoft.smartyoutubetv2.common.exoplayer.telemetry;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.DefaultMediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.liskovsoft.mediaserviceinterfaces.data.PlaybackRequestContext;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Emits one redacted line per media transport stage. */
public final class PlaybackTransportEventListener extends DefaultMediaSourceEventListener {
    private static final String TAG = "PlaybackTransport";
    private final PlaybackRequestContext mContext;
    private final PlaybackTransportTrace.Protocol mProtocol;
    private final String mEngine;
    private final int mAttempt;
    private String mRootManifestHash;

    public PlaybackTransportEventListener(PlaybackRequestContext context,
                                          PlaybackTransportTrace.Protocol protocol,
                                          String engine,
                                          int attempt) {
        mContext = context;
        mProtocol = protocol;
        mEngine = engine != null && engine.matches("[A-Za-z0-9()_-]{1,48}") ? engine : "unknown";
        mAttempt = attempt;
    }

    @Override
    public void onLoadStarted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                              LoadEventInfo info, MediaLoadData data) {
        PlaybackTransportTrace.Stage stage = stage(info, data);
        if (stage == PlaybackTransportTrace.Stage.HLS_MASTER ||
                stage == PlaybackTransportTrace.Stage.DASH_MANIFEST) {
            mRootManifestHash = PlaybackTransportTrace.shortHash(info.dataSpec.uri.toString());
        }
        emit("START", stage, info, data, "pending", null);
    }

    @Override
    public void onLoadCompleted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                                LoadEventInfo info, MediaLoadData data) {
        emit("COMPLETE", stage(info, data), info, data, "2xx", null);
    }

    @Override
    public void onLoadError(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                            LoadEventInfo info, MediaLoadData data, IOException error,
                            boolean wasCanceled) {
        String status = "io";
        if (error instanceof HttpDataSource.InvalidResponseCodeException) {
            status = Integer.toString(((HttpDataSource.InvalidResponseCodeException) error).responseCode);
        }
        emit(wasCanceled ? "ERROR_CANCELLED" : "ERROR", stage(info, data), info, data,
                status, error.getClass().getSimpleName());
    }

    private PlaybackTransportTrace.Stage stage(LoadEventInfo info, MediaLoadData data) {
        boolean initialization = data.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION;
        boolean manifest = data.dataType == C.DATA_TYPE_MANIFEST;
        PlaybackTransportTrace.Track track = data.trackType == C.TRACK_TYPE_AUDIO ?
                PlaybackTransportTrace.Track.AUDIO : data.trackType == C.TRACK_TYPE_VIDEO ?
                PlaybackTransportTrace.Track.VIDEO : PlaybackTransportTrace.Track.UNKNOWN;
        String url = info.dataSpec.uri.toString();
        boolean rootManifest = mRootManifestHash == null ||
                mRootManifestHash.equals(PlaybackTransportTrace.shortHash(url));
        return PlaybackTransportTrace.classify(
                mProtocol, url, manifest, rootManifest, initialization, track);
    }

    private void emit(String event, PlaybackTransportTrace.Stage stage, LoadEventInfo info,
                      MediaLoadData data, String status, String errorClass) {
        String finalUrl = info.uri != null ? info.uri.toString() : info.dataSpec.uri.toString();
        boolean redirected = !info.dataSpec.uri.toString().equals(finalUrl);
        Log.d(TAG,
                "event=%s,stage=%s,protocol=%s,engine=%s,attempt=%s,%s,%s,status=%s,bytes=%s,durationMs=%s,contentType=%s,redirected=%s,error=%s",
                event, stage, mProtocol, mEngine, mAttempt,
                PlaybackTransportTrace.describeContext(mContext),
                PlaybackTransportTrace.describeUrl(finalUrl, System.currentTimeMillis() / 1000L),
                status, info.bytesLoaded, info.loadDurationMs,
                contentType(info.responseHeaders), redirected,
                errorClass != null && errorClass.matches("[A-Za-z0-9_$]{1,96}") ? errorClass : "none");
    }

    private static String contentType(Map<String, List<String>> headers) {
        if (headers == null) return "unknown";
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "content-type".equalsIgnoreCase(entry.getKey()) &&
                    entry.getValue() != null && !entry.getValue().isEmpty()) {
                return PlaybackTransportTrace.safeContentType(entry.getValue().get(0));
            }
        }
        return "unknown";
    }
}
