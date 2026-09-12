package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.os.Build;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteApiData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.CopyOnWriteArrayList;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.ResponseException;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocket;
import fi.iki.elonen.NanoWSD.WebSocketFrame;

public class RemoteApiServer extends NanoWSD {
    private static final String TAG = RemoteApiServer.class.getSimpleName();
    private static final String API_VERSION = RemoteApiConstants.API_VERSION;

    private static RemoteApiServer sInstance;

    private final RemoteApiAuthProvider mAuth;
    private final RemoteApiData mApiData;
    private final Context mContext;
    // Cached once: querying Build.MODEL / PackageManager on every request is wasteful.
    private final String mDeviceName;
    private final String mAppVersion;
    private Thread mUdpThread;
    private volatile DatagramSocket mUdpSocket;
    private volatile boolean mRunning;
    private final CopyOnWriteArrayList<RemoteApiWebSocket> mWebSocketClients = new CopyOnWriteArrayList<>();
    private Thread mBroadcastThread;

    // Routes reachable without a token (discovery + pairing). Everything else goes
    // through the auth gate before its handler runs.
    private final Map<String, RouteHandler> mPublicRoutes = new HashMap<>();
    private final Map<String, RouteHandler> mRoutes = new HashMap<>();

    @FunctionalInterface
    private interface RouteHandler {
        Response handle(IHTTPSession session) throws JSONException, IOException;
    }

    public RemoteApiServer(Context context, RemoteApiData apiData) {
        this(context, apiData.getPort(), apiData);
    }

    public RemoteApiServer(Context context, int port, RemoteApiData apiData) {
        super(port);
        mContext = context.getApplicationContext();
        mApiData = apiData;
        mAuth = new RemoteApiAuthProvider(apiData);
        mDeviceName = resolveDeviceName();
        mAppVersion = resolveAppVersion();
        registerRoutes();
    }

    public static synchronized void startRemoteApi(Context context) {
        if (sInstance != null && sInstance.isAlive()) {
            return;
        }
        RemoteApiData data = RemoteApiData.instance(context);
        if (!data.isApiEnabled()) {
            return;
        }
        try {
            sInstance = new RemoteApiServer(context, data);
            sInstance.start();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start Remote API server: %s", e.getMessage());
        }
    }

    public static synchronized void stopRemoteApi(Context context) {
        if (sInstance != null) {
            sInstance.stop();
            sInstance = null;
        }
    }

    public static synchronized RemoteApiServer getInstance() {
        return sInstance;
    }

    public static void pushEvent(String eventType, JSONObject data) {
        RemoteApiServer server = sInstance;
        if (server != null) {
            server.broadcastEvent(eventType, data);
        }
    }

    @Override
    public void start() throws IOException {
        mRunning = true;
        super.start();
        startUdpListener();
        startBroadcastLoop();
        Log.i(TAG, "Remote API server started on port %d", getListeningPort());
    }

    @Override
    public void stop() {
        mRunning = false;
        // Close the UDP socket first: socket.receive() is a blocking call that interrupt() can't wake,
        // so closing the socket is what actually unblocks the listener thread and lets it exit.
        DatagramSocket udpSocket = mUdpSocket;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (mUdpThread != null) {
            mUdpThread.interrupt();
            mUdpThread = null;
        }
        if (mBroadcastThread != null) {
            mBroadcastThread.interrupt();
            mBroadcastThread = null;
        }
        for (RemoteApiWebSocket ws : mWebSocketClients) {
            try { ws.close(WebSocketFrame.CloseCode.NormalClosure, "server shutting down", false); } catch (Exception ignored) {}
        }
        mWebSocketClients.clear();
        try {
            super.stop();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping HTTP server: %s", e.getMessage());
        }
        Log.i(TAG, "Remote API server stopped");
    }

    private void startUdpListener() {
        mUdpThread = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(getListeningPort());
                mUdpSocket = socket;
                socket.setBroadcast(true);
                byte[] buffer = new byte[1024];

                while (mRunning && !Thread.currentThread().isInterrupted()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength()).trim();
                        handleUdpPacket(socket, packet, message);
                    } catch (IOException e) {
                        if (mRunning) {
                            Log.e(TAG, "UDP receive error: %s", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (mRunning) {
                    Log.e(TAG, "UDP listener error: %s", e.getMessage());
                }
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }, "RemoteApi-UdpListener");
        mUdpThread.setDaemon(true);
        mUdpThread.start();
    }

