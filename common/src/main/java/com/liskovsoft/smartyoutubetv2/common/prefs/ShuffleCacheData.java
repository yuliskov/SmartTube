package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists shuffled video-ID lists keyed by playlist/channel ID.
 * Uses an LRU ordered list — index 0 is oldest, last index is most recent.
 * Shared across profiles (shuffle cache has no profile-specific meaning).
 */
public class ShuffleCacheData {
    private static final String SHUFFLE_CACHE_DATA = "shuffle_cache_data";
    private static final int MAX_ENTRIES = 30;

    @SuppressLint("StaticFieldLeak")
    private static ShuffleCacheData sInstance;
    private final AppPrefs mPrefs;
    private final List<CacheEntry> mEntries;
    private final Runnable mPersistStateInt = this::persistStateInt;

    private static class CacheEntry {
        public final String cacheKey;
        public final List<String> videoIds;

        public CacheEntry(String cacheKey, List<String> videoIds) {
            this.cacheKey = cacheKey;
            this.videoIds = videoIds;
        }

        /**
         * Deserialise from OBJ_DELIM-separated string:
         * cacheKey &vi; videoId1 %AR% videoId2 %AR% ...
         */
        public static CacheEntry fromString(String spec) {
            if (spec == null) {
                return null;
            }

            String[] split = Helpers.splitObj(spec);

            if (split == null || split.length != 2) {
                return null;
            }

            String key = Helpers.parseStr(split[0]);
            if (key == null) {
                return null;
            }

            String[] idArray = Helpers.splitArray(split[1]);
            if (idArray == null || idArray.length == 0) {
                return null;
            }

            List<String> ids = new ArrayList<>(idArray.length);
            for (String id : idArray) {
                String parsed = Helpers.parseStr(id);
                if (parsed != null) {
                    ids.add(parsed);
                }
            }

            return ids.isEmpty() ? null : new CacheEntry(key, ids);
        }

        @Override
        public String toString() {
            return Helpers.mergeObj(cacheKey, Helpers.mergeArray(videoIds.toArray()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CacheEntry) {
                return Helpers.equals(cacheKey, ((CacheEntry) obj).cacheKey);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return cacheKey != null ? cacheKey.hashCode() : 0;
        }
    }

    private ShuffleCacheData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mEntries = new ArrayList<>();
        restoreState();
    }

    public static ShuffleCacheData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ShuffleCacheData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Returns the cached shuffled video IDs for the given key, or null on cache miss.
     */
    public List<String> getCachedVideoIds(String cacheKey) {
        if (cacheKey == null) {
            return null;
        }

        for (CacheEntry entry : mEntries) {
            if (Helpers.equals(entry.cacheKey, cacheKey)) {
                return Collections.unmodifiableList(entry.videoIds);
            }
        }

        return null;
    }

    /**
     * Stores a shuffled video-ID list. Promotes existing key to most-recent;
     * evicts the oldest entry when the cap is exceeded.
     */
    public void putCachedVideoIds(String cacheKey, List<String> videoIds) {
        if (cacheKey == null || videoIds == null || videoIds.isEmpty()) {
            return;
        }

        // Remove existing entry (will be re-added at end = most-recent)
        CacheEntry placeholder = new CacheEntry(cacheKey, Collections.emptyList());
        mEntries.remove(placeholder);

        // Evict oldest if at capacity
        while (mEntries.size() >= MAX_ENTRIES) {
            mEntries.remove(0);
        }

        mEntries.add(new CacheEntry(cacheKey, new ArrayList<>(videoIds)));
        persistState();
    }

    private void restoreState() {
        mEntries.clear();

        String data = mPrefs.getData(SHUFFLE_CACHE_DATA);
        String[] split = Helpers.splitData(data);

        if (split != null) {
            for (String spec : split) {
                CacheEntry entry = CacheEntry.fromString(spec);
                if (entry != null) {
                    mEntries.add(entry);
                }
            }
        }
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        String[] serialised = new String[mEntries.size()];
        for (int i = 0; i < mEntries.size(); i++) {
            serialised[i] = mEntries.get(i).toString();
        }

        mPrefs.setData(SHUFFLE_CACHE_DATA, Helpers.mergeData((Object[]) serialised));
    }
}
