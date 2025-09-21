package com.google.android.exoplayer2.source.sabr.parser.processor;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientAbrState;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;

public class SabrProcessor {
    private final int liveSegmentTargetDurationToleranceMs;
    private ClientAbrState clientAbrState;

    public SabrProcessor() {
        this(100);
        // TODO: not implemented
    }

    public SabrProcessor(int liveSegmentTargetDurationToleranceMs) {
        this.liveSegmentTargetDurationToleranceMs = liveSegmentTargetDurationToleranceMs;
        // TODO: add more values
        initializeClientAbrState();
    }

    private void initializeClientAbrState() {
        // TODO: initialize builder
        clientAbrState = ClientAbrState.newBuilder()
                .build();
    }

    public ProcessMediaHeaderResult processMediaHeader(MediaHeader mediaHeader) {
        return null;
    }

    public boolean isLive() {
        return false;
    }

    @NonNull
    public ClientAbrState getClientAbrState() {
        return clientAbrState;
    }

    public void setClientAbrState(@NonNull ClientAbrState state) {
        clientAbrState = state;
    }

    public int getLiveSegmentTargetDurationToleranceMs() {
        return liveSegmentTargetDurationToleranceMs;
    }
}
