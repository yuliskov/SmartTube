package com.google.android.exoplayer2.source.sabr.parser.exceptions;

/** Typed failure while correlating MEDIA/MEDIA_END with a MEDIA_HEADER. */
public final class SabrMediaCorrelationException extends RuntimeException {
    public enum Reason {
        INVALID_HEADER,
        VIDEO_MISMATCH,
        DUPLICATE_HEADER,
        DUPLICATE_END,
        MISSING_HEADER,
        CONTENT_LENGTH_MISMATCH,
        STORE_LIMIT_EXCEEDED,
        MALFORMED_MEDIA_PREFIX,
        CLOSED
    }

    private final Reason reason;

    public SabrMediaCorrelationException(Reason reason, String safeMessage) {
        super(safeMessage);
        this.reason = reason;
    }

    public SabrMediaCorrelationException(Reason reason, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
