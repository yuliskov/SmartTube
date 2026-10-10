package com.liskovsoft.smartyoutubetv2.common.vot;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** The TV player response can omit the music category, so check the web player metadata. */
public final class VotMusicVideo {
    private static final String PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
    private static final MediaType JSON = MediaType.parse("application/json");

    private VotMusicVideo() {}

    public static boolean isMusic(String videoId) throws Exception {
        JSONObject client = new JSONObject().put("clientName", "WEB")
                .put("clientVersion", "2.20260907.06.00");
        JSONObject body = new JSONObject()
                .put("context", new JSONObject().put("client", client))
                .put("videoId", videoId);
        Request request = new Request.Builder().url(PLAYER_URL)
                .post(RequestBody.create(JSON, body.toString())).build();
        try (Response response = OkHttpManager.instance().getClient().newBuilder()
                .callTimeout(10, TimeUnit.SECONDS).build().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Music metadata unavailable");
            return isMusicResponse(response.body().string());
        }
    }

    static boolean isMusicResponse(String response) throws Exception {
        JSONObject data = new JSONObject(response);
        JSONObject microformat = data.optJSONObject("microformat");
        JSONObject renderer = microformat == null ? null : microformat.optJSONObject("playerMicroformatRenderer");
        if (renderer != null && "Music".equalsIgnoreCase(renderer.optString("category"))) return true;
        JSONObject details = data.optJSONObject("videoDetails");
        String type = details == null ? "" : details.optString("musicVideoType");
        return "MUSIC_VIDEO_TYPE_OMV".equals(type) || "MUSIC_VIDEO_TYPE_ATV".equals(type)
                || "MUSIC_VIDEO_TYPE_UGC".equals(type);
    }
}
