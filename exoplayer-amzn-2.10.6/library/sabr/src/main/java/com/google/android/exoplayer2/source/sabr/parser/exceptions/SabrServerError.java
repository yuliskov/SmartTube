package com.google.android.exoplayer2.source.sabr.parser.exceptions;

import androidx.annotation.Nullable;

/**
 * The server rejected the request with a SabrError part (e.g. "sabr.no_video_selected").<br/>
 * Re-sending the same request gets the same answer, so this must reach ErrorFixerController
 * instead of being swallowed by the extractor (which made the player re-request in a tight loop).
 */
public class SabrServerError extends SabrPropagationError {
    @Nullable
    public final String type;
    public final int action;

    public SabrServerError(@Nullable String type, int action) {
        super(String.format("SABR server error: %s (action %s)", type, action));
        this.type = type;
        this.action = action;
    }
}
