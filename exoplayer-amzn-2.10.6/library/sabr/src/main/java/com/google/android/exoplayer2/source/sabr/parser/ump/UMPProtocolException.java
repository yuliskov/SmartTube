package com.google.android.exoplayer2.source.sabr.parser.ump;

import java.io.IOException;

/** A typed framing failure produced while decoding an untrusted UMP byte stream. */
public final class UMPProtocolException extends IOException {
    public enum Reason {
        MALFORMED_VARINT,
        SIZE_OVERFLOW,
        PART_TOO_LARGE,
        BUFFER_LIMIT_EXCEEDED,
        TRUNCATED,
        CANCELLED,
        FINISHED
    }

    private final Reason reason;

    public UMPProtocolException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
