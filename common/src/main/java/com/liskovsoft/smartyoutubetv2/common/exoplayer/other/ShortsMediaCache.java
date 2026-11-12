package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSinkFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Disk cache for Shorts media.<br/>
 * The first seconds of the next Shorts are downloaded in background while the current one is playing
 * (see {@link ShortsPreloader}), so the next one starts from disk instantly.
 * Played Shorts are kept too (instant going back).
 */
public final class ShortsMediaCache {
    private static final String TAG = ShortsMediaCache.class.getSimpleName();
    private static final String CACHE_DIR = "shorts_media";
    private static final long MAX_CACHE_BYTES = 150L * 1024 * 1024;
    /**
     * A bit more than the player needs to start (bufferForPlaybackMs is 2.5 sec)
     */
    private static final int PREFETCH_SECONDS = 6;
    private static final long MIN_PREFETCH_BYTES = 512 * 1024;
    private static final long MAX_PREFETCH_BYTES = 6L * 1024 * 1024;
    private static SimpleCache sCache;
    private static boolean sCacheFailed;
    private static DataSource.Factory sUpstreamFactory;

    /**
     * The url of the same media differs between requests (init url, signatures, extra params).<br/>
     * So, use stable parts only: video id, itag, content length and extra tags (e.g. drc audio).
     */
    private static final CacheKeyFactory KEY_FACTORY = new CacheKeyFactory() {
        @Override
        public String buildCacheKey(DataSpec dataSpec) {
            if (dataSpec.key != null) {
                return dataSpec.key;
            }

            return buildKey(dataSpec.uri);
        }

        @Override
        public int maxDownloadParallelSegments() {
            return 1; // used by the offline downloader only
        }
    };

    private ShortsMediaCache() {
    }

    /**
     * The same network stack as the player (headers, user agent, okhttp/cronet)
     */
    public static synchronized void setUpstreamFactory(DataSource.Factory upstreamFactory) {
        sUpstreamFactory = upstreamFactory;
    }

