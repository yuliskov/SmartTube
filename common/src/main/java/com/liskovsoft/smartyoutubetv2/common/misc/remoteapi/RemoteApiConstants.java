package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

/**
 * Constants shared across the remote control API (server, prefs, auth).
 * Single source of truth so the default port / protocol version can't drift
 * between {@link RemoteApiServer} and {@link com.liskovsoft.smartyoutubetv2.common.prefs.RemoteApiData}.
 */
public final class RemoteApiConstants {
    private RemoteApiConstants() {}

    /** Default TCP (HTTP/WebSocket) and UDP (discovery) port for the remote API server. */
    public static final int DEFAULT_PORT = 8497;

    /** Wire protocol version reported via UDP discovery, GET /api/system/ping and the WS hello. */
    public static final String API_VERSION = "1";
}
