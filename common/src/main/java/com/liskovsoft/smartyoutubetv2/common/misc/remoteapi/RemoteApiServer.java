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

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.ResponseException;

public class RemoteApiServer extends NanoHTTPD {
    private static final String TAG = RemoteApiServer.class.getSimpleName();
    private static final int DEFAULT_PORT = 8497;
    private static final String API_VERSION = "1";

    private static RemoteApiServer sInstance;

    private final RemoteApiAuthProvider mAuth;
    private final RemoteApiData mApiData;
    private Thread mUdpThread;
    private volatile boolean mRunning;

    public RemoteApiServer(RemoteApiData apiData) {
        this(apiData.getPort(), apiData);
    }

    public RemoteApiServer(int port, RemoteApiData apiData) {
        super(port);
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
            sInstance = new RemoteApiServer(data);
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
        Log.i(TAG, "Remote API server started on port %d", getListeningPort());
    }

    @Override
    public void stop() {
        mRunning = false;
        if (mUdpThread != null) {
            mUdpThread.interrupt();
            mUdpThread = null;
        }
        super.stop();
        Log.i(TAG, "Remote API server stopped");
    }

    private void startUdpListener() {
        mUdpThread = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(getListeningPort());
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

    @Override
    public Response serve(IHTTPSession session) {
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

            String authHeader = headers.get("authorization");
            String token = extractBearerToken(authHeader);
            if (!mAuth.isTokenValid(token)) {
                return errorResponse(Response.Status.UNAUTHORIZED, 401, "Unauthorized");
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

            if (Method.POST.equals(method) && "/api/content/open".equals(path)) {
                return handleContentOpen(session);
            }

            if (Method.GET.equals(method) && "/api/content/suggestions".equals(path)) {
                return handleGetSuggestions();
            }

            if (path.startsWith("/api/content/suggestions/")) {
                String indexStr = path.substring("/api/content/suggestions/".length());
                if (Method.POST.equals(method)) {
                    return handlePlaySuggestion(indexStr);
                }
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
            return errorResponse(Response.Status.BAD_REQUEST, 422, "Invalid JSON");
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

    private Response handlePlaySuggestion(String indexStr) throws JSONException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return errorResponse(Response.Status.BAD_REQUEST, 400, "Invalid suggestion index");
        }

        RemoteApiBridge.playSuggestion(index);

        JSONObject body = new JSONObject();
        body.put("ok", true);
        return corsResponse(jsonResponse(body));
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
        String body = bodyMap.get("postData");
        if (body == null) {
            body = "{}";
        }
        return body;
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
