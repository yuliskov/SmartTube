package com.google.android.exoplayer2.source.sabr;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;

public final class SabrMediaSource extends BaseMediaSource {
    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        
    }

    @Override
    protected void releaseSourceInternal() {

    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {

    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
        return null;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {

    }
}
