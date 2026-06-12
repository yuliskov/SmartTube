package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
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

import java.util.ArrayList;
import java.util.List;

public class RemoteApiBridge {
    private static PlaybackPresenter sPresenter;
    private static RemoteApiServer sServer;

    // Bulk playlist-add caps: bound the blocking continuation loop so a huge playlist
    // can't tie up a NanoHTTPD worker thread indefinitely or flood the queue.
    private static final int MAX_PLAYLIST_CONTINUATIONS = 20;
    private static final int MAX_TOTAL_PLAYLIST_ITEMS = 500;

    // Mutated and read from NanoHTTPD request/WebSocket threads; volatile guarantees visibility.
    private static volatile int sZoomPercents = 100;
    private static volatile int sRotationAngle = 0;
    private static volatile boolean sFlipEnabled = false;
    private static volatile float sPreviousVolume = 1.0f;

    public static void init(PlaybackPresenter presenter) {
        sPresenter = presenter;
    }

    public static void setServer(RemoteApiServer server) {
        sServer = server;
    }

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    // API commands arrive on NanoHTTPD worker threads, but ExoPlayer and the playback
    // controllers must only be touched from the main thread — calling them off-thread
    // makes commands like next/previous silently fail (or crash). All mutators post here.
    private static void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            sMainHandler.post(action);
        }
    }

    private static PlaybackView getPlayer() {
        return sPresenter != null ? sPresenter.getPlayer() : null;
    }

    /// Prefer the background image (high-res) over the card image (small grid thumb).
    private static String bestThumbnail(Video video) {
        if (video.bgImageUrl != null && !video.bgImageUrl.isEmpty()) {
            return video.bgImageUrl;
        }
        return video.getCardImageUrl();
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
                videoJson.put("thumbnail_url", bestThumbnail(video));
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
            state.put("volume", getVolume());

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

            Playlist playlist = Playlist.instance();
            state.put("queue_size", playlist.getAll().size());
            state.put("queue_index", playlist.getAll().indexOf(playlist.getCurrent()));
            state.put("queue_generation", playlist.getGeneration());

            return state;
        } catch (JSONException e) {
            return null;
        }
    }

    // ---- Transport Controls ----

    public static void handlePlay() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setPlayWhenReady(true);
            }
        });
    }

    public static void handlePause() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        });
    }

    public static void handleToggle() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setPlayWhenReady(!player.getPlayWhenReady());
            }
        });
    }

    public static JSONObject handleSeek(long positionMs) {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setPositionMs(positionMs);
            }
        });
        JSONObject result = new JSONObject();
        try {
            result.put("ok", true);
            result.put("position_ms", positionMs);
        } catch (JSONException e) {
            // won't happen
        }
        return result;
    }

    // Rapid next/previous presses arrive before the new video's metadata loads,
    // making VideoLoaderController toast "Please wait while data is loading…".
    // Drop skips that land within the debounce window.
    private static volatile long sLastSkipMs;
    private static final long SKIP_DEBOUNCE_MS = 700;

    private static boolean skipDebounced() {
        long now = System.currentTimeMillis();
        if (now - sLastSkipMs < SKIP_DEBOUNCE_MS) {
            return true;
        }
        sLastSkipMs = now;
        return false;
    }

    public static void handleNext() {
        if (skipDebounced()) {
            return;
        }
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null && sPresenter != null) {
                VideoLoaderController controller = sPresenter.getController(VideoLoaderController.class);
                if (controller != null) {
                    controller.loadNext();
                }
            }
        });
    }

    public static void handlePrevious() {
        if (skipDebounced()) {
            return;
        }
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null && sPresenter != null) {
                VideoLoaderController controller = sPresenter.getController(VideoLoaderController.class);
                if (controller != null) {
                    controller.loadPrevious();
                }
            }
        });
    }

    public static void handleStop() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.finish();
            }
        });
    }

    public static void handleReload() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.reloadPlayback();
            }
        });
    }

    // ---- Speed / Volume / Pitch ----

    public static float getSpeed() {
        PlaybackView player = getPlayer();
        return player != null ? player.getSpeed() : 1.0f;
    }

    public static void setSpeed(float speed) {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setSpeed(speed);
            }
        });
    }

    public static float getVolume() {
        // Report the user's intended volume (PlayerData), NOT player.getVolume():
        // VideoStateController multiplies the persisted value by the video's loudness
        // normalization on every load, so reading the effective value back and
        // persisting it again ratchets the volume lower on each video change.
        if (sPresenter != null && sPresenter.getContext() != null) {
            return com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData
                    .instance(sPresenter.getContext()).getPlayerVolume();
        }
        PlaybackView player = getPlayer();
        return player != null ? player.getVolume() : 1.0f;
    }

    public static void setVolume(float volume) {
        // Clamp to 0..1 (the API contract). Persisting a value > 1.0 arms the
        // VolumeBooster (LoudnessEnhancer) on the next player init — its built-in
        // limiter audibly compresses/"sidechains" the output. 1.0 is the safe max.
        float clamped = Math.max(0f, Math.min(volume, 1f));
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setVolume(clamped);
            }
            // Persist it: VideoStateController re-applies PlayerData's volume on every
            // video load, so without this the API value reverts on the next video.
            if (sPresenter != null && sPresenter.getContext() != null) {
                com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData
                        .instance(sPresenter.getContext()).setPlayerVolume(clamped);
            }
        });
    }

    public static float getPitch() {
        PlaybackView player = getPlayer();
        return player != null ? player.getPitch() : 1.0f;
    }

    public static void setPitch(float pitch) {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setPitch(pitch);
            }
        });
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
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player == null || formatId == null) {
                return;
            }

            FormatItem item = findFormatById(player.getVideoFormats(), formatId);
            if (item != null) {
                player.setFormat(item);
            }
        });
    }

    public static void setAudioFormat(String formatId) {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player == null || formatId == null) {
                return;
            }

            FormatItem item = findFormatById(player.getAudioFormats(), formatId);
            if (item != null) {
                player.setFormat(item);
            }
        });
    }

    public static void setSubtitleFormat(String formatId) {
        runOnMainThread(() -> {
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
        });
    }

    // ---- Video Transform ----

    public static int getResizeMode() {
        PlaybackView player = getPlayer();
        return player != null ? player.getResizeMode() : 0;
    }

    public static void setResizeMode(int mode) {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setResizeMode(mode);
            }
        });
    }

    public static int getZoom() {
        return sZoomPercents;
    }

    public static void setZoom(int zoom) {
        runOnMainThread(() -> {
            sZoomPercents = zoom;
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setZoomPercents(zoom);
            }
        });
    }

    public static int getRotation() {
        return sRotationAngle;
    }

    public static void setRotation(int angle) {
        runOnMainThread(() -> {
            sRotationAngle = angle;
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setRotationAngle(angle);
            }
        });
    }

    public static boolean getFlip() {
        return sFlipEnabled;
    }

    public static void setFlip(boolean enabled) {
        runOnMainThread(() -> {
            sFlipEnabled = enabled;
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setVideoFlipEnabled(enabled);
            }
        });
    }

    // ---- Content ----

    public static void openVideo(String url, String videoId, Long positionMs, String playlistId, Integer playlistIndex) {
        final String resolvedId = (videoId == null && url != null) ? extractVideoIdFromUrl(url) : videoId;
        // No explicit playlist id, but the url may carry one (?list=...) — resolve it.
        final String resolvedPlaylistId = (playlistId == null || playlistId.isEmpty()) && url != null
                ? extractPlaylistIdFromUrl(url) : playlistId;
        runOnMainThread(() -> {
            if (sPresenter == null) {
                return;
            }

            if (resolvedId == null) {
                return;
            }

            Video video = Video.from(resolvedId);

            if (resolvedPlaylistId != null && !resolvedPlaylistId.isEmpty()) {
                video.remotePlaylistId = resolvedPlaylistId;
            }

            if (playlistIndex != null) {
                video.playlistIndex = playlistIndex;
            }

            if (positionMs != null) {
                video.pendingPosMs = positionMs;
            }

            video.isRemote = true;
            sPresenter.openVideo(video);
        });
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
                        videoJson.put("thumbnail_url", bestThumbnail(video));
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

    // ---- Recommended feed (YouTube Home) ----

    private static final Object sRecommendedLock = new Object();
    private static JSONArray sRecommendedCache;
    private static long sRecommendedCacheAt;
    private static final long RECOMMENDED_TTL_MS = 5 * 60 * 1000;

    /**
     * The user's actual Home recommendations — unlike getSuggestions(), which returns
     * the videos related to what's currently playing. Blocking network call; must be
     * invoked from a worker thread (NanoHTTPD request threads are fine).
     */
    public static JSONArray getRecommended() {
        synchronized (sRecommendedLock) {
            if (sRecommendedCache != null && System.currentTimeMillis() - sRecommendedCacheAt < RECOMMENDED_TTL_MS) {
                return sRecommendedCache;
            }
        }

        JSONArray result = new JSONArray();
        try {
            com.liskovsoft.mediaserviceinterfaces.data.MediaGroup group =
                    com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                            .getContentService().getRecommended();
            if (group != null && group.getMediaItems() != null) {
                for (com.liskovsoft.mediaserviceinterfaces.data.MediaItem item : group.getMediaItems()) {
                    if (item.getVideoId() == null) {
                        continue;
                    }
                    JSONObject json = new JSONObject();
                    json.put("video_id", item.getVideoId());
                    json.put("title", item.getTitle());
                    json.put("author", item.getAuthor() != null ? item.getAuthor()
                            : (item.getSecondTitle() != null ? item.getSecondTitle().toString() : null));
                    String thumb = item.getBackgroundImageUrl() != null
                            ? item.getBackgroundImageUrl() : item.getCardImageUrl();
                    json.put("thumbnail_url", thumb);
                    json.put("duration_ms", item.getDurationMs());
                    json.put("is_live", item.isLive());
                    result.put(json);
                }
            }
        } catch (Exception e) {
            // Network/parse failure — return whatever we collected (possibly empty).
        }

        if (result.length() > 0) {
            synchronized (sRecommendedLock) {
                sRecommendedCache = result;
                sRecommendedCacheAt = System.currentTimeMillis();
            }
        }
        return result;
    }

    /**
     * Play a suggestion by video ID — robust against the list refreshing between
     * the client fetching it and the user clicking (indexes go stale, IDs don't).
     * Prefers the player's own suggestion Video object so playback keeps its
     * metadata/context; falls back to opening the bare ID.
     */
    public static void playSuggestionById(String videoId) {
        runOnMainThread(() -> {
            if (videoId == null || sPresenter == null) {
                return;
            }

            PlaybackView player = getPlayer();
            if (player != null) {
                for (int i = 0; i < 50; i++) {
                    VideoGroup group = player.getSuggestionsByIndex(i);
                    if (group == null) {
                        break;
                    }
                    for (Video video : group.getVideos()) {
                        if (video.hasVideo() && videoId.equals(video.videoId)) {
                            video.pendingPosMs = 0;
                            sPresenter.openVideo(video);
                            return;
                        }
                    }
                }
            }

            Video video = Video.from(videoId);
            video.isRemote = true;
            sPresenter.openVideo(video);
        });
    }

    public static void playSuggestion(int index) {
        runOnMainThread(() -> {
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
        });
    }

    // ---- Subtitles ----

    public static void toggleSubtitles() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player == null) {
                return;
            }

            boolean currentlyShown = player.getButtonState(
                    com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_closed_captioning)
                    == PlayerUI.BUTTON_ON;
            player.showSubtitles(!currentlyShown);
        });
    }

    public static boolean areSubtitlesOn() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return false;
        }
        return player.getButtonState(
                com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_closed_captioning)
                == PlayerUI.BUTTON_ON;
    }

    // ---- Mute / Unmute ----

    public static void toggleMute() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player == null) {
                return;
            }

            boolean isMuted = player.getVolume() == 0f;
            if (isMuted && sPreviousVolume > 0f) {
                player.setVolume(sPreviousVolume);
            } else {
                sPreviousVolume = player.getVolume();
                player.setVolume(0f);
            }
        });
    }

    public static boolean isMuted() {
        PlaybackView player = getPlayer();
        return player != null && player.getVolume() == 0f;
    }

    // ---- Search ----

    public static void search(String query) {
        runOnMainThread(() -> {
            if (sPresenter == null || query == null || query.isEmpty()) {
                return;
            }

            Context context = sPresenter.getContext();
            if (context == null) {
                return;
            }

            SearchPresenter.instance(context).startPlay(query);
        });
    }

    /**
     * Fetch YouTube search results WITHOUT starting playback — unlike search(),
     * which plays the first hit. Blocking network call (same synchronous style
     * as getRecommended()); must be invoked from a worker thread (NanoHTTPD
     * request threads are fine). Items match the /api/content/suggestions shape.
     */
    public static JSONArray searchResults(String query, int limit) {
        JSONArray result = new JSONArray();
        if (query == null || query.isEmpty()) {
            return result;
        }

        try {
            List<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup> groups =
                    com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                            .getContentService().getSearch(query);
            if (groups != null) {
                for (com.liskovsoft.mediaserviceinterfaces.data.MediaGroup group : groups) {
                    if (group == null || group.getMediaItems() == null) {
                        continue;
                    }
                    for (com.liskovsoft.mediaserviceinterfaces.data.MediaItem item : group.getMediaItems()) {
                        if (result.length() >= limit) {
                            return result;
                        }
                        if (item.getVideoId() == null) {
                            continue;
                        }
                        JSONObject json = new JSONObject();
                        json.put("video_id", item.getVideoId());
                        json.put("title", item.getTitle());
                        json.put("author", item.getAuthor() != null ? item.getAuthor()
                                : (item.getSecondTitle() != null ? item.getSecondTitle().toString() : null));
                        String thumb = item.getBackgroundImageUrl() != null
                                ? item.getBackgroundImageUrl() : item.getCardImageUrl();
                        json.put("thumbnail_url", thumb);
                        json.put("duration_ms", item.getDurationMs());
                        json.put("is_live", item.isLive());
                        result.put(json);
                    }
                }
            }
        } catch (Exception e) {
            // Network/parse failure — return whatever we collected (possibly empty).
        }

        return result;
    }

    // ---- Queue Management ----

    public static JSONArray getQueue() {
        Playlist playlist = Playlist.instance();
        List<Video> all = playlist.getAll();
        Video current = playlist.getCurrent();

        JSONArray result = new JSONArray();
        for (int i = 0; i < all.size(); i++) {
            Video video = all.get(i);
            try {
                JSONObject item = new JSONObject();
                item.put("index", i);
                item.put("video_id", video.videoId);
                item.put("title", video.getTitle());
                item.put("author", video.getAuthor());
                item.put("thumbnail_url", bestThumbnail(video));
                item.put("duration_ms", video.getDurationMs());
                item.put("is_live", video.isLive);
                item.put("is_current", video.equals(current));
                result.put(item);
            } catch (JSONException e) {
                // skip
            }
        }
        return result;
    }

    public static void addToQueue(String videoId) {
        runOnMainThread(() -> {
            if (videoId == null) {
                return;
            }
            Video video = Video.from(videoId);
            video.isRemote = true;
            Playlist.instance().add(video);
        });
    }

    public static void playNext(String videoId) {
        runOnMainThread(() -> {
            if (videoId == null) {
                return;
            }
            Video video = Video.from(videoId);
            video.isRemote = true;
            Playlist.instance().next(video);
        });
    }

    public static void removeFromQueue(String videoId) {
        runOnMainThread(() -> {
            if (videoId == null) {
                return;
            }
            Playlist playlist = Playlist.instance();
            List<Video> all = playlist.getAll();
            for (Video video : all) {
                if (videoId.equals(video.videoId)) {
                    playlist.remove(video);
                    break;
                }
            }
        });
    }

    public static void clearQueue() {
        runOnMainThread(() -> {
            Playlist.instance().clear();
        });
    }

    public static void shuffleQueue() {
        runOnMainThread(() -> {
            Playlist.instance().shuffle();
        });
    }

    public static void moveQueueItem(int from, int to) {
        runOnMainThread(() -> {
            Playlist.instance().move(from, to);
        });
    }

    /**
     * Bulk-add a whole playlist to the queue. The playlist's items are fetched
     * synchronously (blocking continuation paging like getRecommended()), so this
     * MUST be called from a worker thread (NanoHTTPD request threads are fine).
     * Only the final Playlist mutation + optional auto-start hops to the main thread.
     */
    public static void addPlaylistToQueue(String playlistId, boolean shuffle) {
        if (playlistId == null || playlistId.isEmpty()) {
            return;
        }

        final List<Video> videos = new ArrayList<>();
        try {
            com.liskovsoft.mediaserviceinterfaces.data.MediaGroup playlistGroup = resolvePlaylistGroup(playlistId);
            List<com.liskovsoft.mediaserviceinterfaces.data.MediaItem> items = loadAllPlaylistItems(playlistGroup);
            if (items != null) {
                for (com.liskovsoft.mediaserviceinterfaces.data.MediaItem item : items) {
                    if (item.getVideoId() == null) {
                        continue;
                    }
                    Video video = Video.from(item);
                    video.isRemote = true;
                    videos.add(video);
                }
            }
        } catch (Exception e) {
            // Network/parse failure — swallow and return (matches getRecommended()).
            return;
        }

        if (videos.isEmpty()) {
            return;
        }

        runOnMainThread(() -> {
            Playlist playlist = Playlist.instance();
            for (Video video : videos) {
                playlist.add(video);
            }
            if (shuffle) {
                playlist.shuffle();
            }

            // Nothing is playing yet — start the first item so the bulk add is audible.
            PlaybackView player = getPlayer();
            if (sPresenter != null && (player == null || !player.containsMedia())) {
                Video current = playlist.getCurrent();
                if (current != null) {
                    current.isRemote = true;
                    sPresenter.openVideo(current);
                }
            }
        });
    }

    /**
     * Resolve a playlist id to its MediaGroup. The playlist row is the first
     * suggestion row in the playlist's metadata that actually has media items.
     * Uses the blocking MediaItemService.getMetadata(...) overload to stay
     * consistent with getRecommended()'s synchronous worker-thread style.
     */
    private static com.liskovsoft.mediaserviceinterfaces.data.MediaGroup resolvePlaylistGroup(String playlistId) {
        com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata metadata =
                com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance()
                        .getMediaItemService().getMetadata(null, playlistId, 0, null);
        if (metadata == null || metadata.getSuggestions() == null) {
            return null;
        }
        for (com.liskovsoft.mediaserviceinterfaces.data.MediaGroup group : metadata.getSuggestions()) {
            List<com.liskovsoft.mediaserviceinterfaces.data.MediaItem> mediaItems = group.getMediaItems();
            if (mediaItems != null && !mediaItems.isEmpty()) {
                return group;
            }
        }
        return null;
    }

    /**
     * Page through a playlist's continuations (blocking) up to the configured caps,
     * collecting all of its MediaItems.
     */
    private static List<com.liskovsoft.mediaserviceinterfaces.data.MediaItem> loadAllPlaylistItems(
            com.liskovsoft.mediaserviceinterfaces.data.MediaGroup playlistGroup) {
        if (playlistGroup == null) {
            return null;
        }
        List<com.liskovsoft.mediaserviceinterfaces.data.MediaItem> allItems = new ArrayList<>();
        List<com.liskovsoft.mediaserviceinterfaces.data.MediaItem> firstItems = playlistGroup.getMediaItems();
        if (firstItems != null) {
            allItems.addAll(firstItems);
        }
        com.liskovsoft.mediaserviceinterfaces.data.MediaGroup currentGroup = playlistGroup;
        com.liskovsoft.mediaserviceinterfaces.ContentService contentService =
                com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getContentService();
        for (int i = 0; i < MAX_PLAYLIST_CONTINUATIONS && allItems.size() < MAX_TOTAL_PLAYLIST_ITEMS; i++) {
            try {
                String nextPageKey = currentGroup.getNextPageKey();
                if (nextPageKey == null || nextPageKey.isEmpty()) {
                    break;
                }
                com.liskovsoft.mediaserviceinterfaces.data.MediaGroup nextGroup = contentService.continueGroup(currentGroup);
                if (nextGroup == null || nextGroup.isEmpty()) {
                    break;
                }
                List<com.liskovsoft.mediaserviceinterfaces.data.MediaItem> nextItems = nextGroup.getMediaItems();
                if (nextItems != null) {
                    int remaining = MAX_TOTAL_PLAYLIST_ITEMS - allItems.size();
                    if (nextItems.size() > remaining) {
                        allItems.addAll(nextItems.subList(0, remaining));
                        break;
                    }
                    allItems.addAll(nextItems);
                }
                currentGroup = nextGroup;
            } catch (Exception e) {
                break;
            }
        }
        return allItems;
    }

    // ---- Picture-in-Picture ----

    /**
     * Enter PIP — there's no programmatic "exit", so this only enters (no-op if
     * already in PIP). Mirrors PlayerUIController.onPipClicked().
     */
    public static void togglePip() {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player == null || player.isInPIPMode()) {
                return;
            }
            player.showOverlay(false);
            player.blockEngine(true);
            player.finish();
        });
    }

    // ---- System Control ----

    public static void dpad(String key) {
        runOnMainThread(() -> {
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
        });
    }

    public static void voice(String action) {
        runOnMainThread(() -> {
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
        });
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

    static String extractPlaylistIdFromUrl(String url) {
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

        String playlistId = uri.getQueryParameter("list");
        if (playlistId == null || playlistId.isEmpty()) {
            return null;
        }
        return playlistId;
    }
}
