package com.google.android.exoplayer2.source.sabr.parser.ump;

import java.io.ByteArrayInputStream;

public class UMPPart {
    public final int partId;
    public final int size;
    public final ByteArrayInputStream data;

    public UMPPart(int partId, int size, ByteArrayInputStream data) {
        this.partId = partId;
        this.size = size;
        this.data = data;
    }
}
