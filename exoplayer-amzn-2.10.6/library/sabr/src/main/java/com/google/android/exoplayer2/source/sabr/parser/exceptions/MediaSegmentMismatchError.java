package com.google.android.exoplayer2.source.sabr.parser.exceptions;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

public class MediaSegmentMismatchError extends SabrStreamError {
    public final int expectedSequenceNumber;
    public final int receivedSequenceNumber;

    public MediaSegmentMismatchError(FormatId formatId, int expectedSequenceNumber, int receivedSequenceNumber) {
        super(String.format(
                "Segment sequence number mismatch for format %s: expected %s, received %s",
                formatId,
                expectedSequenceNumber,
                receivedSequenceNumber
        ));
        this.receivedSequenceNumber = receivedSequenceNumber;
        this.expectedSequenceNumber = expectedSequenceNumber;
    }
}
