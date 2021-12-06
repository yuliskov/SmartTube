package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.List;

public class MyDefaultDashChunkSource extends DefaultDashChunkSource {
    public static final class Factory implements DashChunkSource.Factory {

        private final DataSource.Factory dataSourceFactory;
        private final int maxSegmentsPerLoad;

        public Factory(DataSource.Factory dataSourceFactory) {
            this(dataSourceFactory, 1);
        }

        public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad) {
            this.dataSourceFactory = dataSourceFactory;
            this.maxSegmentsPerLoad = maxSegmentsPerLoad;
        }

        @Override
        public DashChunkSource createDashChunkSource(
                LoaderErrorThrower manifestLoaderErrorThrower,
                DashManifest manifest,
                int periodIndex,
                int[] adaptationSetIndices,
                TrackSelection trackSelection,
                int trackType,
                long elapsedRealtimeOffsetMs,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                @Nullable PlayerTrackEmsgHandler playerEmsgHandler,
                @Nullable TransferListener transferListener) {
            DataSource dataSource = dataSourceFactory.createDataSource();
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return new MyDefaultDashChunkSource(
                    manifestLoaderErrorThrower,
                    manifest,
                    periodIndex,
                    adaptationSetIndices,
                    trackSelection,
                    trackType,
                    dataSource,
                    elapsedRealtimeOffsetMs,
                    maxSegmentsPerLoad,
                    enableEventMessageTrack,
                    closedCaptionFormats,
                    playerEmsgHandler);
        }

    }

    public MyDefaultDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower, DashManifest manifest, int periodIndex,
                                    int[] adaptationSetIndices, TrackSelection trackSelection, int trackType,
                                    DataSource dataSource, long elapsedRealtimeOffsetMs, int maxSegmentsPerLoad,
                                    boolean enableEventMessageTrack, List<Format> closedCaptionFormats,
                                    @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler) {
        super(manifestLoaderErrorThrower, manifest, periodIndex, adaptationSetIndices, trackSelection, trackType, dataSource, elapsedRealtimeOffsetMs, maxSegmentsPerLoad, enableEventMessageTrack, closedCaptionFormats, playerTrackEmsgHandler);
    }

    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {
        return true;
    }
}
