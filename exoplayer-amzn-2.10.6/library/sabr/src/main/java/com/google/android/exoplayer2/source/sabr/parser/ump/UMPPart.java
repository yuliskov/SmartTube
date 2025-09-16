package com.google.android.exoplayer2.source.sabr.parser.ump;

public class UMPPart {
    public final int partId;
    public final int size;
    public final byte[] data;

    public UMPPart(int partId, int size, byte[] data) {
        this.partId = partId;
        this.size = size;
        this.data = data;
    }
}