    private void handleUdpPacket(DatagramSocket socket, DatagramPacket incoming, String message) {
        try {
            JSONObject request = new JSONObject(message);
            if ("discover".equals(request.optString("action"))) {
                JSONObject response = new JSONObject();
                response.put("device_name", getDeviceName());
                response.put("device_id", getDeviceId());
                response.put("api_port", getListeningPort());
                response.put("app_version", getAppVersion());
                response.put("api_version", API_VERSION);

                byte[] responseBytes = response.toString().getBytes();
                InetAddress senderAddress = incoming.getAddress();
                DatagramPacket responsePacket = new DatagramPacket(
                        responseBytes, responseBytes.length, senderAddress, incoming.getPort());
                socket.send(responsePacket);
                Log.d(TAG, "UDP discovery response sent to %s:%d", senderAddress.getHostAddress(), incoming.getPort());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid UDP JSON: %s", e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "UDP send error: %s", e.getMessage());
        }
    }



    // ---- WebSocket Support ----

    @Override
    protected WebSocket openWebSocket(NanoHTTPD.IHTTPSession handshake) {
        String uri = handshake.getUri();
        if (!"/ws".equals(uri)) {
            return null;
        }

        Map<String, String> parms = handshake.getParms();
        String token = parms.get("token");
        if (!mApiData.isAllowAllConnections() && (token == null || !mAuth.isTokenValid(token))) {
            Log.w(TAG, "WebSocket connection rejected: invalid or missing token");
            return null;
        }

        RemoteApiWebSocket ws = new RemoteApiWebSocket(handshake);
        mWebSocketClients.add(ws);
        Log.i(TAG, "WebSocket client connected (%d total)", mWebSocketClients.size());
        return ws;
    }

    private void startBroadcastLoop() {
        mBroadcastThread = new Thread(() -> {
            int tick = 0;
            while (mRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                    broadcastState();
                    // NanoHTTPD kills sockets after ~5s without a read. Browser JS can't
                    // send WS pings, so the SERVER must ping; the client's auto-pong
                    // resets the read timeout on both ends.
                    if (++tick % 6 == 0) {
                        pingClients();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Broadcast error: %s", e.getMessage());
                }
            }
        }, "RemoteApi-WsBroadcast");
        mBroadcastThread.setDaemon(true);
        mBroadcastThread.start();
    }

    @FunctionalInterface
    private interface ClientSender {
        void send(RemoteApiWebSocket ws) throws Exception;
    }

    // Single place that walks the client list, prunes anything closed, and drops a
    // client if the send throws. broadcast/ping all funnel through here so the
    // liveness bookkeeping can't drift between them.
    private void forEachOpenClient(ClientSender sender) {
        for (RemoteApiWebSocket ws : mWebSocketClients) {
            if (!ws.isOpen()) {
                mWebSocketClients.remove(ws);
                continue;
            }
            try {
                sender.send(ws);
            } catch (Exception e) {
                mWebSocketClients.remove(ws);
            }
        }
    }

    private void pingClients() {
        byte[] payload = new byte[]{'k', 'a'};
        forEachOpenClient(ws -> ws.ping(payload));
    }

    private void broadcastState() {
        if (mWebSocketClients.isEmpty()) {
            return;
        }
        JSONObject state = RemoteApiBridge.getPlayerState();
        if (state == null) {
            return;
        }
        sendEnvelope("state_update", state);
    }

    public void broadcastEvent(String eventType, JSONObject data) {
        if (mWebSocketClients.isEmpty()) {
            return;
        }
        sendEnvelope(eventType, data);
    }

    // Wrap data in the {type, data} envelope and fan it out to every open client.
    private void sendEnvelope(String type, JSONObject data) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", type);
            msg.put("data", data);
        } catch (JSONException e) {
            return;
        }
        String payload = msg.toString();
        forEachOpenClient(ws -> ws.send(payload));
    }

    private class RemoteApiWebSocket extends WebSocket {
        RemoteApiWebSocket(NanoHTTPD.IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            Log.d(TAG, "WebSocket opened");
            try {
                JSONObject hello = new JSONObject();
                hello.put("type", "hello");
                hello.put("api_version", API_VERSION);
                hello.put("device_name", getDeviceName());
                send(hello.toString());
            } catch (Exception e) {
                Log.e(TAG, "WebSocket hello error: %s", e.getMessage());
            }
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            mWebSocketClients.remove(this);
            Log.d(TAG, "WebSocket closed: %s (%d remaining)", reason, mWebSocketClients.size());
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String text = message.getTextPayload();
            try {
                JSONObject cmd = new JSONObject(text);
                String action = cmd.optString("action");
                handleWebSocketCommand(action, cmd);
            } catch (JSONException e) {
                Log.e(TAG, "Invalid WebSocket message: %s", e.getMessage());
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {}

        @Override
        protected void onException(IOException exception) {
            Log.e(TAG, "WebSocket exception: %s", exception.getMessage());
            mWebSocketClients.remove(this);
        }

        // Send a {type, data} response to this single client.
        private void sendResponse(String type, Object data) {
            try {
                JSONObject resp = new JSONObject();
                resp.put("type", type);
                resp.put("data", data);
                send(resp.toString());
            } catch (Exception e) {
                Log.e(TAG, "WebSocket %s response error: %s", type, e.getMessage());
            }
        }

        private void handleWebSocketCommand(String action, JSONObject cmd) {
            switch (action) {
                case "play":
                    RemoteApiBridge.handlePlay();
                    break;
                case "pause":
                    RemoteApiBridge.handlePause();
                    break;
                case "toggle":
                    RemoteApiBridge.handleToggle();
                    break;
                case "seek":
                    long pos = cmd.optLong("position_ms", 0);
                    RemoteApiBridge.handleSeek(pos);
                    break;
                case "next":
                    RemoteApiBridge.handleNext();
                    break;
                case "previous":
                    RemoteApiBridge.handlePrevious();
                    break;
                case "stop":
                    RemoteApiBridge.handleStop();
                    break;
                case "reload":
                    RemoteApiBridge.handleReload();
                    break;
                case "set_speed":
                    float speed = (float) cmd.optDouble("speed", 1.0);
                    RemoteApiBridge.setSpeed(speed);
                    break;
                case "set_volume":
                    float vol = (float) cmd.optDouble("volume", 1.0);
                    RemoteApiBridge.setVolume(vol);
                    break;
                case "set_video_format":
                    RemoteApiBridge.setVideoFormat(cmd.optString("format_id"));
                    break;
                case "set_audio_format":
                    RemoteApiBridge.setAudioFormat(cmd.optString("format_id"));
                    break;
                case "set_subtitle_format":
                    RemoteApiBridge.setSubtitleFormat(cmd.optString("format_id"));
                    break;
                case "toggle_subtitles":
                    RemoteApiBridge.toggleSubtitles();
                    break;
                case "toggle_mute":
                    RemoteApiBridge.toggleMute();
                    break;
                case "search":
                    RemoteApiBridge.search(cmd.optString("query"));
                    break;
                case "add_to_queue":
                    RemoteApiBridge.addToQueue(cmd.optString("video_id"));
                    break;
                case "play_next":
                    RemoteApiBridge.playNext(cmd.optString("video_id"));
                    break;
                case "remove_from_queue":
                    RemoteApiBridge.removeFromQueue(cmd.optString("video_id"));
                    break;
                case "clear_queue":
                    RemoteApiBridge.clearQueue();
                    break;
                case "shuffle_queue":
                    RemoteApiBridge.shuffleQueue();
                    break;
                case "move_queue_item":
                    if (cmd.has("from") && cmd.has("to")) {
                        RemoteApiBridge.moveQueueItem(cmd.optInt("from"), cmd.optInt("to"));
                    }
                    break;
                case "queue_playlist": {
                    String playlistId = cmd.optString("playlist_id", null);
                    if (playlistId == null || playlistId.isEmpty()) {
                        playlistId = RemoteApiBridge.extractPlaylistIdFromUrl(cmd.optString("playlist_url", null));
                    }
                    if (playlistId != null && !playlistId.isEmpty()) {
                        RemoteApiBridge.addPlaylistToQueue(playlistId, cmd.optBoolean("shuffle", false));
                    }
                    break;
                }
                case "toggle_pip":
                    RemoteApiBridge.togglePip();
                    break;
                case "get_queue":
                    sendResponse("queue_update", RemoteApiBridge.getQueue());
                    break;
                case "theater_power_toggle":
                    getTheater().togglePower();
                    break;
                case "theater_get_state":
                    sendResponse("theater_state", getTheater().getState());
                    break;
                case "get_state":
                    JSONObject state = RemoteApiBridge.getPlayerState();
                    if (state != null) {
                        sendResponse("state_update", state);
                    }
                    break;
            }
        }
    }

    // ---- HTTP routing ----

    // Register a route under the auth gate. Key is "METHOD /path".
    private void route(Method method, String path, RouteHandler handler) {
        mRoutes.put(method.name() + " " + path, handler);
    }

    // Register a fire-and-forget action that just returns {"ok": true}.
    private void action(Method method, String path, Runnable action) {
        route(method, path, session -> handleTransportAction(action));
    }

    private void registerRoutes() {
        // ---- Public (no token required) ----
        mPublicRoutes.put("GET /api/system/ping", session -> handlePing());
        mPublicRoutes.put("GET /api/pair", session -> handlePair());
        mPublicRoutes.put("POST /api/pair/verify", this::handlePairVerify);

        // ---- Player state + transport ----
        route(Method.GET, "/api/player", session -> handleGetPlayerState());
        action(Method.POST, "/api/player/play", RemoteApiBridge::handlePlay);
        action(Method.POST, "/api/player/pause", RemoteApiBridge::handlePause);
        action(Method.POST, "/api/player/toggle", RemoteApiBridge::handleToggle);
        route(Method.POST, "/api/player/seek", this::handleSeek);
        action(Method.POST, "/api/player/next", RemoteApiBridge::handleNext);
        action(Method.POST, "/api/player/previous", RemoteApiBridge::handlePrevious);
        action(Method.POST, "/api/player/stop", RemoteApiBridge::handleStop);
        action(Method.POST, "/api/player/reload", RemoteApiBridge::handleReload);

        // ---- Playback settings ----
        route(Method.GET, "/api/player/speed", session -> handleGetFloat("speed", RemoteApiBridge.getSpeed()));
        route(Method.PUT, "/api/player/speed", session -> handleSetFloat(session, "speed", RemoteApiBridge::setSpeed));
        route(Method.GET, "/api/player/volume", session -> handleGetFloat("volume", RemoteApiBridge.getVolume()));
        route(Method.PUT, "/api/player/volume", session -> handleSetFloat(session, "volume", RemoteApiBridge::setVolume));
        route(Method.GET, "/api/player/pitch", session -> handleGetFloat("pitch", RemoteApiBridge.getPitch()));
        route(Method.PUT, "/api/player/pitch", session -> handleSetFloat(session, "pitch", RemoteApiBridge::setPitch));
        route(Method.GET, "/api/player/chapters", session -> handleGetChapters());

        // ---- Tracks ----
        route(Method.GET, "/api/player/formats/video", session -> handleGetFormats(RemoteApiBridge.getVideoFormats()));
        route(Method.GET, "/api/player/formats/audio", session -> handleGetFormats(RemoteApiBridge.getAudioFormats()));
        route(Method.GET, "/api/player/formats/subtitle", session -> handleGetFormats(RemoteApiBridge.getSubtitleFormats()));
        route(Method.GET, "/api/player/formats/selected", session -> handleGetSelectedTracks());
        route(Method.PUT, "/api/player/formats/video", session -> handleSetFormat(session, RemoteApiBridge::setVideoFormat));
        route(Method.PUT, "/api/player/formats/audio", session -> handleSetFormat(session, RemoteApiBridge::setAudioFormat));
        route(Method.PUT, "/api/player/formats/subtitle", session -> handleSetFormat(session, RemoteApiBridge::setSubtitleFormat));

        // ---- Video transform ----
        route(Method.GET, "/api/player/video/resize", session -> handleGetInt("resize_mode", RemoteApiBridge.getResizeMode()));
        route(Method.PUT, "/api/player/video/resize", session -> handleSetInt(session, "mode", RemoteApiBridge::setResizeMode));
        route(Method.GET, "/api/player/video/zoom", session -> handleGetInt("zoom_percents", RemoteApiBridge.getZoom()));
        route(Method.PUT, "/api/player/video/zoom", session -> handleSetInt(session, "zoom", RemoteApiBridge::setZoom));
        route(Method.GET, "/api/player/video/rotation", session -> handleGetInt("rotation_angle", RemoteApiBridge.getRotation()));
        route(Method.PUT, "/api/player/video/rotation", session -> handleSetInt(session, "angle", RemoteApiBridge::setRotation));
        route(Method.GET, "/api/player/video/flip", session -> handleGetBool("flip_enabled", RemoteApiBridge.getFlip()));
        route(Method.PUT, "/api/player/video/flip", session -> handleSetBool(session, "enabled", RemoteApiBridge::setFlip));

        // ---- Subtitles (closed captions) ----
        route(Method.POST, "/api/player/subtitle/toggle", session -> handleTransportAction(RemoteApiBridge::toggleSubtitles));
        route(Method.PUT, "/api/player/subtitle", session -> handleSetBool(session, "enabled", RemoteApiBridge::setSubtitlesEnabled));
        route(Method.GET, "/api/player/subtitle", session -> handleGetBool("enabled", RemoteApiBridge.areSubtitlesOn()));

        // ---- Mute ----
        action(Method.POST, "/api/player/mute/toggle", RemoteApiBridge::toggleMute);
        route(Method.GET, "/api/player/mute", session -> handleGetBool("muted", RemoteApiBridge.isMuted()));

        // ---- Picture-in-Picture ----
        route(Method.GET, "/api/player/pip", session -> handleGetBool("active", RemoteApiBridge.isPipActive()));
        action(Method.POST, "/api/player/pip/toggle", RemoteApiBridge::togglePip);
        action(Method.POST, "/api/player/pip", RemoteApiBridge::togglePip); // back-compat alias

        // ---- Content ----
        route(Method.POST, "/api/content/search", session -> handleSetString(session, "query", RemoteApiBridge::search));
        route(Method.GET, "/api/content/search/results", this::handleSearchResults);
        route(Method.POST, "/api/content/open", this::handleContentOpen);
        route(Method.GET, "/api/content/suggestions", session -> handleGetSuggestions());
        route(Method.GET, "/api/content/recommended", session -> handleGetRecommended());

        // ---- Queue ----
        route(Method.GET, "/api/player/queue", session -> handleGetQueue());
        route(Method.POST, "/api/player/queue", session -> handleSetString(session, "video_id", RemoteApiBridge::addToQueue));
        route(Method.POST, "/api/player/queue/next", session -> handleSetString(session, "video_id", RemoteApiBridge::playNext));
        route(Method.DELETE, "/api/player/queue", session -> handleSetString(session, "video_id", RemoteApiBridge::removeFromQueue));
        action(Method.POST, "/api/player/queue/clear", RemoteApiBridge::clearQueue);
        action(Method.POST, "/api/player/queue/shuffle", RemoteApiBridge::shuffleQueue);
        route(Method.POST, "/api/player/queue/move", this::handleMoveQueueItem);
        route(Method.POST, "/api/player/queue/playlist", this::handleQueuePlaylist);

        // ---- Home Theater ----
        route(Method.GET, "/api/theater", session -> handleGetTheaterState());
        route(Method.GET, "/api/theater/volume", session -> handleGetTheaterVolume());
        route(Method.PUT, "/api/theater/volume", this::handleSetTheaterVolume);
        action(Method.POST, "/api/theater/volume/up", () -> getTheater().volumeUp());
        action(Method.POST, "/api/theater/volume/down", () -> getTheater().volumeDown());
        action(Method.POST, "/api/theater/mute/toggle", () -> getTheater().toggleMute());
        action(Method.POST, "/api/theater/power/toggle", () -> getTheater().togglePower());
        route(Method.GET, "/api/theater/refresh", session -> handleGetTheaterState());

        // ---- System ----
        route(Method.GET, "/api/system/dpad", this::handleDpad);
        route(Method.POST, "/api/system/voice", this::handleVoice);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (isWebsocketRequested(session)) {
            return super.serve(session);
        }

        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> headers = session.getHeaders();

        if (Method.OPTIONS.equals(method)) {
            return corsResponse(newFixedLengthResponse(Response.Status.OK, "application/json", "{}"));
        }

        String path = uri.endsWith("/") && uri.length() > 1 ? uri.substring(0, uri.length() - 1) : uri;
        String key = method.name() + " " + path;

        try {
            RouteHandler publicRoute = mPublicRoutes.get(key);
            if (publicRoute != null) {
                return publicRoute.handle(session);
            }

            // When "allow all connections" is on, the LAN is trusted and no token is required.
            if (!mApiData.isAllowAllConnections()) {
                String token = extractBearerToken(headers.get("authorization"));
                if (!mAuth.isTokenValid(token)) {
                    return errorResponse(Response.Status.UNAUTHORIZED, 401, "Unauthorized");
                }
            }

            RouteHandler route = mRoutes.get(key);
            if (route != null) {
                return route.handle(session);
            }

            // Dynamic: play a suggestion by index or video id.
            if (Method.POST.equals(method) && path.startsWith("/api/content/suggestions/")) {
                return handlePlaySuggestion(path.substring("/api/content/suggestions/".length()));
            }

            return errorResponse(Response.Status.NOT_FOUND, 404, "Not found");

        } catch (JSONException e) {
            Log.e(TAG, "JSON parse error: %s", e.getMessage());
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Invalid JSON");
        } catch (Exception e) {
            Log.e(TAG, "Request error: %s", e.getMessage());
            return errorResponse(Response.Status.INTERNAL_ERROR, 503, "Internal error");
        }
    }

    private Response handlePing() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("status", "ok");
        body.put("device_name", getDeviceName());
        body.put("app_version", getAppVersion());
        body.put("api_version", API_VERSION);
        // Lets clients skip the pairing flow when the API is open on the LAN.
        body.put("pairing_required", !mApiData.isAllowAllConnections());
        return corsResponse(jsonResponse(body));
    }

    private Response handlePair() throws JSONException {
        String code = mAuth.generatePairingCode();
        JSONObject body = new JSONObject();
        body.put("code", code);
        body.put("expires_in", 300);
        return corsResponse(jsonResponse(body));
    }

    private Response handlePairVerify(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        String code = reqBody.getString("code");
        String clientIp = session.getRemoteIpAddress();

        if (mAuth.isRateLimited(clientIp)) {
            return errorResponse(Response.Status.TOO_MANY_REQUESTS, 429, "Too many attempts, try again later");
        }

        String token = mAuth.verifyPairingCode(code, clientIp);
        if (token == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, 401, "Invalid pairing code");
        }

        JSONObject body = new JSONObject();
        body.put("token", token);
        body.put("device_name", getDeviceName());
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetPlayerState() throws JSONException {
        JSONObject state = RemoteApiBridge.getPlayerState();
        if (state == null) {
            return errorResponse(Response.Status.SERVICE_UNAVAILABLE, 503, "Player not available");
        }
        return corsResponse(jsonResponse(state));
    }

    private Response handleTransportAction(Runnable action) throws JSONException {
        action.run();
        return okResponse();
    }

    private Response handleSeek(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        long positionMs = reqBody.getLong("position_ms");

        JSONObject result = RemoteApiBridge.handleSeek(positionMs);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        if (result != null && result.has("position_ms")) {
            body.put("position_ms", result.getLong("position_ms"));
        } else {
            body.put("position_ms", positionMs);
        }
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetFloat(String key, float value) throws JSONException {
        JSONObject body = new JSONObject();
        body.put(key, value);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSetFloat(IHTTPSession session, String key, FloatSetter setter) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        float value = (float) reqBody.getDouble(key);
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Invalid value for " + key);
        }
        setter.set(value);
        return okResponse();
    }

    private Response handleGetInt(String key, int value) throws JSONException {
        JSONObject body = new JSONObject();
        body.put(key, value);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSetInt(IHTTPSession session, String key, IntSetter setter) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        int value = reqBody.getInt(key);
        setter.set(value);
        return okResponse();
    }

    private Response handleGetBool(String key, boolean value) throws JSONException {
        JSONObject body = new JSONObject();
        body.put(key, value);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSetBool(IHTTPSession session, String key, BoolSetter setter) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        boolean value = reqBody.getBoolean(key);
        setter.set(value);
        return okResponse();
    }

    private Response handleGetChapters() throws JSONException {
        return corsResponse(jsonResponse(orEmptyArray(RemoteApiBridge.getChapters())));
    }

    private Response handleGetFormats(JSONArray formats) throws JSONException {
        return corsResponse(jsonResponse(orEmptyArray(formats)));
    }

    private Response handleGetSelectedTracks() throws JSONException {
        JSONObject tracks = RemoteApiBridge.getSelectedTracks();
        if (tracks == null) {
            tracks = new JSONObject();
        }
        return corsResponse(jsonResponse(tracks));
    }

    private Response handleSetFormat(IHTTPSession session, FormatSetter setter) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        String formatId = reqBody.getString("format_id");
        setter.set(formatId);
        return okResponse();
    }

    private Response handleContentOpen(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);

        String url = reqBody.optString("url", null);
        String videoId = reqBody.optString("video_id", null);
        Long positionMs = reqBody.has("position_ms") ? reqBody.getLong("position_ms") : null;
        String playlistId = reqBody.optString("playlist_id", null);
        Integer playlistIndex = reqBody.has("playlist_index") ? reqBody.getInt("playlist_index") : null;

        RemoteApiBridge.openVideo(url, videoId, positionMs, playlistId, playlistIndex);

        return okResponse();
    }

    private Response handleGetSuggestions() throws JSONException {
        return corsResponse(jsonResponse(orEmptyArray(RemoteApiBridge.getSuggestions())));
    }

    private Response handleGetRecommended() throws JSONException {
        // Blocking network fetch (cached in the bridge) — NanoHTTPD worker thread, so OK.
        return corsResponse(jsonResponse(orEmptyArray(RemoteApiBridge.getRecommended())));
    }

    private Response handlePlaySuggestion(String indexStr) throws JSONException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            // Not a number — treat it as a video ID (exactly 11 chars, e.g. dQw4w9WgXcQ).
            // ID-based play is preferred: indexes go stale when the list refreshes.
            if (indexStr.matches("[A-Za-z0-9_-]{11}")) {
                RemoteApiBridge.playSuggestionById(indexStr);
                return okResponse();
            }
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Invalid suggestion index");
        }

        RemoteApiBridge.playSuggestion(index);
        return okResponse();
    }

    private Response handleSearchResults(IHTTPSession session) throws JSONException {
        String query = session.getParms().get("query");
        if (query == null || query.isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Missing query parameter");
        }

        int limit = 20;
        String limitStr = session.getParms().get("limit");
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                return errorResponse(Response.Status.BAD_REQUEST, 400, "Invalid limit parameter");
            }
        }
        limit = Math.max(1, Math.min(limit, 50));

        // Blocking network fetch — NanoHTTPD worker thread, so OK.
        return corsResponse(jsonResponse(orEmptyArray(RemoteApiBridge.searchResults(query, limit))));
    }

    private Response handleGetQueue() throws JSONException {
        return corsResponse(jsonResponse(RemoteApiBridge.getQueue()));
    }

    private Response handleMoveQueueItem(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        if (!reqBody.has("from") || !reqBody.has("to")) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Missing from/to");
        }
        int from = reqBody.getInt("from");
        int to = reqBody.getInt("to");

        // Reject out-of-range indices up front: Playlist.move() would otherwise throw.
        int size = RemoteApiBridge.getQueue().length();
        if (from < 0 || from >= size || to < 0 || to >= size) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Queue index out of range");
        }

        RemoteApiBridge.moveQueueItem(from, to);
        return okResponse();
    }

    private Response handleQueuePlaylist(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);

        String playlistId = reqBody.optString("playlist_id", null);
        if (playlistId == null || playlistId.isEmpty()) {
            String playlistUrl = reqBody.optString("playlist_url", null);
            playlistId = RemoteApiBridge.extractPlaylistIdFromUrl(playlistUrl);
        }
        if (playlistId == null || playlistId.isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Missing playlist_id or playlist_url");
        }
        boolean shuffle = reqBody.optBoolean("shuffle", false);

        // Blocking playlist fetch happens on this NanoHTTPD worker thread, then the
        // queue mutation is posted to the main thread — return 202 (accepted) since
        // the bulk add completes asynchronously from the client's perspective.
        final String resolvedPlaylistId = playlistId;
        RemoteApiBridge.addPlaylistToQueue(resolvedPlaylistId, shuffle);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        body.put("playlist_id", resolvedPlaylistId);
        Response response = jsonResponse(body);
        response.setStatus(Response.Status.ACCEPTED);
        return corsResponse(response);
    }

    private Response handleDpad(IHTTPSession session) throws JSONException {
        String key = session.getParms().get("key");
        if (key == null || key.isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Missing key parameter");
        }

        RemoteApiBridge.dpad(key);
        return okResponse();
    }

    // ---- Home Theater Handlers ----

    private HomeTheaterController getTheater() {
        return HomeTheaterController.instance(mContext);
    }

    private Response handleGetTheaterState() throws JSONException {
        return corsResponse(jsonResponse(getTheater().getState()));
    }

    private Response handleGetTheaterVolume() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("volume", getTheater().getVolume());
        body.put("muted", getTheater().isMuted());
        return corsResponse(jsonResponse(body));
    }

    private Response handleSetTheaterVolume(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        int volume = reqBody.getInt("volume");
        getTheater().setVolume(volume);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        body.put("volume", volume);
        return corsResponse(jsonResponse(body));
    }

    private Response handleVoice(IHTTPSession session) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        String action = reqBody.getString("action");

        RemoteApiBridge.voice(action);
        return okResponse();
    }

    // ---- Response helpers ----

    // The {"ok": true} acknowledgement shared by every fire-and-forget endpoint.
    private Response okResponse() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private static JSONArray orEmptyArray(JSONArray array) {
        return array != null ? array : new JSONArray();
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private JSONObject parseBody(IHTTPSession session) throws JSONException, IOException {
        return new JSONObject(readBody(session));
    }

    private String readBody(IHTTPSession session) throws IOException {
        Map<String, String> bodyMap = new HashMap<>();
        try {
            session.parseBody(bodyMap);
        } catch (ResponseException e) {
            throw new IOException("Failed to parse body", e);
        }
        // POST bodies arrive in "postData"; PUT bodies are written by NanoHTTPD to a
        // temp file whose path is stored under "content". Without reading that file,
        // every PUT (volume, speed, formats…) parses as "{}" and fails with 400.
        String body = bodyMap.get("postData");
        if (body == null) {
            String tempFilePath = bodyMap.get("content");
            if (tempFilePath != null) {
                body = readFile(tempFilePath);
            }
        }
        if (body == null || body.isEmpty()) {
            body = "{}";
        }
        return body;
    }

    private static String readFile(String path) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(path);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private Response jsonResponse(JSONObject json) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
    }

    private Response jsonResponse(JSONArray json) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
    }

    private Response errorResponse(Response.Status status, int code, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            JSONObject body = new JSONObject();
            body.put("error", error);
            Response response = newFixedLengthResponse(status, "application/json", body.toString());
            return corsResponse(response);
        } catch (JSONException e) {
            return corsResponse(newFixedLengthResponse(status, "application/json",
                    "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}"));
        }
    }

    private Response corsResponse(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        return response;
    }

    // ---- Device info (cached) ----

    private String resolveDeviceName() {
        String name = Build.MODEL;
        if (name == null || name.isEmpty()) {
            name = "SmartTube Device";
        }
        return name;
    }

    private String resolveAppVersion() {
        try {
            return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return API_VERSION;
        }
    }

    private String getDeviceName() {
        return mDeviceName;
    }

    private String getDeviceId() {
        // Stable per-install UUID generated/persisted by RemoteApiData (NOT the literal
        // Settings.Secure.ANDROID_ID key string the old code returned by mistake).
        return mApiData.getDeviceId();
    }

    private String getAppVersion() {
        return mAppVersion;
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    @FunctionalInterface
    private interface BoolSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    private interface FormatSetter {
        void set(String formatId);
    }

    @FunctionalInterface
    private interface StringSetter {
        void set(String value);
    }

    private Response handleSetString(IHTTPSession session, String jsonKey, StringSetter setter) throws JSONException, IOException {
        JSONObject reqBody = parseBody(session);
        String value = reqBody.getString(jsonKey);
        setter.set(value);
        return okResponse();
    }
}
