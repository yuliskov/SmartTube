package com.google.android.exoplayer2.source.sabr;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.sabr.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.sabr.SabrChunkSource.Factory;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;

final class SabrMediaPeriod
   implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<SabrChunkSource>>,
        ChunkSampleStream.ReleaseCallback<SabrChunkSource> {
    private final int mId;
    private final SabrManifest mManifest;
    private final int mPeriodIndex;
    private final Factory mChunkSourceFactory;
    @Nullable
    private final TransferListener mTransferListener;
    private final LoadErrorHandlingPolicy mLoadErrorHandlingPolicy;
    private final EventDispatcher mEventDispatcher;
    private final LoaderErrorThrower mManifestLoaderErrorThrower;
    private final Allocator mAllocator;
    private final CompositeSequenceableLoaderFactory mCompositeSequenceableLoaderFactory;
    private final PlayerEmsgHandler mPlayerEmsgHandler;

    public SabrMediaPeriod(
            int id,
            SabrManifest manifest,
            int periodIndex,
            SabrChunkSource.Factory chunkSourceFactory,
            @Nullable TransferListener transferListener,
            LoadErrorHandlingPolicy loadErrorHandlingPolicy,
            EventDispatcher eventDispatcher,
            LoaderErrorThrower manifestLoaderErrorThrower,
            Allocator allocator,
            CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
            PlayerEmsgCallback playerEmsgCallback) {
        mId = id;
        mManifest = manifest;
        mPeriodIndex = periodIndex;
        mChunkSourceFactory = chunkSourceFactory;
        mTransferListener = transferListener;
        mLoadErrorHandlingPolicy = loadErrorHandlingPolicy;
        mEventDispatcher = eventDispatcher;
        mManifestLoaderErrorThrower = manifestLoaderErrorThrower;
        mAllocator = allocator;
        mCompositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        mPlayerEmsgHandler = new PlayerEmsgHandler(manifest, playerEmsgCallback, allocator);
    }

    @Override
    public void prepare(Callback callback, long positionUs) {
        
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {

    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return null;
    }

    @Override
    public long selectTracks(
            @Nullable TrackSelection[] selections,
            boolean[] mayRetainStreamFlags,
            @Nullable SampleStream[] streams,
            boolean[] streamResetFlags,
            long positionUs) {
        return 0;
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {

    }

    @Override
    public long readDiscontinuity() {
        return 0;
    }

    @Override
    public long seekToUs(long positionUs) {
        return 0;
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return 0;
    }

    @Override
    public long getBufferedPositionUs() {
        return 0;
    }

    @Override
    public long getNextLoadPositionUs() {
        return 0;
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return false;
    }

    @Override
    public void reevaluateBuffer(long positionUs) {

    }

    @Override
    public void onContinueLoadingRequested(ChunkSampleStream<SabrChunkSource> source) {

    }

    @Override
    public void onSampleStreamReleased(ChunkSampleStream<SabrChunkSource> chunkSampleStream) {

    }
}
