package com.google.android.exoplayer2.source.sabr.parser.parts;

public class MediaSeekSabrPart implements SabrPart {
    // Lets the consumer know the media sequence for a format may change
    public enum Reason {
        UNKNOWN,
        SERVER_SEEK,         // SABR_SEEK from server
        CONSUMED_SEEK        // Seeking as next fragment is already buffered
    }
}
