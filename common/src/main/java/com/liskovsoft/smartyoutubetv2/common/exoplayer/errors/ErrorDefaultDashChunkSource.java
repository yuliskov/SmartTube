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

public class ErrorDefaultDashChunkSource extends DefaultDashChunkSource {
    public static final int MAX_SEGMENTS_PER_LOAD = 3; // Value > 1 adds special header?
    private final TrackErrorFixer mTrackErrorFixer;

    public static final class Factory implements DashChunkSource.Factory {

        private final DataSource.Factory dataSourceFactory;
        private final int maxSegmentsPerLoad;
        private final TrackErrorFixer trackErrorFixer;

        public Factory(DataSource.Factory dataSourceFactory, TrackErrorFixer trackErrorFixer) {
            this(dataSourceFactory, trackErrorFixer, MAX_SEGMENTS_PER_LOAD);
        }

        public Factory(DataSource.Factory dataSourceFactory, TrackErrorFixer trackErrorFixer, int maxSegmentsPerLoad) {
            this.dataSourceFactory = dataSourceFactory;
            this.maxSegmentsPerLoad = maxSegmentsPerLoad;
            this.trackErrorFixer = trackErrorFixer;
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
            return new ErrorDefaultDashChunkSource(
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
                    playerEmsgHandler,
                    trackErrorFixer);
        }

    }

    public ErrorDefaultDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower, DashManifest manifest, int periodIndex,
                                       int[] adaptationSetIndices, TrackSelection trackSelection, int trackType,
                                       DataSource dataSource, long elapsedRealtimeOffsetMs, int maxSegmentsPerLoad,
                                       boolean enableEventMessageTrack, List<Format> closedCaptionFormats,
                                       @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler, @Nullable TrackErrorFixer trackErrorFixer) {
        super(manifestLoaderErrorThrower, manifest, periodIndex, adaptationSetIndices, trackSelection,
                trackType, dataSource, elapsedRealtimeOffsetMs, maxSegmentsPerLoad,
                enableEventMessageTrack, closedCaptionFormats, playerTrackEmsgHandler);
        mTrackErrorFixer = trackErrorFixer;
    }

    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {
        if (mTrackErrorFixer == null) {
            return super.onChunkLoadError(chunk, cancelable, e, blacklistDurationMs);
        }

        return mTrackErrorFixer.fixError(e) || super.onChunkLoadError(chunk, cancelable, e, blacklistDurationMs);
    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {
        mTrackErrorFixer.fixEmptyChunk(chunk);

        super.onChunkLoadCompleted(chunk);
    }
}
