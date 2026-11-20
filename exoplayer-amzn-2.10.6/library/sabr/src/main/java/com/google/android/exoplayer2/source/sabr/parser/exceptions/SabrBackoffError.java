package com.google.android.exoplayer2.source.sabr.parser.exceptions;

/**
 * The server told us to back off, honor it instead of re-requesting
 * immediately - otherwise we burn exactly the waiting period the server asked for.
 */
public class SabrBackoffError extends SabrPropagationError {
    public final int backoffMs;

    public SabrBackoffError(int backoffMs) {
        this(backoffMs, String.format("Server asked to back off for %s ms", backoffMs));
    }

    public SabrBackoffError(int backoffMs, String message) {
        super(message);
        this.backoffMs = backoffMs;
    }
}