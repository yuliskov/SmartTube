package com.google.android.exoplayer2.source.sabr.parser.exceptions;

import androidx.annotation.Nullable;

/**
 * The server sent ReloadPlayerResponse. No media will be sent until the player response is re-fetched
 * with the token (playbackContext.reloadPlaybackContext.reloadPlaybackParams.token).
 */
public class SabrReloadPlayerError extends SabrStreamError {
    @Nullable
    public final String reloadPlaybackToken;

    public SabrReloadPlayerError(@Nullable String reloadPlaybackToken) {
        super("SABR server requested a player response reload");
        this.reloadPlaybackToken = reloadPlaybackToken;
    }
}