package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.net.Uri;
import android.view.KeyEvent;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class RemoteApiBridge {
    private static PlaybackPresenter sPresenter;
    private static RemoteApiServer sServer;

    private static int sZoomPercents = 100;
    private static int sRotationAngle = 0;
    private static boolean sFlipEnabled = false;

    public static void init(PlaybackPresenter presenter) {
        sPresenter = presenter;
    }

    public static void setServer(RemoteApiServer server) {
        sServer = server;
    }

    private static PlaybackView getPlayer() {
        return sPresenter != null ? sPresenter.getPlayer() : null;
    }

    // ---- Player State ----

    public static JSONObject getPlayerState() {
        PlaybackView player = getPlayer();
        if (player == null || !player.isEngineInitialized()) {
            return null;
        }

        try {
            JSONObject state = new JSONObject();

            if (player.isPlaying()) {
                state.put("state", "playing");
            } else if (player.getPlayWhenReady()) {
                state.put("state", "buffering");
            } else {
                state.put("state", "idle");
            }

            Video video = sPresenter.getVideo();
            if (video != null) {
                JSONObject videoJson = new JSONObject();
                videoJson.put("video_id", video.videoId);
                videoJson.put("title", video.getTitle());
                videoJson.put("author", video.getAuthor());
                videoJson.put("channel_id", video.channelId);
                videoJson.put("thumbnail_url", video.getCardImageUrl());
                videoJson.put("duration_ms", player.getDurationMs());
                videoJson.put("is_live", video.isLive);
                videoJson.put("is_shorts", video.isShorts);
                videoJson.put("playlist_id", video.getPlaylistId());
                videoJson.put("playlist_index", video.playlistIndex);
                state.put("video", videoJson);
            }

            state.put("position_ms", player.getPositionMs());
            state.put("duration_ms", player.getDurationMs());
            state.put("speed", player.getSpeed());
            state.put("pitch", player.getPitch());
            state.put("volume", player.getVolume());

            JSONObject selectedTracks = new JSONObject();
            FormatItem videoFormat = player.getVideoFormat();
            FormatItem audioFormat = player.getAudioFormat();
            FormatItem subtitleFormat = player.getSubtitleFormat();
            selectedTracks.put("video", formatToJsonObject(videoFormat));
            selectedTracks.put("audio", formatToJsonObject(audioFormat));
            selectedTracks.put("subtitle", formatToJsonObject(subtitleFormat));
            state.put("selected_tracks", selectedTracks);

            JSONObject videoTransform = new JSONObject();
            videoTransform.put("resize_mode", player.getResizeMode());
            videoTransform.put("zoom_percents", sZoomPercents);
            videoTransform.put("rotation_angle", sRotationAngle);
            videoTransform.put("flip_enabled", sFlipEnabled);
            state.put("video_transform", videoTransform);

            int suggestionsCount = 0;
            for (int i = 0; i < 50; i++) {
                VideoGroup group = player.getSuggestionsByIndex(i);
                if (group == null) {
                    break;
                }
                suggestionsCount += group.getSize();
            }
            state.put("suggestions_count", suggestionsCount);

            return state;
        } catch (JSONException e) {
            return null;
        }
    }

    // ---- Transport Controls ----

    public static void handlePlay() {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public static void handlePause() {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public static void handleToggle() {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setPlayWhenReady(!player.getPlayWhenReady());
        }
    }

    public static JSONObject handleSeek(long positionMs) {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setPositionMs(positionMs);
        }
        JSONObject result = new JSONObject();
        try {
            result.put("ok", true);
            result.put("position_ms", positionMs);
        } catch (JSONException e) {
            // won't happen
        }
        return result;
    }

    public static void handleNext() {
        PlaybackView player = getPlayer();
        if (player != null && sPresenter != null) {
            VideoLoaderController controller = sPresenter.getController(VideoLoaderController.class);
            if (controller != null) {
                controller.loadNext();
            }
        }
    }

    public static void handlePrevious() {
        PlaybackView player = getPlayer();
        if (player != null && sPresenter != null) {
            VideoLoaderController controller = sPresenter.getController(VideoLoaderController.class);
            if (controller != null) {
                controller.loadPrevious();
            }
        }
    }

    public static void handleStop() {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.finish();
        }
    }

    public static void handleReload() {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.reloadPlayback();
        }
    }

    // ---- Speed / Volume / Pitch ----

    public static float getSpeed() {
        PlaybackView player = getPlayer();
        return player != null ? player.getSpeed() : 1.0f;
    }

    public static void setSpeed(float speed) {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setSpeed(speed);
        }
    }

    public static float getVolume() {
        PlaybackView player = getPlayer();
        return player != null ? player.getVolume() : 1.0f;
    }

    public static void setVolume(float volume) {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setVolume(volume);
        }
    }

    public static float getPitch() {
        PlaybackView player = getPlayer();
        return player != null ? player.getPitch() : 1.0f;
    }

    public static void setPitch(float pitch) {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setPitch(pitch);
        }
    }

    // ---- Format Listing ----

    public static JSONArray getVideoFormats() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return new JSONArray();
        }

        List<FormatItem> formats = player.getVideoFormats();
        return formatListToJson(formats);
    }

    public static JSONArray getAudioFormats() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return new JSONArray();
        }

        List<FormatItem> formats = player.getAudioFormats();
        return formatListToJson(formats);
    }

    public static JSONArray getSubtitleFormats() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return new JSONArray();
        }

        List<FormatItem> formats = player.getSubtitleFormats();
        return formatListToJson(formats);
    }

    public static JSONObject getSelectedTracks() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return new JSONObject();
        }

        try {
            JSONObject result = new JSONObject();
            result.put("video", formatToJsonObject(player.getVideoFormat()));
            result.put("audio", formatToJsonObject(player.getAudioFormat()));
            result.put("subtitle", formatToJsonObject(player.getSubtitleFormat()));
            return result;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // ---- Format Selection ----

    public static void setVideoFormat(String formatId) {
        PlaybackView player = getPlayer();
        if (player == null || formatId == null) {
            return;
        }

        FormatItem item = findFormatById(player.getVideoFormats(), formatId);
        if (item != null) {
            player.setFormat(item);
        }
    }

    public static void setAudioFormat(String formatId) {
        PlaybackView player = getPlayer();
        if (player == null || formatId == null) {
            return;
        }

        FormatItem item = findFormatById(player.getAudioFormats(), formatId);
        if (item != null) {
            player.setFormat(item);
        }
    }

    public static void setSubtitleFormat(String formatId) {
        PlaybackView player = getPlayer();
        if (player == null) {
            return;
        }

        if (formatId == null || formatId.isEmpty()) {
            player.setFormat(FormatItem.SUBTITLE_NONE);
            return;
        }

        FormatItem item = findFormatById(player.getSubtitleFormats(), formatId);
        if (item != null) {
            player.setFormat(item);
        }
    }

    // ---- Video Transform ----

    public static int getResizeMode() {
        PlaybackView player = getPlayer();
        return player != null ? player.getResizeMode() : 0;
    }

    public static void setResizeMode(int mode) {
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setResizeMode(mode);
        }
    }

    public static int getZoom() {
        return sZoomPercents;
    }

    public static void setZoom(int zoom) {
        sZoomPercents = zoom;
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setZoomPercents(zoom);
        }
    }

    public static int getRotation() {
        return sRotationAngle;
    }

    public static void setRotation(int angle) {
        sRotationAngle = angle;
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setRotationAngle(angle);
        }
    }

    public static boolean getFlip() {
        return sFlipEnabled;
    }

    public static void setFlip(boolean enabled) {
        sFlipEnabled = enabled;
        PlaybackView player = getPlayer();
        if (player != null) {
            player.setVideoFlipEnabled(enabled);
        }
    }

    // ---- Content ----

    public static void openVideo(String url, String videoId, Long positionMs, String playlistId, Integer playlistIndex) {
        if (sPresenter == null) {
            return;
        }

        if (videoId == null && url != null) {
            videoId = extractVideoIdFromUrl(url);
        }

        if (videoId == null) {
            return;
        }

        Video video = Video.from(videoId);

        if (playlistId != null && !playlistId.isEmpty()) {
            video.remotePlaylistId = playlistId;
        }

        if (playlistIndex != null) {
            video.playlistIndex = playlistIndex;
        }

        if (positionMs != null) {
            video.pendingPosMs = positionMs;
        }

        video.isRemote = true;
        sPresenter.openVideo(video);
    }

    public static JSONArray getSuggestions() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return new JSONArray();
        }

        JSONArray result = new JSONArray();

        for (int i = 0; i < 50; i++) {
            VideoGroup group = player.getSuggestionsByIndex(i);
            if (group == null) {
                break;
            }

            for (Video video : group.getVideos()) {
                if (video.hasVideo()) {
                    try {
                        JSONObject videoJson = new JSONObject();
                        videoJson.put("video_id", video.videoId);
                        videoJson.put("title", video.getTitle());
                        videoJson.put("author", video.getAuthor());
                        videoJson.put("thumbnail_url", video.getCardImageUrl());
                        videoJson.put("duration_ms", video.getDurationMs());
                        videoJson.put("is_live", video.isLive);
                        result.put(videoJson);
                    } catch (JSONException e) {
                        // skip this video
                    }
                }
            }
        }

        return result;
    }

    public static void playSuggestion(int index) {
        PlaybackView player = getPlayer();
        if (player == null) {
            return;
        }

        int currentIndex = 0;

        for (int i = 0; i < 50; i++) {
            VideoGroup group = player.getSuggestionsByIndex(i);
            if (group == null) {
                break;
            }

            for (Video video : group.getVideos()) {
                if (video.hasVideo()) {
                    if (currentIndex == index) {
                        video.pendingPosMs = 0;
                        sPresenter.openVideo(video);
                        return;
                    }
                    currentIndex++;
                }
            }
        }
    }

    // ---- System Control ----

    public static void dpad(String key) {
        int keyCode;
        boolean isLongAction = false;

        switch (key) {
            case "up":
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case "down":
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case "left":
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                isLongAction = true;
                break;
            case "right":
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                isLongAction = true;
                break;
            case "enter":
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
                break;
            case "back":
                keyCode = KeyEvent.KEYCODE_BACK;
                break;
            default:
                return;
        }

        if (isLongAction) {
            Utils.sendKey(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            Utils.postDelayed(() -> Utils.sendKey(new KeyEvent(KeyEvent.ACTION_UP, keyCode)), 500);
        } else {
            Utils.sendKey(keyCode);
        }
    }

    public static void voice(String action) {
        if (sPresenter == null) {
            return;
        }

        Context context = sPresenter.getContext();
        if (context == null) {
            return;
        }

        SearchPresenter searchPresenter = SearchPresenter.instance(context);

        if ("start".equals(action)) {
            searchPresenter.startVoice();
        } else if ("stop".equals(action)) {
            searchPresenter.forceFinish();
        }
    }

    // ---- Helpers ----

    private static JSONArray formatListToJson(List<FormatItem> formats) {
        JSONArray array = new JSONArray();
        if (formats == null) {
            return array;
        }

        for (FormatItem format : formats) {
            JSONObject json = formatToJsonObject(format);
            if (json != null) {
                array.put(json);
            }
        }

        return array;
    }

    private static JSONObject formatToJsonObject(FormatItem format) {
        if (format == null) {
            return null;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("format_id", format.getFormatId());
            json.put("width", format.getWidth());
            json.put("height", format.getHeight());
            json.put("frame_rate", format.getFrameRate());
            json.put("label", format.getTitle() != null ? format.getTitle().toString() : "");
            json.put("is_selected", format.isSelected());

            MediaTrack track = format.getTrack();
            if (track != null && track.format != null) {
                json.put("codec", TrackSelectorUtil.extractCodec(track.format));
                json.put("bitrate", track.format.bitrate);
            }

            json.put("language", format.getLanguage() != null ? format.getLanguage() : "");

            return json;
        } catch (JSONException e) {
            return null;
        }
    }

    private static FormatItem findFormatById(List<FormatItem> formats, String formatId) {
        if (formats == null || formatId == null) {
            return null;
        }

        for (FormatItem item : formats) {
            if (formatId.equals(item.getFormatId())) {
                return item;
            }
        }

        return null;
    }

    private static String extractVideoIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return null;
        }

        if (uri == null) {
            return null;
        }

        String videoId = uri.getQueryParameter("v");

        if (videoId == null) {
            String host = uri.getHost();
            if (host != null && (host.contains("youtu.be") || host.contains("youtube-nocookie.com"))) {
                videoId = uri.getLastPathSegment();
            }
        }

        if (videoId == null) {
            String path = uri.getPath();
            if (path != null && path.contains("/embed/")) {
                int embedIdx = path.indexOf("/embed/") + "/embed/".length();
                if (embedIdx < path.length()) {
                    videoId = path.substring(embedIdx);
                    int slashIdx = videoId.indexOf('/');
                    if (slashIdx > 0) {
                        videoId = videoId.substring(0, slashIdx);
                    }
                }
            }
        }

        if (videoId == null) {
            videoId = uri.getLastPathSegment();
        }

        if (videoId != null && videoId.length() == 11) {
            return videoId;
        }

        return null;
    }
}
