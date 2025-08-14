package com.google.android.exoplayer2.source.sabr;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.sabr.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.util.List;

public class DefaultSabrChunkSource implements SabrChunkSource {
    public static final class Factory implements SabrChunkSource.Factory {

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
        public SabrChunkSource createSabrChunkSource(
                LoaderErrorThrower manifestLoaderErrorThrower,
                SabrManifest manifest,
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
            return new DefaultSabrChunkSource();
        }

    }

    @Override
    public void updateManifest(SabrManifest newManifest, int periodIndex) {
        
    }

    @Override
    public void updateTrackSelection(TrackSelection trackSelection) {

    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return 0;
    }

    @Override
    public void maybeThrowError() throws IOException {

    }

    @Override
    public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        return 0;
    }

    @Override
    public void getNextChunk(long playbackPositionUs, long loadPositionUs, List<? extends MediaChunk> queue, ChunkHolder out) {

    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {

    }

    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {
        return false;
    }
}
