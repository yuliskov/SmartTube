package com.google.android.exoplayer2.source.sabr.preload;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.sabr.parser.misc.EnabledTrackTypes;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.VideoPlaybackAbrRequest;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Serves the SABR requests recorded by {@link SabrPreloader} instead of going to the network.<br/>
 * Requests that weren't recorded go to the upstream as usual.
 */
public final class SabrReplayDataSource implements DataSource {
    private static final String TAG = SabrReplayDataSource.class.getSimpleName();

    public static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;

        public Factory(DataSource.Factory upstreamFactory) {
            this.upstreamFactory = upstreamFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new SabrReplayDataSource(upstreamFactory.createDataSource());
        }
    }

    private final DataSource upstream;
    @Nullable private SabrResponseData replayData;
    private int replayPosition;
    @Nullable private Uri replayUri;
    private boolean upstreamOpened;

    public SabrReplayDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        SabrPreloadStore.Response response = findResponse(dataSpec);

        if (response != null) {
            replayData = response.data;
            replayPosition = 0;
            replayUri = dataSpec.uri;
            Log.d(TAG, "Replay preloaded SABR response: itag=%s, size=%s KB", response.formatId.getItag(), response.data.length() / 1024);
            return response.data.length();
        }

        upstreamOpened = true;
        return upstream.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (replayData == null) {
            return upstream.read(buffer, offset, readLength);
        }

        int read = replayData.read(replayPosition, buffer, offset, readLength);

        if (read > 0) {
            replayPosition += read;
        }

        return read;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return replayData != null ? replayUri : upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return replayData != null ? Collections.emptyMap() : upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        replayData = null;
        replayUri = null;

        if (upstreamOpened) {
            upstreamOpened = false;
            upstream.close();
        }
    }

    @Nullable
    private static SabrPreloadStore.Response findResponse(DataSpec dataSpec) {
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_POST || dataSpec.httpBody == null) {
            return null;
        }

        String url = dataSpec.uri.toString();

        if (!SabrPreloadStore.contains(url)) {
            SabrPreloadStore.onPlayerSession(url);
            return null;
        }

        try {
            VideoPlaybackAbrRequest request = VideoPlaybackAbrRequest.parseFrom(dataSpec.httpBody);
            boolean isVideo = request.getClientAbrState().getEnabledTrackTypesBitfield() == EnabledTrackTypes.VIDEO_ONLY;
            List<FormatId> preferred = isVideo ? request.getPreferredVideoFormatIdsList() : request.getPreferredAudioFormatIdsList();

            if (preferred.isEmpty()) {
                SabrPreloadStore.onPlayerSession(url);
                return null;
            }

            int trackType = isVideo ? C.TRACK_TYPE_VIDEO : C.TRACK_TYPE_AUDIO;

            FormatId formatId = preferred.get(0);
            long playerTimeMs = request.getClientAbrState().getPlayerTimeMs();
            SabrPreloadStore.Response response = SabrPreloadStore.poll(url, trackType, formatId, playerTimeMs);

            if (response == null) {
                Log.d(TAG, "Preloaded SABR responses dropped, mismatch: track=%s, itag=%s, xtags=%s, playerTimeMs=%s",
                        trackType, formatId.getItag(), formatId.getXtags(), playerTimeMs);
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Can't parse the SABR request: %s", e.getMessage());
            SabrPreloadStore.onPlayerSession(url);
            return null;
        }
    }
}
