package com.liskovsoft.leanbackassistant.media;

import android.content.Context;

public class ClipServiceCached extends ClipService {
    private static ClipServiceCached mInstance;
    private Playlist mSubscriptions;
    private long mSubscriptionsTime;
    private Playlist mHistory;
    private long mHistoryTime;
    private Playlist mRecommended;
    private long mRecommendedTime;
    private static final long CACHE_UPDATE_TIME = 15 * 60 * 1000;

    public ClipServiceCached(Context context) {
        super(context);
    }

    public static ClipService instance(Context context) {
        if (mInstance == null) {
            mInstance = new ClipServiceCached(context);
        }

        return mInstance;
    }

    public Playlist getSubscriptionsPlaylist() {
        Playlist cache = getSubscriptionsPlaylistCache();

        if (cache == null) {
            cache = super.getSubscriptionsPlaylist();
            setSubscriptionsPlaylistCache(cache);
        }

        return cache;
    }

    private Playlist getSubscriptionsPlaylistCache() {
        if ((System.currentTimeMillis() - mSubscriptionsTime) < CACHE_UPDATE_TIME) {
            return mSubscriptions;
        }

        return null;
    }

    private void setSubscriptionsPlaylistCache(Playlist cache) {
        mSubscriptionsTime = System.currentTimeMillis();
        mSubscriptions = cache;
    }

    public Playlist getHistoryPlaylist() {
        Playlist cache = getHistoryPlaylistCache();

        if (cache == null) {
            cache = super.getHistoryPlaylist();
            setHistoryPlaylistCache(cache);
        }

        return cache;
    }

    private Playlist getHistoryPlaylistCache() {
        if ((System.currentTimeMillis() - mHistoryTime) < CACHE_UPDATE_TIME) {
            return mHistory;
        }

        return null;
    }

    private void setHistoryPlaylistCache(Playlist cache) {
        mHistoryTime = System.currentTimeMillis();
        mHistory = cache;
    }

    public Playlist getRecommendedPlaylist() {
        Playlist cache = getRecommendedPlaylistCache();

        if (cache == null) {
            cache = super.getRecommendedPlaylist();
            setRecommendedPlaylistCache(cache);
        }

        return cache;
    }

    private Playlist getRecommendedPlaylistCache() {
        if ((System.currentTimeMillis() - mRecommendedTime) < CACHE_UPDATE_TIME) {
            return mRecommended;
        }

        return null;
    }

    private void setRecommendedPlaylistCache(Playlist cache) {
        mRecommendedTime = System.currentTimeMillis();
        mRecommended = cache;
    }
}
