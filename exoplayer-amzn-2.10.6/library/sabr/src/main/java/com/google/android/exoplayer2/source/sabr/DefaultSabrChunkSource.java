package com.google.android.exoplayer2.source.sabr;

import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;

import java.io.IOException;
import java.util.List;

public class DefaultSabrChunkSource implements SabrChunkSource {
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
