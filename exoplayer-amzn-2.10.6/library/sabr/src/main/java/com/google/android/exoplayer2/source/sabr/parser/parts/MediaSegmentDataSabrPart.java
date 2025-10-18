package com.google.android.exoplayer2.source.sabr.parser.parts;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

public class MediaSegmentDataSabrPart implements SabrPart {
    public final FormatSelector formatSelector;
    public final FormatId formatId;
    public final long sequenceNumber;
    public final boolean isInitSegment;
    public final int totalSegments;
    public final ExtractorInput data;
    public final int contentLength;
    public final int segmentStartBytes;

    public MediaSegmentDataSabrPart(
            FormatSelector formatSelector,
            FormatId formatId,
            long sequenceNumber,
            boolean isInitSegment,
            int totalSegments,
            ExtractorInput data,
            int contentLength,
            int segmentStartBytes) {
        this.formatSelector = formatSelector;
        this.formatId = formatId;
        this.sequenceNumber = sequenceNumber;
        this.isInitSegment = isInitSegment;
        this.totalSegments = totalSegments;
        this.data = data;
        this.contentLength = contentLength;
        this.segmentStartBytes = segmentStartBytes;
    }
}