    /**
     * Reads from the disk cache first. Writes everything that is loaded from the network.
     */
    public static DataSource.Factory wrap(Context context, DataSource.Factory upstreamFactory) {
        Cache cache = getCache(context);

        if (cache == null) {
            return upstreamFactory;
        }

        return new CacheDataSourceFactory(
                cache,
                upstreamFactory,
                new FileDataSourceFactory(),
                new CacheDataSinkFactory(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                null,
                KEY_FACTORY
        );
    }

    /**
     * Download the start of the given video. Blocking, call from a background thread.
     * @param videoItag itag of the video track that is playing now (the player will most likely pick the same quality)
     * @param audioItag itag of the audio track that is playing now
     * @param canceled stops the download (e.g. the user has skipped the video)
     * @return whether the start of both tracks is in the cache now
     */
    public static boolean prefetch(Context context, MediaItemFormatInfo formatInfo, @Nullable String videoItag, @Nullable String audioItag,
                                   AtomicBoolean canceled) {
        if (formatInfo == null || formatInfo.isLive() || formatInfo.getAdaptiveFormats() == null) {
            return false;
        }

        Cache cache = getCache(context);
        DataSource.Factory upstreamFactory;

        synchronized (ShortsMediaCache.class) {
            upstreamFactory = sUpstreamFactory;
        }

        if (cache == null || upstreamFactory == null) {
            return false;
        }

        MediaFormat video = selectVideo(formatInfo.getAdaptiveFormats(), videoItag);
        MediaFormat audio = selectAudio(formatInfo.getAdaptiveFormats(), audioItag);

        // Both tracks are needed for the start. Load them in parallel like the player does.
        AtomicBoolean audioDone = new AtomicBoolean();
        Thread audioThread = new Thread(() -> audioDone.set(prefetchFormat(cache, upstreamFactory, audio, canceled)), "ShortsPrefetchAudio");
        audioThread.start();
        boolean videoDone = prefetchFormat(cache, upstreamFactory, video, canceled);

        try {
            audioThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        boolean done = videoDone && audioDone.get();
        Log.d(TAG, "Prefetch %s: %s, video itag: %s, audio itag: %s", done ? "done" : "incomplete", formatInfo.getVideoId(),
                video != null ? video.getITag() : null, audio != null ? audio.getITag() : null);

        return done;
    }

    /**
     * @return whether the start of the format is in the cache now
     */
    private static boolean prefetchFormat(Cache cache, DataSource.Factory upstreamFactory, MediaFormat format, AtomicBoolean canceled) {
        // OTF formats are split into numbered segments (no byte ranges). Nothing to prefetch by a range.
        if (format == null || format.getUrl() == null || format.isOtf() || canceled.get()) {
            return false;
        }

        try {
            Uri uri = Uri.parse(format.getUrl());
            long length = getPrefetchLength(format);
            // NOTE: the key must be set explicitly. CacheUtil writes through a data source of its own
            // that doesn't know about the key factory, so the data would be stored under the url
            // and the player (which reads by the stable key) would never find it.
            DataSpec dataSpec = new DataSpec(uri, 0, length, buildKey(uri));
            CacheUtil.cache(dataSpec, cache, KEY_FACTORY, upstreamFactory.createDataSource(), null, canceled);
            return true;
        } catch (InterruptedException e) {
            // Canceled by the flag (the thread itself isn't interrupted). Nothing to restore.
            Log.d(TAG, "Prefetch canceled: itag %s", format.getITag());
        } catch (Exception e) { // network, disk (no space, cache folder removed), malformed url
            Log.e(TAG, "Prefetch error: itag %s: %s", format.getITag(), e.getMessage());
        }

        return false;
    }

    /**
     * Header (init + index) and the first seconds of media
     */
    private static long getPrefetchLength(MediaFormat format) {
        long headerEnd = Math.max(getRangeEnd(format.getIndex()), getRangeEnd(format.getInit())) + 1;
        long bitrate = Helpers.parseLong(format.getBitrate()); // bits per sec
        long mediaBytes = bitrate > 0 ? bitrate / 8 * PREFETCH_SECONDS : MIN_PREFETCH_BYTES;
        long length = headerEnd + Math.max(Math.min(mediaBytes, MAX_PREFETCH_BYTES), MIN_PREFETCH_BYTES);
        long contentLength = Helpers.parseLong(format.getClen());

        return contentLength > 0 ? Math.min(length, contentLength) : length;
    }

    private static long getRangeEnd(String range) {
        if (range == null) {
            return 0;
        }

        String[] parts = range.split("-");

        return parts.length == 2 ? Math.max(Helpers.parseLong(parts[1]), 0) : 0;
    }

    @Nullable
    private static MediaFormat selectVideo(List<MediaFormat> formats, @Nullable String itag) {
        MediaFormat sameItag = findByItag(formats, itag, "video/");

        if (sameItag != null) {
            return sameItag;
        }

        // Unknown or missing quality. Take the format with the highest resolution not above 1080p.
        MediaFormat result = null;

        for (MediaFormat format : formats) {
            if (isType(format, "video/") && format.getHeight() > 0 && Math.min(format.getWidth(), format.getHeight()) <= 1080
                    && (result == null || format.getHeight() > result.getHeight())) {
                result = format;
            }
        }

        return result;
    }

    @Nullable
    private static MediaFormat selectAudio(List<MediaFormat> formats, @Nullable String itag) {
        MediaFormat sameItag = findByItag(formats, itag, "audio/");

        if (sameItag != null) {
            return sameItag;
        }

        MediaFormat result = null;

        for (MediaFormat format : formats) {
            if (isType(format, "audio/") && !format.isDrc()
                    && (result == null || Helpers.parseLong(format.getBitrate()) > Helpers.parseLong(result.getBitrate()))) {
                result = format;
            }
        }

        return result;
    }

    @Nullable
    private static MediaFormat findByItag(List<MediaFormat> formats, @Nullable String itag, String type) {
        if (itag == null) {
            return null;
        }

        for (MediaFormat format : formats) {
            if (isType(format, type) && itag.equals(format.getITag()) && !format.isDrc()) {
                return format;
            }
        }

        return null;
    }

    private static boolean isType(MediaFormat format, String type) {
        return format.getMimeType() != null && format.getMimeType().startsWith(type) && format.getUrl() != null;
    }

    private static String buildKey(Uri uri) {
        try {
            String id = uri.getQueryParameter("id");
            String itag = uri.getQueryParameter("itag");
            String clen = uri.getQueryParameter("clen");

            // The stable key is valid only when the url addresses the whole file (the part is chosen by the byte position).
            // Urls that address a part of the media by themselves (sq - segment number, range) must not share a key.
            if (id == null || itag == null || clen == null
                    || uri.getQueryParameter("sq") != null || uri.getQueryParameter("range") != null) {
                return uri.toString();
            }

            return id + "|" + itag + "|" + clen + "|" + uri.getQueryParameter("xtags");
        } catch (RuntimeException e) { // not a hierarchical uri
            return uri.toString();
        }
    }

    @Nullable
    private static synchronized Cache getCache(Context context) {
        if (sCache == null && !sCacheFailed && context != null) {
            try {
                File dir = new File(context.getApplicationContext().getCacheDir(), CACHE_DIR);
                sCache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES));
            } catch (Exception e) { // e.g. the folder is locked by another instance
                Log.e(TAG, "Can't create the cache: %s", e.getMessage());
                sCacheFailed = true; // don't retry on each video
            }
        }

        return sCache;
    }
}
