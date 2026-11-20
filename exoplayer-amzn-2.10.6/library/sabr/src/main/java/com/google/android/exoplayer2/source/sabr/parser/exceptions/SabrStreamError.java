package com.google.android.exoplayer2.source.sabr.parser.exceptions;

public class SabrStreamError extends RuntimeException {
    private final long createdAtMs;

    public SabrStreamError(String msg) {
        super(msg);
        createdAtMs = System.currentTimeMillis();
    }

    public boolean isStale() {
        return System.currentTimeMillis() - createdAtMs > 1_000;
    }
}
