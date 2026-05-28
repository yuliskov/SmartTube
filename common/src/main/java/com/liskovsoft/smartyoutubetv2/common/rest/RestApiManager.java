package com.liskovsoft.smartyoutubetv2.common.rest;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.google.gson.Gson;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class RestApiManager {
    private static final String TAG = RestApiManager.class.getSimpleName();
    private static final Gson sGson = new Gson();
    private static final int MAX_PLAYLIST_CONTINUATIONS = 20;
    private static final int MAX_TOTAL_PLAYLIST_ITEMS = 500;
    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;
    private static final int HTTP_ACCEPTED = 202;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_UNPROCESSABLE = 422;
    private static final int HTTP_INTERNAL_ERROR = 500;
    private static RestApiManager sInstance;
    private final Context mContext;
    private final Handler mMainHandler;
    private RestApiServer mServer;
    private io.reactivex.disposables.CompositeDisposable mDisposables;

    private RestApiManager(Context context) {
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    private void showQueueNotification(int resId, Object... formatArgs) {
        if (!AppPrefs.instance(mContext).isRestApiShowNotifications()) {
            return;
        }
        MessageHelpers.showLongMessage(mContext, mContext.getString(resId, formatArgs));
    }

    public static RestApiManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new RestApiManager(context);
        }
        return sInstance;
    }

    public synchronized void start() {
        stop();
        mDisposables = new io.reactivex.disposables.CompositeDisposable();

        AppPrefs prefs = AppPrefs.instance(mContext);
        if (!prefs.isRestApiEnabled()) {
            return;
        }

        int port = prefs.getRestApiPort();
        mServer = new RestApiServer(port, this::handleRequest);

        try {
            mServer.start();
            Log.d(TAG, "REST API started on port %s", port);
        } catch (IOException e) {
            Log.e(TAG, "Unable to start REST API: %s", e.getMessage());
            mServer = null;
        }
    }

    public synchronized void stop() {
        if (mServer != null) {
            mServer.stop();
            mServer = null;
            Log.d(TAG, "REST API stopped");
        }
        if (mDisposables != null) {
            mDisposables.dispose();
            mDisposables = null;
        }
    }

    private RestApiServer.RestResponse handleRequest(RestApiServer.RestRequest request) {
        if (!isAuthorized(request)) {
            return response(HTTP_UNAUTHORIZED, "Unauthorized");
        }

        try {
            return route(request);
        } catch (Exception e) {
            Log.e(TAG, "REST API error: %s", e.getMessage());
            return response(HTTP_INTERNAL_ERROR, "Internal error");
        }
    }

    private RestApiServer.RestResponse route(RestApiServer.RestRequest request) {
        String method = request.method;
        String path = request.path;

        if ("POST".equals(method) && "/api/v1/playback/play".equals(path)) {
            return runOnMain(this::play);
        }
        if ("POST".equals(method) && "/api/v1/playback/pause".equals(path)) {
            return runOnMain(this::pause);
        }
        if ("POST".equals(method) && "/api/v1/playback/toggle".equals(path)) {
            return runOnMain(this::toggle);
        }
        if ("POST".equals(method) && "/api/v1/playback/next".equals(path)) {
            return runOnMain(this::next);
        }
        if ("POST".equals(method) && "/api/v1/playback/previous".equals(path)) {
            return runOnMain(this::previous);
        }
        if ("POST".equals(method) && "/api/v1/playback/seek".equals(path)) {
            String positionStr = request.query.get("positionMs");
            long positionMs = Helpers.parseLong(positionStr);
            if (positionMs < 0) {
                return response(HTTP_BAD_REQUEST, "Missing or invalid positionMs");
            }
            return runOnMain(() -> seek(positionMs));
        }
        if ("POST".equals(method) && "/api/v1/playback/stop".equals(path)) {
            return runOnMain(this::playbackStop);
        }
        if ("POST".equals(method) && "/api/v1/playback/pip".equals(path)) {
            return runOnMain(this::togglePip);
        }
        if ("GET".equals(method) && "/api/v1/recommendations".equals(path)) {
            return recommendationsGet();
        }
        if ("GET".equals(method) && "/api/v1/playbackstate".equals(path)) {
            return runOnMain(this::playbackstateGet);
        }

        if ("GET".equals(method) && "/api/v1/queue".equals(path)) {
            return runOnMain(this::queueGet);
        }
        if ("POST".equals(method) && "/api/v1/queue/items".equals(path)) {
            String videoUrl = request.query.get("videoUrl");
            String playlistUrl = request.query.get("playlistUrl");
            boolean shuffle = Helpers.parseBoolean(request.query.get("shuffle"));
            if (playlistUrl != null && !playlistUrl.trim().isEmpty()) {
                String playlistId = extractPlaylistId(playlistUrl);
                if (playlistId != null) {
                    return queueAddPlaylist(playlistId, shuffle);
                } else {
                    return response(HTTP_BAD_REQUEST, "Missing or invalid playlistUrl");
                }
            } else if (videoUrl != null && !videoUrl.trim().isEmpty()) {
                String videoId = extractVideoId(videoUrl);
                if (videoId != null) {
                    return runOnMain(() -> queueAdd(videoId));
                } else {
                    return response(HTTP_BAD_REQUEST, "Missing or invalid videoUrl");
                }
            } else {
                return response(HTTP_BAD_REQUEST, "Missing videoUrl or playlistUrl");
            }
        }
        if ("POST".equals(method) && "/api/v1/queue/shuffle".equals(path)) {
            return runOnMain(this::queueShuffle);
        }
        if ("POST".equals(method) && "/api/v1/queue/clear".equals(path)) {
            return runOnMain(() -> {
                Playlist.instance().clear();
                return response(HTTP_OK, "Queue cleared");
            });
        }
        if ("POST".equals(method) && "/api/v1/queue/show".equals(path)) {
            return runOnMain(this::showQueue);
        }
        if ("DELETE".equals(method) && path.startsWith("/api/v1/queue/items/")) {
            String videoId = path.substring("/api/v1/queue/items/".length());
            if (videoId.isEmpty()) {
                return response(HTTP_BAD_REQUEST, "Missing videoId");
            }
            return runOnMain(() -> queueRemove(videoId));
        }
        if ("POST".equals(method) && "/api/v1/queue/items/move".equals(path)) {
            int from = Helpers.parseInt(request.query.get("from"));
            int to = Helpers.parseInt(request.query.get("to"));
            if (from < 0 || to < 0) {
                return response(HTTP_BAD_REQUEST, "Missing or invalid from/to parameters");
            }
            return runOnMain(() -> queueMove(from, to));
        }

        if ("POST".equals(method) && "/api/v1/play".equals(path)) {
            String url = request.query.get("url");
            int index = Helpers.parseInt(request.query.get("index"));
            boolean shuffle = Helpers.parseBoolean(request.query.get("shuffle"));
            if (url == null || url.trim().isEmpty()) {
                return response(HTTP_BAD_REQUEST, "Missing url");
            }
            String playlistId = extractPlaylistId(url);
            if (playlistId != null) {
                return runOnMain(() -> playPlaylist(playlistId, index, shuffle));
            }
            String videoId = extractVideoId(url);
            if (videoId != null) {
                return runOnMain(() -> playVideo(videoId));
            }
            return response(HTTP_BAD_REQUEST, "Missing or invalid url");
        }

        return response(HTTP_NOT_FOUND, "Unknown endpoint");
    }

    private RestApiServer.RestResponse play() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
        PlaybackView player = presenter.getPlayer();
        if (player != null) {
            player.setPlayWhenReady(true);
            return response(HTTP_OK, "Playback started");
        }

        Video current = Playlist.instance().getCurrent();
        if (current != null) {
            presenter.openVideo(current);
            return response(HTTP_OK, "Playback started");
        }

        return response(HTTP_UNPROCESSABLE, "No active video");
    }

    private RestApiServer.RestResponse playVideo(String videoId) {
        Log.d(TAG, "playVideo: videoId=%s", videoId);
        Playlist playlist = Playlist.instance();
        playlist.clear();
        Video video = Video.from(videoId);

        MediaServiceManager.instance().loadMetadata(video, metadata -> {
            video.sync(metadata);
            if (video.title == null) {
                video.title = metadata.getTitle();
            }
            if (video.secondTitle == null) {
                video.secondTitle = metadata.getSecondTitle();
            }
            mMainHandler.post(() -> {
                try {
                    playlist.add(video);
                    playlist.setCurrent(video);
                    PlaybackPresenter.instance(mContext).openVideo(video);
                } catch (RuntimeException e) {
                    Log.e(TAG, "playVideo callback failed: %s", e.getMessage());
                }
            });
        });

        return response(HTTP_ACCEPTED, "Playback started");
    }

    private RestApiServer.RestResponse pause() {
        PlaybackView player = PlaybackPresenter.instance(mContext).getPlayer();
        if (player == null) {
            return response(HTTP_UNPROCESSABLE, "Player is not active");
        }

        player.setPlayWhenReady(false);
        return response(HTTP_OK, "Playback paused");
    }

    private RestApiServer.RestResponse toggle() {
        PlaybackView player = PlaybackPresenter.instance(mContext).getPlayer();
        if (player == null) {
            return response(HTTP_UNPROCESSABLE, "Player is not active");
        }

        player.setPlayWhenReady(!player.getPlayWhenReady());
        return response(HTTP_OK, "Playback toggled");
    }

    private RestApiServer.RestResponse next() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
        presenter.onNextClicked();
        return response(HTTP_OK, "Next requested");
    }

    private RestApiServer.RestResponse previous() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
        presenter.onPreviousClicked();
        return response(HTTP_OK, "Previous requested");
    }

    private RestApiServer.RestResponse seek(long positionMs) {
        PlaybackPresenter.instance(mContext).setPosition(positionMs);
        return response(HTTP_OK, "Seek requested");
    }

    private RestApiServer.RestResponse playbackStop() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
        PlaybackView player = presenter.getPlayer();
        if (player == null) {
            return response(HTTP_UNPROCESSABLE, "Player is not active");
        }
        presenter.forceFinish();
        return response(HTTP_OK, "Playback stopped");
    }

    private RestApiServer.RestResponse togglePip() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
        PlaybackView player = presenter.getPlayer();
        if (player == null) {
            return response(HTTP_UNPROCESSABLE, "Player is not active");
        }
        // Workaround: check background state directly instead of isRunningInBackground()
        // because getView() is null when the view is an androidx fragment
        boolean isRunningInBackground = player.isEngineBlocked() &&
                player.isEngineInitialized() &&
                !ViewManager.instance(mContext).isPlayerInForeground() &&
                Utils.checkActivity(presenter.getActivity());
        if (isRunningInBackground) {
            ViewManager.instance(mContext).movePlayerToForeground();
            return response(HTTP_OK, "Exited PIP mode");
        } else {
            player.showOverlay(false);
            player.blockEngine(true);
            player.finish();
            return response(HTTP_OK, "Entered PIP mode");
        }
    }

    private RestApiServer.RestResponse playbackstateGet() {
        PlaybackView player = PlaybackPresenter.instance(mContext).getPlayer();
        Video video = player != null ? player.getVideo() : null;
        PlaybackStateResponse state = new PlaybackStateResponse();
        state.videoId = videoId(video);
        state.title = title(video);
        state.isPlaying = player != null && player.isPlaying();
        state.isLoading = player != null && player.isLoading();
        state.playWhenReady = player != null && player.getPlayWhenReady();
        state.positionMs = player != null ? player.getPositionMs() : 0;
        state.durationMs = player != null ? player.getDurationMs() : 0;
        state.speed = player != null ? player.getSpeed() : 1.0f;
        state.volume = player != null ? player.getVolume() : 1.0f;
        state.thumbnail = thumbnail(video);
        return new RestApiServer.RestResponse(HTTP_OK, sGson.toJson(state));
    }

    private RestApiServer.RestResponse queueGet() {
        return new RestApiServer.RestResponse(HTTP_OK, buildQueueResponse());
    }

    private String buildQueueResponse() {
        Playlist playlist = Playlist.instance();
        List<Video> items = playlist.getAll();
        Video current = playlist.getCurrent();
        int currentIndex = current != null ? items.indexOf(current) : -1;
        QueueResponse response = new QueueResponse();
        response.currentVideoId = videoId(current);
        response.currentTitle = title(current);
        response.currentThumbnail = thumbnail(current);
        response.currentIndex = currentIndex;
        response.items = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Video video = items.get(i);
            QueueItem item = new QueueItem();
            item.index = i;
            item.videoId = videoId(video);
            item.title = title(video);
            item.thumbnail = thumbnail(video);
            response.items.add(item);
        }
        return sGson.toJson(response);
    }

    private RestApiServer.RestResponse queueAdd(String videoId) {
        Playlist playlist = Playlist.instance();
        boolean wasEmpty = playlist.getAll().isEmpty();
        Video video = Video.from(videoId);

        MediaServiceManager.instance().loadMetadata(video, metadata -> {
            video.sync(metadata);
            if (video.title == null) {
                video.title = metadata.getTitle();
            }
            if (video.secondTitle == null) {
                video.secondTitle = metadata.getSecondTitle();
            }
            mMainHandler.post(() -> {
                playlist.add(video);
                showQueueNotification(R.string.rest_api_notification_added, video.title);
                if (wasEmpty) {
                    PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
                    PlaybackView player = presenter.getPlayer();
                    if (player == null || !player.containsMedia()) {
                        playlist.setCurrent(video);
                        presenter.openVideo(video);
                    }
                }
            });
        });

        return response(HTTP_ACCEPTED, "Added to queue");
    }

    private RestApiServer.RestResponse queueAddPlaylist(String playlistId, boolean shuffle) {
        showQueueNotification(R.string.rest_api_notification_queuing_playlist);
        final int generationBeforeLoad = Playlist.instance().getGeneration();
        Video video = new Video();
        video.playlistId = playlistId;
        MediaServiceManager.instance().loadMetadata(video, metadata -> {
            if (Playlist.instance().getGeneration() != generationBeforeLoad) {
                Log.d(TAG, "queueAddPlaylist: playlist generation changed during load, skipping add");
                return;
            }
            MediaGroup playlistGroup = findPlaylistRow(metadata);
            final String playlistTitle = playlistGroup != null ? playlistGroup.getTitle() : null;
            io.reactivex.disposables.Disposable d = Observable.fromCallable(() -> loadAllPlaylistItems(playlistGroup))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(allItems -> {
                        if (Playlist.instance().getGeneration() != generationBeforeLoad) {
                            Log.d(TAG, "queueAddPlaylist: playlist generation changed during load, skipping add");
                            showQueueNotification(R.string.rest_api_notification_playlist_cancelled);
                            return;
                        }
                        if (allItems == null || allItems.isEmpty()) {
                            Log.w(TAG, "queueAddPlaylist: no videos found for playlist %s", playlistId);
                            showQueueNotification(R.string.rest_api_notification_playlist_empty);
                            return;
                        }
                        int added = 0;
                        Playlist playlist = Playlist.instance();
                        for (MediaItem item : allItems) {
                            Video v = Video.from(item);
                            if (v != null && !Video.isEmpty(v)) {
                                playlist.add(v);
                                added++;
                            }
                        }
                        if (shuffle) {
                            playlist.shuffle();
                        }
                        if (playlistTitle != null && !playlistTitle.isEmpty()) {
                            showQueueNotification(R.string.rest_api_notification_playlist_added, playlistTitle, added);
                        } else {
                            showQueueNotification(R.string.rest_api_notification_playlist_added_no_title, added);
                        }
                        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
                        PlaybackView player = presenter.getPlayer();
                        if (player == null || !player.containsMedia()) {
                            Video first = playlist.getCurrent();
                            if (first == null && !playlist.getAll().isEmpty()) {
                                first = playlist.getAll().get(0);
                                playlist.setCurrent(first);
                            }
                            if (first != null) {
                                presenter.openVideo(first);
                            }
                        }
                        Log.d(TAG, "queueAddPlaylist: added %s videos from playlist %s (shuffle=%s)", added, playlistId, shuffle);
                    }, error -> Log.e(TAG, "queueAddPlaylist: error loading items: %s", error.getMessage()));
            if (mDisposables != null) {
                mDisposables.add(d);
            }
        });
        return response(HTTP_ACCEPTED, "Playlist queue update scheduled");
    }

    private RestApiServer.RestResponse queueShuffle() {
        Playlist playlist = Playlist.instance();
        int size = playlist.getAll().size();
        playlist.shuffle();
        showQueueNotification(R.string.rest_api_notification_shuffled, size);
        return new RestApiServer.RestResponse(HTTP_OK, buildQueueResponse());
    }

    private RestApiServer.RestResponse queueMove(int from, int to) {
        Playlist playlist = Playlist.instance();
        if (from >= playlist.getAll().size() || to >= playlist.getAll().size()) {
            return response(HTTP_BAD_REQUEST, "Index out of bounds");
        }
        playlist.move(from, to);
        showQueueNotification(R.string.rest_api_notification_reordered);
        return response(HTTP_OK, "Item moved");
    }

    private RestApiServer.RestResponse queueRemove(String videoId) {
        Playlist.instance().remove(Video.from(videoId));
        return response(HTTP_OK, "Removed from queue");
    }

    private RestApiServer.RestResponse showQueue() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mContext);
        PlaybackView player = presenter.getPlayer();
        if (player == null || !player.containsMedia()) {
            return response(HTTP_UNPROCESSABLE, "Player is not active");
        }
        AppDialogUtil.showPlaybackQueueDialog(mContext, video -> PlaybackPresenter.instance(mContext).openVideo(video));
        return response(HTTP_OK, "Queue dialog shown");
    }

    private RestApiServer.RestResponse recommendationsGet() {
        // Safely get current video on the main thread
        final Video[] videoHolder = new Video[1];
        CountDownLatch videoLatch = new CountDownLatch(1);
        mMainHandler.post(() -> {
            videoHolder[0] = PlaybackPresenter.instance(mContext).getVideo();
            videoLatch.countDown();
        });
        try {
            if (!videoLatch.await(5, TimeUnit.SECONDS)) {
                return response(HTTP_INTERNAL_ERROR, "Main thread timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return response(HTTP_INTERNAL_ERROR, "Interrupted");
        }

        Video video = videoHolder[0];
        if (video == null || Video.isEmpty(video)) {
            return new RestApiServer.RestResponse(HTTP_OK, sGson.toJson(new java.util.ArrayList<>()));
        }

        // Load metadata on a background thread to avoid blocking the HTTP worker
        MediaItemMetadata metadata = Observable.fromCallable(() -> {
            com.liskovsoft.mediaserviceinterfaces.MediaItemService itemService =
                    com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getMediaItemService();
            return itemService.getMetadata(video.videoId);
        }).subscribeOn(Schedulers.io()).blockingFirst();

        if (metadata == null || metadata.getSuggestions() == null) {
            return new RestApiServer.RestResponse(HTTP_OK, sGson.toJson(new java.util.ArrayList<>()));
        }

        String currentPlaylistId = video.getPlaylistId();

        // Find the largest non-playlist group (the "Related" section).
        // Playlist row items all share the video's playlistId; related videos have none.
        // General recommendation sections have ~6 items; related has ~30 items.
        MediaGroup relatedGroup = null;
        int maxSize = 0;

        for (MediaGroup group : metadata.getSuggestions()) {
            List<MediaItem> items = group.getMediaItems();
            if (items == null || items.isEmpty()) {
                continue;
            }

            // Skip playlist row: all items share the current video's playlistId
            if (currentPlaylistId != null && !currentPlaylistId.isEmpty()) {
                boolean allSamePlaylist = true;
                for (MediaItem item : items) {
                    if (!currentPlaylistId.equals(item.getPlaylistId())) {
                        allSamePlaylist = false;
                        break;
                    }
                }
                if (allSamePlaylist) {
                    continue;
                }
            }

            int size = items.size();
            if (size > maxSize) {
                maxSize = size;
                relatedGroup = group;
            }
        }

        if (relatedGroup == null || relatedGroup.getMediaItems() == null || relatedGroup.getMediaItems().isEmpty()) {
            return new RestApiServer.RestResponse(HTTP_OK, sGson.toJson(new java.util.ArrayList<>()));
        }

        List<MediaItem> items = relatedGroup.getMediaItems();
        RecommendationGroup group = new RecommendationGroup();
        group.title = relatedGroup.getTitle();
        group.videos = new java.util.ArrayList<>();
        for (MediaItem item : items) {
            Video v = Video.from(item);
            RecommendationVideo rv = new RecommendationVideo();
            rv.videoId = videoId(v);
            rv.title = title(v);
            rv.thumbnail = thumbnail(v);
            group.videos.add(rv);
        }
        return new RestApiServer.RestResponse(HTTP_OK, sGson.toJson(new RecommendationGroup[]{group}));
    }

    private RestApiServer.RestResponse playPlaylist(String playlistId, int index, boolean shuffle) {
        Log.d(TAG, "playPlaylist: playlistId=%s, index=%s, shuffle=%s", playlistId, index, shuffle);
        Video video = new Video();
        video.playlistId = playlistId;
        if (index > 0) {
            video.playlistIndex = index;
        }

        MediaServiceManager.instance().loadMetadata(video, metadata -> {
            MediaGroup playlistGroup = findPlaylistRow(metadata);
            io.reactivex.disposables.Disposable d = Observable.fromCallable(() -> loadAllPlaylistItems(playlistGroup))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(allItems -> {
                        if (allItems == null || allItems.isEmpty()) {
                            Log.w(TAG, "playPlaylist: no videos found in playlist %s", playlistId);
                            return;
                        }

                        List<Video> playlistVideos = new java.util.ArrayList<>();
                        for (MediaItem item : allItems) {
                            Video v = Video.from(item);
                            if (v != null && !Video.isEmpty(v)) {
                                playlistVideos.add(v);
                            }
                        }

                        if (playlistVideos.isEmpty()) {
                            Log.w(TAG, "playPlaylist: no videos found in playlist %s", playlistId);
                            return;
                        }

                        Playlist playlist = Playlist.instance();
                        playlist.clear();
                        for (Video v : playlistVideos) {
                            playlist.add(v);
                        }

                        if (shuffle) {
                            playlist.shuffle();
                        }

                        Video first = null;
                        if (index > 0 && index < playlistVideos.size()) {
                            first = playlistVideos.get(index);
                        }
                        if (first == null) {
                            first = playlist.getCurrent();
                        }
                        if (first == null && !playlist.getAll().isEmpty()) {
                            first = playlist.getAll().get(0);
                            playlist.setCurrent(first);
                        }

                        if (first != null) {
                            playlist.setCurrent(first);
                            first.playlistId = playlistId;
                            if (index > 0) {
                                first.playlistIndex = index;
                            }
                            PlaybackPresenter.instance(mContext).openVideo(first);
                            Log.d(TAG, "playPlaylist: started playback of %s (shuffle=%s)", first.videoId, shuffle);
                        }
                    }, error -> Log.e(TAG, "playPlaylist: error loading items: %s", error.getMessage()));
            if (mDisposables != null) {
                mDisposables.add(d);
            }
        });

        return response(HTTP_OK, "Playlist playback started");
    }

    private RestApiServer.RestResponse runOnMain(MainAction action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return action.run();
            } catch (Exception e) {
                Log.e(TAG, "Main action error: %s", e.getMessage());
                return response(HTTP_INTERNAL_ERROR, "Internal error");
            }
        }

        final RestApiServer.RestResponse[] result = new RestApiServer.RestResponse[1];
        CountDownLatch latch = new CountDownLatch(1);
        mMainHandler.post(() -> {
            try {
                result[0] = action.run();
            } catch (Exception e) {
                Log.e(TAG, "Main action error: %s", e.getMessage());
                result[0] = response(HTTP_INTERNAL_ERROR, "Internal error");
            }
            latch.countDown();
        });

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return response(HTTP_INTERNAL_ERROR, "Main thread timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return response(HTTP_INTERNAL_ERROR, "Interrupted");
        }

        return result[0];
    }

    private boolean isAuthorized(RestApiServer.RestRequest request) {
        return isAuthorized(request, AppPrefs.instance(mContext));
    }

    boolean isAuthorized(RestApiServer.RestRequest request, AppPrefs prefs) {
        String header = request.headers.get("authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }

        String encoded = header.substring("Basic ".length()).trim();
        String decoded;
        try {
            decoded = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }

        int idx = decoded.indexOf(':');
        if (idx < 0) {
            return false;
        }

        String username = decoded.substring(0, idx);
        String password = decoded.substring(idx + 1);

        return Helpers.equals(username, prefs.getRestApiUsername()) &&
                Helpers.equals(password, prefs.getRestApiPassword());
    }

    private RestApiServer.RestResponse response(int status, String message) {
        return new RestApiServer.RestResponse(status, sGson.toJson(new MessageResponse(message)));
    }

    private String videoId(Video video) {
        return video != null && video.videoId != null ? video.videoId : "";
    }

    private String title(Video video) {
        return video != null && video.getTitleFull() != null ? video.getTitleFull() : "";
    }

    private String thumbnail(Video video) {
        return video != null && video.getCardImageUrl() != null ? video.getCardImageUrl() : "";
    }

    String extractVideoId(String... sources) {
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            String trimmed = source.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Bare video ID
            if (trimmed.length() == 11 && !trimmed.contains("/") && !trimmed.contains("?")) {
                return trimmed;
            }
            // Parse as URL
            if (trimmed.startsWith("http")) {
                String id = parseVideoIdFromUrl(trimmed);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    String parseVideoIdFromUrl(String url) {
        Uri uri = Uri.parse(url);
        String id = uri.getQueryParameter("v");
        if (id != null && id.length() == 11) {
            return id;
        }
        String host = uri.getHost();
        if (host != null && (host.equals("youtu.be") || host.endsWith(".youtu.be"))) {
            id = uri.getLastPathSegment();
            if (id != null && id.length() == 11) {
                return id;
            }
        }
        String path = uri.getPath();
        if (path != null) {
            String[] patterns = {"/shorts/", "/live/", "/embed/", "/v/"};
            for (String pattern : patterns) {
                int idx = path.indexOf(pattern);
                if (idx >= 0) {
                    id = path.substring(idx + pattern.length());
                    int end = id.indexOf("/");
                    if (end > 0) {
                        id = id.substring(0, end);
                    }
                    end = id.indexOf("?");
                    if (end > 0) {
                        id = id.substring(0, end);
                    }
                    if (id.length() == 11) {
                        return id;
                    }
                }
            }
        }
        return null;
    }

    String extractPlaylistId(String... sources) {
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            String trimmed = source.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Bare playlist ID (not a URL). Skip 11-char strings that are likely video IDs.
            if (!trimmed.startsWith("http")) {
                if (trimmed.length() == 11) {
                    continue; // likely a video ID, try next source
                }
                return trimmed;
            }
            // Parse as URL
            String id = parsePlaylistIdFromUrl(trimmed);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    String parsePlaylistIdFromUrl(String url) {
        Uri uri = Uri.parse(url);
        String id = uri.getQueryParameter("list");
        return id != null && !id.trim().isEmpty() ? id.trim() : null;
    }

    private List<MediaItem> loadAllPlaylistItems(MediaGroup playlistGroup) {
        if (playlistGroup == null) {
            return null;
        }
        List<MediaItem> allItems = new java.util.ArrayList<>();
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
                Log.e(TAG, "loadAllPlaylistItems: continuation error: %s", e.getMessage());
                break;
            }
        }
        return allItems;
    }

    /**
     * Playlist usually is the first row with media items.<br/>
     * NOTE: before playlist may be the video description row
     */
    private MediaGroup findPlaylistRow(MediaItemMetadata mediaItemMetadata) {
        if (mediaItemMetadata == null || mediaItemMetadata.getSuggestions() == null) {
            Log.d(TAG, "findPlaylistRow: null metadata or suggestions");
            return null;
        }

        for (MediaGroup group : mediaItemMetadata.getSuggestions()) {
            List<MediaItem> mediaItems = group.getMediaItems();
            if (mediaItems != null && !mediaItems.isEmpty()) {
                Log.d(TAG, "findPlaylistRow: found group=%s, items=%s", group.getTitle(), mediaItems.size());
                return group;
            }
        }

        Log.d(TAG, "findPlaylistRow: no group found");
        return null;
    }

    private static class MessageResponse {
        final String message;

        MessageResponse(String message) {
            this.message = message;
        }
    }

    private static class PlaybackStateResponse {
        String videoId;
        String title;
        String thumbnail;
        boolean isPlaying;
        boolean isLoading;
        boolean playWhenReady;
        long positionMs;
        long durationMs;
        float speed;
        float volume;
    }

    private static class QueueItem {
        int index;
        String videoId;
        String title;
        String thumbnail;
    }

    private static class QueueResponse {
        String currentVideoId;
        String currentTitle;
        String currentThumbnail;
        int currentIndex;
        List<QueueItem> items;
    }

    private static class RecommendationVideo {
        String videoId;
        String title;
        String thumbnail;
    }

    private static class RecommendationGroup {
        String title;
        List<RecommendationVideo> videos;
    }

    private interface MainAction {
        RestApiServer.RestResponse run();
    }
}
