package com.google.android.exoplayer2.source.sabr.parser.parts;

public class RefreshPlayerResponseSabrPart implements SabrPart {
    public enum Reason {
        UNKNOWN,
        SABR_URL_EXPIRY,
        SABR_RELOAD_PLAYER_RESPONSE
    }
}
