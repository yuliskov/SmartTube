package com.google.android.exoplayer2.source.sabr.session;

import java.io.IOException;

/** Typed session failure; messages intentionally omit signed URLs, tokens, cookies, and contexts. */
public final class SabrSessionException extends IOException {
    public enum Category {
        UNAVAILABLE,
        PROTOCOL,
        MEDIA_CORRELATION,
        PROTECTION,
        RELOAD_REQUIRED,
        RELOAD_FAILED,
        BEHIND_LIVE_WINDOW,
        STALE_GENERATION,
        CANCELLED,
        SERVER_ERROR,
        NETWORK,
        UNSUPPORTED_FORMAT,
        ENDED,
        REBOOTSTRAP_REQUIRED
    }

    private final Category category;

    public SabrSessionException(Category category, String safeMessage) {
        super(safeMessage);
        this.category = category;
    }

    public SabrSessionException(Category category, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }
}
