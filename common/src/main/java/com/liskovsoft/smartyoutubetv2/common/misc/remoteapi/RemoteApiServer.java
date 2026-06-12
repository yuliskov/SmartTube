package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

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
    private static final int DEFAULT_PORT = 8497;
    private static final String API_VERSION = "1";

    private static RemoteApiServer sInstance;

    private final RemoteApiAuthProvider mAuth;
    private final RemoteApiData mApiData;
    private final Context mContext;
    private Thread mUdpThread;
    private volatile DatagramSocket mUdpSocket;
    private volatile boolean mRunning;
    private final CopyOnWriteArrayList<RemoteApiWebSocket> mWebSocketClients = new CopyOnWriteArrayList<>();
    private Thread mBroadcastThread;

    public RemoteApiServer(Context context, RemoteApiData apiData) {
        this(context, apiData.getPort(), apiData);
    }

    public RemoteApiServer(Context context, int port, RemoteApiData apiData) {
        super(port);
        mContext = context.getApplicationContext();
        mApiData = apiData;
        mAuth = new RemoteApiAuthProvider(apiData);
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

    private void pingClients() {
        byte[] payload = new byte[]{'k', 'a'};
        for (RemoteApiWebSocket ws : mWebSocketClients) {
            if (ws.isOpen()) {
                try {
                    ws.ping(payload);
                } catch (IOException e) {
                    mWebSocketClients.remove(ws);
                }
            } else {
                mWebSocketClients.remove(ws);
            }
        }
    }

    private void broadcastState() {
        if (mWebSocketClients.isEmpty()) {
            return;
        }

        JSONObject state = RemoteApiBridge.getPlayerState();
        if (state == null) {
            return;
        }

        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "state_update");
            msg.put("data", state);
        } catch (JSONException e) {
            return;
        }

        String payload = msg.toString();
        for (RemoteApiWebSocket ws : mWebSocketClients) {
            if (ws.isOpen()) {
                try {
                    ws.send(payload);
                } catch (Exception e) {
                    Log.e(TAG, "WebSocket send error: %s", e.getMessage());
                    mWebSocketClients.remove(ws);
                }
            } else {
                mWebSocketClients.remove(ws);
            }
        }
    }

    public void broadcastEvent(String eventType, JSONObject data) {
        if (mWebSocketClients.isEmpty()) {
            return;
        }

        JSONObject msg = new JSONObject();
        try {
            msg.put("type", eventType);
            msg.put("data", data);
        } catch (JSONException e) {
            return;
        }

        String payload = msg.toString();
        for (RemoteApiWebSocket ws : mWebSocketClients) {
            if (ws.isOpen()) {
                try {
                    ws.send(payload);
                } catch (Exception e) {
                    mWebSocketClients.remove(ws);
                }
            } else {
                mWebSocketClients.remove(ws);
            }
        }
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
                    JSONArray queue = RemoteApiBridge.getQueue();
                    try {
                        JSONObject queueResp = new JSONObject();
                        queueResp.put("type", "queue_update");
                        queueResp.put("data", queue);
                        send(queueResp.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "WebSocket queue response error: %s", e.getMessage());
                    }
                    break;
                case "theater_power_toggle":
                    getTheater().togglePower();
                    break;
                case "theater_get_state":
                    try {
                        JSONObject theaterResp = new JSONObject();
                        theaterResp.put("type", "theater_state");
                        theaterResp.put("data", getTheater().getState());
                        send(theaterResp.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "WebSocket theater state error: %s", e.getMessage());
                    }
                    break;
                case "get_state":
                    JSONObject state = RemoteApiBridge.getPlayerState();
                    if (state != null) {
                        JSONObject resp = new JSONObject();
                        try {
                            resp.put("type", "state_update");
                            resp.put("data", state);
                            send(resp.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "WebSocket state response error: %s", e.getMessage());
                        }
                    }
                    break;
            }
        }
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

        try {
            if (Method.GET.equals(method) && "/api/system/ping".equals(path)) {
                return handlePing();
            }

            if (Method.GET.equals(method) && "/api/pair".equals(path)) {
                return handlePair();
            }

            if (Method.POST.equals(method) && "/api/pair/verify".equals(path)) {
                return handlePairVerify(session);
            }

            // When "allow all connections" is on, the LAN is trusted and no token is required.
            if (!mApiData.isAllowAllConnections()) {
                String authHeader = headers.get("authorization");
                String token = extractBearerToken(authHeader);
                if (!mAuth.isTokenValid(token)) {
                    return errorResponse(Response.Status.UNAUTHORIZED, 401, "Unauthorized");
                }
            }

            if (Method.GET.equals(method) && "/api/player".equals(path)) {
                return handleGetPlayerState();
            }

            if (Method.POST.equals(method) && "/api/player/play".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handlePlay());
            }

            if (Method.POST.equals(method) && "/api/player/pause".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handlePause());
            }

            if (Method.POST.equals(method) && "/api/player/toggle".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handleToggle());
            }

            if (Method.POST.equals(method) && "/api/player/seek".equals(path)) {
                return handleSeek(session);
            }

            if (Method.POST.equals(method) && "/api/player/next".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handleNext());
            }

            if (Method.POST.equals(method) && "/api/player/previous".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handlePrevious());
            }

            if (Method.POST.equals(method) && "/api/player/stop".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handleStop());
            }

            if (Method.POST.equals(method) && "/api/player/reload".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.handleReload());
            }

            if (Method.GET.equals(method) && "/api/player/speed".equals(path)) {
                return handleGetFloat("speed", RemoteApiBridge.getSpeed());
            }

            if (Method.PUT.equals(method) && "/api/player/speed".equals(path)) {
                return handleSetFloat(session, "speed", RemoteApiBridge::setSpeed);
            }

            if (Method.GET.equals(method) && "/api/player/volume".equals(path)) {
                return handleGetFloat("volume", RemoteApiBridge.getVolume());
            }

            if (Method.PUT.equals(method) && "/api/player/volume".equals(path)) {
                return handleSetFloat(session, "volume", RemoteApiBridge::setVolume);
            }

            if (Method.GET.equals(method) && "/api/player/pitch".equals(path)) {
                return handleGetFloat("pitch", RemoteApiBridge.getPitch());
            }

            if (Method.PUT.equals(method) && "/api/player/pitch".equals(path)) {
                return handleSetFloat(session, "pitch", RemoteApiBridge::setPitch);
            }

            if (Method.GET.equals(method) && "/api/player/formats/video".equals(path)) {
                return handleGetFormats(RemoteApiBridge.getVideoFormats());
            }

            if (Method.GET.equals(method) && "/api/player/formats/audio".equals(path)) {
                return handleGetFormats(RemoteApiBridge.getAudioFormats());
            }

            if (Method.GET.equals(method) && "/api/player/formats/subtitle".equals(path)) {
                return handleGetFormats(RemoteApiBridge.getSubtitleFormats());
            }

            if (Method.GET.equals(method) && "/api/player/formats/selected".equals(path)) {
                return handleGetSelectedTracks();
            }

            if (Method.PUT.equals(method) && "/api/player/formats/video".equals(path)) {
                return handleSetFormat(session, RemoteApiBridge::setVideoFormat);
            }

            if (Method.PUT.equals(method) && "/api/player/formats/audio".equals(path)) {
                return handleSetFormat(session, RemoteApiBridge::setAudioFormat);
            }

            if (Method.PUT.equals(method) && "/api/player/formats/subtitle".equals(path)) {
                return handleSetFormat(session, RemoteApiBridge::setSubtitleFormat);
            }

            if (Method.GET.equals(method) && "/api/player/video/resize".equals(path)) {
                return handleGetInt("resize_mode", RemoteApiBridge.getResizeMode());
            }

            if (Method.PUT.equals(method) && "/api/player/video/resize".equals(path)) {
                return handleSetInt(session, "mode", RemoteApiBridge::setResizeMode);
            }

            if (Method.GET.equals(method) && "/api/player/video/zoom".equals(path)) {
                return handleGetInt("zoom_percents", RemoteApiBridge.getZoom());
            }

            if (Method.PUT.equals(method) && "/api/player/video/zoom".equals(path)) {
                return handleSetInt(session, "zoom", RemoteApiBridge::setZoom);
            }

            if (Method.GET.equals(method) && "/api/player/video/rotation".equals(path)) {
                return handleGetInt("rotation_angle", RemoteApiBridge.getRotation());
            }

            if (Method.PUT.equals(method) && "/api/player/video/rotation".equals(path)) {
                return handleSetInt(session, "angle", RemoteApiBridge::setRotation);
            }

            if (Method.GET.equals(method) && "/api/player/video/flip".equals(path)) {
                return handleGetBool("flip_enabled", RemoteApiBridge.getFlip());
            }

            if (Method.PUT.equals(method) && "/api/player/video/flip".equals(path)) {
                return handleSetBool(session, "enabled", RemoteApiBridge::setFlip);
            }

            if (Method.POST.equals(method) && "/api/player/subtitle/toggle".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.toggleSubtitles());
            }

            if (Method.GET.equals(method) && "/api/player/subtitle".equals(path)) {
                return handleGetBool("enabled", RemoteApiBridge.areSubtitlesOn());
            }

            if (Method.POST.equals(method) && "/api/player/mute/toggle".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.toggleMute());
            }

            if (Method.GET.equals(method) && "/api/player/mute".equals(path)) {
                return handleGetBool("muted", RemoteApiBridge.isMuted());
            }

            if (Method.POST.equals(method) && "/api/content/search".equals(path)) {
                return handleSearch(session);
            }

            if (Method.GET.equals(method) && "/api/player/queue".equals(path)) {
                return handleGetQueue();
            }

            if (Method.POST.equals(method) && "/api/player/queue".equals(path)) {
                return handleAddToQueue(session);
            }

            if (Method.POST.equals(method) && "/api/player/queue/next".equals(path)) {
                return handlePlayNext(session);
            }

            if (Method.DELETE.equals(method) && "/api/player/queue".equals(path)) {
                return handleRemoveFromQueue(session);
            }

            if (Method.POST.equals(method) && "/api/player/queue/clear".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.clearQueue());
            }

            if (Method.POST.equals(method) && "/api/player/queue/shuffle".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.shuffleQueue());
            }

            if (Method.POST.equals(method) && "/api/player/queue/move".equals(path)) {
                return handleMoveQueueItem(session);
            }

            if (Method.POST.equals(method) && "/api/player/queue/playlist".equals(path)) {
                return handleQueuePlaylist(session);
            }

            if (Method.POST.equals(method) && "/api/player/pip".equals(path)) {
                return handleTransportAction(() -> RemoteApiBridge.togglePip());
            }

            if (Method.POST.equals(method) && "/api/content/open".equals(path)) {
                return handleContentOpen(session);
            }

            if (Method.GET.equals(method) && "/api/content/suggestions".equals(path)) {
                return handleGetSuggestions();
            }

            if (Method.GET.equals(method) && "/api/content/recommended".equals(path)) {
                return handleGetRecommended();
            }

            if (path.startsWith("/api/content/suggestions/")) {
                String indexStr = path.substring("/api/content/suggestions/".length());
                if (Method.POST.equals(method)) {
                    return handlePlaySuggestion(indexStr);
                }
            }

            if (Method.GET.equals(method) && "/api/theater".equals(path)) {
                return handleGetTheaterState();
            }

            if (Method.GET.equals(method) && "/api/theater/volume".equals(path)) {
                return handleGetTheaterVolume();
            }

            if (Method.PUT.equals(method) && "/api/theater/volume".equals(path)) {
                return handleSetTheaterVolume(session);
            }

            if (Method.POST.equals(method) && "/api/theater/volume/up".equals(path)) {
                return handleTransportAction(() -> getTheater().volumeUp());
            }

            if (Method.POST.equals(method) && "/api/theater/volume/down".equals(path)) {
                return handleTransportAction(() -> getTheater().volumeDown());
            }

            if (Method.POST.equals(method) && "/api/theater/mute/toggle".equals(path)) {
                return handleTransportAction(() -> getTheater().toggleMute());
            }

            if (Method.POST.equals(method) && "/api/theater/power/toggle".equals(path)) {
                return handleTransportAction(() -> getTheater().togglePower());
            }

            if (Method.GET.equals(method) && "/api/theater/refresh".equals(path)) {
                return handleGetTheaterState();
            }

            if (Method.GET.equals(method) && "/api/system/dpad".equals(path)) {
                return handleDpad(session);
            }

            if (Method.POST.equals(method) && "/api/system/voice".equals(path)) {
                return handleVoice(session);
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
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
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
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSeek(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
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
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        float value = (float) reqBody.getDouble(key);
        setter.set(value);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetInt(String key, int value) throws JSONException {
        JSONObject body = new JSONObject();
        body.put(key, value);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSetInt(IHTTPSession session, String key, IntSetter setter) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        int value = reqBody.getInt(key);
        setter.set(value);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetBool(String key, boolean value) throws JSONException {
        JSONObject body = new JSONObject();
        body.put(key, value);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSetBool(IHTTPSession session, String key, BoolSetter setter) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        boolean value = reqBody.getBoolean(key);
        setter.set(value);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetFormats(JSONArray formats) throws JSONException {
        if (formats == null) {
            formats = new JSONArray();
        }
        return corsResponse(jsonResponse(formats));
    }

    private Response handleGetSelectedTracks() throws JSONException {
        JSONObject tracks = RemoteApiBridge.getSelectedTracks();
        if (tracks == null) {
            tracks = new JSONObject();
        }
        return corsResponse(jsonResponse(tracks));
    }

    private Response handleSetFormat(IHTTPSession session, FormatSetter setter) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String formatId = reqBody.getString("format_id");
        setter.set(formatId);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleContentOpen(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);

        String url = reqBody.optString("url", null);
        String videoId = reqBody.optString("video_id", null);
        Long positionMs = reqBody.has("position_ms") ? reqBody.getLong("position_ms") : null;
        String playlistId = reqBody.optString("playlist_id", null);
        Integer playlistIndex = reqBody.has("playlist_index") ? reqBody.getInt("playlist_index") : null;

        RemoteApiBridge.openVideo(url, videoId, positionMs, playlistId, playlistIndex);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetSuggestions() throws JSONException {
        JSONArray suggestions = RemoteApiBridge.getSuggestions();
        if (suggestions == null) {
            suggestions = new JSONArray();
        }
        return corsResponse(jsonResponse(suggestions));
    }

    private Response handleGetRecommended() {
        // Blocking network fetch (cached in the bridge) — NanoHTTPD worker thread, so OK.
        JSONArray recommended = RemoteApiBridge.getRecommended();
        if (recommended == null) {
            recommended = new JSONArray();
        }
        return corsResponse(jsonResponse(recommended));
    }

    private Response handlePlaySuggestion(String indexStr) throws JSONException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            // Not a number — treat it as a video ID (11 chars, e.g. dQw4w9WgXcQ).
            // ID-based play is preferred: indexes go stale when the list refreshes.
            if (indexStr.matches("[A-Za-z0-9_-]{6,16}")) {
                RemoteApiBridge.playSuggestionById(indexStr);
                JSONObject ok = new JSONObject();
                ok.put("ok", true);
                return corsResponse(jsonResponse(ok));
            }
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Invalid suggestion index");
        }

        RemoteApiBridge.playSuggestion(index);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleSearch(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String query = reqBody.getString("query");

        RemoteApiBridge.search(query);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleGetQueue() throws JSONException {
        JSONArray queue = RemoteApiBridge.getQueue();
        return corsResponse(jsonResponse(queue));
    }

    private Response handleAddToQueue(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String videoId = reqBody.getString("video_id");

        RemoteApiBridge.addToQueue(videoId);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handlePlayNext(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String videoId = reqBody.getString("video_id");

        RemoteApiBridge.playNext(videoId);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleRemoveFromQueue(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String videoId = reqBody.getString("video_id");

        RemoteApiBridge.removeFromQueue(videoId);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleMoveQueueItem(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        if (!reqBody.has("from") || !reqBody.has("to")) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Missing from/to");
        }
        int from = reqBody.getInt("from");
        int to = reqBody.getInt("to");

        RemoteApiBridge.moveQueueItem(from, to);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleQueuePlaylist(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);

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

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
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
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        int volume = reqBody.getInt("volume");
        getTheater().setVolume(volume);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        body.put("volume", volume);
        return corsResponse(jsonResponse(body));
    }

    // ---- StringSetter functional interface ----

    @FunctionalInterface
    private interface StringSetter {
        void set(String value);
    }

    private Response handleSetString(IHTTPSession session, String jsonKey, StringSetter setter) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String value = reqBody.getString(jsonKey);
        setter.set(value);
        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
    }

    private Response handleVoice(IHTTPSession session) throws JSONException, IOException {
        String bodyStr = readBody(session);
        JSONObject reqBody = new JSONObject(bodyStr);
        String action = reqBody.getString("action");

        RemoteApiBridge.voice(action);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
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

    private String getDeviceName() {
        String name = Build.MODEL;
        if (name == null || name.isEmpty()) {
            name = "SmartTube Device";
        }
        return name;
    }

    private String getDeviceId() {
        return Settings.Secure.ANDROID_ID;
    }

    private String getAppVersion() {
        return API_VERSION;
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
}
