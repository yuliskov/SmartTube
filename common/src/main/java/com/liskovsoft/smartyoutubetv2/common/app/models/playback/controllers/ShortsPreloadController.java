package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ShortsPreloader;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Instant start of the next Shorts: while the current one is playing, fetches the formats
 * and the first seconds of media of the upcoming ones.<br/>
 * The formats: {@link MediaServiceManager#preloadFormatInfo}. The media: {@link ShortsPreloader}.
 */
public class ShortsPreloadController extends BasePlayerController {
    private static final String TAG = ShortsPreloadController.class.getSimpleName();
    /**
     * Stay ahead when the user skips Shorts quickly
     */
    private static final int PRELOAD_COUNT = 2;
    /**
     * The next item may be unknown until the suggestions are loaded
     */
    private static final int RETRY_MS = 500;
    private static final int MAX_TRIES = 20;
    private SuggestionsController mSuggestionsController;
    private VideoLoaderController mVideoLoaderController;
    private String mLastVideoItag;
    private String mLastAudioItag;
    /**
     * The video the preload has been started for (once per video)
     */
    private String mPreloadedForVideoId;
    private int mTries;
    private final Runnable mPreload = this::preloadIfNeeded;

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);
        mVideoLoaderController = getController(VideoLoaderController.class);
    }

    @Override
    public void onSourceChanged(Video item) {
        // Don't wait for the playback start. The user may skip Shorts every couple of seconds.
        schedule();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        schedule(); // the next video may be known now
    }

    @Override
    public void onPlay() {
        rememberFormats();
        schedule();
    }

    @Override
    public void onEngineReleased() {
        cancel();
    }

    @Override
    public void onFinish() {
        cancel();
    }

    /**
     * The next Shorts most likely will be played in the same quality
     */
    private void rememberFormats() {
        if (getPlayer() == null) {
            return;
        }

        FormatItem videoFormat = getPlayer().getVideoFormat();
        FormatItem audioFormat = getPlayer().getAudioFormat();

        if (videoFormat != null && videoFormat.getFormatId() != null) {
            mLastVideoItag = videoFormat.getFormatId();
        }

        if (audioFormat != null && audioFormat.getFormatId() != null) {
            mLastAudioItag = audioFormat.getFormatId();
        }
    }

    private void schedule() {
        Video current = getVideo();

        if (current != null && !TextUtils.equals(current.videoId, mPreloadedForVideoId)) {
            mTries = 0;
            Utils.removeCallbacks(mPreload);
            Utils.post(mPreload); // let the other controllers handle the event first
        }
    }

    /**
     * Stop everything that is being preloaded (the player is closed or restarted).<br/>
     * What is already preloaded is kept. The preload starts again when a video is loaded.
     */
    private void cancel() {
        Utils.removeCallbacks(mPreload);
        mPreloadedForVideoId = null;
        MediaServiceManager.instance().cancelPreloadFormatInfo();
        ShortsPreloader.cancelAll();
    }

    private void preloadIfNeeded() {
        Video current = getVideo();
        Context context = getContext();

        if (context == null || mSuggestionsController == null || mVideoLoaderController == null || isEmbedPlayer() || getPlayer() == null
                || current == null || current.isLive || !current.belongsToShortsGroup()
                || TextUtils.equals(current.videoId, mPreloadedForVideoId)) {
            return;
        }

        List<Video> nextVideos = mSuggestionsController.getNextVideos(PRELOAD_COUNT);

        Helpers.removeIf(nextVideos, next -> next == null || next.videoId == null || next.isLive || next.isUpcoming
                || TextUtils.equals(next.videoId, current.videoId));

        if (nextVideos.isEmpty()) {
            if (++mTries < MAX_TRIES) {
                Utils.postDelayed(mPreload, RETRY_MS);
            }
            return;
        }

        String currentVideoId = current.videoId;
        mPreloadedForVideoId = currentVideoId;

        String videoItag = mLastVideoItag;
        String audioItag = mLastAudioItag;

        // Media of the skipped Shorts isn't needed anymore. The ones that are still upcoming continue loading.
        ShortsPreloader.retainOnly(getVideoIds(nextVideos));
        MediaServiceManager.instance().preloadFormatInfo(nextVideos, formatInfo -> {
            // Late result: the user has switched to another video (maybe to this one, its formats are being loaded now)
            // or the player is closed.
            // NOTE: never warm up the video the player is opening. Two SABR sessions with the same player response
            // may be cut by the server after the first minute.
            Video now = getVideo();
            if (now == null || !TextUtils.equals(now.videoId, currentVideoId) || !TextUtils.equals(currentVideoId, mPreloadedForVideoId)) {
                return;
            }

            preloadMedia(context, formatInfo, videoItag, audioItag);
        });
    }

    /**
     * Fetch the start of the media the way the player will open it
     */
    private void preloadMedia(Context context, MediaItemFormatInfo formatInfo, String videoItag, String audioItag) {
        try {
            if (mVideoLoaderController.willOpenAsDash(formatInfo)) {
                ShortsPreloader.preload(context, formatInfo, ShortsPreloader.TYPE_DASH, videoItag, audioItag);
            } else if (mVideoLoaderController.willOpenAsSabr(formatInfo)) {
                ShortsPreloader.preload(context, formatInfo, ShortsPreloader.TYPE_SABR, videoItag, audioItag);
            }
        } catch (RuntimeException e) { // The preload is optional. It should never break the playback.
            Log.e(TAG, "Can't preload the media of %s: %s", formatInfo != null ? formatInfo.getVideoId() : null, e.getMessage());
        }
    }

    private static List<String> getVideoIds(List<Video> videos) {
        List<String> result = new ArrayList<>();

        for (Video video : videos) {
            result.add(video.videoId);
        }

        return result;
    }
}
