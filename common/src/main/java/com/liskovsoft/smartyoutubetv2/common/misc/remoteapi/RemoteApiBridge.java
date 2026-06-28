package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

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

    // The player exposes suggestions as indexed groups with no count; scan until the
    // first null group. Bound the scan so a misbehaving provider can't spin forever.
    private static final int MAX_SUGGESTION_GROUPS = 50;

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

    @FunctionalInterface
    private interface PlayerAction {
        void run(PlaybackView player);
    }

    // Most mutating commands share the same shape: hop to the main thread, grab the
    // player, and no-op if it's gone. This bundles that guard so call sites read as
    // just their payload. (getPlayer() != null also implies sPresenter != null.)
    private static void withPlayerOnMain(PlayerAction action) {
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                action.run(player);
            }
        });
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
            try {
                JSONObject state = new JSONObject();
                state.put("state", "idle");
                state.put("suggestions_count", 0);
                Playlist playlist = Playlist.instance();
                state.put("queue_size", playlist.getAll().size());
                state.put("queue_index", playlist.getAll().indexOf(playlist.getCurrent()));
                state.put("queue_generation", playlist.getGeneration());
                return state;
            } catch (JSONException e) {
                return null;
            }
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
                JSONObject videoJson = videoToJson(video);
                videoJson.put("channel_id", video.channelId);
                // The player knows the real, resolved duration; prefer it over the card's.
                videoJson.put("duration_ms", player.getDurationMs());
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

            state.put("selected_tracks", selectedTracksJson(player));

            JSONObject videoTransform = new JSONObject();
            videoTransform.put("resize_mode", player.getResizeMode());
            videoTransform.put("zoom_percents", sZoomPercents);
            videoTransform.put("rotation_angle", sRotationAngle);
            videoTransform.put("flip_enabled", sFlipEnabled);
            state.put("video_transform", videoTransform);

            state.put("suggestions_count", countSuggestions(player));

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
        withPlayerOnMain(player -> player.setPlayWhenReady(true));
    }

    public static void handlePause() {
        withPlayerOnMain(player -> player.setPlayWhenReady(false));
    }

    public static void handleToggle() {
        withPlayerOnMain(player -> player.setPlayWhenReady(!player.getPlayWhenReady()));
    }

    public static JSONObject handleSeek(long positionMs) {
        withPlayerOnMain(player -> player.setPositionMs(positionMs));
        JSONObject result = new JSONObject();
        try {
            result.put("ok", true);
            result.put("position_ms", positionMs);
        } catch (JSONException e) {
            // JSON construction over constant keys can't fail.
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
        withPlayerOnMain(player -> {
            VideoLoaderController controller = sPresenter.getController(VideoLoaderController.class);
            if (controller != null) {
                controller.loadNext();
            }
        });
    }

    public static void handlePrevious() {
        if (skipDebounced()) {
            return;
        }
        withPlayerOnMain(player -> {
            VideoLoaderController controller = sPresenter.getController(VideoLoaderController.class);
            if (controller != null) {
                controller.loadPrevious();
            }
        });
    }

    public static void handleStop() {
        withPlayerOnMain(PlaybackView::finish);
    }

    public static void handleReload() {
        withPlayerOnMain(PlaybackView::reloadPlayback);
    }

    // ---- Speed / Volume / Pitch ----

    public static float getSpeed() {
        PlaybackView player = getPlayer();
        return player != null ? player.getSpeed() : 1.0f;
    }

    public static void setSpeed(float speed) {
        withPlayerOnMain(player -> player.setSpeed(speed));
    }

    public static float getVolume() {
        // Report the user's intended volume (PlayerData), NOT player.getVolume():
        // VideoStateController multiplies the persisted value by the video's loudness
        // normalization on every load, so reading the effective value back and
        // persisting it again ratchets the volume lower on each video change.
        if (sPresenter != null && sPresenter.getContext() != null) {
            return PlayerData.instance(sPresenter.getContext()).getPlayerVolume();
        }
        PlaybackView player = getPlayer();
        return player != null ? player.getVolume() : 1.0f;
    }

    public static void setVolume(float volume) {
        // Accept both the documented normalized form (0.10) and controller/UI
        // percent values (10). The persisted value is always ExoPlayer's 0..1
        // gain, matching the macOS "10/100" player-volume command.
        float normalized = volume > 1f ? volume / 100f : volume;

        // Clamp to 0..1 (the API contract). Persisting a value > 1.0 arms the
        // VolumeBooster (LoudnessEnhancer) on the next player init — its built-in
        // limiter audibly compresses/"sidechains" the output. 1.0 is the safe max.
        // Math.max/min also coerce a non-finite (NaN/Infinity) input to a sane bound.
        float clamped = Float.isNaN(normalized) ? 1f : Math.max(0f, Math.min(normalized, 1f));
        // Persist independently of the player: VideoStateController re-applies
        // PlayerData's volume on every video load, so without this the API value
        // reverts on the next video — even if no player is attached right now.
        runOnMainThread(() -> {
            PlaybackView player = getPlayer();
            if (player != null) {
                player.setVolume(clamped);
            }
            if (sPresenter != null && sPresenter.getContext() != null) {
                PlayerData.instance(sPresenter.getContext()).setPlayerVolume(clamped);
            }
        });
    }

    public static float getPitch() {
        PlaybackView player = getPlayer();
        return player != null ? player.getPitch() : 1.0f;
    }

    public static void setPitch(float pitch) {
        withPlayerOnMain(player -> player.setPitch(pitch));
    }

    // ---- Format Listing ----

    public static JSONArray getVideoFormats() {
        PlaybackView player = getPlayer();
        return player != null ? formatListToJson(player.getVideoFormats()) : new JSONArray();
    }

    public static JSONArray getAudioFormats() {
        PlaybackView player = getPlayer();
        return player != null ? formatListToJson(player.getAudioFormats()) : new JSONArray();
    }

    public static JSONArray getSubtitleFormats() {
        PlaybackView player = getPlayer();
        return player != null ? formatListToJson(player.getSubtitleFormats()) : new JSONArray();
    }

    public static JSONObject getSelectedTracks() {
        PlaybackView player = getPlayer();
        if (player == null) {
            return new JSONObject();
        }
        try {
            return selectedTracksJson(player);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONObject selectedTracksJson(PlaybackView player) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("video", formatToJsonObject(player.getVideoFormat()));
        result.put("audio", formatToJsonObject(player.getAudioFormat()));
        result.put("subtitle", formatToJsonObject(player.getSubtitleFormat()));
        return result;
    }

    // ---- Format Selection ----

    public static void setVideoFormat(String formatId) {
        withPlayerOnMain(player -> setFormatById(player, player.getVideoFormats(), formatId));
    }

    public static void setAudioFormat(String formatId) {
        withPlayerOnMain(player -> setFormatById(player, player.getAudioFormats(), formatId));
    }

    public static void setSubtitleFormat(String formatId) {
        withPlayerOnMain(player -> {
            if (formatId == null || formatId.isEmpty()) {
                player.setFormat(FormatItem.SUBTITLE_NONE);
                return;
            }
            setFormatById(player, player.getSubtitleFormats(), formatId);
        });
    }

    // Find the format matching formatId in the given list and select it; no-op if absent.
    private static void setFormatById(PlaybackView player, List<FormatItem> formats, String formatId) {
        if (formatId == null) {
            return;
        }
        FormatItem item = findFormatById(formats, formatId);
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
        withPlayerOnMain(player -> player.setResizeMode(mode));
    }

    public static int getZoom() {
        return sZoomPercents;
    }

    public static void setZoom(int zoom) {
        // Track the value even when no player is attached so getZoom() stays accurate.
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

    public static void notifySuggestionsUpdated() {
        RemoteApiServer server = RemoteApiServer.getInstance();
        if (server != null) {
            try {
                JSONObject data = new JSONObject();
                data.put("suggestions_count", countSuggestions(getPlayer()));
                server.broadcastEvent("suggestions_updated", data);
            } catch (JSONException e) {
                // JSON construction over constant keys can't fail.
            }
        }
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
        JSONArray result = new JSONArray();
        forEachSuggestion(getPlayer(), video -> {
            try {
                result.put(videoToJson(video));
            } catch (JSONException e) {
                // skip this video
            }
            return false;
        });
        return result;
    }

    /**
     * Chapters of the currently playing video, as parsed from YouTube metadata by
     * SuggestionsController. Read-only snapshot — safe to call from NanoHTTPD worker
     * threads (same direct-read style as getSuggestions()); the chapter list field
     * is volatile and ChapterItems are immutable once parsed. Returns [] when
     * nothing is playing or the video has no chapters.
     */
    public static JSONArray getChapters() {
        JSONArray result = new JSONArray();

        if (sPresenter == null) {
            return result;
        }

        SuggestionsController controller = sPresenter.getController(SuggestionsController.class);
        if (controller == null) {
            return result;
        }

        List<ChapterItem> chapters = controller.getChapters();
        if (chapters == null || chapters.isEmpty()) {
            return result;
        }

        // end_ms of each chapter = start of the next one; the last chapter ends at the
        // video duration (when the player can tell us — otherwise omit the field).
        PlaybackView player = getPlayer();
        long durationMs = player != null ? player.getDurationMs() : 0;

        for (int i = 0; i < chapters.size(); i++) {
            ChapterItem chapter = chapters.get(i);
            if (chapter == null) {
                continue;
            }
            try {
                JSONObject json = new JSONObject();
                json.put("title", chapter.getTitle());
                json.put("start_ms", chapter.getStartTimeMs());
                if (i + 1 < chapters.size() && chapters.get(i + 1) != null) {
                    json.put("end_ms", chapters.get(i + 1).getStartTimeMs());
                } else if (durationMs > 0) {
                    json.put("end_ms", durationMs);
                }
                String thumb = chapter.getCardImageUrl();
                if (thumb != null && !thumb.isEmpty()) {
                    json.put("thumbnail_url", thumb);
                }
                result.put(json);
            } catch (JSONException e) {
                // skip this chapter
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
            MediaGroup group = YouTubeServiceManager.instance().getContentService().getRecommended();
            if (group != null && group.getMediaItems() != null) {
                for (MediaItem item : group.getMediaItems()) {
                    if (item.getVideoId() == null) {
                        continue;
                    }
                    result.put(mediaItemToJson(item));
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

            boolean[] found = {false};
            forEachSuggestion(getPlayer(), video -> {
                if (videoId.equals(video.videoId)) {
                    video.pendingPosMs = 0;
                    sPresenter.openVideo(video);
                    found[0] = true;
                    return true;
                }
                return false;
            });

            if (!found[0]) {
                Video video = Video.from(videoId);
                video.isRemote = true;
                sPresenter.openVideo(video);
            }
        });
    }

    public static void playSuggestion(int index) {
        withPlayerOnMain(player -> {
            int[] currentIndex = {0};
            forEachSuggestion(player, video -> {
                if (currentIndex[0] == index) {
                    video.pendingPosMs = 0;
                    sPresenter.openVideo(video);
                    return true;
                }
                currentIndex[0]++;
                return false;
            });
        });
    }

    @FunctionalInterface
    private interface SuggestionVisitor {
        /** Return true to stop iterating. */
        boolean visit(Video video);
    }

    // Walk every playable suggestion video across all groups for the given player,
    // hiding the indexed-group scan and the null-group termination. No-op if player
    // is null. Stops early once the visitor returns true.
    private static void forEachSuggestion(PlaybackView player, SuggestionVisitor visitor) {
        if (player == null) {
            return;
        }
        for (int i = 0; i < MAX_SUGGESTION_GROUPS; i++) {
            VideoGroup group = player.getSuggestionsByIndex(i);
            if (group == null) {
                break;
            }
            for (Video video : group.getVideos()) {
                if (video.hasVideo() && visitor.visit(video)) {
                    return;
                }
            }
        }
    }

    // Total number of items across all suggestion groups (counts group sizes, not
    // filtered by hasVideo — mirrors what the player's grid shows).
    private static int countSuggestions(PlaybackView player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < MAX_SUGGESTION_GROUPS; i++) {
            VideoGroup group = player.getSuggestionsByIndex(i);
            if (group == null) {
                break;
            }
            count += group.getSize();
        }
        return count;
    }

    // ---- Subtitles ----

    public static void toggleSubtitles() {
        withPlayerOnMain(player -> player.showSubtitles(!isSubtitleButtonOn(player)));
    }

    /** Deterministically enable or disable subtitles (closed captions). */
    public static void setSubtitlesEnabled(boolean enabled) {
        withPlayerOnMain(player -> player.showSubtitles(enabled));
    }

    public static boolean areSubtitlesOn() {
        PlaybackView player = getPlayer();
        return player != null && isSubtitleButtonOn(player);
    }

    private static boolean isSubtitleButtonOn(PlaybackView player) {
        return player.getButtonState(
                com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_closed_captioning)
                == PlayerUI.BUTTON_ON;
    }

    // ---- Mute / Unmute ----

    public static void toggleMute() {
        withPlayerOnMain(player -> {
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

    // ---- Picture-in-Picture ----

    /**
     * Toggle Picture-in-Picture: enter if the player is in fullscreen, exit (back to
     * fullscreen) if it's already in PIP. Entering mirrors PlayerUIController.onPipClicked();
     * exiting unblocks the engine and brings the player view back to the foreground via
     * ViewManager.movePlayerToForeground() — the app's own "surface the player" path,
     * which expands it out of the PIP window.
     */
    public static void togglePip() {
        withPlayerOnMain(player -> {
            if (player.isInPIPMode()) {
                player.blockEngine(false);
                Context context = sPresenter.getContext();
                if (context != null) {
                    ViewManager.instance(context).movePlayerToForeground();
                }
            } else {
                player.showOverlay(false);
                player.blockEngine(true);
                player.finish();
            }
        });
    }

    public static boolean isPipActive() {
        PlaybackView player = getPlayer();
        return player != null && player.isInPIPMode();
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
            List<MediaGroup> groups = YouTubeServiceManager.instance().getContentService().getSearch(query);
            if (groups != null) {
                for (MediaGroup group : groups) {
                    if (group == null || group.getMediaItems() == null) {
                        continue;
                    }
                    for (MediaItem item : group.getMediaItems()) {
                        if (result.length() >= limit) {
                            return result;
                        }
                        if (item.getVideoId() == null) {
                            continue;
                        }
                        result.put(mediaItemToJson(item));
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
                JSONObject item = videoToJson(video);
                item.put("index", i);
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
            for (Video video : playlist.getAll()) {
                if (videoId.equals(video.videoId)) {
                    playlist.remove(video);
                    break;
                }
            }
        });
    }

    public static void clearQueue() {
        runOnMainThread(() -> Playlist.instance().clear());
    }

    public static void shuffleQueue() {
        runOnMainThread(() -> Playlist.instance().shuffle());
    }

    public static void moveQueueItem(int from, int to) {
        runOnMainThread(() -> Playlist.instance().move(from, to));
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
            MediaGroup playlistGroup = resolvePlaylistGroup(playlistId);
            List<MediaItem> items = loadAllPlaylistItems(playlistGroup);
            if (items != null) {
                for (MediaItem item : items) {
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
    private static MediaGroup resolvePlaylistGroup(String playlistId) {
        MediaItemMetadata metadata = YouTubeServiceManager.instance()
                .getMediaItemService().getMetadata(null, playlistId, 0, null);
        if (metadata == null || metadata.getSuggestions() == null) {
            return null;
        }
        for (MediaGroup group : metadata.getSuggestions()) {
            List<MediaItem> mediaItems = group.getMediaItems();
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
    private static List<MediaItem> loadAllPlaylistItems(MediaGroup playlistGroup) {
        if (playlistGroup == null) {
            return null;
        }
        List<MediaItem> allItems = new ArrayList<>();
        List<MediaItem> firstItems = playlistGroup.getMediaItems();
        if (firstItems != null) {
            allItems.addAll(firstItems);
        }
        MediaGroup currentGroup = playlistGroup;
        ContentService contentService = YouTubeServiceManager.instance().getContentService();
        for (int i = 0; i < MAX_PLAYLIST_CONTINUATIONS && allItems.size() < MAX_TOTAL_PLAYLIST_ITEMS; i++) {
            try {
                String nextPageKey = currentGroup.getNextPageKey();
                if (nextPageKey == null || nextPageKey.isEmpty()) {
                    break;
                }
                MediaGroup nextGroup = contentService.continueGroup(currentGroup);
                if (nextGroup == null || nextGroup.isEmpty()) {
                    break;
                }
                List<MediaItem> nextItems = nextGroup.getMediaItems();
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

    // ---- System Control ----

    public static void dpad(String key) {
        runOnMainThread(() -> {
            int keyCode;

            switch (key) {
                case "up":
                    keyCode = KeyEvent.KEYCODE_DPAD_UP;
                    break;
                case "down":
                    keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                    break;
                case "left":
                    keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                    break;
                case "right":
                    keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
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

            // Each press is a clean ACTION_DOWN + ACTION_UP. Focus navigation happens on the
            // DOWN (see Utils.sendKey); the UP completes clicks for DPAD_CENTER/ENTER.
            Utils.sendKey(keyCode);
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

    // The fields shared by every video-list endpoint (suggestions, queue, player
    // state). Callers add their own extras (index/is_current, channel_id, …).
    private static JSONObject videoToJson(Video video) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("video_id", video.videoId);
        // Use getTitleFull(), not getTitle(): a video opened by bare id (search → play, remote
        // API) has a null `title` field — only `metadataTitle` gets populated once metadata loads
        // (author/channel/duration do too, which is why those show but the title was missing).
        // getTitle() ignores metadataTitle; getTitleFull() falls back to it. Otherwise org.json
        // drops the null and the client shows "untitled" / "no video loaded".
        json.put("title", video.getTitleFull());
        json.put("author", video.getAuthor());
        json.put("thumbnail_url", bestThumbnail(video));
        json.put("duration_ms", video.getDurationMs());
        json.put("is_live", video.isLive);
        return json;
    }

    // YouTube-API MediaItem → the same wire shape as videoToJson(), used by the
    // recommended feed and search results. Author falls back to the second title.
    private static JSONObject mediaItemToJson(MediaItem item) throws JSONException {
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
        return json;
    }

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
